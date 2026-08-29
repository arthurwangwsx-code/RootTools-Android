# Xiaomi 14 (`houji`) NFC 实机基线

> 这份文件是 NFC Tools 针对当前 Xiaomi 14 的长期设备基线。后续功能设计、兼容性判断和回归测试优先以这里的实机事实为准，不再把“小米钱包门卡”误认为普通 Android HCE。

## 设备事实

- 设备：Xiaomi 14，device codename `houji`，Android 15 / API 35。
- Root：Magisk `su` 可获得 `uid=0`，SELinux 仍为 Enforcing。
- Android feature：NFC / HCE / HCE-F / eSE / UICC / `com.nxp.mifare` 均存在。
- NFC HAL：NXP AIDL vendor service 存在；小米私有 `com.xiaomi.nfc.IMiNfcAdapter` Binder service 存在。
- NFC transport：I2C。
- 实机读取到的 NXP NFC firmware：`01.01.38`；chip id：`0xc1`。
- Secure Element：`eSE1` 已连接。

## 小米官方 M1 门卡链路

当前小米钱包中存在两张通过官方 NFC 门卡功能创建的 M1 虚拟卡，UI 名称为“公司”和“小区”。其中“公司”在本轮检查时处于 ACTIVATED，“小区”处于 DEACTIVATED。

小米钱包本地数据将这两张卡识别为：

- `cardName = MIFARE_ENTRANCE`
- `mifare_card_type = 0`（普通 M1）
- `door_card_product_name = 实体门卡`
- `is_sector_overwritten = true`

真实 VC UID / CID 等凭证标识不写入本工程文档、日志或测试快照。

## 实体 ISO-DEP 卡识别基线

2026-08-25 使用 Reader Mode 对一张实体卡做了只读协议识别。该实体卡与上面的“小米钱包 M1 虚拟卡”是两类不同证据，不能因为名称或使用场景相近就自动视为同一凭证。

本次实体卡公开协议特征：

- `NfcA + IsoDep`；
- ATQA `4403`，SAK `0x20`；
- NXP `GetVersion` 成功；
- Product Type 属于 `MIFARE DESFire / DUOX` 家族；
- Hardware version `1.0`；
- Software version `1.4`；
- Storage code `0x16`，即 2K；
- Protocol code `0x05`。

按 NXP 官方 `AN10833` 的识别规则，DUOX 的 HW Major Version 为 `0xA0`；当前实测为 `0x01`，因此这张卡应识别为 **MIFARE DESFire EV1 2K**，而不是 DUOX。NFC Tools 已把该规则加入 Reader 的只读产品识别器，且不会持久化 GetVersion 第三帧中的制造/序列信息。

Reader 页面还会对最近两张不同实体卡做公开协议指纹对比。若两张卡 RF / ISO-DEP / NXP 产品指纹一致但门禁表现不同，排查重点应转向卡内应用、认证数据或读卡器策略，而不是继续把问题归因于 NFC 频段或 Android Reader Mode。

## 小米私有 `mi_nfc` API 实机基线

系统 `/system_ext/framework/com.xiaomi.nfc.jar` 已确认包含：

- `com.xiaomi.nfc.MiNfcAdapter`
- `com.xiaomi.nfc.IMiNfcAdapter`
- Binder service：`mi_nfc`

只读接口实机调用结果：

| 能力 | 结果 |
|---|---|
| `getVersion()` | `1` |
| `getSeRouting()` | `1`，即 eSE |
| `getDiscoveryTech(LISTEN_MODE)` | `0x01`，当前 listen 为 NFC-A |
| `getDiscoveryTech(POLLING_MODE)` | `0x0F`，轮询 A/B/F/V |
| `getChipId()` | `0xc1` |
| `getFwVersion()` | `01.01.38` |

接口还包含 `setSeRouting`、`setDiscoveryTech`、`setConfig` 和 custom RF config 等写接口。当前 NFC Tools **只探测并展示只读结果**，没有调用这些写接口；后续只有在明确回滚策略、设备状态保护和实机回归用例齐备后，才考虑把必要的设备控制能力产品化。

## 最关键的架构结论

当前 `/odm/etc/libnfc-nxp.conf` 的关键配置为：

