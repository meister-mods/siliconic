package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlockEntity;
import io.github.meistermods.siliconic.silicon.SiliconProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@SuppressWarnings({"null"})
public class SiliconProcessorScreen extends AbstractContainerScreen<SiliconProcessorMenu> {
  private Button controlButton;

  public SiliconProcessorScreen(SiliconProcessorMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 176;
    imageHeight = 221;
    inventoryLabelY = 128;
  }

  @Override
  protected void init() {
    super.init();
    if (menu.machineKind() == MachineKind.DISTILLATION_TOWER) {
      controlButton =
          addRenderableWidget(
              Button.builder(modeText(), button -> sendButton(0))
                  .bounds(leftPos + 8, topPos + 65, 92, 16)
                  .build());
    } else if (menu.machineKind() == MachineKind.SIEMENS_REACTOR) {
      controlButton =
          addRenderableWidget(
              Button.builder(
                      Component.translatable("screen.siliconic.processor.recover"),
                      button -> sendButton(1))
                  .bounds(leftPos + 8, topPos + 65, 92, 16)
                  .build());
    }
  }

  private void sendButton(int id) {
    if (minecraft == null || minecraft.gameMode == null || minecraft.player == null) return;
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
  }

  private Component modeText() {
    return Component.translatable(
        "screen.siliconic.processor.mode." + (menu.operationMode() == 0 ? "purity" : "throughput"));
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    int inputCount = menu.visibleInputSlots();
    for (int slot = 0; slot < SiliconProcessorBlockEntity.INPUT_SLOTS; slot++)
      if (menu.hasInputSlot(slot))
        slotBox(
            g,
            leftPos + SiliconProcessorMenu.inputX(slot, inputCount),
            topPos + SiliconProcessorMenu.INPUT_Y,
            0xff737a80);
    if (menu.requiresMagma())
      slotBox(
          g,
          leftPos + SiliconProcessorMenu.MAGMA_X,
          topPos + SiliconProcessorMenu.INPUT_Y,
          0xffff8a24);
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 3; column++) {
        int relative = row * 3 + column;
        int border = 0xff737a80;
        if (menu.machineKind() == MachineKind.CHEMICAL_RECYCLER)
          border = relative < 4 ? 0xff55a7c9 : relative < 8 ? 0xffd6b14d : 0xff8d7a9e;
        slotBox(
            g,
            leftPos + SiliconProcessorMenu.OUTPUT_X + column * 18,
            topPos + SiliconProcessorMenu.OUTPUT_Y + row * 18,
            border);
      }
    }
    for (int row = 0; row < 3; row++)
      for (int column = 0; column < 9; column++)
        slotBox(g, leftPos + 8 + column * 18, topPos + 139 + row * 18, 0xff737a80);
    for (int column = 0; column < 9; column++)
      slotBox(g, leftPos + 8 + column * 18, topPos + 197, 0xff737a80);

    for (int slot = 1; slot < inputCount; slot++)
      g.drawString(
          font,
          "+",
          leftPos + SiliconProcessorMenu.inputX(slot, inputCount) - 7,
          topPos + 41,
          0xffe8edf2,
          false);
    g.drawString(font, "→", leftPos + 97, topPos + 41, 0xffe8edf2, false);

    int progressWidth = menu.maxProgress() == 0 ? 0 : 92 * menu.progress() / menu.maxProgress();
    bar(g, leftPos + 8, topPos + 56, 92, progressWidth, 0xff66d99a);
    int energyWidth = menu.capacity() == 0 ? 0 : 160 * menu.energy() / menu.capacity();
    bar(g, leftPos + 8, topPos + 121, 160, energyWidth, 0xffd94f67);

    if (menu.requiresMagma()) {
      int heatHeight = menu.magmaCapacity() == 0 ? 0 : 54 * menu.magmaHeat() / menu.magmaCapacity();
      verticalGauge(g, leftPos + 102, topPos + 20, 3, 54, heatHeight, 0xffff8a24);
      int temperatureHeight =
          menu.targetTemperature() == 0
              ? 0
              : Math.min(54, 54 * menu.temperature() / menu.targetTemperature());
      verticalGauge(g, leftPos + 106, topPos + 20, 3, 54, temperatureHeight, 0xffffd35c);
    }
    int processGauge =
        menu.machineKind() == MachineKind.CHLORINATION_REACTOR
            ? menu.pressure()
            : menu.machineKind() == MachineKind.DISTILLATION_TOWER ? menu.stability() : 0;
    if (processGauge > 0)
      bar(
          g,
          leftPos + 8,
          topPos + 86,
          92,
          92 * processGauge / 1_000,
          menu.machineKind() == MachineKind.CHLORINATION_REACTOR ? 0xffff725c : 0xff63cde8);
  }

  private void bar(GuiGraphics g, int x, int y, int width, int filled, int color) {
    g.fill(x, y, x + width, y + 4, 0xff2b3035);
    if (filled > 0) g.fill(x, y, x + Math.min(width, filled), y + 4, color);
  }

  private void verticalGauge(
      GuiGraphics g, int x, int y, int width, int height, int filled, int color) {
    g.fill(x, y, x + width, y + height, 0xff2b3035);
    if (filled > 0) g.fill(x, y + height - filled, x + width, y + height, color);
  }

  private void slotBox(GuiGraphics g, int x, int y, int border) {
    g.fill(x - 1, y - 1, x + 17, y + 17, border);
    g.fill(x, y, x + 16, y + 16, 0xff202428);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    drawFittedString(g, title, 8, 7, 94, 0xffe8edf2);
    int inputCount = menu.visibleInputSlots();
    for (int slot = 0; slot < SiliconProcessorBlockEntity.INPUT_SLOTS; slot++) {
      if (!menu.hasInputSlot(slot)) continue;
      String key =
          slot == 0
              ? "screen.siliconic.processor.input"
              : "screen.siliconic.processor.input." + menu.machineKind().id() + "." + slot;
      drawCenteredFittedString(
          g,
          Component.translatable(key),
          SiliconProcessorMenu.inputX(slot, inputCount) + 8,
          25,
          22,
          0xffaeb7c0);
    }
    if (menu.requiresMagma())
      drawCenteredFittedString(
          g, Component.translatable("screen.siliconic.processor.magma"), 90, 25, 24, 0xffffa85c);
    g.drawCenteredString(
        font, Component.translatable("screen.siliconic.processor.output"), 136, 9, 0xffaeb7c0);

    Component phase =
        Component.translatable(
            "screen.siliconic.processor.phase." + menu.machineKind().id() + "." + menu.phase());
    drawFittedString(g, phase, 8, 92, 92, 0xff8fd8ff);
    int status = menu.status();
    drawFittedString(
        g,
        Component.translatable("screen.siliconic.processor.status." + status),
        8,
        102,
        160,
        status == 4 ? 0xff66e69a : status == 7 || status == 9 ? 0xffff725c : 0xffffb35c);
    drawFittedString(g, metricText(), 8, 112, 160, 0xffffcf7a);
    g.drawString(
        font, Component.translatable("container.inventory"), 8, inventoryLabelY, 0xffaeb7c0, false);
  }

  private Component metricText() {
    if (menu.machineKind() == MachineKind.CHLORINATION_REACTOR)
      return Component.translatable(
          "screen.siliconic.processor.metrics.pressure",
          menu.temperature(),
          menu.targetTemperature(),
          menu.pressure(),
          menu.energyPerTick());
    if (menu.machineKind() == MachineKind.DISTILLATION_TOWER)
      return Component.translatable(
          "screen.siliconic.processor.metrics.stability",
          menu.temperature(),
          menu.targetTemperature(),
          menu.stability() / 10,
          menu.energyPerTick());
    if (menu.requiresMagma())
      return Component.translatable(
          "screen.siliconic.processor.metrics.thermal",
          menu.temperature(),
          menu.targetTemperature(),
          menu.magmaHeat(),
          menu.energyPerTick());
    return Component.translatable(
        "screen.siliconic.processor.metrics.power",
        menu.energy(),
        menu.capacity(),
        menu.energyPerTick());
  }

  private void drawCenteredFittedString(
      GuiGraphics g, Component text, int centerX, int y, int maxWidth, int color) {
    int width = font.width(text);
    float scale = width > maxWidth ? (float) maxWidth / width : 1.0f;
    g.pose().pushPose();
    g.pose().translate(centerX, y, 0);
    g.pose().scale(scale, scale, 1.0f);
    g.drawString(font, text, -font.width(text) / 2, 0, color, false);
    g.pose().popPose();
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
    if (controlButton != null) {
      if (menu.machineKind() == MachineKind.DISTILLATION_TOWER) {
        controlButton.setMessage(modeText());
        controlButton.active = menu.progress() == 0;
      } else controlButton.active = menu.progress() > 0 && menu.progress() < menu.maxProgress();
    }
    renderBackground(g);
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
