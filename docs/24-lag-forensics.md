# Rolling Lag Forensics

## 1. Problem

Xiaomi 14 / HyperOS 2.0.207.0 在 2026-08-24 22:23~22:57 出现一次与中午 RootTools orphan `tr` 泄漏无关的系统级卡顿。现场保留下来的 ANR、MiSight、PSI 与 kernel blocked-state 指向：

- Memory / IO / CPU PSI 同时显著升高；
- `SystemUI` 与微信先后 ANR；
- F2FS page fault / writeback、UFS、display commit、memlat / bwmon 等多个线程同时进入 D state；
- 多条路径收敛到 Qualcomm ICC / DCVS / RPMh bandwidth vote；
- 当时 Thermal `temp_state=0`，CPU 约 51°C、Virtual/Skin 约 41°C，不符合“严重过热先触发全局卡顿”的时间线；
- UFS 当前健康计数与寿命值正常，没有证据支持闪存硬件损坏。

这类故障重启后会清掉关键运行态，因此 RootTools 需要在不成为性能负担的前提下，保留异常开始前后的轻量上下文。

## 2. Product goal

Rolling Lag Forensics 的优先级顺序是：

1. RootTools 自己不能制造新的卡顿、发热或高频唤醒；
2. 正常状态不运行 `top` / `dumpsys` / `logcat` 循环；
3. 只在持续或严重系统压力时抓一次有边界的证据；
4. 重启前已经落盘的事故证据在重启后仍可查看；
5. 只读观测，不修改 ICC / RPMh / UFS / Thermal 等系统状态。

## 3. Architecture

```text
CpuPolicyService（已有 foreground service）
        │
        ├─ DeviceRepository existing snapshot
        │    └─ + /proc/pressure/{memory,io,cpu}
        │       + selected /proc/meminfo
        │
        └─ LagForensicsMonitor
             ├─ pure LagForensicsPolicy
             ├─ in-memory ring history (20 points)
             └─ abnormal only
                    ↓
                bounded evidence capture
                    ↓
                LagForensicsStore
```

不新增第二个常驻 Android Service，不新增 WakeLock，不新增 foreground watcher。

同时 `DeviceRepository` 去掉原先每轮独立的 `id -u` Root probe，直接以本轮 snapshot command 的成功结果判断 Root，因此虽然增加了几个 `/proc` 读取，正常轮询的 root command 次数反而减少。

## 4. Sampling and overhead contract

稳定 Normal：

- 屏幕亮：约 60 秒一次；
- 屏幕灭：约 120 秒一次；
- Performance + screen-off：60 秒；
- Warm / Moderate / Severe：30 秒，优先保证 Thermal policy 及时性。

正常轮询只增加：

```text
cat /proc/pressure/memory
cat /proc/pressure/io
cat /proc/pressure/cpu
grep selected /proc/meminfo fields
```

禁止在正常路径执行：

- continuous `top`；
- `dumpsys` 全量遍历；
- `dmesg` 全量扫描；
- `/proc/<pid>` 全进程深度遍历；
- 周期性写文件。

Notification 只有文本发生变化时才重新 `notify()`，稳定状态不再每轮重复刷新通知。

## 5. Pressure policy

### Elevated

任一条件满足：

- memory `some avg10 >= 10`；
- memory `full avg10 >= 2`；
- IO `some avg10 >= 10`；
- IO `full avg10 >= 3`；
- CPU `some avg10 >= 40`；
- `MemAvailable < 10%`。

Elevated 必须连续两次采样才允许触发证据采集，避免瞬态波动。

### Severe

任一条件满足：

- memory `some avg10 >= 25`；
- memory `full avg10 >= 10`；
- IO `some avg10 >= 30`；
- IO `full avg10 >= 10`；
- CPU `some avg10 >= 60`；
- `MemAvailable < 6%`。

Severe 可以在一次采样后触发，但仍受全局 capture cooldown 保护。

## 6. Incident capture budget

异常取证固定契约：

- shell hard timeout：5 秒；
- evidence command output：最多约 96k characters；
- 最终 incident file：最多约 128k characters；
- capture cooldown：10 分钟；
- 最多保留 5 个事故文件；
- capture 通过 RootShell process-group timeout 执行，超时 descendant 必须被回收。

证据只包含有限窗口：PSI、selected meminfo、ZRAM、D-state process、bounded top、CPU policy、最近 ANR / DropBox 文件名、以及过滤后的短 kernel tail。

## 7. Boot behavior

Forensics 默认开启。`BOOT_COMPLETED` / `USER_UNLOCKED` 时复用现有 BootReceiver 启动 CpuPolicyService；若用户关闭 forensics，则不因 forensics 单独恢复服务。

启动失败不能循环高频重试，也不能绕过 Android / HyperOS foreground-service 限制。

## 8. UI

Diagnostics 页提供独立 `LagForensicsCard`：

- 当前 Memory / IO PSI；
- 当前 pressure level；
- 已保存 incident 数；
- 最近 incident 摘要；
- 启停开关；
- 复制最近取证证据。

UI / ViewModel 放在 `feature/diagnostics`，legacy `DashboardScreen` 只保留 feature entry。

## 9. Tests

JVM pure tests：

- healthy pressure → Normal；
- sustained pressure → Elevated；
- 2026-08-24 incident-like PSI → Severe；
- Elevated 要求连续两次；
- capture cooldown 阻止重复重抓；
- stable interactive / screen-off / Performance / thermal cadence。

## 10. Real-device acceptance

Xiaomi 14 必须验证：

1. debug APK 可覆盖安装并启动；
2. background RootTools CPU 长期接近 0%，没有持续单核占用；
3. 屏幕关闭后没有高频 RootShell child；
4. 无 residual `tr` / `timeout` / orphan shell；
5. RootTools 不持有不必要 WakeLock；
6. 稳定状态不生成 incident file；
7. PSI 命令与 ADB 手工读取一致；
8. evidence command 在 Xiaomi Toybox 上语法有效并能在 5 秒预算内结束；
9. 安装后的温度趋势没有因为 RootTools 后台服务出现持续上升；
10. boot / user-unlock 恢复不产生启动风暴。

## 11. Residual risk

该功能是“取证”而不是修复 Qualcomm ICC / RPMh 驱动。若再次捕获到相同 blocked-state，需要基于新 incident 判断是 HyperOS vendor bug、特定 video-call workload、root/module 干扰，还是其它设备状态组合；在证据充分前不主动修改 Qualcomm interconnect、RPMh、UFS clock gating 或厂商 Thermal 配置。

## 12. Xiaomi 14 validation result

2026-08-25 已完成首轮安装与后台开销验收，详细记录见：

`docs/validation/lag-forensics-xiaomi14-2026-08-25.md`

关键结果：

- debug APK 覆盖安装并正常启动；
- 后台连续 8 次 `top` 均为 0.0% CPU；
- 约 124 秒累计仅增加 4 个 process CPU tick，且只集中在一次实际 polling pass；
- 没有持续 WakeLock、AlarmManager 周期任务或 JobScheduler 周期任务；
- 没有残留 `tr` / `timeout` root child；
- 验收末 Memory / IO PSI avg10 均为 0，Thermal Status=0；
- debug 后台 PSS 约 133~135 MB，其中约 70 MB 为 code mapping，Private Dirty 约 43~45 MB；
- 完整物理 reboot 后的 boot restore 仍留作下一次受控验证。
