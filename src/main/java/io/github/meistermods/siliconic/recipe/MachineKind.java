package io.github.meistermods.siliconic.recipe;

public enum MachineKind {
  SILICON_ARC_FURNACE("silicon_arc_furnace", false),
  CHLORINATION_REACTOR("chlorination_reactor", true),
  DISTILLATION_TOWER("distillation_tower", true),
  SIEMENS_REACTOR("siemens_reactor", true),
  CHEMICAL_RECYCLER("chemical_recycler", true),
  WAFER_FABRICATOR("wafer_fabricator", false),
  GATE_ASSEMBLER("gate_assembler", false);

  private final String id;
  private final boolean requiresHeat;

  MachineKind(String id, boolean requiresHeat) {
    this.id = id;
    this.requiresHeat = requiresHeat;
  }

  public String id() {
    return id;
  }

  public boolean requiresHeat() {
    return requiresHeat;
  }
}
