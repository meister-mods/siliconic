package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
  public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Siliconic.MOD_ID);
  public static final RegistryObject<MenuType<WaferMenu>> WAFER = MENUS.register("wafer", () -> IForgeMenuType.create(WaferMenu::new));
  private ModMenus() {}
}
