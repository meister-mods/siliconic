package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
    modid = Siliconic.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class ClientEvents {
  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> MenuScreens.register(ModMenus.WAFER.get(), WaferScreen::new));
  }

  private ClientEvents() {}
}
