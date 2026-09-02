package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.ConductorMode;

/** Pure circuit rules kept independent from block entities, networking, inventories, and NBT. */
public final class WaferCircuitLogic {
  public record SignalPulse(int strength, CellType material, int remaining) {
    public static final SignalPulse NONE = new SignalPulse(0, CellType.EMPTY, 0);

    public SignalPulse {
      strength = Math.max(0, Math.min(15, strength));
      remaining = Math.max(0, remaining);
    }
  }

  public static int evaluateGate(
      CellType type,
      int primary,
      int secondary,
      int leftControl,
      int rightControl,
      int dropAmount) {
    return switch (type) {
      case NOT -> primary == 0 ? 15 : 0;
      case BUFFER -> primary > 0 ? 15 : 0;
      case DROP -> Math.max(0, primary - Math.max(0, Math.min(16, dropAmount)));
      case SWITCH -> leftControl > 0 || rightControl > 0 ? clampSignal(primary) : 0;
      case AND -> primary > 0 && secondary > 0 ? 15 : 0;
      case OR -> primary > 0 || secondary > 0 ? 15 : 0;
      case XOR -> (primary > 0) ^ (secondary > 0) ? 15 : 0;
      default -> 0;
    };
  }

  public static int[][] conductorGroups(ConductorMode mode) {
    return switch (mode) {
      case PLUS -> new int[][] {{0, 1, 2, 3}};
      case VERTICAL -> new int[][] {{0, 2}};
      case HORIZONTAL -> new int[][] {{1, 3}};
      case CROSSOVER -> new int[][] {{0, 2}, {1, 3}};
      case CORNER_NE -> new int[][] {{0, 1}};
      case CORNER_ES -> new int[][] {{1, 2}};
      case CORNER_SW -> new int[][] {{2, 3}};
      case CORNER_WN -> new int[][] {{3, 0}};
    };
  }

  public static SignalPulse enterConductor(SignalPulse incoming, CellType target) {
    if (incoming.strength() <= 0 || !target.isConductor()) return SignalPulse.NONE;
    if (incoming.material() == target) {
      if (incoming.remaining() > 0)
        return new SignalPulse(incoming.strength(), target, incoming.remaining() - 1);
      int attenuated = incoming.strength() - 1;
      return attenuated > 0
          ? new SignalPulse(attenuated, target, target.attenuationInterval() - 1)
          : SignalPulse.NONE;
    }
    int strength =
        incoming.material().isConductor() ? incoming.strength() / 2 : incoming.strength();
    return strength > 0
        ? new SignalPulse(strength, target, target.attenuationInterval() - 1)
        : SignalPulse.NONE;
  }

  public static int mirroredCell(int cell, int size) {
    int x = cell % size;
    int y = cell / size;
    return y * size + (size - 1 - x);
  }

  public static int mirrorRotation(int rotation) {
    return switch (rotation & 3) {
      case 1 -> 3;
      case 3 -> 1;
      default -> rotation & 3;
    };
  }

  public static ConductorMode mirrorConductorMode(ConductorMode mode) {
    return switch (mode) {
      case CORNER_NE -> ConductorMode.CORNER_WN;
      case CORNER_ES -> ConductorMode.CORNER_SW;
      case CORNER_SW -> ConductorMode.CORNER_ES;
      case CORNER_WN -> ConductorMode.CORNER_NE;
      default -> mode;
    };
  }

  private static int clampSignal(int value) {
    return Math.max(0, Math.min(15, value));
  }

  private WaferCircuitLogic() {}
}
