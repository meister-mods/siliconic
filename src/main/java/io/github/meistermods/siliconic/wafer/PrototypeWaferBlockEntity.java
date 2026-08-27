package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class PrototypeWaferBlockEntity extends BlockEntity implements MenuProvider {
  public static final int SIZE = 9;
  private static final int CELLS = SIZE * SIZE;
  private static final String DESIGN_TAG = "SiliconicDesign";
  private ItemStack wafer = ItemStack.EMPTY;
  private final int[] inputs = new int[4];
  private final int[] outputs = new int[4];
  private final int[] signals = new int[CELLS];

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
    XOR;

    public boolean isGate() {
      return ordinal() >= NOT.ordinal();
    }
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

  public CellType getCellType(int cell) {
    byte[] cells = cells();
    return valid(cell)
        ? CellType.values()[Math.min(Byte.toUnsignedInt(cells[cell]), CellType.values().length - 1)]
        : CellType.EMPTY;
  }

  public int getRotation(int cell) {
    byte[] rotations = rotations();
    return valid(cell) ? Byte.toUnsignedInt(rotations[cell]) & 3 : 0;
  }

  public boolean hasTrace(int cell) {
    return getCellType(cell) == CellType.COPPER;
  }

  public int getCellSignal(int cell) {
    return valid(cell) ? signals[cell] : 0;
  }

  public void insertWafer(ItemStack held) {
    if (!hasWafer() && !held.isEmpty()) {
      wafer = held.copyWithCount(1);
      held.shrink(1);
      changedAndSync();
    }
  }

  public ItemStack removeWafer() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    Arrays.fill(inputs, 0);
    Arrays.fill(outputs, 0);
    Arrays.fill(signals, 0);
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
    if (!hasWafer() || pin < 0 || pin >= 4) return PinMode.DISABLED;
    int[] modes = design().getIntArray("PinModes");
    if (modes.length != 4)
      return new PinMode[] {PinMode.INPUT, PinMode.OUTPUT, PinMode.OUTPUT, PinMode.INPUT}[pin];
    return PinMode.values()[Math.max(0, Math.min(modes[pin], PinMode.values().length - 1))];
  }

  public void cyclePinMode(int pin) {
    if (!hasWafer() || pin < 0 || pin >= 4) return;
    int[] modes = currentModes();
    modes[pin] = PinMode.values()[modes[pin]].next().ordinal();
    design().putIntArray("PinModes", modes);
    changedAndSync();
  }

  public void interactCell(int cell, boolean rotate, ServerPlayer player) {
    if (!hasWafer() || !valid(cell)) return;
    CellType old = getCellType(cell);
    if (rotate) {
      if (old.isGate()) {
        byte[] rotations = rotations();
        rotations[cell] = (byte) ((rotations[cell] + 1) & 3);
        design().putByteArray("Rotations", rotations);
        changedAndSync();
      }
      return;
    }
    ItemStack carried = player.containerMenu.getCarried();
    if (carried.isEmpty()) {
      if (old != CellType.EMPTY) {
        player.getInventory().placeItemBackInInventory(new ItemStack(itemFor(old)));
        setCell(cell, CellType.EMPTY);
      }
      return;
    }
    CellType placed = typeFor(carried);
    if (old == CellType.EMPTY && placed != CellType.EMPTY) {
      setCell(cell, placed);
      if (!player.getAbilities().instabuild) carried.shrink(1);
      player.containerMenu.setCarried(carried);
    }
  }

  private void setCell(int cell, CellType type) {
    byte[] cells = cells(), rotations = rotations();
    cells[cell] = (byte) type.ordinal();
    rotations[cell] = 0;
    design().putByteArray("Cells", cells);
    design().putByteArray("Rotations", rotations);
    changedAndSync();
  }

  private CellType typeFor(ItemStack stack) {
    if (stack.is(ModItems.COPPER_FRAGMENT.get())) return CellType.COPPER;
    if (stack.is(ModItems.NOT_GATE.get())) return CellType.NOT;
    if (stack.is(ModItems.AND_GATE.get())) return CellType.AND;
    if (stack.is(ModItems.OR_GATE.get())) return CellType.OR;
    if (stack.is(ModItems.XOR_GATE.get())) return CellType.XOR;
    return CellType.EMPTY;
  }

  private Item itemFor(CellType type) {
    return switch (type) {
      case COPPER -> ModItems.COPPER_FRAGMENT.get();
      case NOT -> ModItems.NOT_GATE.get();
      case AND -> ModItems.AND_GATE.get();
      case OR -> ModItems.OR_GATE.get();
      case XOR -> ModItems.XOR_GATE.get();
      default -> ModItems.COPPER_FRAGMENT.get();
    };
  }

  public void refreshSignals() {
    if (level == null || level.isClientSide) return;
    int[] oldOutputs = outputs.clone();
    Direction[] directions = directions();
    for (int i = 0; i < 4; i++)
      inputs[i] =
          getPinMode(i) == PinMode.INPUT
              ? level.getSignal(worldPosition.relative(directions[i]), directions[i].getOpposite())
              : 0;
    simulate();
    for (int pin = 0; pin < 4; pin++)
      outputs[pin] = getPinMode(pin) == PinMode.OUTPUT ? emittedToward(pinCell(pin), pin) : 0;
    if (!Arrays.equals(oldOutputs, outputs))
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    setChanged();
    sync();
  }

  private void simulate() {
    Arrays.fill(signals, 0);
    for (int pass = 0; pass < CELLS + 4; pass++) {
      int[] next = new int[CELLS];
      for (int cell = 0; cell < CELLS; cell++) {
        CellType type = getCellType(cell);
        if (type == CellType.COPPER) next[cell] = maxIncoming(cell);
        else if (type.isGate()) next[cell] = gateOutput(cell, type);
      }
      if (Arrays.equals(signals, next)) break;
      System.arraycopy(next, 0, signals, 0, CELLS);
    }
  }

  private int maxIncoming(int cell) {
    int value = pinInput(cell);
    for (int side = 0; side < 4; side++) {
      int neighbor = neighbor(cell, side);
      if (neighbor >= 0) value = Math.max(value, emittedToward(neighbor, (side + 2) & 3));
    }
    return value;
  }

  private int gateOutput(int cell, CellType type) {
    int facing = getRotation(cell), a, b;
    if (type == CellType.NOT) {
      a = readFrom(cell, (facing + 2) & 3);
      return a == 0 ? 15 : 0;
    }
    a = readFrom(cell, (facing + 3) & 3);
    b = readFrom(cell, (facing + 1) & 3);
    return switch (type) {
      case AND -> a > 0 && b > 0 ? 15 : 0;
      case OR -> a > 0 || b > 0 ? 15 : 0;
      case XOR -> (a > 0) ^ (b > 0) ? 15 : 0;
      default -> 0;
    };
  }

  private int readFrom(int cell, int side) {
    int neighbor = neighbor(cell, side);
    return Math.max(
        pinInputFrom(cell, side), neighbor < 0 ? 0 : emittedToward(neighbor, (side + 2) & 3));
  }

  private int emittedToward(int cell, int side) {
    CellType type = getCellType(cell);
    if (type == CellType.COPPER) return signals[cell];
    return type.isGate() && getRotation(cell) == side ? signals[cell] : 0;
  }

  private int pinInput(int cell) {
    int value = 0;
    for (int pin = 0; pin < 4; pin++)
      if (pinCell(pin) == cell && getPinMode(pin) == PinMode.INPUT)
        value = Math.max(value, inputs[pin]);
    return value;
  }

  private int pinInputFrom(int cell, int side) {
    for (int pin = 0; pin < 4; pin++)
      if (pinCell(pin) == cell && pin == side && getPinMode(pin) == PinMode.INPUT)
        return inputs[pin];
    return 0;
  }

  private int neighbor(int cell, int side) {
    int x = cell % SIZE, y = cell / SIZE;
    return switch (side) {
      case 0 -> y > 0 ? cell - SIZE : -1;
      case 1 -> x + 1 < SIZE ? cell + 1 : -1;
      case 2 -> y + 1 < SIZE ? cell + SIZE : -1;
      default -> x > 0 ? cell - 1 : -1;
    };
  }

  public int getOutput(Direction direction) {
    Direction[] dirs = directions();
    for (int i = 0; i < 4; i++)
      if (dirs[i].getOpposite() == direction && getPinMode(i) == PinMode.OUTPUT) return outputs[i];
    return 0;
  }

  private int pinCell(int pin) {
    int m = SIZE / 2;
    return switch (pin) {
      case 0 -> m;
      case 1 -> m * SIZE + SIZE - 1;
      case 2 -> (SIZE - 1) * SIZE + m;
      default -> m * SIZE;
    };
  }

  private Direction[] directions() {
    return new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
  }

  private boolean valid(int cell) {
    return hasWafer() && cell >= 0 && cell < CELLS;
  }

  private byte[] cells() {
    byte[] value = design().getByteArray("Cells");
    if (value.length == CELLS) return value;
    value = new byte[CELLS];
    // Preserve copper traces made by the earlier trace-only wafer format.
    BitSet legacy = BitSet.valueOf(design().getLongArray("Traces"));
    for (int cell = legacy.nextSetBit(0);
        cell >= 0 && cell < CELLS;
        cell = legacy.nextSetBit(cell + 1)) value[cell] = (byte) CellType.COPPER.ordinal();
    if (!legacy.isEmpty()) {
      design().putByteArray("Cells", value);
      design().remove("Traces");
    }
    return value;
  }

  private byte[] rotations() {
    byte[] value = design().getByteArray("Rotations");
    return value.length == CELLS ? value : new byte[CELLS];
  }

  private int[] currentModes() {
    int[] value = design().getIntArray("PinModes");
    return value.length == 4 ? value : new int[] {1, 2, 2, 1};
  }

  private CompoundTag design() {
    return wafer.getOrCreateTagElement(DESIGN_TAG);
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
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    wafer = ItemStack.of(tag.getCompound("Wafer"));
    copy(tag.getIntArray("Inputs"), inputs);
    copy(tag.getIntArray("Outputs"), outputs);
    copy(tag.getIntArray("Signals"), signals);
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
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.wafer_station");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WaferMenu(id, inv, this);
  }
}
