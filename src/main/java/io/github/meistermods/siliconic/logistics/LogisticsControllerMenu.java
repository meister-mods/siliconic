package io.github.meistermods.siliconic.logistics;

import io.github.meistermods.siliconic.logistics.LogisticsControllerBlockEntity.EndpointInfo;
import io.github.meistermods.siliconic.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class LogisticsControllerMenu extends AbstractContainerMenu {
  public static final int VISIBLE_ROWS = 5;
  public static final int FILTER_X = 114;
  public static final int ROW_Y = 35;
  public static final int ROW_SPACING = 24;
  public static final int PLAYER_X = 74;
  public static final int PLAYER_Y = 166;
  private static final int BUTTON_PREVIOUS = 0;
  private static final int BUTTON_NEXT = 1;
  private static final int BUTTON_MODE_START = 10;

  private record OpeningData(
      @Nullable LogisticsControllerBlockEntity controller, List<EndpointInfo> endpoints) {}

  @Nullable private final LogisticsControllerBlockEntity controller;
  private final List<EndpointInfo> endpoints;
  private final IItemHandlerModifiable filters;
  private final int[] clientData;
  private int serverPage;
  private final ContainerData data;

  public LogisticsControllerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
    this(id, inventory, readOpeningData(inventory, buffer));
  }

  private LogisticsControllerMenu(int id, Inventory inventory, OpeningData opening) {
    this(id, inventory, opening.controller(), opening.endpoints());
  }

  public LogisticsControllerMenu(
      int id,
      Inventory inventory,
      LogisticsControllerBlockEntity controller,
      List<EndpointInfo> endpoints) {
    this(id, inventory, controller, endpoints, false);
  }

  private LogisticsControllerMenu(
      int id,
      Inventory inventory,
      @Nullable LogisticsControllerBlockEntity controller,
      List<EndpointInfo> endpoints,
      boolean ignored) {
    super(ModMenus.LOGISTICS_CONTROLLER.get(), id);
    this.controller = controller;
    this.endpoints = List.copyOf(endpoints);
    this.clientData = new int[this.endpoints.size() + 1];
    this.filters =
        inventory.player.level().isClientSide || controller == null
            ? clientFilters(this.endpoints.size())
            : new EndpointFilterHandler(controller, this.endpoints);
    this.data = createData();

    for (int index = 0; index < this.endpoints.size(); index++)
      addSlot(
          new EndpointFilterSlot(
              filters,
              index,
              FILTER_X,
              ROW_Y + (index % VISIBLE_ROWS) * ROW_SPACING,
              index));
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        addSlot(
            new Slot(
                inventory,
                9 + row * 9 + column,
                PLAYER_X + column * 18,
                PLAYER_Y + row * 18));
    for (int column = 0; column < 9; column++)
      addSlot(new Slot(inventory, column, PLAYER_X + column * 18, PLAYER_Y + 58));
    addDataSlots(data);
  }

  private static OpeningData readOpeningData(Inventory inventory, FriendlyByteBuf buffer) {
    BlockEntity blockEntity = inventory.player.level().getBlockEntity(buffer.readBlockPos());
    LogisticsControllerBlockEntity controller =
        blockEntity instanceof LogisticsControllerBlockEntity found ? found : null;
    int count = Math.min(LogisticsControllerBlockEntity.MAX_ENDPOINTS, buffer.readVarInt());
    List<EndpointInfo> endpoints = new ArrayList<>(count);
    for (int index = 0; index < count; index++)
      endpoints.add(
          new EndpointInfo(
              buffer.readBlockPos(), buffer.readComponent(), buffer.readBoolean()));
    return new OpeningData(controller, List.copyOf(endpoints));
  }

  private static IItemHandlerModifiable clientFilters(int size) {
    return new ItemStackHandler(size) {
      @Override
      public int getSlotLimit(int slot) {
        return 1;
      }
    };
  }

  private ContainerData createData() {
    return new ContainerData() {
      @Override
      public int get(int index) {
        if (controller != null
            && controller.getLevel() != null
            && !controller.getLevel().isClientSide) {
          if (index == 0) return serverPage;
          int endpointIndex = index - 1;
          return endpointIndex >= 0 && endpointIndex < endpoints.size()
              ? controller.flags(endpoints.get(endpointIndex).pos())
              : 0;
        }
        return index >= 0 && index < clientData.length ? clientData[index] : 0;
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
  }

  public List<EndpointInfo> endpoints() {
    return endpoints;
  }

  public int page() {
    return controller != null && controller.getLevel() != null && !controller.getLevel().isClientSide
        ? serverPage
        : clientData[0];
  }

  public int pageCount() {
    return Math.max(1, (endpoints.size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS);
  }

  @Nullable
  public EndpointInfo endpointAtRow(int row) {
    int index = page() * VISIBLE_ROWS + row;
    return row < 0 || row >= VISIBLE_ROWS || index < 0 || index >= endpoints.size()
        ? null
        : endpoints.get(index);
  }

  public boolean inputEnabled(int row) {
    return (flagsAtRow(row) & 1) != 0;
  }

  public boolean outputEnabled(int row) {
    return (flagsAtRow(row) & 2) != 0;
  }

  public boolean forcedEnabled(int row) {
    return (flagsAtRow(row) & 4) != 0;
  }

  private int flagsAtRow(int row) {
    int index = page() * VISIBLE_ROWS + row;
    if (index < 0 || index >= endpoints.size()) return 0;
    return data.get(index + 1);
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (id == BUTTON_PREVIOUS) {
      if (serverPage <= 0) return false;
      serverPage--;
      return true;
    }
    if (id == BUTTON_NEXT) {
      if (serverPage + 1 >= pageCount()) return false;
      serverPage++;
      return true;
    }
    if (controller == null || id < BUTTON_MODE_START) return false;
    int offset = id - BUTTON_MODE_START;
    int row = offset / 3;
    int mode = offset % 3;
    int endpointIndex = serverPage * VISIBLE_ROWS + row;
    if (row < 0 || row >= VISIBLE_ROWS || endpointIndex >= endpoints.size()) return false;
    EndpointInfo endpoint = endpoints.get(endpointIndex);
    switch (mode) {
      case 0 -> controller.toggleInput(endpoint.pos());
      case 1 -> controller.toggleOutput(endpoint.pos());
      case 2 -> controller.toggleForced(endpoint.pos(), endpoint.supportsForced());
      default -> {
        return false;
      }
    }
    return true;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem();
    ItemStack copy = stack.copy();
    int endpointSlots = endpoints.size();
    if (index < endpointSlots) {
      if (!moveItemStackTo(stack, endpointSlots, slots.size(), true)) return ItemStack.EMPTY;
    } else {
      int first = page() * VISIBLE_ROWS;
      int last = Math.min(first + VISIBLE_ROWS, endpointSlots);
      boolean inserted = false;
      for (int endpointIndex = first; endpointIndex < last; endpointIndex++) {
        if (!filters.getStackInSlot(endpointIndex).isEmpty()) continue;
        ItemStack one = stack.copyWithCount(1);
        if (!filters.insertItem(endpointIndex, one, false).isEmpty()) continue;
        stack.shrink(1);
        inserted = true;
        break;
      }
      if (!inserted) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }

  @Override
  public boolean stillValid(Player player) {
    return controller != null
        && !controller.isRemoved()
        && player.distanceToSqr(controller.getBlockPos().getCenter()) <= 64;
  }

  private final class EndpointFilterSlot extends SlotItemHandler {
    private final int endpointIndex;

    EndpointFilterSlot(
        IItemHandlerModifiable handler, int slot, int x, int y, int endpointIndex) {
      super(handler, slot, x, y);
      this.endpointIndex = endpointIndex;
    }

    @Override
    public boolean isActive() {
      return endpointIndex / VISIBLE_ROWS == page();
    }

    @Override
    public int getMaxStackSize() {
      return 1;
    }
  }

  private static final class EndpointFilterHandler implements IItemHandlerModifiable {
    private final LogisticsControllerBlockEntity controller;
    private final List<EndpointInfo> endpoints;

    EndpointFilterHandler(
        LogisticsControllerBlockEntity controller, List<EndpointInfo> endpoints) {
      this.controller = controller;
      this.endpoints = endpoints;
    }

    @Override
    public int getSlots() {
      return endpoints.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
      checkSlot(slot);
      return controller.filter(endpoints.get(slot).pos());
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      checkSlot(slot);
      if (stack.isEmpty() || !getStackInSlot(slot).isEmpty()) return stack;
      if (!simulate) controller.setFilter(endpoints.get(slot).pos(), stack.copyWithCount(1));
      ItemStack remainder = stack.copy();
      remainder.shrink(1);
      return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      checkSlot(slot);
      ItemStack filter = getStackInSlot(slot);
      if (amount <= 0 || filter.isEmpty()) return ItemStack.EMPTY;
      ItemStack result = filter.copyWithCount(1);
      if (!simulate) controller.setFilter(endpoints.get(slot).pos(), ItemStack.EMPTY);
      return result;
    }

    @Override
    public int getSlotLimit(int slot) {
      checkSlot(slot);
      return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      checkSlot(slot);
      return !stack.isEmpty();
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
      checkSlot(slot);
      controller.setFilter(endpoints.get(slot).pos(), stack);
    }

    private void checkSlot(int slot) {
      if (slot < 0 || slot >= endpoints.size())
        throw new IndexOutOfBoundsException(
            "Slot " + slot + " not in valid range [0," + endpoints.size() + ")");
    }
  }
}
