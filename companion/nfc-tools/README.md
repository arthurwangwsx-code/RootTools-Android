# NFC Tools

一个面向 **自有/获授权 NFC 标签与读卡器** 的 Android NFC 研究工具。当前工程优先验证三条链路：

1. **Reader / Inspector**：读取标签公开 UID、Android Tech 列表、NDEF 和协议能力元数据。
2. **HCE Lab**：通过 Android `HostApduService` 模拟本应用自定义的 ISO-DEP 测试卡。
3. **Root Diagnostics**：通过 `su` 读取 NFC service / HAL / SELinux / routing 状态，确认实际硬件栈能力。

当前产品架构已改为 **Generic Android + Vendor Provider**：Compose UI 只消费统一的 `NfcDeviceProfile / NfcCard / NfcCapability`，Xiaomi/NXP/eSE 细节收敛在 `XiaomiNfcProfileProvider`，Samsung 通过独立 Provider/Diagnostics Contributor 做保守的只读能力映射。后续兼容其他厂商时新增 Provider，不需要重写首页、读卡、测试或系统页面。详见 [`docs/companion/nfc-tools/architecture.md`](../../docs/companion/nfc-tools/architecture.md)。

卡片来源采用多钱包模型：`NfcDeviceProfile.wallets` 可以同时描述 OEM Wallet、Google Wallet 或其他已授权卡片源；每张 `NfcCard` 通过 `sourceId/sourceLabel` 关联来源。自动化 JSON 仍保留单数 `wallet` 作为兼容字段，同时输出正式的 `wallets[]`。

MCP/非交互 shell 环境如果没有继承 Java/Android SDK 环境变量，可以统一使用：

```bash
./gradlew :companion:nfc-tools:testDebugUnitTest :companion:nfc-tools:assembleDebug :companion:nfc-tools:lintDebug
```

当前产品化方向已经增加 Xiaomi 专项适配：首页会读取这台 Xiaomi 14 的 Root / eSE / NXP / MIFARE off-host 能力，并以卡片形式展示小米钱包中已经由官方 provision 的 M1 门卡状态。设备基线见 [`docs/companion/nfc-tools/xiaomi14-baseline.md`](../../docs/companion/nfc-tools/xiaomi14-baseline.md)。

Root Diagnostics 采用 Generic Core + Vendor Contributor：通用层读取 Framework / HAL / SELinux / routing；Xiaomi contributor 只读探测官方钱包的 **off-host MIFARE / eSE 路由**，Samsung contributor 只读探测 Type-4/NXP 扩展。诊断输出会主动隐藏 VC UID / CID 等凭证标识，不读取或导出门禁密钥与受保护扇区。

## 当前测试设备

- **主验证机：Xiaomi 14 (`houji`) / Android 15 / HyperOS 2**，Root、eSE、Xiaomi `mi_nfc`、NXP vendor、官方 M1 off-host 已实机验证；
- **Samsung S22 Ultra (`b0q`)**：Reader/HCE、Samsung Type-4 扩展与 NXP 栈已有历史实机基线；
- **Redmi K20 Pro (`raphael`)**：Android 11 / Magisk Root，保留为旧版本与 RF 故障诊断基线。

## HCE 测试协议

- AID: `F001020304050607`
- SELECT AID: 标准 ISO 7816-4 `00 A4 04 00 ...`
- GET DATA: `80CA000000`
- GET DATA 响应：UI 配置的测试 payload + `9000`

Reader Mode 与 HCE 不同时启用，因为 Android `enableReaderMode()` 会让本 NFC 控制器只作为 reader/writer 工作并关闭本机 card-emulation。应用 UI 中必须显式切换模式。

Reader 对 ISO-DEP NXP 卡会额外执行**只读** `GetVersion` 产品识别，展示 MIFARE 产品族、代际、存储规格与实现形态；制造/序列帧不会持久化。最近两张不同实体卡还会自动做公开协议指纹对比，便于区分 RF/协议差异与认证/策略层差异。

