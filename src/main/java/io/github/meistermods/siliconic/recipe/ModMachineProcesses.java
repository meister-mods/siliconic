package io.github.meistermods.siliconic.recipe;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Single source of truth for powered machine processes. Both block entities and JEI read these
 * definitions, so changing a process here updates machine behavior and its JEI view together.
 */
@SuppressWarnings({"null"})
public final class ModMachineProcesses {
  private static final int SHAPELESS_SLOT = -1;
  private static final TagKey<Item> COPPER_NUGGETS =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "nuggets/copper"));

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

  public static boolean usesInputSlot(MachineKind machine, int relativeSlot) {
    for (MachineProcess process : all())
      for (ProcessInput input : process.inputs())
        if (process.machine() == machine && input.slot() == relativeSlot) return true;
    return false;
  }

  private static final class Holder {
    private static final List<MachineProcess> ALL = createProcesses();
  }

  private static List<MachineProcess> createProcesses() {
    return List.of(
        shapedWithByproducts(
            "silicon_arc_furnace/metallurgical_silicon",
            MachineKind.SILICON_ARC_FURNACE,
            ModItems.METALLURGICAL_SILICON.get(),
            4,
            List.of(new ItemStack(ModItems.SILICON_SLAG.get(), 2)),
            300,
            40,
            input(0, Items.QUARTZ, 4),
            input(1, Items.CHARCOAL, 4)),
        shapedWithByproducts(
            "chlorination_reactor/crude_trichlorosilane",
            MachineKind.CHLORINATION_REACTOR,
            ModItems.CRUDE_TRICHLOROSILANE.get(),
            1,
            List.of(new ItemStack(ModItems.HYDROGEN.get())),
            300,
            40,
            input(0, ModItems.METALLURGICAL_SILICON.get()),
            input(1, ModItems.HYDROGEN_CHLORIDE.get(), 3)),
        shaped(
            "distillation_tower/purified_trichlorosilane",
            MachineKind.DISTILLATION_TOWER,
            ModItems.PURIFIED_TRICHLOROSILANE.get(),
            1,
            400,
            40,
            input(0, ModItems.CRUDE_TRICHLOROSILANE.get())),
        shapedWithByproducts(
            "siemens_reactor/high_purity_silicon",
            MachineKind.SIEMENS_REACTOR,
            ModItems.HIGH_PURITY_SILICON.get(),
            1,
            List.of(
                new ItemStack(ModItems.SILICON_TETRACHLORIDE.get(), 3),
                new ItemStack(ModItems.HYDROGEN.get(), 2)),
            600,
            40,
            input(0, ModItems.PURIFIED_TRICHLOROSILANE.get(), 4)),
        shapedWithByproducts(
            "chemical_recycler/crude_trichlorosilane",
            MachineKind.CHEMICAL_RECYCLER,
            ModItems.CRUDE_TRICHLOROSILANE.get(),
            1,
            List.of(new ItemStack(ModItems.HYDROGEN_CHLORIDE.get())),
            300,
            40,
            input(0, ModItems.SILICON_TETRACHLORIDE.get()),
            input(1, ModItems.HYDROGEN.get())),
        shaped(
            "wafer_fabricator/ulsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.ULSI_WAFER.get(),
            1,
            600,
            60,
            input(0, ModItems.HIGH_PURITY_SILICON.get()),
            input(1, Items.ECHO_SHARD),
            input(2, ModItems.HIGH_PURITY_SILICON.get()),
            input(3, Items.ECHO_SHARD),
            input(4, ModItems.VLSI_WAFER.get()),
            input(5, Items.ECHO_SHARD),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, Items.ECHO_SHARD),
            input(8, ModItems.HIGH_PURITY_SILICON.get())),
        shaped(
            "wafer_fabricator/vlsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.VLSI_WAFER.get(),
            1,
            500,
            60,
            input(0, ModItems.HIGH_PURITY_SILICON.get()),
            input(1, Items.NETHERITE_SCRAP),
            input(2, ModItems.HIGH_PURITY_SILICON.get()),
            input(3, Items.NETHERITE_SCRAP),
            input(4, ModItems.LSI_WAFER.get()),
            input(5, Items.NETHERITE_SCRAP),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, Items.NETHERITE_SCRAP),
            input(8, ModItems.HIGH_PURITY_SILICON.get())),
        shaped(
            "wafer_fabricator/lsi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.LSI_WAFER.get(),
            1,
            400,
            60,
            input(0, ModItems.HIGH_PURITY_SILICON.get()),
            input(1, Items.DIAMOND),
            input(2, ModItems.HIGH_PURITY_SILICON.get()),
            input(3, Items.DIAMOND),
            input(4, ModItems.MSI_WAFER.get()),
            input(5, Items.DIAMOND),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, Items.DIAMOND),
            input(8, ModItems.HIGH_PURITY_SILICON.get())),
        shaped(
            "wafer_fabricator/msi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.MSI_WAFER.get(),
            1,
            300,
            60,
            input(0, ModItems.HIGH_PURITY_SILICON.get()),
            input(1, Items.GOLD_INGOT),
            input(2, ModItems.HIGH_PURITY_SILICON.get()),
            input(3, Items.GOLD_INGOT),
            input(4, ModItems.SSI_WAFER.get()),
            input(5, Items.GOLD_INGOT),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, Items.GOLD_INGOT),
            input(8, ModItems.HIGH_PURITY_SILICON.get())),
        shapeless(
            "wafer_fabricator/ssi_wafer",
            MachineKind.WAFER_FABRICATOR,
            ModItems.SSI_WAFER.get(),
            1,
            300,
            80,
            input(SHAPELESS_SLOT, ModItems.HIGH_PURITY_SILICON.get(), 2),
            input(SHAPELESS_SLOT, Items.IRON_INGOT),
            input(SHAPELESS_SLOT, Items.REDSTONE, 2)),
        shaped(
            "gate_assembler/xor_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.XOR_GATE.get(),
            1,
            200,
            40,
            input(0, COPPER_NUGGETS),
            input(1, Items.REDSTONE),
            input(2, COPPER_NUGGETS),
            input(3, Items.AMETHYST_SHARD),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, Items.AMETHYST_SHARD),
            input(6, COPPER_NUGGETS),
            input(7, Items.REDSTONE),
            input(8, COPPER_NUGGETS)),
        shaped(
            "gate_assembler/buffer_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.BUFFER_GATE.get(),
            1,
            160,
            40,
            input(1, COPPER_NUGGETS),
            input(3, Items.REDSTONE_TORCH),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, Items.REDSTONE_TORCH),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, COPPER_NUGGETS),
            input(8, Items.REDSTONE)),
        shaped(
            "gate_assembler/drop_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.DROP_GATE.get(),
            1,
            200,
            40,
            input(0, COPPER_NUGGETS),
            input(1, Items.REDSTONE),
            input(2, COPPER_NUGGETS),
            input(3, Items.REDSTONE),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, Items.COMPARATOR),
            input(6, COPPER_NUGGETS),
            input(7, Items.REDSTONE),
            input(8, COPPER_NUGGETS)),
        shaped(
            "gate_assembler/switch_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.SWITCH_GATE.get(),
            1,
            200,
            40,
            input(0, COPPER_NUGGETS),
            input(1, Items.REDSTONE),
            input(2, COPPER_NUGGETS),
            input(3, Items.REDSTONE_TORCH),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, Items.REDSTONE_TORCH),
            input(6, COPPER_NUGGETS),
            input(7, Items.REPEATER),
            input(8, COPPER_NUGGETS)),
        shaped(
            "gate_assembler/not_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.NOT_GATE.get(),
            1,
            160,
            40,
            input(1, Items.REDSTONE_TORCH),
            input(3, COPPER_NUGGETS),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, COPPER_NUGGETS),
            input(6, ModItems.HIGH_PURITY_SILICON.get()),
            input(7, COPPER_NUGGETS),
            input(8, Items.REDSTONE)),
        shaped(
            "gate_assembler/and_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.AND_GATE.get(),
            1,
            180,
            40,
            input(0, COPPER_NUGGETS),
            input(1, ModItems.HIGH_PURITY_SILICON.get()),
            input(2, COPPER_NUGGETS),
            input(3, Items.REDSTONE),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, Items.REDSTONE),
            input(6, COPPER_NUGGETS),
            input(7, Items.REDSTONE),
            input(8, COPPER_NUGGETS)),
        shaped(
            "gate_assembler/or_gate",
            MachineKind.GATE_ASSEMBLER,
            ModItems.OR_GATE.get(),
            1,
            180,
            40,
            input(0, Items.REDSTONE),
            input(1, COPPER_NUGGETS),
            input(2, Items.REDSTONE),
            input(3, COPPER_NUGGETS),
            input(4, ModItems.HIGH_PURITY_SILICON.get()),
            input(5, COPPER_NUGGETS),
            input(7, Items.REDSTONE),
            input(8, ModItems.HIGH_PURITY_SILICON.get())));
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

  private static MachineProcess shapedWithByproducts(
      String id,
      MachineKind machine,
      Item result,
      int resultCount,
      List<ItemStack> byproducts,
      int ticks,
      int energyPerTick,
      ProcessInput... inputs) {
    return process(
        id, machine, result, resultCount, byproducts, ticks, energyPerTick, true, inputs);
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
    return process(
        id, machine, result, resultCount, List.of(), ticks, energyPerTick, shaped, inputs);
  }

  private static MachineProcess process(
      String id,
      MachineKind machine,
      Item result,
      int resultCount,
      List<ItemStack> byproducts,
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
        byproducts,
        ticks,
        energyPerTick,
        shaped);
  }

  private static ProcessInput input(int slot, Item item) {
    return new ProcessInput(slot, Ingredient.of(item), 1);
  }

  private static ProcessInput input(int slot, Item item, int count) {
    return new ProcessInput(slot, Ingredient.of(item), count);
  }

  private static ProcessInput input(int slot, TagKey<Item> tag) {
    return new ProcessInput(slot, Ingredient.of(tag), 1);
  }

  private ModMachineProcesses() {}
}
