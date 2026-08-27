package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.power.CoalGeneratorBlock;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModBlocks {
  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, Siliconic.MOD_ID);
  public static final RegistryObject<Block> WAFER_STATION =
      BLOCKS.register(
          "wafer_station",
          () ->
              new PrototypeWaferBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f)));
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

  private ModBlocks() {}
}
