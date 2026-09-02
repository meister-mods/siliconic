package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.config.SiliconicConfig;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("null")
public class EnergyBufferBlockEntity extends BlockEntity {
  private final BufferEnergyStorage energy =
      new BufferEnergyStorage(SiliconicConfig.VALUES.powerBufferCapacity.get());
  private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energy);
  private List<PowerNetworkTopology.Receiver> cachedReceivers = List.of();
  private long cachedTopologyRevision = Long.MIN_VALUE;
  private long lastTopologyScan = Long.MIN_VALUE;
  private int distributionCursor;
  private int lastComparatorSignal = -1;

  private final class BufferEnergyStorage extends EnergyStorage {
    BufferEnergyStorage(int capacity) {
      super(
          capacity,
          SiliconicConfig.VALUES.powerTransferPerConnection.get(),
          SiliconicConfig.VALUES.powerTransferPerConnection.get());
    }

    void setStored(int amount) {
      energy = Math.max(0, Math.min(capacity, amount));
    }

    void extractInternal(int amount) {
      energy = Math.max(0, energy - Math.max(0, amount));
      EnergyBufferBlockEntity.this.setChanged();
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) EnergyBufferBlockEntity.this.setChanged();
      return accepted;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
      int extracted = super.extractEnergy(amount, simulate);
      if (extracted > 0 && !simulate) EnergyBufferBlockEntity.this.setChanged();
      return extracted;
    }
  }

  public EnergyBufferBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.ENERGY_BUFFER.get(), pos, state);
  }

  public int energyStored() {
    return energy.getEnergyStored();
  }

  public int energyCapacity() {
    return energy.getMaxEnergyStored();
  }

  public int comparatorSignal() {
    return energy.getMaxEnergyStored() <= 0
        ? 0
        : Math.min(15, 15 * energy.getEnergyStored() / energy.getMaxEnergyStored());
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, EnergyBufferBlockEntity buffer) {
    buffer.pushEnergy(level, pos);
    int comparatorSignal = buffer.comparatorSignal();
    if (comparatorSignal != buffer.lastComparatorSignal) {
      buffer.lastComparatorSignal = comparatorSignal;
      level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
  }

  private void pushEnergy(Level level, BlockPos pos) {
    if (energy.getEnergyStored() <= 0) return;
    refreshTopology(level, pos);
    List<IEnergyStorage> targets = new ArrayList<>(cachedReceivers.size());
    for (PowerNetworkTopology.Receiver receiver : cachedReceivers) {
      if (!level.isLoaded(receiver.pos())) continue;
      BlockEntity blockEntity = level.getBlockEntity(receiver.pos());
      if (blockEntity == null || blockEntity instanceof EnergyBufferBlockEntity) continue;
      blockEntity
          .getCapability(ForgeCapabilities.ENERGY, receiver.side())
          .filter(IEnergyStorage::canReceive)
          .ifPresent(targets::add);
    }
    distribute(targets);
  }

  private void refreshTopology(Level level, BlockPos pos) {
    long revision = PowerNetworkTopology.revision(level);
    long gameTime = level.getGameTime();
    int cacheTicks = SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
    if (revision != cachedTopologyRevision
        || lastTopologyScan == Long.MIN_VALUE
        || gameTime < lastTopologyScan
        || gameTime - lastTopologyScan >= cacheTicks) {
      cachedReceivers =
          PowerNetworkTopology.discover(
              level, pos, SiliconicConfig.VALUES.powerNetworkMaxCables.get());
      cachedTopologyRevision = revision;
      lastTopologyScan = gameTime;
    }
  }

  private void distribute(List<IEnergyStorage> targets) {
    if (targets.isEmpty()) return;
    int connectionLimit = SiliconicConfig.VALUES.powerTransferPerConnection.get();
    int[] demands = new int[targets.size()];
    long totalDemand = 0;
    for (int index = 0; index < targets.size(); index++) {
      demands[index] = Math.max(0, targets.get(index).receiveEnergy(connectionLimit, true));
      totalDemand += demands[index];
    }
    int budget =
        (int)
            Math.min(
                Math.min((long) energy.getEnergyStored(), totalDemand),
                SiliconicConfig.VALUES.powerBufferOutputPerTick.get());
    if (budget <= 0) return;
    int start = Math.floorMod(distributionCursor, targets.size());
    int[] allocations = BalancedEnergyDistributor.allocate(demands, budget, start);
    for (int offset = 0; offset < targets.size(); offset++) {
      int index = (start + offset) % targets.size();
      if (allocations[index] <= 0) continue;
      int accepted =
          Math.min(allocations[index], targets.get(index).receiveEnergy(allocations[index], false));
      if (accepted > 0) energy.extractInternal(accepted);
    }
    distributionCursor = (start + Math.max(1, budget % targets.size())) % targets.size();
  }

  public void invalidateNetworkCache() {
    cachedTopologyRevision = Long.MIN_VALUE;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("DistributionCursor", distributionCursor);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    energy.setStored(tag.getInt("Energy"));
    distributionCursor = Math.max(0, tag.getInt("DistributionCursor"));
    invalidateNetworkCache();
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
}
