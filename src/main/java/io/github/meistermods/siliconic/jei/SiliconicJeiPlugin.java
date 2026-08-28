package io.github.meistermods.siliconic.jei;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

@JeiPlugin
@SuppressWarnings({"null"})
public final class SiliconicJeiPlugin implements IModPlugin {
  private static final ResourceLocation PLUGIN_ID =
      ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "jei_plugin");

  public static final RecipeType<MachineProcess> SILICON_ARC_FURNACE =
      RecipeType.create(Siliconic.MOD_ID, "silicon_arc_furnace", MachineProcess.class);
  public static final RecipeType<MachineProcess> SILICON_PURIFIER =
      RecipeType.create(Siliconic.MOD_ID, "silicon_purifier", MachineProcess.class);
  public static final RecipeType<MachineProcess> WAFER_FABRICATOR =
      RecipeType.create(Siliconic.MOD_ID, "wafer_fabricator", MachineProcess.class);
  public static final RecipeType<MachineProcess> GATE_ASSEMBLER =
      RecipeType.create(Siliconic.MOD_ID, "gate_assembler", MachineProcess.class);

  @Override
  public ResourceLocation getPluginUid() {
    return PLUGIN_ID;
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    var guiHelper = registration.getJeiHelpers().getGuiHelper();
    for (MachineKind machine : MachineKind.values())
      registration.addRecipeCategories(
          new MachineProcessCategory(
              recipeType(machine),
              machine,
              Component.translatable(titleKey(machine)),
              machineItem(machine),
              guiHelper));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    for (MachineKind machine : MachineKind.values())
      registration.addRecipes(recipeType(machine), ModMachineProcesses.forMachine(machine));
  }

  @Override
  public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    for (MachineKind machine : MachineKind.values())
      registration.addRecipeCatalyst(machineItem(machine), recipeType(machine));
  }

  private static RecipeType<MachineProcess> recipeType(MachineKind machine) {
    return switch (machine) {
      case SILICON_ARC_FURNACE -> SILICON_ARC_FURNACE;
      case SILICON_PURIFIER -> SILICON_PURIFIER;
      case WAFER_FABRICATOR -> WAFER_FABRICATOR;
      case GATE_ASSEMBLER -> GATE_ASSEMBLER;
    };
  }

  private static ItemLike machineItem(MachineKind machine) {
    return switch (machine) {
      case SILICON_ARC_FURNACE -> ModItems.SILICON_ARC_FURNACE.get();
      case SILICON_PURIFIER -> ModItems.SILICON_PURIFIER.get();
      case WAFER_FABRICATOR -> ModItems.WAFER_FABRICATOR.get();
      case GATE_ASSEMBLER -> ModItems.GATE_ASSEMBLER.get();
    };
  }

  private static String titleKey(MachineKind machine) {
    return switch (machine) {
      case SILICON_ARC_FURNACE -> "container.siliconic.silicon_arc_furnace";
      case SILICON_PURIFIER -> "container.siliconic.silicon_purifier";
      case WAFER_FABRICATOR -> "container.siliconic.wafer_fabricator";
      case GATE_ASSEMBLER -> "container.siliconic.gate_assembler";
    };
  }
}
