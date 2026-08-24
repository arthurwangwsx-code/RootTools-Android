# 广告治理中心

## 1. Problem / User Story

- 用户场景：Root Tools 已经运行在一台长期在线、通过 Tailscale + Root ADB 远程管理的小米 14 上。用户希望自动跳过知乎、京东等应用的开屏/弹窗广告，并把广告治理能力收口到 Root Tools，而不是长期依赖多个互不透明的第三方工具。
- 现有问题：GKD 已经在真机上验证可自动点击广告关闭控件，但 Root Tools 无法查看其运行状态、订阅与触发结果；AdAway 已安装但当前 Magisk 环境没有向它暴露可用 `su`，强行切到 VPN 模式可能与 Tailscale 管理链路冲突；HyperOS 系统广告包存在，但直接 disable / uninstall 属于高风险且 OEM 行为不稳定。
- 成功标准：Root Tools 提供一个独立的「广告治理」页面，首先以只读方式汇总 GKD、网络 hosts、AdAway、Tailscale 与 HyperOS 广告组件状态，并能看到知乎/京东的真实 GKD 触发记录。第一阶段不绕过 Magisk / Shizuku 授权，不自动修改系统包，不占用 Android VPN 槽位。

## 2. Scope

### In scope — Phase 1

- GKD 安装、进程、工作模式、自动化引擎、订阅文件数量只读探测。
- 从 GKD 自有 external files 日志中解析最近广告动作，聚合知乎 / 京东触发次数。
- `/system/etc/hosts` 行数与 systemless hosts mount 只读探测。
- AdAway 安装 / 进程状态只读探测。
- Tailscale 是否活跃、是否存在 100.x tailnet 地址只读探测。
- Tailscale 地址只接受 `tun*` 上的 100.x，避免把 `rmnet_data*` 的运营商 CGNAT 100.64/10 地址误认成 tailnet。
- HyperOS `com.miui.systemAdSolution` / `com.miui.analytics` 安装与 enabled-state 只读探测。
- Root Tools 独立 Compose 页面 + Toolbox Registry 入口；允许用户显式打开 GKD / AdAway。

### Out of scope — Phase 1

- 不替 AdAway 写 Magisk superuser policy，不通过 symlink / hide 配置绕过其 Root 检测。
- 不启用 AdAway VPN 模式，避免与当前 Tailscale 远程管理链路竞争 VPNService。
- 不自动 disable / uninstall HyperOS 系统广告 package。
- 不自动修改 `/system/etc/hosts`，不安装 systemless hosts 模块，不自动下载第三方 hosts 列表。
- 不自动操作支付、订单、账号授权等非广告 UI。

## 3. Current State

- 已有 Repository / Controller：`RootShell` 负责 root-only Linux read；`PrivilegeRouter` / package controllers 负责 Framework mutation；`ToolRegistry` 负责首页能力入口。
- 已有 UI：Toolbox Home + feature-scoped detail screen 模式。
- 可复用能力：`RootToolsDetailHeader`、`RootToolsSectionCard`、`RootToolsStatusChip`、`RootToolsKeyValueRow`、`RootToolsRiskBanner`。
- 真机证据（2026-08-24，小米 14 / Android 15 / HyperOS 2）：
  - GKD 1.12.1 + Shizuku 自动化模式已运行。
  - 知乎开屏 `com.zhihu.android:id/btn_skip` / `text=跳过3` 被 GKD `clickNode` 成功点击。
  - 京东全屏弹窗与横幅广告关闭动作多次成功触发。
  - `/system/etc/hosts` 当前只有默认 localhost 两行。
  - AdAway 6.1.4 已安装，但其 App 内 Root 检测未获得 `su`；ADB Root 本身正常。

## 4. Capability / Permission

| Capability | Backend | Permission | Fallback | Failure UI |
|---|---|---|---|---|
| GKD / hosts / process read | RootShell | Magisk Root Tools grant | none | 显示 Root unavailable / probe error |
| Package presence | RootShell fixed probe | Root | none | Unknown |
| Open GKD / AdAway | Android launch Intent | none | none | 按钮不可用 |

Phase 1 没有 privileged write path。

## 5. Architecture

```text
AdGovernanceScreen
  -> AdGovernanceViewModel
  -> AdGovernanceRepository
  -> AdGovernanceProbeParser (pure JVM)
  -> RootShell (fixed read-only command)
  -> Android / GKD external files / procfs
```

## 6. Domain Model / API

- `AdGovernanceSnapshot`：Root、GKD、AdAway、hosts、Tailscale、HyperOS、最近触发。
- `AdActionEvent`：time / appId / groupName。
- Query：`AdGovernanceRepository.read()`。
- Action：Phase 1 无 privileged action；UI 仅显式 launch 第三方 App。
- Error：collector transport failure 以 `probeError` / unavailable state 呈现，不伪造 ready。

## 7. Safety / Rollback

