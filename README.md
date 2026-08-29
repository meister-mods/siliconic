# Siliconic

English | [한국어](README.kr.md) 

> Refine silicon, design wafer circuits, and expand Minecraft's redstone system with a new technology progression.

Siliconic introduces a technology path that begins with resource gathering and continues through semiconductor production, logic gate assembly, and integrated circuit design. Build powered machines, create your own wafer circuits, and reuse completed designs inside higher-integration wafers to develop increasingly complex automation systems.

## Features

- Semiconductor production progressing from crude silicon to high-purity silicon
- Basic logic components including NOT, AND, OR, XOR, and signal buffers
- A tiered wafer system ranging from SSI to ULSI
- Circuit design using traces, input and output pins, and signal direction
- Hierarchical integration that allows completed lower-tier wafers to be reused inside higher-tier wafers
- Utility machines for duplicating, mirroring, and operating wafer circuits
- An energy system with coal-powered generation and machine-specific power consumption
- Cleanroom conditioning with seal monitoring, persistent cleanliness, and coated-wall bonuses
- Support for both singleplayer and multiplayer
- Optional JEI integration for viewing machine recipes

## Progression Overview

1. Gather Nether quartz, metals, and other required resources.
2. Build a generator and silicon-processing machines.
3. Refine silicon, then manufacture wafers and logic gates.
4. Design and name custom logic circuits at the Wafer Station.
5. Connect completed wafers to redstone devices or integrate them into higher-tier wafers.
6. Duplicate and expand your designs to build more advanced automation systems.

For exact material layouts and machine recipes, using the in-game JEI interface is recommended.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Minecraft Forge | 47.x (developed against 47.4.10) |
| Java | 17 |
| Just Enough Items | 15.20 or newer, optional |

## Installation

1. Install Minecraft Forge for Minecraft 1.20.1.
2. Place the Siliconic mod file in your Minecraft `mods` folder.
3. Install a compatible version of JEI as well if you want in-game recipe guidance.
4. Launch the game using the Forge profile.

For multiplayer, the server and every connecting player must use the same version of Siliconic. JEI only needs to be installed on the client.

## Building from Source

With JDK 17 installed, run:

```powershell
.\gradlew.bat build
```

The built mod file will be generated in `build/libs`. Use `.\gradlew.bat runClient` to launch the development client.

## Development Status

Siliconic is currently in an early stage of development. More wafers, machines, and circuit features are planned, and recipes or balance may change as development continues. Backing up important worlds before updating is recommended.

Please use [GitHub Issues](https://github.com/meister-mods/siliconic/issues) for bug reports and suggestions.

## License

See [LICENSE](LICENSE) for the terms that apply to this project.
