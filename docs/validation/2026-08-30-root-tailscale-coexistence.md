# Root Tailscale 与 Android VPN 共存验证

## 1. Scope

- Feature / Change：Root Tailscale userspace Serve、root TUN 原生路由、强健康判定、显式第三方 VPN 边界和可撤销启动恢复
- Commit / Branch：`main`，实现与测试 `1cd8a71`
- APK / Version：下一轮真机使用 `0.6.0-beta.1` / versionCode 6 同签名发布候选；tag / Release 仍须等待红米验收

## 2. Environment

- Device：Redmi Mi 9T Pro (`f27e2c0f`)
- Android / OEM：Android 11 / API 30 / MIUI
- Root runtime：`uid=0` / `u:r:magisk:s0`，`/dev/net/tun` 存在
- VPN App：Surfboard `mobile-2.24.4 (Build 250)`；官方 Tailscale Android `1.102.2`
- Root sidecar：官方 ARM64 `tailscale` / `tailscaled` `1.102.3`
- External peer：Mac Tailscale backend `Running`，作为 ping / ADB 的 tailnet 外部观测点
- Connection：USB ADB 始终作为恢复路径；未进行 reboot

## 3. Automated Verification

| Check | Result | Notes |
|---|---|---|
| Tailscale JVM tests | PASS | 31 tests：command / shell syntax、目录权限、parser、policy（含已保存身份被撤销后的重新认证）、runtime hash / tar extraction |
| Quality guard | PASS | legacy debt warnings only，zh-rCN 无缺失 key |
| Security guard | PASS | no blocking findings |
| Debug build | PASS | `:app:assembleDebug` |
| Final signed candidate | PASS | `0.6.0-beta.1` / versionCode 6 四个 APK 全部通过 V2 验签，并使用同一证书 SHA-256 `58b74d...e186e` |
| Project baseline | PASS | 212 JVM tests / 61 suites，0 failure/error；`:app:lintDebug :app:assembleDebug` 通过 |
| Full `scripts/build.sh` delivery gate | PASS | 2026-09-01：416 tasks，质量/安全、全模块测试、Lint、Debug/Release 与 Core coverage guard 全部通过 |
| Emulator UI smoke | PASS | `0.6.0-beta.1` / versionCode 6 覆盖安装；`emulator-5554` 冷启动直达 Root Tailscale，无 Root 状态、入口布局、返回/刷新 accessibility tree 与进程稳定性通过 |

## 4. Device Verification

| Case | Expected | Actual | Result |
|---|---|---|---|
| 覆盖安装 | 同签名升级且不清数据 | `adb install -r` 成功，RootTools data dir inode 保持 `3604547` | PASS |
| 页面与无状态基线 | 无 runtime / 无 VPN / 无 root TUN 如实显示 | UI、accessibility tree、`dumpsys connectivity`、`ip rule` 一致 | PASS |
| Runtime 生产安装入口 | 固定 hash 下载并原子安装 | App UI 安装成功，设备端 `tailscale version` = `1.102.3` | PASS |
| 认证启动 | 产生官方 URL，凭据不进 App | 官方 Tailscale 页面确认 `roottools-raphael` 已加入与 Mac 相同的 tailnet；未记录 URL / token | PASS |
| 未授权 state 识别 | state file 不得误当已保存身份 | 真机发现 119-byte pre-auth state；新版实机仍显示认证入口，可重新打开官方 URL；verified marker 仍为空 | PASS |
| 授权后基础数据面 | Mac 能识别在线节点并收到 Tailscale ping | Mac `tailscale status --json` 显示节点 online；连续 Tailscale ping 成功并协商到直连 LAN endpoint | PASS |
| Surfboard + userspace Serve | Android VPN 保持，Mac 可达 ADB `:5555` | Tailnet 已授权；等待恢复设备控制、开启 TCP ADB 并下发 Serve | PENDING |
| userspace disable rollback | 只停 RootTools daemon / Serve，Surfboard 不变 | 等待前置用例 | PENDING |
| Surfboard + root TUN | `tailscale0` 与 Android VPN 同时在线，普通出口不回归 | 等待恢复设备控制 | PENDING |
| root TUN disable rollback | 原生 Tailscale 路由 / netfilter 清理，Android VPN 不变 | 等待前置用例 | PENDING |
| screen-off / App force-stop | Root sidecar 和远程管理仍在线 | 等待恢复设备控制并先完成两种管理模式 | PENDING |
| boot restore | 只恢复已验证模式，不停第三方 VPN | 须在非 reboot 门全部通过且恢复路径明确后执行 | PENDING |

