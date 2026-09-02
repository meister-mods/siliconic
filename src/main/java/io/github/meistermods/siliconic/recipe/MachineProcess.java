package io.github.meistermods.siliconic.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.meistermods.siliconic.registry.ModRecipes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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

@SuppressWarnings({"null", "deprecated"})
public record MachineProcess(
    ResourceLocation id,
    MachineKind machine,
    List<ProcessInput> inputs,
    Item resultItem,
    int resultCount,
    List<ItemStack> byproducts,
    int ticks,
    int energyPerTick,
    boolean shaped)
    implements Recipe<Container> {
  public MachineProcess {
    Objects.requireNonNull(id, "Process ID must not be null");
    Objects.requireNonNull(machine, "Process machine must not be null");
    Objects.requireNonNull(resultItem, "Process output item must not be null");
    inputs = List.copyOf(Objects.requireNonNull(inputs, "Process inputs must not be null"));
    byproducts =
        Objects.requireNonNull(byproducts, "Process byproducts must not be null").stream()
            .map(ItemStack::copy)
            .toList();
    if (inputs.isEmpty())
      throw new IllegalArgumentException("Process must have at least one input");
    validateInputs(machine, inputs, shaped);
    if (resultItem == Items.AIR)
      throw new IllegalArgumentException("Process output must not be air");
    if (resultCount < 1)
      throw new IllegalArgumentException("Process output count must be positive");
    if (resultCount > new ItemStack(resultItem).getMaxStackSize())
      throw new IllegalArgumentException("Process output count exceeds its maximum stack size");
    for (ItemStack byproduct : byproducts) validateByproduct(byproduct);
    if (ticks < 1) throw new IllegalArgumentException("Process duration must be positive");
    if (energyPerTick < 1)
      throw new IllegalArgumentException("Process energy use must be positive");
  }

  public ItemStack result() {
    return new ItemStack(resultItem, resultCount);
  }

  @Override
  public boolean matches(Container container, Level level) {
    return false;
  }

  @Override
  public ItemStack assemble(Container container, RegistryAccess registryAccess) {
    return result();
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return true;
  }

  @Override
  public ItemStack getResultItem(RegistryAccess registryAccess) {
    return result();
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    NonNullList<Ingredient> ingredients = NonNullList.create();
    inputs.forEach(input -> ingredients.add(input.ingredient()));
    return ingredients;
  }

  @Override
  public ResourceLocation getId() {
    return id;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return ModRecipes.MACHINE_PROCESS_SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return ModRecipes.MACHINE_PROCESS_TYPE.get();
  }

  /** Returns fresh copies of every primary and secondary output produced by one batch. */
  public List<ItemStack> outputCopies() {
    List<ItemStack> outputs = new ArrayList<>(1 + byproducts.size());
    outputs.add(result());
    byproducts.forEach(output -> outputs.add(output.copy()));
    return outputs;
  }

  public long totalEnergy() {
    return (long) ticks * energyPerTick;
  }

  public boolean matches(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (!hasValidInputWindow(inventory, inputStart, inputSlots)) return false;
    if (shaped) {
      for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++) {
        ProcessInput expected = inputAtSlot(relativeSlot);
        ItemStack actual = inventory.getStackInSlot(inputStart + relativeSlot);
        if (expected == null) {
          if (!actual.isEmpty()) return false;
        } else if (!expected.matches(actual) || actual.getCount() < expected.requiredItems()) {
          return false;
        }
      }
      return true;
    }
    return findShapelessMatch(inventory, inputStart, inputSlots) != null;
  }

  public boolean accepts(int relativeSlot, ItemStack stack) {
    if (relativeSlot < 0 || relativeSlot >= machine.inputSlots()) return false;
    if (shaped) {
      ProcessInput input = inputAtSlot(relativeSlot);
      return input != null && input.matches(stack);
    }
    return inputs.stream().anyMatch(input -> input.matches(stack));
  }

  public void consume(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (shaped) {
      for (ProcessInput input : inputs) applyUse(inventory, inputStart + input.slot(), input);
      return;
    }
    ShapelessMatch match = findShapelessMatch(inventory, inputStart, inputSlots);
    if (match == null) return;
    for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
      ProcessInput input = inputs.get(inputIndex);
      if (input.use() == ProcessInput.Use.CATALYST) continue;
      for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++) {
        int assigned = match.allocations()[inputIndex][relativeSlot];
        if (assigned <= 0) continue;
        int slot = inputStart + relativeSlot;
        if (input.use() == ProcessInput.Use.CONSUME) inventory.extractItem(slot, assigned, false);
        else if (input.use() == ProcessInput.Use.DAMAGE) damage(inventory, slot, input.count());
      }
    }
  }

  private void applyUse(ItemStackHandler inventory, int slot, ProcessInput input) {
    if (input.use() == ProcessInput.Use.CONSUME) inventory.extractItem(slot, input.count(), false);
    else if (input.use() == ProcessInput.Use.DAMAGE) damage(inventory, slot, input.count());
  }

  private void damage(ItemStackHandler inventory, int slot, int amount) {
    ItemStack stack = inventory.getStackInSlot(slot).copy();
    if (!stack.isDamageableItem()) return;
    int damage = stack.getDamageValue() + amount;
    if (damage >= stack.getMaxDamage()) stack.shrink(1);
    else stack.setDamageValue(damage);
    inventory.setStackInSlot(slot, stack);
  }

  public ProcessInput inputAtSlot(int slot) {
    for (ProcessInput input : inputs) if (input.slot() == slot) return input;
    return null;
  }

  private boolean hasValidInputWindow(ItemStackHandler inventory, int inputStart, int inputSlots) {
    return inventory != null
        && inputStart >= 0
        && inputSlots == machine.inputSlots()
        && inputStart <= inventory.getSlots() - inputSlots;
  }

  /**
   * Assigns available item counts to shapeless inputs as a small maximum-flow problem. This avoids
   * letting one stack satisfy two overlapping ingredients at the same time.
   */
  private ShapelessMatch findShapelessMatch(
      ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (!hasValidInputWindow(inventory, inputStart, inputSlots)) return null;
    int source = 0;
    int slotBase = 1;
    int inputBase = slotBase + inputSlots;
    int sink = inputBase + inputs.size();
    int[][] residual = new int[sink + 1][sink + 1];
    long totalRequired = 0;

    for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++) {
      ItemStack stack = inventory.getStackInSlot(inputStart + relativeSlot);
      if (stack.isEmpty()) continue;
      int slotNode = slotBase + relativeSlot;
      residual[source][slotNode] = stack.getCount();
      boolean accepted = false;
      for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
        if (!inputs.get(inputIndex).matches(stack)) continue;
        residual[slotNode][inputBase + inputIndex] = stack.getCount();
        accepted = true;
      }
      if (!accepted) return null;
    }
    for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
      int required = inputs.get(inputIndex).requiredItems();
      residual[inputBase + inputIndex][sink] = required;
      totalRequired += required;
    }

    int flow = 0;
    int[] parent = new int[residual.length];
    while (true) {
      Arrays.fill(parent, -1);
      parent[source] = source;
      ArrayDeque<Integer> pending = new ArrayDeque<>();
      pending.add(source);
      while (!pending.isEmpty() && parent[sink] < 0) {
        int node = pending.removeFirst();
        for (int next = 0; next < residual.length; next++) {
          if (parent[next] >= 0 || residual[node][next] <= 0) continue;
          parent[next] = node;
          pending.addLast(next);
        }
      }
      if (parent[sink] < 0) break;
      int amount = Integer.MAX_VALUE;
      for (int node = sink; node != source; node = parent[node])
        amount = Math.min(amount, residual[parent[node]][node]);
      for (int node = sink; node != source; node = parent[node]) {
        residual[parent[node]][node] -= amount;
        residual[node][parent[node]] += amount;
      }
      flow += amount;
    }
    if (flow != totalRequired) return null;

    int[][] allocations = new int[inputs.size()][inputSlots];
    for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++)
      for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++)
        allocations[inputIndex][relativeSlot] =
            residual[inputBase + inputIndex][slotBase + relativeSlot];
    return new ShapelessMatch(allocations);
  }

  private static void validateInputs(
      MachineKind machine, List<ProcessInput> inputs, boolean shaped) {
    Set<Integer> occupiedSlots = new HashSet<>();
    for (ProcessInput input : inputs) {
      if (shaped) {
        if (input.slot() < 0 || input.slot() >= machine.inputSlots())
          throw new IllegalArgumentException(
              "Shaped process input slot "
                  + input.slot()
                  + " is outside the valid range for "
                  + machine.id());
        if (!occupiedSlots.add(input.slot()))
          throw new IllegalArgumentException(
              "Shaped process has more than one input in slot " + input.slot());
      } else if (input.slot() != -1) {
        throw new IllegalArgumentException("Shapeless process input slots must be -1");
      }
    }
  }

  private static void validateByproduct(ItemStack byproduct) {
    if (byproduct == null || byproduct.isEmpty())
      throw new IllegalArgumentException("Process byproducts must not be empty");
    if (byproduct.getCount() > byproduct.getMaxStackSize())
      throw new IllegalArgumentException("Process byproduct count exceeds its maximum stack size");
  }

  private record ShapelessMatch(int[][] allocations) {}

  /** Datapack serializer for {@code data/<namespace>/recipes/*.json}. */
  public static final class Serializer implements RecipeSerializer<MachineProcess> {
    @Override
    public MachineProcess fromJson(ResourceLocation id, JsonObject json) {
      MachineKind machine = machineKind(GsonHelper.getAsString(json, "machine"));
      boolean shaped = GsonHelper.getAsBoolean(json, "shaped", true);
      int ticks = GsonHelper.getAsInt(json, "ticks");
      int energyPerTick = GsonHelper.getAsInt(json, "energy_per_tick");
      List<ProcessInput> inputs = new ArrayList<>();
      JsonArray inputArray = GsonHelper.getAsJsonArray(json, "inputs");
      for (int index = 0; index < inputArray.size(); index++) {
        JsonObject entry = GsonHelper.convertToJsonObject(inputArray.get(index), "input");
        int slot = GsonHelper.getAsInt(entry, "slot", shaped ? index : -1);
        Ingredient ingredient = Ingredient.fromJson(entry.get("ingredient"));
        int count = GsonHelper.getAsInt(entry, "count", 1);
        ProcessInput.Use use = parseUse(GsonHelper.getAsString(entry, "use", "consume"));
        inputs.add(new ProcessInput(slot, ingredient, count, use));
      }
      ItemStack result = readStack(GsonHelper.getAsJsonObject(json, "result"));
      List<ItemStack> byproducts = new ArrayList<>();
      if (json.has("byproducts")) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "byproducts");
        for (int index = 0; index < array.size(); index++)
          byproducts.add(readStack(GsonHelper.convertToJsonObject(array.get(index), "byproduct")));
      }
      return new MachineProcess(
          id,
          machine,
          inputs,
          result.getItem(),
          result.getCount(),
          byproducts,
          ticks,
          energyPerTick,
          shaped);
    }

    @Override
    public MachineProcess fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
      MachineKind machine = buffer.readEnum(MachineKind.class);
      boolean shaped = buffer.readBoolean();
      int ticks = buffer.readVarInt();
      int energyPerTick = buffer.readVarInt();
      List<ProcessInput> inputs =
          buffer.readList(
              data ->
                  new ProcessInput(
                      data.readVarInt(),
                      Ingredient.fromNetwork(data),
                      data.readVarInt(),
                      data.readEnum(ProcessInput.Use.class)));
      ItemStack result = buffer.readItem();
      List<ItemStack> byproducts = buffer.readList(FriendlyByteBuf::readItem);
      return new MachineProcess(
          id,
          machine,
          inputs,
          result.getItem(),
          result.getCount(),
          byproducts,
          ticks,
          energyPerTick,
          shaped);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, MachineProcess process) {
      buffer.writeEnum(process.machine());
      buffer.writeBoolean(process.shaped());
      buffer.writeVarInt(process.ticks());
      buffer.writeVarInt(process.energyPerTick());
      buffer.writeCollection(
          process.inputs(),
          (data, input) -> {
            data.writeVarInt(input.slot());
            input.ingredient().toNetwork(data);
            data.writeVarInt(input.count());
            data.writeEnum(input.use());
          });
      buffer.writeItem(process.result());
      buffer.writeCollection(process.byproducts(), FriendlyByteBuf::writeItem);
    }

    private static MachineKind machineKind(String id) {
      for (MachineKind machine : MachineKind.values()) if (machine.id().equals(id)) return machine;
      throw new JsonParseException("Unknown Siliconic machine: " + id);
    }

    private static ProcessInput.Use parseUse(String name) {
      try {
        return ProcessInput.Use.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
        throw new JsonParseException("Unknown process input use: " + name, exception);
      }
    }

    private static ItemStack readStack(JsonObject json) {
      ResourceLocation itemId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "item"));
      Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
      if (item == null || item == Items.AIR)
        throw new JsonParseException("Unknown process output item: " + itemId);
      int count = GsonHelper.getAsInt(json, "count", 1);
      ItemStack output = new ItemStack(item);
      int maxStackSize = output.getMaxStackSize();
      if (count < 1 || count > maxStackSize)
        throw new JsonParseException(
            "Process output count for " + itemId + " must be between 1 and " + maxStackSize);
      return output.copyWithCount(count);
    }
  }
}
