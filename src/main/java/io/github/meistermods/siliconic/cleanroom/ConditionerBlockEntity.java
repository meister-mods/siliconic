package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.config.SiliconicConfig;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class ConditionerBlockEntity extends BlockEntity implements MenuProvider {
  public static final int ENERGY_CAPACITY = 40_000;
  public static final int ENERGY_PER_TICK = 40;
  public static final int SCAN_INTERVAL = 20;
  public static final int BASE_CLEANLINESS_LIMIT = 75;
  public static final int MAX_CLEANLINESS = 100;
  public static final int CLEANLINESS_RECOVERY_PER_SCAN = 1;
  public static final int CLEANLINESS_DECAY_PER_SCAN = 2;
  public static final int CONTAMINATION_PER_UNPROTECTED_ENTITY = 2;
  public static final int CONTAMINATION_PER_POLLUTION_SOURCE = 4;
  public static final int CONDITIONER_LIMIT_GAP_REDUCTION_PERCENT = 10;
  private static final String CLAIMED_INTERIOR_TAG = "ClaimedInterior";
  private static final String LAST_SHARED_UPDATE_TAG = "LastSharedCleanlinessUpdate";
  private static final String RECOVERY_PROGRESS_TAG = "CleanlinessRecoveryProgress";
  private static final double LOG_2 = Math.log(2.0D);

  private final ConditionerEnergyStorage energy = new ConditionerEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private boolean powered;
  private boolean sharedPowered;
  private int cleanliness;
  private int cleanlinessLimit = BASE_CLEANLINESS_LIMIT;
  private int coatingCoverage;
  private int unprotectedEntities;
  private int equipmentPollutionSources;
  private int blockPollutionSources;
  private int conditionerCount = 1;
  private long lastSharedCleanlinessUpdate = -1L;
  private double cleanlinessRecoveryProgress;
  private RoomScanResult lastScan = RoomScanResult.notScanned();
  private final Set<Long> claimedInteriorPositions = new HashSet<>();
  private final int[] clientData = new int[13];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> sharedPowered ? 1 : 0;
            case 3 -> cleanliness;
            case 4 -> cleanlinessLimit();
            case 5 -> coatingCoverage();
            case 6 -> unprotectedEntities;
            case 7 -> conditionerCount;
            case 8 -> equipmentPollutionSources;
            case 9 -> blockPollutionSources;
            case 10 -> SiliconicConfig.VALUES.cleanroomEnergyPerTick.get();
            case 11 -> SiliconicConfig.VALUES.cleanroomEntityContamination.get();
            case 12 -> SiliconicConfig.VALUES.cleanroomSourceContamination.get();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          if (index >= 0 && index < clientData.length) clientData[index] = value;
        }

        @Override
        public int getCount() {
          return clientData.length;
        }
      };

  private final class ConditionerEnergyStorage extends EnergyStorage {
    ConditionerEnergyStorage() {
      super(ENERGY_CAPACITY, 200, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    boolean consumeInternal(int amount) {
      if (amount < 0 || energy < amount) return false;
      energy -= amount;
      return true;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) ConditionerBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public ConditionerBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.CONDITIONER.get(), pos, state);
  }

  public ContainerData data() {
    return data;
  }

  public RoomScanResult lastScan() {
    return lastScan;
  }

  public int cleanliness() {
    return cleanliness;
  }

  public int cleanlinessLimit() {
    return cleanlinessLimit;
  }

  public int coatingCoverage() {
    return coatingCoverage;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, ConditionerBlockEntity conditioner) {
    boolean nextPowered =
        conditioner.energy.consumeInternal(SiliconicConfig.VALUES.cleanroomEnergyPerTick.get());
    boolean powerChanged = nextPowered != conditioner.powered;
    conditioner.powered = nextPowered;
    if (state.getValue(ConditionerBlock.ACTIVE) != nextPowered)
      level.setBlock(pos, state.setValue(ConditionerBlock.ACTIVE, nextPowered), 3);

    int scanInterval = SiliconicConfig.VALUES.cleanroomScanInterval.get();
    long scanOffset = Math.floorMod(pos.asLong(), scanInterval);
    if (Math.floorMod(level.getGameTime(), scanInterval) == scanOffset) {
      conditioner.lastScan = RoomScanner.scan(level, pos);
      if (conditioner.lastScan.isSealed()) {
        conditioner.claimedInteriorPositions.clear();
        conditioner.claimedInteriorPositions.addAll(conditioner.lastScan.interiorPositions());
      } else if (conditioner.lastScan.status() != RoomScanResult.Status.UNLOADED)
        conditioner.claimedInteriorPositions.clear();
      Set<Long> conditionerGroup =
          CleanroomOccupancy.update(
              level, pos, conditioner.claimedInteriorPositions, conditioner.cleanliness);
      conditioner.synchronizeGroup(level, conditionerGroup);
      conditioner.sync();
    } else if (powerChanged) conditioner.sync();
    if (nextPowered) conditioner.setChanged();
  }

  private void synchronizeGroup(Level level, Set<Long> conditionerPositions) {
    List<ConditionerBlockEntity> conditioners = resolveConditioners(level, conditionerPositions);
    ConditionerBlockEntity source = sharedStateSource(conditioners);
    SharedState snapshot = source.sharedState();
    boolean roomPowered = conditioners.stream().anyMatch(conditioner -> conditioner.powered);
    int sharedConditionerCount = conditioners.size();
    Set<ConditionerBlockEntity> changedConditioners = new HashSet<>();

    for (ConditionerBlockEntity conditioner : conditioners)
      if (conditioner.applySharedState(snapshot, sharedConditionerCount, roomPowered))
        changedConditioners.add(conditioner);

    long gameTime = level.getGameTime();
    if (snapshot.updatedAt() < 0L
        || gameTime < snapshot.updatedAt()
        || gameTime - snapshot.updatedAt() >= SiliconicConfig.VALUES.cleanroomScanInterval.get()) {
      ConditionerBlockEntity updater = cleanlinessUpdater(conditioners);
      if (updater != null) {
        updater.updateCleanliness(level, roomPowered);
        updater.lastSharedCleanlinessUpdate = gameTime;
        changedConditioners.add(updater);
        snapshot = updater.sharedState();
        for (ConditionerBlockEntity conditioner : conditioners)
          if (conditioner.applySharedState(snapshot, sharedConditionerCount, roomPowered))
            changedConditioners.add(conditioner);
      }
    }

    CleanroomOccupancy.synchronizeCleanliness(level, conditionerPositions, snapshot.cleanliness());
    for (ConditionerBlockEntity conditioner : changedConditioners)
      if (conditioner != this) conditioner.sync();
  }

  /**
   * An unloaded scan cannot prove whether the room is still sealed. Prefer a linked conditioner
   * with a complete scan, and freeze the shared state when every available scan is incomplete.
   */
  @Nullable
  private ConditionerBlockEntity cleanlinessUpdater(List<ConditionerBlockEntity> conditioners) {
    if (lastScan.status() != RoomScanResult.Status.UNLOADED) return this;
    ConditionerBlockEntity updater = null;
    for (ConditionerBlockEntity conditioner : conditioners) {
      if (!conditioner.lastScan.isSealed()) continue;
      if (updater == null || conditioner.worldPosition.asLong() < updater.worldPosition.asLong())
        updater = conditioner;
    }
    return updater;
  }

  private List<ConditionerBlockEntity> resolveConditioners(
      Level level, Set<Long> conditionerPositions) {
    List<ConditionerBlockEntity> conditioners = new ArrayList<>();
    for (long conditionerPosition : conditionerPositions) {
      BlockEntity blockEntity = level.getBlockEntity(BlockPos.of(conditionerPosition));
      if (blockEntity instanceof ConditionerBlockEntity conditioner && !conditioner.isRemoved())
        conditioners.add(conditioner);
    }
    if (!conditioners.contains(this)) conditioners.add(this);
    return conditioners;
  }

  private ConditionerBlockEntity sharedStateSource(List<ConditionerBlockEntity> conditioners) {
    ConditionerBlockEntity source = conditioners.get(0);
    for (int index = 1; index < conditioners.size(); index++) {
      ConditionerBlockEntity candidate = conditioners.get(index);
      if (candidate.lastSharedCleanlinessUpdate > source.lastSharedCleanlinessUpdate
          || (candidate.lastSharedCleanlinessUpdate == source.lastSharedCleanlinessUpdate
              && (candidate.cleanliness > source.cleanliness
                  || (candidate.cleanliness == source.cleanliness
                      && candidate.worldPosition.asLong() < source.worldPosition.asLong()))))
        source = candidate;
    }
    return source;
  }

  private SharedState sharedState() {
    return new SharedState(
        cleanliness,
        cleanlinessLimit,
        coatingCoverage,
        unprotectedEntities,
        equipmentPollutionSources,
        blockPollutionSources,
        lastSharedCleanlinessUpdate,
        cleanlinessRecoveryProgress);
  }

  private boolean applySharedState(
      SharedState state, int sharedConditionerCount, boolean roomPowered) {
    boolean changed =
        cleanliness != state.cleanliness()
            || cleanlinessLimit != state.cleanlinessLimit()
            || coatingCoverage != state.coatingCoverage()
            || unprotectedEntities != state.unprotectedEntities()
            || equipmentPollutionSources != state.equipmentPollutionSources()
            || blockPollutionSources != state.blockPollutionSources()
            || lastSharedCleanlinessUpdate != state.updatedAt()
            || Double.compare(cleanlinessRecoveryProgress, state.recoveryProgress()) != 0
            || conditionerCount != sharedConditionerCount
            || sharedPowered != roomPowered;
    cleanliness = state.cleanliness();
    cleanlinessLimit = state.cleanlinessLimit();
    coatingCoverage = state.coatingCoverage();
    unprotectedEntities = state.unprotectedEntities();
    equipmentPollutionSources = state.equipmentPollutionSources();
    blockPollutionSources = state.blockPollutionSources();
    lastSharedCleanlinessUpdate = state.updatedAt();
    cleanlinessRecoveryProgress = state.recoveryProgress();
    conditionerCount = sharedConditionerCount;
    sharedPowered = roomPowered;
    return changed;
  }

  private void updateCleanliness(Level level, boolean roomPowered) {
    if (lastScan.isSealed()) {
      updateCleanlinessLimit();
      if (roomPowered) {
        cleanlinessRecoveryProgress += recoveryPerScan(conditionerCount);
        int recovery = (int) Math.floor(cleanlinessRecoveryProgress);
        cleanlinessRecoveryProgress -= recovery;
        cleanliness = Math.min(cleanlinessLimit, cleanliness + recovery);
        if (cleanliness >= cleanlinessLimit) cleanlinessRecoveryProgress = 0.0D;
      } else cleanliness = Math.min(cleanliness, cleanlinessLimit);
      unprotectedEntities = countUnprotectedEntities(level);
      CleanroomPollution.SourceCounts pollution =
          CleanroomPollution.countSources(level, lastScan.interiorPositions());
      equipmentPollutionSources = pollution.equipment();
      blockPollutionSources = pollution.blocks();
      int contamination =
          Math.min(
              MAX_CLEANLINESS,
              unprotectedEntities * SiliconicConfig.VALUES.cleanroomEntityContamination.get()
                  + pollution.total() * SiliconicConfig.VALUES.cleanroomSourceContamination.get());
      cleanliness = Math.max(0, cleanliness - contamination);
    } else {
      unprotectedEntities = 0;
      equipmentPollutionSources = 0;
      blockPollutionSources = 0;
      cleanlinessRecoveryProgress = 0.0D;
      cleanliness = Math.max(0, cleanliness - CLEANLINESS_DECAY_PER_SCAN);
    }
  }

  private double recoveryPerScan(int conditioners) {
    // One unit keeps the original rate; doubling the linked units adds one recovery per scan.
    return CLEANLINESS_RECOVERY_PER_SCAN * (1.0D + Math.log(Math.max(1, conditioners)) / LOG_2);
  }

  private int countUnprotectedEntities(Level level) {
    Set<Long> interior = lastScan.interiorPositions();
    if (interior.isEmpty()) return 0;
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (long packedPos : interior) {
      BlockPos interiorPos = BlockPos.of(packedPos);
      minX = Math.min(minX, interiorPos.getX());
      minY = Math.min(minY, interiorPos.getY());
      minZ = Math.min(minZ, interiorPos.getZ());
      maxX = Math.max(maxX, interiorPos.getX());
      maxY = Math.max(maxY, interiorPos.getY());
      maxZ = Math.max(maxZ, interiorPos.getZ());
    }
    AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    return level
        .getEntitiesOfClass(
            Entity.class,
            bounds,
            entity ->
                !entity.isSpectator()
                    && interior.contains(entity.blockPosition().asLong())
                    && !CleanroomSuitItem.isFullyProtected(entity))
        .size();
  }

  private void updateCleanlinessLimit() {
    int totalFaces = totalSurfaceFaces();
    int coatedFaces = coatedSurfaceFaces();
    int surfaceLimit = BASE_CLEANLINESS_LIMIT;
    if (totalFaces == 0) {
      coatingCoverage = 0;
    } else {
      coatingCoverage = (100 * coatedFaces + totalFaces / 2) / totalFaces;
      int bonus =
          ((MAX_CLEANLINESS - BASE_CLEANLINESS_LIMIT) * coatedFaces + totalFaces / 2) / totalFaces;
      surfaceLimit += bonus;
    }
    cleanlinessLimit = limitWithConditioners(surfaceLimit, conditionerCount);
  }

  static int limitWithConditioners(int surfaceLimit, int conditioners) {
    int limit = Math.max(BASE_CLEANLINESS_LIMIT, Math.min(MAX_CLEANLINESS, surfaceLimit));
    for (int extra = 1; extra < Math.max(1, conditioners) && limit < MAX_CLEANLINESS; extra++) {
      int remaining = MAX_CLEANLINESS - limit;
      int increase = Math.max(1, (remaining * CONDITIONER_LIMIT_GAP_REDUCTION_PERCENT + 99) / 100);
      limit = Math.min(MAX_CLEANLINESS, limit + increase);
    }
    return limit;
  }

  private int totalSurfaceFaces() {
    return lastScan.surfaceMaterials().values().stream().mapToInt(Integer::intValue).sum();
  }

  private int coatedSurfaceFaces() {
    int coated =
        lastScan.surfaceMaterials().getOrDefault(ModBlocks.COATED_BLOCK.getId(), 0)
            + lastScan.surfaceMaterials().getOrDefault(ModBlocks.CABLE_COATED_BLOCK.getId(), 0);
    for (var entry : lastScan.surfaceMaterials().entrySet()) {
      if (entry.getKey().equals(ModBlocks.COATED_BLOCK.getId())
          || entry.getKey().equals(ModBlocks.CABLE_COATED_BLOCK.getId())) continue;
      var block = ForgeRegistries.BLOCKS.getValue(entry.getKey());
      if (block != null && block.defaultBlockState().is(CleanroomPollution.POST_PROCESS_EQUIPMENT))
        coated += entry.getValue();
    }
    return coated;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Cleanliness", cleanliness);
    tag.putInt("CleanlinessLimit", cleanlinessLimit);
    tag.putInt("CoatingCoverage", coatingCoverage);
    tag.putLong(LAST_SHARED_UPDATE_TAG, lastSharedCleanlinessUpdate);
    tag.putDouble(RECOVERY_PROGRESS_TAG, cleanlinessRecoveryProgress);
    tag.putLongArray(
        CLAIMED_INTERIOR_TAG,
        claimedInteriorPositions.stream().mapToLong(Long::longValue).toArray());
    tag.put("LastScan", lastScan.save());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    energy.setStored(tag.getInt("Energy"));
    cleanliness = Math.max(0, Math.min(MAX_CLEANLINESS, tag.getInt("Cleanliness")));
    cleanlinessLimit =
        tag.contains("CleanlinessLimit", Tag.TAG_INT)
            ? Math.max(
                BASE_CLEANLINESS_LIMIT, Math.min(MAX_CLEANLINESS, tag.getInt("CleanlinessLimit")))
            : BASE_CLEANLINESS_LIMIT;
    coatingCoverage = Math.max(0, Math.min(100, tag.getInt("CoatingCoverage")));
    lastSharedCleanlinessUpdate =
        tag.contains(LAST_SHARED_UPDATE_TAG, Tag.TAG_LONG)
            ? tag.getLong(LAST_SHARED_UPDATE_TAG)
            : -1L;
    double savedRecoveryProgress =
        tag.contains(RECOVERY_PROGRESS_TAG, Tag.TAG_DOUBLE)
            ? tag.getDouble(RECOVERY_PROGRESS_TAG)
            : 0.0D;
    cleanlinessRecoveryProgress =
        Double.isFinite(savedRecoveryProgress)
            ? Math.max(0.0D, Math.min(1.0D, savedRecoveryProgress))
            : 0.0D;
    lastScan =
        tag.contains("LastScan", Tag.TAG_COMPOUND)
            ? RoomScanResult.load(tag.getCompound("LastScan"))
            : RoomScanResult.notScanned();
    claimedInteriorPositions.clear();
    if (lastScan.isSealed() || lastScan.status() == RoomScanResult.Status.UNLOADED) {
      long[] savedClaims = tag.getLongArray(CLAIMED_INTERIOR_TAG);
      RoomScanner.Limits limits = RoomScanner.configuredLimits();
      int savedClaimLimit = Math.min(savedClaims.length, limits.maxVolume());
      for (int index = 0; index < savedClaimLimit; index++) {
        BlockPos savedPosition = BlockPos.of(savedClaims[index]);
        int savedDistance =
            Math.max(
                Math.max(
                    Math.abs(worldPosition.getX() - savedPosition.getX()),
                    Math.abs(worldPosition.getY() - savedPosition.getY())),
                Math.abs(worldPosition.getZ() - savedPosition.getZ()));
        if (savedDistance <= limits.maxDistance()) claimedInteriorPositions.add(savedClaims[index]);
      }
    }
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag tag = saveWithoutMetadata();
    tag.remove(CLAIMED_INTERIOR_TAG);
    tag.remove(LAST_SHARED_UPDATE_TAG);
    tag.remove(RECOVERY_PROGRESS_TAG);
    return tag;
  }

  @Nullable
  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
    if (packet.getTag() != null) load(packet.getTag());
  }

  private void sync() {
    setChanged();
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  @Override
  public void setRemoved() {
    if (level != null) CleanroomOccupancy.remove(level, worldPosition);
    super.setRemoved();
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
    if (capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
    return super.getCapability(capability, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    energyCapability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    energyCapability = LazyOptional.of(() -> energy);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.conditioner");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new ConditionerMenu(id, inventory, this);
  }

  private record SharedState(
      int cleanliness,
      int cleanlinessLimit,
      int coatingCoverage,
      int unprotectedEntities,
      int equipmentPollutionSources,
      int blockPollutionSources,
      long updatedAt,
      double recoveryProgress) {}
}
