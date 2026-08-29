package io.github.meistermods.siliconic.reprocessing;

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
public class ReprocessingStationMenu extends AbstractContainerMenu {
  private static final int MACHINE_SLOTS = ReprocessingStationBlockEntity.SLOT_COUNT;
  private final ReprocessingStationBlockEntity station;

  public ReprocessingStationMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (ReprocessingStationBlockEntity)
            inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public ReprocessingStationMenu(
      int id, Inventory inventory, ReprocessingStationBlockEntity station) {
    super(ModMenus.REPROCESSING_STATION.get(), id);
    this.station = station;
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        addSlot(
            new SlotItemHandler(
                station.items(),
                ReprocessingStationBlockEntity.INPUT_START + row * 3 + column,
                20 + column * 18,
                34 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        addSlot(
            new SlotItemHandler(
                station.items(),
                ReprocessingStationBlockEntity.OUTPUT_START + row * 3 + column,
                114 + column * 18,
                34 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 137 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 195));
    addDataSlots(station.data());
  }

  public int energy() {
    return MenuDataSync.combine(station.data().get(0), station.data().get(1));
  }

  public int capacity() {
    return ReprocessingStationBlockEntity.ENERGY_CAPACITY;
  }

  public int progress() {
    return station.data().get(2);
  }

  public int maxProgress() {
    return station.data().get(3);
  }

  public int energyPerTick() {
    return station.data().get(4);
  }

  public int status() {
    return station.data().get(5);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, ReprocessingStationBlockEntity.INPUT_SLOTS, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !station.isRemoved() && player.distanceToSqr(station.getBlockPos().getCenter()) <= 64;
  }
}
