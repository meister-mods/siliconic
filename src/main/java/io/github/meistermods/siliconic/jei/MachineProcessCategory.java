package io.github.meistermods.siliconic.jei;

import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

@SuppressWarnings({"null"})
public class MachineProcessCategory extends AbstractRecipeCategory<MachineProcess> {
  private static final int WIDTH = 176;
  private static final int HEIGHT = 90;
  private static final int SINGLE_OUTPUT_X = 140;
  private static final int OUTPUT_GRID_X = 126;
  private static final int OUTPUT_GRID_Y = 6;
  private static final int OUTPUT_SPACING = 28;
  private final MachineKind machine;
  private final IDrawable arrow;

  public MachineProcessCategory(
      RecipeType<MachineProcess> recipeType,
      MachineKind machine,
      Component title,
      ItemLike iconItem,
      IGuiHelper guiHelper) {
    super(recipeType, title, guiHelper.createDrawableItemLike(iconItem), WIDTH, HEIGHT);
    this.machine = machine;
    this.arrow = guiHelper.getRecipeArrow();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, MachineProcess process, IFocusGroup focuses) {
    if (isGridMachine()) {
      if (process.shaped()) {
        for (int slot = 0; slot < 9; slot++) {
          var slotBuilder =
              builder
                  .addInputSlot(8 + slot % 3 * 18, 6 + slot / 3 * 18)
                  .setStandardSlotBackground();
          int currentSlot = slot;
          process.inputs().stream()
              .filter(input -> input.slot() == currentSlot)
              .findFirst()
              .ifPresent(input -> slotBuilder.addItemStacks(input.stacks()));
        }
      } else {
        builder.setShapeless(64, 6);
        for (int slot = 0; slot < 9; slot++) {
          var slotBuilder =
              builder
                  .addInputSlot(8 + slot % 3 * 18, 6 + slot / 3 * 18)
                  .setStandardSlotBackground();
          if (slot < process.inputs().size())
            slotBuilder.addItemStacks(process.inputs().get(slot).stacks());
        }
      }
    } else {
      for (int index = 0; index < process.inputs().size(); index++) {
        var input = process.inputs().get(index);
        builder
            .addInputSlot(12 + index * 24, 24)
            .setStandardSlotBackground()
            .addItemStacks(input.stacks());
      }
    }
    var outputs = process.outputCopies();
    for (int index = 0; index < outputs.size(); index++) {
      int x = outputs.size() == 1 ? SINGLE_OUTPUT_X : OUTPUT_GRID_X + index % 2 * OUTPUT_SPACING;
      int y = outputs.size() == 1 ? 24 : OUTPUT_GRID_Y + index / 2 * OUTPUT_SPACING;
      builder.addOutputSlot(x, y).setOutputSlotBackground().addItemStack(outputs.get(index));
    }
  }

  @Override
  public void draw(
      MachineProcess process,
      mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    arrow.draw(graphics, 86, 24);
    String seconds =
        process.ticks() % 20 == 0
            ? Integer.toString(process.ticks() / 20)
            : String.format(Locale.ROOT, "%.1f", process.ticks() / 20.0);
    Component details =
        Component.translatable("jei.siliconic.process.details", seconds, process.energyPerTick());
    if (!isGridMachine()) {
      Component special = Component.translatable("jei.siliconic.process.special." + machine.id());
      drawCenteredFitted(graphics, special, 56, 0xff505a66);
    }
    if (machine.requiresHeat()) {
      Component magma =
          Component.translatable(
              "jei.siliconic.process.magma",
              machine.thermalProfile().targetTemperature(),
              machine.thermalProfile().magmaPerHeatingTick());
      drawCenteredFitted(graphics, magma, 67, 0xffb84b16);
    }
    drawCenteredFitted(graphics, details, machine.requiresHeat() ? 78 : 70, 0xff404040);
  }

  @Override
  public net.minecraft.resources.ResourceLocation getRegistryName(MachineProcess process) {
    return process.id();
  }

  private boolean isGridMachine() {
    return machine == MachineKind.WAFER_FABRICATOR || machine == MachineKind.GATE_FABRICATOR;
  }

  private void drawCenteredFitted(GuiGraphics graphics, Component text, int y, int color) {
    var font = Minecraft.getInstance().font;
    int width = font.width(text);
    float scale = width > WIDTH - 4 ? (float) (WIDTH - 4) / width : 1.0f;
    graphics.pose().pushPose();
    graphics.pose().translate(WIDTH / 2.0f, y, 0);
    graphics.pose().scale(scale, scale, 1.0f);
    graphics.drawString(font, text, -width / 2, 0, color, false);
    graphics.pose().popPose();
  }
}