```text
MIFARE_READER_ENABLE=0x01
LEGACY_MIFARE_READER=0
DEFAULT_AID_ROUTE=0x01
DEFAULT_ISODEP_ROUTE=0x01
DEFAULT_MIFARE_CLT_ROUTE=0x01
DEFAULT_MIFARE_CLT_PWR_STATE=0x3B
HOST_LISTEN_TECH_MASK=0x07
NXP_EXTENDED_FIELD_DETECT_MODE=0x01
NXP_T4T_NFCEE_ENABLE=0x01
```

在这台设备上 `0x01` 对应 eSE，因此：

```text
实体 M1 门卡
   -> 小米钱包读取 / 校验
   -> Xiaomi TSM / provisioning
   -> eSE 内的 M1 虚拟卡 / applet
   -> NFCC MIFARE technology routing
   -> 门禁 Reader
```

这条链路是 **OEM off-host MIFARE card emulation**，不是：

```text
Android App -> HostApduService -> ISO-DEP HCE
```

因此后续针对 Xiaomi 14 的功能优先级应是：

1. 读取和展示小米钱包已经 provision 的 M1 门卡状态；
2. 识别 eSE / MIFARE routing / `mi_nfc` / NXP vendor service；
3. 研究稳定、可审计的官方卡片管理入口和小米私有 NFC Binder 能力；
4. 保留标准 Reader / HCE Lab，用于普通 NFC 标签和自有 ISO-DEP 协议；
5. 不把“Root”错误等价为“任意 MIFARE Classic Host HCE”。

## 2026-08-25 DESFire / Native Card-Emulation 逆向结论

最新实体卡 Reader 扫描通过 NXP `GetVersion` 只读识别为 **MIFARE DESFire EV1 2K**，技术栈为 NFC-A + ISO-DEP。该结果只来自公开产品识别命令，不读取 DESFire Application、File、认证密钥或受保护数据。

当前 ROM 的 NXP Framework 扩展还确认存在：

- `MifareDesfireRouteSet(...)`：设置 DESFire/ISO-DEP protocol route 与 power state；它只决定 Host/eSE/UICC 路由，不创建凭证；
- `doReadT4tData(...)` / `doWriteT4tData(...)`：NXP T4T NFCEE 的 Type-4 NDEF 数据能力，不是通用 DESFire applet provisioning；
- `startExtendedFieldDetectMode(...)` / `stopExtendedFieldDetectMode()`；
- `startCardEmulation()`。

对 `libsn100nfc_nci_jni.so` 的只读符号与反汇编确认，`NativeExtFieldDetect::startCardEmulation()` 的核心流程为：

```text
check NFC active
  -> stop RF discovery (if running)
  -> NFA_SetFieldDetectMode(false)
  -> NFA_EnableListening()
  -> restart RF discovery
```

因此这里的 `startCardEmulation()` 实际作用是从 Extended Field Detect 路径恢复 **Listen / Card-Emulation 接收态**，并不是一个可装载任意 UID、DESFire Application、File 或密钥的通用卡模拟器。

### DESFire route-to-Host 的最终语义

继续追踪 `MifareDesfireRouteSet(...)` 得到完整路径：

```text
MifareDesfireRouteSet(...)
  -> PREF_MIFARE_DESFIRE_PROTO_ROUTE_ID
  -> computeAndSetRoutingParameters()
  -> setRoutingEntry(type=PROTOCOL, protocol=DESFIRE, destination=Host/eSE/UICC, powerState)
  -> RoutingManager::registerProtoRouteEnrty(...)
  -> NFA_EeSetDefaultProtoRouting(...)
```

当 destination 为 Host 时，NXP native stack 上送的 ISO-DEP host-emulation event 会进入：

```text
NativeNfcManager.onHostCardEmulationData(...)
  -> NfcService
  -> CardEmulationManager
  -> HostEmulationManager
  -> SELECT AID resolution
  -> android.nfc.cardemulation.action.HOST_APDU_SERVICE
```

`HostEmulationManager` 会直接绑定 Android `HostApduService`。在 `libsn100nfc_nci_jni.so` 中也未发现另一套 NXP 专用 Host DESFire application/authentication state machine；DESFire routing 最终使用的是 `NFA_EeSetDefaultProtoRouting`。

因此 Xiaomi 14 上“DESFire route 到 Host”可以正式定性为 **protocol routing → Android Host HCE**。它能够决定 ISO-DEP traffic 去 Host 还是 eSE/UICC，但**不会因为改变 route 就自动获得 DESFire Application、File、密钥、Secure Messaging 或实体卡 RF identity**。

