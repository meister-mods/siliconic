package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
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
import net.minecraft.world.item.Items;
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
  public static final int ENERGY_PER_TICK = 40;
  public static final int ARC_FURNACE_TICKS = 200;
  public static final int PURIFIER_TICKS = 300;

  private int progress;
  private final ProcessorEnergyStorage energy = new ProcessorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot == OUTPUT_SLOT) return false;
          if (isArcFurnace()) {
            if (slot == INPUT_SLOT) return stack.is(Items.QUARTZ);
            return slot == CATALYST_SLOT && stack.is(Items.CHARCOAL);
          }
          return slot == INPUT_SLOT && stack.is(ModItems.CRUDE_SILICON.get());
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
          return switch (index) {
            case 0 -> energy.getEnergyStored();
            case 1 -> energy.getMaxEnergyStored();
            case 2 -> progress;
            case 3 -> processTicks();
            case 4 -> ENERGY_PER_TICK;
            case 5 -> status();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          switch (index) {
            case 0 -> energy.setStored(value);
            case 2 -> progress = Math.max(0, value);
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

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int processTicks() {
    return isArcFurnace() ? ARC_FURNACE_TICKS : PURIFIER_TICKS;
  }

  public int status() {
    if (!validInput()) return 0;
    if (isArcFurnace() && !items.getStackInSlot(CATALYST_SLOT).is(Items.CHARCOAL)) return 1;
    if (!canFitOutput()) return 2;
    if (energy.getEnergyStored() < ENERGY_PER_TICK) return 3;
    return 4;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, SiliconProcessorBlockEntity processor) {
    if (!processor.canProcess()) {
      if (processor.progress != 0) {
        processor.progress = 0;
        processor.setChanged();
      }
      return;
    }
    if (!processor.energy.consumeInternal(ENERGY_PER_TICK)) return;
    processor.progress++;
    if (processor.progress >= processor.processTicks()) processor.finishProcess();
    processor.setChanged();
  }

  private boolean canProcess() {
    return validInput()
        && (!isArcFurnace() || items.getStackInSlot(CATALYST_SLOT).is(Items.CHARCOAL))
        && canFitOutput();
  }

  private boolean validInput() {
    ItemStack input = items.getStackInSlot(INPUT_SLOT);
    return isArcFurnace() ? input.is(Items.QUARTZ) : input.is(ModItems.CRUDE_SILICON.get());
  }

  private ItemStack result() {
    return isArcFurnace()
        ? new ItemStack(ModItems.CRUDE_SILICON.get(), 2)
        : new ItemStack(ModItems.PURE_SILICON.get());
  }

  private boolean canFitOutput() {
    ItemStack result = result(), output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) return true;
    return ItemStack.isSameItemSameTags(output, result)
        && output.getCount() + result.getCount() <= output.getMaxStackSize();
  }

  private void finishProcess() {
    ItemStack result = result(), output = items.getStackInSlot(OUTPUT_SLOT);
    items.extractItem(INPUT_SLOT, 1, false);
    if (isArcFurnace()) items.extractItem(CATALYST_SLOT, 1, false);
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