## 5. Mutation / Rollback

- 修改前状态：RootTools `0.5.0-beta.1`，无 `/data/adb/tailscale`，无 `tailscale0`，无 Android VPN active，无 table `1099` 遗留。
- 已执行动作：同签名覆盖安装；从 App 安装固定 runtime；启动 userspace 认证 daemon；通过官方 Tailscale 页面完成设备授权。
- 当前可确认状态：Root Tailscale userspace 节点已在线，Mac 可 Tailscale ping；`:5555` / `:8765` 均明确拒绝连接，尚未建立 Serve。USB ADB 已断开，LAN / tailnet TCP ADB 均未监听，因此当前没有设备端控制通道。
- 回滚动作：可由 RootTools “停用 Root Tailscale”精确停止 managed daemon / Serve；保留 runtime 与已验证身份；开机脚本尚未安装。
- 回滚结果：待两种模式分别验证。

## 6. Logs / Screenshots

- 安装前 Root Tailscale 页面截图与 accessibility tree 已检查；布局、中文 copy、Runtime / Serve / Root TUN 分区正常。
- 未授权重试路径已检查：新 App 未被 pre-auth state 陷住，重试会生成新 URL 并启动最长 10 分钟的身份 marker watcher；授权后 Mac 侧已观察到节点 online。
- 2026-08-31 重新核验：Mac 当前仍可看到 `roottools-raphael` online；`tailscale ping` 先经新加坡 DERP、随后协商到 `10.1.1.75` 局域网直连；tailnet `5555/8765` 均关闭，`adb devices -l` 仅有模拟器，因此没有把“节点在线”误写为“远程控制已建立”。
- 2026-08-31 代码审批补强：缓存 tailnet IP 必须同时满足 `BackendState=Running` 才算认证在线；真正共存必须存在非官方 Android VPN；PID 文件只接受纯数字；Root runtime/state 目录在使用前强制 `0700`；所有生成命令通过 `sh -n`。
- 2026-09-01 发布候选核验：四个 `0.6.0-beta.1` / versionCode 6 Release APK 均通过 V2 验签且证书一致；同版本 Debug APK 在模拟器冷启动直达页面，进程存活且无 FATAL / ANR，截图和 accessibility tree 已人工检查。
- 登录 URL 和 state 内容不写入报告；仅保留 backend 状态与无凭据 marker 证据。
- 待补：设备重新接入 ADB 后的 marker / backend 回读、Surfboard active 截图 / connectivity owner、userspace Serve 页面、Mac ADB receipt、root TUN 页面与回滚差分。

## 7. Regression

- 固定 runtime 供应链默认拒绝 hash 不匹配。
- 新启用 / 修复 / boot 命令不含 `am force-stop`、`ip rule add` 或新的 table `1099` 写入。
- 停用只清理 RootTools managed daemon、Serve、Tailscale native cleanup 及早期 RootTools `1099` 遗留；不删身份 state。

## 8. Residual Risk / Known Issues

- Tailnet 授权已完成，但登录流程本身仍必须保留官方交互边界；RootTools 不持有或代填账户凭据。
- Redmi 当前 USB ADB 断开，且认证前未开启 TCP ADB；在设备恢复 ADB 前无法执行 userspace Serve、Android VPN 共存、root TUN 和回滚验收。
- Redmi 非 reboot 共存门、稳定性门和回滚门仍在进行；按 2026-08-31 调整后的顺序，Redmi 通过后先发布，再由用户在不在电脑旁的小米 14 下载验证。
- reboot 只能在 USB / 解锁 / 恢复边界重新确认后执行；本报告不会用静态 boot script 审计替代实际 reboot 证据。

## 9. Conclusion

- **IN PROGRESS**：实现、供应链安装、未授权状态机、官方授权与基础 Tailscale 数据面已通过；必须恢复 Redmi 的 ADB 控制并完成 userspace Serve / root TUN / Android VPN 共存与回滚验收，才能改为 PASS 并发布。