### Host HCE 首帧分派限制

继续检查当前 ROM 的 `HostEmulationManager.onHostEmulationData()` 得到一个对 DESFire 模拟非常关键的限制：

```text
Host emulation activated
  -> state = WAIT_FOR_SELECT
  -> first host data arrives
  -> findSelectAid(data)
       only accepts 00 A4 04 00 <Lc> <AID>
  -> if SELECT AID matched:
       resolve service
       bind android.nfc.cardemulation.action.HOST_APDU_SERVICE
  -> otherwise:
       "Dropping non-select APDU in STATE_W4_SELECT"
       send UNKNOWN_ERROR
```

也就是说，Android `HostApduService` 并不是“所有 ISO-DEP 数据都直接交给某个 App”。在一个新的 Host HCE session 中，Framework 需要先看到 ISO 7816 SELECT AID，才能决定绑定哪个 HCE service。

这对原生 DESFire Reader 很关键：DESFire reader 可以直接发送 DESFire native / ISO-wrapped native command，而不先执行 Android HCE 所要求的 `SELECT AID`。这种 Reader 即使已经通过 protocol route 把 ISO-DEP traffic 送到 Host，也可能在 `HostEmulationManager` 层就被丢弃，根本到不了应用的 `HostApduService`。

因此对这张 MIFARE DESFire EV1 2K 来说，标准 Host HCE 的 transport 能力应标记为 **PARTIAL**：

- 自有 Reader 若先 SELECT 我们注册的测试 AID，可以完整进入 HostApduService 做 synthetic APDU 实验；
- 如果真实门禁 Reader 使用原生 DESFire 首帧，则标准 Android HCE 的服务分派模型本身就是一个前置阻塞点；
- `MifareDesfireRouteSet(..., Host, ...)` 只能改变 protocol destination，不能绕开 `HostEmulationManager` 的 SELECT-AID 分派规则。

### NXP DTA / RF 产测路径

SN100 native 栈还包含 NFC Forum DTA / Self Test 能力：

- Framework `INfcDta` 的 `enableDta()/disableDta()` 等入口全部经过 `NfcPermissions.enforceAdminPermissions()`；
- 这里的 Admin 权限实际是 `android.permission.WRITE_SECURE_SETTINGS`；
- native `NfcDta::setNfccConfigParams()` 会读取系统属性 `nfc.dta.configTLV`；
- 解析后通过 `NfcDta::setConfigParams()` / `NFA_SetConfig()` 应用到 NFCC；
- native 另有 `NfcSelfTest::PerformRFTest()`、PRBS、RF Tx 配置等明显面向产测/认证的能力。

因此 ROM 确实存在比普通 Android API 更低层的 **NFCC certification/test configuration path**。但目前只确认它能注入认证测试 TLV，尚未证明存在一个适合生产环境、可稳定覆盖任意 NFCID1 / ATQA / SAK 并保持正常 DESFire/HCE 协议行为的接口。工程上把它标记为“RF 层部分可控 / 产测路径”，而不是“可用的实体卡 RF 身份模拟器”。

eSE 侧还存在两层明确授权边界：

1. `com.miui.permission.ACCESS_ESE` 为 `signature|privileged`；系统小米钱包持有该权限；
2. NXP SEMS 通过 OMAPI 与 eSE 通信，但会注册调用方 hash、处理证书/认证帧并执行结构化 SEMS script，未发现通用 `LOAD / INSTALL / DELETE applet` 接口。

小米钱包 APK 的只读结构进一步显示 `SecureDomainMiTSMCardClient` 会取得 `TsmAPDUCommandList`，再通过 `Ese2SmartMxTerminal` 执行到 eSE。因此当前最符合实机证据的 provisioning 模型是：

```text
TSM / server task
  -> signed/authorized command list
  -> privileged Xiaomi Wallet client
  -> Ese2SmartMxTerminal / OMAPI
  -> eSE
```

这也是为什么“有 Root”与“可以任意 provision 一张 DESFire off-host 凭证”是两件不同的事。

进一步反编译当前小米钱包的 `OpenSeService` 得到一个更具体的受控接入模型：

