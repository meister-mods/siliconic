package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public class WaferMenu extends AbstractContainerMenu {
  private final PrototypeWaferBlockEntity wafer;

  public WaferMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (PrototypeWaferBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public WaferMenu(int id, Inventory inventory, PrototypeWaferBlockEntity wafer) {
    super(ModMenus.WAFER.get(), id);
    this.wafer = wafer;
  }

  public PrototypeWaferBlockEntity wafer() {
    return wafer;
  }

  public BlockPos position() {
    return wafer.getBlockPos();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    return !wafer.isRemoved() && player.distanceToSqr(wafer.getBlockPos().getCenter()) <= 64;
  }
}
