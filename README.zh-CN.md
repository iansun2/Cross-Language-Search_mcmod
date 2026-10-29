# Cross-Language-Search

[English](README.md) | [繁體中文](README.zh-TW.md) | **简体中文**

Minecraft 1.21.1 NeoForge 纯客户端模组，让游戏内的搜索可以跨语言使用：

- 用**英文**搜索**中文**物品名称和工具提示（`iron` 可以找到 铁锭／鐵錠）
- **繁体、简体中文**互通（`鐵` 可以找到 铁锭）
- 用中文搜索显示为**英文**的文字（`铁` 可以找到显示成 `Iron` 的物品）
- 全角、半角视为相同（`ｑｕａｎｔａ` = `quanta`）

> [!WARNING]
> **这个模组的开发非常依赖 AI。** 代码大部分由 AI 模型在人类指示下编写，并且只在下方列出的环境中测试过。本模组按现状提供，**不保证没有问题**。遇到问题请到 [Issues](https://github.com/iansun2/Cross-Language-Search_mcmod/issues) 反馈。

## 支持的模组

所有整合都是**可选**的。模组会检测当前安装了哪些模组，只启用对应的功能；一个支持的模组都没装时，它不会做任何事。

| 模组 | 改变的地方 |
|---|---|
| [EMI](https://modrinth.com/mod/emi) | 物品列表搜索：名称、工具提示（`$`）、模组名称（`@`）、别名。也会检测运行中会变化的工具提示（例如「尚未食用」），并在搜索时实时匹配。 |
| [Refined Storage](https://modrinth.com/mod/refined-storage) | 网格搜索，包括 `$` 工具提示搜索。 |
| [Apothic Enchanting](https://modrinth.com/mod/apothic-enchanting) | 附魔图书馆的筛选栏。 |
| Reverie Foundry（模组） | 目录中材料与特性的搜索。 |
| [Just Enough Characters](https://modrinth.com/mod/just-enough-characters) | 保留拼音搜索，并修复它的搜索索引在多线程下会出错、导致 EMI 搜索没有结果的问题。 |

也可以和 **Untranslated Items**（让物品名称显示英文）一起使用：这些物品仍然可以用中文名称搜到。

## 测试环境

只在 **Reverie Foundry** 整合包中开发和测试：

| | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
| EMI | 1.1.24 |
| Refined Storage | 2.0.9 |
| Apothic Enchanting | 1.6.2 |
| Reverie Foundry（模组） | 2.3.5 |
| Just Enough Characters | 4.5.29 |
| Untranslated Items | 1.19 |

其他版本可能也能用，但这些整合会接入目标模组的内部代码。如果目标模组改版导致接入点改变，受影响的功能会直接跳过，不会让游戏崩溃。

## 安装

1. 从 [Releases](https://github.com/iansun2/Cross-Language-Search_mcmod/releases) 下载 jar。
   - 建议使用带版本号的正式版（`v*`）。
   - `latest` 预发布版会从 `main` 自动构建，可能不稳定。
2. 放进 `mods` 文件夹。这是纯客户端模组，服务器不需要安装。

## 工作原理

- **文本规范化**：索引的文本和你输入的查询都会经过同一套处理：去除格式代码、NFKC（全角转半角）、转小写、繁转简（对照表来自 [OpenCC](https://github.com/BYVoid/OpenCC)）。
- **英文索引**：另外加载一份 `en_us` 语言表（不会改变游戏语言），把每个名称和每行工具提示重新组成英文，加入搜索索引。
- **反查表**：只能拿到已翻译字符串的地方（Refined Storage、目录），使用从语言文件建立的中英双向对照表，也支持 ` - Quanta: %s%%` 这类格式字符串。
- **动态工具提示**（EMI）：在后台重新检查工具提示（每 tick 约 1 ms）。工具提示会自己变化的物品，会记录在 `config/crosslanguagesearch-dynamic.txt`，每次搜索时实时匹配。以下情况造成的变化会被忽略：按住 Shift／Ctrl／Alt、切换高级工具提示、大量物品同时变化。

## 文件

| 路径 | 用途 |
|---|---|
| `config/crosslanguagesearch-dynamic.txt` | 检测到的动态工具提示物品。可以自由编辑；删除后会重新检测。 |
| `config/crosslanguagesearch-debug.txt` | 创建这个文件就会在日志中记录搜索诊断。每一行（`#` 开头的注释除外）是一个探针词，建立索引时含有该词的索引内容会被记录。删除即关闭。 |

## 构建

需要 JDK 21。

```sh
./gradlew build
```

jar 会输出到 `build/libs/`。编译用的依赖（EMI、Refined Storage）从 Modrinth Maven 下载，不会打包进 jar。

## 发布

- 推送到 `main`：自动更新 `latest` 预发布版。
- 推送 `v0.2.0` 这类标签：自动创建 `v0.2.0` 版本并附上 jar。标签去掉 `v` 后就是模组版本号。

## 许可证

[MIT](LICENSE)。可以自由放进整合包、重新分发和修改，只需保留许可声明。

内附的繁简对照表来自 OpenCC，采用 Apache-2.0 许可证，详见 [NOTICE](NOTICE)。
