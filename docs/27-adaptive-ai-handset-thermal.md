# AI 高频执行机：自适应性能与温控

## 1. 场景与目标

小米 14 被定位为高频 AI / 自动化执行机：Root Tools、Device MCP、Tailscale、UI 自动化与日常 App 都可能持续工作。

目标不是传统“省电”，而是：

1. 人正在操作时保留明显的突发性能和 120 Hz 交互体验；
2. 无人操作 / 熄屏后台任务继续可用，但避免 Prime / performance cluster 长时间停留在低能效高频尾段；
3. 在厂商 emergency thermal 介入前，以体感温度为目标提前削峰；
4. 永远不关闭 OEM Thermal，不替换 governor，不主动抬高厂商已经施加的 CPU cap；
5. HyperOS 2 -> 3 或后续系统升级后自动丢弃旧 ownership，再基于新系统重新探测；
6. 温度采样必须兼容 Samsung 的 `AP/BAT/SKIN` 和 Xiaomi/Qualcomm 的 `CPUx/battery/skin` 命名。

## 2. 2026-08-25 当前证据

Xiaomi 14 / `houji` 当前只读检查：

- Android 15 / HyperOS 2；
- Thermal Status = 0；
- HAL 当前值约：skin 37.x°C、battery 32.x°C、CPU 最高约 52°C；
- thermalservice 同时保留更高的 cached temperature，因此策略不能直接取第一条缓存；
- Xiaomi 当前传感器使用 lower-case `battery` / `skin` 及 `CPU0..CPU7`，旧的 `mName=(AP|BAT|SKIN)` 过滤会漏采；
- CPU 使用 WALT governor，厂商当前 max cap 已可低于 hardware max；
- 16 GB RAM 设备同时启用了 Xiaomi Memory Extension / zram backing；这属于后续系统级效率建议，不在本次自动 sysfs 写入范围内。

## 3. 已落地能力

### 3.1 OEM-tolerant thermal probe

新增共享 `ThermalProbeParser`：

- 优先读取 `Current temperatures from HAL`；
- 有 current block 时忽略旧 cached temperature；
- Samsung：直接识别 `AP / BAT / SKIN / USB / PATHM`；
- Xiaomi / Qualcomm：识别 lower-case `battery / skin`，AP 缺失时取当前 HAL CPU sensor 最大值；
- `DeviceRepository` 与 `DeviceHealthCollector` 复用同一解析器，避免两套温度真相源。

### 3.2 Adaptive thermal policy

`AUTO` 模式使用新的 `AdaptiveThermalPolicy`，输入：

```text
OEM thermal status
skin temperature
battery temperature
charging state
interactive / unattended state
```

当前体感阈值：

```text
skin < 38°C                 -> Normal
skin >= 38°C                -> Warm
skin >= 40°C                -> Moderate
skin >= 42°C                -> Severe

battery >= 37.5°C           -> Warm
battery >= 39.5°C           -> Moderate
battery >= 42°C             -> Severe

charging + skin/battery >=37°C -> Warm floor
screen-off / unattended        -> Warm floor
```

OEM Thermal status 1/2/3 仍分别至少映射 Warm/Moderate/Severe，并拥有最高安全优先级。

### 3.3 Responsive CPU peak control

继续复用现有 ownership-aware `CpuPolicyController`：

- Normal：允许系统自身决定峰值；
- Warm：效率簇保留完整 peak，performance / Prime 只削高频尾段；
- Moderate / Severe：逐步收紧性能簇和 Prime；
- 如果厂商 cap 更低，不向上覆盖；
- hysteresis 在降温后延迟释放，避免阈值附近频繁震荡；
- 显式 Performance 仍是 15 分钟临时档，并在明显 thermal pressure 下让路。

### 3.4 System-upgrade compatibility guard

PolicyStore 记录 Android `Build.FINGERPRINT`。

检测到 fingerprint 变化时：

1. 清空旧 `owned_max_*`；
2. 清空旧 CPU baseline；
3. 重置 thermal hysteresis；
4. 如果升级前遗留 `PERFORMANCE`，退回 `AUTO`；
5. 写入 compatibility event；
6. 后续只根据新系统实时 CPU topology / HAL thermal 重新建立策略。

