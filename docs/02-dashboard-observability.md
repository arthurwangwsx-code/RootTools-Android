# 设备看板与观测模块

## 目标

提供一个比 `top` / Scene / OpenMonitor 更适合个人测试机的轻量状态看板，重点回答：

- 现在为什么热？
- CPU 有没有异常高频？
- 内存是真紧张还是只是 Swap 看起来满？
- 哪个进程正在消耗 CPU / RAM？
- 系统是否已经 Thermal throttling？

## 首页摘要

看板卡片首页只展示：

- AP / Skin 温度
- Thermal stage
- CPU 总使用率
- MemAvailable
- Top 异常进程名称（存在时）

## CPU Monitor

数据源：

- `/sys/devices/system/cpu/cpufreq/policy*`
- `/proc/stat`
- `/dev/cpuctl/*/cpu.uclamp.*`
- `/dev/cpuset/*/cpus`

展示：

- cluster current/min/max/hardware max
- utilization
- governor
- top-app/background uclamp
- 频率占比分布
- CPU Top Processes

## Memory Monitor

数据源：

- `/proc/meminfo`
- `/proc/pressure/memory`
- `/sys/block/zram0/mm_stat`
- `dumpsys meminfo`

关键指标：

- MemAvailable，而不是只看 MemFree
- Cached / Anon
- Swap / ZRAM used
- ZRAM compression ratio
- PSI some/full
- Top RSS / PSS

判断逻辑：

```text
Swap 高 + MemAvailable 高 + PSI 低
→ 正常，不提示“内存不足”

MemAvailable 低 + PSI 持续升高
→ 真正内存压力
```

## Thermal / Battery

数据源：

- `dumpsys thermalservice`
- `/sys/class/thermal/thermal_zone*`
- `dumpsys battery`

展示：

- AP
- SKIN
- BAT
- USB
- PATHM（设备支持时）
- Thermal Status
- charging / current / voltage
- battery protection status

## 历史与告警

第一版告警条件：

- Thermal >= 2 持续 30 秒
- 单进程 CPU > 60% 持续 60 秒
- Root shell CPU > 50% 持续 60 秒
- Memory PSI avg10 > 10
- Skin > 41°C

触发后自动保存一份 Diagnostic Snapshot，但默认不自动杀进程。

## 验收

- [x] 页面前台 2 秒采样 CPU/Memory 不造成明显发热（Samsung 实测 Root Tools 无持续高 CPU）
- [x] 首页回到 30 秒采样
- [x] 能正确解释三星本次“Swap 接近满但无内存压力”的场景
- [x] 能识别类似 ApexTuner Root Shell 长期 100% CPU 的异常

## 2026-08-19 实施拆解（Milestone B）

### B1. 统一快照

新增 `DeviceHealthSnapshot`，统一承载：

- CPU 总使用率 / idle ratio / load average
- 每个 CPU policy 的 current / min / max / hardware max / governor
- MemAvailable / Cached / Anon / Slab
- Swap / ZRAM / ZRAM 压缩率
- memory / io PSI
- AP / Skin / Battery / USB / PATHM
- Thermal Status
- 电池电量 / 电流 / 电压 / 充电状态 / 80% 保护状态
- 进程数 / uptime
- 低频采样的 Top CPU process

### B2. 采样器

第一版 `DeviceSamplerService` 是**进程内采样协调器**，不额外创建 Android 常驻 FGS，避免 Root Tools 自己增加后台常驻成本。

2026-08-20 起进一步收紧生命周期：Activity 进入后台后 Dashboard sampler 直接暂停；重新回到前台时按当前
页面恢复 30 秒首页采样或 1/2/5 秒详情采样。后台需要持续生效的 Auto/Performance 温控由独立
`CpuPolicyService` 承担，不再让 UI 历史采样成为后台 root 命令来源。

采样节奏：

