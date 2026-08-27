package io.github.meistermods.siliconic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.meistermods.siliconic.network.CellInteractionPacket;
import io.github.meistermods.siliconic.network.CyclePinModePacket;
import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class WaferScreen extends AbstractContainerScreen<WaferMenu> {
  private static final int CELL = 18, GRID = PrototypeWaferBlockEntity.SIZE * CELL;
  private int gridX, gridY;

  public WaferScreen(WaferMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 224;
    imageHeight = 304;
    inventoryLabelY = 207;
  }

  @Override
  protected void init() {
    super.init();
    gridX = leftPos + (imageWidth - GRID) / 2;
    gridY = topPos + 32;
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    RenderSystem.enableBlend();
    graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff15171a);
    graphics.fill(gridX - 4, gridY - 4, gridX + GRID + 4, gridY + GRID + 4, 0xff8c9299);
    for (int y = 0; y < PrototypeWaferBlockEntity.SIZE; y++)
      for (int x = 0; x < PrototypeWaferBlockEntity.SIZE; x++) {
        int cell = y * PrototypeWaferBlockEntity.SIZE + x,
            signal = menu.wafer().getCellSignal(cell);
        CellType type = menu.wafer().getCellType(cell);
        int color =
            switch (type) {
              case EMPTY -> 0xff283338;
              case COPPER -> signal > 0 ? 0xffffb13b : 0xffb86228;
              case NOT -> 0xff9b6dff;
              case AND -> 0xff4fc38b;
              case OR -> 0xff4fa9df;
              case XOR -> 0xffe667a0;
            };
        int x1 = gridX + x * CELL, y1 = gridY + y * CELL;
        graphics.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, color);
        if (type.isGate()) {
          graphics.drawCenteredString(font, gateSymbol(type), x1 + CELL / 2, y1 + 5, 0xffffffff);
          graphics.drawString(
              font, arrow(menu.wafer().getRotation(cell)), x1 + 1, y1 + 1, 0xff202020, false);
        }
      }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        slotBox(graphics, leftPos + 31 + col * 18, topPos + 220 + row * 18);
    for (int col = 0; col < 9; col++) slotBox(graphics, leftPos + 31 + col * 18, topPos + 278);
    int m = PrototypeWaferBlockEntity.SIZE / 2;
    graphics.fill(gridX + m * CELL + 4, gridY - 8, gridX + (m + 1) * CELL - 4, gridY, pinColor(0));
    graphics.fill(
        gridX + GRID,
        gridY + m * CELL + 4,
        gridX + GRID + 8,
        gridY + (m + 1) * CELL - 4,
        pinColor(1));
    graphics.fill(
        gridX + m * CELL + 4,
        gridY + GRID,
        gridX + (m + 1) * CELL - 4,
        gridY + GRID + 8,
        pinColor(2));
    graphics.fill(gridX - 8, gridY + m * CELL + 4, gridX, gridY + (m + 1) * CELL - 4, pinColor(3));
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff777d83);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 8, 0xffe8edf2, false);
    g.drawCenteredString(font, "N " + pinLabel(0), imageWidth / 2, 20, pinColor(0));
    g.drawString(font, "W " + pinLabel(3), 5, 108, pinColor(3), false);
    g.drawString(font, "E " + pinLabel(1), 190, 108, pinColor(1), false);
    g.drawCenteredString(font, "S " + pinLabel(2), imageWidth / 2, 198, pinColor(2));
    g.drawString(font, Component.translatable("container.inventory"), 31, 207, 0xffaeb7c0, false);
    if (!menu.wafer().hasWafer())
      g.drawCenteredString(
          font,
          Component.translatable("screen.siliconic.wafer.no_wafer"),
          imageWidth / 2,
          108,
          0xffff6b6b);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    renderBackground(g);
    super.render(g, mouseX, mouseY, partialTick);
    renderTooltip(g, mouseX, mouseY);
    if (insideGrid(mouseX, mouseY)) {
      int x = (mouseX - gridX) / CELL,
          y = (mouseY - gridY) / CELL,
          cell = y * PrototypeWaferBlockEntity.SIZE + x;
      CellType type = menu.wafer().getCellType(cell);
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable("screen.siliconic.wafer.probe", x, y));
      lines.add(Component.translatable("cell.siliconic." + type.name().toLowerCase()));
      if (type.isGate())
        lines.add(
            Component.translatable(
                "screen.siliconic.wafer.facing", directionName(menu.wafer().getRotation(cell))));
      lines.add(
          Component.translatable(
              "screen.siliconic.wafer.signal", menu.wafer().getCellSignal(cell)));
      g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (menu.wafer().hasWafer()
        && insideGrid((int) mouseX, (int) mouseY)
        && (button == 0 || button == 1)) {
      int x = (int) (mouseX - gridX) / CELL,
          y = (int) (mouseY - gridY) / CELL,
          cell = y * PrototypeWaferBlockEntity.SIZE + x;
      ModNetwork.CHANNEL.sendToServer(
          new CellInteractionPacket(menu.position(), cell, button == 1));
      return true;
    }
    if (button == 0 && menu.wafer().hasWafer()) {
      int pin = hoveredPin(mouseX, mouseY);
      if (pin >= 0) {
        menu.wafer().cyclePinMode(pin);
        ModNetwork.CHANNEL.sendToServer(new CyclePinModePacket(menu.position(), pin));
        return true;
      }
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  private boolean insideGrid(int x, int y) {
    return x >= gridX && x < gridX + GRID && y >= gridY && y < gridY + GRID;
  }

  private int hoveredPin(double x, double y) {
    int m = PrototypeWaferBlockEntity.SIZE / 2;
    if (x >= gridX + m * CELL && x < gridX + (m + 1) * CELL && y >= gridY - 14 && y < gridY)
      return 0;
    if (x >= gridX + GRID
        && x < gridX + GRID + 14
        && y >= gridY + m * CELL
        && y < gridY + (m + 1) * CELL) return 1;
    if (x >= gridX + m * CELL
        && x < gridX + (m + 1) * CELL
        && y >= gridY + GRID
        && y < gridY + GRID + 14) return 2;
    if (x >= gridX - 14 && x < gridX && y >= gridY + m * CELL && y < gridY + (m + 1) * CELL)
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
