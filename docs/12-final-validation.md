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
