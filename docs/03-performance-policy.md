# 性能策略模块

## 用户档位

- `Auto`：默认
- `Cool`：长期低温
- `Performance`：15 分钟临时性能

## Auto 内部阶段

- Normal
- Warm
- Moderate
- Severe

详细原则：

1. 不替换 WALT governor。
2. 不绑核。
3. 不关闭 Samsung Thermal。
4. Thermal > 0 时禁止 Root Tools 主动抬高当前系统 `scaling_max_freq`。
5. 只削减 Big / Prime 高频尾段。
6. 升温立即升级阶段，降温必须稳定 90 秒后才释放 cap，避免 Warm / Normal 高频抖动。

## CPU cap 所有权模型

`scaling_max_freq` 不是 Root Tools 独占资源，Samsung Thermal / Power HAL 也可能修改它。因此 Root Tools 不再把“当前 max 低于目标”简单理解为需要恢复。

规则：

1. Root Tools 只有在主动把 max **降到更低值**时，才记录 `owned_max_<policy>`。
2. Samsung 把 max 压得比 Root Tools 更低时，不向上覆盖。
3. 温度恢复后，只有当前 max **仍等于 Root Tools 记录的 owned cap**，才允许恢复。
4. 如果外部系统已经把 max 改得高于 owned cap，则 Root Tools 清除所有权，不再干预。
5. 每 30 秒重新计算一次策略，但稳定状态下不会写 sysfs。

这样 Root Tools 只是 Samsung Thermal 之上的“额外限峰层”，不会和厂商温控在同一个节点互相抢写。

### 旧版本迁移

旧版 Root Tools 可能在温度恢复前留下尚未释放的 `scaling_max_freq` cap。升级时**不直接写回硬件最大频率**，而是仅在以下条件同时满足时识别旧 cap：

- 检测到旧版 `baseline_min_*` policy 数据
- `Thermal Status = 0`
- `Skin < 35.5°C`
- 当前 `scaling_max_freq` 必须精确命中旧版 Warm / Moderate / Severe 的已知目标频点

命中后只写入新的 `owned_max_<policy>` 所有权记录；后续由正常所有权规则决定是否释放。新安装没有旧 policy 数据，不执行迁移。

## Hysteresis

物理热状态采用独立 `ThermalStageHysteresis`：

```text
更热 → 立即升级 Warm / Moderate / Severe

变冷 → 记录 candidate
      → 连续稳定 90 秒
      → 才释放到更低热阶段
```

30 秒采样意味着降温需要至少 3 个稳定周期才会释放，防止临界温度附近不断写频率。

## 已收口能力

- [x] 当前限频来源提示：RootTools / Samsung Thermal / 其他
- [x] 策略变更历史（实施目标：最多 100 条，仅实际策略变化时记录）
- [x] 一键释放 Root Tools 自己拥有的 cap
- [x] 屏幕关闭额外降频：经 Samsung 真机验证后明确不实现
- [x] App 性能白名单：由 MacroDroid + Root Tools Automation API 编排，不在 Root Tools 内新增 foreground watcher

### Advanced Policy 暂缓原因

如果由 Root Tools 自己实现 `App 性能白名单`，会扩大对系统调度的主动写控制面，需要长期判断 foreground app，并可能与 Samsung Game Booster / Power HAL 竞争。

屏幕关闭额外降频已经通过 SM-S908E 真机验证为**没有必要**：

```text
Thermal=0, Screen off:
Little / Big / Prime max = 1.075 / 1.555 / 1.958 GHz

仅唤醒屏幕约 8 秒后：
1.785 / 2.496 / 2.995 GHz
```

说明 Samsung Power/Perf HAL 已经在 screen-off 时主动降低 CPU 上限；Root Tools 再写 `scaling_min_freq` 只会重复控制并增加恢复状态复杂度。

因此白名单采用已有分层：

```text
MacroDroid
  App launched → Root Tools SET_MODE PERFORMANCE
  App closed   → Root Tools SET_MODE AUTO

Root Tools
  只负责 CPU Policy
  不负责持续监控 foreground app
```

这仍然提供“按 App 自动切档”，但不会让 Root Tools 再增加一个长期 watcher。

当前版本通过 `WALT + cpuset/uclamp + Samsung screen-off policy + 大核高频限峰 + Thermal hysteresis + MacroDroid 外部编排` 达成主要目标。

## K. Xiaomi 14 / Snapdragon 8 Gen 3：Responsive Cool（2026-08-24）

### K1. 真机问题背景

Xiaomi 14（houji / Android 15 / HyperOS 2.0）出现“一用就热”时，真机抓到：

