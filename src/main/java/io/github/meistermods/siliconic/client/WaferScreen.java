package io.github.meistermods.siliconic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.meistermods.siliconic.network.CellInteractionPacket;
import io.github.meistermods.siliconic.network.CompleteWaferPacket;
import io.github.meistermods.siliconic.network.CyclePinModePacket;
import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.ConductorMode;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings({"null"})
public class WaferScreen extends AbstractContainerScreen<WaferMenu> {
  private static final int CELL = 14;
  private static final int GRID_X = 12;
  private static final int GRID_Y = 32;
  private static final int GUI_WIDTH = 320;
  private static final int GUI_HEIGHT = 180;
  private final int size, grid;
  private int gridX, gridY;
  private int lastDraggedCell = -1;
  private int dragButton = -1;
  private EditBox nameBox;

  public WaferScreen(WaferMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    size = menu.wafer().getGridSize();
    grid = size * CELL;
    imageWidth = GUI_WIDTH;
    imageHeight = GUI_HEIGHT;
    inventoryLabelX = WaferMenu.INVENTORY_X;
    inventoryLabelY = WaferMenu.MAIN_INVENTORY_Y - 13;
  }

  @Override
  protected void init() {
    super.init();
    gridX = leftPos + GRID_X;
    gridY = topPos + GRID_Y;
    nameBox =
        new EditBox(
            font,
            leftPos + WaferMenu.INVENTORY_X,
            topPos + 14,
            110,
            18,
            Component.translatable("screen.siliconic.wafer.name"));
    nameBox.setMaxLength(50);
    if (menu.wafer().getWafer().hasCustomHoverName())
      nameBox.setValue(menu.wafer().getWafer().getHoverName().getString());
    addRenderableWidget(nameBox);
    addRenderableWidget(
        Button.builder(
                Component.translatable("screen.siliconic.wafer.complete"),
                button ->
                    ModNetwork.CHANNEL.sendToServer(
                        new CompleteWaferPacket(menu.position(), nameBox.getValue())))
            .bounds(leftPos + 267, topPos + 14, 48, 18)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("↶"), button -> sendMenuButton(WaferMenu.BUTTON_UNDO))
            .bounds(leftPos + 267, topPos + 36, 22, 12)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("↷"), button -> sendMenuButton(WaferMenu.BUTTON_REDO))
            .bounds(leftPos + 293, topPos + 36, 22, 12)
            .build());
  }

  private void sendMenuButton(int id) {
    if (minecraft != null && minecraft.gameMode != null)
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
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
              case BUFFER -> 0xff55d9d2;
              case DROP -> 0xffe0a84f;
              case SWITCH -> 0xff6fcf72;
              case CHIP -> signal > 0 ? 0xffffd24f : 0xffd6b437;
              case CONTAMINATED -> 0xff4f392f;
            };
        int x1 = gridX + x * CELL, y1 = gridY + y * CELL;
        if (type.isConductor()) {
          g.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, 0xff20272b);
          drawConductor(
              g,
              x1,
              y1,
              menu.wafer().getConductorMode(cell),
              conductorColor(type, menu.wafer().getHorizontalSignal(cell) > 0),
              conductorColor(type, menu.wafer().getVerticalSignal(cell) > 0));
        } else if (type.isGate() || type == CellType.CHIP) {
          g.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, color);
          String symbol =
              type == CellType.DROP
                  ? Integer.toString(menu.wafer().getDropAmount(cell))
                  : gateSymbol(type);
          g.drawCenteredString(font, symbol, x1 + CELL / 2, y1 + 3, 0xffffffff);
          g.drawString(font, arrow(menu.wafer().getRotation(cell)), x1, y1, 0xff202020, false);
        } else if (type == CellType.CONTAMINATED) {
          g.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, color);
          g.drawCenteredString(font, "×", x1 + CELL / 2, y1 + 3, 0xffff8d76);
        } else {
          g.fill(x1 + 1, y1 + 1, x1 + CELL - 1, y1 + CELL - 1, color);
        }
      }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        slotBox(
            g,
            leftPos + WaferMenu.INVENTORY_X + col * 18,
            topPos + WaferMenu.MAIN_INVENTORY_Y + row * 18);
    for (int col = 0; col < 9; col++)
      slotBox(g, leftPos + WaferMenu.INVENTORY_X + col * 18, topPos + WaferMenu.HOTBAR_Y);
    int energyWidth =
        menu.isCreativeAssembler()
            ? 162
            : menu.capacity() == 0 ? 0 : 162 * menu.energy() / menu.capacity();
    g.fill(
        leftPos + WaferMenu.INVENTORY_X,
        topPos + 153,
        leftPos + WaferMenu.INVENTORY_X + 162,
        topPos + 161,
        0xff292d32);
    g.fill(
        leftPos + WaferMenu.INVENTORY_X,
        topPos + 153,
        leftPos + WaferMenu.INVENTORY_X + energyWidth,
        topPos + 161,
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
        95,
        7,
        0xffffd85a,
        false);
    g.drawCenteredString(font, "N " + pinLabel(0), GRID_X + grid / 2, 20, pinColor(0));
    int center = GRID_Y + grid / 2;
    g.drawString(font, "W " + pinLabel(3), GRID_X + 3, center, pinColor(3), false);
    String eastLabel = "E " + pinLabel(1);
    g.drawString(
        font, eastLabel, GRID_X + grid - font.width(eastLabel) - 3, center, pinColor(1), false);
    g.drawCenteredString(
        font, "S " + pinLabel(2), GRID_X + grid / 2, GRID_Y + grid + 5, pinColor(2));
    Component powerLabel =
        menu.isCreativeAssembler()
            ? Component.translatable("screen.siliconic.wafer.creative_power")
            : Component.translatable(
                "screen.siliconic.wafer.energy",
                menu.energy(),
                menu.capacity(),
                menu.operationCost());
    drawFittedString(g, powerLabel, WaferMenu.INVENTORY_X, 139, 162, 0xffff8ca0);
    if (!menu.canEditHere())
      drawFittedString(
          g,
          Component.translatable("screen.siliconic.machine.outside_cleanroom"),
          WaferMenu.INVENTORY_X,
          127,
          162,
          0xffffb35c);
    g.drawString(
        font,
        Component.translatable("container.inventory"),
        WaferMenu.INVENTORY_X,
        WaferMenu.MAIN_INVENTORY_Y - 13,
        0xffaeb7c0,
        false);
    if (!menu.wafer().hasWafer())
      g.drawCenteredString(
          font,
          Component.translatable("screen.siliconic.wafer.no_wafer"),
          GRID_X + grid / 2,
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
      if (type == CellType.CHIP) {
        ItemStack embeddedWafer = menu.wafer().getEmbeddedWafer(cell);
        if (!embeddedWafer.isEmpty()) {
          lines.add(
              Component.translatable(
                  "screen.siliconic.wafer.embedded_type",
                  embeddedWafer.getItem().getDescription()));
          if (embeddedWafer.hasCustomHoverName())
            lines.add(
                Component.translatable(
                    "screen.siliconic.wafer.embedded_name", embeddedWafer.getHoverName()));
        }
      }
      if (type.isConductor())
        lines.add(
            Component.translatable(
                "screen.siliconic.wafer.attenuation", type.attenuationInterval()));
      if (type.isConductor())
        lines.add(
            Component.translatable(
                "screen.siliconic.wafer.conductor_mode",
                Component.translatable(
                    "conductor_mode.siliconic."
                        + menu.wafer().getConductorMode(cell).name().toLowerCase())));
      if (type == CellType.DROP)
        lines.add(
            Component.translatable(
                "screen.siliconic.wafer.drop_amount", menu.wafer().getDropAmount(cell)));
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
      dragButton = button;
      lastDraggedCell = -1;
      sendCellEdit(mx, my, button);
      return true;
    }
    if (button == 0 && menu.wafer().hasWafer()) {
      int pin = hoveredPin(mx, my);
      if (pin >= 0) {
        ModNetwork.CHANNEL.sendToServer(new CyclePinModePacket(menu.position(), pin));
        return true;
      }
    }
    return super.mouseClicked(mx, my, button);
  }

  @Override
  public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
    if (button == dragButton && (button == 0 || button == 1) && sendCellEdit(mx, my, button))
      return true;
    return super.mouseDragged(mx, my, button, dragX, dragY);
  }

  @Override
  public boolean mouseReleased(double mx, double my, int button) {
    if (button == dragButton) {
      dragButton = -1;
      lastDraggedCell = -1;
    }
    return super.mouseReleased(mx, my, button);
  }

  private boolean sendCellEdit(double mx, double my, int button) {
    if (!menu.wafer().hasWafer() || !insideGrid((int) mx, (int) my)) return false;
    int x = (int) (mx - gridX) / CELL, y = (int) (my - gridY) / CELL;
    int cell = y * size + x;
    if (cell == lastDraggedCell) return true;
    lastDraggedCell = cell;
    ModNetwork.CHANNEL.sendToServer(new CellInteractionPacket(menu.position(), cell, button == 1));
    return true;
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (nameBox != null && nameBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
      nameBox.keyPressed(keyCode, scanCode, modifiers);
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
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

  private void drawFittedString(
      GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
    int width = font.width(text);
    float scale = width > maxWidth ? (float) maxWidth / width : 1.0f;
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    g.pose().scale(scale, scale, 1.0f);
    g.drawString(font, text, 0, 0, color, false);
    g.pose().popPose();
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
      case AND -> "⋅";
      case OR -> "+";
      case XOR -> "⊕";
      case BUFFER -> ">>";
      case DROP -> "-";
      case SWITCH -> "S";
      case CHIP -> "C";
      default -> "";
    };
  }

  private int conductorColor(CellType type, boolean powered) {
    return switch (type) {
      case REDSTONE -> powered ? 0xffff4040 : 0xff8b2525;
      case COPPER -> powered ? 0xffffb13b : 0xffb86228;
      case LEAD -> powered ? 0xffaeb8c5 : 0xff626b78;
      case SILVER -> powered ? 0xffffffff : 0xffb9c8d5;
      case GOLD -> powered ? 0xffffe05c : 0xffc59b25;
      default -> 0xffffffff;
    };
  }

  private void drawConductor(
      GuiGraphics g, int x, int y, ConductorMode mode, int horizontalColor, int verticalColor) {
    int c = CELL / 2, half = 2;
    boolean north =
        mode == ConductorMode.PLUS
            || mode == ConductorMode.VERTICAL
            || mode == ConductorMode.CROSSOVER
            || mode == ConductorMode.CORNER_NE
            || mode == ConductorMode.CORNER_WN;
    boolean east =
        mode == ConductorMode.PLUS
            || mode == ConductorMode.HORIZONTAL
            || mode == ConductorMode.CROSSOVER
            || mode == ConductorMode.CORNER_NE
            || mode == ConductorMode.CORNER_ES;
    boolean south =
        mode == ConductorMode.PLUS
            || mode == ConductorMode.VERTICAL
            || mode == ConductorMode.CROSSOVER
            || mode == ConductorMode.CORNER_ES
            || mode == ConductorMode.CORNER_SW;
    boolean west =
        mode == ConductorMode.PLUS
            || mode == ConductorMode.HORIZONTAL
            || mode == ConductorMode.CROSSOVER
            || mode == ConductorMode.CORNER_SW
            || mode == ConductorMode.CORNER_WN;
    if (north) g.fill(x + c - half, y + 1, x + c + half, y + c + 1, verticalColor);
    if (east) g.fill(x + c, y + c - half, x + CELL - 1, y + c + half, horizontalColor);
    if (south) g.fill(x + c - half, y + c, x + c + half, y + CELL - 1, verticalColor);
    if (west) g.fill(x + 1, y + c - half, x + c + 1, y + c + half, horizontalColor);
    if (mode == ConductorMode.CROSSOVER) {
      g.fill(x + c - half - 1, y + c - half - 1, x + c + half + 1, y + c + half + 1, 0xff20272b);
      g.fill(x + c - half, y + 1, x + c + half, y + CELL - 1, verticalColor);
    } else {
      int centerColor = mode == ConductorMode.VERTICAL ? verticalColor : horizontalColor;
      g.fill(x + c - half, y + c - half, x + c + half, y + c + half, centerColor);
    }
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
