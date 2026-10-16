package io.github.meistermods.siliconic.client;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Invalidates views before other client listeners inspect newly synchronized recipes. */
@Mod.EventBusSubscriber(modid = Siliconic.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipeEvents {
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void recipesUpdated(RecipesUpdatedEvent event) {
    ModMachineProcesses.invalidateCache(event.getRecipeManager());
    ReprocessingProcess.invalidateCache(event.getRecipeManager());
  }

  private ClientRecipeEvents() {}
}
