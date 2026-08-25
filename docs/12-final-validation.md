# 最终真机验收

## 当前状态

工程侧已经完成：

- Debug build
- Release unsigned build
- lintDebug
- Samsung 各数据源只读验证
- 早期 APK 的 Root / Dashboard / Performance / Root ADB 真机验证

当前环境两次阻止执行最终 `adb install -r`，因此下面这份清单作为最新版 APK 的最终验收入口。

## 1. 安装

```bash
adb -s 100.91.126.56:5555 install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

安装后不要重启设备。

## 2. 首页

确认 12 张卡片全部出现且没有 `NEXT / TODO`：

1. 设备看板
2. 性能控制
3. Root ADB
4. 权限中心
5. 启动治理
6. 应用冻结
7. 进程诊断
8. Root 模块
9. 常用操作
10. 网络诊断
11. 存储与 IO
12. 电池与温控

## 3. 权限

- 通知权限只申请一次
- Root 由前台 Activity 触发 Magisk 授权
- 授权完成后首页显示 `ROOT`
- 同一 App 进程连续刷新 Dashboard、进入 Performance/Startup/Diagnostics/Modules，以及 CPU policy 低频后台采样时，
  Magisk grant toast 不应按命令/采样周期重复出现；只允许进程首次创建 root session 时出现一次
- CPU Policy Service 只在 Root 成功后启动

## 4. 设备看板

对照：

```bash
adb shell cat /proc/loadavg
adb shell cat /proc/meminfo
adb shell cat /proc/pressure/memory
adb shell cat /proc/pressure/io
adb shell dumpsys thermalservice
```

看板停留 5 分钟后：

```bash
adb shell top -b -n 1 -p $(adb shell pidof com.arthur.roottools)
```

不得出现持续高 CPU busy-loop。

## 5. 性能控制

- Auto / Cool / Performance 可切换
- Thermal > 0 时不得把系统当前 `scaling_max_freq` 向上抬高
- Cool Responsive：效率簇保持完整峰值，性能簇/Prime 仅削高频尾段；Vendor cap 更低时不得向上覆盖
- RootShell timeout：超时 payload 必须结束，下一条命令可继续复用共享 root session
- Xiaomi / Toybox process-group：timeout 后后台 child PID 不得继续运行
- 多次 Diagnostics root attribution 后不得出现长期高 CPU 的 `tr` / root shell orphan

## Xiaomi 14 validation — 2026-08-24

设备：`23127PN0CC / houji / Android 15 / HyperOS 2.0.207.0.VNCCNXM`

### RootShell lifecycle

- 新 APK `adb install -r` 成功；
- 真机 `ps` 可观察到 RootTools payload 以 `timeout -k 0.2s <N>s sh -c ...` 运行；
- payload 完成后 `timeout` 消失，仅保留共享 root `sh`；
- 共享 root `sh` 稳定态 CPU=0.0%；
- 未再次观察到长期存活的 `tr`；
- RootTools 主进程退后台后 `top`=0.0%，`dumpsys cpuinfo` 当前窗口约 0.6%。

### Cool / Vendor ownership

模式已切到 `COOL` 并持久化。当前 HyperOS Vendor cap：

```text
policy0 2.0352 < Cool 2.2656 GHz
policy2 2.3232 < Cool 2.7072 GHz
policy5 2.3232 < Cool 2.5152 GHz
policy7 1.9392 < Cool 2.5536 GHz
```

事件日志只有 `AUTO→COOL`，没有新增 `CAP_WRITE`，且 prefs 中没有 `owned_max_*`，证明 RootTools 没有向上覆盖当前更严格的 HyperOS cap。

`CpuPolicyService` 在 Cool 下保持 `isForeground=true / startRequested=true`，用于后续 60s/30s ownership-aware reconcile。

### Responsiveness smoke test

Cool 模式下对 Android Settings 做 5 次 cold-start：

```text
856 / 651 / 759 / 550 / 559 ms
median = 651 ms
```

没有出现秒级异常卡顿或启动失败。该测试主要验证持续 Cool monitor 没有引入明显 UI 启动回归；当前 Vendor cap 已经比 RootTools Cool 目标更严格，因此本轮没有人为抬频来制造 A/B 条件。

### Thermal trend after validation load

测试/安装活跃阶段 Skin 约 38°C；退后台后继续回落到约 37.2°C，Battery 约 33.1°C，CPU 约 45~47°C。未出现此前 41h orphan root process 对应的持续 90%+ 单核占用。
- Performance 15 分钟后回 Auto
- 不关闭 Samsung Thermal

## 6. Root ADB

- 读取 `100.91.126.56:5555`
- 开启动作可验证
- **关闭动作只在手机旁边时验证**，防止远程链路被切断

## 7. Startup / Apps

- Startup 页面能解析本次 `am_proc_start`
- Protected：Tailscale / RootLab / MacroDroid / GKD / Root Tools 不出现 Freeze 入口
- Appium Test Mode 能正确增加/删除 Notification Listener 和 Doze whitelist
- 普通 App Freeze 后能重新 Enable

## 8. Diagnostics

- Top CPU 与 `top` 对照
- 当前正常 Root shell 不误报异常
- WakeLock 与 `dumpsys power` 对照
- Root shell pipe attribution 必须是用户点击后才执行

## 9. Modules

- `zygisk_vector` 显示 Protected
- `zygisk_lsposed` 显示 disabled marker
- Vector 中 Hail / SpoofMyDevice / WeChatTablet 状态正确
- Magisk marker 改动只显示 pending reboot，不自动 reboot

## 10. Network

对照：

```bash
adb shell ip -4 -o addr show
adb shell dumpsys connectivity
adb shell su -c 'ss -ltn'
```

- tun0 / wlan0 / rmnet_data0 地址正确
- ADB 5555 listen 正确
- Ping 只在用户点击时运行 3 次

## 11. Storage / Battery

- Storage 与 `df -k` / `/proc/pressure/io` 对照
- Battery / Thermal 与 `dumpsys battery` / `dumpsys thermalservice` 对照
- Battery Protection 开关与 `settings get global protect_battery` 一致

## 12. Actions / Automation

- 常用动作允许收藏，但仍要求确认
- Automation Receiver 只接受 explicit component：

```text
com.arthur.roottools/.automation.ActionRouterReceiver
```

- 错误 token / 隐式广播 / 未知 command 不执行
- Diagnostic report 可生成并通过 FileProvider 分享
- 当前版本没有 reboot / recovery / bootloader 可执行入口

## 13. Quick Tile

将以下两个 Tile 添加到 Samsung Quick Settings：

- CPU 档位
- Root ADB

确认标题、状态和点击反馈正确。

## Done

只有以上全部通过，`docs/09-delivery-ledger.md` 最后一项才可以勾选。

## 14. Product Navigation / Home UX — Xiaomi 14（2026-08-24）

- 5 个一级 Tab：`首页 / 应用 / 设备 / 诊断 / 系统` 全部可达；
- 1200×2670 竖屏底栏标签完整，无可见裁切；
- Apps / Device / Diagnostics / System Landing Page 第一屏通过截图验收；
- Multiple back stacks：App Control -> Device/Performance -> Apps 后恢复 App Control；
- `open_screen=adb` 与 `open_screen=integrity` typed external entry 通过；
- Integrity Back 返回 Diagnostics Landing；
- Loading Verdict 不再用空采样误报内存压力；
- 稳定态 Home Verdict 为“设备状态稳定”；
- 3 次冷启动：1361 / 1522 / 1407 ms，中位数 1407 ms；
- App 退后台 5 秒后抽样 CPU 0.0%；
- 未发现 RootTools 残留 `tr` / `timeout` 子进程；
- ADB 5555 / Tailscale 管理链路在安装与视觉验证后仍可达。

详细截图与验证记录：`docs/validation/navigation-2026-08-24.md`。

## 15. Agent Session Presence — Samsung SM-S908E

通用后台 Agent 可见性必须先在 Samsung / Generic Android 跑通，再把 Xiaomi Focus / HyperIsland 作为 OEM adapter 叠加。

### Notification fallback

- 未授权 `SYSTEM_ALERT_WINDOW` 时 Agent Session 仍能启动；
- `AgentSessionService` 为 Android 14 `specialUse` foreground service；
- running notification 使用低重要度 channel，包含 Pause/Resume 与 Stop；
- 此状态下不得存在 RootTools `TYPE_APPLICATION_OVERLAY` window。

### Overlay

- 只通过 Samsung Settings 标准「悬浮窗」页面授权；
- collapsed 约 56dp，展开约 320dp，均保持在 logical display bounds 内；
- collapsed / hidden 状态 Shadow Preview mtime 不持续变化；
- expanded + Running 才允许约 0.5fps Preview；
- One UI 敏感系统页强制隐藏 non-system overlay 时必须接受系统策略，不绕过。

### Shadow / Agent integration

- Samsung VirtualDisplay 状态必须显示 `running / processAlive / displayActive=true`；
- target App 启动后 `AgentSessionState.targetPackage / targetLabel / currentStep` 同步；
- expanded overlay 可读取真实 Shadow Preview；
- Presence Stop 不得隐式销毁非该 Session 独占的 Shadow Display。

### Lifecycle

- 通知点击在 RootTools 冷启动、Activity 已运行两种情况下都进入 `agent-session`；
- Pause / Resume / Stop 在 Detail、Notification、persisted state 一致；
- 覆盖安装 / 进程重建后 active session 可按策略恢复 presence surface；
- 息屏 / 锁屏后 Notification / Session 存活，Overlay 不唤醒物理屏；
- 最终 Stop 后 Service / active Notification / Overlay 清理，CPU 稳态无持续 busy-loop。

详细过程：`docs/validation/agent-session-samsung-2026-08-25.md`。
