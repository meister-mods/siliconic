package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.recipe.MachineKind;
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
  public static final int INPUT_Y = 37;
  public static final int MAGMA_X = 82;
  public static final int OUTPUT_X = 110;
  public static final int OUTPUT_Y = 20;

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
    int inputCount = processor.visibleInputSlots();
    addProcessSlot(SiliconProcessorBlockEntity.INPUT_SLOT, inputX(0, inputCount), INPUT_Y);
    if (processor.hasInputSlot(SiliconProcessorBlockEntity.CATALYST_SLOT))
      addProcessSlot(SiliconProcessorBlockEntity.CATALYST_SLOT, inputX(1, inputCount), INPUT_Y);
    if (processor.hasInputSlot(SiliconProcessorBlockEntity.COMPONENT_SLOT))
      addProcessSlot(SiliconProcessorBlockEntity.COMPONENT_SLOT, inputX(2, inputCount), INPUT_Y);
    if (processor.requiresMagma())
      addSlot(
          new SlotItemHandler(
              processor.items(), SiliconProcessorBlockEntity.MAGMA_SLOT, MAGMA_X, INPUT_Y));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        addSlot(
            new SlotItemHandler(
                processor.items(),
                SiliconProcessorBlockEntity.OUTPUT_START + row * 3 + column,
                OUTPUT_X + column * 18,
                OUTPUT_Y + row * 18));
    machineSlots =
        1
            + (processor.hasInputSlot(SiliconProcessorBlockEntity.CATALYST_SLOT) ? 1 : 0)
            + (processor.hasInputSlot(SiliconProcessorBlockEntity.COMPONENT_SLOT) ? 1 : 0)
            + (processor.requiresMagma() ? 1 : 0)
            + SiliconProcessorBlockEntity.OUTPUT_SLOTS;
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 139 + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, 8 + column * 18, 197));
    addDataSlots(processor.data());
  }

  public boolean isArcFurnace() {
    return processor.isArcFurnace();
  }

  public MachineKind machineKind() {
    return processor.machineKind();
  }

  public boolean hasInputSlot(int relativeSlot) {
    return processor.hasInputSlot(relativeSlot);
  }

  public int visibleInputSlots() {
    return processor.visibleInputSlots();
  }

  public boolean requiresMagma() {
    return processor.requiresMagma();
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

  public int magmaHeat() {
    return processor.data().get(6);
  }

  public int magmaCapacity() {
    return processor.data().get(7);
  }

  public int magmaPerTick() {
    return processor.data().get(8);
  }

  public int temperature() {
    return processor.data().get(9);
  }

  public int targetTemperature() {
    return processor.data().get(10);
  }

  public int stability() {
    return processor.data().get(11);
  }

  public int pressure() {
    return processor.data().get(12);
  }

  public int operationMode() {
    return processor.data().get(13);
  }

  public int phase() {
    return processor.data().get(14);
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    return processor.handleMenuButton(id);
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
      int inputSlots = visibleInputSlots();
      boolean moved = moveItemStackTo(stack, 0, inputSlots, false);
      if (!moved && requiresMagma())
        moved = moveItemStackTo(stack, inputSlots, inputSlots + 1, false);
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

  private void addProcessSlot(int inventorySlot, int x, int y) {
    addSlot(
        new SlotItemHandler(processor.items(), inventorySlot, x, y) {
          @Override
          public boolean mayPickup(Player player) {
            return processor.canModifyInputs() && super.mayPickup(player);
          }

          @Override
          public boolean mayPlace(ItemStack stack) {
            return processor.canModifyInputs() && super.mayPlace(stack);
          }
        });
  }

  public static int inputX(int relativeSlot, int inputCount) {
    int start = inputCount == 1 ? 35 : inputCount == 2 ? 16 : 8;
    return start + relativeSlot * 24;
  }
}
