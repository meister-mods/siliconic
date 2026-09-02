package io.github.meistermods.siliconic.power;

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
public class CoalGeneratorMenu extends AbstractContainerMenu {
  private static final int MACHINE_SLOTS = CoalGeneratorBlockEntity.SLOT_COUNT;
  private final CoalGeneratorBlockEntity generator;

  public CoalGeneratorMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (CoalGeneratorBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public CoalGeneratorMenu(int id, Inventory inventory, CoalGeneratorBlockEntity generator) {
    super(ModMenus.COAL_GENERATOR.get(), id);
    this.generator = generator;
    addSlot(new SlotItemHandler(generator.items(), CoalGeneratorBlockEntity.FUEL_SLOT, 35, 38));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 96 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 154));
    addDataSlots(generator.data());
  }

  public int energy() {
    return MenuDataSync.combine(generator.data().get(0), generator.data().get(1));
  }

  public int capacity() {
    return CoalGeneratorBlockEntity.ENERGY_CAPACITY;
  }

  public int burnTime() {
    return MenuDataSync.combine(generator.data().get(2), generator.data().get(3));
  }

  public int totalBurnTime() {
    return MenuDataSync.combine(generator.data().get(4), generator.data().get(5));
  }

  public int status() {
    return generator.data().get(6);
  }

  public int generationPerTick() {
    return generator.data().get(7);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < MACHINE_SLOTS) {
      if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, MACHINE_SLOTS, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !generator.isRemoved()
        && player.distanceToSqr(generator.getBlockPos().getCenter()) <= 64;
  }
}
