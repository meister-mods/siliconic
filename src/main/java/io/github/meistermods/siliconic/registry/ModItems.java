package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomSuitItem;
import io.github.meistermods.siliconic.wafer.WaferItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModItems {
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, Siliconic.MOD_ID);
  public static final RegistryObject<Item> METALLURGICAL_SILICON =
      ITEMS.register("metallurgical_silicon", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> CRUDE_TRICHLOROSILANE =
      ITEMS.register("crude_trichlorosilane", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> PURIFIED_TRICHLOROSILANE =
      ITEMS.register("purified_trichlorosilane", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SILICON_TETRACHLORIDE =
      ITEMS.register("silicon_tetrachloride", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SALT =
      ITEMS.register("salt", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SALT_DIRT =
      ITEMS.register(
          "salt_dirt", () -> new BlockItem(ModBlocks.SALT_DIRT.get(), new Item.Properties()));
  public static final RegistryObject<Item> HYDROGEN_CHLORIDE =
      ITEMS.register("hydrogen_chloride", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> HYDROGEN =
      ITEMS.register("hydrogen", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> HIGH_PURITY_SILICON =
      ITEMS.register("high_purity_silicon", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SILICON_SLAG =
      ITEMS.register("silicon_slag", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> CARBON_ELECTRODE =
      ITEMS.register("carbon_electrode", () -> new Item(new Item.Properties().durability(32)));
  public static final RegistryObject<Item> QUARTZ_DEPOSITION_FILAMENT =
      ITEMS.register("quartz_deposition_filament", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> PARTIAL_POLYSILICON_ROD =
      ITEMS.register("partial_polysilicon_rod", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> DISTILLATION_RESIDUE =
      ITEMS.register("distillation_residue", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> CONTAMINATED_WAFER =
      ITEMS.register("contaminated_wafer", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> CONTAMINATED_GATE =
      ITEMS.register("contaminated_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SSI_WAFER =
      ITEMS.register("ssi_wafer", () -> new WaferItem(new Item.Properties()));
  public static final RegistryObject<Item> MSI_WAFER =
      ITEMS.register("msi_wafer", () -> new WaferItem(new Item.Properties()));
  public static final RegistryObject<Item> LSI_WAFER =
      ITEMS.register("lsi_wafer", () -> new WaferItem(new Item.Properties()));
  public static final RegistryObject<Item> VLSI_WAFER =
      ITEMS.register("vlsi_wafer", () -> new WaferItem(new Item.Properties()));
  public static final RegistryObject<Item> ULSI_WAFER =
      ITEMS.register("ulsi_wafer", () -> new WaferItem(new Item.Properties()));
  public static final RegistryObject<Item> SILVER_INGOT =
      ITEMS.register("silver_ingot", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SILVER_NUGGET =
      ITEMS.register("silver_nugget", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> LEAD_INGOT =
      ITEMS.register("lead_ingot", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> LEAD_NUGGET =
      ITEMS.register("lead_nugget", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> RAW_SILVER =
      ITEMS.register("raw_silver", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> RAW_LEAD =
      ITEMS.register("raw_lead", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> COPPER_NUGGET =
      ITEMS.register("copper_nugget", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> ANTISTATIC_FABRIC =
      ITEMS.register("antistatic_fabric", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> NOT_GATE =
      ITEMS.register("not_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> AND_GATE =
      ITEMS.register("and_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> OR_GATE =
      ITEMS.register("or_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> XOR_GATE =
      ITEMS.register("xor_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> BUFFER_GATE =
      ITEMS.register("buffer_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> DROP_GATE =
      ITEMS.register("drop_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SWITCH_GATE =
      ITEMS.register("switch_gate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> CLEANROOM_HOOD =
      ITEMS.register(
          "cleanroom_hood",
          () -> new CleanroomSuitItem(ArmorItem.Type.HELMET, new Item.Properties()));
  public static final RegistryObject<Item> CLEANROOM_CHESTPLATE =
      ITEMS.register(
          "cleanroom_chestplate",
          () -> new CleanroomSuitItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));
  public static final RegistryObject<Item> CLEANROOM_LEGGINGS =
      ITEMS.register(
          "cleanroom_leggings",
          () -> new CleanroomSuitItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));
  public static final RegistryObject<Item> CLEANROOM_BOOTS =
      ITEMS.register(
          "cleanroom_boots",
          () -> new CleanroomSuitItem(ArmorItem.Type.BOOTS, new Item.Properties()));
  public static final RegistryObject<Item> WAFER_ASSEMBLER =
      ITEMS.register(
          "wafer_assembler",
          () -> new BlockItem(ModBlocks.WAFER_ASSEMBLER.get(), new Item.Properties()));
  public static final RegistryObject<Item> CREATIVE_WAFER_ASSEMBLER =
      ITEMS.register(
          "creative_wafer_assembler",
          () -> new BlockItem(ModBlocks.CREATIVE_WAFER_ASSEMBLER.get(), new Item.Properties()));
  public static final RegistryObject<Item> WAFER_GUARD =
      ITEMS.register(
          "wafer_guard", () -> new BlockItem(ModBlocks.WAFER_GUARD.get(), new Item.Properties()));
  public static final RegistryObject<Item> CREATIVE_WAFER_GUARD =
      ITEMS.register(
          "creative_wafer_guard",
          () -> new BlockItem(ModBlocks.CREATIVE_WAFER_GUARD.get(), new Item.Properties()));
  public static final RegistryObject<Item> WAFER_INVERTER =
      ITEMS.register(
          "wafer_inverter",
          () -> new BlockItem(ModBlocks.WAFER_INVERTER.get(), new Item.Properties()));
  public static final RegistryObject<Item> WAFER_DUPLICATOR =
      ITEMS.register(
          "wafer_duplicator",
          () -> new BlockItem(ModBlocks.WAFER_DUPLICATOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> SILICON_ARC_FURNACE =
      ITEMS.register(
          "silicon_arc_furnace",
          () -> new BlockItem(ModBlocks.SILICON_ARC_FURNACE.get(), new Item.Properties()));
  public static final RegistryObject<Item> CHLORINATION_REACTOR =
      ITEMS.register(
          "chlorination_reactor",
          () -> new BlockItem(ModBlocks.CHLORINATION_REACTOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> DISTILLATION_TOWER =
      ITEMS.register(
          "distillation_tower",
          () -> new BlockItem(ModBlocks.DISTILLATION_TOWER.get(), new Item.Properties()));
  public static final RegistryObject<Item> SIEMENS_REACTOR =
      ITEMS.register(
          "siemens_reactor",
          () -> new BlockItem(ModBlocks.SIEMENS_REACTOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> CHEMICAL_RECYCLER =
      ITEMS.register(
          "chemical_recycler",
          () -> new BlockItem(ModBlocks.CHEMICAL_RECYCLER.get(), new Item.Properties()));
  public static final RegistryObject<Item> WAFER_FABRICATOR =
      ITEMS.register(
          "wafer_fabricator",
          () -> new BlockItem(ModBlocks.WAFER_FABRICATOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> GATE_ASSEMBLER =
      ITEMS.register(
          "gate_assembler",
          () -> new BlockItem(ModBlocks.GATE_ASSEMBLER.get(), new Item.Properties()));
  public static final RegistryObject<Item> REPROCESSOR =
      ITEMS.register(
          "reprocessor", () -> new BlockItem(ModBlocks.REPROCESSOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> SILVER_ORE =
      ITEMS.register(
          "silver_ore", () -> new BlockItem(ModBlocks.SILVER_ORE.get(), new Item.Properties()));
  public static final RegistryObject<Item> DEEPSLATE_SILVER_ORE =
      ITEMS.register(
          "deepslate_silver_ore",
          () -> new BlockItem(ModBlocks.DEEPSLATE_SILVER_ORE.get(), new Item.Properties()));
  public static final RegistryObject<Item> LEAD_ORE =
      ITEMS.register(
          "lead_ore", () -> new BlockItem(ModBlocks.LEAD_ORE.get(), new Item.Properties()));
  public static final RegistryObject<Item> DEEPSLATE_LEAD_ORE =
      ITEMS.register(
          "deepslate_lead_ore",
          () -> new BlockItem(ModBlocks.DEEPSLATE_LEAD_ORE.get(), new Item.Properties()));
  public static final RegistryObject<Item> SILVER_BLOCK =
      ITEMS.register(
          "silver_block", () -> new BlockItem(ModBlocks.SILVER_BLOCK.get(), new Item.Properties()));
  public static final RegistryObject<Item> LEAD_BLOCK =
      ITEMS.register(
          "lead_block", () -> new BlockItem(ModBlocks.LEAD_BLOCK.get(), new Item.Properties()));
  public static final RegistryObject<Item> COAL_GENERATOR =
      ITEMS.register(
          "coal_generator",
          () -> new BlockItem(ModBlocks.COAL_GENERATOR.get(), new Item.Properties()));
  public static final RegistryObject<Item> POWER_CABLE =
      ITEMS.register(
          "power_cable", () -> new BlockItem(ModBlocks.POWER_CABLE.get(), new Item.Properties()));
  public static final RegistryObject<Item> REDSTONE_CLOCK =
      ITEMS.register(
          "redstone_clock",
          () -> new BlockItem(ModBlocks.REDSTONE_CLOCK.get(), new Item.Properties()));
  public static final RegistryObject<Item> CONDITIONER =
      ITEMS.register(
          "conditioner", () -> new BlockItem(ModBlocks.CONDITIONER.get(), new Item.Properties()));
  public static final RegistryObject<Item> COATED_BLOCK =
      ITEMS.register(
          "coated_block", () -> new BlockItem(ModBlocks.COATED_BLOCK.get(), new Item.Properties()));
  public static final RegistryObject<Item> CABLE_COATED_BLOCK =
      ITEMS.register(
          "cable_coated_block",
          () -> new BlockItem(ModBlocks.CABLE_COATED_BLOCK.get(), new Item.Properties()));

  private ModItems() {}
}
