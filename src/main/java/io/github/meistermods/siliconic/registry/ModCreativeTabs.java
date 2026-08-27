package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModCreativeTabs {
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Siliconic.MOD_ID);
  public static final RegistryObject<CreativeModeTab> SILICONIC =
      TABS.register(
          "siliconic",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.siliconic"))
                  .icon(() -> new ItemStack(ModItems.SILICON_WAFER.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.CRUDE_SILICON.get());
                        output.accept(ModItems.PURE_SILICON.get());
                        output.accept(ModItems.SILICON_WAFER.get());
                        output.accept(ModItems.COPPER_FRAGMENT.get());
                        output.accept(ModItems.NOT_GATE.get());
                        output.accept(ModItems.AND_GATE.get());
                        output.accept(ModItems.OR_GATE.get());
                        output.accept(ModItems.XOR_GATE.get());
                        output.accept(ModItems.WAFER_STATION.get());
                      })
                  .build());

  private ModCreativeTabs() {}
}
