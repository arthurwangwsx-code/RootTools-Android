# NFC Tools 架构

## 目标

NFC Tools 的产品 UI 与具体手机厂商实现解耦。小米 14 是当前主要实机，但 Xiaomi / NXP / eSE 细节不能成为应用公共数据模型，否则后续支持 Samsung、Pixel、OnePlus 或不同 NFCC 时会迫使 UI 和 Activity 重写。

## 分层

```text
Compose Product UI
        │
        ▼
NfcDeviceProfile / NfcCard / NfcCapability   <- domain
        │
        ▼
DeviceNfcProfileRepository                   <- orchestration
        │
        ├── GenericAndroidProfileCollector   <- every Android device
        │
        ├── XiaomiNfcProfileProvider         <- Xiaomi / Redmi / POCO
        │
        ├── SamsungNfcProfileProvider        <- Samsung read-only capability probe
        │
        └── OtherVendorProfileProvider       <- future
                 │
                 ▼
         vendor API / eSE / Root / wallet
```

NFC controller 的运行态由独立 runtime 层管理：

```text
MainActivity lifecycle / product action
        │
        ▼
NfcModeController
        │
        ▼
NfcModeDriver
        │
        └── AndroidNfcModeDriver -> NfcAdapter.ReaderMode
```

`MainActivity` 只声明期望的 `DEFAULT / READER / HCE` 模式，不再持有 Reader flags、presence-check 参数或前后台重臂规则。这样未来某些 ROM 需要 Reader workaround 时，应替换/装饰 runtime driver，而不是在 Activity 中增加品牌分支。

UI 本身继续拆为：

```text
NfcLabApp               <- 只负责导航与页面编排
├── NfcHomeScreen       <- 首页 / 卡片中心
├── NfcReaderScreen     <- Reader Mode / 历史
├── NfcHceLabScreen     <- Host HCE 实验
├── NfcSystemScreen     <- 能力矩阵 / Root 诊断
├── NfcSnapshotViews    <- Tag/扫描结果复用视图
├── NfcUiCommon         <- 通用产品组件
└── NfcToolsUiState     <- 页面唯一状态契约
```

页面统一消费 `NfcToolsUiState`，不再为每个页面复制一长串状态参数。这样新增设备
能力通常只修改 domain/profile 映射与对应页面展示，不需要改导航壳和 Activity 参数形态。

Compose 层内部也按产品页面拆分，不再维护单个千行页面文件：

```text
ui/
├── NfcLabApp.kt          # 导航与页面装配
├── NfcToolsShell.kt      # TopAppBar / Bottom Navigation
├── NfcToolsUiState.kt    # UI state / actions contract
├── NfcHomeScreen.kt      # 首页与卡片中心
├── NfcReaderScreen.kt    # Reader 产品页
├── NfcHceLabScreen.kt    # HCE 实验页
├── NfcSystemScreen.kt    # 能力 / Root / Vendor 页
├── NfcSnapshotViews.kt   # Tag 结果视图
└── NfcUiCommon.kt        # 通用视觉组件
```

页面只依赖 domain 与 `NfcToolsUiState`，厂商原始 DTO、Root shell 和数据库实现都不能进入 UI 包。

### Domain

`domain/` 只描述产品真正需要理解的概念：

- `NfcCapability`：Reader、HCE、eSE、UICC、MIFARE Reader、MIFARE off-host、Root、厂商 API；
- `NfcDeviceProfile`：统一的设备能力快照；
- `NfcCard`：来自系统钱包或其他授权来源的统一卡片模型；
- `NfcWalletInfo`：卡片源/系统钱包的展示信息与官方管理入口；`NfcDeviceProfile.wallets` 支持同一设备同时存在多个来源；
- `NfcOperatingMode`：`DEFAULT / READER / HCE` 三态运行模式。

UI 不依赖 `XiaomiNfcProfile`，也不直接读取小米钱包数据库。

卡片来源采用多源模型，不能退化回单一 `wallet` 假设。自动化 JSON 暂时继续输出 `wallet` 作为第一个/主卡片源的兼容字段，同时输出正式的 `wallets[]`；每张 `NfcCard` 通过 `sourceId/sourceLabel` 关联来源。这样 Samsung Wallet、Google Wallet、OEM 门卡服务等后续可以并存，而不是互相覆盖。

### Runtime mode controller

运行时由 `NfcModeController` + `NfcModeDriver` 管理，而不是由页面直接调用 `NfcAdapter`：

```text
DEFAULT
  ├─ Reader Mode: off
  └─ NFC Tools 实验 HostApduService: disabled

READER
  ├─ NFC Tools 实验 HostApduService: disabled
  └─ Reader Mode: on（仅 Activity resumed + NFC on 时）

HCE
  ├─ Reader Mode: off
  └─ NFC Tools 实验 HostApduService: enabled
```

实验 HCE service 在 Manifest 中默认 `enabled=false`，只有用户明确启动 HCE 测试时才动态启用。这样“测试卡已停止”对应真实系统状态，而不是只改变 UI 标签。

