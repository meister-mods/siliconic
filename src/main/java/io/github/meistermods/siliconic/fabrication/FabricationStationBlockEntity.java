package io.github.meistermods.siliconic.fabrication;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class FabricationStationBlockEntity extends BlockEntity implements MenuProvider {
  public static final int INPUT_START = 0, INPUT_SLOTS = 9, OUTPUT_SLOT = 9, SLOT_COUNT = 10;
  public static final int ENERGY_CAPACITY = 60_000;
  public static final int WAFER_ENERGY_PER_TICK = 60;
  public static final int GATE_ENERGY_PER_TICK = 30;
  public static final int GATE_PROCESS_TICKS = 120;

  private int progress;
  private final StationEnergyStorage energy = new StationEnergyStorage();
  private final ItemStackHandler items =
      new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot >= INPUT_START
              && slot < INPUT_START + INPUT_SLOTS
              && isPotentialIngredient(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (slot < INPUT_START + INPUT_SLOTS) progress = 0;
          setChanged();
        }
      };
  private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCapability =
      LazyOptional.of(() -> energy);
  private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
  private final ContainerData data =
      new ContainerData() {
        @Override
        public int get(int index) {
          ProcessRecipe recipe = findRecipe();
          return switch (index) {
            case 0 -> energy.getEnergyStored();
            case 1 -> energy.getMaxEnergyStored();
            case 2 -> progress;
            case 3 -> recipe == null ? 0 : recipe.ticks();
            case 4 -> recipe == null ? 0 : recipe.energyPerTick();
            case 5 -> status(recipe);
            default -> 0;
          };
        }

        @Override
        public void set(int index, int value) {
          switch (index) {
            case 0 -> energy.setStored(value);
            case 2 -> progress = Math.max(0, value);
            default -> {
              // The remaining values are derived from the current recipe and machine state.
            }
          }
        }

        @Override
        public int getCount() {
          return 6;
        }
      };

  private final class StationEnergyStorage extends EnergyStorage {
    StationEnergyStorage() {
      super(ENERGY_CAPACITY, 2_000, 0);
    }

    void setStored(int value) {
      energy = Math.max(0, Math.min(value, capacity));
    }

    boolean consumeInternal(int amount) {
      if (amount <= 0 || energy < amount) return false;
      energy -= amount;
      return true;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
      int accepted = super.receiveEnergy(amount, simulate);
      if (accepted > 0 && !simulate) FabricationStationBlockEntity.this.setChanged();
      return accepted;
    }
  }

  private record Requirement(Item item, int count) {}

  private record ProcessRecipe(
      @Nullable Item[] pattern,
      List<Requirement> requirements,
      ItemStack result,
      int ticks,
      int energyPerTick) {}

  public FabricationStationBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.FABRICATION_STATION.get(), pos, state);
  }

  public boolean isWaferFabricator() {
    return getBlockState().is(ModBlocks.WAFER_FABRICATOR.get());
  }

  public ItemStackHandler items() {
    return items;
  }

  public ContainerData data() {
    return data;
  }

  public int status() {
    return status(findRecipe());
  }

  private int status(@Nullable ProcessRecipe recipe) {
    if (recipe == null) return 0;
    if (!canFitOutput(recipe.result())) return 1;
    if (energy.getEnergyStored() < recipe.energyPerTick()) return 2;
    return 3;
  }

  public static void serverTick(
      Level level, BlockPos pos, BlockState state, FabricationStationBlockEntity station) {
    ProcessRecipe recipe = station.findRecipe();
    if (recipe == null || !station.canFitOutput(recipe.result())) {
      station.resetProgress();
      return;
    }
    if (!station.energy.consumeInternal(recipe.energyPerTick())) return;
    station.progress++;
    if (station.progress >= recipe.ticks()) station.finishProcess(recipe);
    station.setChanged();
  }

  private void resetProgress() {
    if (progress == 0) return;
    progress = 0;
    setChanged();
  }

  @Nullable
  private ProcessRecipe findRecipe() {
    if (isWaferFabricator()) {
      ProcessRecipe recipe =
          waferRecipe(
              ModItems.ULSI_WAFER.get(),
              600,
              ModItems.PURE_SILICON.get(), Items.ECHO_SHARD, ModItems.PURE_SILICON.get(),
              Items.ECHO_SHARD, ModItems.VLSI_WAFER.get(), Items.ECHO_SHARD,
              ModItems.PURE_SILICON.get(), Items.ECHO_SHARD, ModItems.PURE_SILICON.get());
      if (matches(recipe)) return recipe;
      recipe =
          waferRecipe(
              ModItems.VLSI_WAFER.get(),
              500,
              ModItems.PURE_SILICON.get(), Items.NETHERITE_SCRAP, ModItems.PURE_SILICON.get(),
              Items.NETHERITE_SCRAP, ModItems.LSI_WAFER.get(), Items.NETHERITE_SCRAP,
              ModItems.PURE_SILICON.get(), Items.NETHERITE_SCRAP, ModItems.PURE_SILICON.get());
      if (matches(recipe)) return recipe;
      recipe =
          waferRecipe(
              ModItems.LSI_WAFER.get(),
              400,
              ModItems.PURE_SILICON.get(), Items.DIAMOND, ModItems.PURE_SILICON.get(),
              Items.DIAMOND, ModItems.MSI_WAFER.get(), Items.DIAMOND,
              ModItems.PURE_SILICON.get(), Items.DIAMOND, ModItems.PURE_SILICON.get());
      if (matches(recipe)) return recipe;
      recipe =
          waferRecipe(
              ModItems.MSI_WAFER.get(),
              300,
              ModItems.PURE_SILICON.get(), Items.GOLD_INGOT, ModItems.PURE_SILICON.get(),
              Items.GOLD_INGOT, ModItems.SSI_WAFER.get(), Items.GOLD_INGOT,
              ModItems.PURE_SILICON.get(), Items.GOLD_INGOT, ModItems.PURE_SILICON.get());
      if (matches(recipe)) return recipe;
      recipe =
          shapelessWaferRecipe(
              ModItems.SSI_WAFER.get(),
              200,
              2,
              requirement(ModItems.PURE_SILICON.get(), 1),
              requirement(Items.IRON_NUGGET, 1));
      return matches(recipe) ? recipe : null;
    }

    ProcessRecipe recipe =
        gateRecipe(
            ModItems.XOR_GATE.get(),
            ModItems.COPPER_NUGGET.get(), Items.REDSTONE, ModItems.COPPER_NUGGET.get(),
            Items.REDSTONE, Items.AMETHYST_SHARD, Items.REDSTONE,
            ModItems.COPPER_NUGGET.get(), Items.REDSTONE, ModItems.COPPER_NUGGET.get());
    if (matches(recipe)) return recipe;
    recipe =
        gateRecipe(
            ModItems.BUFFER_GATE.get(),
            null, ModItems.COPPER_NUGGET.get(), null,
            Items.REDSTONE_TORCH, Items.QUARTZ, Items.REDSTONE_TORCH,
            null, ModItems.COPPER_NUGGET.get(), null);
    if (matches(recipe)) return recipe;
    recipe =
        gateRecipe(
            ModItems.NOT_GATE.get(),
            null, Items.REDSTONE_TORCH, null,
            ModItems.COPPER_NUGGET.get(), Items.QUARTZ, ModItems.COPPER_NUGGET.get(),
            null, ModItems.COPPER_NUGGET.get(), null);
    if (matches(recipe)) return recipe;
    recipe =
        gateRecipe(
            ModItems.AND_GATE.get(),
            ModItems.COPPER_NUGGET.get(), null, ModItems.COPPER_NUGGET.get(),
            null, Items.QUARTZ, Items.REDSTONE,
            ModItems.COPPER_NUGGET.get(), null, ModItems.COPPER_NUGGET.get());
    if (matches(recipe)) return recipe;
    recipe =
        gateRecipe(
            ModItems.OR_GATE.get(),
            null, ModItems.COPPER_NUGGET.get(), null,
            ModItems.COPPER_NUGGET.get(), Items.QUARTZ, ModItems.COPPER_NUGGET.get(),
            null, Items.REDSTONE, null);
    return matches(recipe) ? recipe : null;
  }

  private ProcessRecipe waferRecipe(Item result, int ticks, Item... pattern) {
    return new ProcessRecipe(
        pattern,
        List.of(),
        new ItemStack(result),
        ticks,
        WAFER_ENERGY_PER_TICK);
  }

  private ProcessRecipe shapelessWaferRecipe(
      Item result, int ticks, int count, Requirement... requirements) {
    return new ProcessRecipe(
        null,
        List.of(requirements),
        new ItemStack(result, count),
        ticks,
        WAFER_ENERGY_PER_TICK);
  }

  private ProcessRecipe gateRecipe(Item result, Item... pattern) {
    return new ProcessRecipe(
        pattern,
        List.of(),
        new ItemStack(result),
        GATE_PROCESS_TICKS,
        GATE_ENERGY_PER_TICK);
  }

  private Requirement requirement(Item item, int count) {
    return new Requirement(item, count);
  }

  private boolean matches(ProcessRecipe recipe) {
    if (recipe.pattern() != null) {
      if (recipe.pattern().length != INPUT_SLOTS) return false;
      for (int slot = INPUT_START; slot < INPUT_START + INPUT_SLOTS; slot++) {
        Item expected = recipe.pattern()[slot - INPUT_START];
        ItemStack stack = items.getStackInSlot(slot);
        if (expected == null ? !stack.isEmpty() : !stack.is(expected)) return false;
      }
      return true;
    }
    for (Requirement requirement : recipe.requirements()) {
      int available = 0;
      for (int slot = INPUT_START; slot < INPUT_START + INPUT_SLOTS; slot++) {
        ItemStack stack = items.getStackInSlot(slot);
        if (stack.is(requirement.item())) available += stack.getCount();
      }
      if (available < requirement.count()) return false;
    }
    for (int slot = INPUT_START; slot < INPUT_START + INPUT_SLOTS; slot++) {
      ItemStack stack = items.getStackInSlot(slot);
      if (stack.isEmpty()) continue;
      boolean used = false;
      for (Requirement requirement : recipe.requirements())
        if (stack.is(requirement.item())) {
          used = true;
          break;
        }
      if (!used) return false;
    }
    return true;
  }

  private boolean isPotentialIngredient(ItemStack stack) {
    if (isWaferFabricator())
      return stack.is(ModItems.PURE_SILICON.get())
          || stack.is(Items.IRON_NUGGET)
          || stack.is(Items.GOLD_INGOT)
          || stack.is(Items.DIAMOND)
          || stack.is(Items.NETHERITE_SCRAP)
          || stack.is(Items.ECHO_SHARD)
          || stack.is(ModItems.SSI_WAFER.get())
          || stack.is(ModItems.MSI_WAFER.get())
          || stack.is(ModItems.LSI_WAFER.get())
          || stack.is(ModItems.VLSI_WAFER.get());
    return stack.is(ModItems.COPPER_NUGGET.get())
        || stack.is(Items.QUARTZ)
        || stack.is(Items.AMETHYST_SHARD)
        || stack.is(Items.REDSTONE)
        || stack.is(Items.REDSTONE_TORCH);
  }

  private boolean canFitOutput(ItemStack result) {
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) return true;
    return ItemStack.isSameItemSameTags(output, result)
        && output.getCount() + result.getCount() <= output.getMaxStackSize();
  }

  private void finishProcess(ProcessRecipe recipe) {
    if (recipe.pattern() != null) {
      for (int slot = INPUT_START; slot < INPUT_START + INPUT_SLOTS; slot++) {
        if (recipe.pattern()[slot - INPUT_START] != null) items.extractItem(slot, 1, false);
      }
    } else {
      for (Requirement requirement : recipe.requirements()) {
        int remaining = requirement.count();
        for (int slot = INPUT_START;
            slot < INPUT_START + INPUT_SLOTS && remaining > 0;
            slot++) {
          ItemStack stack = items.getStackInSlot(slot);
          if (!stack.is(requirement.item())) continue;
          int extracted = Math.min(remaining, stack.getCount());
          items.extractItem(slot, extracted, false);
          remaining -= extracted;
        }
      }
    }
    ItemStack output = items.getStackInSlot(OUTPUT_SLOT);
    if (output.isEmpty()) items.setStackInSlot(OUTPUT_SLOT, recipe.result().copy());
    else output.grow(recipe.result().getCount());
    progress = 0;
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    tag.put("Items", items.serializeNBT());
    tag.putInt("Energy", energy.getEnergyStored());
    tag.putInt("Progress", progress);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    items.deserializeNBT(tag.getCompound("Items"));
    energy.setStored(tag.getInt("Energy"));
    progress = Math.max(0, tag.getInt("Progress"));
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
    if (capability == ForgeCapabilities.ENERGY) return energyCapability.cast();
    if (capability == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
    return super.getCapability(capability, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    energyCapability.invalidate();
    itemCapability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    energyCapability = LazyOptional.of(() -> energy);
    itemCapability = LazyOptional.of(() -> items);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable(
        isWaferFabricator()
            ? "container.siliconic.wafer_fabricator"
            : "container.siliconic.gate_assembler");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new FabricationStationMenu(id, inventory, this);
  }
}
