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
| Minecraft Forge | 47.x (developed against 47.4.10) |
| Java | 17 |
| Just Enough Items | 15.20 or newer, optional |

## Installation

1. Install Minecraft Forge for Minecraft 1.20.1.
2. Place the runnable Siliconic JAR in the Minecraft `mods` folder.
3. Optionally install a compatible JEI version for in-game recipe and process guidance.
4. Launch Minecraft with the Forge profile.

The server and all connecting players must use the same Siliconic version. JEI is only required on clients that use its interface.

## Documentation

- [Gameplay Guide](docs/GAMEPLAY.md) · [한국어](docs/GAMEPLAY.kr.md)
- [Data-pack Machine Process Format](docs/MACHINE_PROCESSES.md)
- [Changelog](CHANGELOG.md)

## Development

Build the mod with JDK 17:

```powershell
.\gradlew.bat build
```

The runnable mod is generated as `build/libs/siliconic-1.20.1-*.jar`. Files ending in `-sources.jar` are development artifacts and must not be installed in the `mods` folder.

Common development tasks:

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat runClient` | Launch the development client. |
| `.\gradlew.bat runServer` | Launch a local development server. |
| `.\gradlew.bat runGameTestServer` | Run automated Forge GameTests. |
| `.\gradlew.bat runData` | Generate configured data resources. |

Source code is under `src/main/java`, bundled assets and data packs are under `src/main/resources`, and generated resources are under `src/generated/resources`. Machine recipes can be extended or overridden with data packs; see the machine-process format linked above.

## Project Status and Contributions

Siliconic is in pre-1.0 development. World data formats, recipes, balance, and machine behavior may change between versions, so back up important worlds before updating.

Bug reports and suggestions are welcome through [GitHub Issues](https://github.com/meister-mods/siliconic/issues). Contributions should keep gameplay behavior documented, include regression coverage where practical, and pass both `build` and `runGameTestServer`.

## License

See [LICENSE](LICENSE) for the terms that apply to this project.
