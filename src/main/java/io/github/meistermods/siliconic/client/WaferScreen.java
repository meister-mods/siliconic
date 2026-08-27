package io.github.meistermods.siliconic.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import io.github.meistermods.siliconic.network.CyclePinModePacket;
import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.network.ToggleTracePacket;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class WaferScreen extends AbstractContainerScreen<WaferMenu> {
  private static final int CELL = 18;
  private static final int GRID = PrototypeWaferBlockEntity.SIZE * CELL;
  private int gridX, gridY;

  public WaferScreen(WaferMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 224;
    imageHeight = 242;
  }

  @Override
  protected void init() {
    super.init();
    gridX = leftPos + (imageWidth - GRID) / 2;
    gridY = topPos + 34;
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    RenderSystem.enableBlend();
    graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff15171a);
    graphics.fill(gridX - 4, gridY - 4, gridX + GRID + 4, gridY + GRID + 4, 0xff8c9299);
    for (int y = 0; y < PrototypeWaferBlockEntity.SIZE; y++)
      for (int x = 0; x < PrototypeWaferBlockEntity.SIZE; x++) {
        int index = y * PrototypeWaferBlockEntity.SIZE + x,
            signal = menu.wafer().getCellSignal(index);
        int color =
            !menu.wafer().hasTrace(index) ? 0xff283338 : signal > 0 ? 0xffffb13b : 0xffb86228;
        graphics.fill(
            gridX + x * CELL + 1,
            gridY + y * CELL + 1,
            gridX + (x + 1) * CELL - 1,
            gridY + (y + 1) * CELL - 1,
            color);
      }
    int middle = PrototypeWaferBlockEntity.SIZE / 2;
    graphics.fill(
        gridX + middle * CELL + 4, gridY - 8, gridX + (middle + 1) * CELL - 4, gridY, pinColor(0));
    graphics.fill(
        gridX + GRID,
        gridY + middle * CELL + 4,
        gridX + GRID + 8,
        gridY + (middle + 1) * CELL - 4,
        pinColor(1));
    graphics.fill(
        gridX + middle * CELL + 4,
        gridY + GRID,
        gridX + (middle + 1) * CELL - 4,
        gridY + GRID + 8,
        pinColor(2));
    graphics.fill(
        gridX - 8, gridY + middle * CELL + 4, gridX, gridY + (middle + 1) * CELL - 4, pinColor(3));
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, title, 8, 10, 0xffe8edf2, false);
    graphics.drawCenteredString(
        font,
        Component.translatable("screen.siliconic.wafer.hint"),
        imageWidth / 2,
        226,
        0xffaeb7c0);
    graphics.drawCenteredString(font, "N " + pinLabel(0), imageWidth / 2, 22, pinColor(0));
    graphics.drawString(font, "W " + pinLabel(3), 5, 112, pinColor(3), false);
    graphics.drawString(font, "E " + pinLabel(1), 190, 112, pinColor(1), false);
    graphics.drawCenteredString(font, "S " + pinLabel(2), imageWidth / 2, 207, pinColor(2));
    if (!menu.wafer().hasWafer())
      graphics.drawCenteredString(
          font,
          Component.translatable("screen.siliconic.wafer.no_wafer"),
          imageWidth / 2,
          112,
          0xffff6b6b);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    if (insideGrid(mouseX, mouseY)) {
      int x = (mouseX - gridX) / CELL,
          y = (mouseY - gridY) / CELL,
          cell = y * PrototypeWaferBlockEntity.SIZE + x;
      graphics.renderComponentTooltip(
          font,
          List.of(
              Component.translatable("screen.siliconic.wafer.probe", x, y),
              Component.translatable(
                  menu.wafer().hasTrace(cell)
                      ? "screen.siliconic.wafer.trace"
                      : "screen.siliconic.wafer.empty"),
              Component.translatable(
                  "screen.siliconic.wafer.signal", menu.wafer().getCellSignal(cell))),
          mouseX,
          mouseY);
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0 && menu.wafer().hasWafer() && insideGrid((int) mouseX, (int) mouseY)) {
      int x = (int) (mouseX - gridX) / CELL,
          y = (int) (mouseY - gridY) / CELL,
          cell = y * PrototypeWaferBlockEntity.SIZE + x;
      menu.wafer().toggleTrace(cell);
      ModNetwork.CHANNEL.sendToServer(new ToggleTracePacket(menu.position(), cell));
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
    int middle = PrototypeWaferBlockEntity.SIZE / 2;
    if (x >= gridX + middle * CELL
        && x < gridX + (middle + 1) * CELL
        && y >= gridY - 14
        && y < gridY) return 0;
    if (x >= gridX + GRID
        && x < gridX + GRID + 14
        && y >= gridY + middle * CELL
        && y < gridY + (middle + 1) * CELL) return 1;
    if (x >= gridX + middle * CELL
        && x < gridX + (middle + 1) * CELL
        && y >= gridY + GRID
        && y < gridY + GRID + 14) return 2;
    if (x >= gridX - 14
        && x < gridX
        && y >= gridY + middle * CELL
        && y < gridY + (middle + 1) * CELL) return 3;
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
}
