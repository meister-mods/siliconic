package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
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
public class WaferInverterBlockEntity extends BlockEntity implements MenuProvider {
  public static final int INPUT_SLOT = 0, OUTPUT_SLOT = 1, SLOT_COUNT = 2;
  public static final int ENERGY_CAPACITY = 50_000;
  public static final int PROCESS_TICKS = 100;

  private int progress;
  private final InverterEnergyStorage energy = new InverterEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot == INPUT_SLOT && PrototypeWaferBlockEntity.isCompleted(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
          return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot == INPUT_SLOT) progress = 0;
          setChanged();
        }
      };
  private final IItemHandler automationItems =
      new FilteredItemHandler(items, slot -> slot == INPUT_SLOT, slot -> slot == OUTPUT_SLOT);
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationItems);
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
            case 2 -> progress;
            case 3 -> PROCESS_TICKS;
            case 4 -> energyPerTick();
            case 5 -> status();
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

  private final class InverterEnergyStorage extends EnergyStorage {
    InverterEnergyStorage() {
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
      if (accepted > 0 && !simulate) WaferInverterBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public WaferInverterBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.WAFER_INVERTER.get(), pos, state);
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public boolean isInsideCleanroom() {
    return CleanroomOccupancy.isMachineInside(level, worldPosition);
  }

  private int energyPerTick() {
    return 20 * PrototypeWaferBlockEntity.levelOf(items.getStackInSlot(INPUT_SLOT));
  }

  private int status() {
    if (!isInsideCleanroom()) return 0;
    ItemStack input = items.getStackInSlot(INPUT_SLOT);
    if (!PrototypeWaferBlockEntity.isCompleted(input)) return 1;
    if (!canFitOutput(resultFor(input))) return 2;
    if (energy.getEnergyStored() < energyPerTick()) return 3;
    return 4;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, WaferInverterBlockEntity inverter) {
    ItemStack input = inverter.items.getStackInSlot(INPUT_SLOT);
    if (!PrototypeWaferBlockEntity.isCompleted(input)) {
      inverter.resetProgress();
      return;
    }
    if (!inverter.isInsideCleanroom()) return;
    ItemStack result = inverter.resultFor(input);
    if (!inverter.canFitOutput(result)) return;
    if (!inverter.energy.consumeInternal(inverter.energyPerTick())) return;
    inverter.progress++;
    if (inverter.progress >= PROCESS_TICKS) inverter.finishProcess(result);
    inverter.setChanged();
  }

  private ItemStack resultFor(ItemStack input) {
    ItemStack result = input.copyWithCount(1);
    PrototypeWaferBlockEntity.mirrorHorizontally(result);
    return result;
  }

  private boolean canFitOutput(ItemStack result) {
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    return output.isEmpty()
        || (ItemStack.isSameItemSameTags(output, result)
            && output.getCount() < output.getMaxStackSize());
  }

  private void finishProcess(ItemStack result) {
    items.extractItem(INPUT_SLOT, 1, false);
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) items.setStackInSlot(OUTPUT_SLOT, result);
    else output.grow(1);
    progress = 0;
  }

  private void resetProgress() {
    if (progress == 0) return;
    progress = 0;
    setChanged();
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
    if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
    energy.setStored(tag.getInt("Energy"));
    progress = Math.max(0, Math.min(PROCESS_TICKS - 1, tag.getInt("Progress")));
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
    itemCapability = LazyOptional.of(() -> automationItems);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.wafer_inverter");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new WaferInverterMenu(id, inventory, this);
  }
}
