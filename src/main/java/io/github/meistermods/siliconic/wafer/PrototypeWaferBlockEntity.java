package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.Arrays;
import java.util.BitSet;
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
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class PrototypeWaferBlockEntity extends BlockEntity implements MenuProvider {
  public static final int LEVEL_1_SIZE = 9, LEVEL_2_SIZE = 13;
  private static final String DESIGN_TAG = "SiliconicDesign";
  private static final TagKey<Item> REDSTONE_DUSTS = materialTag("dusts/redstone");
  private static final TagKey<Item> COPPER_NUGGETS = materialTag("nuggets/copper");
  private static final TagKey<Item> LEAD_NUGGETS = materialTag("nuggets/lead");
  private static final TagKey<Item> SILVER_NUGGETS = materialTag("nuggets/silver");
  private static final TagKey<Item> GOLD_NUGGETS = materialTag("nuggets/gold");
  private ItemStack wafer = ItemStack.EMPTY;
  private final int[] inputs = new int[4], outputs = new int[4];
  private int[] signals = new int[0];
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private boolean wasPowered;

  private final class StationEnergyStorage extends EnergyStorage {
    StationEnergyStorage() {
      super(100_000, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
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
    GOLD;

    public boolean isGate() {
      return ordinal() >= NOT.ordinal() && ordinal() <= XOR.ordinal();
    }

    public boolean isConductor() {
      return this == REDSTONE || this == COPPER || this == LEAD || this == SILVER || this == GOLD;
    }

    public int range() {
      return switch (this) {
        case REDSTONE -> 8;
        case COPPER -> 16;
        case LEAD -> 24;
        case SILVER -> 32;
        case GOLD -> 48;
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

  private record Simulation(int[] signals, int[] outputs) {}

  private record Pulse(int strength, CellType material, int remaining) {
    static final Pulse NONE = new Pulse(0, CellType.EMPTY, 0);
  }

  public PrototypeWaferBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.PROTOTYPE_WAFER.get(), pos, state);
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
    return cellType(design(), getGridSize(), cell);
  }

  public int getRotation(int cell) {
    byte[] rotations = rotations(design(), getGridSize());
    return valid(cell) ? Byte.toUnsignedInt(rotations[cell]) & 3 : 0;
  }

  public ConductorMode getConductorMode(int cell) {
    byte[] modes = conductorModes(design(), getGridSize());
    return valid(cell)
        ? ConductorMode.values()[
            Math.min(Byte.toUnsignedInt(modes[cell]), ConductorMode.values().length - 1)]
        : ConductorMode.PLUS;
  }

  public boolean hasTrace(int cell) {
    return getCellType(cell).isConductor();
  }

  public int getCellSignal(int cell) {
    return cell >= 0 && cell < signals.length ? signals[cell] : 0;
  }

  public int getEnergyStored() {
    return energy.getEnergyStored();
  }

  public int getEnergyCapacity() {
    return energy.getMaxEnergyStored();
  }

  public int getOperationCost() {
    return getWaferLevel() == 2 ? 8 : 2;
  }

  public boolean isPowered() {
    return hasWafer() && energy.getEnergyStored() >= getOperationCost();
  }

  public int addEnergy(int amount) {
    int accepted = energy.receiveEnergy(amount, false);
    if (accepted > 0) changedAndSync();
    return accepted;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, PrototypeWaferBlockEntity station) {
    boolean powered = false;
    if (station.hasWafer()) {
      int cost = station.getOperationCost();
      powered = station.energy.extractEnergy(cost, false) == cost;
    }
    if (powered != station.wasPowered) {
      station.wasPowered = powered;
      station.refreshSignals();
      station.sync();
    }
    if (powered) station.setChanged();
    if (level.getGameTime() % 10 == 0) station.sync();
  }

  public void insertWafer(ItemStack held) {
    if (!hasWafer() && isWafer(held)) {
      wafer = held.copyWithCount(1);
      held.shrink(1);
      signals = new int[getGridSize() * getGridSize()];
      changedAndSync();
    }
  }

  public ItemStack removeWafer() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    Arrays.fill(inputs, 0);
    Arrays.fill(outputs, 0);
    signals = new int[0];
    changedAndSync();
    return result;
  }

  public ItemStack takeWaferOnBreak() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    setChanged();
    return result;
  }

  public PinMode getPinMode(int pin) {
    return hasWafer() ? pinMode(design(), pin) : PinMode.DISABLED;
  }

  public void cyclePinMode(int pin) {
    if (!hasWafer() || pin < 0 || pin >= 4) return;
    int[] modes = modes(design());
    modes[pin] = PinMode.values()[modes[pin]].next().ordinal();
    design().putIntArray("PinModes", modes);
    changedAndSync();
  }

  public void interactCell(int cell, boolean rotate, ServerPlayer player) {
    if (!valid(cell)) return;
    CellType old = getCellType(cell);
    if (rotate) {
      if (old.isConductor()) {
        byte[] modes = conductorModes(design(), getGridSize());
        modes[cell] = (byte) getConductorMode(cell).next().ordinal();
        design().putByteArray("ConductorModes", modes);
        changedAndSync();
      } else if (old.isGate() || old == CellType.CHIP) {
        byte[] value = rotations(design(), getGridSize());
        value[cell] = (byte) ((value[cell] + 1) & 3);
        design().putByteArray("Rotations", value);
        changedAndSync();
      }
      return;
    }
    ItemStack carried = player.containerMenu.getCarried();
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

  private boolean canInsertChip(ItemStack stack) {
    return getWaferLevel() > levelOf(stack) && levelOf(stack) > 0;
  }

  private void setCell(int cell, CellType type) {
    int size = getGridSize();
    byte[] cells = cells(design(), size), rots = rotations(design(), size);
    byte[] conductorModes = conductorModes(design(), size);
    cells[cell] = (byte) type.ordinal();
    rots[cell] = 0;
    conductorModes[cell] = 0;
    design().putByteArray("Cells", cells);
    design().putByteArray("Rotations", rots);
    design().putByteArray("ConductorModes", conductorModes);
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
    if (isWafer(stack)) return CellType.CHIP;
    return CellType.EMPTY;
  }

  private static TagKey<Item> materialTag(String path) {
    return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", path));
  }

  private Item itemFor(CellType type) {
    return switch (type) {
      case REDSTONE -> Items.REDSTONE;
      case COPPER -> ModItems.COPPER_FRAGMENT.get();
      case LEAD -> ModItems.LEAD_NUGGET.get();
      case SILVER -> ModItems.SILVER_NUGGET.get();
      case GOLD -> Items.GOLD_NUGGET;
      case NOT -> ModItems.NOT_GATE.get();
      case AND -> ModItems.AND_GATE.get();
      case OR -> ModItems.OR_GATE.get();
      case XOR -> ModItems.XOR_GATE.get();
      default -> ModItems.COPPER_FRAGMENT.get();
    };
  }

  private void storeChip(int cell, ItemStack stack) {
    CompoundTag chips = design().getCompound("Chips");
    chips.put(Integer.toString(cell), stack.save(new CompoundTag()));
    design().put("Chips", chips);
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
    if (level == null || level.isClientSide || !hasWafer()) return;
    int[] old = outputs.clone();
    if (!isPowered()) {
      Arrays.fill(outputs, 0);
      Arrays.fill(signals, 0);
      if (!Arrays.equals(old, outputs))
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
      setChanged();
      sync();
      return;
    }
    Direction[] dirs = directions();
    for (int i = 0; i < 4; i++)
      inputs[i] =
          getPinMode(i) == PinMode.INPUT
              ? level.getSignal(worldPosition.relative(dirs[i]), dirs[i].getOpposite())
              : 0;
    Simulation result = simulate(design(), getGridSize(), inputs, 0);
    signals = result.signals;
    System.arraycopy(result.outputs, 0, outputs, 0, 4);
    if (!Arrays.equals(old, outputs))
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    setChanged();
    sync();
  }

  private Simulation simulate(CompoundTag design, int size, int[] externalInputs, int depth) {
    int count = size * size;
    int[] values = new int[count];
    int[][] wireStrength = new int[count][4], wireRemaining = new int[count][4];
    int[][] chipOutputs = new int[count][4];
    for (int pass = 0; pass < count + 8; pass++) {
      int[][] nextChips = new int[count][4];
      if (depth < 4)
        for (int cell = 0; cell < count; cell++)
          if (cellType(design, size, cell) == CellType.CHIP) {
            ItemStack chip = chipAt(design, cell);
            if (chip.isEmpty()) continue;
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
            Simulation nested = simulate(child, sizeOf(chip), childInputs, depth + 1);
            for (int local = 0; local < 4; local++)
              if (pinMode(child, local) == PinMode.OUTPUT)
                nextChips[cell][(local + rotation) & 3] = nested.outputs[local];
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
      values = next;
      wireStrength = nextWire;
      wireRemaining = nextWireRemaining;
      chipOutputs = nextChips;
      if (stable) break;
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
    return new Simulation(values, resultOutputs);
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
        if (direct > best.strength) best = new Pulse(direct, target, target.range() - 1);
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
    if (incoming.material == target)
      return incoming.remaining > 0
          ? new Pulse(incoming.strength, target, incoming.remaining - 1)
          : Pulse.NONE;
    int strength = incoming.material.isConductor() ? incoming.strength / 2 : incoming.strength;
    return strength > 0 ? new Pulse(strength, target, target.range() - 1) : Pulse.NONE;
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
    if (type == CellType.NOT) {
      a =
          readFrom(
              d, size, values, wireStrength, wireRemaining, chips, ext, cell, (facing + 2) & 3);
      return a == 0 ? 15 : 0;
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

  private int levelOf(ItemStack stack) {
    if (stack.is(ModItems.LEVEL_2_WAFER.get())) return 2;
    if (stack.is(ModItems.SILICON_WAFER.get())) return 1;
    return 0;
  }

  private int sizeOf(ItemStack stack) {
    return levelOf(stack) == 2 ? LEVEL_2_SIZE : LEVEL_1_SIZE;
  }

  private boolean isWafer(ItemStack stack) {
    return levelOf(stack) > 0;
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
    setChanged();
    refreshSignals();
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
    tag.putInt("Energy", energy.getEnergyStored());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    wafer = ItemStack.of(tag.getCompound("Wafer"));
    copy(tag.getIntArray("Inputs"), inputs);
    copy(tag.getIntArray("Outputs"), outputs);
    signals = tag.getIntArray("Signals");
    energy.setStored(tag.getInt("Energy"));
  }

  private void copy(int[] source, int[] target) {
    if (source.length == target.length) System.arraycopy(source, 0, target, 0, target.length);
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
    if (capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
    return super.getCapability(capability, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    energyCapability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    energyCapability = LazyOptional.of(() -> energy);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.wafer_station");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WaferMenu(id, inv, this);
  }
}
