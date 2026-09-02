package io.github.meistermods.siliconic.recipe;

public enum MachineKind {
  SILICON_ARC_FURNACE("silicon_arc_furnace", 3, null),
  CHLORINATION_REACTOR("chlorination_reactor", 3, new ThermalProfile(450, 25, 5, 4, 2)),
  DISTILLATION_TOWER("distillation_tower", 3, new ThermalProfile(300, 15, 4, 3, 5)),
  SIEMENS_REACTOR("siemens_reactor", 3, new ThermalProfile(1_000, 40, 8, 8, 1)),
  CHEMICAL_RECYCLER("chemical_recycler", 3, new ThermalProfile(600, 30, 6, 3, 3)),
  WAFER_FABRICATOR("wafer_fabricator", 9, null),
  GATE_FABRICATOR("gate_fabricator", 9, null);

  private final String id;
  private final int inputSlots;
  private final ThermalProfile thermalProfile;

  MachineKind(String id, int inputSlots, ThermalProfile thermalProfile) {
    this.id = id;
    this.inputSlots = inputSlots;
    this.thermalProfile = thermalProfile;
  }

  public String id() {
    return id;
  }

  public int inputSlots() {
    return inputSlots;
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
