# 常用 Root 操作

## 设计目标

把经常通过终端执行的命令做成可理解、可审计、可确认的动作卡片。

## 低风险动作

- 打开 Developer Options
- 打开 Magisk
- 打开 Vector
- 打开 Hail
- 刷新 Root 状态
- restart adbd
- 复制 ADB connect
- 刷新 Tailscale 地址

## 中风险动作

- restart SystemUI
- force-stop App
- freeze / enable App
- clear stuck media session
- apply Cool
- restore Auto

需要确认但不要求倒计时。

## 高风险动作

- reboot
- reboot recovery
- reboot bootloader / download
- disable Magisk module
- 修改开机持久化脚本
- 修改系统 thermal 配置

> **当前版本实施边界**：`reboot / recovery / bootloader` 仅保留设计，不提供可执行 UI/API；Magisk module 修改单独在“Root 模块”页二次确认并标记 pending reboot。只有 Tailscale + Root ADB 的 post-boot 自动回连经过真机验收后，才开放远程 reboot 类动作。

高风险动作：

1. 二次确认。
2. 显示可能后果。
3. 远程连接场景显示“可能失联”。
4. 3 秒倒计时后才允许执行。

## 收藏动作

用户可将常用动作加入首页的“快捷操作”区，但高风险动作只允许收藏入口，不允许收藏成一键执行。

## Automation API

MacroDroid / Agent 可以调用语义化 action，不直接执行 shell：

```text
SET_MODE(AUTO|COOL|PERFORMANCE)
SET_ADB(true|false)
RUN_DIAGNOSTIC
FREEZE(package)
UNFREEZE(package)
```

## 2026-08-19 实施拆解（Milestone E2）

### E2.1 Action Registry

Root Tools 不提供“输入 shell 命令”能力，只注册固定语义动作：

低风险：

- open Developer Options
- open Magisk / Vector / Hail
- copy ADB connect
- refresh

中风险：

- restart adbd
- restart SystemUI
- stop Bilibili stale background
- set Cool / Auto

高风险：

- reboot
- reboot recovery
- reboot bootloader

高风险统一二次确认 + 5 秒倒计时，并提示远程连接可能失联。

### E2.2 Automation Broadcast API

注册一个明确 action，但只接受显式 component 广播：

```text
com.arthur.roottools.ACTION
```

ADB 示例必须包含：

```text
-n com.arthur.roottools/.automation.ActionRouterReceiver
```

Receiver 会拒绝没有显式 component 的隐式广播，避免 token 被其他 App 监听同名 action 截获。

接收固定 `command`：

- `SET_MODE`
- `SET_ADB`
- `RUN_DIAGNOSTIC`
- `FREEZE`
- `UNFREEZE`

不接受任意 shell 文本。

由于 MacroDroid / ADB shell 与 Root Tools 不共享签名权限，第一版采用**本机随机 token**：

- 首次启动生成
- 权限/操作页可复制
- Broadcast 必须携带正确 token
- token 不写入 logcat

### Performance App Whitelist

不在 Root Tools 内监听前台 App；由 MacroDroid 使用 Automation API：

```text
触发器：应用启动（例如 Geekbench / 3DMark）
动作：SET_MODE / PERFORMANCE

触发器：应用关闭
动作：SET_MODE / AUTO
```

等价 ADB：

```bash
adb shell am broadcast \
  -n com.arthur.roottools/.automation.ActionRouterReceiver \
  -a com.arthur.roottools.ACTION \
  --es token <TOKEN> \
  --es command SET_MODE \
  --es mode PERFORMANCE

adb shell am broadcast \
  -n com.arthur.roottools/.automation.ActionRouterReceiver \
  -a com.arthur.roottools.ACTION \
  --es token <TOKEN> \
  --es command SET_MODE \
  --es mode AUTO
```

### E2.3 Diagnostic File Export

报告保存：

```text
Android/data/com.arthur.roottools/files/diagnostics/
```

通过 `FileProvider + Share Sheet` 分享，不申请全盘存储权限。

### E2.4 验收

- 不带 token 的 Automation Intent 不执行
- 不支持未知 command
- 当前版本没有远程 reboot 执行入口
- 未来开放高风险动作时不得一击执行
- Diagnostic Snapshot 可落文件并通过 Share URI 暴露
