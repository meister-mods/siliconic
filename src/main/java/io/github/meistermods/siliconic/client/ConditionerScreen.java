package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.cleanroom.ConditionerMenu;
import io.github.meistermods.siliconic.cleanroom.RoomScanResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings({"null"})
public class ConditionerScreen extends AbstractContainerScreen<ConditionerMenu> {
  private static final int VISIBLE_ROWS = 7;
  private int scrollOffset;

  public ConditionerScreen(ConditionerMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 270;
    imageHeight = 250;
    inventoryLabelY = 1_000;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff17191c);
    int contentRight = leftPos + imageWidth - 7;
    int contentWidth = imageWidth - 14;
    int insetBarWidth = imageWidth - 24;
    g.fill(leftPos + 7, topPos + 18, contentRight, topPos + 98, 0xff20252a);
    g.fill(leftPos + 7, topPos + 126, contentRight, topPos + 241, 0xff20252a);

    int cleanlinessWidth = insetBarWidth * menu.cleanliness() / 100;
    g.fill(leftPos + 12, topPos + 59, leftPos + 12 + insetBarWidth, topPos + 64, 0xff2b3035);
    g.fill(leftPos + 12, topPos + 59, leftPos + 12 + cleanlinessWidth, topPos + 64, 0xff66e69a);

    int energyWidth = menu.capacity() == 0 ? 0 : contentWidth * menu.energy() / menu.capacity();
    g.fill(leftPos + 7, topPos + 116, contentRight, topPos + 121, 0xff2b3035);
    g.fill(leftPos + 7, topPos + 116, leftPos + 7 + energyWidth, topPos + 121, 0xff5db7e8);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    RoomScanResult result = menu.lastScan();
    g.drawString(font, title, 8, 6, 0xffe8edf2, false);
    Component conditionerCount =
        Component.translatable("screen.siliconic.conditioner.count", menu.conditionerCount());
    g.drawString(
        font,
        conditionerCount,
        imageWidth - 8 - font.width(conditionerCount),
        6,
        0xff8bcff3,
        false);
    g.drawString(
        font,
        Component.translatable(
            menu.powered()
                ? "screen.siliconic.conditioner.powered"
                : "screen.siliconic.conditioner.unpowered"),
        12,
        23,
        menu.powered() ? 0xff66e69a : 0xffffb35c,
        false);
    Component scanStatus =
        Component.translatable(
            "screen.siliconic.conditioner.status."
                + result.status().name().toLowerCase(Locale.ROOT));
    g.drawString(font, scanStatus, 12, 35, result.isSealed() ? 0xff66e69a : 0xffffb35c, false);
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.conditioner.cleanliness",
            menu.cleanliness(),
            menu.cleanlinessLimit()),
        12,
        47,
        0xffd5dce3,
        false);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.conditioner.coating", menu.coatingCoverage()),
        12,
        69,
        0xffd5dce3,
        false);
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.conditioner.contamination",
            menu.unprotectedEntities(),
            menu.equipmentPollutionSources(),
            menu.blockPollutionSources(),
            menu.totalContaminationPerScan()),
        12,
        81,
        0xffd5dce3,
        false);
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.conditioner.volume", result.volume(), result.scannedPositions()),
        12,
        91,
        0xffd5dce3,
        false);
    g.drawString(
        font,
        Component.translatable(
            "screen.siliconic.conditioner.energy",
            menu.energy(),
            menu.capacity(),
            menu.energyPerTick()),
        8,
        104,
        0xff8bcff3,
        false);
    g.drawString(
        font,
        Component.translatable("screen.siliconic.conditioner.details"),
        12,
        130,
        0xffaeb7c0,
        false);

    List<Component> rows = detailRows(result);
    int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
    scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    for (int row = 0; row < VISIBLE_ROWS && row + scrollOffset < rows.size(); row++)
      g.drawString(font, rows.get(row + scrollOffset), 14, 143 + row * 12, 0xffd5dce3, false);
    if (maxScroll > 0)
      g.drawString(
          font,
          Component.translatable(
              "screen.siliconic.conditioner.scroll", scrollOffset + 1, maxScroll + 1),
          208,
          228,
          0xff89939c,
          false);
  }

  private List<Component> detailRows(RoomScanResult result) {
    List<Component> rows = new ArrayList<>();
    rows.add(Component.translatable("screen.siliconic.conditioner.materials"));
    if (result.surfaceMaterials().isEmpty())
      rows.add(Component.translatable("screen.siliconic.conditioner.none"));
    else
      result
          .surfaceMaterials()
          .forEach(
              (id, faces) ->
                  rows.add(
                      Component.translatable(
                          "screen.siliconic.conditioner.material", blockName(id), faces)));
    rows.add(Component.translatable("screen.siliconic.conditioner.closures"));
    if (result.openables().isEmpty())
      rows.add(Component.translatable("screen.siliconic.conditioner.none"));
    else
      result
          .openables()
          .forEach(
              (id, stats) ->
                  rows.add(
                      Component.translatable(
                          "screen.siliconic.conditioner.closure",
                          blockName(id),
                          stats.total(),
                          stats.open())));
    return rows;
  }

  private Component blockName(ResourceLocation id) {
    Block block = ForgeRegistries.BLOCKS.getValue(id);
    return block == null ? Component.literal(id.toString()) : block.getName();
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
    int maxScroll = Math.max(0, detailRows(menu.lastScan()).size() - VISIBLE_ROWS);
    if (maxScroll == 0) return super.mouseScrolled(mouseX, mouseY, delta);
    scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
    return true;
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    renderBackground(g);
    super.render(g, mouseX, mouseY, partial);
    renderTooltip(g, mouseX, mouseY);
  }
}
