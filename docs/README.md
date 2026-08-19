# Root Tools 文档索引

`RootTools` 是一个面向个人 Root Android 设备的长期工具箱工程。产品形态是 **卡片式启动台 + 独立工具详情页 + Quick Settings / ADB / 自动化入口**，而不是把所有 Root 功能堆在一个页面。

## 文档结构

| 文档 | 作用 |
|---|---|
| [00-product-roadmap.md](./00-product-roadmap.md) | 总体产品规划、功能地图、优先级、里程碑 |
| [01-architecture.md](./01-architecture.md) | 工程架构、数据流、权限边界、扩展规范 |
| [02-dashboard-observability.md](./02-dashboard-observability.md) | 设备看板、CPU / 内存 / 温控 / 电池监控 |
| [03-performance-policy.md](./03-performance-policy.md) | Auto / Cool / Performance 与 CPU 策略 |
| [04-adb-network.md](./04-adb-network.md) | ADB Control Center：Root TCP、Native Wireless、连接数据、启动恢复、Tailscale 与网络诊断 |
| [05-startup-background.md](./05-startup-background.md) | 开机启动、后台治理、应用冻结 |
| [06-process-log-diagnostics.md](./06-process-log-diagnostics.md) | 进程、WakeLock、Service、日志、异常诊断 |
| [07-root-module-center.md](./07-root-module-center.md) | Magisk / Vector / Xposed 模块管理与状态 |
| [08-common-actions.md](./08-common-actions.md) | 常用 Root 操作、快捷动作、安全分级 |
| [09-delivery-ledger.md](./09-delivery-ledger.md) | 分阶段进度账本与验收条件 |
| [10-storage-io.md](./10-storage-io.md) | 存储容量、IO PSI 与块设备观测 |
| [11-battery-thermal.md](./11-battery-thermal.md) | 电池、充电、保护与温控状态 |
| [12-final-validation.md](./12-final-validation.md) | 最终 Samsung 真机验收清单 |
| [13-shizuku-sui-bridge.md](./13-shizuku-sui-bridge.md) | Shizuku / Sui 特权桥接、Backend 路由与组件治理规划 |

## 工程原则

1. **单 app 模块优先**：文档和 feature 分层不等于 Gradle 多模块化。除非后续出现明确编译、复用或发布边界，否则不新增 Android 子模块。
2. **首页只做入口**：首页每个卡片对应一个独立工具领域；监控数据只展示摘要，不在首页塞完整控制表单。
3. **一个能力一个真值源**：例如 CPU sysfs 只由 `CpuPolicyController` 写；MacroDroid、Quick Tile、ADB 都只能调用统一 Controller，不能各自直接写系统节点。
4. **读写分离**：监控采集器尽量只读；Root 修改必须通过明确的 Action / Controller。
5. **安全优先**：不关闭 Thermal、不隐藏失败、不默认永久开放 ADB、不自动绕过 Magisk / Android 权限确认。
6. **低开销观测**：默认 15～30 秒级采样；只有用户进入实时详情页时才提高频率，避免“监控工具成为发热源”。
7. **可回滚**：所有系统修改都要记录前值，能通过 UI 或命令恢复。

## 首页卡片目标

### 第一屏：日常高频

- 设备看板
- 性能控制
- Root ADB
- 启动治理

### 第二屏：诊断与系统

- 应用冻结
- 进程 / 日志
- Root 模块
- 电池 / 温控

### 已扩展能力

- 网络诊断
- 存储 / IO
- 常用 Root 操作
- 自动化入口
- 设备快照 / 报告
- Shizuku / Sui 特权桥接（规划中）

所有新增卡片必须先在 `00-product-roadmap.md` 登记，再创建对应详情页和文档章节。
