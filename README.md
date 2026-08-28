# Root Tools Android

个人 Android Root 工具箱。GitHub Android 仓库使用 `RootTools-Android` 名称，与同名 iOS 工程明确区分。目标不是把所有 Root 能力堆进一个页面，而是提供一个可持续扩展的卡片式启动台，每个能力拥有独立详情页和独立权限边界。

完整产品规划、架构与子模块设计见 [`docs/README.md`](./docs/README.md)。

## 当前能力

### 设备看板

- CPU 使用率、load、每簇频率 / min / max / hardware max / governor
- MemAvailable、Cache、Anon、Slab、Swap、ZRAM 压缩率
- memory / IO PSI
- AP / Skin / Battery / USB / PATHM 与 Android Thermal Status
- 电池电量、电流、电压、80% 保护
- 进程数、uptime、低频 Top Process
- 进程内环形历史；首页 30 秒、详情页 2 秒采样

### 性能控制

- 用户模式：`Auto / Cool / Performance`
- `Auto` 内部状态：`Normal / Warm / Moderate / Severe`
- 保留设备原生 WALT governor、cpuset、uclamp 与 Samsung Thermal
- `Thermal > 0` 时只允许继续削峰，不会把系统当前限频向上覆盖
- `Performance` 默认 15 分钟后自动返回 `Auto`
- Auto 守护使用低频 30 秒采样，不做高频 busy-loop

### Root ADB

- 一键切换 Root ADB TCP `5555`
- 不依赖 Android 原生“无线调试必须连接 Wi-Fi”的限制
- 自动显示官方 `tun0` 或 Root `tailscale0` 上的 Tailscale IPv4
- 可直接复制 `adb connect <tailscale-ip>:5555`
- 默认不做开机永久开放，降低长期暴露风险

### Root Tailscale

- Root `tailscaled` 运行在独立 `tailscale0`，不占 Android 唯一的 `VpnService` 槽位
- 支持与 Hiddify 等 Android VPN 共存，普通互联网继续走 Hiddify，`100.64.0.0/10` 走 tailnet
- App 内完成已验证 runtime 安装、浏览器认证、启停、路由修复和可撤销开机恢复
- Runtime 固定版本并校验 SHA-256；RootTools 不接受 UI 传入任意 shell
- Root overlay 未验证成功前不会主动停止官方 Tailscale Android App

### 启动与应用控制

- 分析本次 boot 的真实 `am_proc_start` 时间线
- BOOT_COMPLETED Receiver、启动次数、触发原因、当前常驻状态
- Protected / On Demand / Rare / Freeze 分类
- Freeze / Enable / force-stop / Standby bucket / AppOps
- Appium 测试模式：Notification Listener + Doze whitelist 按需切换
- 应用清单搜索 / 筛选 / 排序与系统 / 用户 / Running / Frozen 状态
- 单应用 Detail：Package / SDK / installer / path / signing / shared libraries
- Activity / Service / Receiver / Provider 组件查看与受保护的 enable / disable
- dangerous runtime permission、AppOps 与 Special Access 入口统一收敛到 App Control Center

### 进程诊断

- Top CPU / RSS / PPID
- Root Shell 异常高亮
- 按需 pipe inode / FD 归属分析
- WakeLock 与第三方 Active Service 摘要
- 一键 Diagnostic Snapshot

### Root 模块

- Magisk / Zygisk module 状态与 disable/remove marker
- Vector / Xposed module enabled 状态
- Vector Scope 按需读取
- Magisk 模块变更明确标记 pending reboot，不自动重启
- `zygisk_vector` 作为受保护框架

### 网络 / 存储 / 电池

- Wi-Fi / Cellular / VPN / Tailscale interfaces、route、DNS、listen ports
- 单次手工 ping，不做持续公网探测
- `/data` / shared storage 容量、IO PSI、UFS block 统计
- 独立电池与温控页、80% 电池保护控制、近期热状态范围

### 常用操作与自动化

- Developer Options / Magisk / Vector / Hail 快捷入口
- restart adbd / restart SystemUI / Stop Bilibili / Battery Protect 80%
- 收藏常用动作，但执行确认不会被收藏绕过
- token 保护的显式 Broadcast API：`SET_MODE / SET_ADB / RUN_DIAGNOSTIC / FREEZE / UNFREEZE`
- Diagnostic Snapshot 文件导出 + FileProvider 分享
- 当前**不开放 reboot / recovery / bootloader 执行入口**，直到重启后远程回连链路具备可靠保证

### Quick Settings

应用注册三个系统快捷磁贴：

- `CPU 档位`：Auto → Cool → Performance → Auto
- `Root ADB`：开启 / 关闭 5555
- `Wireless ADB`：Android 原生 Wireless Debugging 快捷入口

## 首页结构

