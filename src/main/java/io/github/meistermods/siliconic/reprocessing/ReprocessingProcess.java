package io.github.meistermods.siliconic.reprocessing;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Defines deterministic material recovery from contaminated process waste. */
@SuppressWarnings({"null"})
public record ReprocessingProcess(
    ResourceLocation id,
    Item input,
    int inputCount,
    List<ItemStack> outputs,
    int ticks,
    int energyPerTick) {
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

  public static List<ReprocessingProcess> all() {
    return Holder.PROCESSES;
  }

  public static boolean accepts(ItemStack stack) {
    for (ReprocessingProcess process : Holder.PROCESSES) if (stack.is(process.input())) return true;
    return false;
  }

  public List<ItemStack> outputCopies() {
    return outputs.stream().map(ItemStack::copy).toList();
  }

  public int totalEnergy() {
    return ticks * energyPerTick;
  }

  private static final class Holder {
    private static final List<ReprocessingProcess> PROCESSES =
        List.of(
            process(
                "silicon_slag",
                ModItems.SILICON_SLAG.get(),
                4,
                List.of(new ItemStack(Items.QUARTZ), new ItemStack(Items.CHARCOAL)),
                240,
                40),
            process(
                "contaminated_wafer",
                ModItems.CONTAMINATED_WAFER.get(),
                1,
                List.of(
                    new ItemStack(ModItems.HIGH_PURITY_SILICON.get(), 2),
                    new ItemStack(Items.REDSTONE)),
                300,
                60),
            process(
                "contaminated_gate",
                ModItems.CONTAMINATED_GATE.get(),
                1,
                List.of(
                    new ItemStack(ModItems.HIGH_PURITY_SILICON.get()),
                    new ItemStack(Items.REDSTONE),
                    new ItemStack(ModItems.COPPER_NUGGET.get(), 2)),
                240,
                50));
  }

  private static ReprocessingProcess process(
      String id,
      Item input,
      int inputCount,
      List<ItemStack> outputs,
      int ticks,
      int energyPerTick) {
    return new ReprocessingProcess(
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "reprocessor/" + id),
        input,
        inputCount,
        outputs,
        ticks,
        energyPerTick);
  }
}
