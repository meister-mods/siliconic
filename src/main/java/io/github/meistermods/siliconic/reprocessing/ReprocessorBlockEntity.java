package io.github.meistermods.siliconic.reprocessing;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
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
public class ReprocessorBlockEntity extends BlockEntity implements MenuProvider {
  public static final int INPUT_START = 0, INPUT_SLOTS = 9;
  public static final int OUTPUT_START = 9, OUTPUT_SLOTS = 9, SLOT_COUNT = 18;
  public static final int ENERGY_CAPACITY = 60_000;

  private int progress;
  private final ReprocessorEnergyStorage energy = new ReprocessorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot >= INPUT_START
              && slot < INPUT_START + INPUT_SLOTS
              && ReprocessingProcess.accepts(stack);
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
  private final int[] clientData = new int[6];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          ReprocessingProcess process = currentProcess();
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> progress;
            case 3 -> process == null ? 0 : process.ticks();
            case 4 -> process == null ? 0 : process.energyPerTick();
            case 5 -> status(process);
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

  private final class ReprocessorEnergyStorage extends EnergyStorage {
    ReprocessorEnergyStorage() {
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
      if (accepted > 0 && !simulate) ReprocessorBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public ReprocessorBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.REPROCESSOR.get(), pos, state);
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

  private int status(@Nullable ReprocessingProcess process) {
    if (process == null) return 0;
    if (!canFitOutputs(process.outputs())) return 1;
    if (energy.getEnergyStored() < process.energyPerTick()) return 2;
    return 3;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, ReprocessorBlockEntity reprocessor) {
    ReprocessingProcess process = reprocessor.currentProcess();
    if (process == null) {
      reprocessor.resetProgress();
      return;
    }
    if (!reprocessor.canFitOutputs(process.outputs())) return;
    if (!reprocessor.energy.consumeInternal(process.energyPerTick())) return;
    reprocessor.progress++;
    if (reprocessor.progress >= process.ticks()) reprocessor.finishProcess(process);
    reprocessor.setChanged();
  }

  @Nullable
  private ReprocessingProcess currentProcess() {
    return ReprocessingProcess.find(items, INPUT_START, INPUT_SLOTS);
  }

  private void resetProgress() {
    if (progress == 0) return;
    progress = 0;
    setChanged();
  }

  private boolean canFitOutputs(List<ItemStack> results) {
    List<ItemStack> simulated = new ArrayList<>(OUTPUT_SLOTS);
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS; slot++)
      simulated.add(items.getStackInSlot(slot).copy());
    for (ItemStack result : results) if (!insertSimulated(simulated, result)) return false;
    return true;
  }

  private boolean insertSimulated(List<ItemStack> simulated, ItemStack result) {
    int remaining = result.getCount();
    for (ItemStack output : simulated) {
      if (!ItemStack.isSameItemSameTags(output, result)) continue;
      int moved = Math.min(remaining, output.getMaxStackSize() - output.getCount());
      output.grow(moved);
      remaining -= moved;
      if (remaining == 0) return true;
    }
    for (int index = 0; index < simulated.size() && remaining > 0; index++) {
      if (!simulated.get(index).isEmpty()) continue;
      int moved = Math.min(remaining, result.getMaxStackSize());
      simulated.set(index, result.copyWithCount(moved));
      remaining -= moved;
    }
    return remaining == 0;
  }

  private void finishProcess(ReprocessingProcess process) {
    if (!canFitOutputs(process.outputs())) return;
    consumeInput(process);
    for (ItemStack result : process.outputCopies()) insertOutput(result);
    progress = 0;
  }

  private void consumeInput(ReprocessingProcess process) {
    int remaining = process.inputCount();
    for (int slot = INPUT_START; slot < INPUT_START + INPUT_SLOTS && remaining > 0; slot++) {
      ItemStack stack = items.getStackInSlot(slot);
      if (!stack.is(process.input())) continue;
      int extracted = Math.min(remaining, stack.getCount());
      items.extractItem(slot, extracted, false);
      remaining -= extracted;
    }
  }

  private void insertOutput(ItemStack result) {
    int remaining = result.getCount();
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS; slot++) {
      ItemStack output = items.getStackInSlot(slot);
      if (!ItemStack.isSameItemSameTags(output, result)) continue;
      int moved = Math.min(remaining, output.getMaxStackSize() - output.getCount());
      output.grow(moved);
      remaining -= moved;
      if (remaining == 0) return;
    }
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS && remaining > 0; slot++) {
      if (!items.getStackInSlot(slot).isEmpty()) continue;
      int moved = Math.min(remaining, result.getMaxStackSize());
      items.setStackInSlot(slot, result.copyWithCount(moved));
      remaining -= moved;
    }
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
    return Component.translatable("container.siliconic.reprocessor");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new ReprocessorMenu(id, inventory, this);
  }
}
