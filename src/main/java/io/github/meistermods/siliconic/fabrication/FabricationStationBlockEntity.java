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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class FabricationStationBlockEntity extends BlockEntity
    implements MenuProvider, LogisticsInventoryAccess {
  public static final int INPUT_START = 0, INPUT_SLOTS = 9;
  public static final int OUTPUT_START = 9, OUTPUT_SLOTS = 9, SLOT_COUNT = 18;
  public static final int ENERGY_CAPACITY = 60_000;

  private int progress;
  private List<ItemStack> pendingResults = List.of();
  private boolean consumingPendingInputs;
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return isInputSlot(slot)
              && canModifyInputs()
              && ModMachineProcesses.accepts(
                  FabricationStationBlockEntity.this.level,
                  machineKind(),
                  slot - INPUT_START,
                  stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
          if (isInputSlot(slot) && !canModifyInputs()) return;
          super.setStackInSlot(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
          return isInputSlot(slot) && !canModifyInputs()
              ? stack
              : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
          return isInputSlot(slot) && !canModifyInputs()
              ? ItemStack.EMPTY
              : super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (isInputSlot(slot)) progress = 0;
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

  private static boolean isInputSlot(int slot) {
    return slot >= INPUT_START && slot < INPUT_START + INPUT_SLOTS;
  }

  private boolean canModifyInputs() {
    return pendingResults.isEmpty() || consumingPendingInputs;
  }

  public int status() {
    return status(currentProcess());
  }

  private int status(@Nullable MachineProcess process) {
    if (!CleanroomOccupancy.isMachineInside(level, worldPosition)) return 4;
    if (process == null) return 0;
    if (pendingResults.isEmpty()) {
      if (!canFitPossibleOutputs(process)) return 1;
    } else if (simulateOutputs(pendingResults) == null) return 1;
    if (!pendingResults.isEmpty()) return 3;
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
    if (!station.pendingResults.isEmpty()) {
      station.finishProcess(process, station.pendingResults);
      return;
    }
    if (!station.canFitPossibleOutputs(process)) {
      station.resetProgress();
      return;
    }
    if (!station.energy.consumeInternal(process.energyPerTick())) return;
    station.progress++;
    if (station.progress >= process.ticks()) {
      ItemStack result = CleanroomContamination.processResult(level, pos, process.result());
      List<ItemStack> outputs = outputsFor(process, result);
      if (!station.finishProcess(process, outputs)) station.pendingResults = outputs;
    }
    station.setChanged();
  }

  private void resetProgress() {
    if (progress == 0 && pendingResults.isEmpty()) return;
    progress = 0;
    pendingResults = List.of();
    setChanged();
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(level, machineKind(), items, INPUT_START, INPUT_SLOTS);
  }

  private static List<ItemStack> outputsFor(MachineProcess process, ItemStack result) {
    List<ItemStack> outputs = new ArrayList<>();
    outputs.add(result.copy());
    process.byproducts().forEach(byproduct -> outputs.add(byproduct.copy()));
    return List.copyOf(outputs);
  }

  private boolean canFitPossibleOutputs(MachineProcess process) {
    ItemStack intended = process.result();
    int contaminationChance = CleanroomContamination.contaminationChance(level, worldPosition);
    ItemStack contaminated = CleanroomContamination.contaminatedVersion(intended);
    if (contaminated.isEmpty()) return simulateOutputs(outputsFor(process, intended)) != null;
    return (contaminationChance < 100 && simulateOutputs(outputsFor(process, intended)) != null)
        || (contaminationChance > 0 && simulateOutputs(outputsFor(process, contaminated)) != null);
  }

  @Nullable
  private ItemStackHandler simulateOutputs(List<ItemStack> results) {
    ItemStackHandler simulated = new ItemStackHandler(OUTPUT_SLOTS);
    for (int slot = 0; slot < OUTPUT_SLOTS; slot++)
      simulated.setStackInSlot(slot, items.getStackInSlot(OUTPUT_START + slot).copy());
    for (ItemStack result : results)
      if (!ItemHandlerHelper.insertItemStacked(simulated, result.copy(), false).isEmpty())
        return null;
    return simulated;
  }

  private boolean finishProcess(MachineProcess process, List<ItemStack> results) {
    ItemStackHandler outputs = simulateOutputs(results);
    if (outputs == null) return false;
    consumingPendingInputs = true;
    try {
      process.consume(items, INPUT_START, INPUT_SLOTS);
    } finally {
      consumingPendingInputs = false;
    }
    for (int slot = 0; slot < OUTPUT_SLOTS; slot++)
      items.setStackInSlot(OUTPUT_START + slot, outputs.getStackInSlot(slot));
    progress = 0;
    pendingResults = List.of();
    setChanged();
    return true;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
    ListTag pending = new ListTag();
    pendingResults.forEach(result -> pending.add(result.save(new CompoundTag())));
    tag.put("PendingResults", pending);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    pendingResults = List.of();
    CompoundTag itemData = tag.getCompound("Items").copy();
    itemData.putInt("Size", SLOT_COUNT);
    items.deserializeNBT(itemData);
    energy.setStored(tag.getInt("Energy"));
    ListTag pending = tag.getList("PendingResults", Tag.TAG_COMPOUND);
    List<ItemStack> loadedResults = new ArrayList<>();
    for (int index = 0; index < pending.size(); index++) {
      ItemStack result = ItemStack.of(pending.getCompound(index));
      if (!result.isEmpty()) loadedResults.add(result);
    }
    if (loadedResults.isEmpty() && tag.contains("PendingResult", Tag.TAG_COMPOUND)) {
      ItemStack legacyResult = ItemStack.of(tag.getCompound("PendingResult"));
      if (!legacyResult.isEmpty()) loadedResults.add(legacyResult);
    }
    pendingResults = List.copyOf(loadedResults);
    progress =
        pendingResults.isEmpty()
            ? Math.max(0, Math.min(Integer.MAX_VALUE - 1, tag.getInt("Progress")))
            : 0;
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