这保证 HyperOS 3 改频点、policy id、thermal sensor 命名时，不会把 OS2 的 ownership 当成新系统真相。

## 4. 数据流

```text
dumpsys thermalservice
       │
       ▼
ThermalProbeParser
       │
       ├──────────────► DeviceHealthCollector / UI
       │
       ▼
DeviceRepository
       │
       ▼
AdaptiveThermalPolicy  ◄──── PowerManager.isInteractive
       │
       ▼
ThermalStageHysteresis
       │
       ▼
CpuFrequencyTargetPolicy
       │
       ▼
CpuCapOwnershipDecider
       │
       ▼
CpuPolicyController
       │
       ▼
/sys/.../scaling_max_freq
```

## 5. 安全与回滚

硬边界：

- 不关闭 Android / OEM thermal service；
- 不切 performance governor；
- 不写 scaling_min_freq；
- 不绑核；
- 不改 cpuset/uclamp；
- 不自动改充电电流、GPU vendor 节点或 Xiaomi 私有 thermal 配置；
- 不因为 Root 可用就绕过厂商更严格的 cap；
- 仅写 Root Tools 已验证和可 ownership tracking 的 `scaling_max_freq`。

回滚：

- Performance 页面可释放 Root Tools owned cap；
- system build change 会自动清理旧 ownership；
- App 数据清理后 PolicyStore ownership 消失，OEM 状态仍为系统唯一真相。

## 6. 为什么暂不自动修改充电 / 刷新率 / Memory Extension

这些能力有收益，但在 HyperOS 2 -> 3 升级前不适合作为无条件 daemon 写入：

- Xiaomi charging node / setting 可能随版本改变；
- `user_refresh_rate` / `miui_refresh_rate` 与 LTPO / Smart FPS 的 ownership 不统一；
- Memory Extension 涉及 vendor extm、zram backing 和 reboot，不能仅根据一个 settings key 判断完整状态。

因此当前先把“采样、决策、CPU ownership、升级重新探测”做成稳定底座。OS3 升级后按同一 capability-probe 模式确认新的 charging/display/extm adapter，再接入自动执行，避免把 OS2 私有参数写死进长期产品。

## 7. 测试矩阵

JVM pure tests：

- Xiaomi lower-case current HAL sensor；
- cached temperature 不得覆盖 current HAL；
- Samsung AP/BAT/SKIN fallback；
- interactive cool -> Normal；
- unattended cool -> Warm floor；
- charging heat -> Warm；
- skin 40/42°C -> Moderate/Severe；
- OEM thermal severe -> Severe；
- first install / same build / changed build compatibility policy。

真机：

- Xiaomi 14：温度字段必须出现当前 HAL skin/battery/CPU，而不是旧缓存；
- AUTO + 低温交互：不得新增不必要 cap；
- AUTO + screen off：策略可进入 Warm floor；
- OEM cap 更低：不得向上覆盖；
- Root Tools 退后台：自身 CPU 不得形成 busy loop；
- 模拟 fingerprint change 的 store-level test 不允许直接伪造 Android Build；真实 OS3 升级后做一次 post-upgrade acceptance。

## 8. OS3 升级后的验收顺序

```text
升级完成
  -> Root / Magisk 状态确认
  -> Root Tools 只读 thermal + CPU topology
  -> 确认 compatibility event
  -> 确认 owned_max_* 无 OS2 遗留
  -> AUTO 低温交互 smoke test
  -> screen-off Warm floor test
  -> 15~30 分钟温度趋势
  -> 再评估 charging / refresh / extm adapter
```

## 9. Acceptance criteria

- 小米 14 当前 HAL 温度可稳定读出；
- 新策略 pure tests 全部通过；
- AUTO 不牺牲低温交互 burst；
- unattended / charging heat 会更早削峰；
- 系统 thermal / vendor cap 始终优先；
- 系统升级不会继承旧 CPU ownership；
- Root Tools 自身没有新增持续高 CPU / 高频 shell polling。