- 首页：30 秒
- Dashboard / Battery 详情：用户可选 1 / 2 / 5 秒，默认 2 秒
- 1 秒标记为实验档，只提高轻量 batch 采样，不提高进程采样频率
- Top process：每 10 秒一次，不随 1 / 2 / 5 秒基础采样重复运行 `top`
- Activity 不存在时：停止 Dashboard 全量采样；长期 Thermal 仍由现有 CPU Policy Service 负责

一次采样尽量合并为单次 Root Shell batch，禁止每个指标独立 `su`。

### B3. 历史

实时历史保存进程内环形缓存：

- 最多 900 个点
- Dashboard 2 秒模式下约 30 分钟
- 首页模式下自然形成更长稀疏历史

另增加 24 小时轻量持久历史：

- 每 5 分钟最多写 1 点
- 最多 288 点，超过 24 小时或 288 点自动淘汰
- 字段仅包含 `timestamp / CPU / MemAvailable / AP / Skin / Battery / Thermal / cluster freq`
- 使用应用私有 TSV 文件，不引入 Room
- 不保存进程、PSS、WakeLock、Service 等重数据
- Root Tools 进程没有采样时不额外启动历史采样 Service，因此“24h”表示保留窗口，不代表强制 24 小时连续运行

### B4. UI

首页卡片摘要：

```text
CPU 18% · Mem 4.6 GB
AP 38°C · Skin 32°C
Top: com.xxx 12%
```

详情页分四区：

1. CPU / Load
2. Memory / ZRAM / PSI
3. Thermal / Battery
4. Top process / System

### B5. 判断规则

内存状态不使用 Swap 百分比单独判断：

```text
MemAvailable > 25% && memory PSI avg10 < 2
→ Healthy，即使 Swap > 80%

MemAvailable < 15% || memory PSI avg10 >= 10
→ Pressure
```

### B6. 验收方法

- UI CPU 与 `/proc/stat` delta 对照
- MemAvailable 与 `/proc/meminfo` 对照
- ZRAM 与 `/sys/block/zram0/mm_stat` 对照
- PSI 与 `/proc/pressure/{memory,io}` 对照
- Thermal 与 `dumpsys thermalservice` 对照
- 连续打开 Dashboard 5 分钟后检查 Root Tools 自身 CPU，不允许出现持续高占用

## B7. Dashboard Pro 补齐（2026-08-19）

总路线图还要求详情页具备 scheduler / memory pressure 的解释能力，因此在 B1~B6 MVP 之上继续补齐：

### CPU utilization

`/proc/stat` 不再只读取总 `cpu` 行，同时读取 `cpu0..cpuN`，用相邻样本 delta 计算：

- 全机 CPU utilization
- 每核 utilization
- 按 cpufreq policy 聚合后的 cluster utilization

不使用 `top` 的瞬时 CPU 作为 cluster utilization。

### Scheduler summary

只读：

- `top-app / foreground / background / system-background` cpuset
- `top-app / background` uclamp min/max

用于解释“前台为什么仍流畅、后台为什么被限制”，不在 Dashboard 页面修改这些值。

### Top RSS / PSS

每 10 秒的 Process 采样周期里：

1. 先按 RSS 找前 6 个进程；
2. 只对这 6 个读取 `/proc/<pid>/smaps_rollup` 的 PSS；
3. 页面显示 RSS / PSS，不对全部进程扫描 smaps。

### LMKD

第一版不修改 LMKD 参数，只展示：

- `ro.lmk.*` / `ro.config.low_ram` 等可用配置摘要
- 当前 `MemAvailable + memory PSI` 作为主要压力结论
- 本次 boot 可读取到的 low-memory kill 事件数（设备支持时）

### 历史

Dashboard Pro 最终采用“两轨历史”：

1. 详情实时轨：进程内最多 900 点，用于 30 分钟 CPU / AP / Skin / cluster frequency 曲线；
2. 24h 轻量轨：5 分钟最多 1 次应用私有文件写入，最多 288 点。

因此无需引入数据库或新的常驻 Service，同时可以跨 Activity / App 重开保留最近一天已经采集到的轻量数据。