当前产品运行时已经收敛为 `DEFAULT / READER / HCE` 三态：普通浏览页面不占 Reader Mode，实验 `HostApduService` 默认禁用；进入读卡页时才打开 Reader，只有明确点击启动 HCE 测试时才启用实验服务。系统 NFC 开关发生变化时会自动同步并恢复当前期望模式。

## ADB 全自动扫描

Debug/研究环境可直接用 ADB 驱动扫描，不依赖 UI 点击：

```bash
adb -s <serial> shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode reader \
  --es session_id scan-001 \
  --el timeout_ms 8000
```

默认情况下 Reader 会在整个扫描窗口内保持启用。只有在测试“卡片已经静止贴在天线区、需要主动重新触发 RF discovery”这类场景时，才额外传入 `--ez rearm_reader true`。re-arm 会短暂关闭再恢复 Reader Mode，因此不作为日常扫描和自动化回归的默认行为。

扫描状态与最后一次结果写入应用私有目录：

- `files/scan_status.json`
- `files/last_scan.json`

可通过 `adb shell run-as com.arthur.nfclab cat files/scan_status.json` 读取。

HCE 也支持无 UI 自动切换：

```bash
adb -s <serial> shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode hce \
  --es session_id hce-001
```

此时 `scan_status.json` 会返回 `hce_ready`、测试 AID 和 payload，便于 ADB/E2E 检查系统是否真正进入 Host Card Emulation 路由。

通用设备能力快照支持 ADB：

```bash
adb -s <serial> shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode device_profile \
  --es session_id profile-001
```

结果位于 `files/device_nfc_profile.json`。在 Xiaomi 上会包含 eSE / M1 off-host / 官方门卡元数据；在 Samsung 或其他 Android 上则按对应 Provider 渐进增强。旧的 `automation_mode=xiaomi_profile` 暂时保留兼容。输出有意过滤 VC UID / CID 和支付凭证。

## 读卡器兼容性诊断

Xiaomi Root 设备新增了面向**自有/获授权卡片与读卡器**的“读卡器兼容性诊断”。入口位于 **系统 → 读卡器兼容性诊断**。

诊断开始后 NFC Tools 会保持 `DEFAULT` 模式，不启用本应用 Reader Mode/HCE，因此不会主动抢占 Xiaomi Wallet 的 eSE/off-host 门卡路由。Xiaomi Provider 只读观测系统 `NfcReaderDetector` 已经产生的信号，并在结束后保存摘要：

- 是否观察到 13.56 MHz RF Field；
- Type A/B/F 与最高协议层 L2/L3/L4；
- 是否发生 eSE/NFCEE action；
- 是否出现卡片激活或 HCI/NFCEE AID 交互；
- 与最近一次“识别成功”记录的差异。

原始 AID、NFCEE payload 和 Reader 原始日志只在本次分析的内存中短暂使用，不写入诊断历史。持久化记录只有结论、布尔/计数信号和脱敏证据摘要。

推荐现场流程：

1. 先在系统钱包中选择一张确认可用的卡作为基线；返回 NFC Tools 后确认“当前激活卡片”已刷新；
2. 点“开始记录交互”，看到“正在记录”后，把手机按正常使用姿势靠近已知可用读卡器 2–3 秒；
3. 观察结果，**先把手机移开读卡器**，再点“识别成功”；
4. 在系统钱包切换到待排查卡片，返回 NFC Tools，确认卡片名称已变更；
5. 再开始一次记录，靠近目标读卡器 2–3 秒，移开后按实际情况选择“识别成功 / 设备有提示但未通过 / 设备完全无提示”；
6. NFC Tools 会给出结论，并自动与最近成功基线比较。

主要结论含义：

- `NO_RF_FIELD`：未观察到 13.56 MHz 发场；若成功基线能稳定观察到 RF Field，而目标读卡器重复测试都没有，应优先检查 125 kHz/双频卡或不同读卡器频段；
- `RF_FIELD_NO_CARD_INTERACTION`：已经有 13.56 MHz RF Field，但没有进入有效 eSE/卡片交互，差异更偏向协议、调制、天线耦合或 Reader 兼容性；
- 已进入 eSE/NFCEE、L3/L4 后仍未通过：手机 NFC、13.56 MHz 和 eSE 基本链路已经工作，后续应重点检查卡片数据/认证策略及 Reader 兼容差异。

