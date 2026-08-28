package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.silicon.SiliconProcessorMenu;
import io.github.meistermods.siliconic.wafer.WaferDuplicatorMenu;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(ForgeRegistries.MENU_TYPES, Siliconic.MOD_ID);
  public static final RegistryObject<MenuType<WaferMenu>> WAFER =
      MENUS.register("wafer", () -> IForgeMenuType.create(WaferMenu::new));
  public static final RegistryObject<MenuType<WaferDuplicatorMenu>> WAFER_DUPLICATOR =
      MENUS.register("wafer_duplicator", () -> IForgeMenuType.create(WaferDuplicatorMenu::new));
  public static final RegistryObject<MenuType<SiliconProcessorMenu>> SILICON_PROCESSOR =
      MENUS.register("silicon_processor", () -> IForgeMenuType.create(SiliconProcessorMenu::new));

  private ModMenus() {}
}
