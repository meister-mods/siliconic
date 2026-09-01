package io.github.meistermods.siliconic.logistics;

import net.minecraftforge.items.IItemHandler;

/** Exposes a machine's complete inventory only to a logistics controller in forced mode. */
public interface LogisticsInventoryAccess {
  IItemHandler logisticsInventory();
}
