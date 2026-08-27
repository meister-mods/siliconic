package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
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
public class WaferDuplicatorBlockEntity extends BlockEntity implements MenuProvider {
  public static final int SOURCE_SLOT = 0, BLANK_SLOT = 1, OUTPUT_SLOT = 2;
  public static final int MATERIAL_START = 3, MATERIAL_SLOTS = 18, SLOT_COUNT = 21;
  public static final int ENERGY_CAPACITY = 100_000;
  private final DuplicatorEnergyStorage energy = new DuplicatorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot == SOURCE_SLOT) return PrototypeWaferBlockEntity.isCompleted(stack);
          if (slot == BLANK_SLOT) return PrototypeWaferBlockEntity.isBlankWafer(stack);
          return slot != OUTPUT_SLOT;
        }

        @Override
        protected void onContentsChanged(int slot) {
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
            case 2 -> currentCost();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          if (index == 0) energy.setStored(value);
        }

        @Override
        public int getCount() {
          return 3;
        }
      };

  private final class DuplicatorEnergyStorage extends EnergyStorage {
    DuplicatorEnergyStorage() {
      super(ENERGY_CAPACITY, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) WaferDuplicatorBlockEntity.this.setChanged();
      return accepted;
    }
  }

  public WaferDuplicatorBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.WAFER_DUPLICATOR.get(), pos, state);
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int currentCost() {
    return 10_000 * PrototypeWaferBlockEntity.levelOf(items.getStackInSlot(SOURCE_SLOT));
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, WaferDuplicatorBlockEntity duplicator) {
    if (level.getGameTime() % 20 == 0) duplicator.tryDuplicate();
  }

  private void tryDuplicate() {
    ItemStack source = items.getStackInSlot(SOURCE_SLOT);
    ItemStack blank = items.getStackInSlot(BLANK_SLOT);
    if (!PrototypeWaferBlockEntity.isCompleted(source)
        || !PrototypeWaferBlockEntity.isBlankWafer(blank)
        || !source.is(blank.getItem())
        || !items.getStackInSlot(OUTPUT_SLOT).isEmpty()) return;
    int cost = currentCost();
    if (cost <= 0 || energy.extractEnergy(cost, true) < cost) return;
    List<ItemStack> requirements = PrototypeWaferBlockEntity.requiredComponents(source);
    if (!hasMaterials(requirements)) return;
    consumeMaterials(requirements);
    energy.extractEnergy(cost, false);
    items.extractItem(BLANK_SLOT, 1, false);
    items.setStackInSlot(OUTPUT_SLOT, source.copyWithCount(1));
    setChanged();
  }

  private boolean hasMaterials(List<ItemStack> requirements) {
    for (ItemStack requirement : requirements) {
      int available = 0;
      for (int slot = MATERIAL_START; slot < SLOT_COUNT; slot++) {
        ItemStack candidate = items.getStackInSlot(slot);
        if (ItemStack.isSameItemSameTags(requirement, candidate)) available += candidate.getCount();
      }
      if (available < requirement.getCount()) return false;
    }
    return true;
  }

  private void consumeMaterials(List<ItemStack> requirements) {
    for (ItemStack requirement : requirements) {
      int remaining = requirement.getCount();
      for (int slot = MATERIAL_START; slot < SLOT_COUNT && remaining > 0; slot++) {
        ItemStack candidate = items.getStackInSlot(slot);
        if (!ItemStack.isSameItemSameTags(requirement, candidate)) continue;
        int extracted = Math.min(remaining, candidate.getCount());
        items.extractItem(slot, extracted, false);
        remaining -= extracted;
      }
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    items.deserializeNBT(tag.getCompound("Items"));
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
    itemCapability = LazyOptional.of(() -> items);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.wafer_duplicator");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new WaferDuplicatorMenu(id, inventory, this);
  }
}
