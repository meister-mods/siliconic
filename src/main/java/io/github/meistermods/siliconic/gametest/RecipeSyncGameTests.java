package io.github.meistermods.siliconic.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.client.ClientRecipeEvents;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ModMachineProcesses;
import io.github.meistermods.siliconic.recipe.ProcessInput;
import io.github.meistermods.siliconic.recipe.ProcessRecipeState;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.reprocessing.ReprocessingProcess;
import io.github.meistermods.siliconic.silicon.SiliconProcessorBlockEntity;
import io.github.meistermods.siliconic.silicon.SiliconProcessorMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/** Covers recipe and tag synchronization without retaining stale process definitions. */
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSyncGameTests {
  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", batch = "process_cache_sync")
  public static void refreshesRecipeViewsAndNewMenuLayoutsWithinTheSyncTick(GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    ResourceLocation machineId =
        ResourceLocation.fromNamespaceAndPath(
            Siliconic.MOD_ID, "chlorination_reactor/crude_trichlorosilane");
    ResourceLocation reprocessingId =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "reprocessor/silicon_slag");
    MachineProcess beforeMachine = machine(machineId, false);
    ReprocessingProcess beforeRecovery = recovery(reprocessingId, false);
    List<Recipe<?>> before =
        replaced(original, machineId, reprocessingId, beforeMachine, beforeRecovery);
    manager.replaceRecipes(before);
    helper.runAfterDelay(
        1,
        () -> {
          try {
            long gameTime = helper.getLevel().getGameTime();
            helper.assertTrue(
                ModMachineProcesses.all(helper.getLevel()).stream()
                        .anyMatch(recipe -> recipe == beforeMachine)
                    && ReprocessingProcess.all(helper.getLevel()).stream()
                        .anyMatch(recipe -> recipe == beforeRecovery),
                "Both per-tick caches must be populated with the original synchronized objects");
            MachineProcess afterMachine = machine(machineId, true);
            ReprocessingProcess afterRecovery = recovery(reprocessingId, true);
            manager.replaceRecipes(
                replaced(original, machineId, reprocessingId, afterMachine, afterRecovery));
            // Invoke the client subscriber's common-only body directly on this dedicated server
            // after replacing the same manager, just as a synchronized recipe packet does.
            ClientRecipeEvents.recipesUpdated(new RecipesUpdatedEvent(manager));
            boolean machineFresh =
                ModMachineProcesses.all(helper.getLevel()).stream()
                    .anyMatch(recipe -> recipe == afterMachine);
            boolean recoveryFresh =
                ReprocessingProcess.all(helper.getLevel()).stream()
                    .anyMatch(recipe -> recipe == afterRecovery);
            SiliconProcessorBlockEntity processor =
                new SiliconProcessorBlockEntity(
                    helper.absolutePos(GameTestFixtures.ORIGIN),
                    ModBlocks.CHLORINATION_REACTOR.get().defaultBlockState());
            processor.setLevel(helper.getLevel());
            SiliconProcessorMenu menu =
                new SiliconProcessorMenu(0, new Inventory(helper.makeMockPlayer()), processor);
            System.out.println(
                "PROCESS_CACHE same_manager="
                    + (manager == helper.getLevel().getRecipeManager())
                    + " same_tick="
                    + (gameTime == helper.getLevel().getGameTime())
                    + " machine_fresh="
                    + machineFresh
                    + " recovery_fresh="
                    + recoveryFresh
                    + " new_menu_inputs="
                    + menu.visibleInputSlots());
            helper.assertTrue(
                gameTime == helper.getLevel().getGameTime()
                    && machineFresh
                    && recoveryFresh
                    && menu.visibleInputSlots() == 3,
                "A recipe sync must refresh both cached definitions and new menu input layouts"
                    + " before the next world tick");
            helper.succeed();
          } finally {
            manager.replaceRecipes(original);
            ClientRecipeEvents.recipesUpdated(new RecipesUpdatedEvent(manager));
          }
        });
  }

  private static List<Recipe<?>> replaced(
      List<Recipe<?>> original,
      ResourceLocation machineId,
      ResourceLocation recoveryId,
      MachineProcess machine,
      ReprocessingProcess recovery) {
    List<Recipe<?>> recipes = new ArrayList<>(original);
    recipes.removeIf(
        recipe -> recipe.getId().equals(machineId) || recipe.getId().equals(recoveryId));
    recipes.add(machine);
    recipes.add(recovery);
    return recipes;
  }

  private static MachineProcess machine(ResourceLocation id, boolean changed) {
    List<ProcessInput> inputs = new ArrayList<>();
    inputs.add(new ProcessInput(0, Ingredient.of(Items.BEDROCK), 1));
    if (changed) {
      inputs.add(new ProcessInput(1, Ingredient.of(Items.EMERALD), 1));
      inputs.add(new ProcessInput(2, Ingredient.of(Items.STICK), 1));
    }
    return new MachineProcess(
        id,
        MachineKind.CHLORINATION_REACTOR,
        inputs,
        changed ? Items.EMERALD : Items.DIAMOND,
        1,
        List.of(),
        changed ? 20 : 10,
        changed ? 2 : 1,
        true);
  }

  private static ReprocessingProcess recovery(ResourceLocation id, boolean changed) {
    return new ReprocessingProcess(
        id,
        Items.BEDROCK,
        changed ? 2 : 1,
        List.of(new ItemStack(changed ? Items.EMERALD : Items.DIAMOND)),
        changed ? 20 : 10,
        changed ? 2 : 1);
  }

  private static final TagKey<Item> COPPER =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "nuggets/copper"));
  private static final ResourceLocation NOT_GATE =
      ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "gate_fabricator/not_gate");

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_tag_matching")
  public static void refreshesActualFallbackIngredientsAfterTagReload(GameTestHelper helper) {
    Map<TagKey<Item>, List<Holder<Item>>> original = bindings();
    helper.assertTrue(
        original.containsKey(COPPER) && !original.get(COPPER).isEmpty(),
        "The shipped copper tag must exist");
    try {
      MachineProcess before = fallback();
      ItemStackHandler oldInputs = inputCopies(before);
      helper.assertTrue(
          before.matches(oldInputs, 0, 9),
          "The actual NOT gate fallback must initially match its shipped ingredients");
      helper.assertTrue(
          ModMachineProcesses.all(helper.getLevel()).stream().anyMatch(recipe -> recipe == before),
          "The level cache must contain the actual fallback before tags change");
      Map<TagKey<Item>, List<Holder<Item>>> changed = new LinkedHashMap<>(original);
      changed.put(COPPER, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.BEDROCK)));
      bindAndNotify(helper, changed);
      MachineProcess after = fallback();
      ItemStackHandler newInputs = new ItemStackHandler(9);
      newInputs.deserializeNBT(oldInputs.serializeNBT());
      for (int slot : new int[] {3, 5, 7})
        newInputs.setStackInSlot(slot, new ItemStack(Items.BEDROCK));
      helper.assertTrue(
          ModMachineProcesses.all(helper.getLevel()).stream().anyMatch(recipe -> recipe == after),
          "The same-tick level cache must reflect newly bound fallback ingredients");
      boolean acceptsOld = after.matches(oldInputs, 0, 9);
      boolean acceptsNew = after.matches(newInputs, 0, 9);
      System.out.println(
          "PROCESS_TAG_MATCH actual_fallback=true same_recipe="
              + (before == after)
              + " accepts_removed="
              + acceptsOld
              + " accepts_added="
              + acceptsNew);
      helper.assertTrue(
          !acceptsOld && acceptsNew,
          "Static fallback recipes must stop accepting removed tag materials and accept newly bound"
              + " materials");
      helper.succeed();
    } finally {
      bindAndNotify(helper, original);
    }
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_tag_signatures")
  public static void savesCurrentFallbackSignaturesAfterTagReload(GameTestHelper helper) {
    Map<TagKey<Item>, List<Holder<Item>>> original = bindings();
    helper.assertTrue(original.containsKey(COPPER), "The shipped copper tag must exist");
    try {
      MachineProcess before = fallback();
      ProcessRecipeState initial = new ProcessRecipeState();
      helper.assertTrue(initial.select(before), "Initial fallback work must adopt its definition");
      CompoundTag initialSave = new CompoundTag();
      initial.save(initialSave, true);
      bindAndNotify(helper, original);
      helper.assertTrue(
          initial.select(fallback()),
          "Rebinding identical tags must preserve unfinished work on the live machine");
      List<Holder<Item>> expanded = new ArrayList<>(original.get(COPPER));
      expanded.add(BuiltInRegistries.ITEM.wrapAsHolder(Items.BEDROCK));
      Map<TagKey<Item>, List<Holder<Item>>> changed = new LinkedHashMap<>(original);
      changed.put(COPPER, expanded);
      bindAndNotify(helper, changed);
      MachineProcess current = fallback();
      ProcessRecipeState newMachine = new ProcessRecipeState();
      helper.assertTrue(
          newMachine.select(current), "New work must adopt the currently loaded definition");
      CompoundTag saved = new CompoundTag();
      newMachine.save(saved, true);
      MachineProcess equivalentFreshObject = freshIngredients(current);
      ProcessRecipeState restored = new ProcessRecipeState();
      restored.load(saved);
      boolean equivalent = restored.select(equivalentFreshObject);
      System.out.println(
          "PROCESS_TAG_SIGNATURE saved_matches_current_fresh_definition="
              + equivalent
              + " same_static_recipe="
              + (before == current));
      helper.assertTrue(
          equivalent,
          "A fallback signature saved after a tag update must match an equivalent fresh"
              + " definition");
      helper.assertTrue(
          !initial.select(current),
          "A live machine must reset unfinished work when its fallback tag definition changes");
      bindAndNotify(helper, original);
      ProcessRecipeState unchangedReload = new ProcessRecipeState();
      unchangedReload.load(initialSave);
      helper.assertTrue(
          unchangedReload.select(fallback()),
          "Restoring identical tag contents must preserve the original work signature");
      helper.succeed();
    } finally {
      bindAndNotify(helper, original);
    }
  }

  private static Map<TagKey<Item>, List<Holder<Item>>> bindings() {
    Map<TagKey<Item>, List<Holder<Item>>> snapshot = new LinkedHashMap<>();
    BuiltInRegistries.ITEM
        .getTags()
        .forEach(pair -> snapshot.put(pair.getFirst(), pair.getSecond().stream().toList()));
    return snapshot;
  }

  private static void bindAndNotify(
      GameTestHelper helper, Map<TagKey<Item>, List<Holder<Item>>> tags) {
    BuiltInRegistries.ITEM.bindTags(tags);
    // Mirrors ReloadableServerResources.updateRegistryTags for the affected item registry.
    // Ingredient.invalidateAll is not part of that vanilla/Forge tag-update method.
    MinecraftForge.EVENT_BUS.post(
        new TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
  }

  private static MachineProcess fallback() {
    return ModMachineProcesses.all().stream()
        .filter(recipe -> recipe.id().equals(NOT_GATE))
        .findFirst()
        .orElseThrow();
  }

  private static ItemStackHandler inputCopies(MachineProcess process) {
    ItemStackHandler inputs = new ItemStackHandler(9);
    for (ProcessInput input : process.inputs()) {
      ItemStack stack = input.ingredient().getItems()[0].copy();
      stack.setCount(input.requiredItems());
      inputs.setStackInSlot(input.slot(), stack);
    }
    return inputs;
  }

  private static MachineProcess freshIngredients(MachineProcess process) {
    List<ProcessInput> inputs =
        process.inputs().stream()
            .map(
                input ->
                    new ProcessInput(
                        input.slot(),
                        Ingredient.fromJson(input.ingredient().toJson()),
                        input.count(),
                        input.use()))
            .toList();
    return new MachineProcess(
        process.id(),
        process.machine(),
        inputs,
        process.resultItem(),
        process.resultCount(),
        process.byproducts(),
        process.ticks(),
        process.energyPerTick(),
        process.shaped());
  }

  private static final List<MachineKind> INDUSTRIAL_KINDS =
      List.of(
          MachineKind.SILICON_ARC_FURNACE,
          MachineKind.CHLORINATION_REACTOR,
          MachineKind.DISTILLATION_TOWER,
          MachineKind.SIEMENS_REACTOR,
          MachineKind.CHEMICAL_RECYCLER);

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_missing_kind_idle")
  public static void readsIndustrialMenusWithoutRemainingProcessDefinitions(GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    try {
      moveIndustrialRecipes(manager, original);
      for (MachineKind kind : INDUSTRIAL_KINDS) {
        helper.assertTrue(
            ModMachineProcesses.forMachine(helper.getLevel(), kind).isEmpty(),
            "A valid override may move every original process to a different machine kind");
        SiliconProcessorBlockEntity processor = processor(helper, kind);
        CompoundTag saved = processor.saveWithoutMetadata();
        saved.putInt("Progress", 42);
        saved.putInt("Energy", 12345);
        processor.load(saved);
        assertMenu(helper, processor);
        tick(helper, processor);
        helper.assertTrue(
            processor.saveWithoutMetadata().getInt("Progress") == 0
                && processor.saveWithoutMetadata().getInt("Energy") == 12345,
            "Undefined machines must reset stale work without consuming energy: " + kind);
        assertMenu(helper, processor);
      }
      System.out.println("PROCESS_MISSING_KIND idle_cases=5 menu_fields=18 preserved_energy=true");
      helper.succeed();
    } finally {
      manager.replaceRecipes(original);
      ModMachineProcesses.invalidateCache(manager);
    }
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "process_missing_kind_pending")
  public static void deliversCompletedPlansWithoutRemainingProcessDefinitions(
      GameTestHelper helper) {
    RecipeManager manager = helper.getLevel().getRecipeManager();
    List<Recipe<?>> original = List.copyOf(manager.getRecipes());
    List<SiliconProcessorBlockEntity> processors = new ArrayList<>();
    List<CompoundTag> plans = new ArrayList<>();
    List<List<ItemStack>> outputs = new ArrayList<>();
    for (MachineKind kind : INDUSTRIAL_KINDS) {
      MachineProcess recipe = ModMachineProcesses.primary(kind);
      SiliconProcessorBlockEntity processor = processor(helper, kind);
      for (ProcessInput input : recipe.inputs())
        processor.items().setStackInSlot(input.slot(), input.stacks().get(0));
      CompoundTag plan = recipe.consumedInputState(processor.items(), 0, 3);
      CompoundTag saved = processor.saveWithoutMetadata();
      saved.putInt("Energy", 12345);
      saved.put("PendingInputState", plan.copy());
      ListTag pending = new ListTag();
      recipe.outputCopies().forEach(stack -> pending.add(stack.save(new CompoundTag())));
      saved.put("PendingResults", pending);
      processor.load(saved);
      processors.add(processor);
      plans.add(plan);
      outputs.add(recipe.outputCopies());
    }
    try {
      moveIndustrialRecipes(manager, original);
      for (int index = 0; index < processors.size(); index++) {
        SiliconProcessorBlockEntity processor = processors.get(index);
        assertMenu(helper, processor);
        tick(helper, processor);
        tick(helper, processor);
        ItemStackHandler expectedInputs = new ItemStackHandler(3);
        expectedInputs.deserializeNBT(plans.get(index));
        for (int slot = 0; slot < 3; slot++)
          helper.assertTrue(
              ItemStack.matches(
                  expectedInputs.getStackInSlot(slot), processor.items().getStackInSlot(slot)),
              "Completed work must apply its original cost and tool wear once");
        int expectedTotal = 0;
        for (ItemStack output : outputs.get(index)) {
          expectedTotal += output.getCount();
          int count = 0;
          for (int slot = 4; slot < 13; slot++) {
            ItemStack actual = processor.items().getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(actual, output)) count += actual.getCount();
          }
          helper.assertTrue(
              count == output.getCount(),
              "Original pending products must be delivered exactly once");
        }
        int actualTotal = 0;
        for (int slot = 4; slot < 13; slot++)
          actualTotal += processor.items().getStackInSlot(slot).getCount();
        helper.assertTrue(
            actualTotal == expectedTotal
                && processor.saveWithoutMetadata().getInt("Energy") == 12345,
            "No extra products or energy charge may follow loss of every process definition");
      }
      System.out.println(
          "PROCESS_MISSING_KIND completed_cases=5 original_plan=preserved exactly_once=true");
      helper.succeed();
    } finally {
      manager.replaceRecipes(original);
      ModMachineProcesses.invalidateCache(manager);
    }
  }

  private static void assertMenu(GameTestHelper helper, SiliconProcessorBlockEntity processor) {
    ContainerData data = processor.data();
    for (int field = 0; field < data.getCount(); field++) data.get(field);
    for (int field = 4; field <= 7; field++)
      helper.assertTrue(
          data.get(field) == 0,
          "A machine without a process must display no recipe duration or energy requirement");
    helper.assertTrue(data.get(17) == 0, "A machine without a process must display an idle phase");
  }

  private static void moveIndustrialRecipes(RecipeManager manager, List<Recipe<?>> original) {
    List<Recipe<?>> replacements = new ArrayList<>(original);
    for (MachineProcess existing : ModMachineProcesses.all()) {
      if (!INDUSTRIAL_KINDS.contains(existing.machine())) continue;
      replacements.removeIf(recipe -> recipe.getId().equals(existing.id()));
      JsonObject json =
          JsonParser.parseString(
                  """
                  {"machine":"wafer_fabricator","ticks":17,"energy_per_tick":5,
                   "inputs":[{"slot":0,"ingredient":{"item":"minecraft:bedrock"}}],
                   "result":{"item":"minecraft:diamond"}}
                  """)
              .getAsJsonObject();
      replacements.add(new MachineProcess.Serializer().fromJson(existing.id(), json));
    }
    manager.replaceRecipes(replacements);
    ModMachineProcesses.invalidateCache(manager);
  }

  private static SiliconProcessorBlockEntity processor(GameTestHelper helper, MachineKind kind) {
    BlockState state =
        switch (kind) {
          case SILICON_ARC_FURNACE -> ModBlocks.SILICON_ARC_FURNACE.get().defaultBlockState();
          case CHLORINATION_REACTOR -> ModBlocks.CHLORINATION_REACTOR.get().defaultBlockState();
          case DISTILLATION_TOWER -> ModBlocks.DISTILLATION_TOWER.get().defaultBlockState();
          case SIEMENS_REACTOR -> ModBlocks.SIEMENS_REACTOR.get().defaultBlockState();
          case CHEMICAL_RECYCLER -> ModBlocks.CHEMICAL_RECYCLER.get().defaultBlockState();
          default -> throw new IllegalArgumentException("Not an industrial kind: " + kind);
        };
    SiliconProcessorBlockEntity processor =
        new SiliconProcessorBlockEntity(helper.absolutePos(GameTestFixtures.ORIGIN), state);
    processor.setLevel(helper.getLevel());
    return processor;
  }

  private static void tick(GameTestHelper helper, SiliconProcessorBlockEntity processor) {
    SiliconProcessorBlockEntity.serverTick(
        helper.getLevel(), processor.getBlockPos(), processor.getBlockState(), processor);
  }

  private RecipeSyncGameTests() {}
}