- root `tr` 子进程连续存活约 41 小时；
- 单进程长期约 68% CPU、瞬时接近 100%；
- Skin 约 40~41.5°C；
- CPU 传感器出现 50~60°C 常态、历史缓存峰值接近 90°C；
- 清理异常进程后，Skin 降到约 35°C，CPU 约 37~40°C，电池约 31.6°C。

因此该设备的第一优先级不是继续粗暴限频，而是先保证 RootTools 自身不会制造长期 CPU 常驻任务。

### K2. Responsive Cool 目标

用户场景是不玩游戏，但要求 UI、键盘、应用启动继续跟手，因此 Cool 不采用：

- 固定频率；
- powersave governor；
- 关核；
- 禁用 Xiaomi Thermal / PowerKeeper；
- 降充电功率或关闭快充。

Cool 只削掉性能核与 Prime 核最不经济的高频尾段，同时保留效率簇完整峰值：

```text
Cool + Warm floor
Efficiency: 100%
Performance: 86%
Prime:       78%
```

目标频率始终向下取当前 policy 支持的实际频点。以本次 Xiaomi 14 真机频表为例，典型目标约为：

```text
policy0: 2.2656 GHz（完整）
policy2: 2.7072 GHz
policy5: 2.5152 GHz
policy7: 2.5536 GHz
```

当设备进入 Moderate / Severe 时，仍使用更严格的 Thermal stage 比例；Cool 不得降低热保护强度。

### K3. 厂商 cap 优先级

Xiaomi 真机确认 `com.miui.powerkeeper` / Vendor Thermal 会主动修改 `scaling_max_freq`。因此继续沿用 owned-cap 契约：

```text
Vendor 当前 cap < RootTools 目标
→ 不写，不向上抬频

RootTools 主动写得更低
→ 记录 owned_max_<policy>

释放时只有 current == owned 且 Thermal=0
→ 才允许恢复
```

这使 Responsive Cool 只是厂商策略之上的“额外削峰层”，不会和 HyperOS 抢控制权。

### K4. 测试契约

纯 JVM 测试必须覆盖：

- Cool 保留 efficiency cluster 完整峰值；
- Cool 对 performance / prime 分别使用 86% / 78% 削峰；
- Moderate / Severe 始终比 Cool-Warm 更严格；
- Performance + Normal 保留硬件峰值；
- Vendor 已经限得更低时 `CpuCapOwnershipDecider` 不允许向上覆盖。

### K5. Cool 持续 reconcile

旧版 Cool 只应用一次后停止 `CpuPolicyService`。这在 Samsung 固定 cap 场景还能工作，但 Xiaomi PowerKeeper 会持续重写 `scaling_max_freq`，因此一次性写入不能代表“长期 Cool”。

现在 Cool 与 Auto / Performance 共用同一个 ownership-aware monitor：

```text
Cool + Normal/Warm    → 60s 采样
Cool + Moderate/Severe → 30s 采样
Auto / Performance   → 30s 采样
```

稳定状态只读不写；只有当前 vendor cap 高于 RootTools Cool 目标时才追加更严格的 owned cap。这样避免高频 root 轮询，同时允许 HyperOS 后续抬频后重新进入 Responsive Cool 目标区间。

## J. 可解释性与释放（2026-08-19）

### J1. Cap source

每个 policy 根据 `hardwareMax / scalingMax / thermalStatus / owned_max` 推导：

- `Full`：当前 max >= hardware max
- `Root Tools`：当前 max 精确等于 Root Tools 的 owned cap
- `Samsung Thermal`：Thermal > 0 且当前 max 低于 hardware max，同时不是 Root Tools 精确 owned cap
- `Other System`：Thermal=0、当前 max 低于 hardware max、Root Tools 没有所有权

这只是解释层，不新增 sysfs 写入。

### J2. Policy history

只记录：

- 用户模式变化
- Root Tools 真正写入/释放 `scaling_max_freq`
- Performance 自动回 Auto

最多 100 条，使用应用内部轻量文件；不记录每 30 秒轮询。

### J3. Release owned caps

“释放 Root Tools cap”只处理当前 `owned_max_<policy>`：

```text
owned > 0
AND current max == owned
AND Thermal = 0
→ 恢复到 hardware max
```

如果 Samsung 当前已经压得更低，或者 max 已被其他 owner 修改，则只清理/保留所有权状态，不主动抬频。

## 与设备看板关系

Performance 页面不自行重复采集 Thermal / CPU，统一订阅 Dashboard Sampler 的 Snapshot。
