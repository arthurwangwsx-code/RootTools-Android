# Root Tailscale 与 Android VPN 共存

## 1. 用户场景

RootTools 运行在长期在线的 Root Android 设备上。设备同时需要：

- Hiddify / 其它 Android `VpnService` 作为日常代理出口；
- Tailscale 私网作为远程管理平面；
- Root TCP ADB `:5555` / Device MCP 继续通过 tailnet 可达。

Android 同一用户下只有一个常规 `VpnService` 槽位，因此官方 Tailscale Android App 与 Hiddify
不能作为两个系统 VPN 同时占用该槽位。RootTools 的目标不是绕过 Android 安全模型，而是把 Tailscale
改为 **Root 管理的 Linux sidecar**：

```text
Hiddify / other VPN app
        ↓ Android VpnService
      Internet

RootTools-managed tailscaled
        ├─ userspace Serve: tailnet TCP 5555 / 8765 -> localhost
        └─ optional root Linux TUN: tailscale0 / tailnet routes
                           ↓
                 Mac / trusted tailnet peers
                           ↓
                 ADB 5555 / Device MCP
```

## 2. 产品边界

RootTools 负责：

1. 探测 Root Tailscale runtime / daemon / socket / `tailscale0` / tailnet IPv4；
2. 安装一个 **固定版本 + 固定 SHA-256** 的官方 ARM64 Tailscale runtime；
3. 用 userspace 模式完成一次性登录并返回官方认证 URL；
4. 优先启用 userspace Serve，只把 tailnet TCP `5555` 转发到本机 ADB，存在 MCP listener 时再转发 `8765`；
5. 需要完整 tailnet 连通时，显式切换到 root kernel TUN 模式，并以 Tailscale 原生路由 / netfilter 为真值源；
6. 只在管理链路已验证时开放“停止官方 Tailscale App”单独操作，永不作为启用过程的自动步骤；
7. 可选写入 RootTools 自己拥有的 Magisk `service.d` 启动脚本，恢复当前已验证模式；
8. 提供停用、修复、状态、审计和明确的回滚信息。

RootTools **不**：

- 修改 Hiddify 私有配置 / shared preferences；
- 自动填写第三方账号密码；
- 从 UI 接受任意 shell、URL 或安装路径；
- 在 root overlay 尚未可用时主动停止官方 Tailscale App；
- 在启用、修复或开机恢复时自动停止任何第三方 VPN App；
- 把所有互联网流量改成走 `tailscale0`；
- 默认启用开机常驻。

## 3. 运行时形态

固定目录：

```text
/data/adb/tailscale/
├── bin/
│   ├── tailscale
│   └── tailscaled
├── run/
│   ├── tailscaled.sock
│   ├── tailscaled.pid
│   ├── auth.pid
│   ├── auth.out
│   ├── userspace.log
│   └── kernel.log
└── state/
    ├── tailscaled.state
    └── authenticated.marker
```

启动恢复脚本只允许写：

```text
/data/adb/service.d/99-roottools-tailscale.sh
```

因此禁用开机恢复时可以精确删除 RootTools 自己拥有的文件，不扫描或修改其它 Magisk 脚本。

## 4. 状态机

```text
RUNTIME_MISSING
      │ install verified runtime
      ▼
STOPPED
      │ begin auth
      ▼
NEEDS_LOGIN  ── browser auth ──► AUTHENTICATED_USERSPACE
                                      │
                       ┌─ userspace Serve ─► MANAGEMENT_READY
                       └─ explicit Root TUN ─► KERNEL_READY
                                                        │
                                      optional, separately confirmed stop
                                                        ▼
                                            official Tailscale App OFF
```

UI 不用“VPN 开关”描述 Root Tailscale，因为它不是 Android `VpnService`。统一叫：

- Root Tailscale；
- Management relay / Root Overlay；
- `tailscale0`。

## 5. 安装策略

第一版使用经过设备验证的固定 runtime：

- version: `1.102.3`；
- architecture: `arm64`；
- package: `https://pkgs.tailscale.com/stable/tailscale_1.102.3_arm64.tgz`；
- SHA-256: `a0fa1b154af8c61f862a2259f559f7396d96c0225f4a863eae2333e1546bbe25`。

App 通过普通 Android 网络栈下载到自身 cache，校验完整 SHA-256，只解包 tar 中精确命名的
`tailscale` / `tailscaled`。Root Controller 再以临时文件 + 原子替换方式安装到 `/data/adb/tailscale/bin`。

不做“永远下载 latest”，因为 Root runtime 更新属于特权供应链边界；新版本必须随 RootTools 发布更新
runtime manifest/hash 并重新验证。

## 6. 认证

认证阶段使用：

```text
tailscaled --tun=userspace-networking
tailscale up --accept-dns=false --hostname=<RootTools generated hostname>
```

RootTools 只读取 `https://login.tailscale.com/a/...` URL 并交给系统浏览器。认证状态保存在 root-owned
state file；密码、OAuth cookie 和第三方账号信息不进入 RootTools。

