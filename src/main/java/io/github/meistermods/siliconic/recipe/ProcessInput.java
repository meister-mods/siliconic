package io.github.meistermods.siliconic.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public record ProcessInput(int slot, Item item, int count) {
  public ProcessInput {
    if (slot < -1) throw new IllegalArgumentException("Process input slot must be -1 or greater");
    if (count < 1) throw new IllegalArgumentException("Process input count must be positive");
  }

  public ItemStack stack() {
    return new ItemStack(item, count);
  }
}
