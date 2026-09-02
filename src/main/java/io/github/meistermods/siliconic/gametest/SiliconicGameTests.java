package io.github.meistermods.siliconic.gametest;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.power.BalancedEnergyDistributor;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.ConductorMode;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic.SignalPulse;
import java.util.Arrays;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Fast, deterministic regression tests for rules that do not need blocks placed in the world. */
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiliconicGameTests {
  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void distributesEnergyFairly(GameTestHelper helper) {
    int[] allocations = BalancedEnergyDistributor.allocate(new int[] {100, 100, 10}, 90, 0);
    helper.assertTrue(
        Arrays.equals(allocations, new int[] {40, 40, 10}),
        "Small demands must be satisfied before the remaining energy is split fairly");

    int[] rotated = BalancedEnergyDistributor.allocate(new int[] {5, 5, 5}, 2, 2);
    helper.assertTrue(
        Arrays.equals(rotated, new int[] {1, 0, 1}),
        "Remainders must start at the rotating fairness cursor");
    int[] largeDemands =
        BalancedEnergyDistributor.allocate(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}, 10, 0);
    helper.assertTrue(
        Arrays.equals(largeDemands, new int[] {5, 5}),
        "Large aggregate demand must not overflow the fair-share calculation");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void evaluatesWaferGateTruthTables(GameTestHelper helper) {
    helper.assertTrue(
        WaferCircuitLogic.evaluateGate(CellType.NOT, 0, 0, 0, 0, 0) == 15,
        "NOT must power an empty input");
    helper.assertTrue(
        WaferCircuitLogic.evaluateGate(CellType.AND, 15, 15, 0, 0, 0) == 15,
        "AND must require both inputs");
    helper.assertTrue(
        WaferCircuitLogic.evaluateGate(CellType.XOR, 15, 15, 0, 0, 0) == 0,
        "XOR must turn off when both inputs match");
    helper.assertTrue(
        WaferCircuitLogic.evaluateGate(CellType.SWITCH, 12, 0, 0, 15, 0) == 12,
        "A powered switch control must preserve signal strength");
    helper.assertTrue(
        WaferCircuitLogic.evaluateGate(CellType.DROP, 12, 0, 0, 0, 4) == 8,
        "DROP must subtract its configured amount");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void routesAndMirrorsWaferSignals(GameTestHelper helper) {
    SignalPulse sameMaterial =
        WaferCircuitLogic.enterConductor(new SignalPulse(15, CellType.COPPER, 0), CellType.COPPER);
    helper.assertTrue(sameMaterial.strength() == 14, "Copper must attenuate at its interval");

    SignalPulse materialChange =
        WaferCircuitLogic.enterConductor(new SignalPulse(15, CellType.COPPER, 2), CellType.GOLD);
    helper.assertTrue(
        materialChange.strength() == 7, "Changing conductor material must halve power");
    helper.assertTrue(
        WaferCircuitLogic.mirrorRotation(1) == 3
            && WaferCircuitLogic.mirrorConductorMode(ConductorMode.CORNER_NE)
                == ConductorMode.CORNER_WN
            && WaferCircuitLogic.mirroredCell(0, 9) == 8,
        "Horizontal mirroring must flip cells, rotations, and corner conductors together");
    helper.succeed();
  }

  private SiliconicGameTests() {}
}
