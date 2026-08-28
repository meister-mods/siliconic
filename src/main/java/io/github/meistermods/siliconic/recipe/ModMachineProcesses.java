package io.github.meistermods.siliconic.recipe;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Single source of truth for powered machine processes. Both block entities and JEI read these
 * definitions, so changing a process here updates machine behavior and its JEI view together.
 */
@SuppressWarnings({"null"})
public final class ModMachineProcesses {
  private static final int SHAPELESS_SLOT = -1;

  public static List<MachineProcess> all() {
    return Holder.ALL;
  }

  public static List<MachineProcess> forMachine(MachineKind machine) {
    return all().stream().filter(process -> process.machine() == machine).toList();
  }

  public static MachineProcess primary(MachineKind machine) {
    for (MachineProcess process : all()) if (process.machine() == machine) return process;
    throw new IllegalArgumentException("No processes registered for " + machine);
  }

  @Nullable
  public static MachineProcess findMatching(
      MachineKind machine, ItemStackHandler inventory, int inputStart, int inputSlots) {
    for (MachineProcess process : all())
      if (process.machine() == machine && process.matches(inventory, inputStart, inputSlots))
        return process;
    return null;
  }

  public static boolean accepts(MachineKind machine, int relativeSlot, ItemStack stack) {
    for (MachineProcess process : all())
      if (process.machine() == machine && process.accepts(relativeSlot, stack)) return true;
    return false;
  }

  private static final class Holder {
    private static final List<MachineProcess> ALL = createProcesses();
  }

