package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.logistics.LogisticsInventoryAccess;
import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineKind.ThermalProfile;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModItems;
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
import net.minecraft.world.item.Item;
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
public class SiliconProcessorBlockEntity extends BlockEntity
    implements MenuProvider, LogisticsInventoryAccess {
  public static final int INPUT_SLOT = 0, CATALYST_SLOT = 1, COMPONENT_SLOT = 2;
  public static final int INPUT_SLOTS = 3;
  public static final int MAGMA_SLOT = 3, OUTPUT_START = 4, OUTPUT_SLOTS = 9, SLOT_COUNT = 13;
  public static final int ENERGY_CAPACITY = 30_000;
  public static final int MAGMA_CAPACITY = 32_000;
  private static final int LAYOUT_VERSION = 2;
  private static final int PRESSURE_LIMIT = 1_000;
  private static final int MAX_SYNCED_TEMPERATURE = Short.MAX_VALUE;

  private int progress;
  private final int[] clientData = new int[15];
  private int magmaHeat;
  private int temperature;
  private int stability;
  private int pressure;
  private int operationMode;
  private int ventedTicks;
  private int tickCounter;
  private int lastComparatorSignal = -1;
  private List<ItemStack> pendingResults = List.of();

  private final ProcessorEnergyStorage energy = new ProcessorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot >= OUTPUT_START) return false;
          if (slot == MAGMA_SLOT) return requiresMagma() && magmaValue(stack) > 0;
          return ModMachineProcesses.accepts(
              SiliconProcessorBlockEntity.this.level, machineKind(), slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot >= INPUT_SLOT && slot < INPUT_SLOT + INPUT_SLOTS) {
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
          slot ->
              slot == MAGMA_SLOT
                  || (slot >= INPUT_SLOT && slot < INPUT_SLOT + INPUT_SLOTS && canModifyInputs()),
          slot ->
              (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS)
                  || (slot == MAGMA_SLOT && magmaValue(items.getStackInSlot(slot)) == 0));
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationItems);

  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          MachineProcess process = displayProcess();
          ThermalProfile profile = machineKind().thermalProfile();
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> progress;
            case 3 -> effectiveMaxTicks(process);
            case 4 -> dynamicEnergyPerTick(process);
            case 5 -> status();
            case 6 -> magmaHeat;
            case 7 -> MAGMA_CAPACITY;
            case 8 -> profile == null ? 0 : profile.magmaPerHeatingTick();
            case 9 -> temperature;
            case 10 -> profile == null ? 0 : profile.targetTemperature();
            case 11 -> stability;
            case 12 -> pressure;
            case 13 -> operationMode;
            case 14 -> phase();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          if (index >= 0 && index < clientData.length) clientData[index] = value;
        }

        @Override
        public int getCount() {
          return 15;
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

  public MachineKind machineKind() {
    if (getBlockState().getBlock() instanceof SiliconProcessorBlock processorBlock)
      return processorBlock.machineKind();
    return MachineKind.SILICON_ARC_FURNACE;
  }

  public boolean isArcFurnace() {
    return machineKind() == MachineKind.SILICON_ARC_FURNACE;
  }

  public boolean hasInputSlot(int relativeSlot) {
    return ModMachineProcesses.usesInputSlot(level, machineKind(), relativeSlot);
  }

  public int visibleInputSlots() {
    int count = 1;
    if (hasInputSlot(CATALYST_SLOT)) count++;
    if (hasInputSlot(COMPONENT_SLOT)) count++;
    return count;
  }

  public boolean requiresMagma() {
    return machineKind().requiresHeat();
  }

  public ItemStackHandler items() {
    return items;
  }

  @Override
  public IItemHandler logisticsInventory() {
    return items;
  }

  public boolean canModifyInputs() {
    return progress == 0 && pendingResults.isEmpty();
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    if (ventedTicks > 0) return 7;
    MachineProcess process = currentProcess();
    if (process == null) {
      if (!ModMachineProcesses.accepts(
          level, machineKind(), INPUT_SLOT, items.getStackInSlot(INPUT_SLOT))) return 0;
      return 1;
    }
    List<ItemStack> outputs = pendingResults.isEmpty() ? dynamicOutputs(process) : pendingResults;
    if (!canFitOutputs(outputs) && machineKind() != MachineKind.CHLORINATION_REACTOR) return 2;
    if (!pendingResults.isEmpty()) return machineKind() == MachineKind.CHLORINATION_REACTOR ? 9 : 2;
    if (requiresMagma() && !temperatureReady()) {
      ThermalProfile profile = machineKind().thermalProfile();
      if (temperature > profile.targetTemperature() + profile.tolerance()) return 8;
      if (magmaHeat < profile.magmaPerHeatingTick()) return 5;
      return 6;
    }
    if (pressure >= 800) return 9;
    if (energy.getEnergyStored() < dynamicEnergyPerTick(process)) return 3;
    return 4;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, SiliconProcessorBlockEntity processor) {
    processor.tickCounter++;
    processor.absorbMagmaFuel();
    MachineProcess process = processor.currentProcess();

    if (processor.ventedTicks > 0) {
      processor.ventedTicks--;
      processor.updateTemperature(process, false);
      processor.decayIdleState();
      updateActiveState(level, pos, state, false);
      processor.finishServerTick(level, pos, state);
      return;
    }

    if (process == null) {
      processor.resetProgress();
      processor.updateTemperature(null, false);
      processor.decayIdleState();
      updateActiveState(level, pos, state, false);
      processor.finishServerTick(level, pos, state);
      return;
    }

    if (!processor.pendingResults.isEmpty()) {
      processor.updateTemperature(process, false);
      processor.decayStability();
      if (processor.machineKind() == MachineKind.CHLORINATION_REACTOR) {
        if (processor.canFitOutputs(processor.pendingResults)) {
          processor.finishProcess(process, processor.pendingResults);
        } else {
          processor.pressure = Math.min(PRESSURE_LIMIT, processor.pressure + 8);
          if (processor.pressure >= PRESSURE_LIMIT) processor.ventBatch(process);
        }
      } else if (processor.canFitOutputs(processor.pendingResults)) {
        processor.finishProcess(process, processor.pendingResults);
      }
      updateActiveState(level, pos, state, false);
      processor.finishServerTick(level, pos, state);
      return;
    }

    boolean outputReady = processor.canFitOutputs(processor.dynamicOutputs(process));
    boolean canStart =
        processor.progress > 0
            || outputReady
            || processor.machineKind() == MachineKind.CHLORINATION_REACTOR;
    boolean heating = processor.updateTemperature(process, canStart);
    if (!canStart || !processor.temperatureReady()) {
      processor.decayStability();
      updateActiveState(level, pos, state, heating);
      processor.finishServerTick(level, pos, state);
      return;
    }

    int energyUse = processor.dynamicEnergyPerTick(process);
    if (!processor.energy.consumeInternal(energyUse)) {
      processor.decayStability();
      updateActiveState(level, pos, state, false);
      processor.finishServerTick(level, pos, state);
      return;
    }

    processor.progress++;
    processor.onActiveProcessTick();
    if (processor.progress >= processor.effectiveMaxTicks(process)) {
      List<ItemStack> results = processor.dynamicOutputs(process);
      if (processor.canFitOutputs(results)) processor.finishProcess(process, results);
      else processor.pendingResults = copyStacks(results);
    }
    processor.setChanged();
    updateActiveState(level, pos, state, true);
    processor.finishServerTick(level, pos, state);
  }

  private static void updateActiveState(
      Level level, BlockPos pos, BlockState state, boolean active) {
    if (state.getValue(SiliconProcessorBlock.ACTIVE) != active)
      level.setBlock(pos, state.setValue(SiliconProcessorBlock.ACTIVE, active), 3);
  }

  private void finishServerTick(Level level, BlockPos pos, BlockState state) {
    int signal = analogSignal();
    if (signal != lastComparatorSignal) {
      lastComparatorSignal = signal;
      level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
    setChanged();
  }

  private void decayIdleState() {
    decayStability();
    if (tickCounter % 4 == 0) pressure = Math.max(0, pressure - 1);
  }

  private void decayStability() {
    if (machineKind() == MachineKind.DISTILLATION_TOWER && tickCounter % 4 == 0)
      stability = Math.max(0, stability - 1);
  }

  private void onActiveProcessTick() {
    if (machineKind() == MachineKind.CHLORINATION_REACTOR) pressure = Math.min(900, pressure + 2);
    if (machineKind() == MachineKind.DISTILLATION_TOWER) stability = Math.min(1_000, stability + 2);
  }

  private boolean updateTemperature(@Nullable MachineProcess process, boolean canOperate) {
    ThermalProfile profile = machineKind().thermalProfile();
    if (profile == null) return false;
    boolean forcedCooling =
        machineKind() == MachineKind.SIEMENS_REACTOR && process != null && phase() == 4;
    if (forcedCooling) {
      temperature = Math.max(0, temperature - 12);
      return false;
    }
    boolean heating = false;
    if (process != null
        && canOperate
        && temperature < profile.targetTemperature()
        && magmaHeat >= profile.magmaPerHeatingTick()) {
      magmaHeat -= profile.magmaPerHeatingTick();
      temperature = Math.min(profile.targetTemperature(), temperature + profile.heatingRate());
      heating = true;
    }
    if (tickCounter % profile.passiveCoolingInterval() == 0)
      temperature = Math.max(0, temperature - 1);
    return heating;
  }

  private boolean temperatureReady() {
    ThermalProfile profile = machineKind().thermalProfile();
    if (profile == null) return true;
    if (machineKind() == MachineKind.SIEMENS_REACTOR && phase() == 4) return true;
    return temperature >= profile.targetTemperature() - profile.tolerance()
        && temperature <= profile.targetTemperature() + profile.tolerance();
  }

  private void resetProgress() {
    if (progress == 0 && pendingResults.isEmpty()) return;
    progress = 0;
    pendingResults = List.of();
    setChanged();
  }

  private MachineProcess primaryProcess() {
    return ModMachineProcesses.primary(level, machineKind());
  }

  private MachineProcess displayProcess() {
    MachineProcess process = currentProcess();
    return process == null ? primaryProcess() : process;
  }

  @Nullable
  private MachineProcess currentProcess() {
    return ModMachineProcesses.findMatching(level, machineKind(), items, INPUT_SLOT, INPUT_SLOTS);
  }

  private int effectiveMaxTicks(MachineProcess process) {
    if (machineKind() != MachineKind.DISTILLATION_TOWER) return process.ticks();
    if (operationMode == 1) return Math.max(1, process.ticks() / 2);
    return Math.max(1, process.ticks() - process.ticks() * stability / 4_000);
  }

  private int dynamicEnergyPerTick(MachineProcess process) {
    int currentPhase = phase();
    if (machineKind() == MachineKind.SILICON_ARC_FURNACE)
      return switch (currentPhase) {
        case 0, 1 -> process.energyPerTick();
        case 2 -> process.energyPerTick() + 50;
        case 3 -> process.energyPerTick() + 25;
        default -> Math.max(10, process.energyPerTick() - 10);
      };
    if (machineKind() == MachineKind.SIEMENS_REACTOR)
      return switch (currentPhase) {
        case 0, 1 -> process.energyPerTick() + 10;
        case 2 -> Math.max(20, process.energyPerTick() - 20);
        case 3 -> process.energyPerTick();
        case 4 -> 10;
        default -> process.energyPerTick();
      };
    return process.energyPerTick();
  }

  private int phase() {
    MachineProcess process = currentProcess();
    int max = process == null ? primaryProcess().ticks() : effectiveMaxTicks(process);
    if (progress <= 0) return 0;
    if (machineKind() == MachineKind.SILICON_ARC_FURNACE) {
      int percent = progress * 100 / Math.max(1, max);
      if (percent < 17) return 1;
      if (percent < 55) return 2;
      if (percent < 88) return 3;
      return 4;
    }
    if (machineKind() == MachineKind.SIEMENS_REACTOR) {
      int percent = progress * 100 / Math.max(1, max);
      if (percent < 19) return 1;
      if (percent < 32) return 2;
      if (percent < 82) return 3;
      return 4;
    }
    return 1;
  }

  private List<ItemStack> dynamicOutputs(MachineProcess process) {
    List<ItemStack> results = new ArrayList<>(process.outputCopies());
    if (machineKind() == MachineKind.SILICON_ARC_FURNACE) {
      ItemStack electrode = items.getStackInSlot(COMPONENT_SLOT);
      if (electrode.isDamageableItem()
          && electrode.getDamageValue() * 4 >= electrode.getMaxDamage() * 3)
        results.add(new ItemStack(ModItems.SILICON_SLAG.get()));
    }
    if (machineKind() == MachineKind.DISTILLATION_TOWER && operationMode == 1) {
      ItemStack reducedCut = results.get(0).copy();
      reducedCut.shrink(1);
      results.set(0, reducedCut);
      results.add(new ItemStack(ModItems.DISTILLATION_RESIDUE.get()));
    }
    return results;
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

  public static int magmaValue(ItemStack stack) {
    if (stack.is(Items.MAGMA_CREAM)) return 2_000;
    if (stack.is(Items.MAGMA_BLOCK)) return 8_000;
    if (stack.is(Items.LAVA_BUCKET)) return 16_000;
    return 0;
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
    for (int index = 0; index < simulated.size(); index++) {
      if (!allowedOutputSlot(result.getItem(), index)) continue;
      ItemStack output = simulated.get(index);
      if (!ItemStack.isSameItemSameTags(output, result)) continue;
      int moved = Math.min(remaining, output.getMaxStackSize() - output.getCount());
      output.grow(moved);
      remaining -= moved;
      if (remaining == 0) return true;
    }
    for (int index = 0; index < simulated.size() && remaining > 0; index++) {
      if (!allowedOutputSlot(result.getItem(), index) || !simulated.get(index).isEmpty()) continue;
      int moved = Math.min(remaining, result.getMaxStackSize());
      simulated.set(index, result.copyWithCount(moved));
      remaining -= moved;
    }
    return remaining == 0;
  }

  private boolean allowedOutputSlot(Item item, int relativeSlot) {
    if (machineKind() != MachineKind.CHEMICAL_RECYCLER) return true;
    if (item == ModItems.CRUDE_TRICHLOROSILANE.get()) return relativeSlot < 4;
    if (item == ModItems.HYDROGEN_CHLORIDE.get()) return relativeSlot >= 4 && relativeSlot < 8;
    return relativeSlot == 8;
  }

  private void finishProcess(MachineProcess process, List<ItemStack> results) {
    if (!canFitOutputs(results)) return;
    process.consume(items, INPUT_SLOT, INPUT_SLOTS);
    results.forEach(this::insertOutput);
    progress = 0;
    pendingResults = List.of();
    if (machineKind() == MachineKind.CHLORINATION_REACTOR) pressure = Math.max(0, pressure - 500);
  }

  private void ventBatch(MachineProcess process) {
    process.consume(items, INPUT_SLOT, INPUT_SLOTS);
    progress = 0;
    pressure = 200;
    pendingResults = List.of();
    ventedTicks = 100;
  }

  private void insertOutput(ItemStack result) {
    int remaining = result.getCount();
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS; slot++) {
      int relative = slot - OUTPUT_START;
      if (!allowedOutputSlot(result.getItem(), relative)) continue;
      ItemStack output = items.getStackInSlot(slot);
      if (!ItemStack.isSameItemSameTags(output, result)) continue;
      int moved = Math.min(remaining, output.getMaxStackSize() - output.getCount());
      output.grow(moved);
      remaining -= moved;
      if (remaining == 0) return;
    }
    for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_SLOTS && remaining > 0; slot++) {
      int relative = slot - OUTPUT_START;
      if (!allowedOutputSlot(result.getItem(), relative) || !items.getStackInSlot(slot).isEmpty())
        continue;
      int moved = Math.min(remaining, result.getMaxStackSize());
      items.setStackInSlot(slot, result.copyWithCount(moved));
      remaining -= moved;
    }
  }

  public boolean handleMenuButton(int id) {
    if (id == 0
        && machineKind() == MachineKind.DISTILLATION_TOWER
        && progress == 0
        && pendingResults.isEmpty()) {
      operationMode = operationMode == 0 ? 1 : 0;
      setChanged();
      return true;
    }
    if (id == 1
        && machineKind() == MachineKind.SIEMENS_REACTOR
        && progress > 0
        && pendingResults.isEmpty()) {
      MachineProcess process = currentProcess();
      ItemStack partial = new ItemStack(ModItems.PARTIAL_POLYSILICON_ROD.get());
      if (process == null || !canFitOutputs(List.of(partial))) return false;
      process.consume(items, INPUT_SLOT, INPUT_SLOTS);
      insertOutput(partial);
      progress = 0;
      setChanged();
      return true;
    }
    return false;
  }

  public int analogSignal() {
    int machineStatus = status();
    if (machineStatus == 2 || !pendingResults.isEmpty()) return 15;
    if (machineKind() == MachineKind.CHEMICAL_RECYCLER) {
      ItemStack feed = items.getStackInSlot(INPUT_SLOT);
      if (feed.is(ModItems.SILICON_TETRACHLORIDE.get())
          && !items.getStackInSlot(CATALYST_SLOT).is(ModItems.HYDROGEN.get())) return 10;
      if (currentProcess() == null) return 5;
    }
    MachineProcess process = currentProcess();
    if (process == null || progress == 0) return 0;
    return Math.max(1, Math.min(14, progress * 14 / effectiveMaxTicks(process)));
  }

  private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
    return stacks.stream().map(ItemStack::copy).toList();
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.putInt("LayoutVersion", LAYOUT_VERSION);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
    tag.putInt("MagmaHeat", magmaHeat);
    tag.putInt("Temperature", temperature);
    tag.putInt("Stability", stability);
    tag.putInt("Pressure", pressure);
    tag.putInt("OperationMode", operationMode);
    tag.putInt("VentedTicks", ventedTicks);
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
    if (tag.getInt("LayoutVersion") < LAYOUT_VERSION) migrateLegacySlots();
    energy.setStored(tag.getInt("Energy"));
    magmaHeat = Math.max(0, Math.min(tag.getInt("MagmaHeat"), MAGMA_CAPACITY));
    temperature = Math.max(0, Math.min(tag.getInt("Temperature"), MAX_SYNCED_TEMPERATURE));
    stability = Math.max(0, Math.min(tag.getInt("Stability"), 1_000));
    pressure = Math.max(0, Math.min(tag.getInt("Pressure"), PRESSURE_LIMIT));
    operationMode = tag.getInt("OperationMode") == 0 ? 0 : 1;
    ventedTicks = Math.max(0, Math.min(100, tag.getInt("VentedTicks")));
    ListTag pending = tag.getList("PendingResults", Tag.TAG_COMPOUND);
    List<ItemStack> loadedResults = new ArrayList<>(Math.min(pending.size(), OUTPUT_SLOTS));
    for (int index = 0; index < pending.size() && loadedResults.size() < OUTPUT_SLOTS; index++) {
      ItemStack result = ItemStack.of(pending.getCompound(index));
      if (!result.isEmpty()) loadedResults.add(result);
    }
    if (loadedResults.isEmpty() && tag.contains("PendingResult", Tag.TAG_COMPOUND)) {
      ItemStack legacyResult = ItemStack.of(tag.getCompound("PendingResult"));
      if (!legacyResult.isEmpty()) loadedResults.add(legacyResult);
    }
    pendingResults = List.copyOf(loadedResults);
    MachineProcess process = currentProcess();
    progress =
        process == null || !pendingResults.isEmpty()
            ? 0
            : Math.max(0, Math.min(effectiveMaxTicks(process) - 1, tag.getInt("Progress")));
  }

  private void migrateLegacySlots() {
    List<ItemStack> old = new ArrayList<>(12);
    for (int slot = 0; slot < 12; slot++) old.add(items.getStackInSlot(slot).copy());
    for (int slot = 0; slot < SLOT_COUNT; slot++) items.setStackInSlot(slot, ItemStack.EMPTY);
    items.setStackInSlot(INPUT_SLOT, old.get(0));
    items.setStackInSlot(CATALYST_SLOT, old.get(1));
    items.setStackInSlot(MAGMA_SLOT, old.get(2));
    for (int index = 0; index < OUTPUT_SLOTS; index++)
      items.setStackInSlot(OUTPUT_START + index, old.get(3 + index));
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
