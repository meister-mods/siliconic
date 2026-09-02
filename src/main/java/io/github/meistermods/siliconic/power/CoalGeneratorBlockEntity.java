package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.config.SiliconicConfig;
import io.github.meistermods.siliconic.logistics.LogisticsInventoryAccess;
import io.github.meistermods.siliconic.machine.FilteredItemHandler;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
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
public class CoalGeneratorBlockEntity extends BlockEntity
    implements MenuProvider, LogisticsInventoryAccess {
  public static final int FUEL_SLOT = 0;
  public static final int SLOT_COUNT = 1;
  public static final int ENERGY_CAPACITY = 40_000;
  public static final int GENERATION_PER_TICK = 40;

  private final GeneratorEnergyStorage energy = new GeneratorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
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
  private final int[] clientData = new int[8];
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
            case 7 -> SiliconicConfig.VALUES.generatorOutputPerTick.get();
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
  private List<PowerNetworkTopology.Receiver> cachedReceivers = List.of();
  private long cachedTopologyRevision = Long.MIN_VALUE;
  private long lastTopologyScan = Long.MIN_VALUE;

  private static final class GeneratorEnergyStorage extends EnergyStorage {
    GeneratorEnergyStorage() {
      super(ENERGY_CAPACITY, 0, SiliconicConfig.VALUES.powerTransferPerConnection.get());
    }

    void addInternal(int amount) {
      energy = Math.min(capacity, energy + amount);
    }

    void setStored(int amount) {
      energy = Math.max(0, Math.min(capacity, amount));
    }

    void extractInternal(int amount) {
      energy = Math.max(0, energy - Math.max(0, amount));
    }
  }

  public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.COAL_GENERATOR.get(), pos, state);
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
        generator.storeFuelRemainder(level, pos, remainder);
      }
    }
    if (generator.burnTime > 0
        && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored()) {
      generator.burnTime--;
      generator.energy.addInternal(SiliconicConfig.VALUES.generatorOutputPerTick.get());
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

  private void storeFuelRemainder(Level level, BlockPos pos, ItemStack remainder) {
    if (remainder.isEmpty()) return;
    ItemStack remainingFuel = items.getStackInSlot(FUEL_SLOT);
    if (remainingFuel.isEmpty()) {
      items.setStackInSlot(FUEL_SLOT, remainder);
      return;
    }
    if (ItemStack.isSameItemSameTags(remainingFuel, remainder)) {
      int moved =
          Math.min(
              remainder.getCount(), remainingFuel.getMaxStackSize() - remainingFuel.getCount());
      remainingFuel.grow(moved);
      remainder.shrink(moved);
    }
    if (!remainder.isEmpty())
      Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
  }

  private void pushEnergy(Level level, BlockPos pos) {
    long revision = PowerNetworkTopology.revision(level);
    int cacheTicks = SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
    long gameTime = level.getGameTime();
    if (revision != cachedTopologyRevision
        || lastTopologyScan == Long.MIN_VALUE
        || gameTime < lastTopologyScan
        || gameTime - lastTopologyScan >= cacheTicks) {
      cachedReceivers =
          PowerNetworkTopology.discover(
              level, pos, SiliconicConfig.VALUES.powerNetworkMaxCables.get());
      cachedTopologyRevision = revision;
      lastTopologyScan = gameTime;
    }

    List<IEnergyStorage> targets = new ArrayList<>(cachedReceivers.size());
    for (PowerNetworkTopology.Receiver receiver : cachedReceivers) {
      if (!level.isLoaded(receiver.pos())) continue;
      BlockEntity blockEntity = level.getBlockEntity(receiver.pos());
      if (blockEntity == null) continue;
      blockEntity.getCapability(ForgeCapabilities.ENERGY, receiver.side()).ifPresent(targets::add);
    }
    distributeEnergyEvenly(targets);
  }

  private void distributeEnergyEvenly(List<IEnergyStorage> targets) {
    if (targets.isEmpty() || energy.getEnergyStored() <= 0) return;
    int[] demands = new int[targets.size()];
    long totalDemand = 0;
    int transferLimit = SiliconicConfig.VALUES.powerTransferPerConnection.get();
    for (int index = 0; index < targets.size(); index++) {
      demands[index] =
          Math.max(
              0, Math.min(transferLimit, targets.get(index).receiveEnergy(transferLimit, true)));
      totalDemand += demands[index];
    }
    int budget = (int) Math.min(energy.getEnergyStored(), totalDemand);
    if (budget <= 0) return;

    int start = Math.floorMod(distributionCursor, targets.size());
    int[] allocations = BalancedEnergyDistributor.allocate(demands, budget, start);
    boolean transferred = false;
    for (int offset = 0; offset < targets.size(); offset++) {
      int index = (start + offset) % targets.size();
      int offered = allocations[index];
      if (offered <= 0) continue;
      int accepted = Math.min(offered, targets.get(index).receiveEnergy(offered, false));
      if (accepted <= 0) continue;
      energy.extractInternal(accepted);
      transferred = true;
    }
    int advance = budget % targets.size();
    distributionCursor = (start + Math.max(1, advance)) % targets.size();
    if (transferred) setChanged();
  }

  public void invalidateNetworkCache() {
    cachedTopologyRevision = Long.MIN_VALUE;
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
    if (tag.contains("Items")) {
      CompoundTag itemData = tag.getCompound("Items").copy();
      itemData.putInt("Size", SLOT_COUNT);
      items.deserializeNBT(itemData);
    } else if (tag.contains("Fuel"))
      items.setStackInSlot(FUEL_SLOT, ItemStack.of(tag.getCompound("Fuel")));
    burnTime = Math.max(0, tag.getInt("BurnTime"));
    totalBurnTime = Math.max(burnTime, tag.getInt("TotalBurnTime"));
    distributionCursor = Math.max(0, tag.getInt("DistributionCursor"));
    energy.setStored(tag.getInt("Energy"));
    invalidateNetworkCache();
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
