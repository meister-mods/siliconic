package io.github.meistermods.siliconic;

import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModCreativeTabs;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Siliconic.MOD_ID)
public final class Siliconic {
  public static final String MOD_ID = "siliconic";

  public Siliconic(FMLJavaModLoadingContext context) {
    var bus = context.getModEventBus();
    ModBlocks.BLOCKS.register(bus);
    ModItems.ITEMS.register(bus);
    ModBlockEntities.BLOCK_ENTITIES.register(bus);
    ModMenus.MENUS.register(bus);
    ModCreativeTabs.TABS.register(bus);
    ModNetwork.register();
  }
}
