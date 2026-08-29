package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.silicon.SiliconProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class SiliconProcessorScreen extends AbstractContainerScreen<SiliconProcessorMenu> {
  public SiliconProcessorScreen(SiliconProcessorMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 205;
    inventoryLabelY = 112;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    slotBox(g, leftPos + (menu.isArcFurnace() ? 26 : 44), topPos + 42);
    if (menu.isArcFurnace()) slotBox(g, leftPos + 53, topPos + 42);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        slotBox(g, leftPos + 110 + column * 18, topPos + 24 + row * 18);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(g, leftPos + 8 + column * 18, topPos + 123 + row * 18);
    for (int column = 0; column < 9; column++) slotBox(g, leftPos + 8 + column * 18, topPos + 181);

    if (menu.isArcFurnace()) g.drawString(font, "+", leftPos + 46, topPos + 46, 0xffe8edf2, false);
    g.drawString(font, "→", leftPos + 92, topPos + 46, 0xffe8edf2, false);
    int progressWidth = menu.maxProgress() == 0 ? 0 : 20 * menu.progress() / menu.maxProgress();
    g.fill(leftPos + 84, topPos + 59, leftPos + 104, topPos + 63, 0xff2b3035);
    g.fill(leftPos + 84, topPos + 59, leftPos + 84 + progressWidth, topPos + 63, 0xff66d99a);
    int energyWidth = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    g.fill(leftPos + 8, topPos + 102, leftPos + 168, topPos + 107, 0xff2b3035);
    g.fill(leftPos + 8, topPos + 102, leftPos + 8 + energyWidth, topPos + 107, 0xffd94f67);
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawCenteredString(
        font,
        Component.translatable("screen.siliconic.processor.input"),
        menu.isArcFurnace() ? 34 : 52,
        29,
        0xffaeb7c0);
    if (menu.isArcFurnace())
      g.drawCenteredString(
          font, Component.translatable("screen.siliconic.processor.carbon"), 61, 29, 0xffaeb7c0);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.processor.output"), 136, 13, 0xffaeb7c0);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
    drawFittedString(
        g,
        Component.translatable(
            "screen.siliconic.processor.energy",
            menu.energy(),
            menu.capacity(),
            menu.energyPerTick()),
        8,
        91,
        160,
        0xffff8ca0);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.processor.status." + status),
        8,
        80,
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
