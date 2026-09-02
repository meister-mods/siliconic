# Siliconic Gameplay Guide

English | [Korean](GAMEPLAY.kr.md) · [Project README](../README.md)

This guide describes Siliconic's in-game progression and machine behavior. Exact crafting layouts, process inputs, output quantities, and processing times are best viewed through JEI in game.

## Progression Overview

1. Gather Nether quartz, carbon, silver, lead, copper, and the other required resources.
2. Mine salt dirt from riverbeds and riverbanks, then combine its salt with blaze powder to prepare hydrogen chloride.
3. Reduce quartz and charcoal into metallurgical-grade silicon in the Silicon Arc Furnace and recover useful material from the slag.
4. Chlorinate and distill the intermediate products to obtain purified trichlorosilane.
5. Deposit high-purity polysilicon in the Siemens Reactor and return chemical byproducts to the production loop.
6. Build a sealed cleanroom away from the polluting front-end production line and power its conditioner.
7. Fabricate wafers and logic gates, then design and finish circuits at the Wafer Assembler.
8. Operate, duplicate, mirror, embed, and automate completed wafer circuits.

## Semiconductor Production

The front-end production line converts common resources into high-purity silicon suitable for precision fabrication. Several machines have operating rules beyond their visible recipe inputs.

| Machine | Operation | Automation and failure behavior |
| --- | --- | --- |
| Silicon Arc Furnace | Uses a durable carbon electrode and advances through charging, peak-power arc, reduction melt, and tapping phases. Power draw changes by phase, and a badly worn electrode produces extra slag. | Comparator output rises with batch progress. |
| Chlorination Reactor | Uses stored magma heat to reach a target temperature of 450 while pressure accumulates during the reaction. | A blocked output does not stop the batch. Reaching the pressure limit emergency-vents and loses the batch. |
| Trichlorosilane Distillation Tower | Consecutive hot batches increase column stability and shorten high-purity runs. High-throughput mode refines three of four units twice as fast and separates the remaining unit as residue. | Distillation residue can be recovered as one unit of crude trichlorosilane in the recycler. |
| Siemens Deposition Reactor | Uses a quartz deposition filament as a seed and progresses through preheating, nucleation, rod growth, and controlled cooling. | An interrupted batch can be aborted from the interface to recover a partial rod for a later recovery process. |
| Chlorosilane Recycler | Converts silicon tetrachloride with hydrogen, or distillation residue, back into crude trichlorosilane. | Blue output slots accept crude TCS, yellow slots accept HCl, and purple slots accept other byproducts. Comparator signal 10 means hydrogen is missing; 15 means an output is blocked. |

### Heat and Batch Handling

Hot-process machines consume stored magma heat while warming up and while replacing heat lost during operation.

| Fuel | Heat supplied |
| --- | ---: |
| Magma Cream | 2,000 |
| Magma Block | 8,000 |
| Lava Bucket | 16,000 |

Process input slots lock after a batch begins and remain locked until the batch completes or is recovered. Machine interfaces report conditions such as insufficient energy, temperature preparation, output blockage, pressure, and recovery state.

## Cleanroom Engineering

Precision fabrication equipment operates inside a sealed space monitored by a powered Cleanroom Conditioner.

- Cleanliness persists over time, falls when a room is opened or polluted, and recovers while the room stays sealed.
- An ordinary room reaches the standard cleanliness ceiling. Coated interior surfaces raise the ceiling toward 100%.
- Multiple conditioners in one room share monitoring and improve purification. Each additional conditioner reduces the remaining gap between the current cleanliness limit and 100% by 10%.
- An open doorway can temporarily merge two otherwise sealed spaces, allowing functional airlock layouts without immediately invalidating the cleanroom.
- Cleanroom suits reduce contamination caused by occupants.
- Furnaces, generators, combustion equipment, and compatible automated machines with material input, consumption, and output slots are treated as equipment pollution sources.
- Shovel-mineable dirt, sand, gravel, and similar materials are counted separately as block pollution sources.
- Crafting tables, manual workbenches, simple chests, barrels, and hoppers are excluded from automatic equipment-pollution detection.

The Wafer Assembler, wafer and gate fabricators, Wafer Inverter, and Wafer Duplicator are sealed post-process machines. They do not lower cleanliness, and their contacting faces contribute like coated surfaces. They still require a sealed conditioner space to operate. Wafer Guards and Reprocessors are not classified as cleanroom post-process equipment.

Data packs can adjust detection with the `cleanroom_pollution_sources`, `cleanroom_pollution_exemptions`, and `cleanroom_post_process_equipment` block tags.

## Wafer Fabrication and Circuit Design

Siliconic provides five wafer tiers from SSI through ULSI. Higher tiers support progression toward more capable and reusable circuit designs.

- Design circuits on a 9×9 grid with configurable input and output pins.
- Route signals through redstone, copper, lead, silver, and gold traces with different attenuation characteristics.
- Combine NOT, AND, OR, XOR, buffer, signal-drop, and signal-switch gates.
- Build feedback-based sequential circuits such as latches and flip-flops.
- Embed completed lower-tier wafers inside higher-tier wafers to reuse complex designs.
- Use the Wafer Duplicator and Wafer Inverter to reproduce or mirror completed work.
- Connect a completed wafer to the world as a redstone device with the Wafer Guard and Redstone Clock.

Precision fabrication can produce contaminated results when cleanroom conditions are inadequate. These products can be sent to material recovery instead of being discarded.

## Power and Automation

- Coal Generators produce energy for machines and cable networks.
- Surface-mounted power cables connect generators, buffers, and consumers without forcing nearby chunks to load.
- Available energy is shared fairly across connected receivers.
- Energy Buffers store and redistribute energy; their charge can be read by interaction or comparator output.
- Hoppers automate ordinary machine input and output while protected workpiece slots remain inside their machines.
- Logistics ports support input, output, forced access, allow or deny filters, and priorities from `-2` through `+2`.
- The Logistics Controller can search connected devices by name or coordinate.

## Material Recovery

The Reprocessor recovers useful resources from silicon slag, contaminated fabrication results, standard logic gates, and blank, unfinished, or completed wafers. Higher wafer tiers return progressively more high-purity silicon.

Recovered chemicals and materials can be routed back into the production line to reduce waste and support a closed manufacturing loop.
