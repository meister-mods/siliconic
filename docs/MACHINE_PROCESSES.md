# Data-pack Machine Processes

English | [Korean](MACHINE_PROCESSES.kr.md) · [Project README](../README.md)

Siliconic machine processes can be added with `siliconic:machine_process` recipes. A data pack can override a built-in process by using the same recipe ID.

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

The `machine` field must be one of `silicon_arc_furnace`, `chlorination_reactor`, `distillation_tower`, `siemens_reactor`, `chemical_recycler`, `wafer_fabricator`, or `gate_fabricator`.

Input `use` supports the following values:

- `consume`: remove the configured item count when the batch finishes.
- `damage`: require one damageable item and apply `count` durability damage when the batch finishes.
- `catalyst`: require the item without consuming it.

For a shapeless process, set `shaped` to `false` and use `-1` for every input slot. The matcher assigns overlapping ingredients to distinct available items, so one stack cannot satisfy multiple requirements at once.

For a shaped process, slots are zero-based indexes into the machine's input area. Industrial processors use slots `0` through `2`; wafer and gate fabricators use slots `0` through `8`. Duplicate or out-of-range shaped slots are rejected while the data pack loads. `ticks`, `energy_per_tick`, input counts, and output counts must be positive integers. Output counts cannot exceed the output item's maximum stack size.

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

The `input` object accepts one item and a positive count. `outputs` must contain at least one non-empty item stack, and each output count cannot exceed that item's maximum stack size. `ticks` and `energy_per_tick` must also be positive integers.
