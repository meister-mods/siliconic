# Siliconic

English | [한국어](README.kr.md)

> Refine silicon, engineer cleanrooms, and build working redstone circuits inside programmable wafers.

Siliconic is a technology mod for Minecraft Forge 1.20.1 centered on semiconductor manufacturing and integrated circuit design. Its progression begins with raw materials and industrial processing, then moves into cleanroom construction, logic-gate production, and reusable wafer circuits that interact directly with redstone.

## Core Features

### Semiconductor Production

- Reduce quartz into metallurgical-grade silicon, then chlorinate, distill, and deposit it into high-purity polysilicon through a Siemens-style process.
- Fill each hot-process machine's magma tank with magma cream, magma blocks, or lava to supply process heat.
- Recycle silicon tetrachloride, hydrogen, and hydrogen chloride through a closed chemical loop.
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
- Autonomous equipment with distinct material input, consumption, and output slots is detected as a cleanroom pollution source, including compatible machines from other mods. Existing furnace, combustion, and generator sources remain included.
- The wafer assembler and fabricator, gate fabricator, wafer inverter, and wafer duplicator are sealed cleanroom-compatible post-process equipment, so they do not lower cleanliness. These machines still operate only inside a sealed conditioner space. Wafer guards and reprocessors are not classified as post-process equipment.
- Crafting and manual workbenches and simple chests, barrels, and hoppers are excluded. Data packs can override detection with the `cleanroom_pollution_sources`, `cleanroom_pollution_exemptions`, and `cleanroom_post_process_equipment` block tags.

### Power and Automation

- Generate power with coal generators and distribute it through surface-mounted cable networks.
- Share available power fairly among multiple connected machines instead of filling only the first machine in the network.
- Monitor energy, progress, inputs, outputs, and machine status through dedicated interfaces.
- Automate production with hoppers while protected workpiece and material slots remain inside their machines.
- Inspect machine processes and material costs through optional JEI integration.

## Progression Overview

1. Gather Nether quartz, carbon, silver, lead, copper, and other required resources.
2. Mine salt dirt from riverbeds and riverbanks, then combine the salt with blaze powder to prepare hydrogen chloride.
3. Reduce quartz and charcoal into metallurgical-grade silicon in the arc furnace and reprocess the slag.
4. Use the chlorination reactor and distillation tower to produce purified trichlorosilane.
5. Deposit high-purity polysilicon in the Siemens reactor and recycle silicon tetrachloride, hydrogen, and hydrogen chloride.
6. Construct a sealed cleanroom outside the polluting front-end line and power its conditioner.
7. Fabricate wafers and gates, then design and finish circuits at the Wafer Assembler.
8. Operate, duplicate, mirror, and embed completed wafers while automating the full production line.

Exact crafting layouts and machine processes are best viewed through JEI in game.

### Front-end machine differences

| Machine | Distinct operation | Automation signal / failure |
| --- | --- | --- |
| Silicon Arc Furnace | Uses a durable carbon electrode and moves through charging, peak-power arc, reduction melt, and tapping with different power draw. A badly worn electrode creates extra slag. | Comparator output rises with batch progress. |
| Chlorination Reactor | Magma heats the vessel to a target of 450 while pressure accumulates during reaction. | A blocked output does not stop the batch; reaching the pressure limit emergency-vents and loses it. |
| Trichlorosilane Distillation Tower | Consecutive hot batches build column stability and shorten high-purity runs. High-throughput mode refines three of four units twice as fast and separates the last unit as residue. | The recycler recovers one crude TCS from that residue. |
| Siemens Deposition Reactor | Uses a quartz deposition filament as a seed, then runs preheat, nucleation, rod growth, and controlled cooling. | The UI can abort an interrupted batch and recover a partial rod for a later recovery run. |
| Chlorosilane Recycler | Converts silicon tetrachloride plus hydrogen, or distillation residue, back into crude trichlorosilane. | Blue slots are dedicated to crude TCS, yellow to HCl, and purple to other byproducts. Comparator 10 means missing hydrogen; 15 means blocked output. |

Hot machines spend stored magma heat to reach temperature and replace heat lost during operation. Magma cream supplies 2,000 heat, a magma block 8,000, and a lava bucket 16,000. Process input slots lock after a batch starts and remain locked until completion or recovery.

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
