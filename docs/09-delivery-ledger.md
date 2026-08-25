# Root Tools 交付账本

## 当前状态

更新日期：2026-08-22

### 已完成

- [x] 单 app 工程初始化
- [x] Compose 深色工具箱视觉
- [x] 卡片式首页
- [x] 权限中心
- [x] 自动通知权限申请
- [x] 前台触发 Magisk Root 授权
- [x] 性能控制详情页
- [x] Auto / Cool / Performance
- [x] Thermal 安全限制
- [x] Root ADB TCP 5555
- [x] Tailscale IPv4 展示
- [x] CPU Quick Tile
- [x] Root ADB Quick Tile
- [x] Samsung SM-S908E 真机安装与启动验证
- [x] Dashboard 详情 1 / 2 / 5 秒采样切换（默认 2 秒）
- [x] 24h 轻量历史持久化（5 分钟 / 点，最多 288 点）
- [x] 24h 历史 codec 单元测试

### 2026-08-20 — Root session / Magisk 授权提示收口

- [x] `RootShell` 从每条命令 `su -c` 改为 App 进程级共享持久 `su` session
- [x] 所有现有 Repository / Controller / Service / Tile 自动复用同一 session，不增加第二套 root daemon
- [x] 每条命令使用独立 subshell + completion marker，保持 exit code 与多行输出语义
- [x] timeout / cancellation / transport failure 才销毁 session；失败命令不触发无意义重建
- [x] 写操作不自动 retry，避免部分执行后的重复副作用
- [x] JVM 定向测试覆盖：连续命令单 session、非零 exit 保活、timeout 后安全重建
- [x] Activity 后台时暂停 Dashboard sampler；CPU policy 守护继续使用独立低频 Service
- [x] Samsung 真机后台连续 2 分钟：13 次 / 10 秒采样中 App PID 始终 `29246`、唯一 `su` PID 始终 `29290`，覆盖 4+ 个 30 秒周期且无 root session churn；结合持久 session 机制关闭周期性 Magisk grant toast 根因

### 2026-08-22 — Shadow Display / AI 后台虚拟页面

