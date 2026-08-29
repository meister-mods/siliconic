package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.wafer.WaferItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Selects contaminated process outputs from the cleanliness at the machine's position. */
@SuppressWarnings({"null"})
public final class CleanroomContamination {
  public static final int CHANCE_PER_MISSING_PERCENT = 2;

  public static int contaminationChance(int cleanliness) {
    int clampedCleanliness = Math.max(0, Math.min(100, cleanliness));
    return Math.min(100, (100 - clampedCleanliness) * CHANCE_PER_MISSING_PERCENT);
  }

  public static ItemStack processResult(Level level, BlockPos machinePos, ItemStack intended) {
    Item contaminatedItem = contaminatedItemFor(intended);
    if (contaminatedItem == null) return intended;
    int cleanliness = CleanroomOccupancy.cleanlinessAtMachine(level, machinePos);
    int chance = contaminationChance(cleanliness);
    return chance > 0 && level.random.nextInt(100) < chance
        ? new ItemStack(contaminatedItem, intended.getCount())
        : intended;
  }

  @Nullable
  private static Item contaminatedItemFor(ItemStack intended) {
    if (intended.is(ModItems.CRUDE_SILICON.get()))
      return ModItems.CONTAMINATED_CRUDE_SILICON.get();
    if (intended.is(ModItems.PURE_SILICON.get()))
      return ModItems.CONTAMINATED_PURE_SILICON.get();
    if (intended.getItem() instanceof WaferItem) return ModItems.CONTAMINATED_WAFER.get();
    if (intended.is(ModItems.NOT_GATE.get())
        || intended.is(ModItems.AND_GATE.get())
        || intended.is(ModItems.OR_GATE.get())
        || intended.is(ModItems.XOR_GATE.get())
        || intended.is(ModItems.BUFFER_GATE.get())) return ModItems.CONTAMINATED_GATE.get();
    return null;
  }

  private CleanroomContamination() {}
}
