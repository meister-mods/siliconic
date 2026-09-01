package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.Siliconic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Classifies cleanroom pollution without treating storage or terrain blocks as machinery. */
@SuppressWarnings({"null"})
public final class CleanroomPollution {
  /** Explicit data-pack override. Entries remain pollution sources even without a block entity. */
  public static final TagKey<Block> POLLUTION_SOURCES =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "cleanroom_pollution_sources"));

  /** Explicit data-pack override checked before every capability or name-based inference. */
  public static final TagKey<Block> POLLUTION_EXEMPTIONS =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(
              Siliconic.MOD_ID, "cleanroom_pollution_exemptions"));

  /** Precision back-end equipment that also counts as a coated cleanroom surface. */
  public static final TagKey<Block> POST_PROCESS_EQUIPMENT =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(
              Siliconic.MOD_ID, "cleanroom_post_process_equipment"));

  /** Preserves the former furnace, combustion, generator, and reactor compatibility behavior. */
  private static final String[] LEGACY_EQUIPMENT_NAMES = {
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

  /** Cross-mod processing names are only accepted when the block also exposes an item inventory. */
  private static final String[] PROCESSING_EQUIPMENT_NAMES = {
    "machine",
    "processor",
    "crusher",
    "pulverizer",
    "macerator",
    "grinder",
    "mill",
    "press",
    "mixer",
    "centrifuge",
    "separator",
    "washer",
    "refiner",
    "refinery",
    "extractor",
    "infuser",
    "enricher",
    "alloy",
    "chamber",
    "assembler",
    "fabricator",
    "duplicator",
    "inverter",
    "reprocessor",
    "brewing",
    "brewer",
    "fermenter",
    "squeezer",
    "sawmill",
    "compactor",
    "compressor",
    "electrolyzer",
    "electrolyser",
    "chemical"
  };

  private static final String[] MANUAL_WORKSTATION_NAMES = {
    "workbench", "work_bench", "worktable", "work_table", "crafting_station", "craftingstation"
  };

  private static final String[] PASSIVE_INVENTORY_NAMES = {
    "chest",
    "barrel",
    "shulker",
    "drawer",
    "crate",
    "cabinet",
    "locker",
    "shelf",
    "warehouse",
    "item_storage",
    "itemstorage",
    "hopper",
    "pipe",
    "tube",
    "duct",
    "conduit",
    "router",
    "item_filter",
    "itemfilter",
    "terminal"
  };

  public static SourceCounts countSources(Level level, Set<Long> interiorPositions) {
    Set<Long> inspected = new HashSet<>();
    int equipment = 0;
    int blocks = 0;
    for (long packedPosition : interiorPositions) {
      BlockPos interiorPos = BlockPos.of(packedPosition);
      PollutionSourceType sourceType =
          classifyNewPollutionSource(level, interiorPos, inspected);
      if (sourceType == PollutionSourceType.EQUIPMENT) equipment++;
      else if (sourceType == PollutionSourceType.BLOCK) blocks++;
      for (Direction direction : Direction.values()) {
        sourceType = classifyNewPollutionSource(level, interiorPos.relative(direction), inspected);
        if (sourceType == PollutionSourceType.EQUIPMENT) equipment++;
        else if (sourceType == PollutionSourceType.BLOCK) blocks++;
      }
    }
    return new SourceCounts(equipment, blocks);
  }

  private static PollutionSourceType classifyNewPollutionSource(
      Level level, BlockPos pos, Set<Long> inspectedPositions) {
    if (!inspectedPositions.add(pos.asLong()) || !level.isLoaded(pos))
      return PollutionSourceType.NONE;
    BlockState state = level.getBlockState(pos);
    if (state.isAir() || state.is(POLLUTION_EXEMPTIONS)) return PollutionSourceType.NONE;
    if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return PollutionSourceType.BLOCK;

    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (state.is(POLLUTION_SOURCES))
      return blockEntity == null ? PollutionSourceType.BLOCK : PollutionSourceType.EQUIPMENT;
    if (blockEntity == null) return PollutionSourceType.NONE;
    if (state.getBlock() instanceof CraftingTableBlock) return PollutionSourceType.NONE;

    String identity = equipmentIdentity(state, blockEntity);
    if (isManualWorkstation(identity)) return PollutionSourceType.NONE;
    if (blockEntity instanceof AbstractFurnaceBlockEntity) return PollutionSourceType.EQUIPMENT;
    if (hasOutputOnlyEnergyCapability(blockEntity)) return PollutionSourceType.EQUIPMENT;
    if (containsAny(identity, LEGACY_EQUIPMENT_NAMES)) return PollutionSourceType.EQUIPMENT;

    InventoryProfile inventory = inventoryProfile(blockEntity);
    if (!inventory.present()) return PollutionSourceType.NONE;
    if (containsAny(identity, PROCESSING_EQUIPMENT_NAMES)) return PollutionSourceType.EQUIPMENT;
    if (containsAny(identity, PASSIVE_INVENTORY_NAMES)) return PollutionSourceType.NONE;
    return inventory.hasProcessingSlotLayout()
        ? PollutionSourceType.EQUIPMENT
        : PollutionSourceType.NONE;
  }

  /** Separate source totals for the conditioner UI; both retain the same contamination strength. */
  public record SourceCounts(int equipment, int blocks) {
    public int total() {
      return equipment + blocks;
    }
  }

  private enum PollutionSourceType {
    NONE,
    EQUIPMENT,
    BLOCK
  }

  /**
   * Separates machines from storage by looking for at least one accepting slot and a different
   * output-like slot which rejects all available input probes. Current inventory items are added to
   * the probe set, so mod-specific machines become detectable without knowing their item registry.
   */
  private static InventoryProfile inventoryProfile(BlockEntity blockEntity) {
    Set<IItemHandler> handlers =
        Collections.newSetFromMap(new IdentityHashMap<IItemHandler, Boolean>());
    addItemHandler(handlers, blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null));
    for (Direction direction : Direction.values())
      addItemHandler(
          handlers, blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction));
    if (handlers.isEmpty()) return InventoryProfile.EMPTY;

    List<ItemStack> probes = commonInputProbes();
    for (IItemHandler handler : handlers) {
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack current = handler.getStackInSlot(slot);
        if (!current.isEmpty()
            && probes.stream().noneMatch(probe -> ItemStack.isSameItemSameTags(current, probe)))
          probes.add(current.copyWithCount(1));
      }
    }

    boolean acceptsInput = false;
    boolean rejectsInput = false;
    int slots = 0;
    for (IItemHandler handler : handlers) {
      slots += handler.getSlots();
      for (int slot = 0; slot < handler.getSlots(); slot++) {
        boolean acceptsThisSlot = acceptsAnyProbe(handler, slot, probes);
        acceptsInput |= acceptsThisSlot;
        rejectsInput |= !acceptsThisSlot;
      }
    }
    return new InventoryProfile(true, acceptsInput, rejectsInput, slots);
  }

  private static void addItemHandler(
      Set<IItemHandler> handlers, LazyOptional<IItemHandler> capability) {
    IItemHandler handler = capability.orElse(null);
    if (handler != null) handlers.add(handler);
  }

  private static boolean acceptsAnyProbe(IItemHandler handler, int slot, List<ItemStack> probes) {
    if (handler.getSlotLimit(slot) <= 0) return false;
    for (ItemStack probe : probes) if (handler.isItemValid(slot, probe)) return true;
    return false;
  }

  private static List<ItemStack> commonInputProbes() {
    List<ItemStack> probes = new ArrayList<>();
    probes.add(new ItemStack(Items.COBBLESTONE));
    probes.add(new ItemStack(Items.COAL));
    probes.add(new ItemStack(Items.IRON_ORE));
    probes.add(new ItemStack(Items.IRON_INGOT));
    probes.add(new ItemStack(Items.REDSTONE));
    probes.add(new ItemStack(Items.QUARTZ));
    probes.add(new ItemStack(Items.SAND));
    probes.add(new ItemStack(Items.WHEAT));
    probes.add(new ItemStack(Items.BUCKET));
    probes.add(new ItemStack(Items.WATER_BUCKET));
    return probes;
  }

  private static boolean hasOutputOnlyEnergyCapability(BlockEntity blockEntity) {
    boolean canExtract = false;
    boolean canReceive = false;
    for (IEnergyStorage storage : energyStorages(blockEntity)) {
      canExtract |= storage.canExtract();
      canReceive |= storage.canReceive();
    }
    return canExtract && !canReceive;
  }

  private static Set<IEnergyStorage> energyStorages(BlockEntity blockEntity) {
    Set<IEnergyStorage> storages =
        Collections.newSetFromMap(new IdentityHashMap<IEnergyStorage, Boolean>());
    addEnergyStorage(storages, blockEntity.getCapability(ForgeCapabilities.ENERGY, null));
    for (Direction direction : Direction.values())
      addEnergyStorage(storages, blockEntity.getCapability(ForgeCapabilities.ENERGY, direction));
    return storages;
  }

  private static void addEnergyStorage(
      Set<IEnergyStorage> storages, LazyOptional<IEnergyStorage> capability) {
    IEnergyStorage storage = capability.orElse(null);
    if (storage != null) storages.add(storage);
  }

  private static boolean isManualWorkstation(String identity) {
    if (!containsAny(identity, MANUAL_WORKSTATION_NAMES)) return false;
    return !identity.contains("auto")
        && !identity.contains("automatic")
        && !identity.contains("powered");
  }

  private static String equipmentIdentity(BlockState state, BlockEntity blockEntity) {
    ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
    return ((blockId == null ? "" : blockId.toString())
            + " "
            + state.getBlock().getClass().getName()
            + " "
            + blockEntity.getClass().getName())
        .toLowerCase(Locale.ROOT);
  }

  private static boolean containsAny(String identity, String[] names) {
    for (String name : names) if (identity.contains(name)) return true;
    return false;
  }

  private record InventoryProfile(
      boolean present, boolean acceptsInput, boolean rejectsInput, int slots) {
    private static final InventoryProfile EMPTY = new InventoryProfile(false, false, false, 0);

    boolean hasProcessingSlotLayout() {
      return slots >= 2 && acceptsInput && rejectsInput;
    }
  }

  private CleanroomPollution() {}
}
