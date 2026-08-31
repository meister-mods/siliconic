package io.github.meistermods.siliconic.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

@SuppressWarnings({"null"})
public record MachineProcess(
    ResourceLocation id,
    MachineKind machine,
    List<ProcessInput> inputs,
    Item resultItem,
    int resultCount,
    List<ItemStack> byproducts,
    int ticks,
    int energyPerTick,
    boolean shaped) {
  public MachineProcess {
    inputs = List.copyOf(inputs);
    byproducts = byproducts.stream().map(ItemStack::copy).toList();
    if (resultCount < 1)
      throw new IllegalArgumentException("Process output count must be positive");
    if (ticks < 1) throw new IllegalArgumentException("Process duration must be positive");
    if (energyPerTick < 1)
      throw new IllegalArgumentException("Process energy use must be positive");
  }

  public ItemStack result() {
    return new ItemStack(resultItem, resultCount);
  }

  /** Returns fresh copies of every primary and secondary output produced by one batch. */
  public List<ItemStack> outputCopies() {
    List<ItemStack> outputs = new ArrayList<>(1 + byproducts.size());
    outputs.add(result());
    byproducts.forEach(output -> outputs.add(output.copy()));
    return outputs;
  }

  public int totalEnergy() {
    return ticks * energyPerTick;
  }

  public boolean matches(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (shaped) {
      for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++) {
        ProcessInput expected = inputAt(relativeSlot);
        ItemStack actual = inventory.getStackInSlot(inputStart + relativeSlot);
        if (expected == null) {
          if (!actual.isEmpty()) return false;
        } else if (!expected.matches(actual) || actual.getCount() < expected.count()) {
          return false;
        }
      }
      return true;
    }

    for (ProcessInput input : inputs) {
      int available = 0;
      for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (input.matches(stack)) available += stack.getCount();
      }
      if (available < input.count()) return false;
    }
    for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (!stack.isEmpty() && inputs.stream().noneMatch(input -> input.matches(stack)))
        return false;
    }
    return true;
  }

  public boolean accepts(int relativeSlot, ItemStack stack) {
    if (shaped) {
      ProcessInput input = inputAt(relativeSlot);
      return input != null && input.matches(stack);
    }
    return inputs.stream().anyMatch(input -> input.matches(stack));
  }

  public void consume(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (shaped) {
      for (ProcessInput input : inputs)
        inventory.extractItem(inputStart + input.slot(), input.count(), false);
      return;
    }
    for (ProcessInput input : inputs) {
      int remaining = input.count();
      for (int slot = inputStart; slot < inputStart + inputSlots && remaining > 0; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!input.matches(stack)) continue;
        int extracted = Math.min(remaining, stack.getCount());
        inventory.extractItem(slot, extracted, false);
        remaining -= extracted;
      }
    }
  }

  private ProcessInput inputAt(int slot) {
    for (ProcessInput input : inputs) if (input.slot() == slot) return input;
    return null;
  }
}
