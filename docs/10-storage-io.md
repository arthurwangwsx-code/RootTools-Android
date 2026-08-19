# 存储与 IO

## 目标

Root Tools 的存储模块只回答三个问题：

1. `/data` / 共享存储还剩多少空间？
2. 当前是否存在真实 IO pressure？
3. 哪个物理块设备正在产生大量读写？

第一版不提供“清垃圾”“清 App 数据”“fstrim”“改文件系统参数”等写操作。

## 2026-08-19 实施拆解（Milestone G）

### G1. StorageSnapshot

数据源：

- `df -k`
- `/proc/pressure/io`
- `/proc/diskstats`
- `/sys/block/*/stat`

指标：

- `/data` total / used / available / used%
- `/sdcard` / emulated storage（设备存在时）
- IO PSI some/full avg10
- 物理块设备 read sectors / write sectors / io time
- uptime，用于解释累计数据

### G2. 设备过滤

默认忽略 `loop* / ram* / zram* / dm-*`，优先展示真实 UFS block，例如 `sda / sdb / ...`。

### G3. 状态判断

容量：

```text
available > 20% → Healthy
10~20% → Watch
< 10% → Low space
```

IO：

```text
io PSI some avg10 < 2 → Healthy
2~10 → Watch
>= 10 → Pressure
```

不根据累计 sectors 单独判定“磁盘异常”。

### G4. UI

首页卡片：

```text
Data 42 GB free
IO PSI 0.2
```

详情页：Storage overview / Filesystems / IO pressure / Block devices。

### G5. 验收

- `/data` 容量与 `df -k /data` 对照
- IO PSI 与 `/proc/pressure/io` 对照
- 页面只按需刷新，没有连续写盘/benchmark
