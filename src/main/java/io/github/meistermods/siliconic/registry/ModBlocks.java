package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, Siliconic.MOD_ID);
  public static final RegistryObject<Block> PROTOTYPE_WAFER =
      BLOCKS.register(
          "prototype_wafer",
          () ->
              new PrototypeWaferBlock(
                  BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f)));

  private ModBlocks() {}
}