首页只承担工具入口和状态摘要。所有卡片通过统一 `ToolRegistry` 注册，目前包含：

1. 设备看板
2. 性能控制
3. Root ADB
4. 权限中心
5. 启动治理
6. 应用控制
7. 进程诊断
8. Root 模块
9. 常用操作
10. 网络诊断
11. 存储与 IO
12. 电池与温控

后续新增 Root 功能时，先在 `ToolRegistry` 与文档路线图注册，再增加独立详情页和 Repository / Controller；不要继续向首页塞完整控制表单。

## 权限模型

首次启动按以下顺序自动触发：

1. Android 通知权限
2. Activity 进入前台
3. 主进程调用 `su`，触发 Magisk Root 授权
4. Root 成功后才启动 CPU Auto 前台守护

系统授权始终保留用户确认；应用不会通过修改 Magisk 数据库等方式绕过 Root 确认。

## 安全原则

- 不关闭系统 Thermal Engine
- 不固定绑核
- 不替换原厂 governor
- 不在 Thermal 已限频时主动抬高 `scaling_max_freq`
- ADB 5555 不默认开机常驻
- 性能工具只做“限峰”，避免和厂商调度/温控互相争抢

## 构建

当前构建基线：Android SDK 36、AGP 9.2、Gradle 9.5、JDK 17+。项目自带 `./gradlew` 启动入口，会优先复用本机 Gradle 缓存，并在常见 macOS 开发环境自动发现 JDK 21 与 Android SDK。

```bash
./gradlew :app:assembleDebug
```

## GitHub Release

Android 发布仓库为 `RootTools-Android`。推送 `v*` tag 后，GitHub Actions 会运行质量/安全门禁、单测和 lint，使用仓库 Secrets 中的固定 Android signing key 构建可覆盖升级的签名 Release APK，并上传 APK 与 SHA-256 到 GitHub Releases。

首个远程测试版本为 `v0.4.0-beta.1`。详细流程与签名边界见 [`docs/29-github-release.md`](./docs/29-github-release.md)。

### 工程开发基线

首次 clone 后启用版本化 Git hooks：

```bash
bash scripts/setup-dev.sh
```

日常提交前至少执行：

```bash
python3 scripts/quality_guard.py
python3 scripts/security_guard.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

日常开发优先使用快速门禁，只编译测试所需代码，不生成 APK，避免每次修改都重复跑 native 打包 / dex / lint / release / coverage：

```bash
bash scripts/verify-fast.sh
# 或只聚焦一个 JVM 测试类：
bash scripts/verify-fast.sh com.arthur.roottools.feature.assistant.policy.AssistantSelectionPolicyTest
```

需要真机安装时再单独生成 Debug APK：

```bash
./gradlew :app:assembleDebug
```

`./gradlew` 还会为 RootTools 使用项目级构建锁；同一 checkout 已经有 Gradle 构建时，新调用会快速失败，而不是再启动一套 Kotlin/Gradle 编译进程争抢 CPU 与缓存。必要时可显式设置 `ROOTTOOLS_BUILD_LOCK=0` 绕过，但日常开发不建议这么做。

RootTools 默认使用 Kotlin in-process compilation。这样在多个 Android 工程被 AI Agent 并行开发时，不会全部排队到同一个全局 Kotlin Compile Daemon；编译内存由项目自己的 Gradle daemon 复用，换取更可预测的本工程反馈时间。

构建入口还带主机负载预检：默认在 1 分钟 load average 超过逻辑 CPU 数的 2 倍时直接拒绝启动重型 Gradle 构建，避免在已经严重拥塞的机器上再浪费十几分钟。可用 `ROOTTOOLS_MAX_LOAD_PER_CORE` 调整阈值；只有明确需要抢占式立即构建时才使用 `ROOTTOOLS_FORCE_BUILD=1 ./gradlew ...` 绕过。

核心 JVM 覆盖率基线：

```bash
./gradlew :app:koverXmlReportDebug
python3 scripts/coverage_guard.py
```

提交信息使用 Conventional Commits，并可单独检查：

```bash
python3 scripts/commit_guard.py --subject "feat(adb): add endpoint health check"
```

当前工程坚持 **单 `:app` Gradle module + `app/core/feature` 逻辑边界**。公共 UI token、组件和 Android UI actions 放在 `core/ui`；Feature 不直接依赖其它 Feature 的实现，也不反向依赖 legacy `ui` host。只有出现明确复用、编译隔离、稳定 API 或持续并行冲突时，才评估拆出物理 Gradle module。完整规则见 [`docs/17-engineering-governance-and-ai-workflow.md`](./docs/17-engineering-governance-and-ai-workflow.md)。

完整工程校验：

```bash
bash scripts/build.sh
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Samsung 只读验收（不会安装、重启或关闭 ADB）：

```bash
bash scripts/validate-samsung.sh 100.91.126.56:5555
```
