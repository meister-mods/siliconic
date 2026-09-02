package io.github.meistermods.siliconic.gametest;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.power.BalancedEnergyDistributor;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ProcessInput;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.ConductorMode;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic.SignalPulse;
import java.util.Arrays;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/** Fast, deterministic regression tests for rules that do not need blocks placed in the world. */
@SuppressWarnings({"null"})
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

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void validatesAndAllocatesMachineInputs(GameTestHelper helper) {
    MachineProcess overlapping =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/overlapping_inputs"),
            MachineKind.WAFER_FABRICATOR,
            List.of(
                new ProcessInput(
                    -1, Ingredient.of(Items.COAL, Items.CHARCOAL), 1, ProcessInput.Use.CONSUME),
                new ProcessInput(-1, Ingredient.of(Items.COAL), 1, ProcessInput.Use.CONSUME)),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            false);
    ItemStackHandler shapelessInventory = new ItemStackHandler(9);
    shapelessInventory.setStackInSlot(0, new ItemStack(Items.COAL));
    shapelessInventory.setStackInSlot(1, new ItemStack(Items.CHARCOAL));
    helper.assertTrue(
        overlapping.matches(shapelessInventory, 0, 9),
        "Overlapping shapeless ingredients must be assigned to distinct items");
    overlapping.consume(shapelessInventory, 0, 9);
    helper.assertTrue(
        shapelessInventory.getStackInSlot(0).isEmpty()
            && shapelessInventory.getStackInSlot(1).isEmpty(),
        "A shapeless process must consume its assigned items exactly once");

    MachineProcess toolProcess =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/damage_input"),
            MachineKind.SILICON_ARC_FURNACE,
            List.of(
                new ProcessInput(
                    0, Ingredient.of(Items.WOODEN_PICKAXE), 5, ProcessInput.Use.DAMAGE)),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            true);
    ItemStackHandler shapedInventory = new ItemStackHandler(3);
    shapedInventory.setStackInSlot(0, new ItemStack(Items.WOODEN_PICKAXE));
    helper.assertTrue(
        toolProcess.matches(shapedInventory, 0, 3),
        "A damage input count is durability damage, not a required stack count");
    toolProcess.consume(shapedInventory, 0, 3);
    helper.assertTrue(
        shapedInventory.getStackInSlot(0).getDamageValue() == 5,
        "A damage input must apply its configured durability damage");
    MachineProcess longEnergyProcess =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/large_energy"),
            MachineKind.SILICON_ARC_FURNACE,
            List.of(new ProcessInput(0, Ingredient.of(Items.COAL), 1)),
            Items.DIAMOND,
            1,
            List.of(),
            50_000,
            50_000,
            true);
    helper.assertTrue(
        longEnergyProcess.totalEnergy() == 2_500_000_000L,
        "Total process energy must not overflow a 32-bit integer");

    boolean rejectedInvalidSlot = false;
    try {
      new MachineProcess(
          ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/invalid_slot"),
          MachineKind.SILICON_ARC_FURNACE,
          List.of(new ProcessInput(3, Ingredient.of(Items.COAL), 1)),
          Items.DIAMOND,
          1,
          List.of(),
          1,
          1,
          true);
    } catch (IllegalArgumentException expected) {
      rejectedInvalidSlot = true;
    }
    helper.assertTrue(
        rejectedInvalidSlot, "Industrial machine recipes must reject slots outside 0 through 2");
    helper.succeed();
  }

  private SiliconicGameTests() {}
}
