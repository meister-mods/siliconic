package io.github.meistermods.siliconic.wafer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;

import org.jetbrains.annotations.Nullable;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@SuppressWarnings({"null"})
public class PrototypeWaferBlockEntity extends BlockEntity implements MenuProvider {
  public static final int SIZE = 9;
  private static final String DESIGN_TAG = "SiliconicDesign";
  private ItemStack wafer = ItemStack.EMPTY;
  private final int[] inputs = new int[4];
  private final int[] outputs = new int[4];

  public enum PinMode {
    DISABLED,
    INPUT,
    OUTPUT;

    public PinMode next() {
      return values()[(ordinal() + 1) % values().length];
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

  public void insertWafer(ItemStack held) {
    if (hasWafer() || held.isEmpty()) return;
    wafer = held.copyWithCount(1);
    held.shrink(1);
    changedAndSync();
  }

  public ItemStack removeWafer() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    Arrays.fill(inputs, 0);
    Arrays.fill(outputs, 0);
    changedAndSync();
    return result;
  }

  public ItemStack takeWaferOnBreak() {
    ItemStack result = wafer;
    wafer = ItemStack.EMPTY;
    setChanged();
    return result;
  }

  public boolean hasTrace(int index) {
    return hasWafer() && index >= 0 && index < SIZE * SIZE && traces().get(index);
  }

  public PinMode getPinMode(int pin) {
    if (!hasWafer() || pin < 0 || pin >= 4) return PinMode.DISABLED;
    int[] modes = design().getIntArray("PinModes");
    if (modes.length != 4)
      return new PinMode[] {PinMode.INPUT, PinMode.OUTPUT, PinMode.OUTPUT, PinMode.INPUT}[pin];
    return PinMode.values()[Math.max(0, Math.min(modes[pin], PinMode.values().length - 1))];
  }

  public void toggleTrace(int index) {
    if (!hasWafer() || index < 0 || index >= SIZE * SIZE) return;
    BitSet traces = traces();
    traces.flip(index);
    design().putLongArray("Traces", traces.toLongArray());
    changedAndSync();
  }

  public void cyclePinMode(int pin) {
    if (!hasWafer() || pin < 0 || pin >= 4) return;
    int[] modes = currentModes();
    modes[pin] = PinMode.values()[modes[pin]].next().ordinal();
    design().putIntArray("PinModes", modes);
    changedAndSync();
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
    for (int target = 0; target < 4; target++) {
      int value = 0;
      if (getPinMode(target) == PinMode.OUTPUT)
        for (int source = 0; source < 4; source++)
          if (getPinMode(source) == PinMode.INPUT && connected(pinCell(source), pinCell(target)))
            value = Math.max(value, inputs[source]);
      outputs[target] = value;
    }
    if (!Arrays.equals(oldOutputs, outputs))
      level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    setChanged();
    sync();
  }

  public int getOutput(Direction direction) {
    Direction[] directions = directions();
    // Minecraft supplies the direction from the querying neighbor back toward this block.
    // A physical EAST pin is queried as WEST, and the same inversion applies to N/S.
    for (int i = 0; i < 4; i++)
      if (directions[i].getOpposite() == direction && getPinMode(i) == PinMode.OUTPUT)
        return outputs[i];
    return 0;
  }

  public int getCellSignal(int cell) {
    if (!hasTrace(cell)) return 0;
    int value = 0;
    for (int pin = 0; pin < 4; pin++)
      if (getPinMode(pin) == PinMode.INPUT && connected(pinCell(pin), cell))
        value = Math.max(value, inputs[pin]);
    return value;
  }

  private boolean connected(int start, int goal) {
    BitSet traces = traces();
    if (!traces.get(start) || !traces.get(goal)) return false;
    boolean[] seen = new boolean[SIZE * SIZE];
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    queue.add(start);
    seen[start] = true;
    while (!queue.isEmpty()) {
      int at = queue.remove();
      if (at == goal) return true;
      int x = at % SIZE, y = at / SIZE;
      if (x > 0) visit(at - 1, traces, seen, queue);
      if (x + 1 < SIZE) visit(at + 1, traces, seen, queue);
      if (y > 0) visit(at - SIZE, traces, seen, queue);
      if (y + 1 < SIZE) visit(at + SIZE, traces, seen, queue);
    }
    return false;
  }

  private void visit(int index, BitSet traces, boolean[] seen, ArrayDeque<Integer> queue) {
    if (traces.get(index) && !seen[index]) {
      seen[index] = true;
      queue.add(index);
    }
  }

  private int pinCell(int pin) {
    int middle = SIZE / 2;
    return switch (pin) {
      case 0 -> middle;
      case 1 -> middle * SIZE + SIZE - 1;
      case 2 -> (SIZE - 1) * SIZE + middle;
      default -> middle * SIZE;
    };
  }

  private Direction[] directions() {
    return new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
  }

  private BitSet traces() {
    return BitSet.valueOf(design().getLongArray("Traces"));
  }

  private int[] currentModes() {
    int[] modes = design().getIntArray("PinModes");
    if (modes.length != 4)
      modes =
          new int[] {
            PinMode.INPUT.ordinal(),
            PinMode.OUTPUT.ordinal(),
            PinMode.OUTPUT.ordinal(),
            PinMode.INPUT.ordinal()
          };
    return modes;
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
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    wafer = ItemStack.of(tag.getCompound("Wafer"));
    copyFour(tag.getIntArray("Inputs"), inputs);
    copyFour(tag.getIntArray("Outputs"), outputs);
  }

  private void copyFour(int[] source, int[] target) {
    if (source.length == 4) System.arraycopy(source, 0, target, 0, 4);
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
