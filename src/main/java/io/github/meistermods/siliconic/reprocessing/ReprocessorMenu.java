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
public class ReprocessorMenu extends AbstractContainerMenu {
  private static final int MACHINE_SLOTS = ReprocessorBlockEntity.SLOT_COUNT;
  private final ReprocessorBlockEntity reprocessor;

  public ReprocessorMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (ReprocessorBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public ReprocessorMenu(int id, Inventory inventory, ReprocessorBlockEntity reprocessor) {
    super(ModMenus.REPROCESSOR.get(), id);
    this.reprocessor = reprocessor;
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        addSlot(
            new SlotItemHandler(
                reprocessor.items(),
                ReprocessorBlockEntity.INPUT_START + row * 3 + column,
                20 + column * 18,
                34 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        addSlot(
            new SlotItemHandler(
                reprocessor.items(),
                ReprocessorBlockEntity.OUTPUT_START + row * 3 + column,
                114 + column * 18,
                34 + row * 18));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 137 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 195));
    addDataSlots(reprocessor.data());
  }

  public int energy() {
    return MenuDataSync.combine(reprocessor.data().get(0), reprocessor.data().get(1));
  }

  public int capacity() {
    return ReprocessorBlockEntity.ENERGY_CAPACITY;
  }

  public int progress() {
    return reprocessor.data().get(2);
  }

  public int maxProgress() {
    return reprocessor.data().get(3);
  }

  public int energyPerTick() {
    return reprocessor.data().get(4);
  }

  public int status() {
    return reprocessor.data().get(5);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, ReprocessorBlockEntity.INPUT_SLOTS, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !reprocessor.isRemoved()
        && player.distanceToSqr(reprocessor.getBlockPos().getCenter()) <= 64;
  }
}
