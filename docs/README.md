# Root Tools 文档索引

`RootTools` 是一个面向个人 Root Android 设备的长期工具箱工程。产品形态是 **5 个稳定一级领域 + 领域 Landing Page + 独立工具详情页 + Quick Settings / ADB / 自动化入口**，而不是把所有 Root 功能堆在一个页面。

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
| [14-core-logic-testing-standard.md](./14-core-logic-testing-standard.md) | 核心逻辑分层、Backend 路由、输入安全与单元测试规范 |
| [15-app-control-center.md](./15-app-control-center.md) | App Control Center：应用清单、详情、组件、AppOps、权限、批量策略、Debloat、APK 导出 |
| [16-industry-root-capability-map.md](./16-industry-root-capability-map.md) | Root 工具行业能力地图、竞品/开源能力调研、RootTools 缺口与后续优先级 |
| [reference-projects.md](./reference-projects.md) | 本地忽略的开源参考仓库、版本、许可证边界与更新规则 |
| [17-engineering-governance-and-ai-workflow.md](./17-engineering-governance-and-ai-workflow.md) | 工程治理与 AI 协作：模块化、组件库、多语言、提交、CI、质量门禁、Skills 与模板 |
| [18-engineering-execution-ledger.md](./18-engineering-execution-ledger.md) | 工程治理实施账本：质量基线、Design System、i18n、composition root、巨型文件拆分与 module gate |
| [adr/](./adr/) | Architecture Decision Records：composition root、模块化边界等长期工程决策 |
| [19-environment-integrity-center.md](./19-environment-integrity-center.md) | Environment Integrity Center：Hook/篡改、Root runtime、Boot/ROM、设备表面、Sandbox、Attestation 与可信基线 |
| [20-termux-developer-runtime.md](./20-termux-developer-runtime.md) | Termux / Developer Runtime Bridge：双向 Intent、CLI、managed task、SSH/MCP 执行平面与安全边界 |
| [21-scheduled-actions.md](./21-scheduled-actions.md) | 定时动作与调度边界：任务模型、执行窗口、持久化与安全约束 |
| [22-shadow-display.md](./22-shadow-display.md) | Shadow Display：Root-owned Virtual Display、隔离输入、Preview、Automation 与跨 OEM 状态探针 |
| [23-ad-governance.md](./23-ad-governance.md) | 广告治理：只读识别、跳过/关闭入口边界与设备验证 |
| [23-product-navigation-and-home.md](./23-product-navigation-and-home.md) | 产品导航与首页：5 Tab、multiple back stacks、Health Verdict、Attention、Recent Activity 与领域 Landing Page |
| [24-lag-forensics.md](./24-lag-forensics.md) | Xiaomi / Qualcomm 系统级卡顿的低开销滚动取证、PSI 阈值、证据预算与后台性能契约 |
| [25-agent-session-presence.md](./25-agent-session-presence.md) | Agent Session Presence：统一任务状态、持续通知、悬浮窗、详情页、Shadow Preview 与 OEM 顶部状态适配边界 |
| [28-root-tailscale-coexistence.md](./28-root-tailscale-coexistence.md) | Root Tailscale：与 Hiddify 共存的 Root overlay、认证、路由和恢复模型 |
| [29-github-release.md](./29-github-release.md) | Android GitHub 仓库、固定签名、tag 驱动的 APK Release 流程 |
| [adr/0004-consolidate-roottools-projects.md](./adr/0004-consolidate-roottools-projects.md) | RootTools 相关工程、功能与 Git 历史的归一化收口决策 |
| [companion/nfc-tools/architecture.md](./companion/nfc-tools/architecture.md) | NFC Tools companion：Reader/HCE、OEM Provider、Root 只读诊断与数据边界 |
| [companion/nfc-tools/technical-solution.md](./companion/nfc-tools/technical-solution.md) | NFC / eSE / HCE 技术方案与安全边界 |

## 工程原则

1. **单 app 模块优先**：文档和 feature 分层不等于 Gradle 多模块化。除非后续出现明确编译、复用或发布边界，否则不新增 Android 子模块。
2. **首页只做结论与高频触达**：首页优先回答“设备现在怎么样”，再提供少量 Quick Actions；完整能力按 `首页 / 应用 / 设备 / 诊断 / 系统` 五个一级领域组织。
3. **一个能力一个真值源**：例如 CPU sysfs 只由 `CpuPolicyController` 写；MacroDroid、Quick Tile、ADB 都只能调用统一 Controller，不能各自直接写系统节点。
4. **读写分离**：监控采集器尽量只读；Root 修改必须通过明确的 Action / Controller。
5. **安全优先**：不关闭 Thermal、不隐藏失败、不默认永久开放 ADB、不自动绕过 Magisk / Android 权限确认。
6. **低开销观测**：默认 15～30 秒级采样；只有用户进入实时详情页时才提高频率，避免“监控工具成为发热源”。
7. **可回滚**：所有系统修改都要记录前值，能通过 UI 或命令恢复。

## 一级导航目标

- **首页**：Health Verdict、Quick Actions、Attention、Recent Activity。
- **应用**：App Control、Startup、Ad Governance、Permissions、Components、AppOps。
- **设备**：Performance、Battery/Thermal、Root ADB、Shadow Display、Network、Storage/IO。
- **诊断**：Health Dashboard、Process/Root Shell/WakeLock、Environment Integrity。
- **系统**：Modules、Shizuku/Sui、Common Actions/Automation、Developer Runtime。

### 已扩展能力

- 网络诊断
- 存储 / IO
- 常用 Root 操作
- 自动化入口
- 设备快照 / 报告
- Shizuku / Sui 特权桥接（已落地：typed UserService / PrivilegeRouter / self-test，Framework 操作优先 Shizuku/Sui、Root fallback）
- App Control Center（核心 Inventory / Detail / Components / Permission / AppOps 已落地并持续扩展）
- Environment Integrity Center（基础实现已落地；Hardware Attestation 已完成 Samsung 真机验收，其余扫描模式持续扩展/验收）
- Developer Runtime / Termux Bridge（规划完成；当前 Samsung Play Termux 先走 reverse bridge）
- Agent Session Presence（通用层已实现；Samsung 正在完成通知 / Overlay / Shadow Preview / 生命周期整体验收，Xiaomi Focus / HyperIsland 后续只做 adapter）

### 行业调研后确认的后续一级领域

- Backup & Recovery
- Firewall & App Network
- Multi-root Runtime（Magisk / KernelSU / APatch）
- Charge Controller
- Environment Integrity / Baseline Drift
- Developer Runtime / Termux / Agent execution plane

所有新增卡片必须先在 `00-product-roadmap.md` 登记，再创建对应详情页和文档章节。
