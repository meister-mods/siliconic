package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

@SuppressWarnings({"null"})
public class SiliconProcessorMenu extends AbstractContainerMenu {
  private final SiliconProcessorBlockEntity processor;
  private final int machineSlots;

  public SiliconProcessorMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (SiliconProcessorBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public SiliconProcessorMenu(int id, Inventory inventory, SiliconProcessorBlockEntity processor) {
    super(ModMenus.SILICON_PROCESSOR.get(), id);
    this.processor = processor;
    boolean arcFurnace = processor.isArcFurnace();
    addSlot(
        new SlotItemHandler(
            processor.items(), SiliconProcessorBlockEntity.INPUT_SLOT, arcFurnace ? 26 : 44, 35));
    if (arcFurnace)
      addSlot(
          new SlotItemHandler(
              processor.items(), SiliconProcessorBlockEntity.CATALYST_SLOT, 53, 35));
    addSlot(
        new SlotItemHandler(processor.items(), SiliconProcessorBlockEntity.OUTPUT_SLOT, 116, 35));
    machineSlots = arcFurnace ? 3 : 2;
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 101 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 159));
    addDataSlots(processor.data());
  }

  public boolean isArcFurnace() {
    return processor.isArcFurnace();
  }

  public int energy() {
    return processor.data().get(0);
  }

  public int capacity() {
    return processor.data().get(1);
  }

  public int progress() {
    return processor.data().get(2);
  }

  public int maxProgress() {
    return processor.data().get(3);
  }

  public int energyPerTick() {
    return processor.data().get(4);
  }

  public int status() {
    return processor.data().get(5);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), copy = stack.copy();
    if (index < machineSlots) {
      if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
    } else {
      boolean moved = moveItemStackTo(stack, 0, 1, false);
      if (!moved && isArcFurnace()) moved = moveItemStackTo(stack, 1, 2, false);
      if (!moved) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return !processor.isRemoved()
        && player.distanceToSqr(processor.getBlockPos().getCenter()) <= 64;
  }
}
