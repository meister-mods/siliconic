package io.github.meistermods.siliconic.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.fabrication.FabricationStationBlockEntity;
import io.github.meistermods.siliconic.fabrication.FabricationStationMenu;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ProcessInput;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import io.github.meistermods.siliconic.reprocessing.ReprocessorBlockEntity;
import io.github.meistermods.siliconic.reprocessing.ReprocessorMenu;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlockEntity;
import io.github.meistermods.siliconic.silicon.SiliconProcessorMenu;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/** Regression coverage for process output conservation and data-pack boundaries. */
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProcessStabilityGameTests {
  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesEveryPendingIndustrialOutput(GameTestHelper helper) {
    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(
            BlockPos.ZERO, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState());
    CompoundTag saved = new CompoundTag();
    saved.putInt("LayoutVersion", 2);
    ListTag pending = new ListTag();
    // Separate byproducts can merge into a single output slot, so their number is not slot-limited.
    for (int index = 0; index < SiliconProcessorBlockEntity.OUTPUT_SLOTS + 3; index++)
      pending.add(new ItemStack(Items.DIAMOND).save(new CompoundTag()));
    saved.put("PendingResults", pending);
    processor.load(saved);
    ListTag restored = processor.saveWithoutMetadata().getList("PendingResults", Tag.TAG_COMPOUND);
    helper.assertTrue(
        restored.equals(pending),
        "Reloading a completed batch must preserve every mergeable output entry");
    processor.load(processor.saveWithoutMetadata());
    helper.assertTrue(
        processor.saveWithoutMetadata().getList("PendingResults", Tag.TAG_COMPOUND).equals(pending),
        "Repeated saves must not discard outputs beyond the number of physical slots");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", batch = "process_reload")
  public static void keepsCompletedBatchConsumptionAfterRecipeReload(GameTestHelper helper) {
    assertCompletedBatchSurvivesRecipeChange(helper, false, false);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", batch = "process_removal")
  public static void preservesCompletedBatchAfterRecipeRemoval(GameTestHelper helper) {
    assertCompletedBatchSurvivesRecipeChange(helper, true, false);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", batch = "process_vent_reload")
  public static void ventsOriginalPendingBatchAfterRecipeRemoval(GameTestHelper helper) {
    assertCompletedBatchSurvivesRecipeChange(helper, true, true);
  }

  private static void assertCompletedBatchSurvivesRecipeChange(
      GameTestHelper helper, boolean remove, boolean vent) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/pending_reload");
    MachineProcess before = chlorinationProcess(id, 4);
    List<Recipe<?>> recipes = new ArrayList<>(original);
    recipes.add(before);
    manager.replaceRecipes(recipes);
    helper.runAfterDelay(
        1,
        () -> {
          try {
            BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
            SiliconProcessorBlockEntity processor =
                new SiliconProcessorBlockEntity(
                    pos, ModBlocks.CHLORINATION_REACTOR.get().defaultBlockState());
            processor.setLevel(helper.getLevel());
            CompoundTag saved = processor.saveWithoutMetadata();
            ItemStackHandler inventory =
                new ItemStackHandler(SiliconProcessorBlockEntity.SLOT_COUNT);
            inventory.setStackInSlot(0, new ItemStack(Items.BEDROCK, 4));
            ItemStack tool = new ItemStack(Items.WOODEN_PICKAXE);
            tool.setDamageValue(7);
            inventory.setStackInSlot(1, tool);
            ItemStack catalyst = new ItemStack(Items.EMERALD, 2);
            catalyst.getOrCreateTag().putString("Batch", "original");
            inventory.setStackInSlot(2, catalyst);
            for (int slot = SiliconProcessorBlockEntity.OUTPUT_START;
                slot < SiliconProcessorBlockEntity.SLOT_COUNT;
                slot++) inventory.setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            saved.put("Items", inventory.serializeNBT());
            saved.putInt("Energy", 10);
            saved.putInt("Temperature", 450);
            processor.load(saved);
            SiliconProcessorBlockEntity.serverTick(
                helper.getLevel(), pos, processor.getBlockState(), processor);
            helper.assertTrue(
                !processor
                    .saveWithoutMetadata()
                    .getList("PendingResults", Tag.TAG_COMPOUND)
                    .isEmpty(),
                "A blocked chlorination batch must first complete and retain its outputs");
            helper.assertTrue(
                processor.items().getStackInSlot(0).getCount() == 4,
                "A completed batch must keep its original inputs until delivery");
            if (vent) {
              for (int tick = 0; tick < 124; tick++)
                SiliconProcessorBlockEntity.serverTick(
                    helper.getLevel(), pos, processor.getBlockState(), processor);
              helper.assertTrue(
                  processor.saveWithoutMetadata().getInt("Pressure") == 994,
                  "The original pending batch must naturally approach its pressure limit");
            }
            // Save and restore as well as reload recipes: both boundaries must preserve the batch.
            CompoundTag pending = processor.saveWithoutMetadata();
            SiliconProcessorBlockEntity restored =
                new SiliconProcessorBlockEntity(pos, processor.getBlockState());
            restored.setLevel(helper.getLevel());
            restored.load(pending);
            List<Recipe<?>> changed = new ArrayList<>(original);
            if (!remove) changed.add(chlorinationProcess(id, 1));
            manager.replaceRecipes(changed);
            helper.runAfterDelay(
                1,
                () -> {
                  try {
                    if (!vent)
                      restored
                          .items()
                          .setStackInSlot(
                              SiliconProcessorBlockEntity.OUTPUT_START, ItemStack.EMPTY);
                    SiliconProcessorBlockEntity.serverTick(
                        helper.getLevel(), pos, restored.getBlockState(), restored);
                    helper.assertTrue(
                        restored.items().getStackInSlot(0).isEmpty(),
                        "A completed batch must consume its original four inputs after recipe"
                            + " replacement");
                    if (vent) {
                      helper.assertTrue(
                          restored.saveWithoutMetadata().getInt("VentedTicks") == 100
                              && restored
                                  .saveWithoutMetadata()
                                  .getList("PendingResults", Tag.TAG_COMPOUND)
                                  .isEmpty()
                              && restored.items().getStackInSlot(1).getDamageValue() == 12
                              && restored.items().getStackInSlot(2).getCount() == 2
                              && restored
                                  .items()
                                  .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                                  .is(Items.STONE),
                          "Pressure venting must consume the original pending inputs and tool"
                              + " damage without emitting outputs");
                      SiliconProcessorBlockEntity.serverTick(
                          helper.getLevel(), pos, restored.getBlockState(), restored);
                      helper.assertTrue(
                          restored.saveWithoutMetadata().getInt("VentedTicks") == 99
                              && restored.items().getStackInSlot(1).getDamageValue() == 12,
                          "A vented pending batch must not consume its tool a second time");
                      helper.succeed();
                      return;
                    }
                    helper.assertTrue(
                        restored
                            .items()
                            .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                            .is(Items.DIAMOND),
                        "A completed batch must deliver its original result after recipe"
                            + " replacement");
                    helper.assertTrue(
                        restored.items().getStackInSlot(1).getDamageValue() == 12
                            && ItemStack.isSameItemSameTags(
                                restored.items().getStackInSlot(2), catalyst)
                            && restored.items().getStackInSlot(2).getCount() == 2,
                        "Pending delivery must preserve the original tool damage and catalyst"
                            + " tags");
                    SiliconProcessorBlockEntity.serverTick(
                        helper.getLevel(), pos, restored.getBlockState(), restored);
                    helper.assertTrue(
                        restored.items().getStackInSlot(1).getDamageValue() == 12
                            && restored
                                    .items()
                                    .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                                    .getCount()
                                == 1,
                        "A pending batch must consume and deliver only once");
                    helper.succeed();
                  } finally {
                    manager.replaceRecipes(original);
                  }
                });
          } catch (RuntimeException | Error exception) {
            manager.replaceRecipes(original);
            throw exception;
          }
        });
  }

  private static MachineProcess chlorinationProcess(ResourceLocation id, int count) {
    return new MachineProcess(
        id,
        MachineKind.CHLORINATION_REACTOR,
        List.of(
            new ProcessInput(0, Ingredient.of(Items.BEDROCK), count),
            new ProcessInput(
                1, Ingredient.of(Items.WOODEN_PICKAXE), count + 1, ProcessInput.Use.DAMAGE),
            new ProcessInput(2, Ingredient.of(Items.EMERALD), 1, ProcessInput.Use.CATALYST)),
        Items.DIAMOND,
        1,
        List.of(),
        1,
        1,
        true);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void keepsLockedFabricationInputsDuringQuickMove(GameTestHelper helper) {
    FabricationStationBlockEntity station =
        new FabricationStationBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_FABRICATOR.get().defaultBlockState());
    ItemStackHandler items = new ItemStackHandler(FabricationStationBlockEntity.SLOT_COUNT);
    items.setStackInSlot(0, new ItemStack(Items.BEDROCK));
    CompoundTag saved = new CompoundTag();
    saved.put("Items", items.serializeNBT());
    saved.put("PendingResult", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
    station.load(saved);
    Player player = helper.makeMockPlayer();
    Inventory inventory = new Inventory(player);
    inventory.setItem(9, new ItemStack(Items.BEDROCK, 4));
    FabricationStationMenu menu = new FabricationStationMenu(0, inventory, station);
    player.containerMenu = menu;
    helper.assertTrue(
        menu.quickMoveStack(player, FabricationStationBlockEntity.SLOT_COUNT).isEmpty(),
        "Shift-click must reject insertion into a locked fabrication input");
    helper.assertTrue(
        station.items().getStackInSlot(0).getCount() == 1 && inventory.getItem(9).getCount() == 4,
        "Shift-click must preserve both sides of a rejected transfer");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void keepsIndustrialInputsLockedButAcceptsHeatingFuel(GameTestHelper helper) {
    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(
            BlockPos.ZERO, ModBlocks.CHLORINATION_REACTOR.get().defaultBlockState());
    ItemStackHandler items = new ItemStackHandler(SiliconProcessorBlockEntity.SLOT_COUNT);
    items.setStackInSlot(0, new ItemStack(ModItems.METALLURGICAL_SILICON.get()));
    CompoundTag saved = new CompoundTag();
    saved.putInt("LayoutVersion", 2);
    saved.put("Items", items.serializeNBT());
    saved.put("PendingResult", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
    processor.load(saved);
    Player player = helper.makeMockPlayer();
    Inventory inventory = new Inventory(player);
    inventory.setItem(9, new ItemStack(ModItems.METALLURGICAL_SILICON.get(), 4));
    SiliconProcessorMenu menu = new SiliconProcessorMenu(0, inventory, processor);
    player.containerMenu = menu;
    int firstPlayerSlot = menu.slots.size() - 36;
    helper.assertTrue(
        menu.quickMoveStack(player, firstPlayerSlot).isEmpty(),
        "Shift-click must not merge into a locked industrial process input");
    helper.assertTrue(
        processor.items().getStackInSlot(0).getCount() == 1 && inventory.getItem(9).getCount() == 4,
        "A rejected industrial transfer must preserve both inventories");
    inventory.setItem(9, new ItemStack(Items.MAGMA_CREAM, 2));
    helper.assertTrue(
        !menu.quickMoveStack(player, firstPlayerSlot).isEmpty()
            && inventory.getItem(9).isEmpty()
            && processor.items().getStackInSlot(SiliconProcessorBlockEntity.MAGMA_SLOT).getCount()
                == 2,
        "Heating fuel must remain insertable while process inputs are locked");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesLegacyPendingBatchConsumption(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(
            pos, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState());
    processor.setLevel(helper.getLevel());
    ItemStackHandler items = new ItemStackHandler(SiliconProcessorBlockEntity.SLOT_COUNT);
    items.setStackInSlot(0, new ItemStack(Items.QUARTZ, 4));
    items.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 4));
    items.setStackInSlot(2, new ItemStack(ModItems.CARBON_ELECTRODE.get()));
    CompoundTag saved = new CompoundTag();
    saved.putInt("LayoutVersion", 2);
    saved.put("Items", items.serializeNBT());
    saved.put("PendingResult", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
    processor.load(saved);
    SiliconProcessorBlockEntity.serverTick(
        helper.getLevel(), pos, processor.getBlockState(), processor);
    SiliconProcessorBlockEntity.serverTick(
        helper.getLevel(), pos, processor.getBlockState(), processor);
    helper.assertTrue(
        processor.items().getStackInSlot(0).isEmpty()
            && processor.items().getStackInSlot(1).isEmpty()
            && processor.items().getStackInSlot(2).getDamageValue() == 1
            && processor
                .items()
                .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                .is(Items.DIAMOND)
            && processor.items().getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START).getCount()
                == 1,
        "Legacy pending saves must consume their inputs and damage their tool exactly once");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsProcessMenusFromOtherDimensions(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    ServerLevel otherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
    helper.assertTrue(otherLevel != null, "The test server must provide the Nether dimension");
    Player outsider =
        new FakePlayer(otherLevel, new GameProfile(UUID.randomUUID(), "process-outsider"));
    outsider.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    Inventory inventory = new Inventory(outsider);
    Player local = helper.makeMockPlayer();
    local.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.WAFER_FABRICATOR.get());
    FabricationStationBlockEntity station =
        (FabricationStationBlockEntity) helper.getLevel().getBlockEntity(pos);
    FabricationStationMenu fabricationMenu = new FabricationStationMenu(0, inventory, station);
    helper.assertTrue(
        fabricationMenu.stillValid(local) && !fabricationMenu.stillValid(outsider),
        "Fabrication menus must require the same dimension as their live block entity");

    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.SILICON_ARC_FURNACE.get());
    SiliconProcessorBlockEntity processor =
        (SiliconProcessorBlockEntity) helper.getLevel().getBlockEntity(pos);
    SiliconProcessorMenu siliconMenu = new SiliconProcessorMenu(0, inventory, processor);
    helper.assertTrue(
        siliconMenu.stillValid(local) && !siliconMenu.stillValid(outsider),
        "Industrial menus must reject a player at matching coordinates in another dimension");

    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.REPROCESSOR.get());
    ReprocessorBlockEntity reprocessor =
        (ReprocessorBlockEntity) helper.getLevel().getBlockEntity(pos);
    ReprocessorMenu reprocessorMenu = new ReprocessorMenu(0, inventory, reprocessor);
    helper.assertTrue(
        reprocessorMenu.stillValid(local) && !reprocessorMenu.stillValid(outsider),
        "Reprocessor menus must reject a player in another dimension");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void marksPartiallyTransferredProcessOutputsDirty(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    Player player = helper.makeMockPlayer();
    for (Block block :
        List.of(
            ModBlocks.WAFER_FABRICATOR.get(),
            ModBlocks.SILICON_ARC_FURNACE.get(),
            ModBlocks.REPROCESSOR.get())) {
      helper.setBlock(GameTestFixtures.ORIGIN, block);
      BlockEntity entity = helper.getLevel().getBlockEntity(pos);
      Inventory inventory = new Inventory(player);
      for (int slot = 0; slot < inventory.items.size(); slot++)
        inventory.setItem(slot, new ItemStack(Items.STONE, 64));
      inventory.setItem(8, new ItemStack(Items.DIAMOND, 63));
      AbstractContainerMenu menu;
      ItemStackHandler items;
      int outputSlot;
      int menuOutputSlot;
      if (entity instanceof FabricationStationBlockEntity station) {
        items = station.items();
        outputSlot = FabricationStationBlockEntity.OUTPUT_START;
        menuOutputSlot = outputSlot;
        menu = new FabricationStationMenu(0, inventory, station);
      } else if (entity instanceof SiliconProcessorBlockEntity processor) {
        items = processor.items();
        outputSlot = SiliconProcessorBlockEntity.OUTPUT_START;
        menuOutputSlot = processor.visibleInputSlots() + (processor.requiresMagma() ? 1 : 0);
        menu = new SiliconProcessorMenu(0, inventory, processor);
      } else {
        ReprocessorBlockEntity reprocessor = (ReprocessorBlockEntity) entity;
        items = reprocessor.items();
        outputSlot = ReprocessorBlockEntity.OUTPUT_START;
        menuOutputSlot = outputSlot;
        menu = new ReprocessorMenu(0, inventory, reprocessor);
      }
      items.setStackInSlot(outputSlot, new ItemStack(Items.DIAMOND, 4));
      player.containerMenu = menu;
      helper.getLevel().getChunkAt(pos).setUnsaved(false);
      menu.quickMoveStack(player, menuOutputSlot);
      helper.assertTrue(
          items.getStackInSlot(outputSlot).getCount() == 3 && inventory.getItem(8).getCount() == 64,
          "The fixture must move only one item from the output stack");
      helper.assertTrue(
          helper.getLevel().getChunkAt(pos).isUnsaved(),
          "Partially transferring process outputs must mark their block entity for saving: "
              + block);
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void resetsReprocessingProgressWhenShiftMergingInputs(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.REPROCESSOR.get());
    ReprocessorBlockEntity reprocessor =
        (ReprocessorBlockEntity) helper.getLevel().getBlockEntity(pos);
    reprocessor.items().setStackInSlot(0, new ItemStack(ModItems.SILICON_SLAG.get(), 4));
    CompoundTag saved = reprocessor.saveWithoutMetadata();
    saved.putInt("Energy", 100);
    reprocessor.load(saved);
    ReprocessorBlockEntity.serverTick(
        helper.getLevel(), pos, reprocessor.getBlockState(), reprocessor);
    helper.assertTrue(
        reprocessor.saveWithoutMetadata().getInt("Progress") == 1,
        "The fixture must begin reprocessing before modifying its inputs");
    Player player = helper.makeMockPlayer();
    Inventory inventory = new Inventory(player);
    inventory.setItem(9, new ItemStack(ModItems.SILICON_SLAG.get()));
    ReprocessorMenu menu = new ReprocessorMenu(0, inventory, reprocessor);
    player.containerMenu = menu;
    menu.quickMoveStack(player, ReprocessorBlockEntity.SLOT_COUNT);
    helper.assertTrue(
        reprocessor.items().getStackInSlot(0).getCount() == 5 && inventory.getItem(9).isEmpty(),
        "The fixture must merge new input into an existing input stack");
    helper.assertTrue(
        reprocessor.saveWithoutMetadata().getInt("Progress") == 0,
        "A shift-click input change must reset progress just like ordinary input changes");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", batch = "process_menu_layout")
  public static void closesIndustrialMenuWhenReloadAddsInputSlots(GameTestHelper helper) {
    assertIndustrialMenuLayoutReload(helper, false);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_menu_slot_swap")
  public static void closesIndustrialMenuWhenReloadSwapsInputSlots(GameTestHelper helper) {
    assertIndustrialMenuLayoutReload(helper, true);
  }

  private static void assertIndustrialMenuLayoutReload(GameTestHelper helper, boolean sameCount) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(
            Siliconic.MOD_ID, "chlorination_reactor/crude_trichlorosilane");
    List<ProcessInput> beforeInputs = new ArrayList<>();
    beforeInputs.add(new ProcessInput(0, Ingredient.of(Items.BEDROCK), 1));
    if (sameCount) beforeInputs.add(new ProcessInput(1, Ingredient.of(Items.EMERALD), 1));
    List<Recipe<?>> before = new ArrayList<>(original);
    before.removeIf(recipe -> recipe.getId().equals(id));
    before.add(
        new MachineProcess(
            id,
            MachineKind.CHLORINATION_REACTOR,
            beforeInputs,
            Items.DIAMOND,
            1,
            List.of(),
            10,
            1,
            true));
    manager.replaceRecipes(before);
    helper.runAfterDelay(
        1,
        () -> {
          try {
            BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
            helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CHLORINATION_REACTOR.get());
            SiliconProcessorBlockEntity processor =
                (SiliconProcessorBlockEntity) helper.getLevel().getBlockEntity(pos);
            int targetSlot =
                sameCount
                    ? SiliconProcessorBlockEntity.CATALYST_SLOT
                    : SiliconProcessorBlockEntity.OUTPUT_START;
            processor
                .items()
                .setStackInSlot(
                    targetSlot, new ItemStack(sameCount ? Items.EMERALD : Items.BEDROCK, 2));
            Player player = helper.makeMockPlayer();
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Inventory inventory = new Inventory(player);
            inventory.setItem(9, new ItemStack(sameCount ? Items.EMERALD : Items.BEDROCK));
            SiliconProcessorMenu menu = new SiliconProcessorMenu(0, inventory, processor);
            player.containerMenu = menu;
            helper.assertTrue(
                menu.stillValid(player), "The original industrial menu layout must be valid");
            List<ProcessInput> afterInputs = new ArrayList<>();
            afterInputs.add(new ProcessInput(0, Ingredient.of(Items.BEDROCK), 1));
            if (!sameCount) afterInputs.add(new ProcessInput(1, Ingredient.of(Items.EMERALD), 1));
            afterInputs.add(new ProcessInput(2, Ingredient.of(Items.STICK), 1));
            List<Recipe<?>> after = new ArrayList<>(original);
            after.removeIf(recipe -> recipe.getId().equals(id));
            after.add(
                new MachineProcess(
                    id,
                    MachineKind.CHLORINATION_REACTOR,
                    afterInputs,
                    Items.DIAMOND,
                    1,
                    List.of(),
                    10,
                    1,
                    true));
            manager.replaceRecipes(after);
            helper.runAfterDelay(
                1,
                () -> {
                  try {
                    boolean validAfterReload = menu.stillValid(player);
                    ItemStack moved = menu.quickMoveStack(player, menu.slots.size() - 36);
                    helper.assertTrue(
                        !validAfterReload
                            && moved.isEmpty()
                            && inventory.getItem(9).getCount() == 1
                            && processor.items().getStackInSlot(targetSlot).getCount() == 2,
                        "Reloading an input layout must close the old menu before it can insert"
                            + " into stale or output slots");
                    helper.succeed();
                  } finally {
                    manager.replaceRecipes(original);
                  }
                });
          } catch (RuntimeException | Error exception) {
            manager.replaceRecipes(original);
            throw exception;
          }
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "fabrication_work_reload")
  public static void resetsFabricationWorkAfterRecipeDefinitionChanges(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "fabrication", false);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "industrial_work_reload")
  public static void resetsIndustrialWorkAfterRecipeDefinitionChanges(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "industrial", false);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "reprocessing_work_reload")
  public static void resetsReprocessingWorkAfterRecipeDefinitionChanges(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "reprocessing", false);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "fabrication_same_recipe")
  public static void preservesFabricationWorkForIdenticalReloadedRecipes(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "fabrication", true);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "industrial_same_recipe")
  public static void preservesIndustrialWorkForIdenticalReloadedRecipes(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "industrial", true);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "reprocessing_same_recipe")
  public static void preservesReprocessingWorkForIdenticalReloadedRecipes(GameTestHelper helper) {
    assertUnfinishedWorkRecipeChange(helper, "reprocessing", true);
  }

  private static void assertUnfinishedWorkRecipeChange(
      GameTestHelper helper, String kind, boolean sameDefinition) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/work_reload_" + kind);
    List<Recipe<?>> before = new ArrayList<>(original);
    before.add(workReloadRecipe(id, kind, false));
    manager.replaceRecipes(before);
    helper.runAfterDelay(
        1,
        () -> {
          BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
          try {
            CleanroomOccupancy.update(helper.getLevel(), pos, Set.of(pos.asLong()), 100);
            BlockEntity entity = workReloadEntity(kind, pos);
            entity.setLevel(helper.getLevel());
            ItemStackHandler items = workReloadItems(entity);
            items.setStackInSlot(0, new ItemStack(Items.BEDROCK, 4));
            CompoundTag saved = entity.saveWithoutMetadata();
            saved.putInt("Energy", 1_000);
            saved.putInt("Temperature", 450);
            entity.load(saved);
            for (int tick = 0; tick < 4; tick++) tickWorkReloadEntity(helper, entity);
            helper.assertTrue(
                entity.saveWithoutMetadata().getInt("Progress") == 4,
                "The fixture must complete four of the original recipe's five ticks: " + kind);
            BlockEntity restored = workReloadEntity(kind, pos);
            restored.setLevel(helper.getLevel());
            CompoundTag unfinished = entity.saveWithoutMetadata();
            helper.assertTrue(
                unfinished.getByteArray("ProcessRecipeSignature").length == 32,
                "Unfinished work must persist a fixed-size recipe digest: " + kind);
            restored.load(unfinished);
            List<Recipe<?>> after = new ArrayList<>(original);
            after.add(workReloadRecipe(id, kind, !sameDefinition));
            manager.replaceRecipes(after);
            helper.runAfterDelay(
                1,
                () -> {
                  try {
                    tickWorkReloadEntity(helper, restored);
                    if (sameDefinition) {
                      int outputStart =
                          restored instanceof SiliconProcessorBlockEntity
                              ? SiliconProcessorBlockEntity.OUTPUT_START
                              : FabricationStationBlockEntity.OUTPUT_START;
                      helper.assertTrue(
                          workReloadItems(restored).getStackInSlot(0).isEmpty()
                              && workReloadItems(restored)
                                  .getStackInSlot(outputStart)
                                  .is(Items.DIAMOND)
                              && restored.saveWithoutMetadata().getInt("Energy") == 995,
                          "An equivalent new recipe object must preserve saved work and charge only"
                              + " its remaining tick: "
                              + kind);
                      BlockEntity legacy = workReloadEntity(kind, pos);
                      legacy.setLevel(helper.getLevel());
                      CompoundTag legacySave = unfinished.copy();
                      legacySave.remove("ProcessRecipeSignature");
                      legacy.load(legacySave);
                      tickWorkReloadEntity(helper, legacy);
                      helper.assertTrue(
                          workReloadItems(legacy).getStackInSlot(0).isEmpty()
                              && workReloadItems(legacy)
                                  .getStackInSlot(outputStart)
                                  .is(Items.DIAMOND)
                              && legacy.saveWithoutMetadata().getInt("Energy") == 995,
                          "A legacy save without a definition digest must continue its remaining"
                              + " work: "
                              + kind);
                      helper.succeed();
                      return;
                    }
                    helper.assertTrue(
                        restored.saveWithoutMetadata().getInt("Progress") == 1
                            && workReloadItems(restored).getStackInSlot(0).getCount() == 4,
                        "A changed recipe must start its own work instead of inheriting saved"
                            + " progress: "
                            + kind);
                    for (int tick = 0; tick < 4; tick++) tickWorkReloadEntity(helper, restored);
                    int outputStart =
                        restored instanceof SiliconProcessorBlockEntity
                            ? SiliconProcessorBlockEntity.OUTPUT_START
                            : FabricationStationBlockEntity.OUTPUT_START;
                    helper.assertTrue(
                        workReloadItems(restored).getStackInSlot(0).isEmpty()
                            && workReloadItems(restored)
                                .getStackInSlot(outputStart)
                                .is(Items.EMERALD)
                            && restored.saveWithoutMetadata().getInt("Energy") == 946,
                        "The changed recipe must charge all five new ticks and deliver only its own"
                            + " result: "
                            + kind);
                    helper.succeed();
                  } finally {
                    manager.replaceRecipes(original);
                    CleanroomOccupancy.remove(helper.getLevel(), pos);
                  }
                });
          } catch (RuntimeException | Error exception) {
            manager.replaceRecipes(original);
            CleanroomOccupancy.remove(helper.getLevel(), pos);
            throw exception;
          }
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "siemens_recovery_reload")
  public static void rejectsStaleSiemensRecoveryAfterRecipeReload(GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/siemens_recovery_reload");
    List<Recipe<?>> before = new ArrayList<>(original);
    before.add(siemensRecoveryRecipe(id, 4));
    manager.replaceRecipes(before);
    helper.runAfterDelay(
        1,
        () -> {
          try {
            BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
            SiliconProcessorBlockEntity processor = siemensRecoveryEntity(helper, pos);
            processor.items().setStackInSlot(0, new ItemStack(Items.BEDROCK, 4));
            CompoundTag state = processor.saveWithoutMetadata();
            state.putInt("Energy", 1_000);
            state.putInt("Temperature", 1_000);
            processor.load(state);
            SiliconProcessorBlockEntity.serverTick(
                helper.getLevel(), pos, processor.getBlockState(), processor);
            CompoundTag unfinished = processor.saveWithoutMetadata();
            helper.assertTrue(
                unfinished.getInt("Progress") == 1
                    && unfinished.getByteArray("ProcessRecipeSignature").length == 32,
                "The recovery fixture must begin and save work on the original definition");
            List<Recipe<?>> same = new ArrayList<>(original);
            same.add(siemensRecoveryRecipe(id, 4));
            manager.replaceRecipes(same);
            helper.runAfterDelay(
                1,
                () -> {
                  try {
                    SiliconProcessorBlockEntity equivalent = siemensRecoveryEntity(helper, pos);
                    equivalent.load(unfinished);
                    helper.assertTrue(
                        equivalent.handleMenuButton(1)
                            && !equivalent.handleMenuButton(1)
                            && equivalent.items().getStackInSlot(0).isEmpty()
                            && equivalent
                                .items()
                                .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                                .is(ModItems.PARTIAL_POLYSILICON_ROD.get()),
                        "Equivalent reloaded recipes must recover one partial rod exactly once");
                    List<Recipe<?>> changed = new ArrayList<>(original);
                    changed.add(siemensRecoveryRecipe(id, 1));
                    manager.replaceRecipes(changed);
                    helper.runAfterDelay(
                        1,
                        () -> {
                          try {
                            SiliconProcessorBlockEntity restored =
                                siemensRecoveryEntity(helper, pos);
                            restored.load(unfinished);
                            helper.assertTrue(
                                !restored.handleMenuButton(1)
                                    && restored.saveWithoutMetadata().getInt("Progress") == 0
                                    && restored.items().getStackInSlot(0).getCount() == 4
                                    && restored
                                        .items()
                                        .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                                        .isEmpty()
                                    && restored.saveWithoutMetadata().getInt("Energy")
                                        == unfinished.getInt("Energy"),
                                "A recovery click before the first tick must reject work from a"
                                    + " changed recipe without consuming any new inputs");
                            CompoundTag legacySave = unfinished.copy();
                            legacySave.remove("ProcessRecipeSignature");
                            SiliconProcessorBlockEntity legacy = siemensRecoveryEntity(helper, pos);
                            legacy.load(legacySave);
                            helper.assertTrue(
                                legacy.handleMenuButton(1)
                                    && legacy.items().getStackInSlot(0).getCount() == 3,
                                "Legacy progress without a signature must retain its recovery"
                                    + " behavior on the current recipe");
                            helper.succeed();
                          } finally {
                            manager.replaceRecipes(original);
                          }
                        });
                  } catch (RuntimeException | Error exception) {
                    manager.replaceRecipes(original);
                    throw exception;
                  }
                });
          } catch (RuntimeException | Error exception) {
            manager.replaceRecipes(original);
            throw exception;
          }
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "recycler_comparator_reload")
  public static void honorsRecyclerRecipeOverridesInComparatorSignals(GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(
            Siliconic.MOD_ID, "chemical_recycler/crude_trichlorosilane");
    List<Recipe<?>> recipes = new ArrayList<>(original);
    recipes.removeIf(recipe -> recipe.getId().equals(id));
    recipes.add(
        new MachineProcess(
            id,
            MachineKind.CHEMICAL_RECYCLER,
            List.of(new ProcessInput(0, Ingredient.of(ModItems.SILICON_TETRACHLORIDE.get()), 1)),
            ModItems.CRUDE_TRICHLOROSILANE.get(),
            1,
            List.of(),
            100,
            1,
            true));
    manager.replaceRecipes(recipes);
    helper.runAfterDelay(
        1,
        () -> {
          BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
          try {
            SiliconProcessorBlockEntity processor =
                new SiliconProcessorBlockEntity(
                    pos, ModBlocks.CHEMICAL_RECYCLER.get().defaultBlockState());
            processor.setLevel(helper.getLevel());
            processor
                .items()
                .setStackInSlot(0, new ItemStack(ModItems.SILICON_TETRACHLORIDE.get()));
            CompoundTag saved = processor.saveWithoutMetadata();
            saved.putInt("Energy", 1_000);
            saved.putInt("Temperature", 600);
            processor.load(saved);
            SiliconProcessorBlockEntity.serverTick(
                helper.getLevel(), pos, processor.getBlockState(), processor);
            helper.assertTrue(
                processor.saveWithoutMetadata().getInt("Progress") == 1
                    && processor.analogSignal() == 1,
                "A matching recycler override must report its actual progress instead of a"
                    + " missing reagent that it does not require");
            for (int slot = SiliconProcessorBlockEntity.OUTPUT_START;
                slot < SiliconProcessorBlockEntity.SLOT_COUNT;
                slot++) processor.items().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            helper.assertTrue(
                processor.analogSignal() == 15,
                "Output blockage must retain its higher-priority comparator signal");
            manager.replaceRecipes(original);
            helper.runAfterDelay(
                1,
                () -> {
                  try {
                    SiliconProcessorBlockEntity missingHydrogen =
                        new SiliconProcessorBlockEntity(
                            pos, ModBlocks.CHEMICAL_RECYCLER.get().defaultBlockState());
                    missingHydrogen.setLevel(helper.getLevel());
                    missingHydrogen
                        .items()
                        .setStackInSlot(0, new ItemStack(ModItems.SILICON_TETRACHLORIDE.get()));
                    helper.assertTrue(
                        missingHydrogen.analogSignal() == 10,
                        "The default recycler recipe must still report missing hydrogen");
                    missingHydrogen.items().setStackInSlot(0, new ItemStack(Items.BEDROCK));
                    helper.assertTrue(
                        missingHydrogen.analogSignal() == 5,
                        "Unmatched recycler inputs must retain their waiting signal");
                    helper.succeed();
                  } finally {
                    manager.replaceRecipes(original);
                  }
                });
          } catch (RuntimeException | Error exception) {
            manager.replaceRecipes(original);
            throw exception;
          }
        });
  }

  private static MachineProcess siemensRecoveryRecipe(ResourceLocation id, int count) {
    return new MachineProcess(
        id,
        MachineKind.SIEMENS_REACTOR,
        List.of(new ProcessInput(0, Ingredient.of(Items.BEDROCK), count)),
        Items.DIAMOND,
        1,
        List.of(),
        10,
        1,
        true);
  }

  private static SiliconProcessorBlockEntity siemensRecoveryEntity(
      GameTestHelper helper, BlockPos pos) {
    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(pos, ModBlocks.SIEMENS_REACTOR.get().defaultBlockState());
    processor.setLevel(helper.getLevel());
    return processor;
  }

  private static Recipe<?> workReloadRecipe(ResourceLocation id, String kind, boolean changed) {
    if (kind.equals("reprocessing"))
      return new ReprocessingProcess(
          id,
          Items.BEDROCK,
          4,
          List.of(new ItemStack(changed ? Items.EMERALD : Items.DIAMOND)),
          5,
          changed ? 10 : 1);
    return new MachineProcess(
        id,
        kind.equals("fabrication")
            ? MachineKind.WAFER_FABRICATOR
            : MachineKind.CHLORINATION_REACTOR,
        List.of(new ProcessInput(0, Ingredient.of(Items.BEDROCK), 4)),
        changed ? Items.EMERALD : Items.DIAMOND,
        1,
        List.of(),
        5,
        changed ? 10 : 1,
        true);
  }

  private static BlockEntity workReloadEntity(String kind, BlockPos pos) {
    return switch (kind) {
      case "fabrication" ->
          new FabricationStationBlockEntity(
              pos, ModBlocks.WAFER_FABRICATOR.get().defaultBlockState());
      case "industrial" ->
          new SiliconProcessorBlockEntity(
              pos, ModBlocks.CHLORINATION_REACTOR.get().defaultBlockState());
      default -> new ReprocessorBlockEntity(pos, ModBlocks.REPROCESSOR.get().defaultBlockState());
    };
  }

  private static ItemStackHandler workReloadItems(BlockEntity entity) {
    if (entity instanceof FabricationStationBlockEntity station) return station.items();
    if (entity instanceof SiliconProcessorBlockEntity processor) return processor.items();
    return ((ReprocessorBlockEntity) entity).items();
  }

  private static void tickWorkReloadEntity(GameTestHelper helper, BlockEntity entity) {
    if (entity instanceof FabricationStationBlockEntity station)
      FabricationStationBlockEntity.serverTick(
          helper.getLevel(), entity.getBlockPos(), entity.getBlockState(), station);
    else if (entity instanceof SiliconProcessorBlockEntity processor)
      SiliconProcessorBlockEntity.serverTick(
          helper.getLevel(), entity.getBlockPos(), entity.getBlockState(), processor);
    else
      ReprocessorBlockEntity.serverTick(
          helper.getLevel(),
          entity.getBlockPos(),
          entity.getBlockState(),
          (ReprocessorBlockEntity) entity);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsInvalidProcessEnergyRequests(GameTestHelper helper) {
    List<BlockEntity> entities =
        List.of(
            new FabricationStationBlockEntity(
                BlockPos.ZERO, ModBlocks.WAFER_FABRICATOR.get().defaultBlockState()),
            new SiliconProcessorBlockEntity(
                BlockPos.ZERO, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState()),
            new ReprocessorBlockEntity(
                BlockPos.ZERO, ModBlocks.REPROCESSOR.get().defaultBlockState()));
    for (BlockEntity entity : entities) {
      IEnergyStorage energy =
          entity
              .getCapability(ForgeCapabilities.ENERGY)
              .orElseThrow(() -> new IllegalStateException("Process energy capability missing"));
      helper.assertTrue(
          energy.receiveEnergy(500, false) == 500,
          "The fixture must receive ordinary energy before checking request bounds");
      for (boolean simulate : List.of(false, true)) {
        for (int request : new int[] {0, -1, Integer.MIN_VALUE}) {
          helper.assertTrue(
              energy.receiveEnergy(request, simulate) == 0 && energy.getEnergyStored() == 500,
              "Nonpositive energy requests must preserve process storage: "
                  + entity.getClass().getSimpleName());
        }
      }
      helper.assertTrue(
          energy.receiveEnergy(Integer.MAX_VALUE, true) == 2_000 && energy.getEnergyStored() == 500,
          "A simulated ordinary request must honor the receive limit without consuming capacity");
      helper.assertTrue(
          energy.receiveEnergy(100, false) == 100 && energy.getEnergyStored() == 600,
          "Normal requests must still update process storage");
      while (energy.getEnergyStored() < energy.getMaxEnergyStored())
        energy.receiveEnergy(Integer.MAX_VALUE, false);
      helper.assertTrue(
          energy.receiveEnergy(Integer.MAX_VALUE, false) == 0
              && energy.receiveEnergy(Integer.MAX_VALUE, true) == 0,
          "Both real and simulated requests must stop at process capacity");
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsLossyMachineProcessJsonNumbers(GameTestHelper helper) {
    MachineProcess.Serializer serializer = new MachineProcess.Serializer();
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/exact_machine_numbers");
    JsonObject base =
        JsonParser.parseString(
                """
                {"machine":"silicon_arc_furnace", "ticks":5, "energy_per_tick":1,
                 "inputs":[{"slot":0,"ingredient":{"item":"minecraft:bedrock"},"count":1}],
                 "result":{"item":"minecraft:diamond","count":1},
                 "byproducts":[{"item":"minecraft:emerald","count":1}]}
                """)
            .getAsJsonObject();
    for (String field :
        List.of(
            "ticks",
            "energy_per_tick",
            "input_slot",
            "input_count",
            "result_count",
            "byproduct_count")) {
      for (String raw : List.of("4294967297", "1.9")) {
        JsonObject json = base.deepCopy();
        setProcessNumber(json, field, JsonParser.parseString(raw));
        boolean rejected = false;
        try {
          serializer.fromJson(id, json);
        } catch (JsonParseException | IllegalArgumentException expected) {
          rejected = true;
        }
        helper.assertTrue(rejected, "Machine process JSON must reject lossy " + field + "=" + raw);
      }
    }
    JsonObject valid = base.deepCopy();
    valid.addProperty("ticks", Integer.MAX_VALUE);
    valid.addProperty("energy_per_tick", Integer.MAX_VALUE);
    valid.getAsJsonObject("result").addProperty("count", 64);
    MachineProcess process = serializer.fromJson(id, valid);
    helper.assertTrue(
        process.ticks() == Integer.MAX_VALUE
            && process.energyPerTick() == Integer.MAX_VALUE
            && process.resultCount() == 64,
        "Exact integer limits and maximum output stack sizes must remain valid");
    valid.add("ticks", JsonParser.parseString("5.0"));
    valid.add("energy_per_tick", JsonParser.parseString("1e1"));
    process = serializer.fromJson(id, valid);
    helper.assertTrue(
        process.ticks() == 5 && process.energyPerTick() == 10,
        "Equivalent integral decimal and exponent forms must preserve their exact values");
    helper.succeed();
  }

  private static void setProcessNumber(JsonObject json, String field, JsonElement value) {
    switch (field) {
      case "input_slot" ->
          json.getAsJsonArray("inputs").get(0).getAsJsonObject().add("slot", value);
      case "input_count" ->
          json.getAsJsonArray("inputs").get(0).getAsJsonObject().add("count", value);
      case "result_count" -> json.getAsJsonObject("result").add("count", value);
      case "byproduct_count" ->
          json.getAsJsonArray("byproducts").get(0).getAsJsonObject().add("count", value);
      default -> json.add(field, value);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsLossyReprocessingJsonNumbers(GameTestHelper helper) {
    ReprocessingProcess.Serializer serializer = new ReprocessingProcess.Serializer();
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/exact_reprocessing_numbers");
    JsonObject base =
        JsonParser.parseString(
                """
                {"input":{"item":"minecraft:bedrock","count":1},
                 "outputs":[{"item":"minecraft:diamond","count":1}],
                 "ticks":5,"energy_per_tick":1}
                """)
            .getAsJsonObject();
    for (String field : List.of("ticks", "energy_per_tick", "input_count", "output_count")) {
      for (String raw : List.of("4294967297", "1.9")) {
        JsonObject json = base.deepCopy();
        JsonElement value = JsonParser.parseString(raw);
        if (field.equals("input_count")) json.getAsJsonObject("input").add("count", value);
        else if (field.equals("output_count"))
          json.getAsJsonArray("outputs").get(0).getAsJsonObject().add("count", value);
        else json.add(field, value);
        boolean rejected = false;
        try {
          serializer.fromJson(id, json);
        } catch (JsonParseException | IllegalArgumentException expected) {
          rejected = true;
        }
        helper.assertTrue(rejected, "Reprocessing JSON must reject lossy " + field + "=" + raw);
      }
    }
    JsonObject valid = base.deepCopy();
    valid.addProperty("ticks", Integer.MAX_VALUE);
    valid.addProperty("energy_per_tick", Integer.MAX_VALUE);
    valid.getAsJsonObject("input").addProperty("count", Integer.MAX_VALUE);
    valid.getAsJsonArray("outputs").get(0).getAsJsonObject().addProperty("count", 64);
    ReprocessingProcess process = serializer.fromJson(id, valid);
    helper.assertTrue(
        process.ticks() == Integer.MAX_VALUE
            && process.energyPerTick() == Integer.MAX_VALUE
            && process.inputCount() == Integer.MAX_VALUE
            && process.outputs().get(0).getCount() == 64,
        "Valid reprocessing integer limits must not be narrowed");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void fallsBackFromMalformedPendingInputPlans(GameTestHelper helper) {
    CompoundTag missingItems = new CompoundTag();
    missingItems.putInt("Size", SiliconProcessorBlockEntity.INPUT_SLOTS);
    CompoundTag wrongItems = missingItems.copy();
    wrongItems.putString("Items", "broken");
    CompoundTag wrongEntryType = missingItems.copy();
    ListTag strings = new ListTag();
    strings.add(StringTag.valueOf("broken"));
    wrongEntryType.put("Items", strings);
    List<CompoundTag> malformedPlans =
        new ArrayList<>(List.of(missingItems, wrongItems, wrongEntryType));
    malformedPlans.add(inputPlanWithEntries(new CompoundTag()));
    CompoundTag missingSlot =
        new ItemStack(ModItems.CARBON_ELECTRODE.get()).save(new CompoundTag());
    malformedPlans.add(inputPlanWithEntries(missingSlot));
    for (int slot : new int[] {-1, SiliconProcessorBlockEntity.INPUT_SLOTS}) {
      CompoundTag outsideSlot = missingSlot.copy();
      outsideSlot.putInt("Slot", slot);
      malformedPlans.add(inputPlanWithEntries(outsideSlot));
    }
    CompoundTag stringSlot = missingSlot.copy();
    stringSlot.putString("Slot", "2");
    malformedPlans.add(inputPlanWithEntries(stringSlot));
    CompoundTag duplicate = missingSlot.copy();
    duplicate.putInt("Slot", 2);
    CompoundTag replacement = new ItemStack(Items.DIAMOND).save(new CompoundTag());
    replacement.putInt("Slot", 2);
    malformedPlans.add(inputPlanWithEntries(duplicate, replacement));
    CompoundTag emptyStack = duplicate.copy();
    emptyStack.putByte("Count", (byte) 0);
    malformedPlans.add(inputPlanWithEntries(emptyStack));
    CompoundTag airStack = duplicate.copy();
    airStack.putString("id", "minecraft:air");
    malformedPlans.add(inputPlanWithEntries(airStack));
    CompoundTag invalidTag = duplicate.copy();
    invalidTag.putString("tag", "broken");
    malformedPlans.add(inputPlanWithEntries(invalidTag));
    CompoundTag invalidCaps = duplicate.copy();
    invalidCaps.putString("ForgeCaps", "broken");
    malformedPlans.add(inputPlanWithEntries(invalidCaps));
    CompoundTag overstackedTool = duplicate.copy();
    overstackedTool.putByte("Count", (byte) 2);
    malformedPlans.add(inputPlanWithEntries(overstackedTool));
    CompoundTag wrongCount = duplicate.copy();
    wrongCount.putInt("Count", 1);
    malformedPlans.add(inputPlanWithEntries(wrongCount));
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    for (CompoundTag malformed : malformedPlans) {
      SiliconProcessorBlockEntity processor =
          new SiliconProcessorBlockEntity(
              pos, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState());
      processor.setLevel(helper.getLevel());
      ItemStackHandler items = new ItemStackHandler(SiliconProcessorBlockEntity.SLOT_COUNT);
      items.setStackInSlot(0, new ItemStack(Items.QUARTZ, 4));
      items.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 4));
      items.setStackInSlot(2, new ItemStack(ModItems.CARBON_ELECTRODE.get()));
      CompoundTag saved = processor.saveWithoutMetadata();
      saved.put("Items", items.serializeNBT());
      saved.put("PendingResult", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
      saved.put("PendingInputState", malformed);
      processor.load(saved);
      helper.assertTrue(
          !processor.saveWithoutMetadata().contains("PendingInputState"),
          "Malformed consumption plan structure must not remain authoritative: " + malformed);
      SiliconProcessorBlockEntity.serverTick(
          helper.getLevel(), pos, processor.getBlockState(), processor);
      helper.assertTrue(
          processor.items().getStackInSlot(0).isEmpty()
              && processor.items().getStackInSlot(1).isEmpty()
              && processor.items().getStackInSlot(2).is(ModItems.CARBON_ELECTRODE.get())
              && processor.items().getStackInSlot(2).getDamageValue() == 1
              && processor
                  .items()
                  .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                  .is(Items.DIAMOND),
          "Malformed pending plans must fall back to normal consumption without deleting reusable"
              + " tools");
    }

    SiliconProcessorBlockEntity emptyPlan =
        new SiliconProcessorBlockEntity(
            pos, ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState());
    emptyPlan.setLevel(helper.getLevel());
    emptyPlan.items().setStackInSlot(0, new ItemStack(Items.BEDROCK, 4));
    MachineProcess allConsumed =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/empty_consumption"),
            MachineKind.SILICON_ARC_FURNACE,
            List.of(new ProcessInput(0, Ingredient.of(Items.BEDROCK), 4)),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            true);
    CompoundTag saved = emptyPlan.saveWithoutMetadata();
    saved.put("PendingResult", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
    saved.put("PendingInputState", allConsumed.consumedInputState(emptyPlan.items(), 0, 3));
    emptyPlan.load(saved);
    SiliconProcessorBlockEntity.serverTick(
        helper.getLevel(), pos, emptyPlan.getBlockState(), emptyPlan);
    helper.assertTrue(
        emptyPlan.items().getStackInSlot(0).isEmpty()
            && emptyPlan
                .items()
                .getStackInSlot(SiliconProcessorBlockEntity.OUTPUT_START)
                .is(Items.DIAMOND),
        "A real empty post-consumption list must remain valid even after its recipe was removed");
    helper.succeed();
  }

  private static CompoundTag inputPlanWithEntries(CompoundTag... entries) {
    CompoundTag state = new CompoundTag();
    state.putInt("Size", SiliconProcessorBlockEntity.INPUT_SLOTS);
    ListTag items = new ListTag();
    for (CompoundTag entry : entries) items.add(entry.copy());
    state.put("Items", items);
    return state;
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsImpossibleShapelessInputsBeforeLargeGraphAllocation(
      GameTestHelper helper) {
    ProcessInput input = new ProcessInput(-1, Ingredient.of(Items.BEDROCK), 1);
    MachineProcess impossible =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/impossible_shapeless"),
            MachineKind.WAFER_FABRICATOR,
            Collections.nCopies(2_048, input),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            false);
    ItemStackHandler inventory = new ItemStackHandler(FabricationStationBlockEntity.INPUT_SLOTS);
    java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
    long allocated = -1;
    if (base instanceof com.sun.management.ThreadMXBean meter
        && meter.isThreadAllocatedMemorySupported()
        && meter.isThreadAllocatedMemoryEnabled()) {
      long before = meter.getThreadAllocatedBytes(Thread.currentThread().getId());
      helper.assertTrue(
          !impossible.matches(inventory, 0, FabricationStationBlockEntity.INPUT_SLOTS),
          "An empty inventory cannot satisfy thousands of separate shapeless inputs");
      allocated = meter.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
    } else {
      helper.assertTrue(
          !impossible.matches(inventory, 0, FabricationStationBlockEntity.INPUT_SLOTS),
          "An empty inventory cannot satisfy thousands of separate shapeless inputs");
    }
    helper.assertTrue(
        allocated < 1_000_000,
        "Impossible supply must be rejected before allocating a quadratic matching graph;"
            + " allocated="
            + allocated);
    MachineProcess split =
        new MachineProcess(
            ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "test/valid_split_shapeless"),
            MachineKind.WAFER_FABRICATOR,
            Collections.nCopies(12, input),
            Items.DIAMOND,
            1,
            List.of(),
            1,
            1,
            false);
    inventory.setStackInSlot(0, new ItemStack(Items.BEDROCK, 12));
    helper.assertTrue(
        split.matches(inventory, 0, FabricationStationBlockEntity.INPUT_SLOTS),
        "Twelve separate inputs must remain legal when one physical stack can supply them");
    split.consume(inventory, 0, FabricationStationBlockEntity.INPUT_SLOTS);
    helper.assertTrue(
        inventory.getStackInSlot(0).isEmpty(),
        "A legal split-input recipe must still consume every assigned item once");
    helper.succeed();
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_integer_limits")
  public static void preservesProcessMaterialsAtMaximumIntegerLimits(GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    List<Recipe<?>> recipes = new ArrayList<>(original);
    for (String kind : List.of("fabrication", "industrial", "reprocessing")) {
      recipes.add(integerBoundaryRecipe(kind, false));
      recipes.add(integerBoundaryRecipe(kind, true));
    }
    manager.replaceRecipes(recipes);
    helper.runAfterDelay(
        1,
        () -> {
          BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
          try {
            CleanroomOccupancy.update(helper.getLevel(), pos, Set.of(pos.asLong()), 100);
            for (String kind : List.of("fabrication", "industrial", "reprocessing")) {
              BlockEntity finishing = workReloadEntity(kind, pos);
              finishing.setLevel(helper.getLevel());
              workReloadItems(finishing).setStackInSlot(0, new ItemStack(Items.BEDROCK, 4));
              CompoundTag saved = finishing.saveWithoutMetadata();
              saved.putInt("Progress", Integer.MAX_VALUE);
              saved.putInt("Energy", 5);
              saved.putInt("Temperature", 450);
              finishing.load(saved);
              helper.assertTrue(
                  finishing.saveWithoutMetadata().getInt("Progress") == Integer.MAX_VALUE - 1,
                  "Maximum loaded progress must leave room for its final increment: " + kind);
              tickWorkReloadEntity(helper, finishing);
              tickWorkReloadEntity(helper, finishing);
              int outputStart =
                  finishing instanceof SiliconProcessorBlockEntity
                      ? SiliconProcessorBlockEntity.OUTPUT_START
                      : FabricationStationBlockEntity.OUTPUT_START;
              helper.assertTrue(
                  workReloadItems(finishing).getStackInSlot(0).isEmpty()
                      && workReloadItems(finishing).getStackInSlot(outputStart).is(Items.DIAMOND)
                      && workReloadItems(finishing).getStackInSlot(outputStart).getCount() == 1
                      && finishing.saveWithoutMetadata().getInt("Energy") == 4,
                  "A maximum-duration batch must consume and finish once without wrapping: "
                      + kind);

              BlockEntity blocked = workReloadEntity(kind, pos);
              blocked.setLevel(helper.getLevel());
              workReloadItems(blocked).setStackInSlot(0, new ItemStack(Items.BARRIER, 4));
              saved = blocked.saveWithoutMetadata();
              saved.putInt("Energy", Integer.MAX_VALUE);
              saved.putInt("Temperature", 450);
              blocked.load(saved);
              int capacity =
                  blocked
                      .getCapability(ForgeCapabilities.ENERGY)
                      .orElseThrow(
                          () -> new IllegalStateException("Process energy capability missing"))
                      .getMaxEnergyStored();
              for (int tick = 0; tick < 4; tick++) tickWorkReloadEntity(helper, blocked);
              helper.assertTrue(
                  workReloadItems(blocked).getStackInSlot(0).getCount() == 4
                      && workReloadItems(blocked).getStackInSlot(outputStart).isEmpty()
                      && blocked.saveWithoutMetadata().getInt("Progress") == 0
                      && blocked.saveWithoutMetadata().getInt("Energy") == capacity,
                  "An unaffordable maximum-energy tick must preserve capacity, inputs, and"
                      + " progress: "
                      + kind);
            }
            helper.succeed();
          } finally {
            manager.replaceRecipes(original);
            CleanroomOccupancy.remove(helper.getLevel(), pos);
          }
        });
  }

  private static Recipe<?> integerBoundaryRecipe(String kind, boolean energyLimit) {
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(
            Siliconic.MOD_ID,
            "test/integer_boundary_" + kind + (energyLimit ? "_energy" : "_duration"));
    int ticks = energyLimit ? 1 : Integer.MAX_VALUE;
    int energy = energyLimit ? Integer.MAX_VALUE : 1;
    if (kind.equals("reprocessing"))
      return new ReprocessingProcess(
          id,
          energyLimit ? Items.BARRIER : Items.BEDROCK,
          4,
          List.of(new ItemStack(Items.DIAMOND)),
          ticks,
          energy);
    return new MachineProcess(
        id,
        kind.equals("fabrication")
            ? MachineKind.WAFER_FABRICATOR
            : MachineKind.CHLORINATION_REACTOR,
        List.of(new ProcessInput(0, Ingredient.of(energyLimit ? Items.BARRIER : Items.BEDROCK), 4)),
        Items.DIAMOND,
        1,
        List.of(),
        ticks,
        energy,
        true);
  }

  private ProcessStabilityGameTests() {}
}
