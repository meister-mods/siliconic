package io.github.meistermods.siliconic.recipe;

public enum MachineKind {
  SILICON_ARC_FURNACE("silicon_arc_furnace", null),
  CHLORINATION_REACTOR("chlorination_reactor", new ThermalProfile(450, 25, 5, 4, 2)),
  DISTILLATION_TOWER("distillation_tower", new ThermalProfile(300, 15, 4, 3, 5)),
  SIEMENS_REACTOR("siemens_reactor", new ThermalProfile(1_000, 40, 8, 8, 1)),
  CHEMICAL_RECYCLER("chemical_recycler", new ThermalProfile(600, 30, 6, 3, 3)),
  WAFER_FABRICATOR("wafer_fabricator", null),
  GATE_ASSEMBLER("gate_assembler", null);

  private final String id;
  private final ThermalProfile thermalProfile;

  MachineKind(String id, ThermalProfile thermalProfile) {
    this.id = id;
    this.thermalProfile = thermalProfile;
  }

  public String id() {
    return id;
  }

  public boolean requiresHeat() {
    return thermalProfile != null;
  }

  public ThermalProfile thermalProfile() {
    return thermalProfile;
  }

  /**
   * Temperatures are intentionally game units: they communicate behavior, not real-world kelvin.
   */
  public record ThermalProfile(
      int targetTemperature,
      int tolerance,
      int heatingRate,
      int magmaPerHeatingTick,
      int passiveCoolingInterval) {}
}
