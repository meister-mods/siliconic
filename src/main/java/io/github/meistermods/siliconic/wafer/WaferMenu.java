package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModMenus;
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
  private static final int MAX_MUTATIONS_PER_TICK = 16;
  public static final int INVENTORY_X = 153;
  public static final int MAIN_INVENTORY_Y = 50;
  public static final int HOTBAR_Y = 112;

  private final PrototypeWaferBlockEntity wafer;
  private long mutationTick = Long.MIN_VALUE;
  private int mutationsThisTick;

  public WaferMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (PrototypeWaferBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public WaferMenu(int id, Inventory inventory, PrototypeWaferBlockEntity wafer) {
    super(ModMenus.WAFER.get(), id);
    this.wafer = wafer;
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
  }

  public PrototypeWaferBlockEntity wafer() {
    return wafer;
  }

  public BlockPos position() {
    return wafer.getBlockPos();
  }

  public boolean tryBeginMutation(ServerPlayer player, BlockPos pos) {
    if (player.containerMenu != this
        || !position().equals(pos)
        || !stillValid(player)
        || !wafer.isEditable()
        || !wafer.isInsideCleanroom()) return false;
    long gameTime = player.level().getGameTime();
    if (mutationTick != gameTime) {
      mutationTick = gameTime;
      mutationsThisTick = 0;
    }
    if (mutationsThisTick >= MAX_MUTATIONS_PER_TICK) return false;
    mutationsThisTick++;
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
