package io.github.meistermods.siliconic.fabrication;

import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class FabricationStationBlockEntity extends BlockEntity implements MenuProvider {
  public static final int INPUT_START = 0, INPUT_SLOTS = 9, OUTPUT_SLOT = 9, SLOT_COUNT = 10;
  public static final int ENERGY_CAPACITY = 60_000;

  private int progress;
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot >= INPUT_START
              && slot < INPUT_START + INPUT_SLOTS
              && ModMachineProcesses.accepts(machineKind(), slot - INPUT_START, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot < INPUT_START + INPUT_SLOTS) progress = 0;
          setChanged();
        }
      };
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          MachineProcess process = currentProcess();
          return switch (index) {
            case 0 -> energy.getEnergyStored();
            case 1 -> energy.getMaxEnergyStored();
            case 2 -> progress;
            case 3 -> process == null ? 0 : process.ticks();
            case 4 -> process == null ? 0 : process.energyPerTick();
            case 5 -> status(process);
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          switch (index) {
            case 0 -> energy.setStored(value);
            case 2 -> progress = Math.max(0, value);
            default -> {
              // The remaining values are derived from the shared process definition.
            }
          }
        }

        @Override
        public int getCount() {
          return 6;
        }
      };

  private final class StationEnergyStorage extends EnergyStorage {
    StationEnergyStorage() {
      super(ENERGY_CAPACITY, 2_000, 0);
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
      if (accepted > 0 && !simulate) FabricationStationBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public FabricationStationBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.FABRICATION_STATION.get(), pos, state);
  }

  public boolean isWaferFabricator() {
    return getBlockState().is(ModBlocks.WAFER_FABRICATOR.get());
  }

  private MachineKind machineKind() {
    return isWaferFabricator() ? MachineKind.WAFER_FABRICATOR : MachineKind.GATE_ASSEMBLER;
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    return status(currentProcess());
  }

  private int status(@Nullable MachineProcess process) {
    if (process == null) return 0;
    if (!canFitOutput(process.result())) return 1;
    if (energy.getEnergyStored() < process.energyPerTick()) return 2;
    return 3;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, FabricationStationBlockEntity station) {
    MachineProcess process = station.currentProcess();
    if (process == null || !station.canFitOutput(process.result())) {
      station.resetProgress();
      return;
    }
    if (!station.energy.consumeInternal(process.energyPerTick())) return;
    station.progress++;
    if (station.progress >= process.ticks()) station.finishProcess(process);
    station.setChanged();
  }

  private void resetProgress() {
    if (progress == 0) return;
    progress = 0;
    setChanged();
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(machineKind(), items, INPUT_START, INPUT_SLOTS);
  }

  private boolean canFitOutput(ItemStack result) {
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) return true;
    return ItemStack.isSameItemSameTags(output, result)
        && output.getCount() + result.getCount() <= output.getMaxStackSize();
  }

  private void finishProcess(MachineProcess process) {
    process.consume(items, INPUT_START, INPUT_SLOTS);
    ItemStack result = process.result();
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) items.setStackInSlot(OUTPUT_SLOT, result);
    else output.grow(result.getCount());
    progress = 0;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    items.deserializeNBT(tag.getCompound("Items"));
    energy.setStored(tag.getInt("Energy"));
    progress = Math.max(0, tag.getInt("Progress"));
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
    if (capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
    if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
    return super.getCapability(capability, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    energyCapability.invalidate();
    itemCapability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    energyCapability = LazyOptional.of(() -> energy);
    itemCapability = LazyOptional.of(() -> items);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable(
        isWaferFabricator()
            ? "container.siliconic.wafer_fabricator"
            : "container.siliconic.gate_assembler");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new FabricationStationMenu(id, inventory, this);
  }
}
