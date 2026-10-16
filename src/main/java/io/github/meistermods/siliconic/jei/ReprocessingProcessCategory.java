package io.github.meistermods.siliconic.jei;

import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IScrollGridWidgetFactory;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public final class ReprocessingProcessCategory extends AbstractRecipeCategory<ReprocessingProcess> {
  private static final int WIDTH = 176;
  private static final int HEIGHT = 78;
  private static final int OUTPUT_CENTER_X = 126;
  private static final int OUTPUT_SPACING = 28;
  private final IDrawable arrow;
  private final IScrollGridWidgetFactory<?> outputGrid;

  public ReprocessingProcessCategory(
      RecipeType<ReprocessingProcess> recipeType, IGuiHelper guiHelper) {
    super(
        recipeType,
        Component.translatable("container.siliconic.reprocessor"),
        guiHelper.createDrawableItemLike(ModItems.REPROCESSOR.get()),
        WIDTH,
        HEIGHT);
    this.arrow = guiHelper.getRecipeArrow();
    this.outputGrid = guiHelper.createScrollGridFactory(2, 2);
    this.outputGrid.setPosition(112, 8);
  }

  @Override
  public void setRecipe(
      IRecipeLayoutBuilder builder, ReprocessingProcess process, IFocusGroup focuses) {
    builder
        .addInputSlot(20, 24)
        .setStandardSlotBackground()
        .addItemStack(new ItemStack(process.input(), process.inputCount()));

    List<ItemStack> outputs = process.outputCopies();
    int firstOutputX = OUTPUT_CENTER_X - (outputs.size() - 1) * OUTPUT_SPACING / 2;
    for (int index = 0; index < outputs.size(); index++) {
      if (outputs.size() > 3) {
        builder
            .addSlotToWidget(RecipeIngredientRole.OUTPUT, outputGrid)
            .setStandardSlotBackground()
            .addItemStack(outputs.get(index));
        continue;
      }
      builder
          .addOutputSlot(firstOutputX + index * OUTPUT_SPACING, 24)
          .setOutputSlotBackground()
          .addItemStack(outputs.get(index));
    }
  }

  @Override
  public void draw(
      ReprocessingProcess process,
      mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    arrow.draw(graphics, 58, 24);
    String seconds =
        process.ticks() % 20 == 0
            ? Integer.toString(process.ticks() / 20)
            : String.format(Locale.ROOT, "%.1f", process.ticks() / 20.0);
    Component details =
        Component.translatable(
            "jei.siliconic.process.details",
            seconds,
            process.energyPerTick(),
            process.totalEnergy());
    var font = Minecraft.getInstance().font;
    int detailsWidth = font.width(details);
    float scale = detailsWidth > WIDTH - 4 ? (float) (WIDTH - 4) / detailsWidth : 1.0f;
    graphics.pose().pushPose();
    graphics.pose().translate(WIDTH / 2.0f, 66, 0);
    graphics.pose().scale(scale, scale, 1.0f);
    graphics.drawString(font, details, -detailsWidth / 2, 0, 0xff404040, false);
    graphics.pose().popPose();
  }

  @Override
  public ResourceLocation getRegistryName(ReprocessingProcess process) {
    return process.id();
  }
}
