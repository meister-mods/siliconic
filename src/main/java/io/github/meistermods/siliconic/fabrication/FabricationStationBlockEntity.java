package io.github.meistermods.siliconic.fabrication;

import io.github.meistermods.siliconic.cleanroom.CleanroomContamination;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.logistics.LogisticsInventoryAccess;
import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.network.MenuDataSync;
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
public class FabricationStationBlockEntity extends BlockEntity
    implements MenuProvider, LogisticsInventoryAccess {
  public static final int INPUT_START = 0, INPUT_SLOTS = 9;
  public static final int OUTPUT_START = 9, OUTPUT_SLOTS = 9, SLOT_COUNT = 18;
  public static final int ENERGY_CAPACITY = 60_000;

  private int progress;
  private ItemStack pendingResult = ItemStack.EMPTY;
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot >= INPUT_START
              && slot < INPUT_START + INPUT_SLOTS
              && ModMachineProcesses.accepts(
                  FabricationStationBlockEntity.this.level,
                  machineKind(),
                  slot - INPUT_START,
                  stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot < INPUT_START + INPUT_SLOTS) {
            progress = 0;
            pendingResult = ItemStack.EMPTY;
          }
          setChanged();
        }
      };
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private final IItemHandler automationItems =
      new FilteredItemHandler(
          items,
          slot -> slot >= INPUT_START && slot < INPUT_START + INPUT_SLOTS,
          slot -> slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationItems);
  private final int[] clientData = new int[9];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          MachineProcess process = currentProcess();
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> MenuDataSync.low(progress);
            case 3 -> MenuDataSync.high(progress);
            case 4 -> MenuDataSync.low(process == null ? 0 : process.ticks());
            case 5 -> MenuDataSync.high(process == null ? 0 : process.ticks());
            case 6 -> MenuDataSync.low(process == null ? 0 : process.energyPerTick());
            case 7 -> MenuDataSync.high(process == null ? 0 : process.energyPerTick());
            case 8 -> status(process);
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
    return isWaferFabricator() ? MachineKind.WAFER_FABRICATOR : MachineKind.GATE_FABRICATOR;
  }

  public ItemStackHandler items() {
    return items;
  }

  @Override
  public IItemHandler logisticsInventory() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    return status(currentProcess());
  }

  private int status(@Nullable MachineProcess process) {
    if (!CleanroomOccupancy.isMachineInside(level, worldPosition)) return 4;
    if (process == null) return 0;
    if (pendingResult.isEmpty()) {
      if (!canFitPossibleOutput(process.result())) return 1;
    } else if (!canFitOutput(pendingResult)) return 1;
    if (!pendingResult.isEmpty()) return 3;
    if (energy.getEnergyStored() < process.energyPerTick()) return 2;
    return 3;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, FabricationStationBlockEntity station) {
    if (!CleanroomOccupancy.isMachineInside(level, pos)) return;
    MachineProcess process = station.currentProcess();
    if (process == null) {
      station.resetProgress();
      return;
    }
    if (!station.pendingResult.isEmpty()) {
      if (station.canFitOutput(station.pendingResult))
        station.finishProcess(process, station.pendingResult);
      return;
    }
    if (!station.canFitPossibleOutput(process.result())) {
      station.resetProgress();
      return;
    }
    if (!station.energy.consumeInternal(process.energyPerTick())) return;
    station.progress++;
    if (station.progress >= process.ticks()) {
      ItemStack result = CleanroomContamination.processResult(level, pos, process.result());
      if (station.canFitOutput(result)) station.finishProcess(process, result);
      else station.pendingResult = result;
    }
    station.setChanged();
  }

  private void resetProgress() {
    if (progress == 0 && pendingResult.isEmpty()) return;
    progress = 0;
    pendingResult = ItemStack.EMPTY;
    setChanged();
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(level, machineKind(), items, INPUT_START, INPUT_SLOTS);
  }

  private boolean canFitOutput(ItemStack result) {
    return findOutputSlot(result) >= 0;
  }

  private boolean canFitPossibleOutput(ItemStack intended) {
    int contaminationChance = CleanroomContamination.contaminationChance(level, worldPosition);
    ItemStack contaminated = CleanroomContamination.contaminatedVersion(intended);
    return (contaminationChance < 100 && canFitOutput(intended))
        || (contaminationChance > 0 && !contaminated.isEmpty() && canFitOutput(contaminated));
  }

  private int findOutputSlot(ItemStack result) {
    int emptySlot = -1;
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS; slot++) {
      ItemStack output = items.getStackInSlot(slot);
      if (output.isEmpty()) {
        if (emptySlot < 0) emptySlot = slot;
      } else if (ItemStack.isSameItemSameTags(output, result)
          && output.getCount() + result.getCount() <= output.getMaxStackSize()) return slot;
    }
    return emptySlot;
  }

  private void finishProcess(MachineProcess process, ItemStack result) {
    int outputSlot = findOutputSlot(result);
    if (outputSlot < 0) return;
    process.consume(items, INPUT_START, INPUT_SLOTS);
    ItemStack output = items.getStackInSlot(outputSlot);
    if (output.isEmpty()) items.setStackInSlot(outputSlot, result);
    else output.grow(result.getCount());
    progress = 0;
    pendingResult = ItemStack.EMPTY;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
    tag.put("PendingResult", pendingResult.save(new CompoundTag()));
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    CompoundTag itemData = tag.getCompound("Items").copy();
    itemData.putInt("Size", SLOT_COUNT);
    items.deserializeNBT(itemData);
    energy.setStored(tag.getInt("Energy"));
    pendingResult = ItemStack.of(tag.getCompound("PendingResult"));
    MachineProcess process = currentProcess();
    progress =
        process == null || !pendingResult.isEmpty()
            ? 0
            : Math.max(0, Math.min(process.ticks() - 1, tag.getInt("Progress")));
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
    return Component.translatable(
        isWaferFabricator()
            ? "container.siliconic.wafer_fabricator"
            : "container.siliconic.gate_fabricator");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new FabricationStationMenu(id, inventory, this);
  }
}