  private static List<MachineProcess> createProcesses() {
    return List.of(
        shaped(
            "silicon_arc_furnace/crude_silicon",
            MachineKind.SILICON_ARC_FURNACE,
            ModItems.CRUDE_SILICON.get(),
            2,
            200,
            40,
            input(0, Items.QUARTZ),
            input(1, Items.CHARCOAL)),
        shaped(
            "silicon_purifier/pure_silicon",
            MachineKind.SILICON_PURIFIER,
            ModItems.PURE_SILICON.get(),
            1,
            300,
            40,
            input(0, ModItems.CRUDE_SILICON.get())),
        shaped(
            "wafer_fabricator/ulsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.ULSI_WAFER.get(),
            1,
            600,
            60,
            input(0, ModItems.PURE_SILICON.get()),
            input(1, Items.ECHO_SHARD),
            input(2, ModItems.PURE_SILICON.get()),
            input(3, Items.ECHO_SHARD),
            input(4, ModItems.VLSI_WAFER.get()),
            input(5, Items.ECHO_SHARD),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, Items.ECHO_SHARD),
            input(8, ModItems.PURE_SILICON.get())),
        shaped(
            "wafer_fabricator/vlsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.VLSI_WAFER.get(),
            1,
            500,
            60,
            input(0, ModItems.PURE_SILICON.get()),
            input(1, Items.NETHERITE_SCRAP),
            input(2, ModItems.PURE_SILICON.get()),
            input(3, Items.NETHERITE_SCRAP),
            input(4, ModItems.LSI_WAFER.get()),
            input(5, Items.NETHERITE_SCRAP),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, Items.NETHERITE_SCRAP),
            input(8, ModItems.PURE_SILICON.get())),
        shaped(
            "wafer_fabricator/lsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.LSI_WAFER.get(),
            1,
            400,
            60,
            input(0, ModItems.PURE_SILICON.get()),
            input(1, Items.DIAMOND),
            input(2, ModItems.PURE_SILICON.get()),
            input(3, Items.DIAMOND),
            input(4, ModItems.MSI_WAFER.get()),
            input(5, Items.DIAMOND),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, Items.DIAMOND),
            input(8, ModItems.PURE_SILICON.get())),
        shaped(
            "wafer_fabricator/msi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.MSI_WAFER.get(),
            1,
            300,
            60,
            input(0, ModItems.PURE_SILICON.get()),
            input(1, Items.GOLD_INGOT),
            input(2, ModItems.PURE_SILICON.get()),
            input(3, Items.GOLD_INGOT),
            input(4, ModItems.SSI_WAFER.get()),
            input(5, Items.GOLD_INGOT),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, Items.GOLD_INGOT),
            input(8, ModItems.PURE_SILICON.get())),
        shapeless(
            "wafer_fabricator/ssi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.SSI_WAFER.get(),
            1,
            300,
            80,
            input(SHAPELESS_SLOT, ModItems.PURE_SILICON.get(), 2),
            input(SHAPELESS_SLOT, Items.IRON_INGOT),
            input(SHAPELESS_SLOT, Items.REDSTONE, 2)),
        shaped(
            "gate_assembler/xor_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.XOR_GATE.get(),
            1,
            200,
            40,
            input(0, ModItems.COPPER_NUGGET.get()),
            input(1, Items.REDSTONE),
            input(2, ModItems.COPPER_NUGGET.get()),
            input(3, Items.AMETHYST_SHARD),
            input(4, ModItems.PURE_SILICON.get()),
            input(5, Items.AMETHYST_SHARD),
            input(6, ModItems.COPPER_NUGGET.get()),
            input(7, Items.REDSTONE),
            input(8, ModItems.COPPER_NUGGET.get())),
        shaped(
            "gate_assembler/buffer_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.BUFFER_GATE.get(),
            1,
            160,
            40,
            input(1, ModItems.COPPER_NUGGET.get()),
            input(3, Items.REDSTONE_TORCH),
            input(4, Items.QUARTZ),
            input(5, Items.REDSTONE_TORCH),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, ModItems.COPPER_NUGGET.get()),
            input(8, Items.REDSTONE)),
        shaped(
            "gate_assembler/not_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.NOT_GATE.get(),
            1,
            160,
            40,
            input(1, Items.REDSTONE_TORCH),
            input(3, ModItems.COPPER_NUGGET.get()),
            input(4, Items.QUARTZ),
            input(5, ModItems.COPPER_NUGGET.get()),
            input(6, ModItems.PURE_SILICON.get()),
            input(7, ModItems.COPPER_NUGGET.get()),
            input(8, Items.REDSTONE)),
        shaped(
            "gate_assembler/and_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.AND_GATE.get(),
            1,
            180,
            40,
            input(0, ModItems.COPPER_NUGGET.get()),
            input(1, ModItems.PURE_SILICON.get()),
            input(2, ModItems.COPPER_NUGGET.get()),
            input(3, Items.REDSTONE),
            input(4, Items.QUARTZ),
            input(5, Items.REDSTONE),
            input(6, ModItems.COPPER_NUGGET.get()),
            input(7, Items.REDSTONE),
            input(8, ModItems.COPPER_NUGGET.get())),
        shaped(
            "gate_assembler/or_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.OR_GATE.get(),
            1,
            180,
            40,
            input(0, Items.REDSTONE),
            input(1, ModItems.COPPER_NUGGET.get()),
            input(2, Items.REDSTONE),
            input(3, ModItems.COPPER_NUGGET.get()),
            input(4, Items.QUARTZ),
            input(5, ModItems.COPPER_NUGGET.get()),
            input(7, Items.REDSTONE),
            input(8, ModItems.PURE_SILICON.get())));
  }

  private static MachineProcess shaped(
      String id,
      MachineKind machine,
      Item result,
      int resultCount,
      int ticks,
      int energyPerTick,
      ProcessInput... inputs) {
    return process(id, machine, result, resultCount, ticks, energyPerTick, true, inputs);
  }

  private static MachineProcess shapeless(
      String id,
      MachineKind machine,
      Item result,
      int resultCount,
      int ticks,
      int energyPerTick,
      ProcessInput... inputs) {
    return process(id, machine, result, resultCount, ticks, energyPerTick, false, inputs);
  }

  private static MachineProcess process(
      String id,
      MachineKind machine,
      Item result,
      int resultCount,
      int ticks,
      int energyPerTick,
      boolean shaped,
      ProcessInput... inputs) {
    return new MachineProcess(
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, id),
        machine,
        List.of(inputs),
        result,
        resultCount,
        ticks,
        energyPerTick,
        shaped);
  }

  private static ProcessInput input(int slot, Item item) {
    return new ProcessInput(slot, item, 1);
  }

  private static ProcessInput input(int slot, Item item, int count) {
    return new ProcessInput(slot, item, count);
  }

  private ModMachineProcesses() {}
}
