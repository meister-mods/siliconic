package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
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
  public static final int INPUT_SLOT = 0, CATALYST_SLOT = 1;
  public static final int MAGMA_SLOT = 2, OUTPUT_START = 3, OUTPUT_SLOTS = 9, SLOT_COUNT = 12;
  public static final int ENERGY_CAPACITY = 30_000;
  public static final int MAGMA_CAPACITY = 32_000;
  public static final int MAGMA_PER_TICK = 5;

  private int progress;
  private int clientStatus;
  private int magmaHeat;
  private List<ItemStack> pendingResults = List.of();
  private final ProcessorEnergyStorage energy = new ProcessorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot >= OUTPUT_START) return false;
          if (slot == MAGMA_SLOT) return requiresMagma() && magmaValue(stack) > 0;
          return ModMachineProcesses.accepts(machineKind(), slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot == INPUT_SLOT || slot == CATALYST_SLOT) {
            progress = 0;
            pendingResults = List.of();
          }
          setChanged();
        }
      };
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private final IItemHandler automationItems =
      new FilteredItemHandler(
          items,
          slot -> slot == INPUT_SLOT || slot == CATALYST_SLOT || slot == MAGMA_SLOT,
          slot ->
              (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS)
                  || (slot == MAGMA_SLOT && magmaValue(items.getStackInSlot(slot)) == 0));
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationItems);
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
            case 6 -> magmaHeat;
            case 7 -> MAGMA_CAPACITY;
            case 8 -> machineKind().requiresHeat() ? MAGMA_PER_TICK : 0;
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          switch (index) {
            case 0 -> energy.setStored(value);
            case 2 -> progress = Math.max(0, value);
            case 5 -> clientStatus = value;
            case 6 -> magmaHeat = Math.max(0, Math.min(value, MAGMA_CAPACITY));
            default -> {
              // The remaining values are derived from the machine state.
            }
          }
        }

        @Override
        public int getCount() {
          return 9;
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
    return machineKind() == MachineKind.SILICON_ARC_FURNACE;
  }

  public MachineKind machineKind() {
    if (getBlockState().getBlock() instanceof SiliconProcessorBlock processorBlock)
      return processorBlock.machineKind();
    return MachineKind.SILICON_ARC_FURNACE;
  }

  public boolean hasSecondaryInput() {
    return ModMachineProcesses.usesInputSlot(machineKind(), CATALYST_SLOT);
  }

  public boolean requiresMagma() {
    return machineKind().requiresHeat();
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    MachineProcess process = currentProcess();
    if (process == null) {
      if (!ModMachineProcesses.accepts(machineKind(), INPUT_SLOT, items.getStackInSlot(INPUT_SLOT)))
        return 0;
      return 1;
    }
    if (pendingResults.isEmpty()) {
      if (!canFitOutputs(process.outputCopies())) return 2;
    } else if (!canFitOutputs(pendingResults)) return 2;
    if (!pendingResults.isEmpty()) return 4;
    if (requiresMagma() && magmaHeat < MAGMA_PER_TICK) return 5;
    if (energy.getEnergyStored() < process.energyPerTick()) return 3;
    return 4;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, SiliconProcessorBlockEntity processor) {
    processor.absorbMagmaFuel();
    MachineProcess process = processor.currentProcess();
    if (process == null) {
      processor.resetProgress();
      updateActiveState(level, pos, state, false);
      return;
    }
    if (!processor.pendingResults.isEmpty()) {
      if (processor.canFitOutputs(processor.pendingResults))
        processor.finishProcess(process, processor.pendingResults);
      updateActiveState(level, pos, state, false);
      return;
    }
    if (!processor.canFitOutputs(process.outputCopies())) {
      processor.resetProgress();
      updateActiveState(level, pos, state, false);
      return;
    }
    if (!processor.consumeMagmaHeat()) {
      updateActiveState(level, pos, state, false);
      return;
    }
    if (!processor.energy.consumeInternal(process.energyPerTick())) {
      processor.refundMagmaHeat();
      updateActiveState(level, pos, state, false);
      return;
    }
    processor.progress++;
    if (processor.progress >= process.ticks()) {
      List<ItemStack> results = process.outputCopies();
      if (processor.canFitOutputs(results)) processor.finishProcess(process, results);
      else processor.pendingResults = copyStacks(results);
    }
    processor.setChanged();
    updateActiveState(level, pos, state, true);
  }

  private static void updateActiveState(
      Level level, BlockPos pos, BlockState state, boolean active) {
    if (state.getValue(SiliconProcessorBlock.ACTIVE) != active)
      level.setBlock(pos, state.setValue(SiliconProcessorBlock.ACTIVE, active), 3);
  }

  private void resetProgress() {
    if (progress == 0 && pendingResults.isEmpty()) return;
    progress = 0;
    pendingResults = List.of();
    setChanged();
  }

  private MachineProcess primaryProcess() {
    return ModMachineProcesses.primary(machineKind());
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(machineKind(), items, INPUT_SLOT, 2);
  }

  private void absorbMagmaFuel() {
    if (!requiresMagma()) return;
    ItemStack fuel = items.getStackInSlot(MAGMA_SLOT);
    int value = magmaValue(fuel);
    if (value <= 0 || magmaHeat + value > MAGMA_CAPACITY) return;
    ItemStack consumed = items.extractItem(MAGMA_SLOT, 1, false);
    ItemStack remainder = consumed.getCraftingRemainingItem();
    if (!remainder.isEmpty()) items.setStackInSlot(MAGMA_SLOT, remainder);
    magmaHeat += value;
    setChanged();
  }

  private boolean consumeMagmaHeat() {
    if (!requiresMagma()) return true;
    if (magmaHeat < MAGMA_PER_TICK) return false;
    magmaHeat -= MAGMA_PER_TICK;
    return true;
  }

  private void refundMagmaHeat() {
    if (requiresMagma()) magmaHeat = Math.min(MAGMA_CAPACITY, magmaHeat + MAGMA_PER_TICK);
  }

  public static int magmaValue(ItemStack stack) {
    if (stack.is(Items.MAGMA_CREAM)) return 2_000;
    if (stack.is(Items.MAGMA_BLOCK)) return 8_000;
    if (stack.is(Items.LAVA_BUCKET)) return 16_000;
    return 0;
  }

  private boolean canFitOutputs(List<ItemStack> results) {
    List<ItemStack> simulated = new ArrayList<>(OUTPUT_SLOTS);
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS; slot++) {
      simulated.add(items.getStackInSlot(slot).copy());
    }
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

  private void finishProcess(MachineProcess process, List<ItemStack> results) {
    if (!canFitOutputs(results)) return;
    process.consume(items, INPUT_SLOT, 2);
    results.forEach(this::insertOutput);
    progress = 0;
    pendingResults = List.of();
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

  private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
    return stacks.stream().map(ItemStack::copy).toList();
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
    tag.putInt("MagmaHeat", magmaHeat);
    ListTag pending = new ListTag();
    pendingResults.forEach(result -> pending.add(result.save(new CompoundTag())));
    tag.put("PendingResults", pending);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    CompoundTag itemData = tag.getCompound("Items").copy();
    itemData.putInt("Size", SLOT_COUNT);
    items.deserializeNBT(itemData);
    energy.setStored(tag.getInt("Energy"));
    progress = Math.max(0, tag.getInt("Progress"));
    magmaHeat = Math.max(0, Math.min(tag.getInt("MagmaHeat"), MAGMA_CAPACITY));
    ListTag pending = tag.getList("PendingResults", Tag.TAG_COMPOUND);
    List<ItemStack> loadedResults = new ArrayList<>(pending.size());
    for (int index = 0; index < pending.size(); index++) {
      ItemStack result = ItemStack.of(pending.getCompound(index));
      if (!result.isEmpty()) loadedResults.add(result);
    }
    if (loadedResults.isEmpty() && tag.contains("PendingResult", Tag.TAG_COMPOUND)) {
      ItemStack legacyResult = ItemStack.of(tag.getCompound("PendingResult"));
      if (!legacyResult.isEmpty()) loadedResults.add(legacyResult);
    }
    pendingResults = List.copyOf(loadedResults);
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
    return Component.translatable("container.siliconic." + machineKind().id());
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new SiliconProcessorMenu(id, inventory, this);
  }
}
