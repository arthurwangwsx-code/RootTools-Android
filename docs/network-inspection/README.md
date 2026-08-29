# RootTools Network Inspection

Network Inspection 已从独立的 Net Tools 工程归一化到 RootTools 主 APK（`com.arthur.roottools`）。它保留按应用/全设备抓包、协议与流解析、TLS 明文检查、CA 生命周期和本地证据，同时统一复用 RootTools 的导航、RootShell、审计与多语言基础设施。

独立 Net Tools 的 Git 历史已完整导入，旧源码快照不再作为第二套可构建实现维护。功能与路径映射见 [consolidation-map.md](./consolidation-map.md)。

## 产品入口

`设备 → Network` 由三个工作区组成：

- **诊断**：网络状态与现有 RootTools 网络诊断能力。
- **抓包**：按应用 UID 或全设备抓取 PCAP，展示会话、数据包和 Flow 详情。
- **TLS 检查**：管理 MITM add-on、CA 信任、透明转发规则、会话历史和已脱敏事件。

## 信任与权限边界

- Root-only Linux 操作只通过共享 `RootShell` 和 typed controller 执行。
- iptables 与 Magisk CA module 命令由输入策略生成并写入审计；不接受任意外部 shell 文本。
- PCAPdroid MITM add-on 保持独立安装包和 GPL-3.0 分发边界，RootTools 只使用其稳定 Messenger contract。
- RootTools 不后台下载/静默安装或卸载 add-on；UI 只打开官方发布页或 Android 应用设置。
- CA 写入为可逆 Magisk overlay；重启必须由用户明确执行。
- 系统信任不等于绕过 certificate pinning。遇到 pinning/private trust store 时，原始抓包仍可独立使用。

## 设备数据布局

外部应用数据位于：

```text
Android/data/com.arthur.roottools/files/
├── captures/
│   ├── <session>.pcap
│   ├── <session>.json
│   └── <session>.log
└── intercepts/
    └── <session>-<package>/
        ├── session.json
        ├── events.jsonl
        └── payloads/
```

CA 私钥材料保存在应用私有目录；用于证据导出的 CA 公钥副本位于外部数据的 `network-inspection/certificates/`。`artifacts/` 被 Git 忽略，因为抓包与解密内容可能包含隐私数据。

## 构建与验证

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

设备证据拉取与隐私安全摘要：

```bash
./scripts/network-inspection-pull-device-data.sh <adb-serial>
python3 scripts/network-inspection-summarize-intercepts.py \
  artifacts/device-export/latest/intercepts
```

脚本要求显式提供设备 serial，避免多设备环境误操作。它只读取会话元数据和已脱敏预览，不读取 payload blob。

## 进一步资料

- [architecture.md](./architecture.md)：组件、信任和运行时模型。
- [packet-inspection-ux.md](./packet-inspection-ux.md)：包与 Flow 的交互契约。
- [validation.md](./validation.md)：历史设备证据与当前验收缺口。
- [review-checklist.md](./review-checklist.md)：发布前复核清单。
- [consolidation-map.md](./consolidation-map.md)：旧 Net Tools 到 RootTools 的逐项映射。
- [../third-party/pcapd.md](../third-party/pcapd.md)：pcapd 来源、哈希和许可证边界。
- [../third-party/pcapd-mitm.md](../third-party/pcapd-mitm.md)：MITM add-on 的 IPC、许可证和安装边界。
