# 影子屏 / AI 后台虚拟页面

## 1. Problem / User Story

- 用户场景：Root 手机需要在物理主屏继续正常使用 ChatGPT、微信等 App 的同时，让自动化/AI 在独立虚拟显示器中运行 Google Maps、Chrome 等第三方 App。
- 现有问题：ADB 自动化默认抢占 Display 0；电脑端 scrcpy `--new-display` 原型已经证明 Xiaomi 14 / HyperOS 2 可以运行第二 Display，但依赖电脑维持生命周期。
- 成功标准：Root Tools 能在手机端创建、销毁、查看和操作可信 Virtual Display；主屏保持独立；电脑断开后虚拟屏仍由手机端 Root 守护进程维持。

## 2. Scope

### In scope

- Root Tools 内新增「影子屏」工具卡和独立页面；
- 创建 / 销毁单个受管 Virtual Display；
- 查看 Display ID、分辨率、DPI、运行状态与后端；
- 在影子屏启动指定 package；
- 向影子屏注入 tap / swipe / text；
- 按需抓取缩略预览；
- 所有入口使用 typed Controller，不暴露任意 shell；
- start / stop / launch 写入 Root Action Audit；
- Xiaomi 14 / HyperOS 2 真机验收。

### Out of scope

- 绕过 `FLAG_SECURE`、DRM、银行安全页面；
- 模拟 HOME / RECENTS 等全局系统导航键；
- 多个并行虚拟屏；
- ROM Framework patch；
- 自动化 Agent 的视觉模型与任务规划本身。

## 3. Current State

- `PrivilegeRouter` 是 Framework/Root 语义操作的统一边界；
- `RootShell` 提供单进程持久 Magisk root session；
- `RootActionAuditStore` 可记录 start/stop/launch；
- 电脑端原型已在 Xiaomi 14 上验证 720×1600 secondary display、`am start --display` 和 `input -d` 普通触控隔离；
- HyperOS 2 上 `KEYCODE_HOME` 即使指定 display 仍可能影响 Display 0，因此正式能力禁止 HOME/RECENTS 注入。

## 4. Capability / Permission

| Capability | Backend | Permission | Fallback | Failure UI |
|---|---|---|---|---|
| Virtual Display 生命周期 | Root app_process daemon via PrivilegeRouter | Magisk Root | 无 | 显示 Root/daemon 错误 |
| Activity launch | RootShell typed route | Root | 无 | 显示 package / launch 错误 |
| Display input | RootShell typed route | Root | 无 | 显示 input 错误 |
| Preview capture | daemon ImageReader + RootShell read | Root | 无 | 保留现有显示并提示 capture 失败 |

首版把生命周期限定为 Root-only capability：VirtualDisplay Java 对象必须由长生命周期进程持有，单次 Shizuku shell 命令无法承担对象生命周期。后续若 dedicated Sui UserService 能提供同等生命周期，再把同一 Controller 路由扩展到 Sui；UI/API 不变。

## 5. Architecture

```text
ShadowDisplayScreen
 -> ShadowDisplayViewModel
 -> ShadowDisplayController
 -> PrivilegeRouter
 -> RootShell
 -> app_process ShadowDisplayDaemon
 -> DisplayManager / ImageReader / Input / ActivityManager
```

`ShadowDisplayDaemon` 不注册外部 Android 组件，不接受网络请求，也不解析任意 shell。它只接收经过 Policy 校验的 width / height / dpi / stateDir，持有一个 VirtualDisplay，并把只读状态和按需缩略图写入 Root-only state directory。

## 6. Domain Model / API

- `ShadowDisplayConfig(width, height, densityDpi)`；
- `ShadowDisplayStatus(state, displayId, pid, config, processAlive, activeDisplays, error)`；
- `ShadowDisplayPolicy`：配置、坐标、滑动、文本、package 校验；
- `ShadowDisplayStatusParser`：解析 daemon + system probe；
- Controller query：`status()`、`capturePreview()`；
- Controller action：`start()`、`stop()`、`launchPackage()`、`tap()`、`swipe()`、`typeText()`。

## 7. Safety / Rollback

