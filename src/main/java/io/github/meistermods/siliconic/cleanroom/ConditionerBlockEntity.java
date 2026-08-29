package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
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

  private final ConditionerEnergyStorage energy = new ConditionerEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private boolean powered;
  private int scanCooldown;
  private int cleanliness;
  private int cleanlinessLimit = BASE_CLEANLINESS_LIMIT;
  private int coatingCoverage;
  private RoomScanResult lastScan = RoomScanResult.notScanned();
  private final int[] clientData = new int[6];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> powered ? 1 : 0;
            case 3 -> cleanliness;
            case 4 -> cleanlinessLimit();
            case 5 -> coatingCoverage();
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
      if (amount <= 0 || energy < amount) return false;
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
    boolean nextPowered = conditioner.energy.consumeInternal(ENERGY_PER_TICK);
    boolean powerChanged = nextPowered != conditioner.powered;
    conditioner.powered = nextPowered;
    if (state.getValue(ConditionerBlock.ACTIVE) != nextPowered)
      level.setBlock(pos, state.setValue(ConditionerBlock.ACTIVE, nextPowered), 3);

    if (conditioner.scanCooldown > 0) conditioner.scanCooldown--;
    if (conditioner.scanCooldown == 0) {
      conditioner.lastScan = RoomScanner.scan(level, pos);
      CleanroomOccupancy.update(level, pos, conditioner.lastScan);
      conditioner.updateCleanliness();
      conditioner.scanCooldown = SCAN_INTERVAL;
      conditioner.sync();
    } else if (powerChanged) conditioner.sync();
    if (nextPowered) conditioner.setChanged();
  }

  private void updateCleanliness() {
    if (lastScan.isSealed()) {
      updateCleanlinessLimit();
      if (powered)
        cleanliness = Math.min(cleanlinessLimit, cleanliness + CLEANLINESS_RECOVERY_PER_SCAN);
      else cleanliness = Math.min(cleanliness, cleanlinessLimit);
    } else cleanliness = Math.max(0, cleanliness - CLEANLINESS_DECAY_PER_SCAN);
  }

  private void updateCleanlinessLimit() {
    int totalFaces = totalSurfaceFaces();
    int coatedFaces = coatedSurfaceFaces();
    if (totalFaces == 0) {
      cleanlinessLimit = BASE_CLEANLINESS_LIMIT;
      coatingCoverage = 0;
      return;
    }
    coatingCoverage = (100 * coatedFaces + totalFaces / 2) / totalFaces;
    int bonus =
        ((MAX_CLEANLINESS - BASE_CLEANLINESS_LIMIT) * coatedFaces + totalFaces / 2)
            / totalFaces;
    cleanlinessLimit = BASE_CLEANLINESS_LIMIT + bonus;
  }

  private int totalSurfaceFaces() {
    return lastScan.surfaceMaterials().values().stream().mapToInt(Integer::intValue).sum();
  }

  private int coatedSurfaceFaces() {
    return lastScan.surfaceMaterials().getOrDefault(ModBlocks.COATED_BLOCK.getId(), 0);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Cleanliness", cleanliness);
    tag.putInt("CleanlinessLimit", cleanlinessLimit);
    tag.putInt("CoatingCoverage", coatingCoverage);
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
                BASE_CLEANLINESS_LIMIT,
                Math.min(MAX_CLEANLINESS, tag.getInt("CleanlinessLimit")))
            : BASE_CLEANLINESS_LIMIT;
    coatingCoverage = Math.max(0, Math.min(100, tag.getInt("CoatingCoverage")));
    lastScan =
        tag.contains("LastScan", Tag.TAG_COMPOUND)
            ? RoomScanResult.load(tag.getCompound("LastScan"))
            : RoomScanResult.notScanned();
  }

  @Override
  public CompoundTag getUpdateTag() {
    return saveWithoutMetadata();
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
}
