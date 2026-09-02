package io.github.meistermods.siliconic.reprocessing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.registry.ModRecipes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** Defines deterministic material recovery from process waste and finished components. */
@SuppressWarnings({"null"})
public record ReprocessingProcess(
    ResourceLocation id,
    Item input,
    int inputCount,
    List<ItemStack> outputs,
    int ticks,
    int energyPerTick)
    implements Recipe<Container> {
  private static final Map<Level, TickCache> LEVEL_CACHE = new WeakHashMap<>();

  private record TickCache(long gameTime, List<ReprocessingProcess> processes) {}

  public ReprocessingProcess {
    if (inputCount < 1) throw new IllegalArgumentException("Input count must be positive");
    if (outputs.isEmpty()) throw new IllegalArgumentException("At least one output is required");
    outputs = outputs.stream().map(ItemStack::copy).toList();
    if (ticks < 1) throw new IllegalArgumentException("Process duration must be positive");
    if (energyPerTick < 1) throw new IllegalArgumentException("Energy use must be positive");
  }

  @Nullable
  public static ReprocessingProcess find(
      ItemStackHandler inventory, int inputStart, int inputSlots) {
    return find(null, inventory, inputStart, inputSlots);
  }

  @Nullable
  public static ReprocessingProcess find(
      @Nullable Level level, ItemStackHandler inventory, int inputStart, int inputSlots) {
    for (ReprocessingProcess process : all(level)) {
      int available = 0;
      for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (process.matches(stack)) available += stack.getCount();
      }
      if (available >= process.inputCount()) return process;
    }
    return null;
  }

  public static List<ReprocessingProcess> all() {
    return Holder.PROCESSES;
  }

  public static List<ReprocessingProcess> all(@Nullable Level level) {
    if (level == null) return Holder.PROCESSES;
    long gameTime = level.getGameTime();
    synchronized (LEVEL_CACHE) {
      TickCache cached = LEVEL_CACHE.get(level);
      if (cached != null && cached.gameTime() == gameTime) return cached.processes();
    }
    Map<ResourceLocation, ReprocessingProcess> merged = new LinkedHashMap<>();
    Holder.PROCESSES.forEach(process -> merged.put(process.id(), process));
    level
        .getRecipeManager()
        .getAllRecipesFor(ModRecipes.REPROCESSING_TYPE.get())
        .forEach(process -> merged.put(process.id(), process));
    List<ReprocessingProcess> processes = List.copyOf(merged.values());
    synchronized (LEVEL_CACHE) {
      LEVEL_CACHE.put(level, new TickCache(gameTime, processes));
    }
    return processes;
  }

  public static boolean accepts(ItemStack stack) {
    return accepts(null, stack);
  }

  public static boolean accepts(@Nullable Level level, ItemStack stack) {
    for (ReprocessingProcess process : all(level)) if (process.matches(stack)) return true;
    return false;
  }

  public boolean matches(ItemStack stack) {
    return stack.is(input);
  }

  public List<ItemStack> outputCopies() {
    return outputs.stream().map(ItemStack::copy).toList();
  }

  public int totalEnergy() {
    return ticks * energyPerTick;
  }

  @Override
  public boolean matches(Container container, Level level) {
    return !container.isEmpty() && matches(container.getItem(0));
  }

  @Override
  public ItemStack assemble(Container container, RegistryAccess registryAccess) {
    return outputs.get(0).copy();
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return true;
  }

  @Override
  public ItemStack getResultItem(RegistryAccess registryAccess) {
    return outputs.get(0).copy();
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    NonNullList<Ingredient> ingredients = NonNullList.create();
    ingredients.add(Ingredient.of(new ItemStack(input, inputCount)));
    return ingredients;
  }

  @Override
  public ResourceLocation getId() {
    return id;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.REPROCESSING_SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return ModRecipes.REPROCESSING_TYPE.get();
  }

  private static final class Holder {
    private static final List<ReprocessingProcess> PROCESSES =
        List.of(
            process(
                "silicon_slag",
                ModItems.SILICON_SLAG.get(),
                4,
                List.of(new ItemStack(Items.QUARTZ), new ItemStack(Items.CHARCOAL)),
                240,
                40),
            process(
                "contaminated_wafer",
                ModItems.CONTAMINATED_WAFER.get(),
                1,
                List.of(
                    new ItemStack(ModItems.HIGH_PURITY_SILICON.get(), 2),
                    new ItemStack(Items.REDSTONE)),
                300,
                60),
            process(
                "contaminated_gate",
                ModItems.CONTAMINATED_GATE.get(),
                1,
                List.of(
                    new ItemStack(ModItems.HIGH_PURITY_SILICON.get()),
                    new ItemStack(Items.REDSTONE),
                    new ItemStack(ModItems.COPPER_NUGGET.get(), 2)),
                240,
                50),
            wafer("ssi_wafer", ModItems.SSI_WAFER.get(), 2),
            wafer("msi_wafer", ModItems.MSI_WAFER.get(), 3),
            wafer("lsi_wafer", ModItems.LSI_WAFER.get(), 4),
            wafer("vlsi_wafer", ModItems.VLSI_WAFER.get(), 5),
            wafer("ulsi_wafer", ModItems.ULSI_WAFER.get(), 6),
            gate("not_gate", ModItems.NOT_GATE.get()),
            gate("and_gate", ModItems.AND_GATE.get()),
            gate("or_gate", ModItems.OR_GATE.get()),
            gate("xor_gate", ModItems.XOR_GATE.get()),
            gate("buffer_gate", ModItems.BUFFER_GATE.get()),
            gate("drop_gate", ModItems.DROP_GATE.get()),
            gate("switch_gate", ModItems.SWITCH_GATE.get()));
  }

  private static ReprocessingProcess wafer(String id, Item input, int siliconOutput) {
    return process(
        id,
        input,
        1,
        List.of(
            new ItemStack(ModItems.HIGH_PURITY_SILICON.get(), siliconOutput),
            new ItemStack(Items.REDSTONE)),
        300,
        60);
  }

  private static ReprocessingProcess gate(String id, Item input) {
    return process(
        id,
        input,
        1,
        List.of(
            new ItemStack(ModItems.HIGH_PURITY_SILICON.get()),
            new ItemStack(Items.REDSTONE),
            new ItemStack(ModItems.COPPER_NUGGET.get(), 2)),
        240,
        50);
  }

  private static ReprocessingProcess process(
      String id,
      Item input,
      int inputCount,
      List<ItemStack> outputs,
      int ticks,
      int energyPerTick) {
    return new ReprocessingProcess(
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "reprocessor/" + id),
        input,
        inputCount,
        outputs,
        ticks,
        energyPerTick);
  }

  public static final class Serializer implements RecipeSerializer<ReprocessingProcess> {
    @Override
    public ReprocessingProcess fromJson(ResourceLocation id, JsonObject json) {
      JsonObject inputJson = GsonHelper.getAsJsonObject(json, "input");
      Item input = readItem(GsonHelper.getAsString(inputJson, "item"));
      int inputCount = GsonHelper.getAsInt(inputJson, "count", 1);
      JsonArray outputArray = GsonHelper.getAsJsonArray(json, "outputs");
      List<ItemStack> outputs = new java.util.ArrayList<>();
      for (int index = 0; index < outputArray.size(); index++) {
        JsonObject output = GsonHelper.convertToJsonObject(outputArray.get(index), "output");
        outputs.add(
            new ItemStack(
                readItem(GsonHelper.getAsString(output, "item")),
                GsonHelper.getAsInt(output, "count", 1)));
      }
      return new ReprocessingProcess(
          id,
          input,
          inputCount,
          outputs,
          GsonHelper.getAsInt(json, "ticks"),
          GsonHelper.getAsInt(json, "energy_per_tick"));
    }

    @Override
    public ReprocessingProcess fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
      Item input = ForgeRegistries.ITEMS.getValue(buffer.readResourceLocation());
      int inputCount = buffer.readVarInt();
      List<ItemStack> outputs = buffer.readList(FriendlyByteBuf::readItem);
      return new ReprocessingProcess(
          id, input, inputCount, outputs, buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, ReprocessingProcess process) {
      buffer.writeResourceLocation(ForgeRegistries.ITEMS.getKey(process.input()));
      buffer.writeVarInt(process.inputCount());
      buffer.writeCollection(process.outputs(), FriendlyByteBuf::writeItem);
      buffer.writeVarInt(process.ticks());
      buffer.writeVarInt(process.energyPerTick());
    }

    private static Item readItem(String name) {
      ResourceLocation id = ResourceLocation.tryParse(name);
      Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
      if (item == null || item == Items.AIR)
        throw new JsonParseException("Unknown reprocessing item: " + name);
      return item;
    }
  }
}
