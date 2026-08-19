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
