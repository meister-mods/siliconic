# Siliconic

English | [Korean](README.kr.md)

> Refine silicon, engineer cleanrooms, and build working redstone circuits inside programmable wafers.

Siliconic is a technology mod for Minecraft Forge 1.20.1 focused on semiconductor manufacturing, cleanroom engineering, factory automation, and programmable wafer circuits.

## Overview

Siliconic provides a connected technology progression built around four systems:

- Semiconductor production from raw quartz to high-purity silicon, logic gates, and tiered wafers.
- Programmable 9×9 wafer circuits that interact with Minecraft redstone.
- Cleanrooms whose construction, equipment, and contamination affect precision manufacturing.
- Power generation, energy distribution, logistics control, and material recovery.

Detailed progression and machine behavior are documented in the [Gameplay Guide](docs/GAMEPLAY.md), also available in [Korean](docs/GAMEPLAY.kr.md). Exact recipes are best viewed through JEI in game.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Minecraft Forge | 47.3.19 or newer (developed against 47.4.10) |
| Java | 17 |
| Patchouli | 1.20.1-85-FORGE or a newer release for Forge 1.20.1; required |
| Just Enough Items | 15.20 or newer for Forge 1.20.1; optional |

## Installation

1. Install Minecraft Forge for Minecraft 1.20.1.
2. Place the runnable Siliconic JAR and a compatible [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) JAR in the Minecraft `mods` folder.
3. Optionally install a compatible JEI version for in-game recipe and process guidance.
4. Launch Minecraft with the Forge profile.

The server and all connecting players must use the same Siliconic version and install Patchouli. JEI is only required on clients that use its interface.

## In-Game Field Guide

The Siliconic Field Guide contains 19 chapters, one for each survival machine or utility device, with English and Korean text and crafting pages. A player who has not received it in the current world gets one on their first survival tick with this version installed. This also applies to existing worlds. Other game modes preserve eligibility, so a player who later enters survival can still receive it.

Reconnecting or respawning does not grant another copy. If the inventory is full, the guide drops at the player. A replacement can be crafted with a book and a copper nugget.

## Documentation

- [Gameplay Guide](docs/GAMEPLAY.md) · [한국어](docs/GAMEPLAY.kr.md)
- [Data-pack Machine Process Format](docs/MACHINE_PROCESSES.md) · [한국어](docs/MACHINE_PROCESSES.kr.md)
- [Changelog](CHANGELOG.md)

## Development

Build the mod with JDK 17. On Linux or macOS:

```sh
bash gradlew build
```

On Windows, use PowerShell:

```powershell
.\gradlew.bat build
```

The runnable mod is generated as `build/libs/siliconic-1.20.1-*.jar`. Files ending in `-sources.jar` are development artifacts and must not be installed in the `mods` folder.

Use the same wrapper command with these development tasks:

| Task | Purpose |
| --- | --- |
| `runClient` | Launch the development client. |
| `runServer` | Launch a local development server. |
| `runGameTestServer` | Run automated Forge GameTests. |
| `runData` | Generate configured data resources. |

Source code is under `src/main/java`, bundled assets and data packs are under `src/main/resources`, and generated resources are under `src/generated/resources`. Machine recipes can be extended or overridden with data packs; see the machine-process format linked above.

The [field guide resources](src/main/resources/assets/siliconic/patchouli_books/field_guide) contain English and Korean category and entry files. Keep one category and one entry per machine or utility chapter, with matching content in both languages. The book definition, one-time grant advancement, loot reward, and replacement recipe are under `src/main/resources/data/siliconic`.

The book ID and the grant advancement ID are both `siliconic:field_guide`. Keep them stable across updates to preserve existing guide items and per-player completion records.

## Project Status and Contributions

Siliconic is in pre-1.0 development. World data formats, recipes, balance, and machine behavior may change between versions, so back up important worlds before updating.

Bug reports and suggestions are welcome through [GitHub Issues](https://github.com/meister-mods/siliconic/issues). Contributions should keep gameplay behavior documented, include regression coverage where practical, and pass both `build` and `runGameTestServer`.

## License

See [LICENSE](LICENSE) for the terms that apply to this project.
