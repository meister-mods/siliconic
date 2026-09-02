package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
  public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
      DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, Siliconic.MOD_ID);
  public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
      DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Siliconic.MOD_ID);

  public static final RegistryObject<RecipeType<MachineProcess>> MACHINE_PROCESS_TYPE =
      RECIPE_TYPES.register(
          "machine_process",
          () ->
              new RecipeType<MachineProcess>() {
                @Override
                public String toString() {
                  return Siliconic.MOD_ID + ":machine_process";
                }
              });

  public static final RegistryObject<RecipeSerializer<MachineProcess>> MACHINE_PROCESS_SERIALIZER =
      RECIPE_SERIALIZERS.register("machine_process", MachineProcess.Serializer::new);
  public static final RegistryObject<RecipeType<ReprocessingProcess>> REPROCESSING_TYPE =
      RECIPE_TYPES.register(
          "reprocessing",
          () ->
              new RecipeType<ReprocessingProcess>() {
                @Override
                public String toString() {
                  return Siliconic.MOD_ID + ":reprocessing";
                }
              });
  public static final RegistryObject<RecipeSerializer<ReprocessingProcess>>
      REPROCESSING_SERIALIZER =
          RECIPE_SERIALIZERS.register("reprocessing", ReprocessingProcess.Serializer::new);

  private ModRecipes() {}
}
