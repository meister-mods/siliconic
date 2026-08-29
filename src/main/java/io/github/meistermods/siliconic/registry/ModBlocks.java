package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.ConditionerBlock;
import io.github.meistermods.siliconic.fabrication.FabricationStationBlock;
import io.github.meistermods.siliconic.power.CoalGeneratorBlock;
import io.github.meistermods.siliconic.power.PowerCableBlock;
import io.github.meistermods.siliconic.power.RedstoneClockBlock;
import io.github.meistermods.siliconic.reprocessing.ReprocessorBlock;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlock;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlock;
import io.github.meistermods.siliconic.wafer.WaferDuplicatorBlock;
import io.github.meistermods.siliconic.wafer.WaferInverterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModBlocks {
  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, Siliconic.MOD_ID);
  public static final RegistryObject<Block> WAFER_ASSEMBLER =
      BLOCKS.register(
          "wafer_assembler",
          () ->
              new PrototypeWaferBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f)));
  public static final RegistryObject<Block> WAFER_GUARD =
      BLOCKS.register(
          "wafer_guard",
          () ->
              new PrototypeWaferBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(2.0f)
                      .noOcclusion(),
                  false));
  public static final RegistryObject<Block> WAFER_INVERTER =
      BLOCKS.register(
          "wafer_inverter",
          () ->
              new WaferInverterBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f)));
  public static final RegistryObject<Block> WAFER_DUPLICATOR =
      BLOCKS.register(
          "wafer_duplicator",
          () ->
              new WaferDuplicatorBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f)));
  public static final RegistryObject<Block> SILICON_ARC_FURNACE =
      BLOCKS.register(
          "silicon_arc_furnace",
          () ->
              new SiliconProcessorBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()
                      .lightLevel(state -> state.getValue(SiliconProcessorBlock.ACTIVE) ? 10 : 0)));
  public static final RegistryObject<Block> SILICON_PURIFIER =
      BLOCKS.register(
          "silicon_purifier",
          () ->
              new SiliconProcessorBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.QUARTZ)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()
                      .lightLevel(state -> state.getValue(SiliconProcessorBlock.ACTIVE) ? 6 : 0)));
  public static final RegistryObject<Block> WAFER_FABRICATOR =
      BLOCKS.register(
          "wafer_fabricator",
          () ->
              new FabricationStationBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> GATE_ASSEMBLER =
      BLOCKS.register(
          "gate_assembler",
          () ->
              new FabricationStationBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.COLOR_ORANGE)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> REPROCESSOR =
      BLOCKS.register(
          "reprocessor",
          () ->
              new ReprocessorBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> SILVER_ORE =
      BLOCKS.register(
          "silver_ore",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.STONE)
                      .strength(3.0f, 3.0f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> DEEPSLATE_SILVER_ORE =
      BLOCKS.register(
          "deepslate_silver_ore",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.DEEPSLATE)
                      .strength(4.5f, 3.0f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> LEAD_ORE =
      BLOCKS.register(
          "lead_ore",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.STONE)
                      .strength(3.0f, 3.0f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> DEEPSLATE_LEAD_ORE =
      BLOCKS.register(
          "deepslate_lead_ore",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.DEEPSLATE)
                      .strength(4.5f, 3.0f)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> SILVER_BLOCK =
      BLOCKS.register(
          "silver_block",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(5.0f, 6.0f)
                      .sound(SoundType.METAL)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> LEAD_BLOCK =
      BLOCKS.register(
          "lead_block",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.COLOR_GRAY)
                      .strength(5.0f, 6.0f)
                      .sound(SoundType.METAL)
                      .requiresCorrectToolForDrops()));
  public static final RegistryObject<Block> COAL_GENERATOR =
      BLOCKS.register(
          "coal_generator",
          () ->
              new CoalGeneratorBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()
                      .lightLevel(state -> state.getValue(CoalGeneratorBlock.LIT) ? 13 : 0)));
  public static final RegistryObject<Block> POWER_CABLE =
      BLOCKS.register(
          "power_cable",
          () ->
              new PowerCableBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.COLOR_ORANGE)
                      .strength(0.2f)
                      .noCollission()
                      .noOcclusion()));
  public static final RegistryObject<Block> REDSTONE_CLOCK =
      BLOCKS.register(
          "redstone_clock",
          () ->
              new RedstoneClockBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.METAL)
                      .strength(2.5f)
                      .requiresCorrectToolForDrops()
                      .lightLevel(state -> state.getValue(RedstoneClockBlock.POWERED) ? 7 : 0)));
  public static final RegistryObject<Block> CONDITIONER =
      BLOCKS.register(
          "conditioner",
          () ->
              new ConditionerBlock(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.QUARTZ)
                      .strength(3.5f)
                      .requiresCorrectToolForDrops()
                      .lightLevel(state -> state.getValue(ConditionerBlock.ACTIVE) ? 7 : 0)));
  public static final RegistryObject<Block> COATED_BLOCK =
      BLOCKS.register(
          "coated_block",
          () ->
              new Block(
                  BlockBehaviour.Properties.of()
                      .mapColor(MapColor.QUARTZ)
                      .strength(3.5f, 6.0f)
                      .requiresCorrectToolForDrops()));

  private ModBlocks() {}
}
