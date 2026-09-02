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
    return find(blockEntity, side) != null;
  }

  @Nullable
  static IItemHandler find(BlockEntity blockEntity, Direction side) {
    if (blockEntity.isRemoved()) return null;

    IItemHandler capability =
        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    if (capability != null) return usable(capability);

    if (blockEntity instanceof WorldlyContainer sidedContainer)
      return usable(new SidedInvWrapper(sidedContainer, side));
    if (blockEntity instanceof Container container) return usable(new InvWrapper(container));
    return null;
  }

  @Nullable
  private static IItemHandler usable(IItemHandler handler) {
    return handler.getSlots() > 0 ? handler : null;
  }
}
