package io.github.meistermods.siliconic.machine;

import java.util.function.IntPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

@SuppressWarnings({"null"})
public final class FilteredItemHandler implements IItemHandler {
  private final IItemHandler delegate;
  private final IntPredicate insertable;
  private final IntPredicate extractable;

  public FilteredItemHandler(
      IItemHandler delegate, IntPredicate insertable, IntPredicate extractable) {
    this.delegate = delegate;
    this.insertable = insertable;
    this.extractable = extractable;
  }

  @Override
  public int getSlots() {
    return delegate.getSlots();
  }

  @Override
  public ItemStack getStackInSlot(int slot) {
    return delegate.getStackInSlot(slot);
  }

  @Override
  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    return insertable.test(slot) ? delegate.insertItem(slot, stack, simulate) : stack;
  }

  @Override
  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    return extractable.test(slot)
        ? delegate.extractItem(slot, amount, simulate)
        : ItemStack.EMPTY;
  }

  @Override
  public int getSlotLimit(int slot) {
    return delegate.getSlotLimit(slot);
  }

  @Override
  public boolean isItemValid(int slot, ItemStack stack) {
    return insertable.test(slot) && delegate.isItemValid(slot, stack);
  }
}
