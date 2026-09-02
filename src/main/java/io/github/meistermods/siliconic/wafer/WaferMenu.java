package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.config.SiliconicConfig;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModMenus;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public class WaferMenu extends AbstractContainerMenu {
  public static final int INVENTORY_X = 153;
  public static final int MAIN_INVENTORY_Y = 50;
  public static final int HOTBAR_Y = 112;
  public static final int BUTTON_UNDO = 0;
  public static final int BUTTON_REDO = 1;
  private static final int HISTORY_LIMIT = 32;

  private final PrototypeWaferBlockEntity wafer;
  private long mutationTick = Long.MIN_VALUE;
  private int mutationsThisTick;
  private final Deque<ItemStack> undoHistory = new ArrayDeque<>();
  private final Deque<ItemStack> redoHistory = new ArrayDeque<>();
  private ItemStack historyBaseline;

  public WaferMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (PrototypeWaferBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public WaferMenu(int id, Inventory inventory, PrototypeWaferBlockEntity wafer) {
    super(ModMenus.WAFER.get(), id);
    this.wafer = wafer;
    historyBaseline = wafer.getWafer().copy();
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(
            new Slot(
                inventory,
                column + row * 9 + 9,
                INVENTORY_X + column * 18,
                MAIN_INVENTORY_Y + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
    addDataSlots(wafer.data());
  }

  public PrototypeWaferBlockEntity wafer() {
    return wafer;
  }

  public BlockPos position() {
    return wafer.getBlockPos();
  }

  public int energy() {
    return MenuDataSync.combine(wafer.data().get(0), wafer.data().get(1));
  }

  public int capacity() {
    return wafer.getEnergyCapacity();
  }

  public int operationCost() {
    return wafer.data().get(2);
  }

  public boolean canEditHere() {
    return wafer.data().get(3) != 0;
  }

  public boolean isCreativeAssembler() {
    return wafer.isCreativeAssembler();
  }

  public boolean tryBeginMutation(ServerPlayer player, BlockPos pos) {
    if (player.containerMenu != this
        || !position().equals(pos)
        || !stillValid(player)
        || !wafer.isEditable()
        || !wafer.canEditHere()) return false;
    long gameTime = player.level().getGameTime();
    if (mutationTick != gameTime) {
      mutationTick = gameTime;
      mutationsThisTick = 0;
    }
    if (mutationsThisTick >= SiliconicConfig.VALUES.waferMutationsPerTick.get()) return false;
    mutationsThisTick++;
    return true;
  }

  public void interactCell(ServerPlayer player, int cell, boolean rotate) {
    resetHistoryAfterExternalChange();
    ItemStack before = wafer.getWafer().copy();
    PrototypeWaferBlockEntity.CellType beforeType = wafer.getCellType(cell);
    wafer.interactCell(cell, rotate, player);
    if (ItemStack.matches(before, wafer.getWafer())) return;
    if (rotate && beforeType == wafer.getCellType(cell)) addUndoSnapshot(before);
    else clearHistory();
    historyBaseline = wafer.getWafer().copy();
  }

  public void cyclePinMode(int pin) {
    recordMutation(() -> wafer.cyclePinMode(pin));
  }

  public void completeWafer(String name) {
    recordMutation(() -> wafer.completeWafer(name));
  }

  private void recordMutation(Runnable mutation) {
    resetHistoryAfterExternalChange();
    ItemStack before = wafer.getWafer().copy();
    mutation.run();
    if (ItemStack.matches(before, wafer.getWafer())) return;
    addUndoSnapshot(before);
    historyBaseline = wafer.getWafer().copy();
  }

  private void addUndoSnapshot(ItemStack before) {
    undoHistory.addFirst(before);
    while (undoHistory.size() > HISTORY_LIMIT) undoHistory.removeLast();
    redoHistory.clear();
  }

  private void resetHistoryAfterExternalChange() {
    if (!ItemStack.matches(historyBaseline, wafer.getWafer())) {
      clearHistory();
      historyBaseline = wafer.getWafer().copy();
    }
  }

  private void clearHistory() {
    undoHistory.clear();
    redoHistory.clear();
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (!(player instanceof ServerPlayer serverPlayer)
        || (id != BUTTON_UNDO && id != BUTTON_REDO)
        || !tryBeginMutation(serverPlayer, position())) return false;
    if (!ItemStack.matches(historyBaseline, wafer.getWafer())) {
      clearHistory();
      historyBaseline = wafer.getWafer().copy();
      return false;
    }
    Deque<ItemStack> source = id == BUTTON_UNDO ? undoHistory : redoHistory;
    Deque<ItemStack> destination = id == BUTTON_UNDO ? redoHistory : undoHistory;
    if (source.isEmpty()) return false;
    ItemStack current = wafer.getWafer().copy();
    ItemStack snapshot = source.removeFirst();
    if (!wafer.restoreWaferSnapshot(snapshot)) {
      source.addFirst(snapshot);
      return false;
    }
    destination.addFirst(current);
    while (destination.size() > HISTORY_LIMIT) destination.removeLast();
    historyBaseline = wafer.getWafer().copy();
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack original = slot.getItem(), copy = original.copy();
    if (index < 27) {
      if (!moveItemStackTo(original, 27, 36, false)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(original, 0, 27, false)) return ItemStack.EMPTY;
    if (original.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !wafer.isRemoved() && player.distanceToSqr(wafer.getBlockPos().getCenter()) <= 64;
  }
}
