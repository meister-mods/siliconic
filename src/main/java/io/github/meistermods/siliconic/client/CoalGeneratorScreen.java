package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.power.CoalGeneratorBlockEntity;
import io.github.meistermods.siliconic.power.CoalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class CoalGeneratorScreen extends AbstractContainerScreen<CoalGeneratorMenu> {
  public CoalGeneratorScreen(CoalGeneratorMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 180;
    inventoryLabelY = 85;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    slotBox(g, leftPos + 35, topPos + 38);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(g, leftPos + 8 + column * 18, topPos + 96 + row * 18);
    for (int column = 0; column < 9; column++) slotBox(g, leftPos + 8 + column * 18, topPos + 154);

    int flameHeight = menu.totalBurnTime() == 0 ? 0 : 16 * menu.burnTime() / menu.totalBurnTime();
    g.fill(leftPos + 66, topPos + 38, leftPos + 78, topPos + 54, 0xff2b3035);
    g.fill(leftPos + 66, topPos + 54 - flameHeight, leftPos + 78, topPos + 54, 0xffff9f43);
    int energyWidth = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    g.fill(leftPos + 8, topPos + 76, leftPos + 168, topPos + 81, 0xff2b3035);
    g.fill(leftPos + 8, topPos + 76, leftPos + 8 + energyWidth, topPos + 81, 0xffd94f67);
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.generator.fuel"), 43, 25, 0xffaeb7c0);
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.generator.output", CoalGeneratorBlockEntity.GENERATION_PER_TICK),
        86,
        42,
        0xff66e69a,
        false);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.generator.energy", menu.energy(), menu.capacity()),
        8,
        65,
        160,
        0xffff8ca0);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.generator.status." + status),
        86,
        54,
        82,
        status == 1 ? 0xff66e69a : 0xffffb35c);
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
