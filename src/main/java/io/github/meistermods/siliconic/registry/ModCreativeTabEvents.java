package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Siliconic.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTabEvents {
  @SubscribeEvent public static void buildContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
      event.accept(ModItems.CRUDE_SILICON); event.accept(ModItems.PURE_SILICON); event.accept(ModItems.SILICON_WAFER);
    }
    if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) event.accept(ModItems.PROTOTYPE_WAFER);
  }
  private ModCreativeTabEvents() {}
}