`NfcSystemStateObserver` 监听系统 NFC Adapter 状态变化。Reader 页面处于前台时，如果用户关闭再重新打开系统 NFC，`NfcModeController` 会自动重新应用 READER 状态，不要求重新进入页面。

### Generic Android

`GenericAndroidProfileCollector` 在所有 Android 手机上工作，只读取标准 Android feature、设备身份、Root 是否可用和 SELinux 状态。即使没有任何厂商 Provider，读卡和标准 HCE 仍可正常使用。

### Vendor Provider

`NfcProfileProvider` 是厂商扩展边界。Provider 的职责是把厂商专属事实映射为统一 domain，而不是把 vendor DTO 暴露给 UI。

当前 Xiaomi Provider 负责：

- `mi_nfc` / NXP vendor service；
- eSE1；
- MIFARE reader 与 off-host route；
- 小米钱包已 provision 的门卡元数据；
- 小米钱包公开的 `DOOR_CARD_SELECT` 官方管理入口；
- Xiaomi/NXP 固件和 routing 信息。

当前 Samsung Provider 采用保守策略：

- 识别 Samsung 设备；
- 识别 `com.samsung.android.nfc.t4temul`，映射为 Type-4 NDEF emulation 能力；
- 不因为设备声明 `com.nxp.mifare` 或 vendor 配置存在 MIFARE route 就推断设备可以完整模拟 MIFARE Classic；
- 更深的 Samsung/NXP Root 信息通过独立的只读 Diagnostics Contributor 采集，而不是污染通用 Provider。

Xiaomi 的原始探测 DTO 与钱包只读探测器也位于 `platform/xiaomi/`，而不是 `root/`。Root 只是 Provider 获取某些事实的一种执行能力，不能成为厂商架构层。

新增手机厂商时，应新增 Provider 并注册到 `NfcProfileProviderRegistry`。不应该在 `MainActivity` 或 Compose 页面里增加 `if (Samsung...)` / `if (Xiaomi...)` 分支来访问底层。

当前 Samsung Provider 只做保守的只读能力映射：识别 Samsung 设备，并检测系统是否声明 `com.samsung.android.nfc.t4temul`。即使设备同时使用 NXP NFCC，也不会据此推断其支持 MIFARE Classic off-host 模拟；只有经过具体机型实测的能力才应进入 domain capability。

### Provisioning Capability Provider

安全卡 Provisioning 不再通过 `vendor.extras` 直接驱动 UI，而是使用独立的 `NfcProvisioningProvider` 插件边界：

```text
NfcDeviceProfile
      │
      ▼
ProvisioningCapabilityRepository
      │
      └── NfcProvisioningProviderRegistry
           ├── XiaomiProvisioningProvider
           ├── SamsungProvisioningProvider
           └── GenericProvisioningProvider
                    │
                    ▼
          ProvisioningCapabilityReport
```

统一报告把安全卡创建路径拆成：

- `OEM_WALLET`：由系统/厂商钱包管理的正式 Provisioning；
- `PARTNER_TSM`：需要合作方 caller identity、服务端授权和 APDU task 的 TSM 链路；
- `DIRECT_ESE`：直接 eSE / OMAPI 路径，必须单独确认安全域和 applet 生命周期管理权限。

每条路径都输出 `ProvisioningRequirement`，状态区分为已满足、可执行、需要合作方、仅特权调用、缺失和待确认。这样产品页面可以直接回答“下一步还缺什么”，而不是把 Root/eSE/服务存在误判成“已经能写安全卡”。

Xiaomi 14 当前实机基线为：OEM Wallet 路径可用；OpenSE/MiSE Partner TSM 服务存在但需要合作方身份与服务端授权；Direct eSE 受 OEM `signature|privileged` 权限和安全域管理权约束。Samsung 当前只映射官方 Wallet 路径，不从钱包安装状态推断具体 off-host 卡类型。

### Root Diagnostics Contributor

Root 深度诊断与设备 Profile 使用同样的插件化原则，但单独通过 `RootDiagnosticsContributor` 扩展，避免品牌专属 shell/HAL 逻辑重新进入通用层：

```text
RootNfcDiagnostics
  ├── Generic Framework / SELinux / HAL probe
  └── RootDiagnosticsContributorRegistry
       ├── XiaomiRootDiagnosticsContributor
       └── SamsungRootDiagnosticsContributor
```

Contributor 仅做只读观测。Xiaomi contributor 负责 eSE M1/off-host 证据，Samsung contributor 负责 Samsung Type-4/NXP 扩展证据。后续任何写操作必须走独立 command abstraction，不允许混入 diagnostics。

### Reader compatibility diagnostics

读卡器现场兼容性使用独立 `AccessDiagnosticProbe` 插件边界，不把 Xiaomi `NfcReaderDetector` 细节写入 UI 或通用 domain：

