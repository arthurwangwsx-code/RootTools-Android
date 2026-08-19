# Root Tools

个人 Android Root 工具箱。目标不是把所有 Root 能力堆进一个页面，而是提供一个可持续扩展的卡片式启动台，每个能力拥有独立详情页和独立权限边界。

完整产品规划、架构与子模块设计见 [`docs/README.md`](./docs/README.md)。

## 当前能力

### 设备看板

- CPU 使用率、load、每簇频率 / min / max / hardware max / governor
- MemAvailable、Cache、Anon、Slab、Swap、ZRAM 压缩率
- memory / IO PSI
- AP / Skin / Battery / USB / PATHM 与 Android Thermal Status
- 电池电量、电流、电压、80% 保护
- 进程数、uptime、低频 Top Process
- 进程内环形历史；首页 30 秒、详情页 2 秒采样

### 性能控制

- 用户模式：`Auto / Cool / Performance`
- `Auto` 内部状态：`Normal / Warm / Moderate / Severe`
- 保留设备原生 WALT governor、cpuset、uclamp 与 Samsung Thermal
- `Thermal > 0` 时只允许继续削峰，不会把系统当前限频向上覆盖
- `Performance` 默认 15 分钟后自动返回 `Auto`
- Auto 守护使用低频 30 秒采样，不做高频 busy-loop

### Root ADB

- 一键切换 Root ADB TCP `5555`
- 不依赖 Android 原生“无线调试必须连接 Wi-Fi”的限制
- 自动显示 `tun0` 上的 Tailscale IPv4
- 可直接复制 `adb connect <tailscale-ip>:5555`
- 默认不做开机永久开放，降低长期暴露风险

### 启动与应用治理

- 分析本次 boot 的真实 `am_proc_start` 时间线
- BOOT_COMPLETED Receiver、启动次数、触发原因、当前常驻状态
- Protected / On Demand / Rare / Freeze 分类
- Freeze / Enable / force-stop / Standby bucket / AppOps
- Appium 测试模式：Notification Listener + Doze whitelist 按需切换

### 进程诊断

- Top CPU / RSS / PPID
- Root Shell 异常高亮
- 按需 pipe inode / FD 归属分析
- WakeLock 与第三方 Active Service 摘要
- 一键 Diagnostic Snapshot

### Root 模块

- Magisk / Zygisk module 状态与 disable/remove marker
- Vector / Xposed module enabled 状态
- Vector Scope 按需读取
- Magisk 模块变更明确标记 pending reboot，不自动重启
- `zygisk_vector` 作为受保护框架

### 网络 / 存储 / 电池

- Wi-Fi / Cellular / VPN / Tailscale interfaces、route、DNS、listen ports
- 单次手工 ping，不做持续公网探测
- `/data` / shared storage 容量、IO PSI、UFS block 统计
- 独立电池与温控页、80% 电池保护控制、近期热状态范围

### 常用操作与自动化

- Developer Options / Magisk / Vector / Hail 快捷入口
- restart adbd / restart SystemUI / Stop Bilibili / Battery Protect 80%
- 收藏常用动作，但执行确认不会被收藏绕过
- token 保护的显式 Broadcast API：`SET_MODE / SET_ADB / RUN_DIAGNOSTIC / FREEZE / UNFREEZE`
- Diagnostic Snapshot 文件导出 + FileProvider 分享
- 当前**不开放 reboot / recovery / bootloader 执行入口**，直到重启后远程回连链路具备可靠保证

### Quick Settings

应用注册两个系统快捷磁贴：

- `CPU 档位`：Auto → Cool → Performance → Auto
- `Root ADB`：开启 / 关闭 5555

## 首页结构

首页只承担工具入口和状态摘要。所有卡片通过统一 `ToolRegistry` 注册，目前包含：

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

后续新增 Root 功能时，先在 `ToolRegistry` 与文档路线图注册，再增加独立详情页和 Repository / Controller；不要继续向首页塞完整控制表单。

## 权限模型

首次启动按以下顺序自动触发：

1. Android 通知权限
2. Activity 进入前台
3. 主进程调用 `su`，触发 Magisk Root 授权
4. Root 成功后才启动 CPU Auto 前台守护

系统授权始终保留用户确认；应用不会通过修改 Magisk 数据库等方式绕过 Root 确认。

## 安全原则

- 不关闭系统 Thermal Engine
- 不固定绑核
- 不替换原厂 governor
- 不在 Thermal 已限频时主动抬高 `scaling_max_freq`
- ADB 5555 不默认开机常驻
- 性能工具只做“限峰”，避免和厂商调度/温控互相争抢

## 构建

当前本机验证环境：Android SDK 36、AGP 9.2、Gradle 9.4.x、JDK 17+。

```bash
gradle :app:assembleDebug
```

完整工程校验：

```bash
bash scripts/build.sh
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Samsung 只读验收（不会安装、重启或关闭 ADB）：

```bash
bash scripts/validate-samsung.sh 100.91.126.56:5555
```
