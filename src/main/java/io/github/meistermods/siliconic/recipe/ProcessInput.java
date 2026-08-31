package io.github.meistermods.siliconic.recipe;

import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

@SuppressWarnings({"null"})
public record ProcessInput(int slot, Ingredient ingredient, int count, Use use) {
  public enum Use {
    CONSUME,
    DAMAGE,
    CATALYST
  }

  public ProcessInput {
    if (slot < -1) throw new IllegalArgumentException("Process input slot must be -1 or greater");
    if (ingredient.isEmpty()) throw new IllegalArgumentException("Process input must not be empty");
    if (count < 1) throw new IllegalArgumentException("Process input count must be positive");
    if (use == null) throw new IllegalArgumentException("Process input use must not be null");
  }

  public ProcessInput(int slot, Ingredient ingredient, int count) {
    this(slot, ingredient, count, Use.CONSUME);
  }

  public boolean matches(ItemStack candidate) {
    return ingredient.test(candidate);
  }

  public List<ItemStack> stacks() {
    return Arrays.stream(ingredient.getItems())
        .map(
            match -> {
              ItemStack display = match.copy();
              display.setCount(count);
              return display;
            })
        .toList();
  }
}
