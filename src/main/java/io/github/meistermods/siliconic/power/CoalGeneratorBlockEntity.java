package io.github.meistermods.siliconic.power;

import org.jetbrains.annotations.Nullable;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;

@SuppressWarnings({"null"})
public class CoalGeneratorBlockEntity extends BlockEntity {
  public static final int GENERATION_PER_TICK = 40;
  private final GeneratorEnergyStorage energy = new GeneratorEnergyStorage();
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private ItemStack fuel = ItemStack.EMPTY;
  private int burnTime;

  private static final class GeneratorEnergyStorage extends EnergyStorage {
    GeneratorEnergyStorage() {
      super(40_000, 0, 200);
    }

    void addInternal(int amount) {
      energy = Math.min(capacity, energy + amount);
    }

    void setStored(int amount) {
      energy = Math.max(0, Math.min(capacity, amount));
    }
  }

  public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.COAL_GENERATOR.get(), pos, state);
  }

  public boolean hasFuel() {
    return !fuel.isEmpty();
  }

  public int getFuelCount() {
    return fuel.getCount();
  }

  public int getBurnTime() {
    return burnTime;
  }

  public int getEnergyStored() {
    return energy.getEnergyStored();
  }

  public int getEnergyCapacity() {
    return energy.getMaxEnergyStored();
  }

  public boolean canInsertFuel(ItemStack stack) {
    return fuel.isEmpty()
        || (ItemStack.isSameItemSameTags(fuel, stack) && fuel.getCount() < fuel.getMaxStackSize());
  }

  public void insertFuel(ItemStack stack) {
    if (fuel.isEmpty()) fuel = stack.copyWithCount(1);
    else fuel.grow(1);
    setChanged();
  }

  public ItemStack removeFuel() {
    ItemStack result = fuel;
    fuel = ItemStack.EMPTY;
    setChanged();
    return result;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, CoalGeneratorBlockEntity generator) {
    boolean wasLit = state.getValue(CoalGeneratorBlock.LIT);
    if (generator.burnTime <= 0
        && !generator.fuel.isEmpty()
        && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored()) {
      int duration = ForgeHooks.getBurnTime(generator.fuel, RecipeType.SMELTING);
      if (duration > 0) {
        generator.burnTime = duration;
        generator.fuel.shrink(1);
        if (generator.fuel.isEmpty()) generator.fuel = ItemStack.EMPTY;
      }
    }
    if (generator.burnTime > 0) {
      generator.burnTime--;
      generator.energy.addInternal(GENERATION_PER_TICK);
      generator.setChanged();
    }
    generator.pushEnergy(level, pos);
    boolean lit = generator.burnTime > 0;
    if (lit != wasLit) level.setBlock(pos, state.setValue(CoalGeneratorBlock.LIT, lit), 3);
  }

  private void pushEnergy(Level level, BlockPos pos) {
    for (Direction direction : Direction.values()) {
      if (energy.getEnergyStored() <= 0) break;
      BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
      if (neighbor == null) continue;
      neighbor
          .getCapability(ForgeCapabilities.ENERGY, direction.getOpposite())
          .ifPresent(
              storage -> {
                int offered = Math.min(200, energy.getEnergyStored());
                int accepted = storage.receiveEnergy(offered, false);
                if (accepted > 0) energy.extractEnergy(accepted, false);
              });
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Fuel", fuel.save(new CompoundTag()));
    tag.putInt("BurnTime", burnTime);
    tag.putInt("Energy", energy.getEnergyStored());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    fuel = ItemStack.of(tag.getCompound("Fuel"));
    burnTime = tag.getInt("BurnTime");
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
