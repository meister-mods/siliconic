package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomSuitItem;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@SuppressWarnings({"null"})
@Mod.EventBusSubscriber(
    modid = Siliconic.MOD_ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class ClientEvents {
  @SubscribeEvent
  public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
    event.register(
        (stack, tintIndex) -> tintIndex == 0 ? CleanroomSuitItem.DEFAULT_COLOR : 0xFFFFFFFF,
        ModItems.CLEANROOM_HOOD.get(),
        ModItems.CLEANROOM_CHESTPLATE.get(),
        ModItems.CLEANROOM_LEGGINGS.get(),
        ModItems.CLEANROOM_BOOTS.get());
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> MenuScreens.register(ModMenus.WAFER.get(), WaferScreen::new));
    event.enqueueWork(
        () -> MenuScreens.register(ModMenus.WAFER_DUPLICATOR.get(), WaferDuplicatorScreen::new));
    event.enqueueWork(
        () -> MenuScreens.register(ModMenus.SILICON_PROCESSOR.get(), SiliconProcessorScreen::new));
    event.enqueueWork(
        () ->
            MenuScreens.register(
                ModMenus.FABRICATION_STATION.get(), FabricationStationScreen::new));
    event.enqueueWork(
        () ->
            MenuScreens.register(
                ModMenus.REPROCESSOR.get(), ReprocessorScreen::new));
    event.enqueueWork(
        () -> MenuScreens.register(ModMenus.CONDITIONER.get(), ConditionerScreen::new));
  }

  private ClientEvents() {}
}
