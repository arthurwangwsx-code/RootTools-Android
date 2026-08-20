# 开机启动与后台治理

## 目标

解决：

- 开机到桌面慢
- 桌面出现后前几分钟仍卡
- 普通 App 自启
- Service / Provider / Job 间接拉起
- 自动化基础设施被误杀

## Startup Trace

记录启动后的：

- `am_proc_start`
- BOOT_COMPLETED / LOCKED_BOOT_COMPLETED
- Service start
- Provider start
- JobScheduler
- Accessibility restore
- Notification Listener restore

按时间生成：

```text
0~30s
30~60s
1~3m
3~5m
```

## 应用分类

### Keep Alive

- Tailscale
- AiBox RootLab
- GKD
- MacroDroid（当前主调度器）

### On Demand

- Appium
- ApexTuner
- 性能诊断工具

### Rare / Deep Sleep

- Bilibili
- Facebook
- ES File Explorer
- 普通消费 App

### Freeze

- 应用宝等高侵入、非实时需要 App

## 控制手段顺序

优先从弱到强：

1. Standby bucket
2. AppOps background
3. force-stop
4. freeze / disable-user
5. component-level disable（高级模式）

每一档必须可恢复。

## 开机 A/B 测试

优化前后都记录：

- boot completed
- 30s / 60s / 180s process count
- CPU load
- IO PSI
- memory pressure
- Thermal
- 可交互时间

只有实际 A/B 变快才算治理有效。

## 2026-08-19 实施拆解（Milestone C）

### C1. 不增加新的开机负担

Root Tools 第一版**不注册自己的 BOOT_COMPLETED 采样服务**。启动分析采用 on-demand：

```text
/proc/uptime + date
        ↓
计算本次 boot epoch
        ↓
logcat -b events -v epoch -s am_proc_start:I
        ↓
过滤本次 boot 后事件
```

因此不会为了统计开机而新增长期开机 Receiver / FGS。

### C2. StartupAppRecord

每个第三方 App 记录：

- package / label
- first start elapsed seconds
- process start count
- start reasons（broadcast / service / provider / job / next-top-activity）
- BOOT_COMPLETED Receiver 数量
- 当前是否仍有进程
- 当前 enabled / disabled-user 状态

时间线分桶：

- 0~30s
- 30~60s
- 60~180s
- 180~300s
- 300s+

### C3. PackagePolicyController

所有应用治理写操作统一收口：

- `enable`
- `freeze`（`disable-user`）
- standby `active / rare / restricted`
- background AppOps `allow / ignore`
- force-stop

保护名单第一版：

- `com.arthur.roottools`
- `com.tailscale.ipn`
- `com.arthur.aibox.android.rootlab`
- `com.arlosoft.macrodroid`
- `li.songe.gkd`

保护名单不得从普通 UI 执行 Freeze。

### C4. Appium Test Mode

测试模式 ON：

- enable `io.appium.settings`
- allow Notification Listener
- 加入 Doze whitelist

测试模式 OFF：

- remove Notification Listener
- remove Doze whitelist
- 保留 APK，不卸载

### C5. UI

`启动治理`：本次启动摘要 + 时间分桶 + App 排名。

原 `应用冻结` 将在 Milestone L 升级为 `应用控制 / App Control Center`。Startup 页面继续负责“本次开机发生了什么”，
App Control Center 的 Runtime/Components 页面负责“这个 App 为什么会起来、具体控制哪个入口”。两边复用同一个 `StartupRepository`，不重复采集。

当前 `应用冻结` 入口优先显示当前测试机高价值对象，支持筛选：

- Protected
- Running
- Boot capable
- Frozen
- Restricted

完整应用治理升级方案见 `15-app-control-center.md`。

### C6. 验收

- 不能冻结保护名单
- Freeze 后 `pm` 状态立即刷新
- Enable 后可恢复
- Appium test mode 的 Notification Listener / Doze 状态与系统命令一致
- Startup ranking 能识别本次三星启动中的 Facebook / OneConnect / WeChat 等真实启动事件
- 页面分析只在用户打开时运行，不形成后台采样循环
