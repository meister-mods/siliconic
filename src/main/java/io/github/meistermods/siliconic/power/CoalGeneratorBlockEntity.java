package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class CoalGeneratorBlockEntity extends BlockEntity implements MenuProvider {
  public static final int FUEL_SLOT = 0;
  public static final int ENERGY_CAPACITY = 40_000;
  public static final int GENERATION_PER_TICK = 40;
  private static final int TRANSFER_PER_CONNECTION = 200;
  private static final int MAX_CABLES_PER_NETWORK = 4_096;

  private final GeneratorEnergyStorage energy = new GeneratorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot == FUEL_SLOT && burnDuration(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
          setChanged();
        }
      };
  private final IItemHandler automationItems =
      new FilteredItemHandler(
          items,
          slot -> slot == FUEL_SLOT,
          slot -> slot == FUEL_SLOT && burnDuration(items.getStackInSlot(slot)) <= 0);
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automationItems);
  private final int[] clientData = new int[7];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> MenuDataSync.low(burnTime);
            case 3 -> MenuDataSync.high(burnTime);
            case 4 -> MenuDataSync.low(totalBurnTime);
            case 5 -> MenuDataSync.high(totalBurnTime);
            case 6 -> status();
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
  private int burnTime;
  private int totalBurnTime;
  private int distributionCursor;

  private static final class GeneratorEnergyStorage extends EnergyStorage {
    GeneratorEnergyStorage() {
      super(ENERGY_CAPACITY, 0, 200);
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

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
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

  private int status() {
    if (energy.getEnergyStored() >= energy.getMaxEnergyStored()
        && (burnTime > 0 || burnDuration(items.getStackInSlot(FUEL_SLOT)) > 0)) return 2;
    if (burnTime > 0) return 1;
    return 0;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, CoalGeneratorBlockEntity generator) {
    boolean wasLit = state.getValue(CoalGeneratorBlock.LIT);
    generator.pushEnergy(level, pos);
    ItemStack fuel = generator.items.getStackInSlot(FUEL_SLOT);
    if (generator.burnTime <= 0
        && !fuel.isEmpty()
        && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored()) {
      int duration = burnDuration(fuel);
      if (duration > 0) {
        generator.burnTime = duration;
        generator.totalBurnTime = duration;
        ItemStack consumed = generator.items.extractItem(FUEL_SLOT, 1, false);
        ItemStack remainder = consumed.getCraftingRemainingItem();
        if (!remainder.isEmpty()) generator.items.setStackInSlot(FUEL_SLOT, remainder);
      }
    }
    if (generator.burnTime > 0
        && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored()) {
      generator.burnTime--;
      generator.energy.addInternal(GENERATION_PER_TICK);
      generator.setChanged();
    }
    boolean lit =
        generator.burnTime > 0
            && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored();
    if (lit != wasLit) level.setBlock(pos, state.setValue(CoalGeneratorBlock.LIT, lit), 3);
  }

  private static int burnDuration(ItemStack stack) {
    return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
  }

  private void pushEnergy(Level level, BlockPos pos) {
    Map<BlockPos, Direction> receivers = new LinkedHashMap<>();
    ArrayDeque<BlockPos> pendingCables = new ArrayDeque<>();
    Set<BlockPos> visitedCables = new HashSet<>();

    for (Direction direction : Direction.values()) {
      BlockPos neighborPos = pos.relative(direction);
      if (level.getBlockState(neighborPos).getBlock() instanceof PowerCableBlock)
        pendingCables.add(neighborPos);
      else if (level.getBlockEntity(neighborPos) != null)
        receivers.putIfAbsent(neighborPos, direction.getOpposite());
    }

    while (!pendingCables.isEmpty() && visitedCables.size() < MAX_CABLES_PER_NETWORK) {
      BlockPos cablePos = pendingCables.removeFirst();
      if (!visitedCables.add(cablePos)) continue;
      BlockState cableState = level.getBlockState(cablePos);
      if (!(cableState.getBlock() instanceof PowerCableBlock)) continue;

      for (BlockPos connected : PowerCableBlock.connectedCables(level, cablePos, cableState))
        if (!visitedCables.contains(connected)) pendingCables.addLast(connected);

      for (Direction direction : Direction.values()) {
        BlockPos receiverPos = cablePos.relative(direction);
        if (receiverPos.equals(pos)
            || level.getBlockState(receiverPos).getBlock() instanceof PowerCableBlock) continue;
        if (level.getBlockEntity(receiverPos) != null)
          receivers.putIfAbsent(receiverPos, direction.getOpposite());
      }
    }

    List<IEnergyStorage> targets = new ArrayList<>(receivers.size());
    for (Map.Entry<BlockPos, Direction> receiver : receivers.entrySet()) {
      BlockEntity blockEntity = level.getBlockEntity(receiver.getKey());
      if (blockEntity == null) continue;
      blockEntity
          .getCapability(ForgeCapabilities.ENERGY, receiver.getValue())
          .ifPresent(targets::add);
    }
    distributeEnergyEvenly(targets);
  }

  private void distributeEnergyEvenly(List<IEnergyStorage> targets) {
    if (targets.isEmpty() || energy.getEnergyStored() <= 0) return;
    int[] demands = new int[targets.size()];
    int totalDemand = 0;
    for (int index = 0; index < targets.size(); index++) {
      demands[index] =
          Math.max(
              0,
              Math.min(
                  TRANSFER_PER_CONNECTION,
                  targets.get(index).receiveEnergy(TRANSFER_PER_CONNECTION, true)));
      totalDemand += demands[index];
    }
    int budget = Math.min(energy.getEnergyStored(), totalDemand);
    if (budget <= 0) return;

    int start = Math.floorMod(distributionCursor, targets.size());
    int[] allocations = balancedAllocations(demands, budget, start);
    boolean transferred = false;
    for (int offset = 0; offset < targets.size(); offset++) {
      int index = (start + offset) % targets.size();
      int offered = allocations[index];
      if (offered <= 0) continue;
      int accepted = Math.min(offered, targets.get(index).receiveEnergy(offered, false));
      if (accepted <= 0) continue;
      energy.extractEnergy(accepted, false);
      transferred = true;
    }
    int advance = budget % targets.size();
    distributionCursor = (start + Math.max(1, advance)) % targets.size();
    if (transferred) setChanged();
  }

  private static int[] balancedAllocations(int[] demands, int budget, int start) {
    int[] allocations = new int[demands.length];
    boolean[] active = new boolean[demands.length];
    int activeCount = 0;
    for (int index = 0; index < demands.length; index++) {
      active[index] = demands[index] > 0;
      if (active[index]) activeCount++;
    }

    int remaining = budget;
    while (remaining > 0 && activeCount > 0) {
      int share = remaining / activeCount;
      if (share == 0) {
        for (int offset = 0; offset < demands.length && remaining > 0; offset++) {
          int index = (start + offset) % demands.length;
          if (!active[index]) continue;
          allocations[index]++;
          remaining--;
        }
        break;
      }

      boolean cappedReceiver = false;
      for (int index = 0; index < demands.length; index++) {
        if (!active[index]) continue;
        int unmetDemand = demands[index] - allocations[index];
        if (unmetDemand > share) continue;
        allocations[index] += unmetDemand;
        remaining -= unmetDemand;
        active[index] = false;
        activeCount--;
        cappedReceiver = true;
      }
      if (cappedReceiver) continue;

      for (int index = 0; index < demands.length; index++)
        if (active[index]) {
          allocations[index] += share;
          remaining -= share;
        }
      for (int offset = 0; offset < demands.length && remaining > 0; offset++) {
        int index = (start + offset) % demands.length;
        if (!active[index]) continue;
        allocations[index]++;
        remaining--;
      }
    }
    return allocations;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("BurnTime", burnTime);
    tag.putInt("TotalBurnTime", totalBurnTime);
    tag.putInt("DistributionCursor", distributionCursor);
    tag.putInt("Energy", energy.getEnergyStored());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
    else if (tag.contains("Fuel"))
      items.setStackInSlot(FUEL_SLOT, ItemStack.of(tag.getCompound("Fuel")));
    burnTime = Math.max(0, tag.getInt("BurnTime"));
    totalBurnTime = Math.max(burnTime, tag.getInt("TotalBurnTime"));
    distributionCursor = Math.max(0, tag.getInt("DistributionCursor"));
    energy.setStored(tag.getInt("Energy"));
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
    return Component.translatable("container.siliconic.coal_generator");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new CoalGeneratorMenu(id, inventory, this);
  }
}
