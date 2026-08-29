# Net Tools consolidation map

本表是独立 Net Tools 收入 RootTools 后的维护真值源。导入使用非 squash Git 历史；下表描述当前实现位置，不要求保留第二份源码快照。

| 独立工程能力 | RootTools 真值源 | 归一化结果 |
|---|---|---|
| `CaptureRepository` / `PcapdBridge` / `PcapParser` | `feature/network/inspection/data` | 保留按 UID 与全设备 PCAP、协议/Packet/Flow 解析；root 执行改用共享 `RootShell` |
| 独立 `MainActivity` / `MainViewModel` | `app/navigation/NetworkInspectionRoute.kt`、`feature/network/inspection/ui` | 合入现有 5-domain navigation；抓包与 TLS 使用专用 ViewModel，不扩张 Dashboard |
| MITM Messenger API/client | `com/pcapdroid/mitm/MitmAPI.java`、`MitmAddonClient.kt` | 保留官方稳定 IPC contract 与 typed 状态 |
| Interception engine/store | `InterceptionEngine.kt`、`InterceptionStore.kt`、`InterceptionService.kt` | 保留前台运行、会话与已脱敏事件；生命周期显式可见 |
| iptables UID redirect | `InterceptionNetworkController.kt` | 规则由 typed policy 生成并审计；失败只做一次确定性回滚 |
| CA manager / Magisk overlay | `InterceptionCertificateManager.kt` | 支持导入 add-on CA 与 standalone CA；安装/移除可逆且需显式确认 |
| 自动下载/静默安装 add-on | `NetworkInterceptionScreen.kt` | 不迁移高风险自动安装；打开官方发布页，由 Android package installer 完成确认 |
| `pm uninstall` | `NetworkInterceptionScreen.kt` | 不直接卸载；打开 Android 应用设置 |
| 自动 reboot | UI readiness | 不直接重启；明确提示用户手动重启 |
| debug exported receiver / 自动设备矩阵 | 无生产入口 | 不并入发布 APK，避免外部广播触发 root/抓包动作；旧实现仍可从导入历史追溯 |
| 设备数据拉取/摘要 | `scripts/network-inspection-*` | 显式 adb serial；包名与路径改为 `com.arthur.roottools`；不读取 raw payload blob |
| 架构、UX、验收文档 | `docs/network-inspection/` | 旧包设备证据标记为历史证据，不冒充 RootTools 最新真机验收 |

## 维护规则

1. UI 不直接拼接或执行 shell；新命令必须先进入可单测的 policy/controller。
2. Framework 权限操作优先走 `PrivilegeRouter`；Linux/root-only 操作统一走共享 `RootShell`。
3. 抓包、TLS、CA 三条状态机独立；MITM 失败不能让原始抓包失效。
4. destructive 或跨应用动作不得后台重试，必须保留用户确认、审计和恢复路径。
5. 新协议先补纯解析测试，再接 UI；新 IPC 字段必须补序列化/常量兼容测试。
6. 设备验收按“实现/单测/构建/真机抓包/真机 TLS/隐私检查”分层记录。