- `OpenSeService` 在 manifest 中 `exported=true`，action 为 `com.miui.tsmclient.action.ESE2_OPEN_SERVICE`；
- Binder 接口只有一个窄入口 `s1(action, Bundle)`；
- 服务先用 Binder caller UID 找真实包名，再计算调用包签名的 SHA-1；
- 如果调用方与小米钱包同签名则直接通过，否则请求 `GET api/app/sign/verify` 验证 `appPkgName + appPkgSign`；
- 真正的 eSE operation 请求为 `POST api/se/start/operation`，参数包含 app info、授权 credential、目标 AID 和 operation；
- 服务端返回 `TsmAPDUTaskDetail / TsmAPDUCommandList` 后，客户端才通过 `Ese2SmartMxTerminal` 执行；
- eSE 响应随后通过 `api/se/task/processResponse` 回传以取得后续任务。

因此 `OpenSeService` 是一个**导出的合作方接口，但内部由 caller signature + TSM server authorization 控制**。如果未来有正式测试包签名、测试 JWT/credential 和测试 AID，这条链路是当前最值得优先接入的 off-host 测试方案；它并不是一个可以离线构造任意真实门禁凭证的 raw APDU/eSE 后门。

当前钱包还保留一条旧版合作方 SDK 接口 `MiSeOpenService`：

- manifest `exported=true`，action 为 `com.miui.tsmclient.action.SE_OPEN_SERVICE`；
- Binder descriptor 为 `com.miui.tsmclient.open.IMiSeOpenService`；
- 四个能力分别是 `executeSeOperation`、`getOperationResult`、`login`、`getSeid`；
- 每个敏感调用先要求 `spId`，然后通过 Binder caller UID 取实际包列表，要求 `spId` 必须等于真实 caller package；
- 随后读取该包签名并调用 `POST api/%s/busCard/outApp/verifySpInfo`，参数包括 `spId`、`cardName` 和 `appSign`；
- `executeSeOperation` 接受 `sessionId/authType/jwtToken/operation/extraData` 等受控参数，最终仍然进入 `TsmStartActionResponse -> TsmAPDUCommand -> eSE` 执行链；
- `getOperationResult` 继续通过 TSM session 获取服务端 operation response。

因此 Legacy MiSE 与新版 OpenSE 本质上属于同一安全模型：**真实调用包身份 + 包签名 + TSM 服务端授权 + 服务端 APDU task**。它更像旧版 Partner SDK 兼容层，而不是比新版接口权限更宽的本地 eSE 后门。

## 产品交互基线

NFC Tools 不再采用单页工程控制台式 UI。面向日常使用应分为：

- **首页**：设备状态、常用动作、小米钱包门卡、最近识别；
- **读卡**：明确的贴卡状态、当前结果、历史记录；
- **模拟**：Host HCE synthetic profile、模拟能力分层与 Reader 兼容实验；
- **系统**：Root / eSE / NXP / MIFARE routing 的高级诊断。

普通用户路径使用友好的中文状态表达；原始 dumpsys / HAL / AID 等技术细节放到二级区域，不占据主流程。

## 2026-08-24 Reader / HCE 三态回归

在设备 `mScreenState=ON_UNLOCKED` 时，通过 ADB 自动化进入 Reader Mode，系统实测最终状态：

```text
mEnableReader: true
mEnableHostRouting: false
```

同一版本进入 HCE 测试模式时：

```text
mEnableReader: false
mEnableHostRouting: true
```

此前锁屏状态下出现 Reader 请求被系统拒绝，已经确认是 Android 前台/锁屏约束，而不是 Reader 实现回归。因此产品运行模式固定为：

- 首页 / 系统：`DEFAULT`，不主动占用 Reader；
- 读卡页：`READER`；
- HCE：仅用户明确启动测试卡时进入 `HCE`。

这一约束可以避免 NFC Tools 日常打开时不必要地影响小米钱包的 eSE/off-host 路由。

## 自动化约定

ADB 自动化除了既有 `reader` / `hce` / `root_diag` 外，增加：

```bash
adb shell am start -W \
  -n com.arthur.nfclab/.MainActivity \
  --es automation_mode xiaomi_profile \
  --es session_id xiaomi-profile-001
```

结果写入：

```text
files/xiaomi_nfc_profile.json
```

结构化结果包含设备、Root、eSE、NXP firmware、MIFARE route、官方 M1 卡名称/激活状态等信息，但不包含 VC UID / CID 或支付凭证。
