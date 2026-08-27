package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModItems {
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, Siliconic.MOD_ID);
  public static final RegistryObject<Item> CRUDE_SILICON =
      ITEMS.register("crude_silicon", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> PURE_SILICON =
      ITEMS.register("pure_silicon", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> SILICON_WAFER =
      ITEMS.register("silicon_wafer", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> LEVEL_2_WAFER =
      ITEMS.register("level_2_wafer", () -> new Item(new Item.Properties().stacksTo(1)));
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
  public static final RegistryObject<Item> SILVER_LEAD_POWER_CELL =
      ITEMS.register("silver_lead_power_cell", () -> new Item(new Item.Properties().stacksTo(16)));
  public static final RegistryObject<Item> COPPER_FRAGMENT =
      ITEMS.register("copper_fragment", () -> new Item(new Item.Properties()));
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
  public static final RegistryObject<Item> WAFER_STATION =
      ITEMS.register(
          "wafer_station",
          () -> new BlockItem(ModBlocks.WAFER_STATION.get(), new Item.Properties()));
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
  public static final RegistryObject<Item> COAL_GENERATOR =
      ITEMS.register(
          "coal_generator",
          () -> new BlockItem(ModBlocks.COAL_GENERATOR.get(), new Item.Properties()));

  private ModItems() {}
}
