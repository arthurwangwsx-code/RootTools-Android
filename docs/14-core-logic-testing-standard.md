# Root Tools 核心逻辑与测试规范

## 1. 目标

Root Tools 会直接修改 Android 系统状态，因此“页面能点通”不能作为完成标准。任何会影响 package、AppOps、CPU、ADB、模块、组件或其它系统状态的核心逻辑，都必须先建立可验证的行为契约，再接 UI。

本规范从 Shizuku / Sui 接入开始执行，并逐步覆盖现有 Controller。

---

## 2. 强制分层

```text
Compose / Tile / Automation
          │
          ▼
    Typed Controller
          │
          ▼
 Pure Policy / Validator   ← JVM unit tests
          │
          ▼
 Privilege Adapter        ← Shizuku / RootShell integration
          │
          ▼
 Android / Linux system
```

规则：

1. UI、Quick Tile、Broadcast Receiver 不直接拼 privileged shell；
2. Backend 选择、风险判断、输入校验必须尽量写成 Android-free pure logic；
3. pure logic 必须有 JVM unit test；
4. 真实 Binder / shell / sysfs 行为用编译测试 + Samsung 真机测试覆盖；
5. 外部入口只能传语义参数，不接受任意 shell 文本。

---

## 3. Backend 路由契约

Framework 类操作：

```text
Package / Component / Activity / AppOps / Framework diagnostics
    → Shizuku/Sui first
    → RootShell fallback（仅安全、幂等操作）
```

Linux / root-only 操作：

```text
sysfs / root files / Magisk / adbd
    → RootShell only
```

不能因为 Sui 的 UID 是 0 就把所有现有 root-only 逻辑迁到 Shizuku；选择 Backend 必须以语义 Capability 为准。

### 自动 fallback 的边界

允许：

- read/query；
- force-stop；
- set enabled state；
- set component state；
- set standby bucket；
- set AppOp mode。

这些动作具有目标状态，重复执行不会产生新的累计副作用。

禁止无条件自动 retry / fallback：

- install / uninstall；
- clear data；
- destructive file mutation；
- module install/remove；
- reboot / flash；
- 任何无法判断“第一次是否部分成功”的动作。

---

## 4. 输入安全契约

所有会进入 privileged adapter 的动态 token 必须先经过统一 validator：

- package name；
- flattened component name；
- AppOp name；
- AppOp mode；
- standby bucket；
- 后续新增的 userId / permission / profile id 等。

禁止：

```text
UI string → "pm ... $value" → shell
```

必须：

```text
UI value → semantic validator → typed controller → fixed command/API template
```

单元测试至少覆盖 `;`、`&&`、空格、换行、非法 component 等注入/畸形输入。

---

## 5. 每个核心 Feature 的最低测试集

新增或修改核心逻辑时，至少覆盖：

1. happy path；
2. capability 不可用；
3. Shizuku permission denied / binder unavailable；
4. Root fallback；
5. 非法输入；
6. protected target；
7. 边界值；
8. action result / backend attribution；
9. 可回滚信息；
10. 原有功能 regression。

纯决策逻辑不允许只靠真机手测。

---

## 6. 提交与验收顺序

一个高风险 Feature 按以下顺序推进：

```text
contract / design
    ↓
pure policy + unit tests
    ↓
backend adapter
    ↓
controller integration + audit
    ↓
UI
    ↓
assemble / lint / unit tests
    ↓
Samsung real-device validation
    ↓
atomic commit + push
```

如果真机验证发现 OEM 差异，先把差异补回文档和 capability probe，再修实现，禁止在 UI 里临时加隐藏分支。

---

## 7. 当前 Shizuku/Sui 必测契约

- UID `2000` = Shizuku ADB，不显示成 ROOT；
- UID `0` + Sui = SUI ROOT；
- UID `0` + 非 Sui = Shizuku ROOT；
- Binder alive 但 permission denied 时不允许执行 Shizuku route；
- Framework operation 在 Shizuku Ready 时优先 Shizuku；
- Shizuku 不可用但 Root 可用时继续 RootShell；
- `SYSFS_WRITE` 等 root-only capability 不迁移到 Shizuku/Sui；
- 无 Root + Shizuku ADB 时允许可支持的 Framework operation；
- 所有动态 package/component/AppOp 参数拒绝 shell injection；
- Backend 失败不能被 UI 静默吞掉，必须保留 backend 与 failure detail。
