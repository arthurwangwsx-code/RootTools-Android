# Root 模块中心

## 覆盖对象

- Magisk modules
- Zygisk modules
- Vector / Xposed modules
- Vector scope

## 首页摘要

- Magisk 模块总数
- enabled / disabled
- Vector 是否 active
- Xposed enabled modules 数量
- 是否存在“双框架”或冲突风险

## 模块详情

- id / name / version
- enabled
- description
- 是否需要 reboot
- dependencies
- action entry

## Vector

- modules list
- enabled state
- scope list
- bridge log recent errors

## 风险控制

模块 enable/disable 属于高风险操作：

- 必须二次确认
- 明确提示“下次重启生效”
- 当前远程设备若没有可靠 reboot 后回连能力，不主动重启
- 保留前一个模块状态用于回滚

## 2026-08-19 实施拆解（Milestone E1）

### E1.1 Magisk repository

扫描 `/data/adb/modules/*`：

- `module.prop`
- `disable`
- `remove`
- `update`

展示 `id / name / version / author / description / enabled / pending remove`。

写操作只允许：

- disable：创建 `disable` marker
- enable：删除 `disable` marker

不从 Root Tools 执行 module install / uninstall zip。

### E1.2 Vector repository

使用设备现有 Vector CLI：

```text
/data/adb/lspd/cli modules
```

解析 package / UID / STATUS；Scope 第一版只读，通过 `scope ls <module>` 获取。

### E1.3 Protected framework

以下对象默认 Protected：

- `zygisk_vector`
- `com.arthur.roottools`（如果未来自身成为模块）

旧 `zygisk_lsposed` 可以显示状态，但写操作需要二次确认。

### E1.4 生效语义

Magisk/Zygisk module：

```text
UI 修改 marker
→ pending reboot
→ 不自动 reboot
```

Vector Xposed module：CLI enable/disable 后即时刷新。

### E1.5 验收

- 能正确列出当前 `zygisk_vector / zygisk_lsposed` 等 Magisk 模块
- 能显示 Vector 中 Hail / SpoofMyDevice / WeChatTablet 的 enabled 状态
- Protected module 没有普通禁用按钮
- Magisk module 修改后显示 pending reboot
- 不触发自动重启
