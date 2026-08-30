package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

@SuppressWarnings({"null"})
public class WaferInverterMenu extends AbstractContainerMenu {
  private static final int MACHINE_SLOTS = WaferInverterBlockEntity.SLOT_COUNT;
  private final WaferInverterBlockEntity inverter;

  public WaferInverterMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (WaferInverterBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public WaferInverterMenu(int id, Inventory inventory, WaferInverterBlockEntity inverter) {
    super(ModMenus.WAFER_INVERTER.get(), id);
    this.inverter = inverter;
    addSlot(new SlotItemHandler(inverter.items(), WaferInverterBlockEntity.INPUT_SLOT, 42, 39));
    addSlot(new SlotItemHandler(inverter.items(), WaferInverterBlockEntity.OUTPUT_SLOT, 116, 39));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 107 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 165));
    addDataSlots(inverter.data());
  }

  public int energy() {
    return MenuDataSync.combine(inverter.data().get(0), inverter.data().get(1));
  }

  public int capacity() {
    return WaferInverterBlockEntity.ENERGY_CAPACITY;
  }

  public int progress() {
    return inverter.data().get(2);
  }

  public int maxProgress() {
    return inverter.data().get(3);
  }

  public int energyPerTick() {
    return inverter.data().get(4);
  }

  public int status() {
    return inverter.data().get(5);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, 1, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !inverter.isRemoved() && player.distanceToSqr(inverter.getBlockPos().getCenter()) <= 64;
  }
}
