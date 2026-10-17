# Siliconic Gameplay Guide

English | [Korean](GAMEPLAY.kr.md) · [Project README](../README.md)

This guide describes Siliconic's in-game progression and machine behavior. With optional JEI installed, you can browse crafting layouts, process inputs, output quantities, and base processing times. The values below describe the built-in recipes and default server settings; operating conditions can change actual processing times and power draw.

## Using the Field Guide

The Siliconic Field Guide provides 19 chapters, one per survival machine or utility device, in English and Korean. It is granted once per player per world when an eligible player first plays in survival. Players in other game modes retain eligibility until they enter survival. This includes players in existing worlds after installing the update.

Reconnecting and respawning do not grant extra copies. A full inventory causes the guide to drop at the player. Craft a replacement with a book and a copper nugget. See the [README](../README.md#requirements) for the required Patchouli version.

Click a machine icon on the landing page, then select its entry to read the chapter. Linked machine names in the text open related chapters. The book icon opens the **Entry Index**; type part of a chapter name there to search.

## Progression Overview

Silver and lead ores generate in Overworld biomes. Default vein placement heights are Y=-48 to 32 for silver and Y=-64 to 48 for lead. Mine them with an iron pickaxe or better, then smelt the raw materials into ingots. If you add Siliconic to an existing world, look for these ores in newly generated chunks.

1. Gather Nether quartz, carbon, silver, lead, copper, and the other required resources.
2. Mine salt dirt from riverbeds and riverbanks, then craft hydrogen chloride from salt, blaze powder, and a glass bottle.
3. Fuel a Coal Generator and connect it to the production machines with power cables.
4. Process quartz with charcoal and a carbon electrode in the Silicon Arc Furnace to obtain metallurgical-grade silicon, then recover useful material from the slag.
5. Chlorinate and distill the intermediate products to obtain purified trichlorosilane.
6. Deposit high-purity polysilicon in the Siemens Reactor and return chemical byproducts to the production loop.
7. Build a sealed cleanroom away from the polluting front-end production line and power its conditioner.
8. Fabricate wafers and logic gates, then design and finish circuits at the Wafer Assembler.
9. Operate, duplicate, mirror, embed, and automate completed wafer circuits.

## Semiconductor Production

The front-end production line converts common resources into high-purity silicon suitable for precision fabrication. Several machines have operating rules beyond their visible recipe inputs. Below, TCS means trichlorosilane and HCl means hydrogen chloride.

| Machine | Operation | Automation and failure behavior |
| --- | --- | --- |
| Silicon Arc Furnace | Uses a carbon electrode with limited durability. Batches advance through charging, peak-power arc, reduction melt, and tapping phases. Power draw changes by phase, and a badly worn electrode produces extra slag. | Comparator output rises with batch progress. |
| Chlorination Reactor | Uses stored magma heat to reach a target temperature of 450 while pressure accumulates during the reaction. | A blocked output does not stop the batch. At the pressure limit, the reactor vents and discards the batch. |
| Trichlorosilane Distillation Tower | Consecutive hot batches increase column stability and shorten high-purity-mode runs. In the built-in recipe, throughput mode produces three purified units and one residue from four crude units in half the base recipe duration. | Distillation residue can be recovered as one unit of crude trichlorosilane in the recycler. |
| Siemens Deposition Reactor | Converts purified trichlorosilane into high-purity polysilicon using a quartz deposition filament that is retained. It progresses through preheating, nucleation, rod growth, and controlled cooling. | The interface can abort an unfinished batch, consuming its inputs and returning a partial polysilicon rod. The rod can be processed with more purified trichlorosilane in a recovery recipe. |
| Chlorosilane Recycler | Converts silicon tetrachloride with hydrogen, or distillation residue, back into crude trichlorosilane. | Blue output slots accept crude TCS, yellow slots accept HCl, and purple slots accept other byproducts. When no process matches, signal 10 indicates silicon tetrachloride without hydrogen. During a batch, signals 1–14 show progress; signal 15 indicates blocked or pending output. |

### Heat and Batch Handling

Hot-process machines consume stored magma heat while warming up and while replacing heat lost during operation. Temperatures and heat values use game units. They do not represent degrees Celsius or kelvin.

| Fuel | Heat supplied |
| --- | ---: |
| Magma Cream | 2,000 |
| Magma Block | 8,000 |
| Lava Bucket | 16,000 |

Place fuel in the machine's heat slot. A lava bucket leaves an empty bucket; remove it before supplying the next fuel.

Industrial process input slots lock after a batch begins and remain locked until the batch completes or is recovered. Machine interfaces report conditions such as insufficient energy, temperature preparation, output blockage, pressure, and recovery state.

## Cleanroom Engineering

Precision fabrication equipment requires a sealed space monitored by a Cleanroom Conditioner. Place the conditioner inside the room. Supply power to both the conditioner and the machines; the conditioner uses power to restore cleanliness.

- Cleanliness changes over time, falls when a room is opened or polluted, and recovers while the room stays sealed and at least one linked conditioner has power. Without power, monitoring continues but cleanliness recovery stops.
- With one conditioner, an uncoated room has a cleanliness ceiling of 75%. Coated interior surfaces raise the ceiling toward 100%.
- Multiple conditioners in one room share monitoring and improve purification. Each additional conditioner raises the ceiling by 10% of the remaining gap to 100%, rounded up to a whole percentage point.
- Opening an airlock's inner door can join two sealed compartments into one monitored room. Keep the outer door closed and the combined space within the scan limits.
- Wear the Cleanroom Hood, Cleanroom Suit Top, Cleanroom Suit Bottom, and Cleanroom Boots together to prevent contamination from your character. Partial sets do not provide this protection.
- Keep unprotected mobs and loose items out of the room; they also count as occupant pollution sources.
- Furnaces, generators, combustion equipment, and compatible automated machines with material input, consumption, and output slots are treated as equipment pollution sources.
- Shovel-mineable dirt, sand, gravel, and similar materials are counted separately as block pollution sources.
- Crafting tables, manual workbenches, simple chests, barrels, and hoppers are excluded from automatic equipment-pollution detection.

The Wafer Assembler, wafer and gate fabricators, Wafer Inverter, and Wafer Duplicator are sealed post-process machines. They do not lower cleanliness, and their faces bordering the room count as coated surfaces. They still require a sealed space monitored by a conditioner to operate. Wafer Guards and Reprocessors need power but can operate outside a cleanroom; they are not classified as cleanroom post-process equipment.

Data packs can adjust detection with the `siliconic:cleanroom_pollution_sources`, `siliconic:cleanroom_pollution_exemptions`, and `siliconic:cleanroom_post_process_equipment` block tags.

By default, a scan visits up to 4,096 traversable positions within 32 blocks of the conditioner on each axis. Keep the room and its boundary within loaded chunks. Server settings can change these limits.

## Wafer Fabrication and Circuit Design

Siliconic provides five wafer tiers: SSI, MSI, LSI, VLSI, and ULSI. Every tier uses a 9×9 grid. Higher tiers can embed completed lower-tier wafers to reuse their circuit designs.

Upgrade recipes consume a previous-tier wafer and produce a new blank wafer. They do not transfer a saved circuit, so prepare blank wafers for upgrades and keep completed designs separately.

Poor cleanliness can contaminate fabrication results, which can be sent to the Reprocessor for material recovery. Changing inputs in wafer and gate fabricators resets progress; inputs lock only while a completed batch waits for output space.

### Editing and Running a Wafer

1. Place the Wafer Assembler in a monitored, sealed cleanroom and supply power for circuit operation. Check cleanliness before editing.
2. Hold a wafer and right-click the block to mount it. Right-click again to open the editor.
3. Pick up material from the inventory and left-click an empty cell to place it. Left-click an occupied cell with an empty cursor to return its material to the inventory.
4. Right-click gates to rotate them and wires to cycle connection modes. Hold redstone and right-click a Signal Drop gate to change its drop amount.
5. Click the edge pins to cycle input, output, and disabled modes. The directions follow the world compass: north and west default to inputs; east and south default to outputs.
6. Optionally enter a name, then press **Finish**. Further edits mark the wafer unfinished again. Sneak-right-click the block with an empty hand to retrieve it.
7. Mount the completed wafer in a powered Wafer Guard and connect redstone to the configured pins. The guard can operate outside a cleanroom.

Undo and redo restore settings such as rotations and pin modes within the current editor session. Placing or reclaiming components or closing the editor clears this history. Changes to the wafer from another player or an automated device also clear it.

Poor cleanliness can contaminate cells during editing; contaminated cells cannot be edited.

### Trace Materials and Shapes

On a continuous trace of the same material, signal strength drops by 1 at the following intervals:

| Material | Attenuation interval |
| --- | ---: |
| Redstone dust | 1 cell |
| Copper nugget | 2 cells |
| Lead nugget | 3 cells |
| Silver nugget | 4 cells |
| Gold nugget | 6 cells |

A direct connection between different trace materials halves the signal strength, rounding down. A **+ junction** joins all four directions, while an **insulated crossover** keeps horizontal and vertical signals separate. Straight and corner shapes limit connections to the selected directions.

### Gate Behavior

Gate arrows mark their output direction. For the table below, the rear is opposite the arrow, and the sides are perpendicular to it. Signals greater than zero count as on.

| Component | Inputs | Output |
| --- | --- | --- |
| NOT Gate | Rear | 15 when the input is off; otherwise 0. |
| Signal Buffer | Rear | 15 when the input is on; otherwise 0. |
| AND Gate | Both sides | 15 when both inputs are on; otherwise 0. |
| OR Gate | Both sides | 15 when at least one input is on; otherwise 0. |
| XOR Gate | Both sides | 15 when exactly one input is on; otherwise 0. |
| Signal Drop Gate | Rear | Input minus the configured drop amount, with a minimum of 0. |
| Signal Switch Gate | Rear signal; side controls | Passes the rear signal at its original strength when either side control is on; otherwise 0. |

Feedback paths support sequential circuits such as latches and flip-flops. Use a Redstone Clock when a pulse input is needed.

### Reusing, Copying, and Mirroring

Embed a completed lower-tier wafer inside a higher-tier wafer to reuse its design. Embedding has a circuit-complexity limit. If the combined design exceeds it, the assembler rejects the insertion and keeps the material on the cursor. Reduce the combined design before trying again.

- The Wafer Duplicator retains the completed source and consumes a blank wafer of the same tier plus the required circuit components. It has 18 material slots and nine output slots. Each copy costs 10,000 SE multiplied by the tier number, from 10,000 SE for SSI to 50,000 SE for ULSI. Nested wafer materials must match the required design.
- The Wafer Inverter transforms the input wafer itself, including nested circuits and east-west pin settings. It requires no additional wafer or circuit components. Processing takes 100 ticks and consumes 20 SE multiplied by the tier number each tick.

Both machines require a monitored, sealed cleanroom, and poor cleanliness can contaminate their output.

## Power and Automation

- Coal Generators burn furnace fuels to generate 40 SE per tick by default.
- Surface-mounted power cables connect generators, buffers, and consumers without forcing nearby chunks to load.
- Available energy is shared fairly across connected receivers.
- Energy Buffers store 200,000 SE by default and distribute up to 800 SE per tick in total, capped at 200 SE per receiver per buffer. Use multiple powered sources for higher demand. Read the charge by interaction or comparator output.
- Hoppers automate ordinary machine input and output while protected workpiece slots remain inside their machines.
- Logistics ports support input, output, forced extraction, allow or exclude filters, and priorities from `-2` through `+2`. Enable **Output** at the source and **Input** at the destination.
- Filters use one physical item and compare item type, without comparing custom names or circuit designs. Empty filters impose no item restriction.
- Forced mode can extract from additional slots exposed by supported machines, including unfinished wafers in assemblers and guards. Insertion still follows normal rules, and industrial batch and pending-output input locks still apply.
- The Logistics Controller needs no power. Search by name or coordinates to jump to a matching device's page.
- The Redstone Clock needs no power and alternates 18 ticks off with two ticks at signal strength 15. At the normal tick rate, it produces one pulse per second.

## Material Recovery

The Reprocessor recovers useful resources from silicon slag, contaminated fabrication results, standard logic gates, and blank, unfinished, or completed wafers. Higher wafer tiers return progressively more high-purity polysilicon.

Reprocessing consumes the input and does not preserve its circuit design. Set aside any wafers whose designs you intend to keep.

Recovered chemicals and materials can be routed back into the production line to reduce waste and support a closed manufacturing loop.
