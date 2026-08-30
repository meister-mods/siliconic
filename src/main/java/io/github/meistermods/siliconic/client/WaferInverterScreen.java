package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.wafer.WaferInverterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class WaferInverterScreen extends AbstractContainerScreen<WaferInverterMenu> {
  public WaferInverterScreen(WaferInverterMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 191;
    inventoryLabelY = 96;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    slotBox(g, leftPos + 42, topPos + 39);
    slotBox(g, leftPos + 116, topPos + 39);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(g, leftPos + 8 + column * 18, topPos + 107 + row * 18);
    for (int column = 0; column < 9; column++)
      slotBox(g, leftPos + 8 + column * 18, topPos + 165);

    g.drawString(font, "⇄", leftPos + 82, topPos + 43, 0xffe8edf2, false);
    int progressWidth =
        menu.maxProgress() == 0 ? 0 : 38 * menu.progress() / menu.maxProgress();
    g.fill(leftPos + 69, topPos + 58, leftPos + 107, topPos + 62, 0xff2b3035);
    g.fill(leftPos + 69, topPos + 58, leftPos + 69 + progressWidth, topPos + 62, 0xff66d99a);
    int energyWidth = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    g.fill(leftPos + 8, topPos + 87, leftPos + 168, topPos + 92, 0xff2b3035);
    g.fill(leftPos + 8, topPos + 87, leftPos + 8 + energyWidth, topPos + 92, 0xffd94f67);
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.inverter.input"), 50, 26, 0xffaeb7c0);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.inverter.output"), 124, 26, 0xffaeb7c0);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
    drawFittedString(
        g,
        Component.translatable(
            "screen.siliconic.inverter.energy",
            menu.energy(),
            menu.capacity(),
            menu.energyPerTick()),
        8,
        76,
        160,
        0xffff8ca0);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.inverter.status." + status),
        8,
        65,
        160,
        status == 4 ? 0xff66e69a : 0xffffb35c);
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

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    renderBackground(g);
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
