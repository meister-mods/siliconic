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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

@SuppressWarnings({"null"})
public class MachineProcessCategory extends AbstractRecipeCategory<MachineProcess> {
  private static final int WIDTH = 150;
  private static final int HEIGHT = 78;
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
  public void setRecipe(
      IRecipeLayoutBuilder builder, MachineProcess process, IFocusGroup focuses) {
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
              .ifPresent(input -> slotBuilder.addItemStack(input.stack()));
        }
      } else {
        builder.setShapeless(64, 6);
        for (int slot = 0; slot < 9; slot++) {
          var slotBuilder =
              builder
                  .addInputSlot(8 + slot % 3 * 18, 6 + slot / 3 * 18)
                  .setStandardSlotBackground();
          if (slot < process.inputs().size())
            slotBuilder.addItemStack(process.inputs().get(slot).stack());
        }
      }
    } else {
      for (int index = 0; index < process.inputs().size(); index++) {
        var input = process.inputs().get(index);
        builder
            .addInputSlot(12 + index * 24, 24)
            .setStandardSlotBackground()
            .addItemStack(input.stack());
      }
    }
    builder
        .addOutputSlot(124, 24)
        .setOutputSlotBackground()
        .addItemStack(process.result());
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
        Component.translatable(
            "jei.siliconic.process.details",
            seconds,
            process.energyPerTick(),
            process.totalEnergy());
    graphics.drawCenteredString(
        Minecraft.getInstance().font, details, WIDTH / 2, 66, 0xff404040);
  }

  @Override
  public net.minecraft.resources.ResourceLocation getRegistryName(MachineProcess process) {
    return process.id();
  }

  private boolean isGridMachine() {
    return machine == MachineKind.WAFER_FABRICATOR || machine == MachineKind.GATE_ASSEMBLER;
  }
}
