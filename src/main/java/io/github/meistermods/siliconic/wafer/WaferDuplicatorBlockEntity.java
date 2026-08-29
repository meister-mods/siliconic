package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.cleanroom.CleanroomContamination;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModItems;
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
  public static final int MATERIAL_START = 3, MATERIAL_SLOTS = 18;
  public static final int EXTRA_OUTPUT_START = 21, OUTPUT_SLOTS = 9, SLOT_COUNT = 29;
  public static final int ENERGY_CAPACITY = 100_000;
  private ItemStack pendingResult = ItemStack.EMPTY;
  private final DuplicatorEnergyStorage energy = new DuplicatorEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          if (slot == SOURCE_SLOT) return PrototypeWaferBlockEntity.isCompleted(stack);
          if (slot == BLANK_SLOT) return PrototypeWaferBlockEntity.isBlankWafer(stack);
          return slot >= MATERIAL_START && slot < MATERIAL_START + MATERIAL_SLOTS;
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (!isOutputSlot(slot)) pendingResult = ItemStack.EMPTY;
          setChanged();
        }
      };
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
  private final int[] clientData = new int[5];
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          if (level != null && level.isClientSide)
            return index >= 0 && index < clientData.length ? clientData[index] : 0;
          return switch (index) {
            case 0 -> MenuDataSync.low(energy.getEnergyStored());
            case 1 -> MenuDataSync.high(energy.getEnergyStored());
            case 2 -> MenuDataSync.low(currentCost());
            case 3 -> MenuDataSync.high(currentCost());
            case 4 -> status();
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          if (index >= 0 && index < clientData.length) clientData[index] = value;
        }

        @Override
        public int getCount() {
          return 5;
        }
      };

  private final class DuplicatorEnergyStorage extends EnergyStorage {
    DuplicatorEnergyStorage() {
      super(ENERGY_CAPACITY, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    boolean canConsumeInternal(int amount) {
      return amount > 0 && energy >= amount;
    }

    boolean consumeInternal(int amount) {
      if (!canConsumeInternal(amount)) return false;
      energy -= amount;
      return true;
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

  public int status() {
    if (!CleanroomOccupancy.isMachineInside(level, worldPosition)) return 7;
    ItemStack source = items.getStackInSlot(SOURCE_SLOT);
    ItemStack blank = items.getStackInSlot(BLANK_SLOT);
    if (!PrototypeWaferBlockEntity.isCompleted(source)) return 0;
    if (!PrototypeWaferBlockEntity.isBlankWafer(blank)) return 1;
    if (!source.is(blank.getItem())) return 2;
    if (pendingResult.isEmpty()) {
      if (!canFitOutput(source.copyWithCount(1))
          && !canFitOutput(new ItemStack(ModItems.CONTAMINATED_WAFER.get()))) return 3;
    } else if (!canFitOutput(pendingResult)) return 3;
    if (!energy.canConsumeInternal(currentCost())) return 4;
    if (!hasMaterials(PrototypeWaferBlockEntity.requiredComponents(source))) return 5;
    return 6;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, WaferDuplicatorBlockEntity duplicator) {
    if (level.getGameTime() % 20 == 0 && CleanroomOccupancy.isMachineInside(level, pos))
      duplicator.tryDuplicate();
  }

  private void tryDuplicate() {
    ItemStack source = items.getStackInSlot(SOURCE_SLOT);
    ItemStack blank = items.getStackInSlot(BLANK_SLOT);
    if (!PrototypeWaferBlockEntity.isCompleted(source)
        || !PrototypeWaferBlockEntity.isBlankWafer(blank)
        || !source.is(blank.getItem())) return;
    int cost = currentCost();
    if (!energy.canConsumeInternal(cost)) return;
    List<ItemStack> requirements = PrototypeWaferBlockEntity.requiredComponents(source);
    if (!hasMaterials(requirements)) return;
    ItemStack result = pendingResult;
    if (result.isEmpty()) {
      result =
          CleanroomContamination.processResult(
              level, worldPosition, source.copyWithCount(1));
      pendingResult = result;
    }
    int outputSlot = findOutputSlot(result);
    if (outputSlot < 0) {
      setChanged();
      return;
    }
    consumeMaterials(requirements);
    energy.consumeInternal(cost);
    items.extractItem(BLANK_SLOT, 1, false);
    insertOutput(outputSlot, result);
    pendingResult = ItemStack.EMPTY;
    setChanged();
  }

  public static int outputSlot(int index) {
    if (index < 0 || index >= OUTPUT_SLOTS) throw new IndexOutOfBoundsException(index);
    return index == 0 ? OUTPUT_SLOT : EXTRA_OUTPUT_START + index - 1;
  }

  private static boolean isOutputSlot(int slot) {
    return slot == OUTPUT_SLOT
        || (slot >= EXTRA_OUTPUT_START && slot < EXTRA_OUTPUT_START + OUTPUT_SLOTS - 1);
  }

  private boolean canFitOutput(ItemStack result) {
    return findOutputSlot(result) >= 0;
  }

  private int findOutputSlot(ItemStack result) {
    int emptySlot = -1;
    for (int index = 0; index < OUTPUT_SLOTS; index++) {
      int slot = outputSlot(index);
      ItemStack output = items.getStackInSlot(slot);
      if (output.isEmpty()) {
        if (emptySlot < 0) emptySlot = slot;
      } else if (ItemStack.isSameItemSameTags(output, result)
          && output.getCount() + result.getCount() <= output.getMaxStackSize()) return slot;
    }
    return emptySlot;
  }

  private void insertOutput(int slot, ItemStack result) {
    ItemStack output = items.getStackInSlot(slot);
    if (output.isEmpty()) items.setStackInSlot(slot, result);
    else output.grow(result.getCount());
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
