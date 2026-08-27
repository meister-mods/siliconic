package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
  private final int[] outputs = new int[4];

  public PrototypeWaferBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.PROTOTYPE_WAFER.get(), pos, state);
  }

  public boolean hasTrace(int index) {
    return index >= 0 && index < traces.length && traces[index];
  }

  public boolean[] copyTraces() {
    return traces.clone();
  }

  public void toggleTrace(int index) {
    if (index < 0 || index >= traces.length) return;
    traces[index] = !traces[index];
    setChanged();
    refreshSignals();
  }

  public void refreshSignals() {
    if (level == null || level.isClientSide) return;
    int[] inputs = new int[4];
    Direction[] directions = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
    for (int i = 0; i < 4; i++)
      inputs[i] =
          level.getSignal(worldPosition.relative(directions[i]), directions[i].getOpposite());
    for (int target = 0; target < 4; target++) {
      int value = 0;
      for (int source = 0; source < 4; source++)
        if (source != target && connected(source, target)) value = Math.max(value, inputs[source]);
      outputs[target] = value;
    }
    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    setChanged();
  }

  public int getOutput(Direction queriedFrom) {
    Direction[] directions = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
    for (int i = 0; i < 4; i++) if (directions[i] == queriedFrom) return outputs[i];
    return 0;
  }

  private boolean connected(int a, int b) {
    int start = pinCell(a), goal = pinCell(b);
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

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    long bits = 0;
    for (int i = 0; i < traces.length; i++) if (traces[i]) bits |= 1L << i;
    tag.putLong("Traces", bits);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    long bits = tag.getLong("Traces");
    for (int i = 0; i < traces.length; i++) traces[i] = (bits & (1L << i)) != 0;
  }

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
