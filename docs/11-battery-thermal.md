# 电池与温控

## 目标

把设备“为什么热、是否正在充电、保护是否开启、CPU Auto 当前处于什么阶段”放在一个独立卡片中。

不重复启动 Thermal 轮询；全部复用 `DeviceSamplerService` 与 `CpuPolicyService`。

## 2026-08-19 实施拆解（Milestone H）

### H1. 数据复用

直接消费 `DeviceHealthSnapshot`：

- AP / Skin / Battery / USB / PATHM
- Android Thermal Status
- Battery level / current / voltage / charging
- battery protection enabled / threshold
- 最近轻量历史
- 当前 Auto / Cool / Performance + Thermal stage

### H2. 电池保护 Controller

第一版支持：

- Enable Samsung battery protection，threshold=80
- Disable battery protection

修改后立即重新读取状态，不做 reboot。

### H3. UI

首页摘要：

```text
Skin 35°C · Battery 35°C
Protect 80% · Thermal 0
```

详情页：

1. Thermal overview
2. Battery / charging
3. Protection switch
4. Recent min/max trend
5. CPU policy relation

### H4. 安全

- 不修改 Samsung Thermal HAL
- 不关闭 thermal-engine
- 不提供 thermal bypass
- 不因为温度升高自动改电池保护

### H5. 验收

- 温度与 `dumpsys thermalservice` 对照
- 充电电流/电压与 `dumpsys battery` 对照
- protect_battery 与 `settings get global protect_battery` 对照
- 关闭页面不会新增采样器

## Xiaomi 14 使用策略（2026-08-24）

当前用户目标是优先改善日常续航与发热，但**不限制充电速度**。因此 Xiaomi 14 上明确保持以下边界：

- 不修改 90W 快充能力；
- 不主动限制充电电流/电压；
- 不因为进入 Cool 模式而改变充电策略；
- 不把 Samsung `protect_battery` 方案强行迁移到 Xiaomi；
- 日常续航优化放在 CPU 持续功耗、异常后台任务与厂商 cap 协作上。

本次真机电池读数：`Health=Good`、cycle≈613、`charge_full≈4099mAh`、`charge_full_design≈4610mAh`。异常 root CPU 任务清理后，电池温度可从约 35°C 回落到约 31.6°C，因此本次“快速发热”的首要根因不是充电或电池自身。
