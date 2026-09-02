package io.github.meistermods.siliconic.jei;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

@JeiPlugin
@SuppressWarnings({"null"})
public final class SiliconicJeiPlugin implements IModPlugin {
  private static final ResourceLocation PLUGIN_ID =
      ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "jei_plugin");

  public static final RecipeType<MachineProcess> SILICON_ARC_FURNACE =
      RecipeType.create(Siliconic.MOD_ID, "silicon_arc_furnace", MachineProcess.class);
  public static final RecipeType<MachineProcess> CHLORINATION_REACTOR =
      RecipeType.create(Siliconic.MOD_ID, "chlorination_reactor", MachineProcess.class);
  public static final RecipeType<MachineProcess> DISTILLATION_TOWER =
      RecipeType.create(Siliconic.MOD_ID, "distillation_tower", MachineProcess.class);
  public static final RecipeType<MachineProcess> SIEMENS_REACTOR =
      RecipeType.create(Siliconic.MOD_ID, "siemens_reactor", MachineProcess.class);
  public static final RecipeType<MachineProcess> CHEMICAL_RECYCLER =
      RecipeType.create(Siliconic.MOD_ID, "chemical_recycler", MachineProcess.class);
  public static final RecipeType<MachineProcess> WAFER_FABRICATOR =
      RecipeType.create(Siliconic.MOD_ID, "wafer_fabricator", MachineProcess.class);
  public static final RecipeType<MachineProcess> GATE_FABRICATOR =
      RecipeType.create(Siliconic.MOD_ID, "gate_fabricator", MachineProcess.class);
  public static final RecipeType<ReprocessingProcess> REPROCESSOR =
      RecipeType.create(Siliconic.MOD_ID, "reprocessor", ReprocessingProcess.class);

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
    registration.addRecipeCategories(new ReprocessingProcessCategory(REPROCESSOR, guiHelper));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    for (MachineKind machine : MachineKind.values())
      registration.addRecipes(
          recipeType(machine),
          ModMachineProcesses.forMachine(Minecraft.getInstance().level, machine));
    registration.addRecipes(REPROCESSOR, ReprocessingProcess.all(Minecraft.getInstance().level));
    registerProcessInformation(registration);
  }

  @Override
  public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    for (MachineKind machine : MachineKind.values())
      registration.addRecipeCatalyst(machineItem(machine), recipeType(machine));
    registration.addRecipeCatalyst(ModItems.REPROCESSOR.get(), REPROCESSOR);
  }

  private static RecipeType<MachineProcess> recipeType(MachineKind machine) {
    return switch (machine) {
      case SILICON_ARC_FURNACE -> SILICON_ARC_FURNACE;
      case CHLORINATION_REACTOR -> CHLORINATION_REACTOR;
      case DISTILLATION_TOWER -> DISTILLATION_TOWER;
      case SIEMENS_REACTOR -> SIEMENS_REACTOR;
      case CHEMICAL_RECYCLER -> CHEMICAL_RECYCLER;
      case WAFER_FABRICATOR -> WAFER_FABRICATOR;
      case GATE_FABRICATOR -> GATE_FABRICATOR;
    };
  }

  private static ItemLike machineItem(MachineKind machine) {
    return switch (machine) {
      case SILICON_ARC_FURNACE -> ModItems.SILICON_ARC_FURNACE.get();
      case CHLORINATION_REACTOR -> ModItems.CHLORINATION_REACTOR.get();
      case DISTILLATION_TOWER -> ModItems.DISTILLATION_TOWER.get();
      case SIEMENS_REACTOR -> ModItems.SIEMENS_REACTOR.get();
      case CHEMICAL_RECYCLER -> ModItems.CHEMICAL_RECYCLER.get();
      case WAFER_FABRICATOR -> ModItems.WAFER_FABRICATOR.get();
      case GATE_FABRICATOR -> ModItems.GATE_FABRICATOR.get();
    };
  }

  private static String titleKey(MachineKind machine) {
    return "container.siliconic." + machine.id();
  }

  private static void registerProcessInformation(IRecipeRegistration registration) {
    registration.addIngredientInfo(
        ModItems.SALT_DIRT.get(),
        Component.translatable("jei.siliconic.info.salt_dirt.discovery"),
        Component.translatable("jei.siliconic.info.salt_dirt.drops"));
    registration.addIngredientInfo(
        ModItems.SALT.get(), Component.translatable("jei.siliconic.info.salt.use"));

    registration.addIngredientInfo(
        Items.MAGMA_CREAM, Component.translatable("jei.siliconic.info.magma_fuel", 2_000));
    registration.addIngredientInfo(
        Items.MAGMA_BLOCK, Component.translatable("jei.siliconic.info.magma_fuel", 8_000));
    registration.addIngredientInfo(
        Items.LAVA_BUCKET, Component.translatable("jei.siliconic.info.magma_fuel.lava", 16_000));

    registration.addIngredientInfo(
        ModItems.SILICON_ARC_FURNACE.get(),
        Component.translatable("jei.siliconic.info.arc_furnace.operation"));
    registration.addIngredientInfo(
        ModItems.CARBON_ELECTRODE.get(),
        Component.translatable("jei.siliconic.info.arc_furnace.electrode"));
    registration.addIngredientInfo(
        ModItems.CHLORINATION_REACTOR.get(),
        Component.translatable("jei.siliconic.info.chlorination.pressure"));
    registration.addIngredientInfo(
        ModItems.DISTILLATION_TOWER.get(),
        Component.translatable("jei.siliconic.info.distillation.purity"),
        Component.translatable("jei.siliconic.info.distillation.throughput"),
        Component.translatable("jei.siliconic.info.distillation.stability"));
    registration.addIngredientInfo(
        ModItems.DISTILLATION_RESIDUE.get(),
        Component.translatable("jei.siliconic.info.distillation.residue"));
    registration.addIngredientInfo(
        ModItems.SIEMENS_REACTOR.get(),
        Component.translatable("jei.siliconic.info.siemens.filament"),
        Component.translatable("jei.siliconic.info.siemens.recovery"));
    registration.addIngredientInfo(
        ModItems.PARTIAL_POLYSILICON_ROD.get(),
        Component.translatable("jei.siliconic.info.siemens.partial_rod"));
    registration.addIngredientInfo(
        ModItems.CHEMICAL_RECYCLER.get(),
        Component.translatable("jei.siliconic.info.recycler.loop"),
        Component.translatable("jei.siliconic.info.recycler.outputs"));
    registration.addIngredientInfo(
        ModItems.WAFER_ASSEMBLER.get(),
        Component.translatable("jei.siliconic.info.wafer_assembler.contamination"));
    registration.addIngredientInfo(
        ModItems.WAFER_INVERTER.get(),
        Component.translatable("jei.siliconic.info.wafer_inverter.contamination"));
    registration.addIngredientInfo(
        ModItems.REPROCESSOR.get(),
        Component.translatable("jei.siliconic.info.reprocessor.finished_components"));
    registration.addIngredientInfo(
        ModItems.CONDITIONER.get(),
        Component.translatable("jei.siliconic.info.conditioner.limit"),
        Component.translatable("jei.siliconic.info.conditioner.shovel_pollution"));
  }
}
