package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModMenus;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

@SuppressWarnings({"null"})
public class WaferDuplicatorMenu extends AbstractContainerMenu {
  private static final int MACHINE_SLOTS = WaferDuplicatorBlockEntity.SLOT_COUNT;
  private final WaferDuplicatorBlockEntity duplicator;

  public WaferDuplicatorMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (WaferDuplicatorBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public WaferDuplicatorMenu(int id, Inventory inventory, WaferDuplicatorBlockEntity duplicator) {
    super(ModMenus.WAFER_DUPLICATOR.get(), id);
    this.duplicator = duplicator;
    addSlot(new SlotItemHandler(duplicator.items(), 0, 20, 31));
    addSlot(new SlotItemHandler(duplicator.items(), 1, 61, 31));
    addSlot(new SlotItemHandler(duplicator.items(), 2, 138, 31));
    for (int row = 0; row < 2; row++)
      for (int column = 0; column < 9; column++)
        addSlot(
            new SlotItemHandler(
                duplicator.items(),
                WaferDuplicatorBlockEntity.MATERIAL_START + row * 9 + column,
                8 + column * 18,
                68 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 147 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 207));
    addDataSlots(duplicator.data());
  }

  public int energy() {
    return MenuDataSync.combine(duplicator.data().get(0), duplicator.data().get(1));
  }

  public int capacity() {
    return WaferDuplicatorBlockEntity.ENERGY_CAPACITY;
  }

  public int cost() {
    return MenuDataSync.combine(duplicator.data().get(2), duplicator.data().get(3));
  }

  public List<ItemStack> requirements() {
    return PrototypeWaferBlockEntity.requiredComponents(
        duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.SOURCE_SLOT));
  }

  public int status() {
    return duplicator.data().get(4);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) return ItemStack.EMPTY;
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !duplicator.isRemoved()
        && player.distanceToSqr(duplicator.getBlockPos().getCenter()) <= 64;
  }
}
