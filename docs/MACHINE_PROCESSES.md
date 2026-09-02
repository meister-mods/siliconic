# Data-pack Machine Processes

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
- `damage`: apply the configured amount of durability damage.
- `catalyst`: require the item without consuming it.

For a shapeless process, set `shaped` to `false` and use `-1` for every input slot. For a shaped process, slots are zero-based indexes into the machine's input area. `ticks`, `energy_per_tick`, input counts, and output counts must be positive integers.

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

The `input` object accepts one item and a positive count. `outputs` must contain at least one item stack. `ticks` and `energy_per_tick` must also be positive integers.
