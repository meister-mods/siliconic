package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.power.CoalGeneratorBlockEntity;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferDuplicatorBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferInverterBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModBlockEntities {
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Siliconic.MOD_ID);
  public static final RegistryObject<BlockEntityType<PrototypeWaferBlockEntity>> PROTOTYPE_WAFER =
      BLOCK_ENTITIES.register(
          "wafer_station",
          () ->
              BlockEntityType.Builder.of(
                      PrototypeWaferBlockEntity::new,
                      ModBlocks.WAFER_STATION.get(),
                      ModBlocks.WAFER_GUARD.get())
                  .build(null));
  public static final RegistryObject<BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR =
      BLOCK_ENTITIES.register(
          "coal_generator",
          () ->
              BlockEntityType.Builder.of(
                      CoalGeneratorBlockEntity::new, ModBlocks.COAL_GENERATOR.get())
                  .build(null));
  public static final RegistryObject<BlockEntityType<WaferInverterBlockEntity>> WAFER_INVERTER =
      BLOCK_ENTITIES.register(
          "wafer_inverter",
          () ->
              BlockEntityType.Builder.of(
                      WaferInverterBlockEntity::new, ModBlocks.WAFER_INVERTER.get())
                  .build(null));
  public static final RegistryObject<BlockEntityType<WaferDuplicatorBlockEntity>> WAFER_DUPLICATOR =
      BLOCK_ENTITIES.register(
          "wafer_duplicator",
          () ->
              BlockEntityType.Builder.of(
                      WaferDuplicatorBlockEntity::new, ModBlocks.WAFER_DUPLICATOR.get())
                  .build(null));

  private ModBlockEntities() {}
}
