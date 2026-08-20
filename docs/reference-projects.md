# 本地参考工程

RootTools 允许在工程根目录的 `references/` 下保留外部开源项目，供产品能力、系统行为和边界设计调研使用。

`references/` 已加入根目录 `.gitignore`：参考仓库不会进入 RootTools Git 历史，也不会参与 Android 构建、打包和发布。

## App Manager

本地路径：

```text
references/AppManager
```

上游：

```text
https://github.com/MuntashirAkon/AppManager.git
```

当前首次固定参考版本：

```text
fc1e700  2026-06-29  Improve the creation process of main.jar and am.jar
```

本地仓库已补齐完整 Git 历史与 tags（不是 shallow clone），便于后续用 `git log / blame / tag` 追踪某项系统能力在不同 Android 版本上的演进。

许可证：`GPL-3.0-or-later`。

### 允许参考的内容

- 应用清单的信息架构；
- App Detail 的功能分组；
- Activity / Service / Receiver / Provider 管理范围；
- Permission / AppOps 的产品表达；
- Batch / Profile / Debloater / Backup / Scanner 等能力边界；
- Root / ADB / framework service 在不同 Android 版本上的问题分类；
- 性能策略，例如重数据按需加载、搜索/筛选和批处理交互。

### RootTools 的实现边界

RootTools **不复制** App Manager 的 GPL 源文件、资源、图标、tracker 数据集或内部实现。所有正式代码继续按 RootTools 自己的架构实现：

```text
Compose UI
   ↓
Repository / Typed Controller
   ↓
PrivilegeRouter
   ├── Shizuku / Sui
   └── RootShell
```

因此参考项目可以持续升级，但 RootTools 不依赖它进行编译，也不会因为参考项目不可用而影响产品运行。

### 更新参考仓库

需要同步最新上游时，在 RootTools 根目录执行：

```bash
git -C references/AppManager pull --ff-only
```

更新后如果关键能力或 Android 兼容策略发生变化，应把结论更新到 RootTools 自己的 `docs/`，不要让实现知识只存在于被忽略的参考目录中。
