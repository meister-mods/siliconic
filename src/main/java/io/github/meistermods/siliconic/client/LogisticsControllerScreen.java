package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.logistics.LogisticsControllerBlockEntity.EndpointInfo;
import io.github.meistermods.siliconic.logistics.LogisticsControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class LogisticsControllerScreen
    extends AbstractContainerScreen<LogisticsControllerMenu> {
  private static final int INPUT_X = 135;
  private static final int OUTPUT_X = 187;
  private static final int FORCED_X = 239;
  private final Button[] inputButtons = new Button[LogisticsControllerMenu.VISIBLE_ROWS];
  private final Button[] outputButtons = new Button[LogisticsControllerMenu.VISIBLE_ROWS];
  private final Button[] forcedButtons = new Button[LogisticsControllerMenu.VISIBLE_ROWS];
  private Button previousButton;
  private Button nextButton;

  public LogisticsControllerScreen(
      LogisticsControllerMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 310;
    imageHeight = 250;
    inventoryLabelY = 154;
  }

  @Override
  protected void init() {
    super.init();
    previousButton =
        addRenderableWidget(
            Button.builder(Component.literal("<"), button -> sendButton(0))
                .bounds(leftPos + 245, topPos + 5, 20, 16)
                .build());
    nextButton =
        addRenderableWidget(
            Button.builder(Component.literal(">"), button -> sendButton(1))
                .bounds(leftPos + 283, topPos + 5, 20, 16)
                .build());
    for (int row = 0; row < LogisticsControllerMenu.VISIBLE_ROWS; row++) {
      final int buttonRow = row;
      int y = topPos + LogisticsControllerMenu.ROW_Y - 2 + row * LogisticsControllerMenu.ROW_SPACING;
      inputButtons[row] =
          addRenderableWidget(
              Button.builder(Component.empty(), button -> sendButton(10 + buttonRow * 3))
                  .bounds(leftPos + INPUT_X, y, 50, 20)
                  .build());
      outputButtons[row] =
          addRenderableWidget(
              Button.builder(Component.empty(), button -> sendButton(11 + buttonRow * 3))
                  .bounds(leftPos + OUTPUT_X, y, 50, 20)
                  .build());
      forcedButtons[row] =
          addRenderableWidget(
              Button.builder(Component.empty(), button -> sendButton(12 + buttonRow * 3))
                  .bounds(leftPos + FORCED_X, y, 62, 20)
                  .build());
    }
    updateButtons();
  }

  private void sendButton(int id) {
    if (minecraft == null || minecraft.gameMode == null) return;
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
  }

  @Override
  protected void containerTick() {
    super.containerTick();
    updateButtons();
  }

  private void updateButtons() {
    if (previousButton == null || nextButton == null) return;
    previousButton.active = menu.page() > 0;
    nextButton.active = menu.page() + 1 < menu.pageCount();
    for (int row = 0; row < LogisticsControllerMenu.VISIBLE_ROWS; row++) {
      EndpointInfo endpoint = menu.endpointAtRow(row);
      boolean visible = endpoint != null;
      inputButtons[row].visible = visible;
      outputButtons[row].visible = visible;
      forcedButtons[row].visible = visible;
      if (!visible) continue;
      inputButtons[row].setMessage(modeText("input", menu.inputEnabled(row)));
      outputButtons[row].setMessage(modeText("output", menu.outputEnabled(row)));
      forcedButtons[row].setMessage(modeText("forced", menu.forcedEnabled(row)));
      forcedButtons[row].active = endpoint.supportsForced() || menu.forcedEnabled(row);
    }
  }

  private Component modeText(String mode, boolean enabled) {
    return Component.translatable(
        "screen.siliconic.logistics_controller." + mode + "." + (enabled ? "on" : "off"));
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    g.fill(leftPos + 7, topPos + 25, leftPos + imageWidth - 7, topPos + 154, 0xff20252a);
    for (int row = 0; row < LogisticsControllerMenu.VISIBLE_ROWS; row++) {
      if (menu.endpointAtRow(row) == null) continue;
      int y = topPos + LogisticsControllerMenu.ROW_Y + row * LogisticsControllerMenu.ROW_SPACING;
      g.fill(leftPos + 9, y - 3, leftPos + imageWidth - 9, y + 19, 0xff292f34);
      slotBox(g, leftPos + LogisticsControllerMenu.FILTER_X, y);
    }
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(
            g,
            leftPos + LogisticsControllerMenu.PLAYER_X + column * 18,
            topPos + LogisticsControllerMenu.PLAYER_Y + row * 18);
    for (int column = 0; column < 9; column++)
      slotBox(
          g,
          leftPos + LogisticsControllerMenu.PLAYER_X + column * 18,
          topPos + LogisticsControllerMenu.PLAYER_Y + 58);
  }

  private void slotBox(GuiGraphics g, int x, int y) {
    g.fill(x - 1, y - 1, x + 17, y + 17, 0xff737a80);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, title, 8, 8, 0xffe8edf2, false);
    Component page =
        Component.translatable(
            "screen.siliconic.logistics_controller.page", menu.page() + 1, menu.pageCount());
    g.drawCenteredString(font, page, 274, 9, 0xffaeb7c0);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.logistics_controller.machine"),
        11,
        26,
        0xffaeb7c0,
        false);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.logistics_controller.filter"),
        109,
        26,
        0xffaeb7c0,
        false);

    if (menu.endpoints().isEmpty())
      g.drawCenteredString(
          font,
          Component.translatable("screen.siliconic.logistics_controller.none"),
          imageWidth / 2,
          83,
          0xff89939c);

    for (int row = 0; row < LogisticsControllerMenu.VISIBLE_ROWS; row++) {
      EndpointInfo endpoint = menu.endpointAtRow(row);
      if (endpoint == null) continue;
      int y = LogisticsControllerMenu.ROW_Y + row * LogisticsControllerMenu.ROW_SPACING;
      drawFittedString(g, endpoint.name(), 11, y - 1, 98, 0xffe8edf2);
      g.drawString(
          font,
          Component.literal(
              endpoint.pos().getX()
                  + ", "
                  + endpoint.pos().getY()
                  + ", "
                  + endpoint.pos().getZ()),
          11,
          y + 9,
          0xff89939c,
          false);
    }
    g.drawString(
        font,
        Component.translatable("container.inventory"),
        LogisticsControllerMenu.PLAYER_X,
        inventoryLabelY,
        0xffaeb7c0,
        false);
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
