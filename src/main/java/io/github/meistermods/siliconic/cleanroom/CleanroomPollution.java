package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.Siliconic;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;

/** Cross-mod cleanroom pollution detection for furnaces and energy-producing equipment. */
@SuppressWarnings({"null"})
public final class CleanroomPollution {
  public static final TagKey<Block> POLLUTION_SOURCES =
      TagKey.create(
          Registries.BLOCK,
          new ResourceLocation(Siliconic.MOD_ID, "cleanroom_pollution_sources"));
  public static final TagKey<Block> POLLUTION_EXEMPTIONS =
      TagKey.create(
          Registries.BLOCK,
          new ResourceLocation(Siliconic.MOD_ID, "cleanroom_pollution_exemptions"));

  private static final String[] POLLUTING_EQUIPMENT_NAMES = {
    "furnace",
    "smoker",
    "smelter",
    "smeltery",
    "kiln",
    "oven",
    "foundry",
    "boiler",
    "burner",
    "combustor",
    "incinerator",
    "generator",
    "alternator",
    "dynamo",
    "turbine",
    "reactor",
    "engine",
    "heater",
    "power_plant"
  };

  public static int countSources(Level level, Set<Long> interiorPositions) {
    Set<Long> inspected = new HashSet<>();
    int sources = 0;
    for (long packedPosition : interiorPositions) {
      BlockPos interiorPos = BlockPos.of(packedPosition);
      if (isNewPollutionSource(level, interiorPos, inspected)) sources++;
      for (Direction direction : Direction.values())
        if (isNewPollutionSource(level, interiorPos.relative(direction), inspected)) sources++;
    }
    return sources;
  }

  private static boolean isNewPollutionSource(
      Level level, BlockPos pos, Set<Long> inspectedPositions) {
    if (!inspectedPositions.add(pos.asLong()) || !level.isLoaded(pos)) return false;
    BlockState state = level.getBlockState(pos);
    if (state.isAir() || state.is(POLLUTION_EXEMPTIONS)) return false;
    if (state.is(POLLUTION_SOURCES)) return true;

    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (blockEntity == null) return false;
    if (blockEntity instanceof AbstractFurnaceBlockEntity) return true;
    if (hasOutputOnlyEnergyCapability(blockEntity)) return true;
    return hasPollutingEquipmentName(state, blockEntity);
  }

  private static boolean hasOutputOnlyEnergyCapability(BlockEntity blockEntity) {
    boolean canExtract = false;
    boolean canReceive = false;
    IEnergyStorage storage =
        energyStorage(blockEntity.getCapability(ForgeCapabilities.ENERGY, null));
    if (storage != null) {
      canExtract |= storage.canExtract();
      canReceive |= storage.canReceive();
    }
    for (Direction direction : Direction.values()) {
      storage = energyStorage(blockEntity.getCapability(ForgeCapabilities.ENERGY, direction));
      if (storage == null) continue;
      canExtract |= storage.canExtract();
      canReceive |= storage.canReceive();
    }
    return canExtract && !canReceive;
  }

  private static IEnergyStorage energyStorage(LazyOptional<IEnergyStorage> capability) {
    return capability.orElse(null);
  }

  private static boolean hasPollutingEquipmentName(BlockState state, BlockEntity blockEntity) {
    ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
    String identity =
        ((blockId == null ? "" : blockId.getPath())
                + " "
                + state.getBlock().getClass().getSimpleName()
                + " "
                + blockEntity.getClass().getSimpleName())
            .toLowerCase(Locale.ROOT);
    for (String name : POLLUTING_EQUIPMENT_NAMES) if (identity.contains(name)) return true;
    return false;
  }

  private CleanroomPollution() {}
}
