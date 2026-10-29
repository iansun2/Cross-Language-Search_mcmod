# Cross-Language-Search

**English** | [繁體中文](README.zh-TW.md) | [简体中文](README.zh-CN.md)

A client-side NeoForge mod for Minecraft 1.21.1 that makes in-game search work across languages:

- Search **Chinese** item names and tooltips **in English** (`iron` finds 铁锭 / 鐵錠).
- Search in **Traditional or Simplified Chinese** interchangeably (`鐵` finds 铁锭).
- Search **English** text in Chinese (`鐵` finds an item displayed as `Iron`).
- Full-width / half-width characters are treated the same (`ｑｕａｎｔａ` = `quanta`).

> [!WARNING]
> **This mod was developed with heavy use of AI assistance.** The code was written largely by an AI model under human direction and was tested only in the environment listed below. It is provided as-is with **no guarantee that it is free of bugs**. Please report problems on the [issue tracker](https://github.com/iansun2/Cross-Language-Search_mcmod/issues).

## Supported mods

Every integration is **optional**. The mod detects what is installed and only enables the matching features; if none of the supported mods are present, it does nothing.

| Mod | What changes |
|---|---|
| [EMI](https://modrinth.com/mod/emi) | Item list search: names, tooltips (`$`), mod names (`@`), aliases. Also detects items whose tooltips change at runtime (e.g. "not yet eaten") and evaluates them live. |
| [Refined Storage](https://modrinth.com/mod/refined-storage) | Grid search, including `$` tooltip search. |
| [Apothic Enchanting](https://modrinth.com/mod/apothic-enchanting) | Enchantment Library filter box. |
| Reverie Foundry (mod) | Catalog search for materials and traits. |
| [Just Enough Characters](https://modrinth.com/mod/just-enough-characters) | Keeps pinyin search working, and fixes a thread-safety crash in its search index that made EMI searches return nothing. |

It also works alongside **Untranslated Items** (which shows item names in English): you can still find those items by their Chinese names.

## Tested environment

Developed and tested only in the **Reverie Foundry** modpack:

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
| EMI | 1.1.24 |
| Refined Storage | 2.0.9 |
| Apothic Enchanting | 1.6.2 |
| Reverie Foundry (mod) | 2.3.5 |
| Just Enough Characters | 4.5.29 |
| Untranslated Items | 1.19 |

Other versions may work, but the integrations hook into internal code of these mods. If a target mod changes, the affected feature is silently skipped instead of crashing the game.

## Installation

1. Download the jar from [Releases](https://github.com/iansun2/Cross-Language-Search_mcmod/releases).
   - Versioned releases (`v*`) are the recommended builds.
   - The `latest` pre-release is rebuilt automatically from `main` and may be unstable.
2. Put it in the `mods` folder. It is client-only; servers do not need it.

## How it works

- **Text folding**: both indexed text and your query go through the same normalization: formatting codes removed, NFKC (full-width → half-width), lowercase, Traditional → Simplified (character table from [OpenCC](https://github.com/BYVoid/OpenCC)).
- **English index**: the mod loads the `en_us` language table separately (without changing your game language) and re-renders each name and tooltip line in English for the search index.
- **Reverse lookup**: where only already-translated strings are available (Refined Storage, the catalog), a two-way Chinese ↔ English table built from the language files is used, including format templates such as ` - Quanta: %s%%`.
- **Dynamic tooltips** (EMI): tooltips are re-checked in the background (about 1 ms per tick). Items whose tooltips change on their own are stored in `config/crosslanguagesearch-dynamic.txt` and matched live on every search. Changes caused by holding Shift / Ctrl / Alt, toggling advanced tooltips, or affecting many items at once are ignored.

## Files

| Path | Purpose |
|---|---|
| `config/crosslanguagesearch-dynamic.txt` | Detected dynamic-tooltip items. Edit freely; delete to re-detect. |
| `config/crosslanguagesearch-debug.txt` | Create this file to log search diagnostics. Each non-comment line is a probe word; index entries containing it are logged during indexing. Delete to turn off. |

## Building

Requires JDK 21.

```sh
./gradlew build
```

The jar is written to `build/libs/`. Compile-only dependencies (EMI, Refined Storage) are fetched from Modrinth Maven and are not bundled.

## Releasing

- Push to `main` → the `latest` pre-release is updated automatically.
- Push a tag such as `v0.2.0` → a release `v0.2.0` is created with the jar attached. The tag (without `v`) becomes the mod version.

## License

[MIT](LICENSE). You may include this mod in modpacks, redistribute and modify it freely; keep the license notice.

The bundled Traditional-to-Simplified table is from OpenCC and licensed under Apache-2.0; see [NOTICE](NOTICE).