- 风险等级：Phase 1 = Safe / read-only。
- 前值：无需保存。
- 回滚：无需回滚。
- 是否二次确认：无写操作，不需要。
- 是否可能远程失联：不会；明确不修改 Root ADB / Tailscale / VPN / DNS。

后续若进入 systemless hosts Phase 2，必须新建 typed Controller、保存 before-state、提供 disable marker / rollback，并在远程链路安全矩阵通过后才能启用。

## 8. UI / UX

- Screen：广告治理总览、GKD UI 跳过、真机触发、网络层、HyperOS 层、安全边界。
- State：Loading / Ready / Error / Root unavailable。
- Shared components：复用 Root Tools design system，不在旧 `DashboardScreen.kt` 内实现 feature body。
- i18n：默认英文 + `values-zh-rCN`；产品名、package name、GKD / AdAway / Tailscale 不翻译。
- Accessibility：按钮与状态文本均使用资源字符串。

## 9. Test Matrix

- happy path：GKD automation mode 2 + user service + Shizuku server => ready；解析知乎 / 京东 action log。
- unavailable：Root / GKD / log 不存在时返回稳定默认值。
- malformed input：collector section 缺失、store 内容损坏不得 crash。
- fallback：无自动 fallback；read probe 失败直接 unavailable。
- protected target：Phase 1 无 mutation。
- rollback：Phase 1 无 mutation。
- regression：`ToolRegistry` 仍能完整 route 所有已有工具。
- real device：Xiaomi 14 安装最新版 APK，页面状态与 `adb shell` probe 对照；再次冷启动知乎 / 京东确认触发计数可更新。

## 10. Implementation Plan

1. contract + parser JVM tests；
2. fixed read-only probe + parser；
3. feature ViewModel / UI；
4. Tool Registry 最小路由接入；
5. quality guard + unit + lint + debug build；
6. Xiaomi 14 覆盖安装与状态对照；
7. 冷启动知乎 / 京东回归；
8. ledger 更新。

## 11. Acceptance Criteria

- [x] 首页出现「广告治理」独立卡片且能进入详情页。
- [x] 页面能识别 GKD 已安装 / running / automation ready / positive subscription count。
- [x] 页面能显示知乎 / 京东最近真实触发计数与最近规则类别。
- [x] 页面明确显示当前 hosts 未启用网络级过滤时的状态。
- [x] 页面识别 Tailscale 活跃，并明确避免 VPN-based blocker 与远程管理冲突。
- [x] 不修改 HyperOS 系统包、不修改 Magisk superuser policy、不修改 hosts。
- [x] JVM parser tests、`security_guard.py`、`testDebugUnitTest + lintDebug + assembleDebug` 通过。
- [x] Xiaomi 14 真机覆盖安装与页面 smoke test 通过。

### 2026-08-24 Xiaomi 14 验收记录

- 设备：`23127PN0CC` / Android 15 / HyperOS 2.0；Root ADB 通过 Tailscale `100.110.5.86:5555`。
- 首页卡片：`广告治理` / `GKD · hosts · HyperOS 广告面` / `ADS` 已在真机 Compose UI dump 中确认。
- 详情页在 GKD 实际运行期间采集到：UI 引擎 `正常`、自动化模式 `Shizuku 自动化`、特权工作进程 `是`、订阅 `1`。
- GKD 当前日志窗口：6 次真实动作，其中知乎 1 次（最近 `开屏广告`），京东 5 次（最近 `局部广告-横幅广告`）。
- Hosts：`2 行`、过滤 `未启用`、Systemless hosts `否`。
- Tailnet：`在线`，地址 `100.110.5.86`。真机曾同时存在 `rmnet_data1=100.87.248.227/29`；collector 已修正为只接受 `tun*` 上的 100.x，避免运营商 CGNAT 误判。
- AdAway：已安装、进程可见，但 Phase 1 仅观察，不修改其 Magisk Root policy，也不启用 VPN 模式。
- HyperOS：`com.miui.systemAdSolution` 与 `com.miui.analytics` 均检测为启用；页面保持只读。
- 为了获取 UI dump 曾临时 force-stop GKD；验收结束后已从桌面重新启动，确认 `li.songe.gkd`、`li.songe.gkd:shizuku-user-service`、`shizuku_server` 均运行，日志再次出现 `自动化已启动` / `Shizuku 服务连接成功`。
- `quality_guard.py` 当前仍被本任务之前已经存在的 `DeveloperRuntimeScreen.kt=1328` 和 DeveloperRuntime legacy dependency 两项工程债阻塞；本功能没有新增对应违规。`security_guard.py` 与 Gradle baseline gate 均通过。

## 12. Open Questions

- Phase 2 是否由 Root Tools 自己管理 systemless hosts，还是只对接一个能在当前 Magisk 环境正常授权的专用 blocker？
- hosts source 的 license、更新签名、回滚和误杀白名单策略需要在任何网络 mutation 前单独设计。
