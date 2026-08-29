package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.ConditionerBlockEntity;
import io.github.meistermods.siliconic.fabrication.FabricationStationBlockEntity;
import io.github.meistermods.siliconic.power.CoalGeneratorBlockEntity;
import io.github.meistermods.siliconic.reprocessing.ReprocessingStationBlockEntity;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlockEntity;
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
  public static final RegistryObject<BlockEntityType<SiliconProcessorBlockEntity>>
      SILICON_PROCESSOR =
          BLOCK_ENTITIES.register(
              "silicon_processor",
              () ->
                  BlockEntityType.Builder.of(
                          SiliconProcessorBlockEntity::new,
                          ModBlocks.SILICON_ARC_FURNACE.get(),
                          ModBlocks.SILICON_PURIFIER.get())
                      .build(null));
  public static final RegistryObject<BlockEntityType<FabricationStationBlockEntity>>
      FABRICATION_STATION =
          BLOCK_ENTITIES.register(
              "fabrication_station",
              () ->
                  BlockEntityType.Builder.of(
                          FabricationStationBlockEntity::new,
                          ModBlocks.WAFER_FABRICATOR.get(),
                          ModBlocks.GATE_ASSEMBLER.get())
                      .build(null));
  public static final RegistryObject<BlockEntityType<ReprocessingStationBlockEntity>>
      REPROCESSING_STATION =
          BLOCK_ENTITIES.register(
              "reprocessing_station",
              () ->
                  BlockEntityType.Builder.of(
                          ReprocessingStationBlockEntity::new,
                          ModBlocks.REPROCESSING_STATION.get())
                      .build(null));
  public static final RegistryObject<BlockEntityType<ConditionerBlockEntity>> CONDITIONER =
      BLOCK_ENTITIES.register(
          "conditioner",
          () ->
              BlockEntityType.Builder.of(ConditionerBlockEntity::new, ModBlocks.CONDITIONER.get())
                  .build(null));

  private ModBlockEntities() {}
}
