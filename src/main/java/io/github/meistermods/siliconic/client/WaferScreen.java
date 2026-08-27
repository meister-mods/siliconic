package io.github.meistermods.siliconic.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import io.github.meistermods.siliconic.network.CellInteractionPacket;
import io.github.meistermods.siliconic.network.CyclePinModePacket;
import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class WaferScreen extends AbstractContainerScreen<WaferMenu> {
  private static final int CELL = 14;
  private final int size, grid, inventoryY;
  private int gridX, gridY;

  public WaferScreen(WaferMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    size = menu.wafer().getGridSize();
    grid = size * CELL;
    inventoryY = menu.inventoryY();
    imageWidth = 232;
    imageHeight = inventoryY + 84;
    inventoryLabelY = inventoryY - 13;
  }

  @Override
  protected void init() {
    super.init();
    gridX = leftPos + (imageWidth - grid) / 2;
    gridY = topPos + 30;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    RenderSystem.enableBlend();
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff15171a);
    g.fill(gridX - 4, gridY - 4, gridX + grid + 4, gridY + grid + 4, 0xff8c9299);
    for (int y = 0; y < size; y++)
      for (int x = 0; x < size; x++) {
        int cell = y * size + x, signal = menu.wafer().getCellSignal(cell);
        CellType type = menu.wafer().getCellType(cell);
        int color =
            switch (type) {
              case EMPTY -> 0xff283338;
              case REDSTONE -> signal > 0 ? 0xffff4040 : 0xff8b2525;
              case COPPER -> signal > 0 ? 0xffffb13b : 0xffb86228;
              case LEAD -> signal > 0 ? 0xffaeb8c5 : 0xff626b78;
              case SILVER -> signal > 0 ? 0xffffffff : 0xffb9c8d5;
              case GOLD -> signal > 0 ? 0xffffe05c : 0xffc59b25;
              case NOT -> 0xff9b6dff;
              case AND -> 0xff4fc38b;
              case OR -> 0xff4fa9df;
              case XOR -> 0xffe667a0;
              case CHIP -> signal > 0 ? 0xffffd24f : 0xffd6b437;
            };
        int x1 = gridX + x * CELL, y1 = gridY + y * CELL;
        g.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, color);
        if (type.isGate() || type == CellType.CHIP) {
          g.drawCenteredString(font, gateSymbol(type), x1 + CELL / 2, y1 + 3, 0xffffffff);
          g.drawString(font, arrow(menu.wafer().getRotation(cell)), x1, y1, 0xff202020, false);
        }
      }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        slotBox(g, leftPos + 35 + col * 18, topPos + inventoryY + row * 18);
    for (int col = 0; col < 9; col++) slotBox(g, leftPos + 35 + col * 18, topPos + inventoryY + 58);
    int energyWidth = 162 * menu.wafer().getEnergyStored() / menu.wafer().getEnergyCapacity();
    g.fill(
        leftPos + 35,
        topPos + inventoryY - 26,
        leftPos + 197,
        topPos + inventoryY - 18,
        0xff292d32);
    g.fill(
        leftPos + 35,
        topPos + inventoryY - 26,
        leftPos + 35 + energyWidth,
        topPos + inventoryY - 18,
        0xffd94f67);
    int m = size / 2;
    g.fill(gridX + m * CELL + 3, gridY - 7, gridX + (m + 1) * CELL - 3, gridY, pinColor(0));
    g.fill(
        gridX + grid,
        gridY + m * CELL + 3,
        gridX + grid + 7,
        gridY + (m + 1) * CELL - 3,
        pinColor(1));
    g.fill(
        gridX + m * CELL + 3,
        gridY + grid,
        gridX + (m + 1) * CELL - 3,
        gridY + grid + 7,
        pinColor(2));
    g.fill(gridX - 7, gridY + m * CELL + 3, gridX, gridY + (m + 1) * CELL - 3, pinColor(3));
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff777d83);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.wafer.level", menu.wafer().getWaferLevel()),
        imageWidth - 57,
        7,
        0xffffd85a,
        false);
    g.drawCenteredString(font, "N " + pinLabel(0), imageWidth / 2, 18, pinColor(0));
    int center = 30 + grid / 2;
    g.drawString(font, "W " + pinLabel(3), 5, center, pinColor(3), false);
    g.drawString(font, "E " + pinLabel(1), imageWidth - 37, center, pinColor(1), false);
    g.drawCenteredString(font, "S " + pinLabel(2), imageWidth / 2, 34 + grid, pinColor(2));
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.wafer.energy",
            menu.wafer().getEnergyStored(),
            menu.wafer().getEnergyCapacity(),
            menu.wafer().getOperationCost()),
        35,
        inventoryY - 38,
        0xffff8ca0,
        false);
    g.drawString(
        font,
        Component.translatable("container.inventory"),
        35,
        inventoryY - 13,
        0xffaeb7c0,
        false);
    if (!menu.wafer().hasWafer())
      g.drawCenteredString(
          font,
          Component.translatable("screen.siliconic.wafer.no_wafer"),
          imageWidth / 2,
          center,
          0xffff6b6b);
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g);
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
    if (insideGrid(mx, my)) {
      int x = (mx - gridX) / CELL, y = (my - gridY) / CELL, cell = y * size + x;
      CellType type = menu.wafer().getCellType(cell);
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable("screen.siliconic.wafer.probe", x, y));
      lines.add(Component.translatable("cell.siliconic." + type.name().toLowerCase()));
      if (type.isConductor())
        lines.add(Component.translatable("screen.siliconic.wafer.range", type.range()));
      if (type.isGate() || type == CellType.CHIP)
        lines.add(
            Component.translatable(
                "screen.siliconic.wafer.facing", directionName(menu.wafer().getRotation(cell))));
      lines.add(
          Component.translatable(
              "screen.siliconic.wafer.signal", menu.wafer().getCellSignal(cell)));
      g.renderComponentTooltip(font, lines, mx, my);
    }
  }

  @Override
  public boolean mouseClicked(double mx, double my, int button) {
    if (menu.wafer().hasWafer() && insideGrid((int) mx, (int) my) && (button == 0 || button == 1)) {
      int x = (int) (mx - gridX) / CELL, y = (int) (my - gridY) / CELL;
      ModNetwork.CHANNEL.sendToServer(
          new CellInteractionPacket(menu.position(), y * size + x, button == 1));
      return true;
    }
    if (button == 0 && menu.wafer().hasWafer()) {
      int pin = hoveredPin(mx, my);
      if (pin >= 0) {
        menu.wafer().cyclePinMode(pin);
        ModNetwork.CHANNEL.sendToServer(new CyclePinModePacket(menu.position(), pin));
        return true;
      }
    }
    return super.mouseClicked(mx, my, button);
  }

  private boolean insideGrid(int x, int y) {
    return x >= gridX && x < gridX + grid && y >= gridY && y < gridY + grid;
  }

  private int hoveredPin(double x, double y) {
    int m = size / 2;
    if (x >= gridX + m * CELL && x < gridX + (m + 1) * CELL && y >= gridY - 12 && y < gridY)
      return 0;
    if (x >= gridX + grid
        && x < gridX + grid + 12
        && y >= gridY + m * CELL
        && y < gridY + (m + 1) * CELL) return 1;
    if (x >= gridX + m * CELL
        && x < gridX + (m + 1) * CELL
        && y >= gridY + grid
        && y < gridY + grid + 12) return 2;
    if (x >= gridX - 12 && x < gridX && y >= gridY + m * CELL && y < gridY + (m + 1) * CELL)
      return 3;
    return -1;
  }

  private int pinColor(int pin) {
    return switch (menu.wafer().getPinMode(pin)) {
      case INPUT -> 0xff63c5ff;
      case OUTPUT -> 0xffffa94d;
      case DISABLED -> 0xff70777d;
    };
  }

  private String pinLabel(int pin) {
    return switch (menu.wafer().getPinMode(pin)) {
      case INPUT -> "IN";
      case OUTPUT -> "OUT";
      case DISABLED -> "OFF";
    };
  }

  private String gateSymbol(CellType type) {
    return switch (type) {
      case NOT -> "!";
      case AND -> "&";
      case OR -> "≥";
      case XOR -> "≠";
      case CHIP -> "C";
      default -> "";
    };
  }

  private String arrow(int rotation) {
    return switch (rotation & 3) {
      case 0 -> "↑";
      case 1 -> "→";
      case 2 -> "↓";
      default -> "←";
    };
  }

  private Component directionName(int rotation) {
    return Component.translatable(
        "direction.siliconic."
            + switch (rotation & 3) {
              case 0 -> "north";
              case 1 -> "east";
              case 2 -> "south";
              default -> "west";
            });
  }
}
