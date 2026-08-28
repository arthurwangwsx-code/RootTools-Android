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
        ↓ root Linux TUN
    tailscale0 / 100.64.0.0/10
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
4. 切换到 root kernel TUN 模式；
5. 安装仅面向 `100.64.0.0/10` 的受控路由规则；
6. 在 root overlay 已经验证成功后，允许停止官方 Tailscale Android App，释放 `VpnService` 槽位；
7. 可选写入 RootTools 自己拥有的 Magisk `service.d` 启动脚本；
8. 提供停用、修复、状态、审计和明确的回滚信息。

RootTools **不**：

- 修改 Hiddify 私有配置 / shared preferences；
- 自动填写第三方账号密码；
- 从 UI 接受任意 shell、URL 或安装路径；
- 在 root overlay 尚未可用时主动停止官方 Tailscale App；
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
    └── tailscaled.state
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
                                      │ enable
                                      ▼
                                  ROOT_TUN
                                      │
                    optional stop official Android Tailscale
                                      │
                                      ▼
                              VPN slot free for Hiddify
```

UI 不用“VPN 开关”描述 Root Tailscale，因为它不是 Android `VpnService`。统一叫：

- Root Tailscale；
- Root Overlay；
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

回到 App 后自动刷新；一旦拿到合法 `100.64.0.0/10` 地址即可启用 root TUN。

## 7. Root TUN 与路由

Kernel 模式：

```text
tailscaled --tun=tailscale0 ...
```

RootTools 只为 tailnet IPv4 添加受控策略：

```text
100.64.0.0/10 -> tailscale0
table 1099 100.64.0.0/10 -> tailscale0
fwmark 1099 -> lookup 1099
```

规则必须幂等；停用时只删除 RootTools 自己的 `1099` 规则。默认 DNS 保持 `--accept-dns=false`，避免
MagicDNS 与 Hiddify DNS 冲突。普通 Internet 默认路由仍由 Android / Hiddify 管理。

## 8. 官方 Tailscale App 迁移安全

如果 `com.tailscale.ipn` 当前拥有 Android VPN：

1. RootTools 可以先在后台启动 root overlay；
2. 必须验证 `tailscale0` + 合法 tailnet IPv4；
3. **只有验证成功后**，才允许通过 `PrivilegeRouter.forceStop("com.tailscale.ipn")` 停止官方 App；
4. 停止后再次确认 root overlay 仍存在；
5. RootTools 不自动启动 Hiddify，避免依赖第三方私有实现。

这保证“停止官方 App”是一个后置动作，而不是拿当前远程管理链路做赌博。

## 9. Boot persistence

开机常驻是显式 opt-in。

脚本：

- 等待 `sys.boot_completed=1`；
- 最多 30 秒等待 socket / `tailscale0`；
- 有合法 tailnet IPv4 才认为成功；
- 成功后才停止官方 Tailscale App；
- 不做无限重试 / 高频轮询；
- 日志有界地写入 runtime 目录，后续可继续增加轮转。

## 10. UI

独立详情页放在「设备」领域，避免继续扩张 legacy `DashboardScreen.kt`。

页面结构：

1. Overview：runtime/version、daemon mode、IP、Android VPN owner；
2. Root Overlay：启用 / 停用 / 修复；
3. Authentication：需要登录时展示“打开 Tailscale 认证”；
4. Runtime：安装 / 更新固定已验证版本；
5. Coexistence：明确显示 Hiddify / 其它 VPN 是否可以继续占用 Android VPN；
6. Boot：显式开关 + 风险说明；
7. Diagnostics：ADB 5555、route、socket、最近动作结果。

## 11. 测试矩阵

JVM：

- tailnet IPv4 `100.64.0.0/10` 边界；
- hostname canonicalization；
- probe parser：missing runtime / needs login / userspace / kernel TUN / malformed input；
- action policy：runtime missing、needs login、ready、official VPN owner、boot persistence；
- runtime package metadata/hash contract。

真机：

- Hiddify ON + Root Tailscale ON；
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
3. Root Tailscale 独立显示 IP / mode / route / auth 状态；
4. Enable / Disable / Repair 都可从 App 完成；
5. 安装包严格 hash 校验；
6. 官方 Tailscale App 的停止动作只发生在 root overlay verified 之后；
7. 开机恢复可显式关闭并完全删除 RootTools 自有脚本；
8. Root TCP ADB 能通过 root tailnet IP 真实远程连接；
9. reboot 验收通过后再把该路径作为远程 reboot 的可信前置条件。

