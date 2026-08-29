package io.github.meistermods.siliconic.reprocessing;

import io.github.meistermods.siliconic.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Defines deterministic material recovery from contaminated process waste. */
@SuppressWarnings({"null"})
public record ReprocessingProcess(
    Item input, int inputCount, List<ItemStack> outputs, int ticks, int energyPerTick) {
  public ReprocessingProcess {
    if (inputCount < 1) throw new IllegalArgumentException("Input count must be positive");
    if (outputs.isEmpty()) throw new IllegalArgumentException("At least one output is required");
    outputs = outputs.stream().map(ItemStack::copy).toList();
    if (ticks < 1) throw new IllegalArgumentException("Process duration must be positive");
    if (energyPerTick < 1) throw new IllegalArgumentException("Energy use must be positive");
  }

  @Nullable
  public static ReprocessingProcess find(
      ItemStackHandler inventory, int inputStart, int inputSlots) {
    for (ReprocessingProcess process : Holder.PROCESSES) {
      int available = 0;
      for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.is(process.input())) available += stack.getCount();
      }
      if (available >= process.inputCount()) return process;
    }
    return null;
  }

  public static boolean accepts(ItemStack stack) {
    for (ReprocessingProcess process : Holder.PROCESSES) if (stack.is(process.input())) return true;
    return false;
  }

  public List<ItemStack> outputCopies() {
    return outputs.stream().map(ItemStack::copy).toList();
  }

  private static final class Holder {
    private static final List<ReprocessingProcess> PROCESSES =
        List.of(
            new ReprocessingProcess(
                ModItems.CONTAMINATED_CRUDE_SILICON.get(),
                2,
                List.of(new ItemStack(Items.QUARTZ)),
                200,
                40),
            new ReprocessingProcess(
                ModItems.CONTAMINATED_PURE_SILICON.get(),
                1,
                List.of(new ItemStack(ModItems.CRUDE_SILICON.get())),
                200,
                40),
            new ReprocessingProcess(
                ModItems.CONTAMINATED_WAFER.get(),
                1,
                List.of(
                    new ItemStack(ModItems.PURE_SILICON.get(), 2), new ItemStack(Items.REDSTONE)),
                300,
                60),
            new ReprocessingProcess(
                ModItems.CONTAMINATED_GATE.get(),
                1,
                List.of(
                    new ItemStack(ModItems.PURE_SILICON.get()),
                    new ItemStack(Items.REDSTONE),
                    new ItemStack(ModItems.COPPER_NUGGET.get(), 2)),
                240,
                50));
  }
}
