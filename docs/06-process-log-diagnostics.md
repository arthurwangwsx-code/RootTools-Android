# 进程与日志诊断

## 卡片目标

首页摘要：

```text
Top CPU: xxx 18%
Root shell: 0 abnormal
WakeLock: normal
```

## Process 页面

- CPU 排名
- RSS / PSS 排名
- UID / package 映射
- parent / child process
- elapsed time
- process state
- root process 高亮

## Root Shell 归属

针对 Root 工具经常通过 Magisk/libsu 创建 shell 的特点，支持：

- PID / PPID
- `/proc/<pid>/fd`
- pipe inode
- 与 App 进程 fd 对照
- syscall / IO 计数

这用于复现本次 ApexTuner：

```text
ApexTuner FD ↔ root sh pipe
→ 证明高 CPU root sh 归属 ApexTuner
```

## WakeLock

- 当前 WakeLock
- 历史 release
- Screen bright wakelock
- App attribution

## Services

- active service
- foreground service
- restarting service
- recentCallingPackage

## 一键诊断快照

生成一个轻量文本/JSON：

- timestamp
- thermal
- battery
- CPU
- memory
- top processes
- root shell
- active services
- wakelock
- network

当前已经可以写入应用私有诊断报告，并通过 Android Share Sheet 分享到 Obsidian、Agent 或其他目标。

## 2026-08-19 实施拆解（Milestone D）

### D1. 常规诊断只按需执行

打开“进程诊断”页或点击刷新时执行一次：

- Top CPU / RSS
- Root processes / Root shell
- WakeLock 当前状态
- 第三方 active services
- load / memory / thermal 摘要复用 `DeviceHealthSnapshot`

页面不建立 1~2 秒的独立 `top` 循环，避免与 Dashboard 采样重复。

### D2. RootShellRecord

记录：

- PID / PPID
- CPU%
- elapsed
- cmd
- stdin/stdout/stderr pipe
- `/proc/<pid>/io` 中 `rchar/syscr/read_bytes`

只有用户点击“归属分析”时，才针对该 PID 的 pipe inode 扫描其他进程 FD，返回可能 owner：

```text
root sh fd0 -> pipe:[132875]
app pid 18707 fd102 -> pipe:[132875]
=> likely owner: com.apextuner.app.debug
```

### D3. WakeLock / Service

第一版展示语义化摘要，不完整复制 `dumpsys`：

- 当前 Wake Locks count
- 前几个持有者 / WorkSource
- 最近 Screen bright wake lock attribution
- 第三方 active service package / component / foreground 标记

### D4. Diagnostic Snapshot

快照内容：

- DeviceHealthSnapshot
- top processes
- root shell records
- wakelock summary
- third-party services

第一版保存到 App 内存并支持复制；文件分享/Obsidian 写入属于 Milestone E。

### D5. 性能预算

- 常规诊断：用户操作触发
- 深度 FD attribution：用户明确点击某个 Root Shell 才触发
- 退出页面后无诊断任务常驻

### D6. 验收

- 当前设备无异常 Root Shell 时能返回 0 abnormal
- 能正确显示 Root Tools / Tailscale 等当前进程但不会误判为 Root Shell
- WakeLock 与 `dumpsys power` 对照
- active services 与 `dumpsys activity services` 对照
- 深度归属扫描必须有超时，不能长期占 CPU

## 2026-08-24 Root 子进程泄漏修复

### 现场证据

Xiaomi 14 真机发现一个由 RootTools 诊断链路派生的 root `tr`：

```text
elapsed ≈ 41h
average CPU ≈ 68%
instant CPU ≈ 98%
stdin -> /proc/<wechat-thread>/cmdline
TMPDIR=/data/user/0/com.arthur.roottools/cache
```

旧 RootShell 在 Kotlin 侧超时时只销毁持有的 `su` transport process；Magisk 派生出的 shell / pipeline 子进程不保证跟随 transport 一起退出，因此一次超时诊断可能留下 orphan root child。

### 修复契约

1. 每条 RootShell payload 都通过 Android Toybox `timeout` 执行；
2. 不使用 `--foreground`，让 payload 获得独立 process group；
3. `-k 0.2s` 保证忽略 TERM 的任务最终被 KILL；
4. timeout 的 124 / 137 统一映射为 `Result.timedOut=true`；
5. shell-level timeout 正常完成后复用同一个 `su` session，不为超时反复创建 root transport；
6. 只有 transport 自身失联时才 invalidate session；
7. `/proc/<pid>/cmdline` 先用 `head -c` 限制读取，再交给 `tr`，避免 `tr` 直接持有 proc fd 长时间运行。

### 回归要求

- JVM：普通命令复用同一 shell；
- JVM：单引号 / 多行 payload 不破坏 shell quoting；
- JVM：忽略 TERM 的 payload 仍会 timeout，下一条命令继续复用 session；
- Xiaomi 14：人为构造的 Toybox timeout process-group 测试后，后台 child PID 不得存活；
- Xiaomi 14：多次进程诊断后不得出现长期高 CPU 的 `tr` / RootTools root shell。
