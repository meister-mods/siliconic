package io.github.meistermods.siliconic.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.meistermods.siliconic.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

@SuppressWarnings({"null"})
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
    inputs = List.copyOf(inputs);
    byproducts = byproducts.stream().map(ItemStack::copy).toList();
    if (resultCount < 1)
      throw new IllegalArgumentException("Process output count must be positive");
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

  public int totalEnergy() {
    return ticks * energyPerTick;
  }

  public boolean matches(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (shaped) {
      for (int relativeSlot = 0; relativeSlot < inputSlots; relativeSlot++) {
        ProcessInput expected = inputAt(relativeSlot);
        ItemStack actual = inventory.getStackInSlot(inputStart + relativeSlot);
        if (expected == null) {
          if (!actual.isEmpty()) return false;
        } else if (!expected.matches(actual) || actual.getCount() < expected.count()) {
          return false;
        }
      }
      return true;
    }

    for (ProcessInput input : inputs) {
      int available = 0;
      for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (input.matches(stack)) available += stack.getCount();
      }
      if (available < input.count()) return false;
    }
    for (int slot = inputStart; slot < inputStart + inputSlots; slot++) {
      ItemStack stack = inventory.getStackInSlot(slot);
      if (!stack.isEmpty() && inputs.stream().noneMatch(input -> input.matches(stack)))
        return false;
    }
    return true;
  }

  public boolean accepts(int relativeSlot, ItemStack stack) {
    if (shaped) {
      ProcessInput input = inputAt(relativeSlot);
      return input != null && input.matches(stack);
    }
    return inputs.stream().anyMatch(input -> input.matches(stack));
  }

  public void consume(ItemStackHandler inventory, int inputStart, int inputSlots) {
    if (shaped) {
      for (ProcessInput input : inputs) applyUse(inventory, inputStart + input.slot(), input);
      return;
    }
    for (ProcessInput input : inputs) {
      int remaining = input.count();
      for (int slot = inputStart; slot < inputStart + inputSlots && remaining > 0; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!input.matches(stack)) continue;
        int used = Math.min(remaining, stack.getCount());
        if (input.use() == ProcessInput.Use.CONSUME) inventory.extractItem(slot, used, false);
        else if (input.use() == ProcessInput.Use.DAMAGE) damage(inventory, slot, used);
        remaining -= used;
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

  private ProcessInput inputAt(int slot) {
    for (ProcessInput input : inputs) if (input.slot() == slot) return input;
    return null;
  }

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
      return new ItemStack(item, GsonHelper.getAsInt(json, "count", 1));
    }
  }
}