- [x] 首页新增「影子屏」卡片与独立 Compose 页面，支持状态、分辨率/DPI、App launch、tap/swipe/text、按需 Preview 与 Stop 确认
- [x] 生命周期通过 typed `ShadowDisplayController -> PrivilegeRouter -> RootShell`，UI 不公开任意 privileged shell
- [x] 新增 root `app_process` `ShadowDisplayDaemon`，在手机端持有单个 Trusted Virtual Display；不依赖电脑 scrcpy 维持生命周期
- [x] 默认 720×1600 / 320dpi；Android 15 使用 trusted / own-focus / always-unlocked 等 secondary-display flags
- [x] `input -d <displayId>` 仅用于 display-scoped tap/swipe/text；明确不暴露 HOME / RECENTS，规避 HyperOS 全局导航行为
- [x] Preview 使用 daemon-owned `ImageReader` 按需 JPEG；不绕过 `FLAG_SECURE` / DRM 安全限制
- [x] start / stop / launch-package 写入 `RootActionAuditStore`，记录 before/after/backend/rollback hint
- [x] daemon Stop 先校验 PID 与 `/proc/<pid>/cmdline` 的 ShadowDisplayDaemon 身份，避免 stale PID / PID reuse 误杀其他 root 进程
- [x] 文本输入拒绝 NUL / newline / ISO control，package / config / coordinate / duration 全部经 typed validator/policy
- [x] JVM pure tests 覆盖 config 边界、坐标、文本控制字符、package 注入和 stale/running status parser
- [x] `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 全部通过；`security_guard.py` PASS；`assembleRelease` + R8/keep rule 通过
- [x] Xiaomi 14 `23127PN0CC` Android 15 / HyperOS 2：Root Tools 手机端成功创建 Display 44/45/46；Google Maps 在 secondary display Resumed，Display 0 同时保持 Root Tools
- [x] Xiaomi 14：display-scoped tap 后 Display 0 仍保持 Root Tools；按需 Preview 成功生成并在页面展示
- [x] Xiaomi 14：Root Tools App force-stop 后 Display 45 与 root daemon 仍存活，daemon `PPID=1`；重新打开 Root Tools 可识别 Display 45 并安全 Stop
- [x] Xiaomi 14：Stop 后只剩 Display 0，审计记录确认 `start -> launch-package -> stop` 均成功
- [x] Xiaomi 14：swipe 经 Display 0 / Display 48 前后快照确认未抢主屏；text 已完成 Root Tools 字段输入 `KLCC` 与发送触发，但期间物理主屏存在并发操作，因此不把该次结果作为严格隔离证明
- [x] 最新 PID-reuse 安全硬化 APK 已通过标准 `adb install -r` 覆盖安装；最终版再次成功创建 Display 47，daemon `PPID=1`，随后从 Root Tools 正常 Stop，仅剩 Display 0

### 2026-08-24 — 广告治理 / GKD 真机收口

- [x] 新增 `ToolId.AD_GOVERNANCE` 与独立广告治理页面；feature body 保持在 `feature/adgovernance`，旧 `DashboardScreen.kt` 只增加最小 route/status glue
- [x] 新增 read-only `AdGovernanceRepository -> AdGovernanceProbeParser -> RootShell`，不引入任意 shell UI 入口，不修改 hosts / VPN / Magisk policy / HyperOS package
- [x] 汇总 GKD running / automation mode / Shizuku worker / subscription、AdAway、hosts、Tailnet、HyperOS 广告组件状态
- [x] GKD action log 解析只把实际 `addActionLog` 成功记录计入真机触发；知乎与京东分别显示最近规则类别
- [x] Xiaomi 14 真机页面确认：GKD `正常` / `Shizuku 自动化` / 1 订阅；知乎 1 次、京东 5 次；hosts 2 行未启用；Tailnet `100.110.5.86`
- [x] 修正 100.64/10 CGNAT 误判：不再把 `rmnet_data1=100.87.248.227` 当 Tailscale，只接受 `tun*` 上的 tailnet IPv4
- [x] GKD 为 UI dump 临时停止后已恢复运行；`li.songe.gkd:shizuku-user-service` 与 `shizuku_server` 均在线，日志确认自动化与 Shizuku 重新连接
- [x] AdAway VPN 继续保持关闭，避免与当前 Tailscale 远程管理链路竞争 Android VPNService；HyperOS 系统广告包保持只读不卸载
- [x] parser 定向 JVM tests + `security_guard.py` + `testDebugUnitTest / lintDebug / assembleDebug` 通过
- [ ] Repository `quality_guard.py` 被既有 Developer Runtime 两项 debt 阻塞，需在独立工程治理任务中修复，不混入广告治理提交

### Next — I Tool Registry

- [x] `ToolId / ToolCategory / Capability`
- [x] unified `ToolRegistry`
- [x] dynamic status resolver
- [x] 首页从手写 card list 迁移到 registry

### Next — J Shizuku / Sui Privilege Bridge

- [x] 完成现状审计：当前工程仅在架构文档预留 `SHIZUKU`，代码尚未接入 API / Provider / Binder / permission
- [x] 输出 `13-shizuku-sui-bridge.md`，明确 RootShell 与 Shizuku/Sui 的职责边界
- [x] 确定不新增 Gradle module，只在现有 `app` 内增加 privilege package
- [x] 确定优先落地：状态 / 授权 / backend UID / self-test → App Governance typed gateway → Component Manager
- [x] Shizuku API / provider 13.1.5
- [x] Binder lifecycle + permission request + UID/backend/Sui detection
- [x] `ToolCapability.SHIZUKU` / `FRAMEWORK_PRIVILEGE` + `ToolId.SHIZUKU` / `COMPONENTS`
- [x] Shizuku / Sui detail page + Manager / Wireless Debugging 入口
- [x] typed UserService AIDL，不公开任意 privileged shell API
- [x] Package / Activity / AppOps `PrivilegeRouter`
- [x] Router 只在 Shizuku/Sui route 失败后 probe Root fallback，避免无意义 Magisk toast
- [x] `PackagePolicyController` 与 Automation 复用统一 Router/Controller
- [x] Component Manager + BOOT / FGS / exported / disabled filter
- [x] Package / Component 安全策略 + before/after/backend audit
- [x] Non-root Framework catalog + Root-only capability 灰显
- [x] JVM test：routing / UID mode / validator / package mutation / component safety / self-test parser
- [x] Samsung 已识别 Shizuku Manager 13.5.4，server root UID，Root Tools permission granted，首页 `Shizuku Root · UID 0 · Ready`
- [ ] Samsung 运行最新版 typed UserService self-test
- [ ] Samsung 对普通用户 App 执行 1 个 component disable → refresh → enable rollback
- [ ] 无 Root + Shizuku ADB 真机专项验收
- [ ] Sui Root 真机验收

### Next — K ADB Control Center

- [x] 识别截图对应的开源参考项目 WADBS，并记录 GPL-3.0 边界：只参考产品设计，不直接复制实现
- [x] 审计当前 `DeviceRepository / DashboardScreen / AdbTileService / Automation` 的 Root ADB 实现
- [x] 明确 Root TCP 5555 / Android Native Wireless / USB Debugging 三类 transport / status 分离
- [x] 输出完整实施方案到 `04-adb-network.md`
- [x] `AdbSnapshot / AdbEndpoint / AdbBootPolicy`
- [x] `AdbStateParser / AdbRepository / AdbController`
- [x] 现有 Root TCP 写操作从 `DeviceRepository` 迁移到唯一 Controller
- [x] ADB Control Center：Root TCP / Native Wireless / USB 三 transport UI
- [x] Tailscale / LAN / Native TLS endpoint 列表
- [x] Copy `adb connect` + Share Sheet；auto-copy / 自定义 prefix 经评估不引入
- [x] Native Wireless Samsung capability probe + adbd-owned TLS port 状态
- [x] 系统 pairing 使用 Developer Options fallback
- [x] trusted host comment 只读；unpair 保持系统 UI fallback，避免误删当前管理 key
- [x] Boot Policy + `BOOT_COMPLETED / USER_UNLOCKED` + bounded restore retry
- [ ] reboot 后 Root TCP 5555 自动恢复
- [ ] reboot 后 Tailscale + ADB 真实远程重连
- [x] 2x1 Launcher Widget，`updatePeriodMillis=0`，事件驱动刷新
- [x] Root ADB Quick Tile + 新增 Wireless ADB Quick Tile
- [x] `SET_NATIVE_ADB` token-protected Automation API
- [x] Widget / Boot restore 无 1 秒 Root polling
- [x] `testDebugUnitTest + assembleDebug + lintDebug`
- [x] Samsung SM-S908E：Root TCP 5555、Native Wireless 1→0→1、动态 TLS port、USB ADB、Tailscale/LAN endpoint 实机对照
- [x] Root Tools 后台单次 `top` 检查 0.0% CPU
- [ ] 最终 reboot reconnect 验收等待明确允许重启设备后执行

### Next — L App Control Center

- [x] 将 `references/AppManager` 克隆到工程本地参考目录并由 `.gitignore` 排除
- [x] 记录 App Manager 参考 commit / GPL 边界到 `docs/reference-projects.md`
- [x] 根据用户截图完成能力逆向拆解：App 列表、详情、多组件、AppOps、权限、共享库、批量操作、一键策略、Debloat
- [x] 对照 App Manager / Hail / Shizuku 官方资料完成行业调研
- [x] 输出 `15-app-control-center.md` 详细产品、架构、风险、性能、测试与分阶段计划
- [x] 确定升级现有 `ToolId.APPS`，不新增重复的“应用管理”首页卡片
- [x] 确定保持单 `app` Gradle module，不为 App Manager 再拆 Android library module
- [x] 确定复用 `PackagePolicyController / StartupRepository / DiagnosticsRepository / RootActionAuditStore`
- [x] 确定 component / AppOps / permission 等 Framework 操作接入 Shizuku/Sui typed gateway
- [x] 确定 tracker 数据库进入工程前必须单独做 license review，不复制 App Manager 规则/源码
- [x] App inventory + search/filter/sort 核心链路
- [x] App Detail 核心页：Overview / Components / Permissions / AppOps + 单应用诊断导出
- [x] Activity / Service / Receiver / Provider Component Manager：搜索/过滤、Activity launch、typed enable/disable、写后校验
- [x] AppOps + Runtime Permission：基础读取、AppOps typed write、dangerous permission grant/revoke、Special Access 系统设置入口
- [x] Samsung SM-S908E 安装并完成 App Control 只读冒烟：首页卡片与真实 package Detail / SDK / UID / path / flag 信息可显示
- [ ] Samsung App Control 写操作回滚抽样：必须使用可牺牲测试 App，不对 SystemUI / 导航等系统 package 做实验性写入
- [ ] Runtime / Startup 聚合
- [ ] Batch `ActionPlan` / diff / verify / rollback
- [ ] App Policy Profile
- [ ] APK export / policy backup
- [ ] Debloater + restore
- [ ] Tracker / Library Scanner
- [ ] Samsung SM-S908E 代表 App 真机回滚验收

### Next — M Root Industry Gap Expansion

- [x] 输出 `16-industry-root-capability-map.md`
- [x] 完成 Root 能力分层：Linux/Kernel、Framework、Root Runtime、App Private Data、Boot/Partition、Automation
- [x] 完成 App Manager / Hail / Shizuku / Magisk / KernelSU / APatch / MMRL / AFWall+ / Neo Backup / ACC / AdAway 调研
- [x] 明确后续 4 个主要一级缺口：Backup & Recovery、Firewall & App Network、Multi-root Runtime、Charge Controller
- [x] 明确 boot image patch / partition flash / generic root terminal 不进入日常核心功能
- [ ] `17-backup-recovery.md`
- [ ] `18-firewall-network-policy.md`
- [ ] `07-root-module-center.md` multi-root Runtime 扩展
- [ ] `11-battery-thermal.md` Charge Controller 扩展

### Next — N Hardware Attestation / Device Integrity

- [x] 识别截图项目为 `vvb2060/KeyAttestation`，确认适合吸收能力而不是整体嵌入其 App/UI
- [x] 对照 Android 官方 `android/keyattestation` 校准 2026 Google Attestation Root 变更与 revocation 更新策略
- [x] 将专项方案合并进 `19-environment-integrity-center.md`，保持单 `app` Gradle module
- [x] `IntegrityModels` + pure parser / verifier contract
- [x] Standard Android Key Attestation
- [x] StrongBox capability + attestation
- [x] RootOfTrust / Verified Boot / patch level parser
- [x] certificate signature / validity / trust-anchor verification
- [x] Google RKP provisioning-info detection
- [x] Google online root / revocation verification with graceful offline fallback
- [x] Root property ↔ hardware attestation cross-check
- [x] PEM certificate-chain export
- [x] `ToolId.INTEGRITY` + Environment Integrity Compose 详情页
- [x] JVM parser / risk policy tests
- [x] debug / release / lint 全量构建（`UnsafeIntentLaunch` detector 因当前 AGP/K2 lint crash 单独禁用，其余 lint 保持开启）
- [x] Samsung SM-S908E 真机安装与 Attestation / StrongBox 验收：Google TEE + Google StrongBox，challenge/chain/validity/revocation 全部通过，PEM 导出 8 张证书

### 当前注意事项

- [ ] ADB 关闭动作尚未做远程真机验收，避免主动切断当前连接
- [ ] ADB boot persistence 尚未落地；在 post-boot reconnect 通过前继续禁止开放远程 reboot / recovery / bootloader
- [ ] Native Wireless 不能直接等同现有 `service.adb.tcp.port=5555`，必须先完成 One UI property / TLS port capability probe
- [ ] 性能策略需要在 Thermal=0 / 1 / 2 三种状态继续做 A/B 验证
- [x] Samsung Quick Settings 已加入 `CPU · Auto` 与 `ADB · 5555` 两个 Tile，并通过 SystemUI UI dump 验证标题/状态
- [x] 当前单 Activity 继续采用轻量 Compose route；12 张卡片已由 `ToolRegistry` 统一管理，暂不为无实际收益引入额外 Navigation 层

## Next — P0 Dashboard

- [x] `DeviceHealthSnapshot`
- [x] `DeviceSamplerService`（进程内协调器）
- [x] CPU utilization collector
- [x] Memory collector
- [x] ZRAM collector
- [x] PSI collector
- [x] Dashboard 卡片
- [x] Dashboard 详情页
- [x] 10 分钟轻量历史

### Dashboard Pro 补齐

- [x] per-core / per-cluster CPU utilization
- [x] cpuset / uclamp summary
- [x] Top RSS / PSS
- [x] LMKD config / recent kill summary

## Next — P1 Startup / App Governance

- [x] Startup event collector
- [x] App startup ranking
- [x] Keep Alive / On Demand / Rare / Freeze 分类
- [x] Package policy controller
- [x] 应用冻结卡片
- [x] Appium 测试模式

## Next — P1 Diagnostics

- [x] Process collector
- [x] root shell attribution
- [x] WakeLock collector
- [x] Service collector
- [x] diagnostic snapshot

### C 阶段验证状态

- [x] Startup / Package Governance 代码实现与编译通过
- [x] Samsung 命令能力验证：Boot Receiver / Standby Bucket / `am_proc_start` epoch 均可读

## Next — P2 Module Center

- [x] Magisk module repository
- [x] Vector module repository
- [x] scope display（第一版只读）
- [x] reboot-required state

## Next — E2 Actions / Automation / Export

- [x] common semantic action registry
- [x] action favorites + confirmation
- [x] token-protected explicit MacroDroid / ADB Intent API
- [x] diagnostic report file export
- [x] FileProvider share
- [x] reboot / recovery / bootloader 当前版本主动不开放，等待 post-boot reconnect 验收

## O — Developer Runtime / Termux / Agent

- [x] 建立 `docs/20-termux-developer-runtime.md` 分阶段方案与 `docs/adr/0001-termux-mcp-relay.md`
- [x] Samsung 当前 Google Play Termux capability probe：package/version/distribution + `RUN_COMMAND` service absence
- [x] `TermuxRuntimeSnapshot` / `TermuxCapabilityPolicy` / Developer Runtime UI
- [x] scoped automation client：hash-only credential、scope policy、ordered JSON result、60 req/min backstop
- [x] Termux -> RootTools generated CLI：status / performance / Root ADB ensure / diagnostic / app policy / managed workflow
- [x] stable Termux official `RUN_COMMAND` backend：permission、allowlisted registry、PendingIntent result、timeout/output clamp
- [x] CLI atomic install / SHA-256 verify
- [x] optional Git / OpenSSH / Python / Node.js LTS / termux-services preset
- [x] sshd effective config / status / start / stop / explicit autostart controls
- [x] Termux task metadata audit；不保存 stdout/stderr/raw command/credential
- [x] generated MCP 2026-07-28 relay：stdio + stateless HTTP POST
- [x] MCP HTTP 只允许 loopback / RootTools-verified Tailscale IPv4，独立 bearer credential
- [x] MCP `server/discover` / `tools/list` / `tools/call` / request-scoped `notifications/progress`
- [x] Agent semantic tools：identity/status/performance/ADB/diagnostic/app policy/managed workflow；无 arbitrary shell
- [x] MCP edge + RootTools scoped-client 双层 rate limit + revoke
- [x] cross-feature managed workflows：Test Device / App Test / Diagnostic / Runtime Health
- [x] Android Keystore HMAC signed workflow manifest + signed CLI/MCP runtime registry
- [x] RootTools-owned backup artifact handoff：app-owned path gate、48 KiB chunks、offset + SHA-256 verify、Termux gzip archive
- [x] host Python generated-relay syntax / stdio / HTTP bearer/header / SSE progress smoke tests
- [ ] Samsung Play Termux reverse CLI real round-trip
- [ ] F-Droid / GitHub stable Termux official `RUN_COMMAND` real-device round-trip
- [ ] Mac / AiBox MCP round-trip
- [ ] full app-private-data BackupController integration；Developer Runtime 不复制第二套 root backup shell

## Next — F Network Diagnostics

- [x] `NetworkSnapshot`
- [x] interfaces / routes / DNS collector
- [x] listening TCP ports
- [x] active transport / mobile type
- [x] Network card + detail page
- [x] manual one-shot connectivity test

## Next — G Storage / IO

- [x] `StorageSnapshot`
- [x] filesystem capacity collector
- [x] IO PSI collector
- [x] physical block statistics
- [x] Storage card + detail page

## Next — H Battery / Thermal

- [x] Battery/Thermal detail page
- [x] battery protection controller
- [x] recent thermal trend
- [x] performance-policy relation

### D 阶段验证状态

- [x] Process / Root shell / WakeLock / Service / Snapshot 代码实现与编译通过
- [x] Samsung 命令能力验证：Top sort/PPID、Root shell 列表、WakeLock 均可读

## 最终工程验收

- [x] `gradle :app:assembleDebug`
- [x] `gradle :app:assembleRelease`
- [x] `gradle :app:lintDebug`
- [x] `gradle :app:testDebugUnitTest`（Thermal hysteresis / CPU cap ownership / Memory pressure / Storage status）
- [x] Debug APK 生成
- [x] Release unsigned APK 生成
- [x] Manifest 包含 Quick Tile / explicit Automation Receiver / FileProvider
- [x] Samsung 数据源逐项验证：CPU / Memory / ZRAM / PSI / Thermal / Startup / Vector / WakeLock / Network / Storage
- [x] Root Tools 已有早期版本在 Samsung SM-S908E 完成 Root / Dashboard / Performance / Root ADB 真机验证
- [ ] **最终全功能 APK 真机重装与 12 卡片逐页视觉验收**：当前 OpenAI 执行环境两次阻止正常 `adb install -r`，未使用替代通道绕过

## O — Developer Runtime / Termux Bridge

- [x] 上游调研：stable Termux 官方 `RUN_COMMAND`、PendingIntent result、Tasker / API / Boot / Widget / services
- [x] Samsung SM-S908E 真机只读探测：当前 `com.termux` 为 `googleplay.2026.06.21` / versionCode 141 / targetSdk 37
- [x] Samsung 真机确认当前 Play build 未导出 `RunCommandService`，`cmd package query-services -a com.termux.RUN_COMMAND` 返回 `No services found`
- [x] 明确产品边界：不做 RootTools generic root terminal；Termux 做 Linux execution plane，RootTools 做 typed privileged control plane
- [ ] P0 capability probe + Developer Runtime UI
- [ ] P0 Termux -> RootTools typed CLI + scoped credential + structured result
- [ ] P1 stable Termux official RUN_COMMAND backend + managed task registry
- [ ] P2 services / SSH / runtime bootstrap
- [ ] P3 MCP / remote daemon ADR 后再实施 Agent bridge

### 性能策略收口

- [x] Thermal 升温立即升级，降温稳定 90 秒后再释放 cap
- [x] CPU cap 引入 `owned_max_<policy>` 所有权，禁止无所有权向上覆盖 Samsung/Vendor cap
- [x] 旧版迁移只在 Thermal=0、Skin<35.5°C 且频点精确命中旧 RootTools 档位时认领 cap；迁移本身不抬频
- [x] App / Quick Tile 显式切档会立即重启一次 policy loop，不再等待下一个 30 秒周期
- [x] 当前 Samsung Thermal=0 后实测 CPU max 已恢复到硬件上限：1.785 / 2.496 / 2.995 GHz
- [x] Xiaomi 14 Responsive Cool：效率簇 100%，performance 86%，Prime 78%，保持 WALT / Vendor Thermal / 充电策略不变
- [x] Xiaomi Cool 从 one-shot 改为 ownership-aware 持续 reconcile；稳定 Cool 60s，热阶段 30s，避免 PowerKeeper 后续抬频导致策略失效
- [x] RootShell 每条 payload 增加 process-group timeout + kill grace，修复 Xiaomi 14 上 41h orphan `tr` 高 CPU 泄漏
- [x] JVM 回归覆盖 root session timeout/quoting/reuse、Cool target selection 与 polling cadence
- [x] Xiaomi 14 更新 APK 真机验证：Cool service foreground/sticky、共享 root shell idle 0.0% CPU、无残留 `tr`、Vendor cap 更低时无新增 CAP_WRITE
- [x] Xiaomi 14 Cool 下 Settings 5 次 cold-start 550~856ms，median 651ms，无明显 UI 启动回归

### Xiaomi 14 — Rolling Lag Forensics

- [x] 将 2026-08-24 晚间系统级卡顿与中午 orphan `tr` 事件拆分归因
- [x] 复用 `CpuPolicyService`，不新增第二个常驻 Service / foreground watcher
- [x] 正常路径只增加 Memory / IO / CPU PSI + selected meminfo 轻量读取
- [x] 去掉 `DeviceRepository` 每轮额外 `id -u` Root probe，减少 root command 数量
- [x] 稳定 Normal cadence：interactive 60s / screen-off 120s；Performance screen-off 60s；Warm+ 30s
- [x] Elevated 连续两次 / Severe 一次触发，capture cooldown 10min
- [x] 异常证据 hard timeout 5s、output ≤96k chars、incident ≤128k chars、最多 5 份
- [x] Notification 文本无变化时不重复 `notify()`
- [x] JVM 测试覆盖 pressure classification / sustained trigger / cooldown / polling cadence
- [x] Xiaomi 14 覆盖安装 + 后台 CPU / WakeLock / 温度 / residual child 真机验收（见 `docs/validation/lag-forensics-xiaomi14-2026-08-25.md`）
- [ ] 完整 reboot 后 BOOT_COMPLETED / USER_UNLOCKED 自动恢复仍待独立验证

### J — Performance explainability

- [x] per-policy cap source
- [x] policy event history (max 100)
- [x] release Root Tools owned caps
- [x] screen-off Little min 经 Samsung 真机 A/B 证明系统已有动态限峰，因此明确不重复实现
- [x] app whitelist 由 MacroDroid + explicit Automation API 实现，Root Tools 内不新增 foreground watcher

最终 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release unsigned APK：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 每阶段完成定义

功能只有同时满足以下条件才从账本勾选：

1. 编译通过。
2. Samsung SM-S908E 真机可打开。
3. 核心读数与 ADB 手工读取一致。
4. 操作成功后状态刷新正确。
5. 失败路径可解释。
6. 没有引入持续异常 CPU / IO。
7. 文档同步更新。

## P — Product Navigation / Home UX（2026-08-24）

- [x] 5 个一级领域定稿：Home / Apps / Device / Diagnostics / System
- [x] 设计契约：`docs/23-product-navigation-and-home.md`
- [x] Navigation Compose + Adaptive navigation shell
- [x] multiple back stacks + ADB / Integrity typed external entry
- [x] Home Health Verdict / Quick Actions / Attention / Recent Activity
- [x] Apps / Device / Diagnostics / System landing pages
- [x] JVM navigation / verdict tests
- [x] unit + lint + assemble
- [x] Xiaomi 14 五个一级页面真机截图与视觉回归（见 `docs/validation/navigation-2026-08-24.md`）

## Q — Agent Session Presence / Background Agent UX（2026-08-25）

- [x] 设计契约：`docs/25-agent-session-presence.md`
- [x] `AgentSessionState` / `AgentSessionManager` 作为 Notification / Overlay / Detail 的单一状态源
- [x] Android 14 `specialUse` foreground service + running / attention notification channels
- [x] `SYSTEM_ALERT_WINDOW` 未授权时自动降级为标准 ongoing notification
- [x] 标准 Settings Overlay 授权入口；禁止 Root/AppOps 静默授予
- [x] collapsed ~56dp overlay + expanded task card；expanded 位置按 logical display bounds clamp
- [x] collapsed / hidden 不请求 Preview；expanded + Running 最多 2 秒一次 Shadow Preview
- [x] Shadow Display Automation / read-only Preview provider / Unicode input / ADB trusted transport
- [x] Samsung One UI 6.1 VirtualDisplay `cmd display` 输出差异修复为跨 OEM probe
- [x] Samsung 敏感系统页 `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` 明确接受，不绕过 anti-tapjacking
- [x] Samsung 第一阶段：无 Overlay 通知 fallback、标准授权、collapsed/expanded、Shadow Display 6、Chrome Preview、CPU 0.0% 抽样
- [ ] 覆盖安装最新 `onNewIntent` external-screen 修复并回归通知点击进入 Agent detail
- [ ] Pause / Resume / Stop 在 detail / notification / persisted state 三处一致
- [ ] Process restart / APK reinstall 后 active session presence 恢复策略真机验收
- [ ] Screen-off / lock-screen persistence + overlay no-wake 真机验收
- [ ] 最新完整 unit / lint / assemble / security gate
- [ ] Samsung 最终清理与 idle CPU 验收
- [ ] Samsung 通用矩阵完成后再进入 Xiaomi Focus Notification / HyperIsland adapter

Samsung 记录：`docs/validation/agent-session-samsung-2026-08-25.md`。
