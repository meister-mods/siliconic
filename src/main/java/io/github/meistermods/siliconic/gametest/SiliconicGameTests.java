package io.github.meistermods.siliconic.gametest;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.cleanroom.ConditionerBlockEntity;
import io.github.meistermods.siliconic.fabrication.FabricationStationBlockEntity;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.power.BalancedEnergyDistributor;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ProcessInput;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.reprocessing.ReprocessorBlockEntity;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlock;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlockEntity;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.ConductorMode;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic;
import io.github.meistermods.siliconic.wafer.WaferCircuitLogic.SignalPulse;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
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
  public static void boundsNestedWaferTransforms(GameTestHelper helper) {
    ItemStack validChild = waferWithCell(new ItemStack(ModItems.VLSI_WAFER.get()), CellType.NOT);
    ItemStack invalidChild = waferWithCell(new ItemStack(ModItems.ULSI_WAFER.get()), CellType.AND);
    ItemStack parent = new ItemStack(ModItems.ULSI_WAFER.get());
    CompoundTag parentDesign = parent.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] parentCells =
        new byte[PrototypeWaferBlockEntity.GRID_SIZE * PrototypeWaferBlockEntity.GRID_SIZE];
    parentCells[0] = (byte) CellType.CHIP.ordinal();
    parentCells[1] = (byte) CellType.CHIP.ordinal();
    parentDesign.putByteArray("Cells", parentCells);
    CompoundTag chips = new CompoundTag();
    chips.put("0", validChild.save(new CompoundTag()));
    chips.put("1", invalidChild.save(new CompoundTag()));
    parentDesign.put("Chips", chips);

    PrototypeWaferBlockEntity.mirrorHorizontally(parent);

    CompoundTag mirroredChips =
        parent.getTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).getCompound("Chips");
    ItemStack mirroredValidChild = ItemStack.of(mirroredChips.getCompound("8"));
    ItemStack preservedInvalidChild = ItemStack.of(mirroredChips.getCompound("7"));
    byte[] validCells =
        mirroredValidChild
            .getTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
            .getByteArray("Cells");
    byte[] invalidCells =
        preservedInvalidChild
            .getTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
            .getByteArray("Cells");
    helper.assertTrue(
        Byte.toUnsignedInt(validCells[8]) == CellType.NOT.ordinal(),
        "A valid lower-tier chip must be mirrored recursively");
    helper.assertTrue(
        Byte.toUnsignedInt(invalidCells[0]) == CellType.AND.ordinal()
            && Byte.toUnsignedInt(invalidCells[8]) == CellType.EMPTY.ordinal(),
        "A malformed same-tier chip must remain opaque instead of extending recursion");
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
    helper.assertTrue(
        overlapping.usesInputSlot(0) && overlapping.usesInputSlot(8),
        "Shapeless processes must expose every input slot in their machine");
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

    MachineProcess overflowingToolProcess =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(
                Siliconic.MOD_ID, "test/overflowing_damage_input"),
            MachineKind.SILICON_ARC_FURNACE,
            List.of(
                new ProcessInput(
                    0,
                    Ingredient.of(Items.WOODEN_PICKAXE),
                    Integer.MAX_VALUE,
                    ProcessInput.Use.DAMAGE)),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            true);
    ItemStack damagedTool = new ItemStack(Items.WOODEN_PICKAXE);
    damagedTool.setDamageValue(1);
    shapedInventory.setStackInSlot(0, damagedTool);
    overflowingToolProcess.consume(shapedInventory, 0, 3);
    helper.assertTrue(
        shapedInventory.getStackInSlot(0).isEmpty(),
        "Large durability damage must break the tool instead of overflowing");

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

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesLargeMenuValues(GameTestHelper helper) {
    int value = 2_000_000_000;
    helper.assertTrue(
        MenuDataSync.combine(MenuDataSync.low(value), MenuDataSync.high(value)) == value,
        "Menu data must preserve values larger than a signed 16-bit field");
    helper.assertTrue(
        MenuDataSync.scale(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 92) == 91,
        "Progress scaling must not overflow for large data-pack durations");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void restoresMachineProgressBeforeLevelAttachment(GameTestHelper helper) {
    CompoundTag saved = new CompoundTag();
    saved.putInt("Progress", 42);

    FabricationStationBlockEntity fabrication =
        new FabricationStationBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_FABRICATOR.get().defaultBlockState());
    fabrication.load(saved);
    helper.assertTrue(
        MenuDataSync.combine(fabrication.data().get(2), fabrication.data().get(3)) == 42,
        "Fabrication progress must survive loading before a level is attached");

    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(
            BlockPos.ZERO, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState());
    processor.load(saved);
    helper.assertTrue(
        MenuDataSync.combine(processor.data().get(2), processor.data().get(3)) == 42,
        "Industrial process progress must survive loading before a level is attached");

    ReprocessorBlockEntity reprocessor =
        new ReprocessorBlockEntity(BlockPos.ZERO, ModBlocks.REPROCESSOR.get().defaultBlockState());
    reprocessor.load(saved);
    helper.assertTrue(
        MenuDataSync.combine(reprocessor.data().get(2), reprocessor.data().get(3)) == 42,
        "Reprocessing progress must survive loading before a level is attached");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void restoresCleanroomClaimsOnLoad(GameTestHelper helper) {
    BlockPos conditionerPos = BlockPos.ZERO;
    BlockPos interiorPos = conditionerPos.above();
    CompoundTag lastScan = new CompoundTag();
    lastScan.putString("Status", "SEALED");
    CompoundTag saved = new CompoundTag();
    saved.putInt("Cleanliness", 80);
    saved.putLongArray("ClaimedInterior", new long[] {interiorPos.asLong()});
    saved.put("LastScan", lastScan);

    ConditionerBlockEntity conditioner =
        new ConditionerBlockEntity(
            conditionerPos, ModBlocks.CONDITIONER.get().defaultBlockState());
    conditioner.load(saved);
    conditioner.setLevel(helper.getLevel());
    conditioner.onLoad();
    helper.assertTrue(
        CleanroomOccupancy.isMachineInside(helper.getLevel(), interiorPos),
        "Saved cleanroom claims must be restored as soon as a conditioner loads");

    conditioner.setRemoved();
    helper.assertTrue(
        !CleanroomOccupancy.isMachineInside(helper.getLevel(), interiorPos),
        "Removing a conditioner must release its restored cleanroom claims");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void protectsRunningIndustrialInputsFromForcedLogistics(GameTestHelper helper) {
    BlockState activeState =
        ModBlocks.SILICON_ARC_FURNACE
            .get()
            .defaultBlockState()
            .setValue(SiliconProcessorBlock.ACTIVE, true);
    ItemStackHandler savedItems = new ItemStackHandler(SiliconProcessorBlockEntity.SLOT_COUNT);
    savedItems.setStackInSlot(
        SiliconProcessorBlockEntity.INPUT_SLOT, new ItemStack(Items.QUARTZ, 4));
    savedItems.setStackInSlot(
        SiliconProcessorBlockEntity.CATALYST_SLOT, new ItemStack(Items.CHARCOAL, 4));
    savedItems.setStackInSlot(
        SiliconProcessorBlockEntity.COMPONENT_SLOT,
        new ItemStack(ModItems.CARBON_ELECTRODE.get()));
    CompoundTag saved = new CompoundTag();
    saved.putInt("LayoutVersion", 2);
    saved.put("Items", savedItems.serializeNBT());
    saved.putInt("Energy", SiliconProcessorBlockEntity.ENERGY_CAPACITY);
    saved.putInt("Progress", 299);

    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(BlockPos.ZERO, activeState);
    processor.load(saved);
    processor.setLevel(helper.getLevel());
    IItemHandler forcedInventory = processor.logisticsInventory();
    ItemStack extracted =
        forcedInventory.extractItem(SiliconProcessorBlockEntity.INPUT_SLOT, 1, false);
    ItemStack rejected =
        forcedInventory.insertItem(
            SiliconProcessorBlockEntity.INPUT_SLOT, new ItemStack(Items.QUARTZ), false);
    helper.assertTrue(
        extracted.isEmpty()
            && rejected.getCount() == 1
            && processor.items().getStackInSlot(SiliconProcessorBlockEntity.INPUT_SLOT).getCount()
                == 4,
        "Forced logistics must not modify inputs after an industrial process starts");

    SiliconProcessorBlockEntity.serverTick(
        helper.getLevel(), BlockPos.ZERO, activeState, processor);
    helper.assertTrue(
        processor.items().getStackInSlot(SiliconProcessorBlockEntity.INPUT_SLOT).isEmpty()
            && processor.items().getStackInSlot(SiliconProcessorBlockEntity.CATALYST_SLOT).isEmpty()
            && processor
                    .items()
                    .getStackInSlot(SiliconProcessorBlockEntity.COMPONENT_SLOT)
                    .getDamageValue()
                == 1
            && processor
                .items()
                .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                .is(ModItems.METALLURGICAL_SILICON.get()),
        "The machine must still consume its locked inputs when the process completes");
    helper.succeed();
  }

  private static ItemStack waferWithCell(ItemStack wafer, CellType type) {
    CompoundTag design = wafer.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] cells =
        new byte[PrototypeWaferBlockEntity.GRID_SIZE * PrototypeWaferBlockEntity.GRID_SIZE];
    cells[0] = (byte) type.ordinal();
    design.putByteArray("Cells", cells);
    return wafer;
  }

  private SiliconicGameTests() {}
}
