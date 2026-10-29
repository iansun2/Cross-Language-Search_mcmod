# Cross-Language-Search

[English](README.md) | **繁體中文** | [简体中文](README.zh-CN.md)

Minecraft 1.21.1 NeoForge 純客戶端模組，讓遊戲內的搜尋可以跨語言使用：

- 用**英文**搜尋**中文**物品名稱和工具提示（`iron` 可以找到 铁锭／鐵錠）
- **繁體、簡體中文**互通（`鐵` 可以找到 铁锭）
- 用中文搜尋顯示為**英文**的文字（`鐵` 可以找到顯示成 `Iron` 的物品）
- 全形、半形視為相同（`ｑｕａｎｔａ` = `quanta`）

> [!WARNING]
> **這個模組的開發非常依賴 AI。** 程式碼大部分由 AI 模型在人類指示下撰寫，而且只在下方列出的環境測試過。本模組依現狀提供，**不保證沒有問題**。遇到問題請到 [Issues](https://github.com/iansun2/Cross-Language-Search_mcmod/issues) 回報。

## 支援的模組

所有整合都是**選用**的。模組會偵測目前安裝了哪些模組，只啟用對應的功能；一個支援的模組都沒裝時，它不會做任何事。

| 模組 | 改變的地方 |
|---|---|
| [EMI](https://modrinth.com/mod/emi) | 物品清單搜尋：名稱、工具提示（`$`）、模組名稱（`@`）、別名。也會偵測執行中會改變的工具提示（例如「尚未食用」），並在搜尋時即時比對。 |
| [Refined Storage](https://modrinth.com/mod/refined-storage) | 網格搜尋，包含 `$` 工具提示搜尋。 |
| [Apothic Enchanting](https://modrinth.com/mod/apothic-enchanting) | 附魔圖書館的篩選欄。 |
| Reverie Foundry（模組） | 目錄中材料與特性的搜尋。 |
| [Just Enough Characters](https://modrinth.com/mod/just-enough-characters) | 保留拼音搜尋，並修正它的搜尋索引在多執行緒下會出錯、導致 EMI 搜尋沒有結果的問題。 |

也可以和 **Untranslated Items**（讓物品名稱顯示英文）一起使用：這些物品仍然可以用中文名稱搜到。

## 測試環境

只在 **Reverie Foundry** 模組包中開發和測試：

| | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
| EMI | 1.1.24 |
| Refined Storage | 2.0.9 |
| Apothic Enchanting | 1.6.2 |
| Reverie Foundry（模組） | 2.3.5 |
| Just Enough Characters | 4.5.29 |
| Untranslated Items | 1.19 |

其他版本可能也能用，但這些整合會接入目標模組的內部程式碼。如果目標模組改版導致接入點改變，受影響的功能會直接跳過，不會讓遊戲崩潰。

## 安裝

1. 從 [Releases](https://github.com/iansun2/Cross-Language-Search_mcmod/releases) 下載 jar。
   - 建議使用有版本號的正式版（`v*`）。
   - `latest` 預發布版會從 `main` 自動建置，可能不穩定。
2. 放進 `mods` 資料夾。這是純客戶端模組，伺服器不需要安裝。

## 運作方式

- **文字正規化**：索引的文字和你輸入的查詢都會經過同一套處理：去除格式碼、NFKC（全形轉半形）、轉小寫、繁轉簡（對照表來自 [OpenCC](https://github.com/BYVoid/OpenCC)）。
- **英文索引**：另外載入一份 `en_us` 語言表（不會改變遊戲語言），把每個名稱和每行工具提示重新組成英文，加進搜尋索引。
- **反查表**：只拿得到已翻譯字串的地方（Refined Storage、目錄），使用從語言檔建立的中英雙向對照表，也支援 ` - Quanta: %s%%` 這類格式字串。
- **動態工具提示**（EMI）：在背景重新檢查工具提示（每 tick 約 1 ms）。工具提示會自己改變的物品，會記錄在 `config/crosslanguagesearch-dynamic.txt`，每次搜尋時即時比對。以下情況造成的變化會被忽略：按住 Shift／Ctrl／Alt、切換進階工具提示、大量物品同時改變。

## 檔案

| 路徑 | 用途 |
|---|---|
| `config/crosslanguagesearch-dynamic.txt` | 偵測到的動態工具提示物品。可以自由編輯；刪除後會重新偵測。 |
| `config/crosslanguagesearch-debug.txt` | 建立這個檔案就會在 log 記錄搜尋診斷。每一行（`#` 開頭的註解除外）是一個探針字詞，建索引時含有該字詞的索引內容會被記錄。刪除即關閉。 |

## 建置

需要 JDK 21。

```sh
./gradlew build
```

jar 會輸出到 `build/libs/`。編譯用的依賴（EMI、Refined Storage）從 Modrinth Maven 下載，不會打包進 jar。

## 發布

- 推送到 `main`：自動更新 `latest` 預發布版。
- 推送 `v0.2.0` 這類標籤：自動建立 `v0.2.0` 版本並附上 jar。標籤去掉 `v` 後就是模組版本號。

## 授權

[MIT](LICENSE)。可以自由放進模組包、重新散布和修改，只需保留授權聲明。

內附的繁簡對照表來自 OpenCC，採用 Apache-2.0 授權，詳見 [NOTICE](NOTICE)。
