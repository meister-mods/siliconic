# Siliconic

English | [한국어](README.kr.md)

> Refine silicon, engineer cleanrooms, and build working redstone circuits inside programmable wafers.

Siliconic is a technology mod for Minecraft Forge 1.20.1 centered on semiconductor manufacturing and integrated circuit design. Its progression begins with raw materials and industrial processing, then moves into cleanroom construction, logic-gate production, and reusable wafer circuits that interact directly with redstone.

## Core Features

### Semiconductor Production

- Process quartz and carbon into crude silicon, then refine it into high-purity silicon.
- Manufacture logic gates and five wafer tiers, from SSI through ULSI.
- Use specialized powered machines for fabrication, purification, duplication, inversion, and material recovery.
- Reprocess contaminated products into useful raw materials instead of discarding them.

### Programmable Wafer Circuits

- Design circuits on a 9×9 wafer grid with configurable input and output pins.
- Route signals through redstone, copper, lead, silver, and gold traces with different attenuation characteristics.
- Build with NOT, AND, OR, XOR, buffer, signal-drop, and signal-switch gates.
- Create feedback-based sequential circuits such as latches and flip-flops.
- Embed completed lower-tier wafers inside higher-tier wafers to reuse and compose complex designs.
- Connect completed wafers to the world as redstone devices with the Wafer Guard and Redstone Clock.

### Cleanroom Engineering

- Build sealed rooms monitored by powered Cleanroom Conditioners.
- Track cleanliness as a persistent value that falls when a room is opened or polluted and recovers while the room remains sealed.
- Reach the standard cleanliness ceiling in an ordinary room, or improve it toward 100% by coating the interior surfaces.
- Synchronize multiple conditioners in the same room for shared monitoring and improved purification efficiency.
- Build working airlocks: an open doorway can temporarily merge two otherwise sealed spaces without immediately invalidating the cleanroom.
- Reduce contamination from occupants with cleanroom suits, and keep furnaces, generators, and other polluting equipment outside precision work areas.
- Contamination can affect silicon processing, while low cleanroom cleanliness increases the risk during wafer and gate fabrication.

### Power and Automation

- Generate power with coal generators and distribute it through surface-mounted cable networks.
- Share available power fairly among multiple connected machines instead of filling only the first machine in the network.
- Monitor energy, progress, inputs, outputs, and machine status through dedicated interfaces.
- Automate production with hoppers while protected workpiece and material slots remain inside their machines.
- Inspect machine processes and material costs through optional JEI integration.

## Progression Overview

1. Gather Nether quartz, carbon, silver, lead, copper, and other required resources.
2. Build a coal generator, silicon arc furnace, and purifier to establish high-purity silicon production.
3. Construct a sealed cleanroom and power its conditioner.
4. Fabricate blank wafers and assemble logic-gate components.
5. Design, test, and name a circuit at the Wafer Assembler.
6. Operate, duplicate, mirror, or embed completed wafers to create larger systems.
7. Automate the production line and recover useful materials from contaminated batches.

Exact crafting layouts and machine processes are best viewed through JEI in game.

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
3. Optionally install a compatible version of JEI for in-game recipe and process guidance.
4. Launch the game using the Forge profile.

For multiplayer, the server and every connecting player must use the same version of Siliconic. JEI is only required on clients that want its interface.

## Building from Source

With JDK 17 installed, run:

```powershell
.\gradlew.bat build
```

The built mod file will be generated in `build/libs`. Use `.\gradlew.bat runClient` to launch the development client.

## Development Status

Siliconic is in pre-1.0 development. World data formats, recipes, balance, and machine behavior may change between versions, so back up important worlds before updating.

Please use [GitHub Issues](https://github.com/meister-mods/siliconic/issues) for bug reports and suggestions.

## License

See [LICENSE](LICENSE) for the terms that apply to this project.