Debug/回归环境另提供 `automation_mode=access_diag_start` / `access_diag_stop`，结果写入 `files/access_diagnostic.json`；现场正常使用不需要 ADB。

模拟能力分层也可通过 ADB 导出：

```bash
adb shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode simulation_capability \
  --es session_id sim-cap-001
```

结果位于 `files/simulation_capability.json`，分别报告 RF identity、ISO-DEP、应用协议、安全凭证与 eSE/off-host 五层能力。报告只使用公开扫描元数据与设备能力，不读取或导出真实门禁密钥。

安全卡 Provisioning 能力可以单独导出：

```bash
adb -s <serial> shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode provisioning_capability \
  --es session_id provisioning-001
```

结果位于 `files/provisioning_capability.json`。当前模型明确区分 OEM Wallet、Partner TSM 与 Direct eSE 三条路径，并逐项标记“已满足 / 可执行 / 需要合作方 / 仅特权调用 / 尚缺 / 待确认”，用于回答安全卡继续推进时还缺哪个条件。

当结果为 `noTagActivation=true` 时，含义是 NFC controller 在 Reader Mode 下没有产生外部 Tag activation，问题发生在 UID/NDEF 等解析之前。对门禁卡应优先检查：是否位于本机 NFC 天线耦合区、是否实际上属于 125 kHz LF RFID，而不是继续修改 NDEF/APDU 解析代码。

## iOS NFC Probe

工程新增 `ios/NFCProbe`，用于把 iPhone 作为 Samsung NFC Lab 的真实 RF 对端：

- ISO14443 / MIFARE-compatible tag discovery；
- ISO7816 identifier / selected AID / historical bytes；
- 自动验证 NFC Lab 测试 AID `F001020304050607`；
- 自动发送 `80 CA 00 00 00` 并读取测试 payload；
- 结果可复制为 JSON。

构建、签名、安装和启动：

```bash
./scripts/nfc-ios-probe.sh install
```

如果 iPhone 当前未被 Xcode/CoreDevice 识别为可用物理设备，脚本会停止并给出连接提示，不会误装到模拟器。

## Root Native Bridge

工程已包含一个由 Android NDK 构建的 arm64 原生 helper：

```text
app/src/main/cpp/nfc_root_bridge.c
```

构建时会被编译并打入 APK assets。运行时由 APP 通过 `su` 安装到 `/data/local/tmp/nfc-tools-root-bridge-v2` 并执行。当前只开放白名单诊断命令 `status` / `version`，用于返回：

- UID/EUID/GID；
- 当前 SELinux domain；
- SELinux Enforcing 状态；
- NFC firmware / chip ID / port / initialization properties（支持多组厂商属性 fallback）；
- NFC IRQ；
- 通用 NFC 路径以及 NXP / ST 等候选节点、配置文件的存在性和访问权限。

该 bridge 不提供关闭 SELinux、任意 sepolicy 修改、任意设备节点写入或真实门禁凭证克隆接口。

## 安全边界

这个工程不会实现：门禁密钥破解、受保护扇区导出、真实门禁 APDU 重放、真实门禁凭证克隆、绕过访问控制或禁用 Android NFC 安全机制。

需要特别区分两条模拟路径：Android `HostApduService` 仍然是 ISO-DEP/APDU HCE；某些小米机型的官方“实体门卡”能力则可能由 NFC Controller 把 MIFARE technology **off-host 路由到 eSE**，由 OEM/TSM 已下发的安全元件应用完成。这种官方路径的存在不意味着第三方 Root App 能直接取得其中的密钥或复制 provisioning 权限。

完整调研与架构见 [`docs/companion/nfc-tools/technical-solution.md`](../../docs/companion/nfc-tools/technical-solution.md)。
