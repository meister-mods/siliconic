package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.fabrication.FabricationStationMenu;
import io.github.meistermods.siliconic.network.MenuDataSync;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class FabricationStationScreen extends AbstractContainerScreen<FabricationStationMenu> {
  public FabricationStationScreen(
      FabricationStationMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 219;
    inventoryLabelY = 126;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        slotBox(g, leftPos + 20 + column * 18, topPos + 34 + row * 18);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 3; column++)
        slotBox(g, leftPos + 114 + column * 18, topPos + 34 + row * 18);
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(g, leftPos + 8 + column * 18, topPos + 137 + row * 18);
    for (int column = 0; column < 9; column++) slotBox(g, leftPos + 8 + column * 18, topPos + 195);

    g.drawString(font, "→", leftPos + 98, topPos + 56, 0xffe8edf2, false);
    int progressWidth = MenuDataSync.scale(menu.progress(), menu.maxProgress(), 24);
    g.fill(leftPos + 84, topPos + 70, leftPos + 108, topPos + 74, 0xff2b3035);
    g.fill(leftPos + 84, topPos + 70, leftPos + 84 + progressWidth, topPos + 74, 0xff66d99a);
    int energyWidth = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    g.fill(leftPos + 8, topPos + 120, leftPos + 168, topPos + 125, 0xff2b3035);
    g.fill(leftPos + 8, topPos + 120, leftPos + 8 + energyWidth, topPos + 125, 0xffd94f67);
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.fabricator.materials"), 46, 22, 0xffaeb7c0);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.fabricator.output"), 140, 22, 0xffaeb7c0);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
    drawFittedString(
        g,
        Component.translatable(
            "screen.siliconic.fabricator.energy",
            menu.energy(),
            menu.capacity(),
            menu.energyPerTick()),
        8,
        109,
        160,
        0xffff8ca0);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.fabricator.status." + status),
        8,
        98,
        160,
        status == 3 ? 0xff66e69a : 0xffffb35c);
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