```text
System UI / NfcToolsUiState
        │
        ▼
AccessDiagnosticsManager
        │
        └── AccessDiagnosticProbeRegistry
             └── XiaomiAccessDiagnosticProbe
                    ├── NfcReaderDetector RF Field/session logs
                    ├── NFCEE/eSE action
                    └── sanitized ReaderEntry snapshot
```

Probe 生命周期始终运行在 `DEFAULT`，避免本应用 Reader/HCE 与系统钱包 off-host 路由互相干扰。Xiaomi 实现把原始日志和 AID 仅作为一次性内存证据；`AccessDiagnosticReport` 持久化时只保留 RF Field、协议层、计数、匹配布尔值、结论和建议。

当前分类模型：

- 无 RF Field → `NO_RF_FIELD`；
- 有 RF Field、无卡片/eSE 交互 → `RF_FIELD_NO_CARD_INTERACTION`；
- 已进入卡片/eSE 交互但现场未通过 → `CARD_INTERACTION_AUTH_FAILED` 或 `CARD_INTERACTION_NO_READER_FEEDBACK`；
- 现场成功 → `SUCCESS`，并可作为后续不同读卡器/不同卡片的成功基线。

后续支持其他厂商时新增 Probe，不允许在 Compose 页面直接解析厂商 logcat/dumpsys 格式。

## 产品交互原则

一级交互继续保持当前四区：

1. 首页：设备状态、常用动作、系统钱包卡片；
2. 读卡：Reader Mode 与扫描历史；
3. 测试：标准 Android HCE / 自有协议实验；
4. 系统：能力矩阵、厂商扩展、Root 深度诊断。

厂商能力采用渐进增强：无 Provider 时显示标准 Android 能力；有 Provider 时补充钱包、eSE、off-host 和 vendor 信息。页面结构不随手机品牌变化。

“系统”页的能力卡片从 `NfcCapability` 动态生成：NFC / HCE / Root / eSE 等核心能力保持稳定，HCE-F、UICC、MIFARE Reader、M1 off-host、Type-4 NDEF、Vendor API 等只在设备实际声明或实测支持时出现。厂商详情没有值时不显示该字段，而不是在非目标品牌设备上堆叠“未知”。

首页、系统页和未启动 HCE 的测试页均使用 `DEFAULT`，不会为了展示设备信息而长期占用 Reader Mode。只有进入读卡页才申请 Reader Mode；HCE 也必须由用户显式启动。

运行模式也与导航解耦为三个显式状态：

- `DEFAULT`：首页/系统/普通测试页，不主动占用 Reader Mode；
- `READER`：只在读卡场景启用 Reader Mode；
- `HCE`：用户明确启动测试卡后进入 Host HCE。

这样普通打开 NFC Tools 不会无意义地抑制系统钱包/off-host 路由。

## 自动化接口

Reader 自动化默认保持 Reader Mode，不再无条件执行 disable/enable re-arm。`rearm_reader=true` 只用于明确需要重新触发 RF discovery 的实验场景，避免与 `dumpsys nfc`、厂商 Profile 探测争用 NFC Service 锁并制造假失败。

通用设备快照：

```bash
adb shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode device_profile \
  --es session_id profile-001
```

结果：`files/device_nfc_profile.json`。

Provisioning 能力也可独立导出：

```bash
adb shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode provisioning_capability \
  --es session_id provisioning-001
```

结果：`files/provisioning_capability.json`。报告只包含能力、授权模型与缺失条件，不包含实体卡密钥、支付凭证或 eSE 中的受保护内容。

旧的 `automation_mode=xiaomi_profile` 暂时保留兼容，并额外写入 `xiaomi_nfc_profile.json`。新代码与新自动化应使用 `device_profile`。

## 后续扩展约束

- 厂商 Provider 默认只读探测；修改 routing / RF / SE 状态必须通过独立 command abstraction，并带前置状态、结果验证和回滚策略。
- 卡片 domain 不保存真实支付凭证、密钥或不可公开的认证材料。
- Root 不是能力本身；每个 Root 功能仍需要一个明确、可审计的 command。
- 不以设备 codename 写死 UI 行为。codename 只能用于 Provider 内部兼容矩阵和已验证 workaround。
- ISO-DEP 实验模拟只允许使用 `IsoDepLabProfile` 这类 synthetic/test profile。真实门禁卡密钥、受保护应用数据和门禁认证凭证不进入工程；若管理员拥有正式测试密钥，应通过单独的受控测试环境接入，而不是从实体卡或系统钱包中提取。
- HCE 兼容性追踪只记录 `HostApduService` 收到的帧数量、首尾时间和 deactivation reason，不持久化外部 Reader 的 APDU 内容。这样可以区分“Reader 根本没有路由到 HCE”与“已进入 APDU 层但协议不匹配”。
- Reader Mode 必须服从 Activity 前台生命周期；Android 在应用退到后台、锁屏或不再满足 foreground 条件时可以撤销 Reader Mode，因此自动化验收必须同时记录 `mScreenState` 与 `mEnableReader`，不能只看应用内 mode 状态。
