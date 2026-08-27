package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.wafer.WaferDuplicatorMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class WaferDuplicatorScreen extends AbstractContainerScreen<WaferDuplicatorMenu> {
  public WaferDuplicatorScreen(WaferDuplicatorMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 231;
    inventoryLabelY = 136;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    for (int[] slot : machineSlots()) slotBox(g, leftPos + slot[0], topPos + slot[1]);
    int width = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    g.fill(leftPos + 8, topPos + 129, leftPos + 168, topPos + 135, 0xff2b3035);
    g.fill(leftPos + 8, topPos + 129, leftPos + 8 + width, topPos + 135, 0xffd94f67);
    g.drawString(font, "+", leftPos + 45, topPos + 35, 0xffe8edf2, false);
    g.drawString(font, "→", leftPos + 103, topPos + 35, 0xffe8edf2, false);
  }

  private int[][] machineSlots() {
    int[][] positions = new int[57][2];
    positions[0] = new int[] {20, 31};
    positions[1] = new int[] {61, 31};
    positions[2] = new int[] {138, 31};
    int index = 3;
    for (int row = 0; row < 2; row++)
      for (int column = 0; column < 9; column++)
        positions[index++] = new int[] {8 + column * 18, 68 + row * 18};
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        positions[index++] = new int[] {8 + column * 18, 147 + row * 18};
    for (int column = 0; column < 9; column++)
      positions[index++] = new int[] {8 + column * 18, 207};
    return positions;
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 7, 0xffe8edf2, false);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.duplicator.materials"),
        8,
        56,
        0xffaeb7c0,
        false);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.duplicator.source"), 28, 20, 0xffaeb7c0);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.duplicator.blank"), 69, 20, 0xffaeb7c0);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.duplicator.output"), 146, 20, 0xffaeb7c0);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
    drawFittedString(
        g,
        Component.translatable(
            "screen.siliconic.duplicator.energy", menu.energy(), menu.capacity(), menu.cost()),
        8,
        107,
        160,
        0xffff8ca0);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.duplicator.status." + status),
        8,
        118,
        160,
        status == 6 ? 0xff66e69a : 0xffffb35c);
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
    if (mouseX >= leftPos + 8
        && mouseX < leftPos + 100
        && mouseY >= topPos + 54
        && mouseY < topPos + 66) {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.translatable("screen.siliconic.duplicator.requirements"));
      for (var requirement : menu.requirements())
        lines.add(
            Component.literal(requirement.getCount() + "× ").append(requirement.getHoverName()));
      if (lines.size() == 1)
        lines.add(Component.translatable("screen.siliconic.duplicator.no_requirements"));
      g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
  }
}
