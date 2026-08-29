package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
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
public class SiliconProcessorBlockEntity extends BlockEntity implements MenuProvider {
  public static final int INPUT_SLOT = 0, CATALYST_SLOT = 1, OUTPUT_SLOT = 2, SLOT_COUNT = 3;
  public static final int ENERGY_CAPACITY = 30_000;

  private int progress;
  private int clientStatus;
  private final ProcessorEnergyStorage energy = new ProcessorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot == OUTPUT_SLOT) return false;
          return ModMachineProcesses.accepts(machineKind(), slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot != OUTPUT_SLOT) progress = 0;
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
          if (level != null && level.isClientSide && index == 5) return clientStatus;
          MachineProcess process = primaryProcess();
          return switch (index) {
            case 0 -> energy.getEnergyStored();
            case 1 -> energy.getMaxEnergyStored();
            case 2 -> progress;
            case 3 -> process.ticks();
            case 4 -> process.energyPerTick();
            case 5 -> status();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          switch (index) {
            case 0 -> energy.setStored(value);
            case 2 -> progress = Math.max(0, value);
            case 5 -> clientStatus = value;
            default -> {
              // The remaining values are derived from the machine state.
            }
          }
        }

        @Override
        public int getCount() {
          return 6;
        }
      };

  private final class ProcessorEnergyStorage extends EnergyStorage {
    ProcessorEnergyStorage() {
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
      if (accepted > 0 && !simulate) SiliconProcessorBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public SiliconProcessorBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.SILICON_PROCESSOR.get(), pos, state);
  }

  public boolean isArcFurnace() {
    return getBlockState().is(ModBlocks.SILICON_ARC_FURNACE.get());
  }

  private MachineKind machineKind() {
    return isArcFurnace() ? MachineKind.SILICON_ARC_FURNACE : MachineKind.SILICON_PURIFIER;
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    if (!CleanroomOccupancy.isMachineInside(level, worldPosition)) return 5;
    MachineProcess process = currentProcess();
    if (process == null) {
      if (!ModMachineProcesses.accepts(machineKind(), INPUT_SLOT, items.getStackInSlot(INPUT_SLOT)))
        return 0;
      return isArcFurnace() ? 1 : 0;
    }
    if (!canFitOutput(process.result())) return 2;
    if (energy.getEnergyStored() < process.energyPerTick()) return 3;
    return 4;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, SiliconProcessorBlockEntity processor) {
    if (!CleanroomOccupancy.isMachineInside(level, pos)) return;
    MachineProcess process = processor.currentProcess();
    if (process == null || !processor.canFitOutput(process.result())) {
      if (processor.progress != 0) {
        processor.progress = 0;
        processor.setChanged();
      }
      return;
    }
    if (!processor.energy.consumeInternal(process.energyPerTick())) return;
    processor.progress++;
    if (processor.progress >= process.ticks()) processor.finishProcess(process);
    processor.setChanged();
  }

  private MachineProcess primaryProcess() {
    return ModMachineProcesses.primary(machineKind());
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(machineKind(), items, INPUT_SLOT, 2);
  }

  private boolean canFitOutput(ItemStack result) {
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) return true;
    return ItemStack.isSameItemSameTags(output, result)
        && output.getCount() + result.getCount() <= output.getMaxStackSize();
  }

  private void finishProcess(MachineProcess process) {
    ItemStack result = process.result(), output = items.getStackInSlot(OUTPUT_SLOT);
    process.consume(items, INPUT_SLOT, 2);
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
        isArcFurnace()
            ? "container.siliconic.silicon_arc_furnace"
            : "container.siliconic.silicon_purifier");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new SiliconProcessorMenu(id, inventory, this);
  }
}
