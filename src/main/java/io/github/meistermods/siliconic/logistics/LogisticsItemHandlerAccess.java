package io.github.meistermods.siliconic.logistics;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;

/** Resolves a pipe-facing inventory without bypassing the inventory's normal sided rules. */
@SuppressWarnings("null")
final class LogisticsItemHandlerAccess {
  private LogisticsItemHandlerAccess() {}

  static boolean isPresent(BlockEntity blockEntity, Direction side) {
    return !blockEntity.isRemoved()
        && (find(blockEntity, side) != null
            || blockEntity instanceof LogisticsInventoryAccess access
                && usable(access.logisticsInventory()) != null);
  }

  @Nullable
  static IItemHandler find(BlockEntity blockEntity, Direction side) {
    if (blockEntity.isRemoved()) return null;

    IItemHandler capability =
        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    if (capability != null) return usable(capability);

    // A publisher can deliberately deny this side. Raw Container access is only a compatibility
    // fallback for inventories without any capability publisher, never a bypass of a denied face.
    if (!(blockEntity instanceof Container)
        || blockEntity instanceof LogisticsInventoryAccess
        || hasCapabilityPublisher(blockEntity, side)) return null;

    if (blockEntity instanceof WorldlyContainer sidedContainer)
      return usable(new SidedInvWrapper(sidedContainer, side));
    if (blockEntity instanceof Container container) return usable(new InvWrapper(container));
    return null;
  }

  private static boolean hasCapabilityPublisher(BlockEntity blockEntity, Direction deniedSide) {
    if (blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) return true;
    for (Direction side : Direction.values())
      if (side != deniedSide
          && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent())
        return true;
    return false;
  }

  @Nullable
  private static IItemHandler usable(IItemHandler handler) {
    return handler.getSlots() > 0 ? handler : null;
  }
}