回到 App 后自动刷新；只有 `BackendState=Running` 且拿到合法 tailnet IPv4 才算在线。
仅存在缓存 IP 但 backend 为 `Stopped` / `NeedsLogin` 不得误报可用。
`tailscaled.state` 在未授权时也可能已存在，因此不得单独当作已保存身份。RootTools 只在
backend + tailnet IP 已验证后写入无凭据内容的 `authenticated.marker`；用户取消登录时仍可重试认证。

## 7. Userspace Serve 与 Root TUN

首选管理模式：

```text
tailscaled --tun=userspace-networking ...
tailscale serve --bg --tcp=5555 tcp://127.0.0.1:5555
tailscale serve --bg --tcp=8765 tcp://127.0.0.1:8765  # 仅本机 listener 存在时
```

该模式不创建 `tailscale0`、不写 Android 路由、不占 `VpnService`，只暴露固定管理端口。
启用前必须已有 adbd `:5555` listener；MCP `:8765` 不存在时不创建空转发。

Kernel 模式：

```text
tailscaled --tun=tailscale0 ...
```

RootTools 不再新增自定义 table / fwmark。成功判定使用 Tailscale 原生路由结果：
`BackendState=Running`、`tailscale0` 存在，且 `ip route get 100.100.100.100` 实际命中
`tailscale0`。启停时仅额外清理 RootTools 早期版本遗留的 table `1099` / fwmark 规则。
默认 DNS 保持 `--accept-dns=false`，避免 MagicDNS 与 Android VPN DNS 冲突。普通 Internet 路由仍由
Android / Surfboard / Hiddify 等系统 VPN 管理。

## 8. 官方 Tailscale App 迁移安全

如果 `com.tailscale.ipn` 当前拥有 Android VPN：

1. RootTools 先在后台启动并验证 userspace Serve 或 root TUN；
2. **启用、修复、模式切换、开机恢复都不停止官方 App**；
3. 只有当前管理模式已强验证时，UI 才开放单独的“停止官方 Tailscale App”操作；
4. 该操作再次显示确认对话框，并提示先从外部 peer 验证管理链路；
5. 停止后再次确认当前管理模式仍 ready；
6. RootTools 不自动启停 Surfboard / Hiddify，避免依赖第三方私有实现。

这保证“停止官方 App”是一个后置动作，而不是拿当前远程管理链路做赌博。

## 9. Boot persistence

开机常驻是显式 opt-in。

脚本：

- 等待 `sys.boot_completed=1`；
- 有界等待 socket / backend / 模式就绪；
- 恢复用户显式选择且已验证的 userspace Serve 或 root TUN；
- 绝不停止官方 Tailscale、Surfboard、Hiddify 或其它 VPN App；
- 不做无限重试 / 高频轮询；
- 日志有界地写入 runtime 目录，后续可继续增加轮转。

## 10. UI

独立详情页放在「设备」领域，避免继续扩张 legacy `DashboardScreen.kt`。

页面结构：

1. Overview：runtime/version、daemon mode、IP、Android VPN owner；
2. Management relay：优先启用 userspace Serve；
3. Root Overlay：以高风险确认切换 root TUN，可停用 / 修复；
4. Authentication：需要登录时展示“打开 Tailscale 认证”；
5. Runtime：安装 / 更新固定已验证版本；
6. Coexistence：明确显示 Android VPN owner，官方 App 停止保持独立确认；
7. Boot：只允许对当前 ready 模式显式开启；
8. Diagnostics：backend、ADB 5555、Serve 5555/8765、route、socket、VPN active 与最近动作。

## 11. 测试矩阵

JVM：

- tailnet IPv4 `100.64.0.0/10` 边界；
- hostname canonicalization；
- probe parser：missing runtime / needs login / offline cached IP / userspace Serve / kernel TUN / malformed input；
- action policy：runtime missing、needs login、backend offline、mode-specific ready、official VPN owner、boot persistence；
- shell contract：Serve 只映射固定本地端口，新流程不写 table `1099` / 全覆盖 fwmark，不自动 force-stop；
- runtime package metadata/hash contract。

真机：

- Surfboard / Hiddify ON + userspace Serve ON；
- Surfboard / Hiddify ON + root TUN ON；
- 官方 Tailscale App OFF；
- `tailscale0` 存在且 100.x 地址稳定；
- Mac `tailscale ping`；
- Mac `adb connect <root-ip>:5555`；
- Wi-Fi / mobile 切换；
- 屏幕熄灭；
- App force-stop；
- reboot 后 root overlay / Hiddify / ADB 真实恢复。

## 12. 验收条件

功能只有同时满足以下条件才标记完整：

1. 用户不再需要 Termux 手敲 Tailscale daemon 命令；
2. Hiddify 可以保持 Android VPN；
3. Root Tailscale 独立显示 IP / mode / backend / Serve / route / auth 状态；
4. Enable / Disable / Repair 都可从 App 完成；
5. 安装包严格 hash 校验；
6. 任何启用 / 修复 / 启动恢复都不自动停止 VPN App，官方 Tailscale App 只能在 ready 后被单独确认停止；
7. 开机恢复可显式关闭并完全删除 RootTools 自有脚本；
8. Root TCP ADB 能通过 root tailnet IP 真实远程连接；
9. reboot 验收通过后再把该路径作为远程 reboot 的可信前置条件。