- 风险等级：Caution；不会修改 boot image、SELinux、Magisk 配置或 ADB 链路；
- 前值：start 前记录 stopped/running；stop 前记录 display/config；
- 回滚：Stop 终止 daemon，进程退出后 VirtualDisplay 自动释放；
- 二次确认：Stop 会销毁影子屏上的 task，UI 明确提示；Start/普通输入无需危险确认；
- 远程失联：不会关闭 ADB/Tailscale/Wi‑Fi；
- 全局键：不提供 HOME/RECENTS 注入，避免 HyperOS 把物理主屏带回桌面；
- 安全画面：不设置 secure capture 绕过，受保护窗口保持系统默认黑屏/拒绝行为。

## 8. UI / UX

- 首页增加「影子屏」卡片；
- 独立页面使用现有 `RootToolsDetailHeader`、`RootToolsSectionCard`、`RootToolsStatusChip`、`RootToolsRiskBanner`；
- 状态：Loading / Stopped / Running / Error；
- 支持默认 720×1600 / 320dpi 和可编辑尺寸；
- App 启动提供 package 输入与 Maps / Chrome / Settings 快捷项；
- Input 区提供 tap、swipe、text；
- Preview 只按用户请求刷新，不持续编码/截图；
- default English + `values-zh-rCN`。

## 9. Test Matrix

- happy path：合法配置 / 状态解析 / 坐标输入；
- unavailable：无 Root、daemon 未启动；
- invalid input：过大尺寸、负坐标、超长文本、畸形 package；
- fallback：首版无跨 backend 自动 fallback；
- protected target：不提供 secure-capture 绕过；
- rollback：stop 后 daemon 退出且 display 不再出现在 active displays；
- regression：PrivilegeRoutingPolicy 的既有 Shizuku / Root 路由保持不变；
- real device：Xiaomi 14 Android 15 / HyperOS 2，主屏 ChatGPT 保持 resumed，同时影子屏 Maps resumed，tap 不改变 Display 0 topResumedActivity。

## 10. Implementation Plan

1. contract + pure policy/parser tests；
2. Root app_process daemon；
3. PrivilegeRouter typed API + audit Controller；
4. ViewModel / Compose UI / registry / i18n；
5. targeted tests + quality guard + lint + assemble；
6. 安装 Xiaomi 14 并执行 lifecycle / launch / input / preview / main-display isolation 验收；
7. 更新 delivery ledger。

## 11. Acceptance Criteria

- [x] Root Tools 首页出现「影子屏」卡片；
- [x] 手机端可独立创建 720×1600 / 320dpi Virtual Display；
- [x] Google Maps 可在 secondary display Resumed；
- [x] 物理 Display 0 同时保持用户当前 App；
- [ ] `tap/swipe/text` 三类输入均完成无并发干扰的真机抽样；`tap` 与 `swipe` 已确认不改变 Display 0，`text` 已完成字段输入与发送触发，但抽样期间物理主屏存在并发操作，隔离结论不做过度归因；
- [x] Preview 可按需刷新并在 Root Tools 页面解码展示；
- [x] Stop 后 display 和 daemon 均消失；
- [x] Root Tools App force-stop 后 daemon / Virtual Display 仍存活，重新打开 App 可恢复识别并停止；
- [x] daemon PID 操作同时校验 `/proc/<pid>/cmdline` 身份，避免 stale PID / PID reuse 误杀其他进程；
- [x] 无任意 shell UI 接口；
- [x] JVM tests / `lintDebug` / `assembleDebug` / `assembleRelease` / security guard 通过；
- [x] Shadow Display 改动未增加 quality guard 新违规；仓库级 guard 仍被既有 Developer Runtime 两项历史债阻塞；
- [x] Xiaomi 14 / Android 15 / HyperOS 2 真机通过核心生命周期、Maps secondary-display、tap 隔离、Preview、App force-stop 生存与恢复接管。
- [x] 最新 PID/cmdline 身份校验安全硬化 APK 已覆盖安装；最终实机再次创建 Display 47 并正常 Stop，结束后仅剩 Display 0。

## 12. Open Questions

- 后续是否把 daemon 生命周期迁到 dedicated Sui UserService；
- 后续是否给 AI Runtime 增加内部 Binder/Automation typed API；
- 不同 OEM 对 `OWN_FOCUS` / system decorations 的兼容矩阵需要逐步补齐。
