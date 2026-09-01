package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class PrototypeWaferBlockEntity extends BlockEntity implements MenuProvider {
  public static final int GRID_SIZE = 9;
  public static final String DESIGN_TAG = "SiliconicDesign";
  public static final String COMPLETED_TAG = "SiliconicCompleted";
  public static final String RUNTIME_TAG = "SiliconicRuntime";
  private static final int MAX_SIGNAL_STRENGTH = 15;
  private static final int MAX_DROP_AMOUNT = 16;
  private static final int MAX_CONDUCTOR_ATTENUATION_INTERVAL = 6;
  private static final TagKey<Item> REDSTONE_DUSTS = materialTag("dusts/redstone");
  private static final TagKey<Item> COPPER_NUGGETS = materialTag("nuggets/copper");
  private static final TagKey<Item> LEAD_NUGGETS = materialTag("nuggets/lead");
  private static final TagKey<Item> SILVER_NUGGETS = materialTag("nuggets/silver");
  private static final TagKey<Item> GOLD_NUGGETS = materialTag("nuggets/gold");
  private ItemStack wafer = ItemStack.EMPTY;
  private final int[] inputs = new int[4], outputs = new int[4];
  private int[] signals = new int[0];
  private int[] horizontalSignals = new int[0], verticalSignals = new int[0];
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private final IItemHandler waferItems =
      new IItemHandler() {
        @Override
        public int getSlots() {
          return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
          checkWaferSlot(slot);
          return wafer;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
          checkWaferSlot(slot);
          if (stack.isEmpty() || hasWafer() || !isWafer(stack)) return stack;
          if (!simulate) mountWafer(stack);
          ItemStack remainder = stack.copy();
          remainder.shrink(1);
          return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
          checkWaferSlot(slot);
          if (amount <= 0 || !isCompleted(wafer)) return ItemStack.EMPTY;
          ItemStack result = wafer.copyWithCount(1);
          if (!simulate) removeWafer();
          return result;
        }

        @Override
        public int getSlotLimit(int slot) {
          checkWaferSlot(slot);
          return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          checkWaferSlot(slot);
          return isWafer(stack);
        }
      };
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> waferItems);
  private final int[] clientData = new int[4];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> getOperationCost();
            case 3 -> canEditHere() ? 1 : 0;
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          if (index >= 0 && index < clientData.length) clientData[index] = value;
        }

        @Override
        public int getCount() {
          return clientData.length;
        }
      };
  private boolean powered;
  private boolean insideCleanroom;
  private boolean simulationDirty = true;

  private final class StationEnergyStorage extends EnergyStorage {
    StationEnergyStorage() {
      super(100_000, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    boolean consumeInternal(int amount) {
      if (amount <= 0 || energy < amount) return false;
      energy -= amount;
      return true;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) PrototypeWaferBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public enum PinMode {
    DISABLED,
    INPUT,
    OUTPUT;

    public PinMode next() {
      return values()[(ordinal() + 1) % values().length];
    }
  }

  public enum CellType {
    EMPTY,
    COPPER,
    NOT,
    AND,
    OR,
    XOR,
    CHIP,
    REDSTONE,
    LEAD,
    SILVER,
    GOLD,
    BUFFER,
    DROP,
    SWITCH,
    CONTAMINATED;

    public boolean isGate() {
      return (ordinal() >= NOT.ordinal() && ordinal() <= XOR.ordinal())
          || this == BUFFER
          || this == DROP
          || this == SWITCH;
    }

    public boolean isConductor() {
      return this == REDSTONE || this == COPPER || this == LEAD || this == SILVER || this == GOLD;
    }

    public int attenuationInterval() {
      return switch (this) {
        case REDSTONE -> 1;
        case COPPER -> 2;
        case LEAD -> 3;
        case SILVER -> 4;
        case GOLD -> 6;
        default -> 0;
      };
    }
  }

  public enum ConductorMode {
    PLUS,
    VERTICAL,
    HORIZONTAL,
    CROSSOVER,
    CORNER_NE,
    CORNER_ES,
    CORNER_SW,
    CORNER_WN;

    public ConductorMode next() {
      return values()[(ordinal() + 1) % values().length];
    }
  }

  private record Simulation(
      int[] signals,
      int[] horizontalSignals,
      int[] verticalSignals,
      int[] outputs,
      int[][] wireStrength,
      int[][] wireRemaining,
      int[][] chipOutputs) {}

  private record RuntimeState(
      int[] signals, int[][] wireStrength, int[][] wireRemaining, int[][] chipOutputs) {}

  private record SimulationFrame(
      int[] signals, int[][] wireStrength, int[][] wireRemaining, int[][] chipOutputs) {}

  private record WireState(int[] signals, int[][] strength, int[][] remaining) {}

  private record Pulse(int strength, CellType material, int remaining) {
    static final Pulse NONE = new Pulse(0, CellType.EMPTY, 0);
  }

  public PrototypeWaferBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.WAFER_ASSEMBLER.get(), pos, state);
  }

  public boolean hasWafer() {
    return !wafer.isEmpty();
  }

  public ItemStack getWafer() {
    return wafer;
  }

  public int getWaferLevel() {
    return levelOf(wafer);
  }

  public int getGridSize() {
    return sizeOf(wafer);
  }

  public CellType getCellType(int cell) {
    return valid(cell) ? cellType(design(), getGridSize(), cell) : CellType.EMPTY;
  }

  public ItemStack getEmbeddedWafer(int cell) {
    if (!valid(cell) || getCellType(cell) != CellType.CHIP) return ItemStack.EMPTY;
    return chipAt(design(), cell);
  }

  public int getRotation(int cell) {
    if (!valid(cell)) return 0;
    byte[] rotations = rotations(design(), getGridSize());
    return Byte.toUnsignedInt(rotations[cell]) & 3;
  }

  public ConductorMode getConductorMode(int cell) {
    if (!valid(cell)) return ConductorMode.PLUS;
    byte[] modes = conductorModes(design(), getGridSize());
    return ConductorMode.values()[
        Math.min(Byte.toUnsignedInt(modes[cell]), ConductorMode.values().length - 1)];
  }

  public int getDropAmount(int cell) {
    if (!valid(cell) || getCellType(cell) != CellType.DROP) return 0;
    return Math.min(
        MAX_DROP_AMOUNT, Byte.toUnsignedInt(dropAmounts(design(), getGridSize())[cell]));
  }

  public boolean hasTrace(int cell) {
    return getCellType(cell).isConductor();
  }

  public int getCellSignal(int cell) {
    return cell >= 0 && cell < signals.length ? signals[cell] : 0;
  }

  public int getHorizontalSignal(int cell) {
    return cell >= 0 && cell < horizontalSignals.length ? horizontalSignals[cell] : 0;
  }

  public int getVerticalSignal(int cell) {
    return cell >= 0 && cell < verticalSignals.length ? verticalSignals[cell] : 0;
  }

  public int getEnergyStored() {
    return energy.getEnergyStored();
  }

  public int getEnergyCapacity() {
    return energy.getMaxEnergyStored();
  }

  public ContainerData data() {
    return data;
  }

  public int getOperationCost() {
    if (isCreativeWaferMachine() || !hasWafer()) return 0;
    int level = Math.max(1, getWaferLevel());
    int stationCost = 2 << ((level - 1) * 2);
    return isEditable() ? stationCost : Math.max(1, stationCost / 2);
  }

  public boolean isEditable() {
    return getBlockState().is(ModBlocks.WAFER_ASSEMBLER.get()) || isCreativeAssembler();
  }

  public boolean isCreativeAssembler() {
    return getBlockState().is(ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
  }

  public boolean isCreativeGuard() {
    return getBlockState().is(ModBlocks.CREATIVE_WAFER_GUARD.get());
  }

  public boolean isCreativeWaferMachine() {
    return isCreativeAssembler() || isCreativeGuard();
  }

  public boolean isPowered() {
    return hasWafer() && canOperateHere() && (isCreativeWaferMachine() || powered);
  }

  public boolean isInsideCleanroom() {
    return level != null && !level.isClientSide
        ? CleanroomOccupancy.isMachineInside(level, worldPosition)
        : insideCleanroom;
  }

  public boolean canOperateHere() {
    return isCreativeWaferMachine()
        || getBlockState().is(ModBlocks.WAFER_GUARD.get())
        || isInsideCleanroom();
  }

  public boolean canEditHere() {
    return isCreativeAssembler() || isInsideCleanroom();
  }

  public int addEnergy(int amount) {
    if (isCreativeWaferMachine()) return 0;
    int accepted = energy.receiveEnergy(amount, false);
    if (accepted > 0) sync();
    return accepted;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, PrototypeWaferBlockEntity station) {
    boolean nextInsideCleanroom = CleanroomOccupancy.isMachineInside(level, pos);
    boolean cleanroomChanged = nextInsideCleanroom != station.insideCleanroom;
    station.insideCleanroom = nextInsideCleanroom;
    boolean canOperate =
        station.isCreativeWaferMachine()
            || state.is(ModBlocks.WAFER_GUARD.get())
            || nextInsideCleanroom;
    boolean nextPowered = false;
    if (canOperate && station.hasWafer()) {
      int cost = station.getOperationCost();
      nextPowered = station.isCreativeWaferMachine() || station.energy.consumeInternal(cost);
    }
    if (nextPowered != station.powered) {
      station.powered = nextPowered;
      station.refreshSignals();
    } else if (cleanroomChanged) station.sync();
    if (nextPowered) station.setChanged();
  }

  public void insertWafer(ItemStack held) {
    if (!hasWafer() && isWafer(held)) {
      mountWafer(held);
      held.shrink(1);
    }
  }

  private void mountWafer(ItemStack stack) {
    wafer = stack.copyWithCount(1);
    powered = false;
    signals = new int[getGridSize() * getGridSize()];
    horizontalSignals = new int[signals.length];
    verticalSignals = new int[signals.length];
    changedAndSync();
  }

  private static void checkWaferSlot(int slot) {
    if (slot != 0)
      throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range - [0,1)");
  }

  public ItemStack removeWafer() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    powered = false;
    Arrays.fill(inputs, 0);
    Arrays.fill(outputs, 0);
    signals = new int[0];
    horizontalSignals = new int[0];
    verticalSignals = new int[0];
    changedAndSync();
    return result;
  }

  public ItemStack takeWaferOnBreak() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    powered = false;
    setChanged();
    return result;
  }

  public PinMode getPinMode(int pin) {
    return hasWafer() ? pinMode(design(), pin) : PinMode.DISABLED;
  }

  public void cyclePinMode(int pin) {
    if (!canEditHere() || !hasWafer() || pin < 0 || pin >= 4) return;
    markUnfinished();
    clearRuntimeState(wafer);
    int[] modes = modes(design());
    modes[pin] = pinMode(design(), pin).next().ordinal();
    design().putIntArray("PinModes", modes);
    changedAndSync();
  }

  public void interactCell(int cell, boolean rotate, ServerPlayer player) {
    if (!canEditHere() || !valid(cell)) return;
    CellType old = getCellType(cell);
    if (old == CellType.CONTAMINATED) return;
    if (contaminateCellOnEdit()) {
      if (old == CellType.CHIP) removeChip(cell);
      setCell(cell, CellType.CONTAMINATED);
      return;
    }
    ItemStack carried = player.containerMenu.getCarried();
    if (rotate) {
      if (old == CellType.DROP
          && (carried.is(REDSTONE_DUSTS)
              || player.getMainHandItem().is(REDSTONE_DUSTS)
              || player.getOffhandItem().is(REDSTONE_DUSTS))) {
        cycleDropAmount(cell);
      } else if (old.isConductor()) {
        markUnfinished();
        clearRuntimeState(wafer);
        byte[] modes = conductorModes(design(), getGridSize());
        modes[cell] = (byte) getConductorMode(cell).next().ordinal();
        design().putByteArray("ConductorModes", modes);
        changedAndSync();
      } else if (old.isGate() || old == CellType.CHIP) {
        markUnfinished();
        clearRuntimeState(wafer);
        byte[] value = rotations(design(), getGridSize());
        value[cell] = (byte) ((value[cell] + 1) & 3);
        design().putByteArray("Rotations", value);
        changedAndSync();
      }
      return;
    }
    if (carried.isEmpty()) {
      if (old != CellType.EMPTY) {
        ItemStack returned = old == CellType.CHIP ? removeChip(cell) : new ItemStack(itemFor(old));
        setCell(cell, CellType.EMPTY);
        if (!player.getAbilities().instabuild)
          player.getInventory().placeItemBackInInventory(returned);
      }
      return;
    }
    CellType placed = typeFor(carried);
    if (old == CellType.EMPTY
        && placed != CellType.EMPTY
        && (placed != CellType.CHIP || canInsertChip(carried))) {
      if (placed == CellType.CHIP) storeChip(cell, carried.copyWithCount(1));
      setCell(cell, placed);
      if (!player.getAbilities().instabuild) carried.shrink(1);
      player.containerMenu.setCarried(carried);
    }
  }

  private boolean contaminateCellOnEdit() {
    if (level == null || level.isClientSide || !getBlockState().is(ModBlocks.WAFER_ASSEMBLER.get()))
      return false;
    int cleanliness =
        Math.max(0, Math.min(100, CleanroomOccupancy.cleanlinessAtMachine(level, worldPosition)));
    int missingCleanliness = 100 - cleanliness;
    return missingCleanliness > 0 && level.random.nextInt(200) < missingCleanliness;
  }

  private boolean canInsertChip(ItemStack stack) {
    return canEmbedWafer(getWaferLevel(), levelOf(stack));
  }

  private static boolean canEmbedWafer(int parentLevel, int childLevel) {
    return childLevel > 0 && childLevel < parentLevel;
  }

  private void setCell(int cell, CellType type) {
    markUnfinished();
    clearRuntimeState(wafer);
    int size = getGridSize();
    byte[] cells = cells(design(), size), rots = rotations(design(), size);
    byte[] conductorModes = conductorModes(design(), size);
    byte[] dropAmounts = dropAmounts(design(), size);
    cells[cell] = (byte) type.ordinal();
    rots[cell] = 0;
    conductorModes[cell] = 0;
    dropAmounts[cell] = 0;
    design().putByteArray("Cells", cells);
    design().putByteArray("Rotations", rots);
    design().putByteArray("ConductorModes", conductorModes);
    design().putByteArray("DropAmounts", dropAmounts);
    changedAndSync();
  }

  private void cycleDropAmount(int cell) {
    markUnfinished();
    clearRuntimeState(wafer);
    byte[] values = dropAmounts(design(), getGridSize());
    values[cell] = (byte) ((Byte.toUnsignedInt(values[cell]) + 1) % (MAX_DROP_AMOUNT + 1));
    design().putByteArray("DropAmounts", values);
    changedAndSync();
  }

  private CellType typeFor(ItemStack stack) {
    if (stack.is(REDSTONE_DUSTS)) return CellType.REDSTONE;
    if (stack.is(COPPER_NUGGETS)) return CellType.COPPER;
    if (stack.is(LEAD_NUGGETS)) return CellType.LEAD;
    if (stack.is(SILVER_NUGGETS)) return CellType.SILVER;
    if (stack.is(GOLD_NUGGETS)) return CellType.GOLD;
    if (stack.is(ModItems.NOT_GATE.get())) return CellType.NOT;
    if (stack.is(ModItems.AND_GATE.get())) return CellType.AND;
    if (stack.is(ModItems.OR_GATE.get())) return CellType.OR;
    if (stack.is(ModItems.XOR_GATE.get())) return CellType.XOR;
    if (stack.is(ModItems.BUFFER_GATE.get())) return CellType.BUFFER;
    if (stack.is(ModItems.DROP_GATE.get())) return CellType.DROP;
    if (stack.is(ModItems.SWITCH_GATE.get())) return CellType.SWITCH;
    if (isWafer(stack)) return CellType.CHIP;
    return CellType.EMPTY;
  }

  private static TagKey<Item> materialTag(String path) {
    return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", path));
  }

  private static Item itemFor(CellType type) {
    return switch (type) {
      case REDSTONE -> Items.REDSTONE;
      case COPPER -> ModItems.COPPER_NUGGET.get();
      case LEAD -> ModItems.LEAD_NUGGET.get();
      case SILVER -> ModItems.SILVER_NUGGET.get();
      case GOLD -> Items.GOLD_NUGGET;
      case NOT -> ModItems.NOT_GATE.get();
      case AND -> ModItems.AND_GATE.get();
      case OR -> ModItems.OR_GATE.get();
      case XOR -> ModItems.XOR_GATE.get();
      case BUFFER -> ModItems.BUFFER_GATE.get();
      case DROP -> ModItems.DROP_GATE.get();
      case SWITCH -> ModItems.SWITCH_GATE.get();
      default -> ModItems.COPPER_NUGGET.get();
    };
  }

  private void storeChip(int cell, ItemStack stack) {
    storeChip(design(), cell, stack);
  }

  private void storeChip(CompoundTag design, int cell, ItemStack stack) {
    CompoundTag chips = design.getCompound("Chips");
    chips.put(Integer.toString(cell), stack.save(new CompoundTag()));
    design.put("Chips", chips);
  }

  private ItemStack chipAt(CompoundTag design, int cell) {
    return ItemStack.of(design.getCompound("Chips").getCompound(Integer.toString(cell)));
  }

  private ItemStack removeChip(int cell) {
    CompoundTag chips = design().getCompound("Chips");
    ItemStack result = ItemStack.of(chips.getCompound(Integer.toString(cell)));
    chips.remove(Integer.toString(cell));
    design().put("Chips", chips);
    return result;
  }

  public void refreshSignals() {
    if (!recalculateSignals()) return;
    setChanged();
    sync();
  }

  private boolean recalculateSignals() {
    if (level == null || level.isClientSide || !hasWafer()) return false;
    int[] old = outputs.clone();
    if (!isPowered()) {
      simulationDirty = true;
      Arrays.fill(outputs, 0);
      Arrays.fill(signals, 0);
      Arrays.fill(horizontalSignals, 0);
      Arrays.fill(verticalSignals, 0);
      if (!Arrays.equals(old, outputs))
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
      return true;
    }
    Direction[] dirs = directions();
    int[] nextInputs = new int[4];
    for (int i = 0; i < 4; i++)
      nextInputs[i] =
          getPinMode(i) == PinMode.INPUT
              ? level.getSignal(worldPosition.relative(dirs[i]), dirs[i].getOpposite())
              : 0;
    if (!simulationDirty && Arrays.equals(inputs, nextInputs)) return false;
    System.arraycopy(nextInputs, 0, inputs, 0, inputs.length);
    Simulation result = simulateWafer(wafer, inputs, getWaferLevel());
    simulationDirty = false;
    signals = result.signals.clone();
    horizontalSignals = result.horizontalSignals.clone();
    verticalSignals = result.verticalSignals.clone();
    System.arraycopy(result.outputs, 0, outputs, 0, 4);
    if (!Arrays.equals(old, outputs))
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    return true;
  }

  private Simulation simulateWafer(
      ItemStack stack, int[] externalInputs, int containingWaferLevel) {
    int size = sizeOf(stack);
    CompoundTag design = stack.getOrCreateTagElement(DESIGN_TAG);
    Simulation result =
        simulate(design, size, externalInputs, containingWaferLevel, readRuntimeState(stack, size));
    writeRuntimeState(stack, result);
    return result;
  }

  private Simulation simulate(
      CompoundTag design,
      int size,
      int[] externalInputs,
      int containingWaferLevel,
      RuntimeState previous) {
    int count = size * size;
    boolean hasNestedChips = false;
    for (int cell = 0; cell < count; cell++)
      if (cellType(design, size, cell) == CellType.CHIP) {
        hasNestedChips = true;
        break;
      }
    // Seed fixed-point iteration with the prior settled state so feedback circuits retain memory.
    int[] values = previous.signals.clone();
    int[][] wireStrength = copyMatrix(previous.wireStrength);
    int[][] wireRemaining = copyMatrix(previous.wireRemaining);
    int[][] chipOutputs = copyMatrix(previous.chipOutputs);
    // Nested chips are instance-local and only need reevaluation when their inputs change.
    int[][] lastChipInputs = new int[count][];
    int[][] lastNestedOutputs = new int[count][];
    List<SimulationFrame> history = new ArrayList<>();
    history.add(captureFrame(values, wireStrength, wireRemaining, chipOutputs));
    boolean networkStable = false;
    int maxSettlePasses = count + MAX_SIGNAL_STRENGTH * MAX_CONDUCTOR_ATTENUATION_INTERVAL + 8;
    for (int pass = 0; pass < maxSettlePasses; pass++) {
      CompoundTag designBeforePass = hasNestedChips ? design.copy() : null;
      int[][] nextChips = new int[count][4];
      for (int cell = 0; cell < count; cell++)
        if (cellType(design, size, cell) == CellType.CHIP) {
          ItemStack chip = chipAt(design, cell);
          int childLevel = levelOf(chip);
          if (!canEmbedWafer(containingWaferLevel, childLevel)) continue;
          int rotation = rotation(design, size, cell);
          CompoundTag child = chip.getOrCreateTagElement(DESIGN_TAG);
          int[] childInputs = new int[4];
          for (int local = 0; local < 4; local++)
            if (pinMode(child, local) == PinMode.INPUT) {
              int world = (local + rotation) & 3;
              childInputs[local] =
                  readFrom(
                      design,
                      size,
                      values,
                      wireStrength,
                      wireRemaining,
                      chipOutputs,
                      externalInputs,
                      cell,
                      world);
            }
          int[] nestedOutputs;
          if (Arrays.equals(lastChipInputs[cell], childInputs)) {
            nestedOutputs = lastNestedOutputs[cell];
          } else {
            nestedOutputs = simulateWafer(chip, childInputs, childLevel).outputs.clone();
            lastChipInputs[cell] = childInputs.clone();
            lastNestedOutputs[cell] = nestedOutputs;
            storeChip(design, cell, chip);
          }
          for (int local = 0; local < 4; local++)
            if (pinMode(child, local) == PinMode.OUTPUT)
              nextChips[cell][(local + rotation) & 3] = nestedOutputs[local];
        }
      int[] next = new int[count];
      int[][] nextWire = new int[count][4], nextWireRemaining = new int[count][4];
      for (int cell = 0; cell < count; cell++) {
        CellType type = cellType(design, size, cell);
        if (type.isConductor()) {
          routeConductor(
              design,
              size,
              values,
              wireStrength,
              wireRemaining,
              nextChips,
              externalInputs,
              cell,
              type,
              nextWire,
              nextWireRemaining);
          for (int side = 0; side < 4; side++)
            next[cell] = Math.max(next[cell], nextWire[cell][side]);
        } else if (type.isGate())
          next[cell] =
              gateOutput(
                  design,
                  size,
                  values,
                  wireStrength,
                  wireRemaining,
                  nextChips,
                  externalInputs,
                  cell,
                  type);
        else if (type == CellType.CHIP)
          for (int side = 0; side < 4; side++)
            next[cell] = Math.max(next[cell], nextChips[cell][side]);
      }
      boolean stable =
          Arrays.equals(values, next)
              && Arrays.deepEquals(wireStrength, nextWire)
              && Arrays.deepEquals(wireRemaining, nextWireRemaining)
              && Arrays.deepEquals(chipOutputs, nextChips);
      if (stable) {
        values = next;
        wireStrength = nextWire;
        wireRemaining = nextWireRemaining;
        chipOutputs = nextChips;
        networkStable = true;
        break;
      }

      SimulationFrame nextFrame = captureFrame(next, nextWire, nextWireRemaining, nextChips);
      if (containsFrame(history, nextFrame)) {
        // A feedback loop such as T = Q xor D has no fixed point during an active clock edge.
        // Keep the last unique phase so one external input event advances sequential state once.
        if (designBeforePass != null) restoreDesign(design, designBeforePass);
        break;
      }
      values = next;
      wireStrength = nextWire;
      wireRemaining = nextWireRemaining;
      chipOutputs = nextChips;
      history.add(nextFrame);
    }
    if (!networkStable) {
      WireState settledWires =
          settleWires(design, size, values, chipOutputs, externalInputs, maxSettlePasses);
      values = settledWires.signals;
      wireStrength = settledWires.strength;
      wireRemaining = settledWires.remaining;
    }
    int[] resultOutputs = new int[4];
    for (int pin = 0; pin < 4; pin++)
      if (pinMode(design, pin) == PinMode.OUTPUT)
        resultOutputs[pin] =
            emitted(
                    design,
                    size,
                    values,
                    wireStrength,
                    wireRemaining,
                    chipOutputs,
                    pinCell(size, pin),
                    pin)
                .strength;
    int[] horizontal = new int[count], vertical = new int[count];
    for (int cell = 0; cell < count; cell++) {
      if (cellType(design, size, cell).isConductor()) {
        horizontal[cell] = Math.max(wireStrength[cell][1], wireStrength[cell][3]);
        vertical[cell] = Math.max(wireStrength[cell][0], wireStrength[cell][2]);
      } else {
        horizontal[cell] = values[cell];
        vertical[cell] = values[cell];
      }
    }
    return new Simulation(
        values, horizontal, vertical, resultOutputs, wireStrength, wireRemaining, chipOutputs);
  }

  private RuntimeState readRuntimeState(ItemStack stack, int size) {
    int count = size * size;
    CompoundTag runtime = stack.getTagElement(RUNTIME_TAG);
    if (runtime == null || runtime.getInt("Size") != size)
      return new RuntimeState(
          new int[count], new int[count][4], new int[count][4], new int[count][4]);
    return new RuntimeState(
        readByteVector(runtime, "Signals", count),
        readByteMatrix(runtime, "WireStrength", count),
        readByteMatrix(runtime, "WireRemaining", count),
        readByteMatrix(runtime, "ChipOutputs", count));
  }

  private int[] readByteVector(CompoundTag tag, String key, int length) {
    byte[] stored = tag.getByteArray(key);
    int[] result = new int[length];
    if (stored.length != length) return result;
    for (int index = 0; index < length; index++) result[index] = Byte.toUnsignedInt(stored[index]);
    return result;
  }

  private int[][] readByteMatrix(CompoundTag tag, String key, int rows) {
    byte[] stored = tag.getByteArray(key);
    int[][] result = new int[rows][4];
    if (stored.length != rows * 4) return result;
    for (int row = 0; row < rows; row++)
      for (int column = 0; column < 4; column++)
        result[row][column] = Byte.toUnsignedInt(stored[row * 4 + column]);
    return result;
  }

  private void writeRuntimeState(ItemStack stack, Simulation simulation) {
    CompoundTag runtime = stack.getOrCreateTagElement(RUNTIME_TAG);
    runtime.putInt("Size", sizeOf(stack));
    runtime.putByteArray("Signals", toByteArray(simulation.signals));
    runtime.putByteArray("WireStrength", toByteArray(simulation.wireStrength));
    runtime.putByteArray("WireRemaining", toByteArray(simulation.wireRemaining));
    runtime.putByteArray("ChipOutputs", toByteArray(simulation.chipOutputs));
  }

  private byte[] toByteArray(int[] values) {
    byte[] result = new byte[values.length];
    for (int index = 0; index < values.length; index++)
      result[index] = (byte) Math.max(0, Math.min(255, values[index]));
    return result;
  }

  private byte[] toByteArray(int[][] values) {
    byte[] result = new byte[values.length * 4];
    for (int row = 0; row < values.length; row++)
      for (int column = 0; column < 4; column++)
        result[row * 4 + column] = (byte) Math.max(0, Math.min(255, values[row][column]));
    return result;
  }

  private int[][] copyMatrix(int[][] values) {
    int[][] result = new int[values.length][];
    for (int row = 0; row < values.length; row++) result[row] = values[row].clone();
    return result;
  }

  private SimulationFrame captureFrame(
      int[] signals, int[][] wireStrength, int[][] wireRemaining, int[][] chipOutputs) {
    return new SimulationFrame(
        signals.clone(),
        copyMatrix(wireStrength),
        copyMatrix(wireRemaining),
        copyMatrix(chipOutputs));
  }

  private boolean containsFrame(List<SimulationFrame> history, SimulationFrame candidate) {
    for (SimulationFrame frame : history)
      if (Arrays.equals(frame.signals, candidate.signals)
          && Arrays.deepEquals(frame.wireStrength, candidate.wireStrength)
          && Arrays.deepEquals(frame.wireRemaining, candidate.wireRemaining)
          && Arrays.deepEquals(frame.chipOutputs, candidate.chipOutputs)) return true;
    return false;
  }

  private void restoreDesign(CompoundTag design, CompoundTag snapshot) {
    for (String key : new ArrayList<>(design.getAllKeys())) design.remove(key);
    design.merge(snapshot.copy());
  }

  private WireState settleWires(
      CompoundTag design,
      int size,
      int[] fixedSignals,
      int[][] chipOutputs,
      int[] externalInputs,
      int maxPasses) {
    int count = size * size;
    int[][] strength = new int[count][4];
    int[][] remaining = new int[count][4];
    for (int pass = 0; pass < maxPasses; pass++) {
      int[][] nextStrength = new int[count][4];
      int[][] nextRemaining = new int[count][4];
      for (int cell = 0; cell < count; cell++) {
        CellType type = cellType(design, size, cell);
        if (type.isConductor())
          routeConductor(
              design,
              size,
              fixedSignals,
              strength,
              remaining,
              chipOutputs,
              externalInputs,
              cell,
              type,
              nextStrength,
              nextRemaining);
      }
      boolean stable =
          Arrays.deepEquals(strength, nextStrength) && Arrays.deepEquals(remaining, nextRemaining);
      strength = nextStrength;
      remaining = nextRemaining;
      if (stable) break;
    }

    int[] signals = fixedSignals.clone();
    for (int cell = 0; cell < count; cell++)
      if (cellType(design, size, cell).isConductor()) {
        signals[cell] = 0;
        for (int side = 0; side < 4; side++)
          signals[cell] = Math.max(signals[cell], strength[cell][side]);
      }
    return new WireState(signals, strength, remaining);
  }

  private void routeConductor(
      CompoundTag d,
      int size,
      int[] values,
      int[][] wireStrength,
      int[][] wireRemaining,
      int[][] chips,
      int[] ext,
      int cell,
      CellType target,
      int[][] nextStrength,
      int[][] nextRemaining) {
    for (int[] group : conductorGroups(conductorMode(d, size, cell))) {
      Pulse best = Pulse.NONE;
      for (int side : group) {
        int direct = pinInputFrom(d, size, ext, cell, side);
        if (direct > best.strength)
          best = new Pulse(direct, target, target.attenuationInterval() - 1);
        int n = neighbor(size, cell, side);
        if (n < 0) continue;
        Pulse incoming =
            emitted(d, size, values, wireStrength, wireRemaining, chips, n, (side + 2) & 3);
        Pulse candidate = enterConductor(incoming, target);
        if (candidate.strength > best.strength
            || (candidate.strength == best.strength && candidate.remaining > best.remaining))
          best = candidate;
      }
      for (int side : group) {
        nextStrength[cell][side] = best.strength;
        nextRemaining[cell][side] = best.remaining;
      }
    }
  }

  private int[][] conductorGroups(ConductorMode mode) {
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

  private Pulse enterConductor(Pulse incoming, CellType target) {
    if (incoming.strength <= 0) return Pulse.NONE;
    if (incoming.material == target) {
      if (incoming.remaining > 0)
        return new Pulse(incoming.strength, target, incoming.remaining - 1);
      int attenuated = incoming.strength - 1;
      return attenuated > 0
          ? new Pulse(attenuated, target, target.attenuationInterval() - 1)
          : Pulse.NONE;
    }
    int strength = incoming.material.isConductor() ? incoming.strength / 2 : incoming.strength;
    return strength > 0
        ? new Pulse(strength, target, target.attenuationInterval() - 1)
        : Pulse.NONE;
  }

  private int gateOutput(
      CompoundTag d,
      int size,
      int[] values,
      int[][] wireStrength,
      int[][] wireRemaining,
      int[][] chips,
      int[] ext,
      int cell,
      CellType type) {
    int facing = rotation(d, size, cell), a, b;
    if (type == CellType.NOT || type == CellType.BUFFER || type == CellType.DROP) {
      a =
          readFrom(
              d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 2) & 3);
      if (type == CellType.NOT) return a == 0 ? 15 : 0;
      if (type == CellType.BUFFER) return a > 0 ? 15 : 0;
      return Math.max(0, a - dropAmount(d, size, cell));
    }
    if (type == CellType.SWITCH) {
      int input =
          readFrom(
              d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 2) & 3);
      int leftControl =
          readFrom(
              d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 3) & 3);
      int rightControl =
          readFrom(
              d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 1) & 3);
      return leftControl > 0 || rightControl > 0 ? input : 0;
    }
    a = readFrom(d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 3) & 3);
    b = readFrom(d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 1) & 3);
    return switch (type) {
      case AND -> a > 0 && b > 0 ? 15 : 0;
      case OR -> a > 0 || b > 0 ? 15 : 0;
      case XOR -> (a > 0) ^ (b > 0) ? 15 : 0;
      default -> 0;
    };
  }

  private int readFrom(
      CompoundTag d,
      int size,
      int[] values,
      int[][] wireStrength,
      int[][] wireRemaining,
      int[][] chips,
      int[] ext,
      int cell,
      int side) {
    int n = neighbor(size, cell, side);
    return Math.max(
        pinInputFrom(d, size, ext, cell, side),
        n < 0
            ? 0
            : emitted(d, size, values, wireStrength, wireRemaining, chips, n, (side + 2) & 3)
                .strength);
  }

  private Pulse emitted(
      CompoundTag d,
      int size,
      int[] values,
      int[][] wireStrength,
      int[][] wireRemaining,
      int[][] chips,
      int cell,
      int side) {
    CellType type = cellType(d, size, cell);
    if (type.isConductor())
      return new Pulse(wireStrength[cell][side], type, wireRemaining[cell][side]);
    if (type.isGate() && rotation(d, size, cell) == side)
      return new Pulse(values[cell], CellType.EMPTY, 0);
    if (type == CellType.CHIP) return new Pulse(chips[cell][side], CellType.EMPTY, 0);
    return Pulse.NONE;
  }

  // private int pinInput(CompoundTag d, int size, int[] ext, int cell) {
  //   int value = 0;
  //   for (int pin = 0; pin < 4; pin++)
  //     if (pinCell(size, pin) == cell && pinMode(d, pin) == PinMode.INPUT)
  //       value = Math.max(value, ext[pin]);
  //   return value;
  // }

  private int pinInputFrom(CompoundTag d, int size, int[] ext, int cell, int side) {
    return side >= 0 && side < 4 && pinCell(size, side) == cell && pinMode(d, side) == PinMode.INPUT
        ? ext[side]
        : 0;
  }

  private int neighbor(int size, int cell, int side) {
    int x = cell % size, y = cell / size;
    return switch (side) {
      case 0 -> y > 0 ? cell - size : -1;
      case 1 -> x + 1 < size ? cell + 1 : -1;
      case 2 -> y + 1 < size ? cell + size : -1;
      default -> x > 0 ? cell - 1 : -1;
    };
  }

  public int getOutput(Direction direction) {
    if (!isPowered()) return 0;
    Direction[] dirs = directions();
    for (int i = 0; i < 4; i++)
      if (dirs[i].getOpposite() == direction && getPinMode(i) == PinMode.OUTPUT) return outputs[i];
    return 0;
  }

  private static int pinCell(int size, int pin) {
    int m = size / 2;
    return switch (pin) {
      case 0 -> m;
      case 1 -> m * size + size - 1;
      case 2 -> (size - 1) * size + m;
      default -> m * size;
    };
  }

  public static int levelOf(ItemStack stack) {
    if (stack.is(ModItems.ULSI_WAFER.get())) return 5;
    if (stack.is(ModItems.VLSI_WAFER.get())) return 4;
    if (stack.is(ModItems.LSI_WAFER.get())) return 3;
    if (stack.is(ModItems.MSI_WAFER.get())) return 2;
    if (stack.is(ModItems.SSI_WAFER.get())) return 1;
    return 0;
  }

  public static boolean isCompleted(ItemStack stack) {
    return levelOf(stack) > 0 && stack.hasTag() && stack.getTag().getBoolean(COMPLETED_TAG);
  }

  public static boolean isBlankWafer(ItemStack stack) {
    return levelOf(stack) > 0
        && !isCompleted(stack)
        && (stack.getTagElement(DESIGN_TAG) == null
            || stack.getTagElement(DESIGN_TAG).getByteArray("Cells").length == 0);
  }

  public static List<ItemStack> requiredComponents(ItemStack wafer) {
    List<ItemStack> result = new ArrayList<>();
    CompoundTag design = wafer.getTagElement(DESIGN_TAG);
    if (design == null) return result;
    migrateLegacyGrid(design);
    byte[] cells = design.getByteArray("Cells");
    CompoundTag chips = design.getCompound("Chips");
    for (int cell = 0; cell < Math.min(cells.length, GRID_SIZE * GRID_SIZE); cell++) {
      CellType type =
          CellType.values()[
              Math.min(Byte.toUnsignedInt(cells[cell]), CellType.values().length - 1)];
      ItemStack required;
      if (type == CellType.CHIP) required = ItemStack.of(chips.getCompound(Integer.toString(cell)));
      else if (type == CellType.EMPTY || type == CellType.CONTAMINATED) continue;
      else required = new ItemStack(itemFor(type));
      mergeRequirement(result, required);
    }
    return result;
  }

  private static void mergeRequirement(List<ItemStack> requirements, ItemStack added) {
    if (added.isEmpty()) return;
    for (ItemStack requirement : requirements)
      if (isSameComponent(requirement, added)) {
        requirement.grow(added.getCount());
        return;
      }
    requirements.add(added.copy());
  }

  /** Runtime latch state does not change which physical circuit component an item represents. */
  public static boolean isSameComponent(ItemStack first, ItemStack second) {
    if (!first.is(second.getItem())) return false;
    if (levelOf(first) == 0 && levelOf(second) == 0)
      return ItemStack.isSameItemSameTags(first, second);
    ItemStack normalizedFirst = first.copy();
    ItemStack normalizedSecond = second.copy();
    removeRuntimeStateRecursively(normalizedFirst);
    removeRuntimeStateRecursively(normalizedSecond);
    return ItemStack.isSameItemSameTags(normalizedFirst, normalizedSecond);
  }

  private static void removeRuntimeStateRecursively(ItemStack stack) {
    clearRuntimeState(stack);
    CompoundTag design = stack.getTagElement(DESIGN_TAG);
    if (design == null) return;
    CompoundTag chips = design.getCompound("Chips");
    for (String key : chips.getAllKeys()) {
      ItemStack child = ItemStack.of(chips.getCompound(key));
      if (child.isEmpty()) continue;
      removeRuntimeStateRecursively(child);
      chips.put(key, child.save(new CompoundTag()));
    }
    design.put("Chips", chips);
  }

  public static void mirrorHorizontally(ItemStack stack) {
    if (levelOf(stack) == 0) return;
    clearRuntimeState(stack);
    CompoundTag design = stack.getTagElement(DESIGN_TAG);
    if (design == null) return;
    migrateLegacyGrid(design);
    int count = GRID_SIZE * GRID_SIZE;
    byte[] oldCells = design.getByteArray("Cells");
    byte[] oldRotations = design.getByteArray("Rotations");
    byte[] oldModes = design.getByteArray("ConductorModes");
    byte[] oldDropAmounts = design.getByteArray("DropAmounts");
    byte[] newCells = new byte[count], newRotations = new byte[count], newModes = new byte[count];
    byte[] newDropAmounts = new byte[count];
    for (int oldCell = 0; oldCell < count; oldCell++) {
      int newCell = mirroredCell(oldCell);
      if (oldCells.length == count) newCells[newCell] = oldCells[oldCell];
      if (oldRotations.length == count)
        newRotations[newCell] = (byte) mirrorRotation(Byte.toUnsignedInt(oldRotations[oldCell]));
      if (oldModes.length == count)
        newModes[newCell] =
            (byte)
                mirrorConductorMode(
                        ConductorMode.values()[
                            Math.min(
                                Byte.toUnsignedInt(oldModes[oldCell]),
                                ConductorMode.values().length - 1)])
                    .ordinal();
      if (oldDropAmounts.length == count) newDropAmounts[newCell] = oldDropAmounts[oldCell];
    }
    if (oldCells.length == count) design.putByteArray("Cells", newCells);
    if (oldRotations.length == count) design.putByteArray("Rotations", newRotations);
    if (oldModes.length == count) design.putByteArray("ConductorModes", newModes);
    if (oldDropAmounts.length == count) design.putByteArray("DropAmounts", newDropAmounts);
    int[] pinModes = design.getIntArray("PinModes");
    if (pinModes.length == 4) {
      int west = pinModes[3];
      pinModes[3] = pinModes[1];
      pinModes[1] = west;
      design.putIntArray("PinModes", pinModes);
    }
    CompoundTag oldChips = design.getCompound("Chips"), newChips = new CompoundTag();
    for (String key : oldChips.getAllKeys()) {
      try {
        int oldCell = Integer.parseInt(key);
        if (oldCell < 0 || oldCell >= count) continue;
        ItemStack chip = ItemStack.of(oldChips.getCompound(key));
        mirrorHorizontally(chip);
        newChips.put(Integer.toString(mirroredCell(oldCell)), chip.save(new CompoundTag()));
      } catch (NumberFormatException ignored) {
        // Ignore malformed legacy chip indices.
      }
    }
    design.put("Chips", newChips);
  }

  private static int mirroredCell(int cell) {
    int x = cell % GRID_SIZE, y = cell / GRID_SIZE;
    return y * GRID_SIZE + (GRID_SIZE - 1 - x);
  }

  private static int mirrorRotation(int rotation) {
    return switch (rotation & 3) {
      case 1 -> 3;
      case 3 -> 1;
      default -> rotation & 3;
    };
  }

  private static ConductorMode mirrorConductorMode(ConductorMode mode) {
    return switch (mode) {
      case CORNER_NE -> ConductorMode.CORNER_WN;
      case CORNER_ES -> ConductorMode.CORNER_SW;
      case CORNER_SW -> ConductorMode.CORNER_ES;
      case CORNER_WN -> ConductorMode.CORNER_NE;
      default -> mode;
    };
  }

  private static void migrateLegacyGrid(CompoundTag design) {
    final int oldSize = 13, offset = (oldSize - GRID_SIZE) / 2;
    byte[] oldCells = design.getByteArray("Cells");
    if (oldCells.length != oldSize * oldSize) return;
    byte[] oldRotations = design.getByteArray("Rotations");
    byte[] oldModes = design.getByteArray("ConductorModes");
    byte[] oldDropAmounts = design.getByteArray("DropAmounts");
    byte[] cells = new byte[GRID_SIZE * GRID_SIZE];
    byte[] rotations = new byte[cells.length], modes = new byte[cells.length];
    byte[] dropAmounts = new byte[cells.length];
    CompoundTag oldChips = design.getCompound("Chips"), chips = new CompoundTag();
    for (int y = 0; y < GRID_SIZE; y++)
      for (int x = 0; x < GRID_SIZE; x++) {
        int oldCell = (y + offset) * oldSize + x + offset;
        int cell = y * GRID_SIZE + x;
        cells[cell] = oldCells[oldCell];
        if (oldRotations.length == oldCells.length) rotations[cell] = oldRotations[oldCell];
        if (oldModes.length == oldCells.length) modes[cell] = oldModes[oldCell];
        if (oldDropAmounts.length == oldCells.length) dropAmounts[cell] = oldDropAmounts[oldCell];
        String oldKey = Integer.toString(oldCell);
        if (oldChips.contains(oldKey))
          chips.put(Integer.toString(cell), oldChips.get(oldKey).copy());
      }
    design.putByteArray("Cells", cells);
    design.putByteArray("Rotations", rotations);
    design.putByteArray("ConductorModes", modes);
    design.putByteArray("DropAmounts", dropAmounts);
    design.put("Chips", chips);
  }

  private int sizeOf(ItemStack stack) {
    return GRID_SIZE;
  }

  private boolean isWafer(ItemStack stack) {
    return levelOf(stack) > 0;
  }

  public void completeWafer(String name) {
    if (!canEditHere() || !hasWafer()) return;
    String trimmed = name == null ? "" : name.strip();
    if (trimmed.length() > 50) trimmed = trimmed.substring(0, 50);
    if (trimmed.isEmpty()) wafer.resetHoverName();
    else wafer.setHoverName(Component.literal(trimmed));
    wafer.getOrCreateTag().putBoolean(COMPLETED_TAG, true);
    changedAndSync();
  }

  private void markUnfinished() {
    if (wafer.hasTag()) wafer.getTag().remove(COMPLETED_TAG);
  }

  private static void clearRuntimeState(ItemStack stack) {
    if (stack.hasTag()) stack.getTag().remove(RUNTIME_TAG);
  }

  private boolean valid(int cell) {
    return hasWafer() && cell >= 0 && cell < getGridSize() * getGridSize();
  }

  private CellType cellType(CompoundTag d, int size, int cell) {
    byte[] value = cells(d, size);
    return cell >= 0 && cell < value.length
        ? CellType.values()[Math.min(Byte.toUnsignedInt(value[cell]), CellType.values().length - 1)]
        : CellType.EMPTY;
  }

  private byte[] cells(CompoundTag d, int size) {
    migrateLegacyGrid(d);
    int count = size * size;
    byte[] value = d.getByteArray("Cells");
    if (value.length == count) return value;
    value = new byte[count];
    BitSet legacy = BitSet.valueOf(d.getLongArray("Traces"));
    for (int cell = legacy.nextSetBit(0);
        cell >= 0 && cell < count;
        cell = legacy.nextSetBit(cell + 1)) value[cell] = (byte) CellType.COPPER.ordinal();
    if (!legacy.isEmpty()) {
      d.putByteArray("Cells", value);
      d.remove("Traces");
    }
    return value;
  }

  private byte[] rotations(CompoundTag d, int size) {
    byte[] value = d.getByteArray("Rotations");
    return value.length == size * size ? value : new byte[size * size];
  }

  private byte[] conductorModes(CompoundTag d, int size) {
    byte[] value = d.getByteArray("ConductorModes");
    return value.length == size * size ? value : new byte[size * size];
  }

  private byte[] dropAmounts(CompoundTag d, int size) {
    byte[] value = d.getByteArray("DropAmounts");
    return value.length == size * size ? value : new byte[size * size];
  }

  private int dropAmount(CompoundTag d, int size, int cell) {
    return Math.min(MAX_DROP_AMOUNT, Byte.toUnsignedInt(dropAmounts(d, size)[cell]));
  }

  private ConductorMode conductorMode(CompoundTag d, int size, int cell) {
    byte[] modes = conductorModes(d, size);
    return ConductorMode.values()[
        Math.min(Byte.toUnsignedInt(modes[cell]), ConductorMode.values().length - 1)];
  }

  private int rotation(CompoundTag d, int size, int cell) {
    return Byte.toUnsignedInt(rotations(d, size)[cell]) & 3;
  }

  private int[] modes(CompoundTag d) {
    int[] value = d.getIntArray("PinModes");
    return value.length == 4 ? value : new int[] {1, 2, 2, 1};
  }

  private PinMode pinMode(CompoundTag d, int pin) {
    int[] value = modes(d);
    return PinMode.values()[Math.max(0, Math.min(value[pin], PinMode.values().length - 1))];
  }

  private CompoundTag design() {
    return wafer.getOrCreateTagElement(DESIGN_TAG);
  }

  private Direction[] directions() {
    return new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
  }

  private void changedAndSync() {
    simulationDirty = true;
    setChanged();
    recalculateSignals();
    updateBlockState();
    sync();
  }

  private void updateBlockState() {
    if (level != null
        && getBlockState().hasProperty(PrototypeWaferBlock.HAS_WAFER)
        && getBlockState().getValue(PrototypeWaferBlock.HAS_WAFER) != hasWafer())
      level.setBlock(
          worldPosition, getBlockState().setValue(PrototypeWaferBlock.HAS_WAFER, hasWafer()), 3);
  }

  private void sync() {
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Wafer", wafer.save(new CompoundTag()));
    tag.putIntArray("Inputs", inputs);
    tag.putIntArray("Outputs", outputs);
    tag.putIntArray("Signals", signals);
    tag.putIntArray("HorizontalSignals", horizontalSignals);
    tag.putIntArray("VerticalSignals", verticalSignals);
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putBoolean("InsideCleanroom", insideCleanroom);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    simulationDirty = true;
    ItemStack loadedWafer = ItemStack.of(tag.getCompound("Wafer"));
    wafer = isWafer(loadedWafer) ? loadedWafer.copyWithCount(1) : ItemStack.EMPTY;
    copy(tag.getIntArray("Inputs"), inputs);
    copy(tag.getIntArray("Outputs"), outputs);
    int signalCount = hasWafer() ? getGridSize() * getGridSize() : 0;
    signals = fixedLength(tag.getIntArray("Signals"), signalCount);
    horizontalSignals = fixedLength(tag.getIntArray("HorizontalSignals"), signalCount);
    verticalSignals = fixedLength(tag.getIntArray("VerticalSignals"), signalCount);
    energy.setStored(tag.getInt("Energy"));
    insideCleanroom = tag.getBoolean("InsideCleanroom");
  }

  private void copy(int[] source, int[] target) {
    Arrays.fill(target, 0);
    if (source.length == target.length) System.arraycopy(source, 0, target, 0, target.length);
  }

  private int[] fixedLength(int[] source, int length) {
    return source.length == length ? source : new int[length];
  }

  @Override
  public CompoundTag getUpdateTag() {
    return saveWithoutMetadata();
  }

  @Nullable
  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
    if (packet.getTag() != null) load(packet.getTag());
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
    if (capability == ForgeCapabilities.ENERGY && !isCreativeWaferMachine())
      return energyCapability.cast();
    if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
    return super.getCapability(capability, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    energyCapability.invalidate();
    itemCapability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    energyCapability = LazyOptional.of(() -> energy);
    itemCapability = LazyOptional.of(() -> waferItems);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable(
        isCreativeAssembler()
            ? "container.siliconic.creative_wafer_assembler"
            : "container.siliconic.wafer_assembler");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WaferMenu(id, inv, this);
  }
}
