# ADB Control Center 与网络模块

## 0. 2026-08-20 规划结论

当前 `Root ADB` 卡片只解决了 **Root + legacy ADB TCP 5555** 的最小闭环：开启 / 关闭、监听检查、
Tailscale 地址和复制 `adb connect`。对于“把 Root 手机长期作为开发 / 自动化执行节点”这个目标还不够。

本轮参考的截图来自开源项目
[Smooth-E/wireless-adb-switch](https://github.com/Smooth-E/wireless-adb-switch)（WADBS）。它重点解决 Android
原生 Wireless Debugging 的快速开关，并提供 Widget、Quick Settings Tile、启动恢复、连接数据复制以及 KDE
Connect 集成。该项目为 **GPL-3.0**，Root Tools 本轮只吸收产品交互和能力拆分思路，不直接复制其实现代码；
底层实现优先依据 Android AOSP 行为和 Root Tools 自己的 `RootShell / Controller / Audit` 体系独立实现。

最终目标不是把现有页面继续堆开关，而是把它升级成 **ADB Control Center**：

```text
ADB Control Center
├── Root TCP ADB       # fixed 5555, Tailscale / LAN / 5G
├── Native Wireless    # Android 11+ TLS wireless debugging
├── USB Debugging      # status + safe shortcut
├── Connection Data    # endpoint / command / deep link / share
├── Pairing & Devices  # native wireless paired hosts
├── Boot Persistence   # explicit opt-in only
├── Quick Entry        # Quick Tile / Launcher Widget / Automation
└── Safety & Diagnose  # exposure / listener / transport / audit
```

核心原则：**Root TCP ADB 与 Android 原生 Wireless Debugging 是两套不同 transport，不能再用一个“无线 ADB”
概念混在一起。**

---

## 1. 当前能力与缺口

### 1.1 已有能力

- `service.adb.tcp.port=5555`
- restart `adbd`
- `ss -ltn` 检查监听状态
- 显示 Tailscale IPv4
- 复制 `adb connect <tailscale-ip>:5555`
- Root ADB Quick Settings Tile
- Automation `SET_ADB`（只允许确保开启，不允许远程关闭当前链路）
- 关闭前远程失联警告
- Root action audit

### 1.2 主要缺口

| 缺口 | 当前问题 | 目标 |
|---|---|---|
| Transport 不完整 | 只有 legacy TCP 5555 | 同时管理 Root TCP、Native Wireless、USB 状态 |
| 原生无线调试 | 无法查看 / 切换 Android 11+ Wireless Debugging | 显示 TLS port、开关状态、系统支持情况 |
| 连接数据 | 只有 Tailscale 的一条命令 | LAN / Tailscale / Native TLS 多 endpoint + 多格式复制 |
| 开机恢复 | 重启后 5555 不保证恢复 | 显式 opt-in 的 Boot Policy + 状态可验证 |
| Pairing | 只能跳开发者选项 | 至少提供原生配对页快捷入口；高级阶段再管理 paired devices |
| Widget | 只有 Quick Tile | 增加 Launcher Widget，但禁止 1 秒 Root 轮询 |
| 分享 | 只有 Clipboard | Android Sharesheet；KDE Connect 作为可选增强 |
| 安全可见性 | 只提示“默认不常驻” | 明确展示 listener、网络暴露面、当前推荐 endpoint |
| 故障定位 | 开关失败只有通用报错 | 展示 property / listener / transport 三层状态与失败阶段 |

---

## 2. 三类 ADB Transport 必须分开建模

### 2.1 Root TCP ADB / Legacy TCP

这是 Root Tools 当前已有模式：

```text
service.adb.tcp.port=5555
stop adbd
start adbd
```

特点：

- 固定端口，最适合自动化和远程脚本；
- 不依赖 Android 11+ pairing；
- 可配合 Tailscale，在 5G / Wi-Fi 下继续使用；
- 通常监听在多个网络接口上，暴露面比 Native Wireless 大；
- 重启后默认不保证恢复。

产品默认仍以 **Tailscale endpoint** 作为推荐连接方式：

```text
adb connect 100.x.x.x:5555
```

### 2.2 Android Native Wireless Debugging

Android 11+ 的系统 Wireless Debugging 使用 TLS + pairing，并由系统动态分配连接端口。

需要读取的关键状态：

```text
settings get global adb_wifi_enabled
getprop persist.adb.tls_server.enable
getprop service.adb.tls.port
cmd adb is-wifi-supported
cmd adb is-wifi-qr-supported
```

Root Tools 不把 `service.adb.tls.port` 当成配置写入；它是 **系统 / adbd 产生的运行态端口**。

Native Wireless 第一阶段目标：

1. 准确读取系统支持与 ON/OFF；
2. 显示当前 TLS connect port；
3. 组合 Wi-Fi IPv4 得到 `ip:tlsPort`；
4. 快速打开系统 Wireless Debugging / pairing 页面；
5. 经过 Samsung 真机 capability probe 后，再决定使用何种受控方式切换 ON/OFF。

> 不直接假设 `settings put global adb_wifi_enabled 1` 在所有 ROM 都能完整触发 Framework / adbd 状态机。
> 实施前必须先在 SM-S908E 上做“系统 UI 切换前后 property diff + Root 写入验证”。

### 2.3 USB Debugging

USB Debugging 不是本轮重点，但必须进入状态模型，否则 ADB 页面无法解释 `adbd` 为什么仍然活着。

第一阶段只做：

- Developer Options 状态；
- USB Debugging 状态；
- “打开开发者选项”快捷入口；
- 不在首页提供一击关闭 USB Debugging，避免用户调试时误断线。

---

## 3. 目标 UI：从单卡片升级为 ADB Control Center

### 3.1 首页卡片

首页仍然只保留一张 `Root ADB` / `ADB` 工具卡，不增加新的顶层卡片。

建议摘要：

```text
ADB · Remote Ready
Root TCP 5555 · Tailscale 100.91.x.x
Native Wireless ON · TLS 37123
```

状态 Badge：

- `READY`：存在至少一个可连接 endpoint；
- `LOCAL`：只有局域网 endpoint；
- `REMOTE`：Tailscale + Root TCP 可达；
- `OFF`：所有网络 ADB transport 均关闭；
- `WARN`：property 显示开启但没有 listener，或 listener 与端口不一致。

### 3.2 详情页结构

详情页按信息层次拆为 6 个 Section，而不是把所有设置塞进一张卡：

#### A. Overview

```text
Root TCP ADB                 ON
Fixed port                   5555
Native Wireless              ON
TLS port                     37123
USB Debugging                ON
Active network               Wi-Fi + Tailscale
```

Root TCP 和 Native Wireless 各自独立 Switch。

#### B. Connection Endpoints

按照推荐顺序显示：

1. `Tailscale · 100.x.x.x:5555` — 推荐远程连接；
2. `LAN · 192.168.x.x:5555` — 同网段开发；
3. `Native TLS · 192.168.x.x:<dynamic-port>` — Android 原生 Wireless Debugging；
4. 其他有效 IPv4 / IPv6 endpoint 放到“更多”。

每行提供：

- Copy endpoint；
- Copy `adb connect ...`；
- Share；
- 风险标签：`TAILNET / LAN / ALL INTERFACES`。

#### C. Pairing & Trusted Devices

第一阶段：

- “打开系统配对页面”；
- “使用配对码”；
- “扫描二维码”入口跳转系统 UI；
- 显示当前 Native TLS connect endpoint。

第二阶段仅在 capability probe 证明安全可行后实现：

- paired device 列表；
- fingerprint / device name；
- forget / unpair；
- pairing result 状态。

Android 的 paired-device / pairing API 属于 hidden / system ADB API，Root Tools 不为了“看起来完整”而直接引入不稳定
反射。先验证 Samsung / AOSP binder 权限链路，再决定是否实现 typed bridge。

#### D. Startup & Persistence

参照截图中的“启动时启用”，但 Root Tools 必须做成明确策略，而不是一个语义不清的 Boolean：

```text
Boot policy
○ Disabled                         # 默认
○ Restore Root TCP 5555
○ Restore Native Wireless
○ Restore last explicit state      # 后续可选
```

默认 **Disabled**。

启动恢复必须满足：

- 用户显式 opt-in；
- 记录最近一次恢复成功 / 失败时间；
- 不在 boot 后无限 retry；
- 不创建 1 秒 / 5 秒常驻 root polling；
- Native Wireless 需要等待 Wi-Fi 条件满足；
- Root TCP 启动后必须再次验证 property + listener；
- 失败不静默吞掉，在 ADB 页面显示原因。

实现优先级：

1. `BOOT_COMPLETED / USER_UNLOCKED` 后的轻量 restore；
2. Samsung 真机验证重启后远程可恢复；
3. 如果 App Receiver 在 One UI 上可靠性不足，再评估 **可卸载的 Magisk `service.d` persistence**；
4. Magisk persistence 只作为 Advanced 选项，不作为默认实现。

只有完成第 2 步，“远程 reboot / recovery”类高风险动作才具备继续开放的前提。

#### E. Quick Entry

保留已有 Root ADB Quick Settings Tile，并新增 Launcher Widget：

```text
1x1 Widget
  ADB icon + ON/OFF/ERROR

2x1 Widget
  Root TCP 5555
  100.x.x.x
  [Enable] [Copy]
```

Widget 刷新策略 **不照搬截图里的 1 秒轮询**。

Root Tools 采用：

- action 成功后主动刷新；
- 网络切换事件后刷新；
- boot restore 后刷新；
- App 打开时读取实时状态；
- 如需要 watchdog，最低 30 秒且不执行重型 dumpsys。

这样可以避免 Widget 自己变成新的 `su` / shell busy-loop，也避免再次出现 Magisk 授权提示频繁弹出的历史问题。

#### F. Safety & Diagnose

至少展示：

- `service.adb.tcp.port`
- Root TCP listener 是否存在
- `adb_wifi_enabled`
- `service.adb.tls.port`
- USB Debugging 状态
- Tailscale IPv4
- Wi-Fi IPv4
- active transport
- 最近一次 ADB action + result

出现以下不一致时显示 `WARN`：

```text
tcp.port > 0 but no listener
tls enabled but tls port invalid
listener exists but state says OFF
boot restore configured but last restore failed
```

---

## 4. 从参考应用吸收什么，不吸收什么

截图中的 WADBS 设置可以映射为 Root Tools 的以下能力：

| 参考应用 | Root Tools 设计 | 处理方式 |
|---|---|---|
| 无线调试主开关 | Native Wireless Switch | 增加，但与 Root TCP Switch 分离 |
| 启动时启用 | Boot Policy | 增强为多策略，默认关闭 |
| Widget updates | Widget event refresh | 增加，但不做高频 root polling |
| Widget interval 1s | 可选 watchdog | 不照搬 1s；默认事件驱动 |
| 复制连接数据 | Auto-copy on successful enable | 增加，默认关闭 |
| 连接数据前缀 | Copy / Deep Link Template | 增加预设 + Advanced 自定义 |
| KDE Connect 集成 | Share Provider / KDE optional adapter | 可选，不作为核心依赖 |
| Quick Settings Tile | 已有 Root ADB Tile | 保留并统一走 Controller |
| Launcher widgets | 1x1 / 2x1 ADB Widget | 新增 |

连接数据格式建议提供 4 个 preset：

```text
Endpoint
100.91.x.x:5555

ADB command
adb connect 100.91.x.x:5555

Root Tools deep link
roottools://adb/connect?host=100.91.x.x&port=5555&transport=root-tcp

Custom prefix (Advanced)
<prefix><endpoint>
```

`Auto-copy` 默认 OFF；只有从 OFF -> ON 且最终 listener 验证成功后才自动写剪贴板，不能在后台采样时反复覆盖用户剪贴板。

---

## 5. 数据模型

不要继续向 `DeviceSnapshot` 平铺越来越多 ADB 字段。新增领域 Snapshot：

```kotlin
data class AdbSnapshot(
    val developerOptionsEnabled: Boolean,
    val usbDebuggingEnabled: Boolean,
    val rootTcp: RootTcpAdbState,
    val nativeWireless: NativeWirelessAdbState,
    val endpoints: List<AdbEndpoint>,
    val bootPolicy: AdbBootPolicy,
    val lastRestore: AdbRestoreResult?,
    val lastAction: AdbActionResult?,
)

data class RootTcpAdbState(
    val configuredPort: Int?,
    val listening: Boolean,
)

data class NativeWirelessAdbState(
    val supported: Boolean,
    val qrSupported: Boolean,
    val enabled: Boolean,
    val tlsPort: Int?,
)

data class AdbEndpoint(
    val transport: AdbTransport,
    val network: AdbNetwork,
    val host: String,
    val port: Int,
    val recommended: Boolean,
    val exposure: AdbExposure,
)
```

网络地址继续由 `NetworkRepository` 负责，`AdbRepository` 不重复执行一套 `ip addr / dumpsys connectivity`。

---

## 6. Controller / Repository 收口

架构文档已经约定写操作必须进入 Controller，但当前 `setAdbTcpEnabled()` 仍在 `DeviceRepository`。
ADB Control Center 实施时顺便完成这一次收口：

```text
AdbStateCollector     # only read ADB state
       ↓
AdbRepository         # combine ADB + NetworkSnapshot + preferences
       ↓
AdbViewModel
       ↓
ADB Compose UI

UI / Tile / Widget / Automation
       ↓
AdbController         # single write truth source
       ↓
RootShell / Android system service bridge
       ↓
RootActionAuditStore
```

`AdbController` 只暴露 typed action：

```text
setRootTcpEnabled(enabled, port)
setNativeWirelessEnabled(enabled)
restartAdbd()
restoreBootPolicy()
startNativePairing()          # capability proven 后
forgetPairedDevice(id)        # capability proven 后
```

禁止：

- UI 直接拼 root shell；
- Widget 自己启动 `su`；
- Tile 自己改 property；
- Automation 接收任意 shell 字符串；
- Native Wireless 直接写动态 TLS port；
- 同一 action 因 UI 重组重复执行。

迁移策略保持小改动：

1. 新增 `AdbController`；
2. 先让现有 `DeviceRepository.setAdbTcpEnabled()` 内部委托 Controller，保持调用方兼容；
3. ADB 页面迁移到 `AdbSnapshot`；
4. Tile / Automation 迁移；
5. 最后删除旧写入口。

不新增 Gradle module，继续保持单 `app` module。

---

## 7. Native Wireless capability probe

实现 Native Wireless 写操作前，先在 Samsung SM-S908E 做一次只读 / 可回滚实验，结果写回本文件。

### 7.1 Baseline

```bash
adb shell settings get global development_settings_enabled
adb shell settings get global adb_enabled
adb shell settings get global adb_wifi_enabled
adb shell getprop persist.adb.tls_server.enable
adb shell getprop service.adb.tls.port
adb shell cmd adb is-wifi-supported
adb shell cmd adb is-wifi-qr-supported
adb shell su -c 'ss -ltn'
```

### 7.2 系统 UI 对照

手工在 Developer Options 中执行：

```text
Wireless Debugging OFF
→ 采集 baseline
Wireless Debugging ON
→ 采集 baseline
```

确认 Samsung One UI 实际使用哪些 Global Setting / property，确认 TLS port 是否动态变化。

### 7.3 Root 写操作实验

按最小侵入顺序验证：

1. Root shell 修改官方 Global Setting 是否会完整触发状态变化；
2. 如果不完整，研究 AOSP `IAdbManager / AdbManager` system service bridge；
3. 如果需要 hidden API，只做最窄 typed bridge，不引入完整 hidden-api 依赖树；
4. 如果 One UI 行为不稳定，Native Wireless 保持“状态 + 系统快捷入口”，Root TCP 继续承担可靠远程连接。

成功标准不是“Switch 能变蓝”，而是：

```text
state enabled
AND tls port valid
AND listener exists
AND paired host can adb connect
```

---

## 8. Boot Persistence 设计

### 8.1 默认策略

```text
BootPolicy.DISABLED
```

不因为用户上一次手工打开 ADB 就自动永久化。

### 8.2 App restore

用户选择 `Restore Root TCP 5555` 后：

```text
BOOT_COMPLETED / USER_UNLOCKED
→ read persisted policy
→ wait bounded delay
→ AdbController.restoreBootPolicy()
→ verify property + listener
→ record result
→ update Widget / Tile
```

约束：

- bounded retry，不无限循环；
- 每次 retry 复用同一 App 级 `RootShell` session；
- 不唤起 Activity；
- 不弹 Toast；
- 失败通过页面状态 / notification（可选）呈现；
- 没有 opt-in 时 Boot Receiver 只做 O(1) preference check 后退出。

### 8.3 Advanced Magisk persistence

仅当 Samsung 真机证明 App restore 不足够可靠时，才实现：

```text
/data/adb/service.d/roottools-adb.sh
```

必须同时提供：

- Install；
- Verify；
- Disable；
- Remove；
- 恢复前值 / rollback；
- 文件内容版本号；
- RootActionAudit；
- 页面明确标识“Advanced / boot persistent”。

这一阶段不能与普通 App restore 同时生效，避免两套机制重复 restart `adbd`。

---

## 9. 安全策略

### 9.1 默认不永久开放

仍维持现有原则：

- 默认不在 boot 后自动开启 5555；
- 不把公网 IP 作为推荐 endpoint；
- 远程优先 Tailscale；
- 不静默关闭当前管理链路。

### 9.2 Exposure 显示

Root TCP 卡片必须明确告诉用户：

```text
Recommended endpoint: Tailscale
Listener: 0.0.0.0:5555 / [::]:5555 (if detected)
Exposure: Multiple interfaces
```

后续如果增加 firewall scope，必须作为单独 Milestone 验证，不与本轮 ADB 基础能力混在一起。

### 9.3 关闭保护

当 Root Tools 判断当前远程管理主要依赖 Root TCP 时：

- App 页面关闭：二次确认；
- Quick Tile：ON 状态点击不关闭；
- Automation：拒绝 `SET_ADB(false)`；
- Widget：默认只提供 Enable / Copy，不提供一击 Disable。

### 9.4 剪贴板保护

- Auto-copy 默认关闭；
- 后台刷新绝不写 clipboard；
- 地址变化不自动覆盖 clipboard；
- 只在用户 action 成功后执行一次。

---

## 10. 网络看板继续保持独立真值源

当前网络诊断已覆盖：

- 当前默认网络：Wi-Fi / Cellular
- 移动网络 RAT
- Tailscale `tun0`
- local IP / Tailscale IP
- DNS
- routes
- listening TCP ports
- ping tailnet peers
- ADB round-trip latency：由 Mac 端 `scripts/validate-samsung.sh` 连续执行 3 次 `adb shell true` 计算 min/avg/max；不由手机自测

ADB Control Center 只消费上述网络数据，不再新增第二套网络采集 loop。

---

## 11. Quick Tile 规则

现有 Root ADB Tile 保留：

- OFF：单击“确保 Root TCP 5555 开启”；
- ON：再次单击不关闭，避免远程误触断连；
- 状态显示 endpoint / Tailscale IP；
- 所有写操作统一调用 `AdbController`。

暂不增加第二个 Native Wireless Tile。原因：ADB 页面已经提供双 transport 控制，系统本身也有 Wireless Debugging
开发者入口，再增加 Tile 会让通知栏变得冗余。真机使用一段时间后，如果 Native Wireless 高频使用，再单独评估。

---

## 12. Milestone F — Network Diagnostics（已完成）

### F1. NetworkSnapshot

当前只读采集：

- interface name / IPv4 / prefix
- default route / gateway / interface
- Tailscale `tun0` IPv4
- DNS properties
- 当前 active network transport（Cellular / Wi-Fi / VPN）
- mobile network type（设备能读取时）
- TCP listening ports
- ADB port / listening state

### F2. 采样策略

网络诊断只在以下场景执行：

- 打开“网络诊断”页
- 用户点击刷新
- 用户点击单次连通性测试

不注册定时 ping，不做公网 keep-alive。

### F3. 单次测试

- DNS resolve（只展示系统 DNS 配置）
- TCP listen 检查
- 单次 `ping -c 3`，目标必须通过 hostname / IPv4 校验

默认不自动向固定公网地址发包。

### F4. UI

```text
5G · Tailscale ON
100.91.x.x
ADB 5555 listening
```

详情页：

1. Connectivity Overview
2. Interfaces
3. Route / DNS
4. Listening Ports
5. Manual connectivity test

### F5. 验收

- `tun0` / `rmnet_data0` / `wlan0` 与 `ip -4 -o addr` 对照
- default route 与 `ip route` 对照
- ADB 5555 监听与 `ss -ltn` 对照
- 页面退出后没有 ping / ss / dumpsys 循环

---

## 13. Milestone K — ADB Control Center 实施拆解

### K0. Research / Design

- [x] 识别参考应用 WADBS 与许可证边界
- [x] 对照截图拆解 Boot / Widget / Copy / Prefix / KDE Connect 能力
- [x] 审计 Root Tools 当前 Root ADB / Tile / Automation 实现
- [x] 明确 Root TCP 与 Native Wireless 双 transport 架构

### K1. Domain / Controller

- [x] `AdbSnapshot / AdbEndpoint / AdbBootPolicy`
- [x] `AdbStateParser / AdbRepository`
- [x] 新增唯一写入口 `AdbController`
- [x] 删除 `DeviceRepository.setAdbTcpEnabled()`，Root TCP 写操作迁移到 Controller
- [x] Root ADB Tile / Wireless ADB Tile / Widget / Automation 统一调用 Controller
- [x] ADB action audit 覆盖 Root TCP 与 Native Wireless 成功 / 失败结果
- [x] “确保开启”具备 no-op：已经监听时不会为了刷新状态重复 restart adbd

### K2. ADB Control Center UI

- [x] Overview：Root TCP / Native Wireless / USB 三类状态分离
- [x] Root TCP 独立开关 + 关闭远程链路二次确认
- [x] Native Wireless 独立开关
- [x] USB Debugging 状态
- [x] Connection Endpoints：Tailscale / LAN / Native TLS
- [x] Copy `adb connect` command
- [x] Android Share Sheet
- [x] Safety / Diagnose 状态卡
- [x] **Auto-copy 不落地**：改为显式 Copy / Share，避免后台剪贴板副作用和系统 clipboard 提示
- [x] **自定义连接前缀不落地**：当前没有 Root Tools consumer；保留标准 `adb connect` 作为可互操作协议

### K3. Native Wireless

- [x] Samsung SM-S908E baseline / property diff
- [x] `cmd adb is-wifi-supported` / QR capability
- [x] `settings global adb_wifi_enabled` 作为 Native Wireless 状态真值
- [x] Native TLS port 通过 **adbd-owned listening socket** 探测，不依赖三星为空的 `service.adb.tls.port`
- [x] 系统 pairing 入口采用 Developer Options fallback；One UI 14 未暴露可解析的 `android.settings.WIRELESS_DEBUGGING_SETTINGS`
- [x] typed ON/OFF 实现并在 Samsung 真机通过 Automation API 做 1 → 0 → 1 回滚验证
- [x] trusted hosts 只读取 `/data/misc/adb/adb_keys` 的 comment，不把公钥暴露到 UI
- [x] unpair 不直接改 `/data/misc/adb/*`；统一回退系统设置，避免误删当前远程管理 key

### K4. Boot Persistence

- [x] `AdbBootPolicy` preference，默认关闭
- [x] `BOOT_COMPLETED / USER_UNLOCKED` restore
- [x] 失败使用一次性 Alarm 做 bounded retry，不注册周期任务
- [ ] reboot 后 Root TCP 5555 自动恢复真机验收
- [ ] reboot 后 Tailscale + ADB 远程重连验收
- [x] App restore 不可靠前不引入 Magisk `service.d`；真实 reboot 验收后再决定是否需要

### K5. Quick Entry / Widget

- [x] 2x1 Launcher Widget：状态 + 一键确保 Root TCP 开启 + 点击进入 Control Center
- [x] action event-driven refresh；`updatePeriodMillis=0`
- [x] Root ADB Quick Tile 保持“只确保开启”安全语义
- [x] 新增 Wireless ADB Quick Tile，可切换 Android 原生无线调试
- [x] 确认没有 1s root polling；Widget 没有实例时不会主动采集
- [x] KDE Connect adapter 当前不引入：Android Share Sheet 已覆盖跨应用发送，避免新增硬依赖

### K6. Regression / Acceptance

- [x] `assembleDebug`
- [x] `lintDebug`
- [x] `AdbStateParserTest`：legacy/native port 分离 + endpoint 生成
- [x] Samsung SM-S908E 页面层级与真机状态验收：Root TCP + Native Wireless + USB 均正确显示
- [x] Root TCP **ON** 与 `getprop service.adb.tcp.port + ss -ltnp` 对照一致；OFF 暂不主动执行，避免切断当前远程链路
- [x] Native Wireless 1 → 0 → 1 与 `settings global adb_wifi_enabled` 对照一致
- [x] Native TLS 端口重开后从 `42387` 变化为 `43885`，页面能按 adbd socket 动态识别
- [x] 不做自动 clipboard 写入
- [x] Root Tools 退到后台后 Samsung `top` 单次检查为 0.0% CPU；没有 ADB / Widget shell busy-loop
- [ ] reboot + post-boot reconnect 属于破坏当前会话的验收，等待明确允许重启后执行

---

## 14. 完成定义

ADB Control Center 只有同时满足以下条件才算完成：

1. Root TCP 与 Native Wireless 在 UI / Model / Controller 中完全分离；
2. Root TCP 5555 与 `getprop + ss` 对照一致；
3. Native Wireless 与 One UI 系统设置页状态一致；
4. 至少能生成 Tailscale / LAN / Native TLS 的有效连接数据；
5. Copy / Share / Widget 不产生后台副作用；不做后台 auto-copy；
6. Boot Policy 默认关闭，打开后可以解释恢复结果；
7. 重启后 Tailscale + Root ADB 能完成一次真实远程重连，才解除“remote reboot”前置阻塞；
8. 所有写动作经过 `AdbController + RootActionAuditStore`；
9. 不新增 Gradle module，不复制 GPL-3.0 源码，不引入与目标无关的大依赖；
10. Samsung SM-S908E 完成功能、页面状态与后台开销验收；真实 reboot reconnect 单独作为高风险验收门槛。

---

## 15. 2026-08-20 Samsung 实机基线

本轮在 `SM-S908E / Android 14` 上读取到：

```text
Root TCP:          5555 / listening
Tailscale:         100.91.126.56
Local Wi-Fi:       10.1.1.193 / wlan0
Wireless support:  true
Wireless QR:       true
adb_wifi_enabled:  1
Native TLS port:   42387 -> disable/enable -> 43885
USB ADB:           active
```

关键结论：

1. One UI 14 上 `service.adb.tls.port` 为空，不能用它作为 Native Wireless port 真值；
2. `adbd` 会同时监听 Root TCP 5555 和一个动态 Native TLS port，因此 Collector 按 owner=`adbd` 识别并排除 legacy port；
3. Native Wireless disable / enable 不需要重启整个 `adbd`，因此不会主动影响现有 5555 远程链路；
4. Root TCP 的“确保开启”如果当前已经监听 5555 必须直接 no-op，禁止无意义 restart adbd；
5. 当前已授权 Host 只展示 comment（例如设备名），UI 和报告都不保存/显示原始 ADB key。
