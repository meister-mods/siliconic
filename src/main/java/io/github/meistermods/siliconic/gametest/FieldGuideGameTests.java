package io.github.meistermods.siliconic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Exercises the real advancement predicate, loot reward, and replacement crafting recipe. */
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class FieldGuideGameTests {
  private static final ResourceLocation GUIDE =
      ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "field_guide");
  private static final ResourceLocation BOOK_ITEM =
      ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void grantsFieldGuideOnceInSurvival(GameTestHelper helper) {
    GuideReader player = reader(helper);
    try {
      player.setGameMode(GameType.SURVIVAL);
      Advancement advancement = advancement(helper);
      helper.assertTrue(advancement.getDisplay() == null, "The grant must be a hidden advancement");
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(countGuides(player) == 1, "First survival tick must grant one field guide");
      helper.assertTrue(
          player.getAdvancements().getOrStartProgress(advancement).isDone(),
          "The grant must record completion per player");
      for (int tick = 0; tick < 5; tick++) CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(countGuides(player) == 1, "Later ticks must not duplicate the guide");
      player.getInventory().clearContent();
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(
          countGuides(player) == 0, "Losing a guide must not reset its one-time grant");
      helper.succeed();
    } finally {
      player.getAdvancements().stopListening();
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void withholdsFieldGuideOutsideSurvival(GameTestHelper helper) {
    for (GameType mode : List.of(GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR)) {
      GuideReader player = reader(helper);
      try {
        player.setGameMode(mode);
        CriteriaTriggers.TICK.trigger(player);
        helper.assertTrue(countGuides(player) == 0, "Must not grant a guide in " + mode);
        helper.assertTrue(
            !player.getAdvancements().getOrStartProgress(advancement(helper)).isDone(),
            "An ineligible mode must not consume the future survival grant");
        player.setGameMode(GameType.SURVIVAL);
        CriteriaTriggers.TICK.trigger(player);
        helper.assertTrue(
            countGuides(player) == 1, "The first eligible survival tick must grant the guide");
      } finally {
        player.getAdvancements().stopListening();
      }
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void deliversFieldGuideWithFullInventory(GameTestHelper helper) {
    GuideReader player = reader(helper);
    try {
      player.setGameMode(GameType.SURVIVAL);
      for (int slot = 0; slot < player.getInventory().items.size(); slot++)
        player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(
          countGuides(player) == 0
              && player.dropped.stream().filter(FieldGuideGameTests::isGuide).count() == 1,
          "A full inventory must receive its guide as a dropped item");
      helper.assertTrue(
          player.getAdvancements().getOrStartProgress(advancement(helper)).isDone(),
          "Dropping the reward must still complete the one-time grant");
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(
          player.dropped.stream().filter(FieldGuideGameTests::isGuide).count() == 1,
          "A full inventory must not cause repeated drops");
      helper.succeed();
    } finally {
      player.getAdvancements().stopListening();
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void craftsReplacementFieldGuide(GameTestHelper helper) {
    GuideReader player = reader(helper);
    try {
      TransientCraftingContainer grid = new TransientCraftingContainer(player.inventoryMenu, 2, 2);
      grid.setItem(0, new ItemStack(Items.BOOK));
      grid.setItem(3, new ItemStack(ModItems.COPPER_NUGGET.get()));
      var recipe =
          helper
              .getLevel()
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
      helper.assertTrue(
          recipe.isPresent() && recipe.get().getId().equals(GUIDE),
          "A book and copper nugget must match the field guide recipe without a fixed layout");
      ItemStack result = recipe.orElseThrow().assemble(grid, helper.getLevel().registryAccess());
      helper.assertTrue(
          isGuide(result) && result.getCount() == 1, "Crafting must produce this guide");
      helper.succeed();
    } finally {
      player.getAdvancements().stopListening();
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesFieldGuideGrantAfterProgressReload(GameTestHelper helper) {
    GuideReader player = reader(helper);
    try {
      player.setGameMode(GameType.SURVIVAL);
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(countGuides(player) == 1, "The initial grant must succeed");
      player.getAdvancements().save();
      player.getInventory().clearContent();
      player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
      helper.assertTrue(
          player.getAdvancements().getOrStartProgress(advancement(helper)).isDone(),
          "The completed grant must survive saving and reloading player progress");
      CriteriaTriggers.TICK.trigger(player);
      helper.assertTrue(
          countGuides(player) == 0, "Reloading completed progress must not grant another guide");
      helper.succeed();
    } finally {
      player.getAdvancements().stopListening();
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void unlocksReplacementRecipeWithBothMaterials(GameTestHelper helper) {
    var limitedCrafting = helper.getLevel().getGameRules().getRule(GameRules.RULE_LIMITED_CRAFTING);
    boolean previousRule = limitedCrafting.get();
    try {
      limitedCrafting.set(true, helper.getLevel().getServer());
      var recipe = helper.getLevel().getRecipeManager().byKey(GUIDE).orElseThrow();
      for (boolean bookFirst : List.of(true, false)) {
        GuideReader player = reader(helper);
        try {
          helper.assertTrue(
              !new ResultContainer().setRecipeUsed(helper.getLevel(), player, recipe),
              "Limited crafting must require unlocking the replacement recipe");
          ItemStack book = new ItemStack(Items.BOOK);
          ItemStack nugget = new ItemStack(ModItems.COPPER_NUGGET.get());
          ItemStack first = bookFirst ? book : nugget;
          ItemStack second = bookFirst ? nugget : book;
          player.getInventory().setItem(0, first);
          CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), first);
          helper.assertTrue(
              !player.getRecipeBook().contains(GUIDE),
              "One replacement ingredient alone must not unlock the recipe");
          player.getInventory().setItem(8, second);
          CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), second);
          helper.assertTrue(
              player.getRecipeBook().contains(GUIDE),
              "Both ingredients must unlock the recipe regardless of pickup order");
          helper.assertTrue(
              new ResultContainer().setRecipeUsed(helper.getLevel(), player, recipe),
              "The unlocked replacement recipe must work with limited crafting enabled");
        } finally {
          player.getAdvancements().stopListening();
        }
      }
      helper.succeed();
    } finally {
      limitedCrafting.set(previousRule, helper.getLevel().getServer());
    }
  }

  private static Advancement advancement(GameTestHelper helper) {
    Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(GUIDE);
    helper.assertTrue(advancement != null, "Field guide advancement must load from data");
    return advancement;
  }

  private static GuideReader reader(GameTestHelper helper) {
    GuideReader player = new GuideReader(helper);
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    return player;
  }

  private static int countGuides(ServerPlayer player) {
    return player.getInventory().items.stream()
        .filter(FieldGuideGameTests::isGuide)
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  private static boolean isGuide(ItemStack stack) {
    return BOOK_ITEM.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
        && stack.hasTag()
        && GUIDE.toString().equals(stack.getTag().getString("patchouli:book"));
  }

  // Forge intentionally prevents FakePlayer from earning advancements. Exercise the real player
  // path while discarding outbound packets instead of opening a network connection.
  private static final class GuideReader extends ServerPlayer {
    private final List<ItemStack> dropped = new ArrayList<>();

    private GuideReader(GameTestHelper helper) {
      super(
          helper.getLevel().getServer(),
          helper.getLevel(),
          new GameProfile(UUID.randomUUID(), "guide_reader"));
      connection =
          new ServerGamePacketListenerImpl(
              helper.getLevel().getServer(), new Connection(PacketFlow.CLIENTBOUND), this) {
            @Override
            public void send(Packet<?> packet) {}

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
          };
    }

    @Override
    @Nullable
    public ItemEntity drop(ItemStack stack, boolean random) {
      dropped.add(stack.copy());
      return super.drop(stack, random);
    }
  }

  private FieldGuideGameTests() {}
}
