package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.Arrays;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PrototypeWaferBlockEntity extends BlockEntity implements MenuProvider {
  public static final int SIZE = 8;
  private final boolean[] traces = new boolean[SIZE * SIZE];
  private final PinMode[] pinModes = {PinMode.INPUT, PinMode.OUTPUT, PinMode.OUTPUT, PinMode.INPUT};
  private final int[] inputs = new int[4];
  private final int[] outputs = new int[4];

  public enum PinMode {
    DISABLED("disabled"), INPUT("input"), OUTPUT("output");
    public final String translationKey;
    PinMode(String translationKey) { this.translationKey = translationKey; }
    public PinMode next() { return values()[(ordinal() + 1) % values().length]; }
  }

  public PrototypeWaferBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.PROTOTYPE_WAFER.get(), pos, state);
  }

  public boolean hasTrace(int index) {
    return index >= 0 && index < traces.length && traces[index];
  }

  public PinMode getPinMode(int pin) { return pin >= 0 && pin < 4 ? pinModes[pin] : PinMode.DISABLED; }

  public void toggleTrace(int index) {
    if (index < 0 || index >= traces.length) return;
    traces[index] = !traces[index];
    setChanged();
    refreshSignals();
    sync();
  }

  public void cyclePinMode(int pin) {
    if (pin < 0 || pin >= 4) return;
    pinModes[pin] = pinModes[pin].next();
    setChanged(); refreshSignals(); sync();
  }

  public void refreshSignals() {
    if (level == null || level.isClientSide) return;
    int[] oldOutputs = outputs.clone();
    Direction[] directions = directions();
    for (int i = 0; i < 4; i++) inputs[i] = pinModes[i] == PinMode.INPUT
        ? level.getSignal(worldPosition.relative(directions[i]), directions[i].getOpposite()) : 0;
    for (int target = 0; target < 4; target++) {
      int value = 0;
      if (pinModes[target] == PinMode.OUTPUT) for (int source = 0; source < 4; source++)
        if (pinModes[source] == PinMode.INPUT && connected(pinCell(source), pinCell(target))) value = Math.max(value, inputs[source]);
      outputs[target] = value;
    }
    if (!Arrays.equals(oldOutputs, outputs)) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    setChanged();
    sync();
  }

  public int getOutput(Direction queriedFrom) {
    Direction[] directions = directions();
    for (int i = 0; i < 4; i++) if (directions[i] == queriedFrom) return outputs[i];
    return 0;
  }

  public int getCellSignal(int cell) {
    if (!hasTrace(cell)) return 0;
    int value = 0;
    for (int pin = 0; pin < 4; pin++)
      if (pinModes[pin] == PinMode.INPUT && connected(pinCell(pin), cell)) value = Math.max(value, inputs[pin]);
    return value;
  }

  private boolean connected(int start, int goal) {
    if (!traces[start] || !traces[goal]) return false;
    boolean[] seen = new boolean[traces.length];
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    queue.add(start);
    seen[start] = true;
    while (!queue.isEmpty()) {
      int at = queue.remove();
      if (at == goal) return true;
      int x = at % SIZE, y = at / SIZE;
      if (x > 0) visit(at - 1, seen, queue);
      if (x + 1 < SIZE) visit(at + 1, seen, queue);
      if (y > 0) visit(at - SIZE, seen, queue);
      if (y + 1 < SIZE) visit(at + SIZE, seen, queue);
    }
    return false;
  }

  private void visit(int index, boolean[] seen, ArrayDeque<Integer> queue) {
    if (traces[index] && !seen[index]) {
      seen[index] = true;
      queue.add(index);
    }
  }

  private int pinCell(int pin) {
    return switch (pin) {
      case 0 -> 3;
      case 1 -> 4 * SIZE + 7;
      case 2 -> 7 * SIZE + 4;
      default -> 3 * SIZE;
    };
  }

  private Direction[] directions() { return new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}; }
  private void sync() { if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    long bits = 0;
    for (int i = 0; i < traces.length; i++) if (traces[i]) bits |= 1L << i;
    tag.putLong("Traces", bits);
    tag.putIntArray("PinModes", Arrays.stream(pinModes).mapToInt(Enum::ordinal).toArray());
    tag.putIntArray("Inputs", inputs);
    tag.putIntArray("Outputs", outputs);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    long bits = tag.getLong("Traces");
    for (int i = 0; i < traces.length; i++) traces[i] = (bits & (1L << i)) != 0;
    int[] modes = tag.getIntArray("PinModes");
    if (modes.length == 4) for (int i = 0; i < 4; i++) pinModes[i] = PinMode.values()[Math.max(0, Math.min(modes[i], PinMode.values().length - 1))];
    copyFour(tag.getIntArray("Inputs"), inputs);
    copyFour(tag.getIntArray("Outputs"), outputs);
  }

  private void copyFour(int[] source, int[] target) { if (source.length == 4) System.arraycopy(source, 0, target, 0, 4); }
  @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
  @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
  @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { if (packet.getTag() != null) load(packet.getTag()); }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.wafer");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WaferMenu(id, inv, this);
  }
}
