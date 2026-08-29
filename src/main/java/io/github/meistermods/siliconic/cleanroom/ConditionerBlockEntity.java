package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
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

  private final ConditionerEnergyStorage energy = new ConditionerEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private boolean powered;
  private int scanCooldown;
  private RoomScanResult lastScan = RoomScanResult.notScanned();
  private final int[] clientData = new int[3];
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

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, ConditionerBlockEntity conditioner) {
    boolean nextPowered = conditioner.energy.consumeInternal(ENERGY_PER_TICK);
    boolean powerChanged = nextPowered != conditioner.powered;
    conditioner.powered = nextPowered;
    if (state.getValue(ConditionerBlock.ACTIVE) != nextPowered)
      level.setBlock(pos, state.setValue(ConditionerBlock.ACTIVE, nextPowered), 3);

    if (nextPowered) {
      if (powerChanged || conditioner.scanCooldown <= 0) {
        conditioner.lastScan = RoomScanner.scan(level, pos);
        conditioner.scanCooldown = SCAN_INTERVAL;
        conditioner.sync();
      } else conditioner.scanCooldown--;
      conditioner.setChanged();
    } else {
      conditioner.scanCooldown = 0;
      if (powerChanged) conditioner.sync();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("Energy", energy.getEnergyStored());
    tag.put("LastScan", lastScan.save());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    energy.setStored(tag.getInt("Energy"));
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
