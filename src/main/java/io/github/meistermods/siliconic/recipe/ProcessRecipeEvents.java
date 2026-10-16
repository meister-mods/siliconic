package io.github.meistermods.siliconic.recipe;

import io.github.meistermods.siliconic.Siliconic;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Refreshes built-in definitions before listeners inspect newly bound item tags. */
@Mod.EventBusSubscriber(modid = Siliconic.MOD_ID)
public final class ProcessRecipeEvents {
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void tagsUpdated(TagsUpdatedEvent event) {
    if (event.shouldUpdateStaticData()) ModMachineProcesses.refreshDefaults();
  }

  private ProcessRecipeEvents() {}
}
