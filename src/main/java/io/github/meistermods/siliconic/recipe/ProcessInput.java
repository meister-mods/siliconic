package io.github.meistermods.siliconic.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

@SuppressWarnings({"null"})
public record ProcessInput(int slot, Ingredient ingredient, int count) {
  public ProcessInput {
    if (slot < -1) throw new IllegalArgumentException("Process input slot must be -1 or greater");
    if (ingredient.isEmpty()) throw new IllegalArgumentException("Process input must not be empty");
    if (count < 1) throw new IllegalArgumentException("Process input count must be positive");
  }

  public boolean matches(ItemStack candidate) {
    return ingredient.test(candidate);
  }

  public ItemStack stack() {
    ItemStack[] matches = ingredient.getItems();
    if (matches.length == 0) return ItemStack.EMPTY;
    ItemStack display = matches[0].copy();
    display.setCount(count);
    return display;
  }
}
