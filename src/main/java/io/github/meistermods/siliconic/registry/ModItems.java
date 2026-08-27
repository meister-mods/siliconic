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
  public static final RegistryObject<Item> WAFER_STATION =
      ITEMS.register(
          "wafer_station",
          () -> new BlockItem(ModBlocks.WAFER_STATION.get(), new Item.Properties()));

  private ModItems() {}
}
