# NFC Probe (iOS)

`NFCProbe` 是 NFC-Tools 的 iPhone 对端验证应用，用来对 Samsung NFC Lab 的射频与 HCE 行为做真实真机验证。

## 当前能力

- `NFCTagReaderSession(.iso14443)` 扫描；
- 识别 `NFCMiFareTag`，记录 identifier / family / historical bytes；
- 识别 `NFCISO7816Tag`，记录 identifier / selected AID / historical bytes；
- 对 NFC Lab 测试 AID `F001020304050607` 自动发送 `80 CA 00 00 00`；
- 展示并复制机器可读 JSON 结果。

## 设计边界

Core NFC 不暴露 MIFARE Classic Crypto1 认证，因此本应用用于 RF、标签类型、UID/identifier、AID 和 ISO7816 APDU 验证，不作为 MIFARE Classic Crypto1 验证器。

## 安装

项目根目录执行：

```bash
./scripts/ios-probe.sh doctor
./scripts/ios-probe.sh install
```

脚本会：

1. 用 XcodeGen 生成 Xcode 工程；
2. 自动寻找 `Apple Development` Team；
3. 自动寻找可用的物理 iPhone；
4. `xcodebuild -allowProvisioningUpdates` 构建；
5. 通过 `devicectl` 安装并启动。

### 第一次签名

NFC Tag Reading 需要带 `com.apple.developer.nfc.readersession.formats=TAG` 的 Development provisioning profile。若这台 Mac 的 Xcode 还没有登录开发者账号，先做一次：

1. Xcode → Settings → Accounts；
2. 登录你的 Apple Developer Program Apple ID；
3. 保证目标 Team 可见；
4. 重新执行 `./scripts/ios-probe.sh install`。

之后 App ID / NFC capability / Development profile 可以由 Xcode automatic signing 自动维护。

也可显式指定：

```bash
TEAM_ID=XXXXXXXXXX DEVICE_ID=<CoreDevice UUID> ./scripts/ios-probe.sh install
```

