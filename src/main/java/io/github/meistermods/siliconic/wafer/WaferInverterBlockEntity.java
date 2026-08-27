package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class WaferInverterBlockEntity extends BlockEntity {
  private final InverterEnergyStorage energy = new InverterEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);

  public WaferInverterBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.WAFER_INVERTER.get(), pos, state);
  }

  private final class InverterEnergyStorage extends EnergyStorage {
    InverterEnergyStorage() {
      super(50_000, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) WaferInverterBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public int getEnergyStored() {
    return energy.getEnergyStored();
  }

  public int costFor(ItemStack wafer) {
    return 2_000 * PrototypeWaferBlockEntity.levelOf(wafer);
  }

  public boolean invert(ItemStack wafer) {
    int cost = costFor(wafer);
    if (cost <= 0 || energy.extractEnergy(cost, true) < cost) return false;
    energy.extractEnergy(cost, false);
    PrototypeWaferBlockEntity.mirrorHorizontally(wafer);
    setChanged();
    return true;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("Energy", energy.getEnergyStored());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    energy.setStored(tag.getInt("Energy"));
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
