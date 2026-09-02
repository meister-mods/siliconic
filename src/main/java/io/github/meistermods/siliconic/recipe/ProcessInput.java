package io.github.meistermods.siliconic.recipe;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    Objects.requireNonNull(ingredient, "Process input ingredient must not be null");
    Objects.requireNonNull(use, "Process input use must not be null");
    if (slot < -1) throw new IllegalArgumentException("Process input slot must be -1 or greater");
    if (ingredient.isEmpty()) throw new IllegalArgumentException("Process input must not be empty");
    if (count < 1) throw new IllegalArgumentException("Process input count must be positive");
  }

  public ProcessInput(int slot, Ingredient ingredient, int count) {
    this(slot, ingredient, count, Use.CONSUME);
  }

  public boolean matches(ItemStack candidate) {
    return ingredient.test(candidate) && (use != Use.DAMAGE || candidate.isDamageableItem());
  }

  /** Number of items that must be present. For tools, {@link #count} is durability damage. */
  public int requiredItems() {
    return use == Use.DAMAGE ? 1 : count;
  }

  public List<ItemStack> stacks() {
    return Arrays.stream(ingredient.getItems())
        .map(
            match -> {
              ItemStack display = match.copy();
              display.setCount(requiredItems());
              return display;
            })
        .toList();
  }
}
