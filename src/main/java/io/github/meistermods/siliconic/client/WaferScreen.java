package io.github.meistermods.siliconic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.meistermods.siliconic.network.ModNetwork;
import io.github.meistermods.siliconic.network.ToggleTracePacket;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WaferScreen extends AbstractContainerScreen<WaferMenu> {
  private static final int CELL = 20;
  private static final int GRID = PrototypeWaferBlockEntity.SIZE * CELL;
  private int gridX, gridY;

  public WaferScreen(WaferMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 224;
    imageHeight = 214;
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
    for (int y = 0; y < 8; y++)
      for (int x = 0; x < 8; x++) {
        int index = y * 8 + x;
        int color = menu.wafer().hasTrace(index) ? 0xffd8792d : 0xff283338;
        graphics.fill(
            gridX + x * CELL + 1,
            gridY + y * CELL + 1,
            gridX + (x + 1) * CELL - 1,
            gridY + (y + 1) * CELL - 1,
            color);
      }
    int pin = 0xffffd85a;
    graphics.fill(gridX + 3 * CELL + 4, gridY - 8, gridX + 4 * CELL - 4, gridY, pin);
    graphics.fill(gridX + GRID, gridY + 4 * CELL + 4, gridX + GRID + 8, gridY + 5 * CELL - 4, pin);
    graphics.fill(gridX + 4 * CELL + 4, gridY + GRID, gridX + 5 * CELL - 4, gridY + GRID + 8, pin);
    graphics.fill(gridX - 8, gridY + 3 * CELL + 4, gridX, gridY + 4 * CELL - 4, pin);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    graphics.drawString(font, title, 8, 10, 0xffe8edf2, false);
    graphics.drawCenteredString(
        font,
        Component.translatable("screen.siliconic.wafer.hint"),
        imageWidth / 2,
        199,
        0xffaeb7c0);
    graphics.drawCenteredString(font, "N", imageWidth / 2, 22, 0xffffd85a);
    graphics.drawString(font, "W", 18, 111, 0xffffd85a, false);
    graphics.drawString(font, "E", 199, 111, 0xffffd85a, false);
    graphics.drawCenteredString(font, "S", imageWidth / 2, 187, 0xffffd85a);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0
        && mouseX >= gridX
        && mouseX < gridX + GRID
        && mouseY >= gridY
        && mouseY < gridY + GRID) {
      int x = (int) (mouseX - gridX) / CELL, y = (int) (mouseY - gridY) / CELL;
      int cell = y * 8 + x;
      menu.wafer().toggleTrace(cell);
      ModNetwork.CHANNEL.sendToServer(new ToggleTracePacket(menu.position(), cell));
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }
}
