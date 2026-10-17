# Data-pack Machine Processes

English | [Korean](MACHINE_PROCESSES.kr.md) · [Project README](../README.md)

Siliconic machine processes can be added with `siliconic:machine_process` recipes. A data pack can override a built-in process by using the same recipe ID.

## File Placement and Reloading

Place recipes under `data/<namespace>/recipes/<path>.json` in a data pack. For example, `data/example/recipes/residue_recovery.json` defines the ID `example:residue_recovery`. To replace the built-in residue process, use `data/siliconic/recipes/chemical_recycler/distillation_residue.json`, which defines `siliconic:chemical_recycler/distillation_residue`.

Install the pack in the world's `datapacks` directory. Its root must contain `pack.mcmeta`; Minecraft 1.20.1 uses pack format 15:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "Custom Siliconic processes"
  }
}
```

Run `/reload` after changing server data. Check the server log for recipe-loading errors.

## Machine Process Example

```json
{
  "type": "siliconic:machine_process",
  "machine": "chemical_recycler",
  "shaped": true,
  "ticks": 220,
  "energy_per_tick": 35,
  "inputs": [
    {
      "slot": 0,
      "ingredient": {"item": "siliconic:distillation_residue"},
      "count": 1,
      "use": "consume"
    }
  ],
  "result": {"item": "siliconic:crude_trichlorosilane", "count": 1},
  "byproducts": []
}
```

## Supported Machines

The `machine` field uses one of these names without a namespace:

| Machine | Input slots | Additional operating condition |
| --- | --- | --- |
| `silicon_arc_furnace` | `0`–`2` | Power draw varies by phase. |
| `chlorination_reactor` | `0`–`2` | Magma heat; pressure management. |
| `distillation_tower` | `0`–`2` | Magma heat; mode and stability affect duration. |
| `siemens_reactor` | `0`–`2` | Magma heat; power draw varies by phase. |
| `chemical_recycler` | `0`–`2` | Magma heat; outputs have dedicated slot groups. |
| `wafer_fabricator` | `0`–`8` | A monitored, sealed cleanroom. |
| `gate_fabricator` | `0`–`8` | A monitored, sealed cleanroom. |

All machines also require power. Industrial input slots run from left to right. Fabricator input slots form a 3×3 grid, numbered from left to right and top to bottom. These indexes refer to the input area, excluding heat-fuel and output slots.

## Fields and Defaults

| Field | Meaning |
| --- | --- |
| `type` | Required: `siliconic:machine_process`. |
| `machine` | Required: one of the machine names above. |
| `shaped` | Whether input positions matter. Defaults to `true`. |
| `ticks` | Required positive integer: base processing duration in game ticks. |
| `energy_per_tick` | Required positive integer: base energy consumption in SE per tick. |
| `inputs` | Required non-empty array of input definitions. |
| `result` | Required primary output with an `item` ID and optional `count`, which defaults to `1`. |
| `byproducts` | Additional outputs in the same format as `result`. Defaults to an empty array. |

Each input has the following fields:

| Field | Meaning |
| --- | --- |
| `slot` | Input-area index for a shaped process, or `-1` for a shapeless process. If omitted, defaults to the entry's array index for shaped processes or `-1` for shapeless processes. |
| `ingredient` | Required ingredient definition, such as `{"item": "minecraft:quartz"}` or `{"tag": "forge:nuggets/copper"}`. |
| `count` | Positive item count, or durability damage for `damage` inputs. Defaults to `1`. |
| `use` | Input handling mode. Defaults to `consume`. |

Input `use` supports the following values:

- `consume`: remove the configured item count when the batch finishes.
- `damage`: require one damageable item and apply `count` durability damage when the batch finishes. If this exhausts its remaining durability, the item breaks; the batch still produces its outputs.
- `catalyst`: require `count` matching items without consuming them.

At the normal tick rate, 20 ticks equal one second. Phase-based power draw, distillation modes, heating, and output blockage can change a run's actual duration or energy use. These machine rules are described in the [Gameplay Guide](GAMEPLAY.md); a recipe does not change the machine's thermal profile or cleanroom requirement.

## Input Matching and Validation

For a shapeless process, set `shaped` to `false` and use `-1` for every input slot. Each item can satisfy only one requirement, even when ingredient definitions overlap. A stack can supply multiple requirements if it contains enough matching items, and one requirement can draw from several stacks.

For a shaped process, each required input must be in its specified slot, and unspecified input slots must be empty. For a shapeless process, every item in the input area must match at least one listed ingredient; matching stacks may contain more items than the batch needs.

Duplicate or out-of-range shaped slots are rejected while the recipe loads. `ticks`, `energy_per_tick`, input counts, and output counts must be integers from `1` through `2,147,483,647`. Values with a fractional part are rejected. Every primary output and byproduct must name a registered item other than `minecraft:air`, and its count cannot exceed that item's maximum stack size.

Choose `consume` and `catalyst` quantities that fit the machine's input capacity. A shaped input must fit its assigned stack.

Provide room for the primary result and every byproduct. Industrial processors and fabricators have nine output slots. The recycler reserves four for crude trichlorosilane, four for hydrogen chloride, and one for other outputs, so a custom recipe must fit those groups as well.

## Reprocessing Recipes

The reprocessor uses the `siliconic:reprocessing` recipe type.

```json
{
  "type": "siliconic:reprocessing",
  "input": {"item": "siliconic:silicon_slag", "count": 4},
  "outputs": [
    {"item": "minecraft:quartz"},
    {"item": "minecraft:charcoal"}
  ],
  "ticks": 240,
  "energy_per_tick": 40
}
```

The `input` object requires an `item` ID for a registered item other than `minecraft:air` and accepts a positive `count`, which defaults to `1`. It matches the item type, including wafers with saved circuit designs; it does not select by tag or circuit state. Matching quantities can be collected across the reprocessor's nine input slots.

`outputs` must contain at least one non-empty item stack. Each output requires an `item` ID and accepts a `count` that defaults to `1`; its count cannot exceed that item's maximum stack size. All outputs must fit in the nine output slots. `ticks` and `energy_per_tick` are required positive integers with the same units and numeric range as machine processes.
