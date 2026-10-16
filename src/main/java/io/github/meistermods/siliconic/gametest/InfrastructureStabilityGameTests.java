package io.github.meistermods.siliconic.gametest;

import static io.github.meistermods.siliconic.gametest.GameTestFixtures.ORIGIN;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.authlib.GameProfile;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.cleanroom.ConditionerBlockEntity;
import io.github.meistermods.siliconic.cleanroom.ConditionerMenu;
import io.github.meistermods.siliconic.cleanroom.RoomScanResult;
import io.github.meistermods.siliconic.cleanroom.RoomScanner;
import io.github.meistermods.siliconic.config.SiliconicConfig;
import io.github.meistermods.siliconic.logistics.LogisticsControllerBlockEntity;
import io.github.meistermods.siliconic.logistics.LogisticsControllerMenu;
import io.github.meistermods.siliconic.logistics.LogisticsInventoryAccess;
import io.github.meistermods.siliconic.logistics.LogisticsPipeBlock;
import io.github.meistermods.siliconic.network.LogisticsPagePacket;
import io.github.meistermods.siliconic.power.CoalGeneratorBlockEntity;
import io.github.meistermods.siliconic.power.CoalGeneratorMenu;
import io.github.meistermods.siliconic.power.EnergyBufferBlockEntity;
import io.github.meistermods.siliconic.power.PowerCableBlock;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** Regression coverage for power, logistics, and cleanroom edge cases. */
@SuppressWarnings("null")
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfrastructureStabilityGameTests {
  @Nullable private static Runnable tickingPowerCleanup;
  @Nullable private static Runnable initialCableCleanup;
  @Nullable private static CommentedConfig originalConfigData;
  private static int configScopes;

  public static void beginConfigIsolation() {
    if (configScopes > 0) {
      configScopes++;
      return;
    }
    CommentedConfig original =
        ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
            .filter(config -> config.getSpec() == SiliconicConfig.SPEC)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Siliconic common config is missing"))
            .getConfigData();
    // ConfigValue.set autosaves file-backed data. A concurrent Forge watcher reload can otherwise
    // overwrite a fixture's temporary values between its setup and manual server tick.
    SiliconicConfig.SPEC.setConfig(copyConfig(original));
    originalConfigData = original;
    configScopes = 1;
  }

  public static void endConfigIsolation() {
    if (configScopes == 0 || --configScopes > 0) return;
    CommentedConfig original = originalConfigData;
    originalConfigData = null;
    if (original != null) SiliconicConfig.SPEC.setConfig(original);
  }

  private static CommentedConfig copyConfig(UnmodifiableConfig original) {
    CommentedConfig copy = CommentedConfig.inMemory();
    for (var entry : original.entrySet())
      copy.set(List.of(entry.getKey()), copyConfigValue(entry.getRawValue()));
    return copy;
  }

  private static Object copyConfigValue(Object value) {
    if (value instanceof UnmodifiableConfig config) return copyConfig(config);
    if (value instanceof List<?> list)
      return list.stream().map(InfrastructureStabilityGameTests::copyConfigValue).toList();
    return value;
  }

  @BeforeBatch(batch = "defaultBatch")
  public static void beforeDefaultBatch(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "defaultBatch")
  public static void afterDefaultBatch(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_logistics_drops")
  public static void beforeLogisticsDrops(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_logistics_drops")
  public static void afterLogisticsDrops(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_large_logistics")
  public static void beforeLargeLogistics(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_large_logistics")
  public static void afterLargeLogistics(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_logistics_lifecycle")
  public static void beforeLogisticsLifecycle(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_logistics_lifecycle")
  public static void afterLogisticsLifecycle(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_lifecycle")
  public static void beforePowerLifecycle(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_lifecycle")
  public static void afterPowerLifecycle(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_buffer_lifecycle")
  public static void beforeBufferLifecycle(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_buffer_lifecycle")
  public static void afterBufferLifecycle(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_cable_connections")
  public static void beforeCableConnections(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_cable_connections")
  public static void afterCableConnections(ServerLevel level) {
    endConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_ticking_power_lifecycle")
  public static void beforeTickingPower(ServerLevel level) {
    beginConfigIsolation();
  }

  @BeforeBatch(batch = "infrastructure_initial_cable_connections")
  public static void beforeInitialCable(ServerLevel level) {
    beginConfigIsolation();
  }

  @AfterBatch(batch = "infrastructure_ticking_power_lifecycle")
  public static void afterTickingPower(ServerLevel level) {
    try {
      cleanUpTickingPower(level);
    } finally {
      endConfigIsolation();
    }
  }

  private static void cleanUpTickingPower(ServerLevel level) {
    Runnable cleanup = tickingPowerCleanup;
    tickingPowerCleanup = null;
    if (cleanup != null) cleanup.run();
  }

  @AfterBatch(batch = "infrastructure_initial_cable_connections")
  public static void afterInitialCable(ServerLevel level) {
    try {
      cleanUpInitialCable(level);
    } finally {
      endConfigIsolation();
    }
  }

  private static void cleanUpInitialCable(ServerLevel level) {
    Runnable cleanup = initialCableCleanup;
    initialCableCleanup = null;
    if (cleanup != null) cleanup.run();
  }

  private record LogisticsFixture(
      LogisticsControllerBlockEntity controller,
      ChestBlockEntity source,
      ChestBlockEntity receiver,
      BlockPos pos) {}

  private static class ReplaceableCapabilityChest extends ChestBlockEntity {
    private ItemStackHandler machineItems = new ItemStackHandler(1);
    private IEnergyStorage machineEnergy = new EnergyStorage(1_000, 200, 0);
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> machineItems);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> machineEnergy);
    private boolean energyEnabled = true;
    private boolean itemsEnabled = true;
    @Nullable private Direction deniedItemSide;

    ReplaceableCapabilityChest(BlockPos pos) {
      super(pos, Blocks.CHEST.defaultBlockState());
    }

    ItemStackHandler inventoryHandler() {
      return machineItems;
    }

    void replaceItems(ItemStackHandler next) {
      itemCapability.invalidate();
      machineItems = next;
      itemCapability = LazyOptional.of(() -> machineItems);
    }

    void replaceEnergy(IEnergyStorage next) {
      energyCapability.invalidate();
      machineEnergy = next;
      energyEnabled = true;
      energyCapability = LazyOptional.of(() -> machineEnergy);
    }

    void disableEnergy() {
      energyEnabled = false;
      energyCapability.invalidate();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
      if (capability == ForgeCapabilities.ITEM_HANDLER)
        return !itemsEnabled || (deniedItemSide != null && side == deniedItemSide)
            ? LazyOptional.empty()
            : itemCapability.cast();
      if (capability == ForgeCapabilities.ENERGY)
        return energyEnabled ? energyCapability.cast() : LazyOptional.empty();
      return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
      super.invalidateCaps();
      itemCapability.invalidate();
      energyCapability.invalidate();
    }
  }

  private static final class ForcedCapabilityChest extends ReplaceableCapabilityChest
      implements LogisticsInventoryAccess {
    ForcedCapabilityChest(BlockPos pos) {
      super(pos);
    }

    @Override
    public IItemHandler logisticsInventory() {
      return inventoryHandler();
    }
  }

  private static ReplaceableCapabilityChest installCapabilityChest(
      ServerLevel level, BlockPos pos) {
    level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 18);
    ReplaceableCapabilityChest chest = new ReplaceableCapabilityChest(pos);
    level.setBlockEntity(chest);
    return chest;
  }

  private static LogisticsControllerBlockEntity setUpCapabilityLogisticsRoute(
      ServerLevel level, BlockPos pos) {
    level.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
    level.setBlock(pos.east(), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
    level.setBlock(pos.east(2), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
    return (LogisticsControllerBlockEntity) level.getBlockEntity(pos);
  }

  private static void configureCapabilityLogisticsRoute(
      LogisticsControllerBlockEntity controller,
      BlockPos source,
      BlockPos receiver,
      boolean forced) {
    CompoundTag saved = new CompoundTag();
    ListTag configs = new ListTag();
    CompoundTag output = logisticsEndpoint(source, Direction.SOUTH, false, true);
    output.putBoolean("Forced", forced);
    configs.add(output);
    configs.add(logisticsEndpoint(receiver, Direction.NORTH, true, false));
    saved.put("Configurations", configs);
    controller.load(saved);
    controller.refreshNetwork();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesLegacyContainerLogisticsFallback(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    try {
      LogisticsControllerBlockEntity controller = setUpCapabilityLogisticsRoute(level, pos);
      ReplaceableCapabilityChest source = installCapabilityChest(level, pos.east().north());
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, pos.east(2).south());
      source.itemsEnabled = false;
      source.setItem(0, new ItemStack(Items.COAL, 8));
      try {
        configureCapabilityLogisticsRoute(
            controller, source.getBlockPos(), receiver.getBlockPos(), false);
        helper.assertTrue(
            controller.endpointInfos().size() == 2,
            "A legacy Container with no item capability on any face must remain discoverable");
        tickLogistics(helper, controller);
        helper.assertTrue(
            source.getItem(0).isEmpty() && receiver.machineItems.getStackInSlot(0).getCount() == 8,
            "Normal logistics must retain its fallback for legacy Containers with no publisher");
      } finally {
        source.clearContent();
        receiver.clearContent();
      }
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
      level.removeBlock(pos, false);
      level.removeBlock(pos.east().north(), false);
      level.removeBlock(pos.east(2).south(), false);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesExplicitForcedLogisticsAccess(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    try {
      LogisticsControllerBlockEntity controller = setUpCapabilityLogisticsRoute(level, pos);
      BlockPos sourcePos = pos.east().north();
      level.setBlock(sourcePos, ModBlocks.COAL_GENERATOR.get().defaultBlockState(), 18);
      CoalGeneratorBlockEntity source = (CoalGeneratorBlockEntity) level.getBlockEntity(sourcePos);
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, pos.east(2).south());
      source.items().setStackInSlot(0, new ItemStack(Items.COAL, 16));
      try {
        configureCapabilityLogisticsRoute(controller, sourcePos, receiver.getBlockPos(), false);
        tickLogistics(helper, controller);
        helper.assertTrue(
            source.items().getStackInSlot(0).getCount() == 16
                && receiver.machineItems.getStackInSlot(0).isEmpty(),
            "Normal logistics must respect the generator's locked fuel extraction");
        configureCapabilityLogisticsRoute(controller, sourcePos, receiver.getBlockPos(), true);
        tickLogistics(helper, controller);
        helper.assertTrue(
            source.items().getStackInSlot(0).getCount() == 8
                && receiver.machineItems.getStackInSlot(0).getCount() == 8,
            "Explicit forced mode must retain complete LogisticsInventoryAccess extraction");
        source.items().setStackInSlot(0, ItemStack.EMPTY);
        level.setBlock(sourcePos, Blocks.CHEST.defaultBlockState(), 18);
        ForcedCapabilityChest forcedSource = new ForcedCapabilityChest(sourcePos);
        level.setBlockEntity(forcedSource);
        forcedSource.inventoryHandler().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        ((ReplaceableCapabilityChest) forcedSource).deniedItemSide = Direction.SOUTH;
        configureCapabilityLogisticsRoute(controller, sourcePos, receiver.getBlockPos(), true);
        tickLogistics(helper, controller);
        helper.assertTrue(
            forcedSource.inventoryHandler().getStackInSlot(0).isEmpty()
                && receiver.machineItems.getStackInSlot(0).getCount() == 16,
            "An explicit forced publisher must stay discoverable and extractable even when its"
                + " normal pipe-facing capability is absent");
      } finally {
        source.items().setStackInSlot(0, ItemStack.EMPTY);
        receiver.clearContent();
      }
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
      level.removeBlock(pos, false);
      level.removeBlock(pos.east().north(), false);
      level.removeBlock(pos.east(2).south(), false);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void respectsDirectionalCapabilityDenialsInNormalLogistics(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    try {
      level.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
      level.setBlock(pos.east(), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      level.setBlock(pos.east(2), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      ReplaceableCapabilityChest source = installCapabilityChest(level, pos.east().north());
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, pos.east(2).south());
      source.machineItems.setStackInSlot(0, new ItemStack(Items.COAL, 16));
      LogisticsControllerBlockEntity controller =
          (LogisticsControllerBlockEntity) level.getBlockEntity(pos);
      CompoundTag saved = new CompoundTag();
      ListTag configs = new ListTag();
      configs.add(logisticsEndpoint(source.getBlockPos(), Direction.SOUTH, false, true));
      configs.add(logisticsEndpoint(receiver.getBlockPos(), Direction.NORTH, true, false));
      saved.put("Configurations", configs);
      controller.load(saved);
      controller.refreshNetwork();
      LogisticsFixture fixture = new LogisticsFixture(controller, source, receiver, pos);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          source.machineItems.getStackInSlot(0).getCount() == 8
              && receiver.machineItems.getStackInSlot(0).getCount() == 8,
          "The directional provider must transfer through its initially available side: "
              + logisticsFixtureDiagnostic(helper, fixture));
      source.setItem(0, new ItemStack(Items.COAL, 64));
      try {
        source.deniedItemSide = Direction.SOUTH;
        source.replaceItems(source.machineItems);
        tickLogistics(helper, fixture);
        helper.assertTrue(
            source.getItem(0).getCount() == 64
                && source.machineItems.getStackInSlot(0).getCount() == 8
                && receiver.machineItems.getStackInSlot(0).getCount() == 8,
            "Normal extraction must respect an empty pipe-facing capability instead of accessing"
                + " the provider's raw Container");
        source.deniedItemSide = null;
        source.replaceItems(source.machineItems);
        tickLogistics(helper, fixture);
        helper.assertTrue(
            source.machineItems.getStackInSlot(0).isEmpty()
                && receiver.machineItems.getStackInSlot(0).getCount() == 16,
            "Restoring a source side must restore only its published capability inventory");
        source.machineItems.setStackInSlot(0, new ItemStack(Items.COAL, 8));
        receiver.deniedItemSide = Direction.NORTH;
        receiver.replaceItems(receiver.machineItems);
        tickLogistics(helper, fixture);
        helper.assertTrue(
            source.machineItems.getStackInSlot(0).getCount() == 8
                && receiver.machineItems.getStackInSlot(0).getCount() == 16
                && receiver.getItem(0).isEmpty(),
            "Normal insertion must respect a denied pipe-facing capability without using the raw"
                + " Container");
        receiver.deniedItemSide = null;
        receiver.replaceItems(receiver.machineItems);
        tickLogistics(helper, fixture);
        helper.assertTrue(
            source.machineItems.getStackInSlot(0).isEmpty()
                && receiver.machineItems.getStackInSlot(0).getCount() == 24
                && source.getItem(0).getCount() == 64
                && receiver.getItem(0).isEmpty(),
            "Restoring a destination side must preserve each published and raw inventory");
      } finally {
        source.clearContent();
        receiver.clearContent();
      }
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
      level.removeBlock(pos, false);
      level.removeBlock(pos.east().north(), false);
      level.removeBlock(pos.east(2).south(), false);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void resolvesReplacedAndDisabledLogisticsCapabilities(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    int originalScan = SiliconicConfig.VALUES.logisticsScanInterval.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    // All transfers run synchronously at one game time. Avoid the periodic refresh slot so this
    // verifies resolution through the same cached endpoint descriptions after capability changes.
    for (int interval = 1_200; interval >= 5; interval--)
      if (Math.floorMod(level.getGameTime(), interval) != Math.floorMod(pos.asLong(), interval)) {
        SiliconicConfig.VALUES.logisticsScanInterval.set(interval);
        break;
      }
    try {
      level.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
      level.setBlock(pos.east(), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      level.setBlock(pos.east(2), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      ReplaceableCapabilityChest source = installCapabilityChest(level, pos.east().north());
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, pos.east(2).south());
      source.machineItems.setStackInSlot(0, new ItemStack(Items.COAL, 24));
      LogisticsControllerBlockEntity controller =
          (LogisticsControllerBlockEntity) level.getBlockEntity(pos);
      CompoundTag saved = new CompoundTag();
      ListTag configs = new ListTag();
      configs.add(logisticsEndpoint(source.getBlockPos(), Direction.SOUTH, false, true));
      configs.add(logisticsEndpoint(receiver.getBlockPos(), Direction.NORTH, true, false));
      saved.put("Configurations", configs);
      controller.load(saved);
      controller.refreshNetwork();
      LogisticsFixture fixture = new LogisticsFixture(controller, source, receiver, pos);
      var cachedEndpoints = controller.endpointInfos();
      helper.assertTrue(
          cachedEndpoints.size() == 2, "Both programmable chest endpoints must exist");
      tickLogistics(helper, fixture);
      ItemStackHandler firstReceiver = receiver.machineItems;
      helper.assertTrue(
          source.machineItems.getStackInSlot(0).getCount() == 16
              && firstReceiver.getStackInSlot(0).getCount() == 8,
          "The original handler must receive the initial transfer");
      receiver.replaceItems(new ItemStackHandler(1));
      tickLogistics(helper, fixture);
      ItemStackHandler secondReceiver = receiver.machineItems;
      helper.assertTrue(
          source.machineItems.getStackInSlot(0).getCount() == 8
              && firstReceiver.getStackInSlot(0).getCount() == 8
              && secondReceiver.getStackInSlot(0).getCount() == 8,
          "Replacing a capability must use its new handler without mutating the invalid old one");
      receiver.replaceItems(new ItemStackHandler(0));
      tickLogistics(helper, fixture);
      helper.assertTrue(
          source.machineItems.getStackInSlot(0).getCount() == 8
              && bufferedLogisticsItems(fixture) == 0
              && controller.endpointInfos().equals(cachedEndpoints),
          "A zero-slot destination must stop extraction through the existing cached route");
      receiver.replaceItems(new ItemStackHandler(1));
      tickLogistics(helper, fixture);
      helper.assertTrue(
          source.machineItems.getStackInSlot(0).isEmpty()
              && receiver.machineItems.getStackInSlot(0).getCount() == 8
              && firstReceiver.getStackInSlot(0).getCount() == 8
              && secondReceiver.getStackInSlot(0).getCount() == 8,
          "Re-enabling the destination must deliver the remaining batch once before a rescan");
      ItemStackHandler replacedSource = new ItemStackHandler(1);
      replacedSource.setStackInSlot(0, new ItemStack(Items.COAL, 8));
      source.setItem(0, new ItemStack(Items.COAL, 64));
      try {
        source.replaceItems(new ItemStackHandler(0));
        tickLogistics(helper, fixture);
        helper.assertTrue(
            receiver.machineItems.getStackInSlot(0).getCount() == 8
                && source.getItem(0).getCount() == 64,
            "A disabled source capability must not extract through the container fallback");
      } finally {
        source.clearContent();
      }
      source.replaceItems(replacedSource);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          replacedSource.getStackInSlot(0).isEmpty()
              && receiver.machineItems.getStackInSlot(0).getCount() == 16
              && controller.endpointInfos().equals(cachedEndpoints),
          "A replacement source handler must become usable through its cached endpoint");
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
      SiliconicConfig.VALUES.logisticsScanInterval.set(originalScan);
      level.removeBlock(pos, false);
      level.removeBlock(pos.east().north(), false);
      level.removeBlock(pos.east(2).south(), false);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void discoversReceiverCapabilitiesEnabledAfterCablePlacement(
      GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    BlockPos receiverPos = pos.east(3);
    BlockPos blockedReceiverPos = pos.east(2).above();
    try {
      level.setBlock(pos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState(), 18);
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, receiverPos);
      ReplaceableCapabilityChest blockedReceiver =
          installCapabilityChest(level, blockedReceiverPos);
      receiver.disableEnergy();
      PowerCableBlock cable = (PowerCableBlock) ModBlocks.POWER_CABLE.get();
      for (int offset = 1; offset <= 2; offset++) {
        BlockPos cablePos = pos.east(offset);
        level.setBlock(cablePos.below(), Blocks.STONE.defaultBlockState(), 18);
        level.setBlock(cablePos, cable.defaultBlockState(), 18);
      }
      for (int offset = 1; offset <= 2; offset++) {
        BlockPos cablePos = pos.east(offset);
        cable.tick(level.getBlockState(cablePos), level, cablePos, level.random);
      }
      EnergyBufferBlockEntity source = (EnergyBufferBlockEntity) level.getBlockEntity(pos);
      CompoundTag saved = new CompoundTag();
      saved.putInt("Energy", 10_000);
      source.load(saved);
      tickPowerSource(level, pos, source);
      helper.assertTrue(
          !level.getBlockState(pos.east(2)).getValue(PowerCableBlock.EAST)
              && receiver.machineEnergy.getEnergyStored() == 0,
          "A receiver disabled during placement must start with no visible cable arm or delivery");
      receiver.replaceEnergy(new EnergyStorage(1_000, 200, 0));
      // Rebuild through the same discovery path as cache expiry, without a world/block update.
      source.invalidateNetworkCache();
      tickPowerSource(level, pos, source);
      helper.assertTrue(
          receiver.machineEnergy.getEnergyStored() > 0
              && blockedReceiver.machineEnergy.getEnergyStored() == 0,
          "Refreshing a network must discover a receiver capability enabled after cable placement");
      helper.succeed();
    } finally {
      level.removeBlock(pos, false);
      level.removeBlock(receiverPos, false);
      level.removeBlock(blockedReceiverPos, false);
    }
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void resolvesReplacedAndDisabledPowerCapabilities(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(ORIGIN);
    BlockPos receiverPos = pos.east(3);
    int originalCache = SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
    int originalConnectionLimit = SiliconicConfig.VALUES.powerTransferPerConnection.get();
    int originalOutput = SiliconicConfig.VALUES.powerBufferOutputPerTick.get();
    SiliconicConfig.VALUES.powerNetworkCacheTicks.set(1_200);
    SiliconicConfig.VALUES.powerTransferPerConnection.set(200);
    SiliconicConfig.VALUES.powerBufferOutputPerTick.set(800);
    try {
      level.setBlock(pos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState(), 18);
      ReplaceableCapabilityChest receiver = installCapabilityChest(level, receiverPos);
      PowerCableBlock cable = (PowerCableBlock) ModBlocks.POWER_CABLE.get();
      for (int offset = 1; offset <= 2; offset++) {
        BlockPos cablePos = pos.east(offset);
        level.setBlock(cablePos.below(), Blocks.STONE.defaultBlockState(), 18);
        level.setBlock(cablePos, cable.defaultBlockState(), 18);
      }
      for (int offset = 1; offset <= 2; offset++) {
        BlockPos cablePos = pos.east(offset);
        cable.tick(level.getBlockState(cablePos), level, cablePos, level.random);
      }
      EnergyBufferBlockEntity source = (EnergyBufferBlockEntity) level.getBlockEntity(pos);
      CompoundTag saved = new CompoundTag();
      saved.putInt("Energy", 10_000);
      source.load(saved);
      tickPowerSource(level, pos, source);
      IEnergyStorage firstReceiver = receiver.machineEnergy;
      int firstAccepted = firstReceiver.getEnergyStored();
      helper.assertTrue(firstAccepted > 0, "The original receiver capability must accept power");
      receiver.replaceEnergy(new EnergyStorage(1_000, 200, 0));
      tickPowerSource(level, pos, source);
      IEnergyStorage secondReceiver = receiver.machineEnergy;
      helper.assertTrue(
          firstReceiver.getEnergyStored() == firstAccepted && secondReceiver.getEnergyStored() > 0,
          "Replacing the energy capability must leave its invalid old storage unchanged");
      int beforeDisabled = source.energyStored();
      receiver.disableEnergy();
      tickPowerSource(level, pos, source);
      helper.assertTrue(
          source.energyStored() == beforeDisabled,
          "An absent receiver capability must stop cached power delivery immediately");
      receiver.replaceEnergy(new EnergyStorage(1_000, 0, 0));
      tickPowerSource(level, pos, source);
      helper.assertTrue(
          source.energyStored() == beforeDisabled,
          "A present capability with canReceive false must also stop cached delivery");
      receiver.replaceEnergy(
          new EnergyStorage(1_000, 200, 0) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
              return super.receiveEnergy(Math.min(amount, simulate ? 200 : 37), simulate);
            }
          });
      tickPowerSource(level, pos, source);
      helper.assertTrue(
          receiver.machineEnergy.getEnergyStored() == 37
              && source.energyStored() == beforeDisabled - 37
              && firstReceiver.getEnergyStored() == firstAccepted,
          "A restored receiver accepting less than its simulation must consume exactly accepted"
              + " energy before a fallback scan");
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.powerNetworkCacheTicks.set(originalCache);
      SiliconicConfig.VALUES.powerTransferPerConnection.set(originalConnectionLimit);
      SiliconicConfig.VALUES.powerBufferOutputPerTick.set(originalOutput);
      level.removeBlock(pos, false);
      level.removeBlock(receiverPos, false);
    }
  }

  private static LogisticsFixture partialLogisticsFixture(GameTestHelper helper) {
    BlockPos relative = ORIGIN;
    helper.setBlock(relative, ModBlocks.LOGISTICS_CONTROLLER.get());
    helper.setBlock(relative.east(), ModBlocks.LOGISTICS_PIPE.get());
    helper.setBlock(relative.east(2), ModBlocks.LOGISTICS_PIPE.get());
    helper.setBlock(relative.east().north(), Blocks.CHEST);
    helper.setBlock(relative.east(2).south(), Blocks.CHEST);
    BlockPos pos = helper.absolutePos(relative);
    LogisticsControllerBlockEntity controller =
        (LogisticsControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
    ChestBlockEntity source =
        (ChestBlockEntity) helper.getLevel().getBlockEntity(pos.east().north());
    ChestBlockEntity receiver =
        (ChestBlockEntity) helper.getLevel().getBlockEntity(pos.east(2).south());
    for (int slot = 0; slot < source.getContainerSize(); slot++)
      source.setItem(slot, ItemStack.EMPTY);
    source.setItem(0, new ItemStack(Items.COAL, 64));
    for (int slot = 0; slot < receiver.getContainerSize(); slot++)
      receiver.setItem(slot, new ItemStack(Items.STONE, 64));
    receiver.setItem(0, new ItemStack(Items.COAL, 63));
    ListTag configurations = new ListTag();
    CompoundTag output = logisticsEndpoint(source.getBlockPos(), Direction.SOUTH, false, true);
    output.put("Filter", new ItemStack(Items.COAL).save(new CompoundTag()));
    configurations.add(output);
    CompoundTag input = logisticsEndpoint(receiver.getBlockPos(), Direction.NORTH, true, false);
    input.put("Filter", new ItemStack(Items.COAL).save(new CompoundTag()));
    configurations.add(input);
    CompoundTag saved = new CompoundTag();
    saved.put("Configurations", configurations);
    controller.load(saved);
    controller.refreshNetwork();
    LogisticsFixture fixture = new LogisticsFixture(controller, source, receiver, pos);
    helper.assertTrue(
        controller.endpointInfos().size() == 2,
        "The partial-delivery fixture must discover both configured chest interfaces: "
            + logisticsFixtureDiagnostic(helper, fixture));
    return fixture;
  }

  private static void tickLogistics(GameTestHelper helper, LogisticsFixture fixture) {
    tickLogistics(helper, fixture.controller());
  }

  private static void tickLogistics(
      GameTestHelper helper, LogisticsControllerBlockEntity controller) {
    LogisticsControllerBlockEntity.serverTick(
        helper.getLevel(), controller.getBlockPos(), controller.getBlockState(), controller);
  }

  private static int bufferedLogisticsItems(LogisticsFixture fixture) {
    return ItemStack.of(fixture.controller().saveWithoutMetadata().getCompound("TransferBuffer"))
        .getCount();
  }

  private static String logisticsFixtureDiagnostic(
      GameTestHelper helper, LogisticsFixture fixture) {
    return "pos="
        + fixture.pos()
        + ", time="
        + helper.getLevel().getGameTime()
        + ", interval="
        + SiliconicConfig.VALUES.logisticsTransferInterval.get()
        + ", limit="
        + SiliconicConfig.VALUES.logisticsTransferLimit.get()
        + ", endpoints="
        + fixture.controller().endpointInfos()
        + ", sourceLoaded="
        + helper.getLevel().isLoaded(fixture.source().getBlockPos())
        + ", receiverLoaded="
        + helper.getLevel().isLoaded(fixture.receiver().getBlockPos())
        + ", sourceRemoved="
        + fixture.source().isRemoved()
        + ", receiverRemoved="
        + fixture.receiver().isRemoved()
        + ", extractable="
        + fixture
            .source()
            .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH)
            .map(handler -> handler.extractItem(0, 8, true).getCount())
            .orElse(-1)
        + ", insertRemainder="
        + fixture
            .receiver()
            .getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
            .map(handler -> handler.insertItem(0, new ItemStack(Items.COAL, 8), true).getCount())
            .orElse(-1);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesLogisticsPageSelectionAcrossWire(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pos = new BlockPos(1_002_400, helper.absolutePos(BlockPos.ZERO).getY(), 1_002_400);
    level.setBlock(pos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
    for (int index = 1; index <= LogisticsControllerBlockEntity.MAX_ENDPOINTS; index++) {
      level.setBlock(pos.east(index), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      level.setBlock(pos.east(index).above(), Blocks.CHEST.defaultBlockState(), 18);
    }
    LogisticsControllerBlockEntity controller =
        (LogisticsControllerBlockEntity) level.getBlockEntity(pos);
    controller.refreshNetwork();
    helper.assertTrue(
        controller.endpointInfos().size() == LogisticsControllerBlockEntity.MAX_ENDPOINTS,
        "The fixture must discover all 128 endpoints for the final search pages");
    FakePlayer player =
        new FakePlayer(level, new GameProfile(UUID.randomUUID(), "logistics_navigator"));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    LogisticsControllerMenu menu =
        new LogisticsControllerMenu(
            7, player.getInventory(), controller, controller.endpointInfos());
    player.containerMenu = menu;
    helper.assertTrue(menu.pageCount() == 26, "The maximum endpoint list must have 26 pages");
    CompoundTag before = controller.saveWithoutMetadata();
    for (int page : new int[] {24, 25}) {
      FriendlyByteBuf vanillaWire = new FriendlyByteBuf(Unpooled.buffer());
      try {
        new ServerboundContainerButtonClickPacket(menu.containerId, 1_000 + page)
            .write(vanillaWire);
        ServerboundContainerButtonClickPacket truncated =
            new ServerboundContainerButtonClickPacket(vanillaWire);
        helper.assertTrue(
            truncated.getButtonId() == page - 24,
            "Vanilla's byte-sized page commands must reproduce the previous/next collision");
      } finally {
        vanillaWire.release();
      }
    }
    for (int page : new int[] {0, 24, 25}) {
      LogisticsPagePacket requested = new LogisticsPagePacket(pos, menu.containerId, page);
      FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
      try {
        LogisticsPagePacket.encode(requested, wire);
        LogisticsPagePacket decoded = LogisticsPagePacket.decode(wire);
        helper.assertTrue(
            decoded.equals(requested)
                && wire.readableBytes() == 0
                && decoded.apply(player)
                && menu.page() == page,
            "Dedicated logistics page packets must preserve and select the requested page");
      } finally {
        wire.release();
      }
    }
    helper.assertTrue(
        controller.saveWithoutMetadata().equals(before),
        "Search page navigation must not change any endpoint mode, filter, or stored items");
    helper.assertTrue(
        !new LogisticsPagePacket(pos, menu.containerId + 1, 0).apply(player)
            && !new LogisticsPagePacket(pos.east(), menu.containerId, 0).apply(player)
            && !new LogisticsPagePacket(pos, menu.containerId, -1).apply(player)
            && !new LogisticsPagePacket(pos, menu.containerId, menu.pageCount()).apply(player)
            && !new LogisticsPagePacket(pos, menu.containerId, Integer.MAX_VALUE).apply(player)
            && menu.page() == 25,
        "Wrong owners, containers, and invalid pages must preserve the current search page");
    player.containerMenu = player.inventoryMenu;
    helper.assertTrue(
        !new LogisticsPagePacket(pos, menu.containerId, 0).apply(player),
        "Page packets must not target a menu that the sender has already closed");
    player.containerMenu = menu;
    player.setGameMode(GameType.SPECTATOR);
    player.containerMenu = menu;
    helper.assertTrue(
        menu.stillValid(player) && !new LogisticsPagePacket(pos, menu.containerId, 0).apply(player),
        "A spectator inspecting a valid controller must not send page mutation packets");
    player.setGameMode(GameType.SURVIVAL);
    player.containerMenu = menu;
    level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 18);
    controller.refreshNetwork();
    helper.assertTrue(
        !new LogisticsPagePacket(pos, menu.containerId, 0).apply(player) && menu.page() == 25,
        "A stale endpoint list must reject page selection without changing the previous page");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesPartialLogisticsDeliveryAcrossReload(GameTestHelper helper) {
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    try {
      LogisticsFixture fixture = partialLogisticsFixture(helper);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          fixture.source().getItem(0).getCount() == 56
              && fixture.receiver().getItem(0).getCount() == 64
              && bufferedLogisticsItems(fixture) == 7,
          "A partial transfer must retain the seven items that do not fit in its destination");
      CompoundTag saved = fixture.controller().saveWithoutMetadata();
      fixture.controller().load(saved);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          fixture.source().getItem(0).getCount() == 56 && bufferedLogisticsItems(fixture) == 7,
          "Restored buffered items must wait without extracting a second batch");
      ItemStack recovered = fixture.receiver().removeItem(0, 7);
      tickLogistics(helper, fixture);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          recovered.getCount() == 7
              && fixture.source().getItem(0).getCount() == 56
              && fixture.receiver().getItem(0).getCount() == 64
              && bufferedLogisticsItems(fixture) == 0,
          "A restored partial transfer must deliver each buffered item once when space returns");
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
    }
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_logistics_drops")
  public static void dropsBufferedLogisticsAndFiltersOnce(GameTestHelper helper) {
    awaitInfrastructureTicking(
        helper, List.of(helper.absolutePos(ORIGIN)), 40, () -> checkBufferedLogisticsDrops(helper));
  }

  private static void checkBufferedLogisticsDrops(GameTestHelper helper) {
    int originalInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    int originalLimit = SiliconicConfig.VALUES.logisticsTransferLimit.get();
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    SiliconicConfig.VALUES.logisticsTransferLimit.set(8);
    try {
      LogisticsFixture fixture = partialLogisticsFixture(helper);
      tickLogistics(helper, fixture);
      helper.assertTrue(
          fixture.source().getItem(0).getCount() == 56
              && fixture.receiver().getItem(0).getCount() == 64
              && bufferedLogisticsItems(fixture) == 7,
          "The drop fixture must initialize its seven buffered items before removal: "
              + logisticsFixtureDiagnostic(helper, fixture));
      fixture.controller().load(fixture.controller().saveWithoutMetadata());
      AABB dropBounds = new AABB(fixture.pos()).inflate(1);
      Set<UUID> previousDrops =
          helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropBounds).stream()
              .map(ItemEntity::getUUID)
              .collect(Collectors.toSet());
      helper.assertTrue(
          helper.getLevel().areEntitiesLoaded(ChunkPos.asLong(fixture.pos())),
          "The drop collector's entity section must be visible before removing the controller");
      helper.getLevel().removeBlock(fixture.pos(), false);
      helper.getLevel().removeBlock(fixture.pos(), false);
      int dropped =
          helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropBounds).stream()
              .filter(entity -> !previousDrops.contains(entity.getUUID()))
              .filter(entity -> entity.getItem().is(Items.COAL))
              .mapToInt(entity -> entity.getItem().getCount())
              .sum();
      helper.assertTrue(
          dropped == 9
              && fixture.source().getItem(0).getCount() == 56
              && fixture.receiver().getItem(0).getCount() == 64,
          "Removing the controller must drop its seven buffered items and two real filters once:"
              + " dropped="
              + dropped
              + ", source="
              + fixture.source().getItem(0).getCount()
              + ", receiver="
              + fixture.receiver().getItem(0).getCount()
              + ", entitiesLoaded="
              + helper.getLevel().areEntitiesLoaded(ChunkPos.asLong(fixture.pos())));
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsTransferInterval.set(originalInterval);
      SiliconicConfig.VALUES.logisticsTransferLimit.set(originalLimit);
    }
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_menu_availability",
      timeoutTicks = 100)
  public static void rejectsInfrastructureMenusWithoutAvailableOwners(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos origin = new BlockPos(1_001_128, helper.absolutePos(BlockPos.ZERO).getY(), 1_001_128);
    ChunkPos chunk = new ChunkPos(origin);
    ChunkPos anchor = new ChunkPos(origin.east(32));
    level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, anchor, 0, anchor);
    level.getChunkAt(origin.east(32));
    level.setBlock(origin, ModBlocks.COAL_GENERATOR.get().defaultBlockState(), 18);
    level.setBlock(origin.east(2), ModBlocks.CONDITIONER.get().defaultBlockState(), 18);
    level.setBlock(origin.south(2), ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
    CoalGeneratorBlockEntity generator = (CoalGeneratorBlockEntity) level.getBlockEntity(origin);
    ConditionerBlockEntity conditioner =
        (ConditionerBlockEntity) level.getBlockEntity(origin.east(2));
    LogisticsControllerBlockEntity controller =
        (LogisticsControllerBlockEntity) level.getBlockEntity(origin.south(2));
    Player player = helper.makeMockPlayer();
    player.setPos(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
    List<AbstractContainerMenu> menus =
        List.of(
            new CoalGeneratorMenu(0, player.getInventory(), generator),
            new ConditionerMenu(1, player.getInventory(), conditioner),
            new LogisticsControllerMenu(2, player.getInventory(), controller, List.of()));
    helper.runAfterDelay(
        2,
        () -> {
          helper.assertTrue(
              level.isLoaded(origin), "The menu owner chunk must initially be available");
          for (AbstractContainerMenu menu : menus)
            helper.assertTrue(
                menu.stillValid(player), "The available owner must keep its menu valid");
          level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
          helper.runAfterDelay(
              20,
              () -> {
                try {
                  helper.assertTrue(
                      !level.isLoaded(origin) && !generator.isRemoved(),
                      "The menu owner must lose FULL availability before final removal: loaded="
                          + level.isLoaded(origin)
                          + ", removed="
                          + generator.isRemoved());
                  for (AbstractContainerMenu menu : menus)
                    helper.assertTrue(
                        !menu.stillValid(player),
                        menu.getClass().getSimpleName() + " must reject its unavailable owner");
                  helper.assertTrue(
                      !level.isLoaded(origin), "Menu validation must not reload its owner");
                  helper.succeed();
                } finally {
                  level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
                  level.getChunkSource().removeRegionTicket(TicketType.FORCED, anchor, 0, anchor);
                }
              });
        });
  }

  private record GeneratorMenuFixture(
      CoalGeneratorBlockEntity generator, CoalGeneratorMenu menu, Player player, BlockPos pos) {}

  private static GeneratorMenuFixture generatorMenuFixture(GameTestHelper helper, int fuel) {
    helper.setBlock(ORIGIN, ModBlocks.COAL_GENERATOR.get());
    BlockPos pos = helper.absolutePos(ORIGIN);
    CoalGeneratorBlockEntity generator =
        (CoalGeneratorBlockEntity) helper.getLevel().getBlockEntity(pos);
    CompoundTag saved = new CompoundTag();
    saved.putInt("Energy", CoalGeneratorBlockEntity.ENERGY_CAPACITY);
    generator.load(saved);
    generator.items().setStackInSlot(0, new ItemStack(Items.COAL, fuel));
    Player player = helper.makeMockPlayer();
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    CoalGeneratorMenu menu = new CoalGeneratorMenu(0, player.getInventory(), generator);
    player.containerMenu = menu;
    return new GeneratorMenuFixture(generator, menu, player, pos);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void persistsMergedGeneratorFuel(GameTestHelper helper) {
    GeneratorMenuFixture fixture = generatorMenuFixture(helper, 10);
    fixture.player().getInventory().setItem(9, new ItemStack(Items.COAL, 64));
    helper.getLevel().getChunkAt(fixture.pos()).setUnsaved(false);
    fixture.menu().quickMoveStack(fixture.player(), 1);
    helper.assertTrue(
        fixture.generator().items().getStackInSlot(0).getCount() == 64
            && fixture.player().getInventory().getItem(9).getCount() == 10,
        "Shift-click must merge fuel into the generator without changing the item total");
    helper.assertTrue(
        helper.getLevel().getChunkAt(fixture.pos()).isUnsaved(),
        "A fuel merge must mark even an idle full generator for saving");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void persistsPartiallyExtractedGeneratorFuel(GameTestHelper helper) {
    GeneratorMenuFixture fixture = generatorMenuFixture(helper, 64);
    for (int slot = 0; slot < 36; slot++)
      fixture.player().getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
    fixture.player().getInventory().setItem(9, new ItemStack(Items.COAL, 63));
    helper.getLevel().getChunkAt(fixture.pos()).setUnsaved(false);
    fixture.menu().quickMoveStack(fixture.player(), 0);
    helper.assertTrue(
        fixture.generator().items().getStackInSlot(0).getCount() == 63
            && fixture.player().getInventory().getItem(9).getCount() == 64,
        "Partial fuel recovery must preserve the item total when the player inventory is almost"
            + " full");
    helper.assertTrue(
        helper.getLevel().getChunkAt(fixture.pos()).isUnsaved(),
        "Partial fuel recovery must mark even an idle full generator for saving");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void revivesInfrastructureCapabilitiesWithoutLosingState(GameTestHelper helper) {
    List<BlockEntity> machines =
        List.of(
            new CoalGeneratorBlockEntity(
                BlockPos.ZERO, ModBlocks.COAL_GENERATOR.get().defaultBlockState()),
            new EnergyBufferBlockEntity(
                BlockPos.ZERO, ModBlocks.ENERGY_BUFFER.get().defaultBlockState()),
            new ConditionerBlockEntity(
                BlockPos.ZERO, ModBlocks.CONDITIONER.get().defaultBlockState()));
    for (BlockEntity machine : machines) {
      CompoundTag saved = new CompoundTag();
      saved.putInt("Energy", 1_000);
      machine.load(saved);
      if (machine instanceof CoalGeneratorBlockEntity generator)
        generator.items().setStackInSlot(0, new ItemStack(Items.COAL, 4));
      LazyOptional<IEnergyStorage> previousEnergy = machine.getCapability(ForgeCapabilities.ENERGY);
      LazyOptional<IItemHandler> previousItems =
          machine.getCapability(ForgeCapabilities.ITEM_HANDLER);
      helper.assertTrue(
          previousEnergy.isPresent(), "Infrastructure must initially expose its energy");
      machine.invalidateCaps();
      helper.assertTrue(
          !previousEnergy.isPresent()
              && !previousItems.isPresent()
              && !machine.getCapability(ForgeCapabilities.ENERGY).isPresent(),
          "Invalidating infrastructure must release previously exposed capabilities");
      machine.reviveCaps();
      IEnergyStorage restored =
          machine
              .getCapability(ForgeCapabilities.ENERGY)
              .orElseThrow(() -> new IllegalStateException("Revived energy capability missing"));
      helper.assertTrue(
          restored.getEnergyStored() == 1_000 && !previousEnergy.isPresent(),
          "Revival must preserve energy without making an invalid capability live again");
      if (machine instanceof CoalGeneratorBlockEntity) {
        IItemHandler restoredItems =
            machine
                .getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(() -> new IllegalStateException("Revived generator items missing"));
        helper.assertTrue(
            restoredItems.getStackInSlot(0).is(Items.COAL)
                && restoredItems.getStackInSlot(0).getCount() == 4
                && !previousItems.isPresent(),
            "Revival must preserve generator fuel and replace its invalid inventory capability");
      }
    }
    helper.succeed();
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_large_logistics",
      timeoutTicks = 100)
  public static void boundsLargeLogisticsDiscovery(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos base = new BlockPos(1_000_960, level.getMinBuildHeight() + 16, 1_000_960);
    ChunkPos chunk = new ChunkPos(base);
    int originalLimit = SiliconicConfig.VALUES.logisticsMaxPipes.get();
    level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
    try {
      for (int y = 0; y < 256; y++)
        for (int x = 0; x < 16; x++)
          for (int z = 0; z < 16; z++)
            level.setBlock(
                base.offset(x, y, z), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 18);
      BlockPos controllerPos = base.offset(8, 128, 7);
      BlockPos bottomInventory = base.offset(8, 0, 8);
      BlockPos topInventory = base.offset(8, 255, 8);
      level.setBlock(controllerPos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
      level.setBlock(bottomInventory, Blocks.CHEST.defaultBlockState(), 18);
      level.setBlock(topInventory, Blocks.CHEST.defaultBlockState(), 18);
      LogisticsControllerBlockEntity controller =
          (LogisticsControllerBlockEntity) level.getBlockEntity(controllerPos);
      SiliconicConfig.VALUES.logisticsMaxPipes.set(64);
      controller.refreshNetwork();
      helper.assertTrue(
          controller.endpointInfos().isEmpty(),
          "A limited scan must stop before discovering inventories more than one hundred pipes"
              + " away");
      SiliconicConfig.VALUES.logisticsMaxPipes.set(65_536);
      long scanStarted = System.nanoTime();
      controller.refreshNetwork();
      long elapsedMicros = (System.nanoTime() - scanStarted) / 1_000;
      helper.assertTrue(
          controller.endpointInfos().stream()
                  .anyMatch(endpoint -> endpoint.pos().equals(bottomInventory))
              && controller.endpointInfos().stream()
                  .anyMatch(endpoint -> endpoint.pos().equals(topInventory)),
          "The maximum supported scan must discover both ends of a densely connected 65533-pipe"
              + " graph");
      System.out.println("INFRA_BENCH 65533-pipe discovery: " + elapsedMicros + " microseconds");
      helper.succeed();
    } finally {
      SiliconicConfig.VALUES.logisticsMaxPipes.set(originalLimit);
      level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
    }
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_deferred_claims")
  public static void rejectsClaimsFromDeferredRemovedConditioners(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos owner = new BlockPos(1_001_296, helper.absolutePos(BlockPos.ZERO).getY(), 1_001_296);
    BlockPos machine = owner.east(2);
    ChunkPos chunk = new ChunkPos(owner);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
    level.setBlock(owner, ModBlocks.CONDITIONER.get().defaultBlockState(), 18);
    ConditionerBlockEntity conditioner = (ConditionerBlockEntity) level.getBlockEntity(owner);
    CompoundTag saved = new CompoundTag();
    saved.putInt("Cleanliness", 80);
    saved.putLongArray("ClaimedInterior", new long[] {machine.above().asLong()});
    CompoundTag lastScan = new CompoundTag();
    lastScan.putString("Status", "SEALED");
    saved.put("LastScan", lastScan);
    conditioner.load(saved);
    conditioner.onLoad();
    helper.assertTrue(
        CleanroomOccupancy.isMachineInside(level, machine),
        "The live conditioner must initially expose its saved room claim");
    // Forge queues chunk-load onLoad calls until tickBlockEntities, after entity/block events can
    // remove an owner. Exercise the real registration queue rather than calling onLoad by hand.
    level.getChunkAt(owner).registerAllBlockEntitiesAfterLevelLoad();
    level.removeBlock(owner, false);
    helper.assertTrue(
        conditioner.isRemoved() && !CleanroomOccupancy.isMachineInside(level, machine),
        "Removing the owner must clear its room claim before the queued callback runs");
    helper.runAfterDelay(
        2,
        () -> {
          try {
            helper.assertTrue(
                !CleanroomOccupancy.isMachineInside(level, machine)
                    && CleanroomOccupancy.cleanlinessAtMachine(level, machine) == 0,
                "A deferred onLoad callback must not reactivate a removed conditioner's room");
            helper.succeed();
          } finally {
            CleanroomOccupancy.remove(level, owner);
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
          }
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_cleanroom_claims",
      timeoutTicks = 100)
  public static void stopsClaimsFromUnloadedConditionerChunks(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos conditionerPos =
        new BlockPos(1_000_655, helper.absolutePos(BlockPos.ZERO).getY(), 1_000_648);
    BlockPos machinePos = conditionerPos.east(17);
    ChunkPos conditionerChunk = new ChunkPos(conditionerPos);
    ChunkPos machineChunk = new ChunkPos(machinePos);
    level
        .getChunkSource()
        .addRegionTicket(TicketType.FORCED, conditionerChunk, 0, conditionerChunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, machineChunk, 0, machineChunk);
    level.setBlock(conditionerPos, ModBlocks.CONDITIONER.get().defaultBlockState(), 2);
    level.setBlock(machinePos, Blocks.IRON_BLOCK.defaultBlockState(), 2);
    level.setBlock(machinePos.south(), Blocks.AIR.defaultBlockState(), 18);
    level.setBlock(machinePos.east(2).south(), Blocks.AIR.defaultBlockState(), 18);
    CleanroomOccupancy.remove(level, machinePos.south());
    CleanroomOccupancy.remove(level, machinePos.east(2).south());
    ConditionerBlockEntity conditioner =
        (ConditionerBlockEntity) level.getBlockEntity(conditionerPos);
    CompoundTag saved = new CompoundTag();
    saved.putInt("Cleanliness", 80);
    saved.putLongArray(
        "ClaimedInterior",
        new long[] {machinePos.above().asLong(), machinePos.east(2).above().asLong()});
    CompoundTag lastScan = new CompoundTag();
    lastScan.putString("Status", "SEALED");
    saved.put("LastScan", lastScan);
    conditioner.load(saved);
    conditioner.onLoad();
    helper.runAfterDelay(
        2,
        () -> {
          helper.assertTrue(
              level.isLoaded(conditionerPos), "The conditioner must initially be loaded");
          helper.assertTrue(
              CleanroomOccupancy.isMachineInside(level, machinePos)
                  && CleanroomOccupancy.cleanlinessAtMachine(level, machinePos) == 80,
              "The loaded conditioner must initially provide its saved room cleanliness");
          level
              .getChunkSource()
              .removeRegionTicket(TicketType.FORCED, conditionerChunk, 0, conditionerChunk);
          helper.runAfterDelay(
              20,
              () -> {
                try {
                  helper.assertTrue(
                      !level.isLoaded(conditionerPos) && level.isLoaded(machinePos),
                      "Only the conditioner chunk must lose FULL availability");
                  helper.assertTrue(
                      !conditioner.isRemoved(),
                      "The fixture must reach the interval before final conditioner removal");
                  boolean inside = CleanroomOccupancy.isMachineInside(level, machinePos);
                  int currentCleanliness =
                      CleanroomOccupancy.cleanlinessAtMachine(level, machinePos);
                  helper.assertTrue(
                      !inside && currentCleanliness == 0,
                      "An unavailable conditioner must not provide cleanroom claims to loaded"
                          + " machines: inside="
                          + inside
                          + ", cleanliness="
                          + currentCleanliness);
                  helper.assertTrue(
                      !level.isLoaded(conditionerPos),
                      "Claim lookups must not reload the conditioner");
                  BlockPos secondPos = machinePos.south();
                  level.setBlock(secondPos, ModBlocks.CONDITIONER.get().defaultBlockState(), 18);
                  ConditionerBlockEntity second =
                      (ConditionerBlockEntity) level.getBlockEntity(secondPos);
                  CompoundTag secondSaved = saved.copy();
                  secondSaved.putInt("Cleanliness", 40);
                  secondSaved.putLongArray(
                      "ClaimedInterior", new long[] {machinePos.above().asLong()});
                  second.load(secondSaved);
                  second.onLoad();
                  helper.assertTrue(
                      CleanroomOccupancy.isMachineInside(level, machinePos)
                          && CleanroomOccupancy.cleanlinessAtMachine(level, machinePos) == 40,
                      "An overlapping available conditioner must provide its own cleanliness");
                  BlockPos thirdPos = machinePos.east(2).south();
                  level.setBlock(thirdPos, ModBlocks.CONDITIONER.get().defaultBlockState(), 18);
                  ConditionerBlockEntity third =
                      (ConditionerBlockEntity) level.getBlockEntity(thirdPos);
                  CompoundTag thirdSaved = saved.copy();
                  thirdSaved.putInt("Cleanliness", 60);
                  thirdSaved.putLongArray(
                      "ClaimedInterior", new long[] {machinePos.east(2).above().asLong()});
                  third.load(thirdSaved);
                  third.onLoad();
                  Set<Long> group =
                      CleanroomOccupancy.update(
                          level, thirdPos, Set.of(machinePos.east(2).above().asLong()), 60);
                  helper.assertTrue(
                      group.equals(Set.of(thirdPos.asLong())),
                      "An unavailable old room claim must not bridge separate available conditioner"
                          + " groups");
                  third.setRemoved();
                  second.setRemoved();
                  helper.assertTrue(
                      !CleanroomOccupancy.isMachineInside(level, machinePos)
                          && CleanroomOccupancy.cleanlinessAtMachine(level, machinePos) == 0,
                      "Removing the available overlap must not expose an unavailable claim");
                  level.getChunkAt(conditionerPos);
                  helper.assertTrue(
                      level.isLoaded(conditionerPos)
                          && CleanroomOccupancy.isMachineInside(level, machinePos)
                          && CleanroomOccupancy.cleanlinessAtMachine(level, machinePos) == 80,
                      "Restoring FULL availability must reactivate the preserved cleanroom claim");
                  helper.succeed();
                } finally {
                  CleanroomOccupancy.remove(level, conditionerPos);
                  CleanroomOccupancy.remove(level, machinePos.south());
                  CleanroomOccupancy.remove(level, machinePos.east(2).south());
                  level.setBlock(machinePos.south(), Blocks.AIR.defaultBlockState(), 18);
                  level.setBlock(machinePos.east(2).south(), Blocks.AIR.defaultBlockState(), 18);
                  level
                      .getChunkSource()
                      .removeRegionTicket(TicketType.FORCED, machineChunk, 0, machineChunk);
                }
              });
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_logistics_lifecycle",
      timeoutTicks = 100)
  public static void stopsLogisticsAcrossUnloadedPipeChunks(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos firstPipe =
        new BlockPos(1_000_335, helper.absolutePos(BlockPos.ZERO).getY(), 1_000_328);
    BlockPos controllerPos = firstPipe.north();
    BlockPos sourcePos = firstPipe.west();
    BlockPos receiverPos = firstPipe.east(17);
    BlockPos middle = firstPipe.east(8);
    ChunkPos sourceChunk = new ChunkPos(sourcePos);
    ChunkPos receiverChunk = new ChunkPos(receiverPos);
    int originalScanInterval = SiliconicConfig.VALUES.logisticsScanInterval.get();
    int originalTransferInterval = SiliconicConfig.VALUES.logisticsTransferInterval.get();
    SiliconicConfig.VALUES.logisticsScanInterval.set(1_200);
    SiliconicConfig.VALUES.logisticsTransferInterval.set(1);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, sourceChunk, 0, sourceChunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, receiverChunk, 0, receiverChunk);
    level.setBlock(controllerPos, ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 2);
    level.setBlock(sourcePos, Blocks.CHEST.defaultBlockState(), 2);
    level.setBlock(receiverPos, Blocks.CHEST.defaultBlockState(), 2);
    for (int offset = 0; offset < 17; offset++)
      level.setBlock(firstPipe.east(offset), ModBlocks.LOGISTICS_PIPE.get().defaultBlockState(), 2);
    ChestBlockEntity source = (ChestBlockEntity) level.getBlockEntity(sourcePos);
    ChestBlockEntity receiver = (ChestBlockEntity) level.getBlockEntity(receiverPos);
    source.setItem(0, new ItemStack(Items.COAL, 64));
    for (int slot = 0; slot < receiver.getContainerSize(); slot++)
      receiver.setItem(slot, ItemStack.EMPTY);
    LogisticsControllerBlockEntity controller =
        (LogisticsControllerBlockEntity) level.getBlockEntity(controllerPos);
    CompoundTag saved = new CompoundTag();
    ListTag configurations = new ListTag();
    configurations.add(logisticsEndpoint(sourcePos, Direction.EAST, false, true));
    configurations.add(logisticsEndpoint(receiverPos, Direction.WEST, true, false));
    saved.put("Configurations", configurations);
    controller.load(saved);
    controller.refreshNetwork();
    LogisticsControllerBlockEntity.serverTick(
        level, controllerPos, controller.getBlockState(), controller);
    helper.assertTrue(
        !receiver.getItem(0).isEmpty(),
        "The loaded pipe route must move items before its middle chunk unloads");

    int checkDelay =
        Math.floorMod(level.getGameTime() + 20, 1_200)
                == Math.floorMod(controllerPos.asLong(), 1_200)
            ? 21
            : 20;
    helper.runAfterDelay(
        checkDelay,
        () -> {
          try {
            helper.assertTrue(
                level.isLoaded(controllerPos) && level.isLoaded(receiverPos),
                "Logistics source and destination chunks must stay loaded");
            helper.assertTrue(!level.isLoaded(middle), "The middle pipe chunk must have unloaded");
            int sourceBefore = source.getItem(0).getCount();
            int receiverBefore = receiver.getItem(0).getCount();
            LogisticsControllerBlockEntity.serverTick(
                level, controllerPos, controller.getBlockState(), controller);
            helper.assertTrue(
                source.getItem(0).getCount() == sourceBefore
                    && receiver.getItem(0).getCount() == receiverBefore,
                "Cached logistics endpoints must not move items across an unloaded pipe chunk");
            helper.assertTrue(!level.isLoaded(middle), "Logistics must not reload the route chunk");
            level.getChunkAt(middle);
            helper.assertTrue(
                level.isLoaded(middle), "The pipe route must be available after reload");
            LogisticsControllerBlockEntity.serverTick(
                level, controllerPos, controller.getBlockState(), controller);
            helper.assertTrue(
                receiver.getItem(0).getCount() > receiverBefore,
                "Reloading the pipe route must restore transfers before the fallback interval");
            helper.succeed();
          } finally {
            SiliconicConfig.VALUES.logisticsScanInterval.set(originalScanInterval);
            SiliconicConfig.VALUES.logisticsTransferInterval.set(originalTransferInterval);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, sourceChunk, 0, sourceChunk);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, receiverChunk, 0, receiverChunk);
          }
        });
  }

  private static CompoundTag logisticsEndpoint(
      BlockPos pos, Direction side, boolean input, boolean output) {
    CompoundTag endpoint = new CompoundTag();
    endpoint.putLong("Pos", pos.asLong());
    endpoint.putByte("Side", (byte) side.get3DDataValue());
    endpoint.putBoolean("Input", input);
    endpoint.putBoolean("Output", output);
    return endpoint;
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsInfrastructureMenusFromOtherDimensions(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    helper.setBlock(ORIGIN, ModBlocks.COAL_GENERATOR.get());
    helper.setBlock(ORIGIN.east(2), ModBlocks.CONDITIONER.get());
    helper.setBlock(ORIGIN.east(4), ModBlocks.LOGISTICS_CONTROLLER.get());
    Player owner = helper.makeMockPlayer();
    BlockPos origin = helper.absolutePos(ORIGIN);
    owner.setPos(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
    Level otherLevel = level.getServer().getLevel(Level.NETHER);
    helper.assertTrue(otherLevel != null, "The other test dimension must exist");
    Player otherDimensionPlayer =
        new Player(otherLevel, origin, 0.0F, owner.getGameProfile()) {
          @Override
          public boolean isSpectator() {
            return false;
          }

          @Override
          public boolean isCreative() {
            return false;
          }
        };
    otherDimensionPlayer.setPos(owner.getX(), owner.getY(), owner.getZ());
    CoalGeneratorBlockEntity generator = (CoalGeneratorBlockEntity) level.getBlockEntity(origin);
    ConditionerBlockEntity conditioner =
        (ConditionerBlockEntity) level.getBlockEntity(origin.east(2));
    LogisticsControllerBlockEntity controller =
        (LogisticsControllerBlockEntity) level.getBlockEntity(origin.east(4));
    List<AbstractContainerMenu> menus =
        List.of(
            new CoalGeneratorMenu(0, owner.getInventory(), generator),
            new ConditionerMenu(1, owner.getInventory(), conditioner),
            new LogisticsControllerMenu(2, owner.getInventory(), controller, List.of()));
    for (AbstractContainerMenu menu : menus) {
      helper.assertTrue(menu.stillValid(owner), "An adjacent same-dimension menu must stay usable");
      helper.assertTrue(
          !menu.stillValid(otherDimensionPlayer),
          menu.getClass().getSimpleName() + " must close for players in another dimension");
    }
    helper.succeed();
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_lifecycle",
      timeoutTicks = 100)
  public static void stopsPowerAcrossUnloadedCableChunks(GameTestHelper helper) {
    checkPowerRoute(helper, false);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_buffer_lifecycle",
      timeoutTicks = 100)
  public static void stopsBufferedPowerAcrossUnloadedCableChunks(GameTestHelper helper) {
    checkPowerRoute(helper, true);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_cable_connections",
      timeoutTicks = 100)
  public static void preservesConnectionsToUnavailableCableNeighbors(GameTestHelper helper) {
    checkPowerRoute(helper, false, true);
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_ticking_power_lifecycle",
      timeoutTicks = 100)
  public static void stopsTickingPowerAcrossUnavailableMiddleChunks(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos sourcePos =
        new BlockPos(1_001_800, helper.absolutePos(BlockPos.ZERO).getY(), 1_001_800);
    BlockPos receiverPos = sourcePos.east(96);
    BlockPos middle = sourcePos.east(48);
    ChunkPos sourceChunk = new ChunkPos(sourcePos);
    ChunkPos receiverChunk = new ChunkPos(receiverPos);
    ChunkPos middleChunk = new ChunkPos(middle);
    int originalCacheTicks = SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
    tickingPowerCleanup =
        () -> {
          SiliconicConfig.VALUES.powerNetworkCacheTicks.set(originalCacheTicks);
          level.getChunkSource().removeRegionTicket(TicketType.FORCED, middleChunk, 0, middleChunk);
          level.getChunkSource().removeRegionTicket(TicketType.FORCED, sourceChunk, 2, sourceChunk);
          level
              .getChunkSource()
              .removeRegionTicket(TicketType.FORCED, receiverChunk, 2, receiverChunk);
        };
    SiliconicConfig.VALUES.powerNetworkCacheTicks.set(1_200);
    // Radius two gives each endpoint an ENTITY_TICKING center and its complete surrounding FULL
    // ring. The central route chunk is three chunks from both endpoint centers.
    level.getChunkSource().addRegionTicket(TicketType.FORCED, sourceChunk, 2, sourceChunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, receiverChunk, 2, receiverChunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, middleChunk, 0, middleChunk);
    loadInfrastructureTickingArea(level, sourceChunk);
    loadInfrastructureTickingArea(level, receiverChunk);
    level.setBlock(sourcePos, ModBlocks.ENERGY_BUFFER.get().defaultBlockState(), 18);
    level.setBlock(receiverPos, ModBlocks.CONDITIONER.get().defaultBlockState(), 18);
    PowerCableBlock cable = (PowerCableBlock) ModBlocks.POWER_CABLE.get();
    for (int offset = 1; offset < 96; offset++) {
      BlockPos cablePos = sourcePos.east(offset);
      level.setBlock(cablePos.below(), Blocks.STONE.defaultBlockState(), 18);
      level.setBlock(cablePos, cable.defaultBlockState(), 18);
    }
    for (int offset = 1; offset < 96; offset++) {
      BlockPos cablePos = sourcePos.east(offset);
      cable.tick(level.getBlockState(cablePos), level, cablePos, level.random);
    }
    EnergyBufferBlockEntity source = (EnergyBufferBlockEntity) level.getBlockEntity(sourcePos);
    ConditionerBlockEntity receiver = (ConditionerBlockEntity) level.getBlockEntity(receiverPos);
    IEnergyStorage received =
        receiver
            .getCapability(ForgeCapabilities.ENERGY)
            .orElseThrow(() -> new IllegalStateException("Ticking receiver energy missing"));
    CompoundTag saved = new CompoundTag();
    saved.putInt("Energy", Integer.MAX_VALUE);
    source.load(saved);
    // Region tickets do not populate the persistent forced-chunk set that disables the server's
    // 300-tick playerless idle cutoff for block-entity ticking.
    level.resetEmptyTime();
    tickPowerSource(level, sourcePos, source);
    int initialEnergy = source.energyStored();
    awaitInfrastructureTicking(
        helper,
        List.of(sourcePos, receiverPos),
        40,
        () -> {
          helper.assertTrue(
              source.energyStored() < initialEnergy && received.getEnergyStored() > 0,
              "The real server ticker must transfer power before the central chunk expires:"
                  + " status="
                  + level.getChunkAt(sourcePos).getFullStatus()
                  + ", blockTicking="
                  + level.shouldTickBlocksAt(sourcePos)
                  + ", entitiesLoaded="
                  + level.areEntitiesLoaded(sourceChunk.toLong())
                  + ", initial="
                  + initialEnergy
                  + ", current="
                  + source.energyStored());
          level.getChunkSource().removeRegionTicket(TicketType.FORCED, middleChunk, 0, middleChunk);
          helper.runAfterDelay(
              20,
              () -> {
                helper.assertTrue(
                    level.getChunkAt(sourcePos).getFullStatus() == FullChunkStatus.ENTITY_TICKING
                        && level.getChunkAt(receiverPos).getFullStatus()
                            == FullChunkStatus.ENTITY_TICKING
                        && !level.isLoaded(middle),
                    "Both endpoint chunks must keep ticking around an unavailable middle chunk");
                int beforeSource = source.energyStored();
                int beforeReceiver = received.getEnergyStored();
                helper.runAfterDelay(
                    5,
                    () -> {
                      try {
                        helper.assertTrue(
                            source.energyStored() == beforeSource
                                && received.getEnergyStored() <= beforeReceiver,
                            "Actual server ticks must not deliver cached power across the missing"
                                + " middle route");
                        helper.assertTrue(
                            !level.isLoaded(middle),
                            "Actual network ticks must not force-load the missing route");
                        helper.succeed();
                      } finally {
                        cleanUpTickingPower(level);
                      }
                    });
              });
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_pipe_shape_loading",
      timeoutTicks = 100)
  public static void keepsPipeShapeRefreshFromLoadingUnavailableChunks(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos pipePos = new BlockPos(1_001_455, helper.absolutePos(BlockPos.ZERO).getY(), 1_001_448);
    BlockPos unavailable = pipePos.east();
    ChunkPos chunk = new ChunkPos(pipePos);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, chunk, 0, chunk);
    LogisticsPipeBlock pipe = (LogisticsPipeBlock) ModBlocks.LOGISTICS_PIPE.get();
    level.setBlock(pipePos, pipe.defaultBlockState(), 18);
    level.setBlock(pipePos.north(), ModBlocks.LOGISTICS_CONTROLLER.get().defaultBlockState(), 18);
    helper.runAfterDelay(
        20,
        () -> {
          try {
            helper.assertTrue(
                level.isLoaded(pipePos) && !level.isLoaded(unavailable),
                "The pipe must be loaded beside an unavailable chunk before its shape refresh");
            pipe.tick(level.getBlockState(pipePos), level, pipePos, level.random);
            helper.assertTrue(
                level.getBlockState(pipePos).getValue(LogisticsPipeBlock.NORTH),
                "The shape refresh must connect the pipe to its loaded controller");
            helper.assertTrue(
                !level.isLoaded(unavailable),
                "Refreshing a pipe shape must not force-load its unavailable neighboring chunk");
            helper.succeed();
          } finally {
            level.getChunkSource().removeRegionTicket(TicketType.FORCED, chunk, 0, chunk);
          }
        });
  }

  @GameTest(
      templateNamespace = Siliconic.MOD_ID,
      template = "empty",
      batch = "infrastructure_initial_cable_connections",
      timeoutTicks = 100)
  public static void discoversCableNeighborsLoadedAfterFirstPlacement(GameTestHelper helper) {
    checkPowerRoute(helper, false, true, true);
  }

  private static void checkPowerRoute(GameTestHelper helper, boolean buffered) {
    checkPowerRoute(helper, buffered, false);
  }

  private static void checkPowerRoute(
      GameTestHelper helper, boolean buffered, boolean boundaryCable) {
    checkPowerRoute(helper, buffered, boundaryCable, false);
  }

  private static void checkPowerRoute(
      GameTestHelper helper, boolean buffered, boolean boundaryCable, boolean firstPlacement) {
    ServerLevel level = helper.getLevel();
    int coordinateOffset = firstPlacement ? 1_440 : boundaryCable ? 1_120 : buffered ? 320 : 0;
    int cableCount = boundaryCable ? 17 : 16;
    BlockPos generatorPos =
        new BlockPos(
            1_000_175 + coordinateOffset - (boundaryCable ? 1 : 0),
            helper.absolutePos(BlockPos.ZERO).getY(),
            1_000_168 + coordinateOffset);
    BlockPos receiverPos = generatorPos.east(cableCount + 1);
    BlockPos middle = generatorPos.east(8);
    ChunkPos sourceChunk = new ChunkPos(generatorPos);
    ChunkPos receiverChunk = new ChunkPos(receiverPos);
    int originalCacheTicks = SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
    if (firstPlacement)
      initialCableCleanup =
          () -> {
            SiliconicConfig.VALUES.powerNetworkCacheTicks.set(originalCacheTicks);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, sourceChunk, 0, sourceChunk);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, receiverChunk, 0, receiverChunk);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, sourceChunk, 2, sourceChunk);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, receiverChunk, 2, receiverChunk);
          };
    SiliconicConfig.VALUES.powerNetworkCacheTicks.set(1_200);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, sourceChunk, 0, sourceChunk);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, receiverChunk, 0, receiverChunk);
    level.setBlock(
        generatorPos,
        (buffered ? ModBlocks.ENERGY_BUFFER.get() : ModBlocks.COAL_GENERATOR.get())
            .defaultBlockState(),
        2);
    level.setBlock(receiverPos, ModBlocks.CONDITIONER.get().defaultBlockState(), 2);
    PowerCableBlock cable = (PowerCableBlock) ModBlocks.POWER_CABLE.get();
    for (int offset = 1; offset <= cableCount; offset++) {
      BlockPos cablePos = generatorPos.east(offset);
      level.setBlock(cablePos.below(), Blocks.STONE.defaultBlockState(), 2);
      level.setBlock(cablePos, cable.defaultBlockState(), 2);
    }
    for (int offset = 1; offset <= cableCount; offset++) {
      BlockPos cablePos = generatorPos.east(offset);
      cable.tick(level.getBlockState(cablePos), level, cablePos, level.random);
    }
    BlockEntity generator = level.getBlockEntity(generatorPos);
    ConditionerBlockEntity receiver = (ConditionerBlockEntity) level.getBlockEntity(receiverPos);
    CompoundTag saved = new CompoundTag();
    saved.putInt("Energy", 10_000);
    generator.load(saved);
    tickPowerSource(level, generatorPos, generator);
    tickPowerSource(level, generatorPos, generator);
    IEnergyStorage receiverEnergy =
        receiver
            .getCapability(ForgeCapabilities.ENERGY)
            .orElseThrow(() -> new IllegalStateException("Receiver energy capability missing"));
    helper.assertTrue(
        receiverEnergy.getEnergyStored() > 0,
        "The loaded cable route must transfer power before its middle chunk unloads");

    helper.runAfterDelay(
        20,
        () -> {
          boolean scheduledVerification = false;
          try {
            helper.assertTrue(
                level.isLoaded(generatorPos) && level.isLoaded(receiverPos),
                "Source and destination chunks must stay loaded while the route unloads");
            helper.assertTrue(!level.isLoaded(middle), "The middle cable chunk must have unloaded");
            int before = receiverEnergy.getEnergyStored();
            tickPowerSource(level, generatorPos, generator);
            helper.assertTrue(
                receiverEnergy.getEnergyStored() == before,
                "Cached power endpoints must stop receiving energy across an unloaded cable chunk");
            helper.assertTrue(
                !level.isLoaded(middle), "The cached route must not reload its chunk");
            if (firstPlacement) {
              BlockPos cablePos = generatorPos.east();
              level.setBlock(cablePos, Blocks.AIR.defaultBlockState(), 18);
              helper.assertTrue(
                  !level.isLoaded(middle),
                  "Removing the boundary cable must not load its neighbor");
              level.setBlock(cablePos, cable.defaultBlockState(), 18);
              helper.assertTrue(
                  !level.isLoaded(middle), "Placing the boundary cable must not load its neighbor");
              cable.tick(level.getBlockState(cablePos), level, cablePos, level.random);
              helper.assertTrue(
                  !level.isLoaded(middle),
                  "Updating the boundary cable must not load its neighbor");
              helper.assertTrue(
                  !level.getBlockState(cablePos).getValue(PowerCableBlock.EAST),
                  "A cable first placed at the unavailable boundary must begin without an east"
                      + " connection");
              tickPowerSource(level, generatorPos, generator);
              helper.assertTrue(
                  receiverEnergy.getEnergyStored() == before,
                  "Placing the boundary cable must not cross the unavailable route: before="
                      + before
                      + ", after="
                      + receiverEnergy.getEnergyStored());
              helper.assertTrue(
                  !level.isLoaded(middle),
                  "Scanning the replaced boundary cable must not load its neighbor");
            } else if (boundaryCable) {
              BlockPos cablePos = generatorPos.east();
              var preserved =
                  cable.updateShape(
                      level.getBlockState(cablePos),
                      Direction.NORTH,
                      level.getBlockState(cablePos.north()),
                      level,
                      cablePos,
                      cablePos.north());
              helper.assertTrue(
                  preserved.getValue(PowerCableBlock.EAST),
                  "An unrelated shape update must retain the existing connection to an unavailable"
                      + " chunk");
              level.setBlock(cablePos, preserved, 18);
            }
            level.getChunkAt(middle);
            helper.assertTrue(
                level.isLoaded(middle), "The cable route must be available after reload");
            if (firstPlacement) {
              level.resetEmptyTime();
              level
                  .getChunkSource()
                  .addRegionTicket(TicketType.FORCED, sourceChunk, 2, sourceChunk);
              level
                  .getChunkSource()
                  .addRegionTicket(TicketType.FORCED, receiverChunk, 2, receiverChunk);
              loadInfrastructureTickingArea(level, sourceChunk);
              loadInfrastructureTickingArea(level, receiverChunk);
              awaitInfrastructureTicking(
                  helper,
                  List.of(generatorPos, receiverPos),
                  40,
                  () -> {
                    // Readiness does not order cable updates before source BE work. One run had
                    // restored arms after two ticks, but receiver FE was 320 (initially 400) and
                    // source FE was still 9600. Observe both completions within the original
                    // 100-tick timeout, well before the 1200-tick cache fallback can run.
                    helper.succeedWhen(
                        () -> {
                          helper.assertTrue(
                              level.getChunkAt(generatorPos).getFullStatus()
                                      == FullChunkStatus.ENTITY_TICKING
                                  && level
                                      .getBlockState(generatorPos.east())
                                      .getValue(PowerCableBlock.EAST),
                              "The normal queued cable tick must discover the restored neighbor:"
                                  + " status="
                                  + level.getChunkAt(generatorPos).getFullStatus()
                                  + ", blockTicking="
                                  + level.shouldTickBlocksAt(generatorPos)
                                  + ", entitiesLoaded="
                                  + level.areEntitiesLoaded(sourceChunk.toLong())
                                  + ", east="
                                  + level
                                      .getBlockState(generatorPos.east())
                                      .getValue(PowerCableBlock.EAST)
                                  + ", scheduled="
                                  + level
                                      .getBlockTicks()
                                      .hasScheduledTick(generatorPos.east(), cable));
                          helper.assertTrue(
                              receiverEnergy.getEnergyStored() > before,
                              "A cable first placed at the unavailable boundary must transfer power"
                                  + " once normal scheduled ticks resume: "
                                  + powerRouteDiagnostic(
                                      level,
                                      generatorPos,
                                      generator,
                                      receiver,
                                      receiverEnergy,
                                      before));
                          // Failed observations retain their tickets/configuration; the existing
                          // AfterBatch hook also performs this cleanup if the test times out.
                          cleanUpInitialCable(level);
                        });
                  });
              scheduledVerification = true;
              return;
            }
            tickPowerSource(level, generatorPos, generator);
            helper.assertTrue(
                receiverEnergy.getEnergyStored() > before,
                "Reloading the cable route must restore power before the fallback scan interval");
            helper.succeed();
          } finally {
            if (!scheduledVerification)
              SiliconicConfig.VALUES.powerNetworkCacheTicks.set(originalCacheTicks);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, sourceChunk, 0, sourceChunk);
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, receiverChunk, 0, receiverChunk);
          }
        });
  }

  private static String powerRouteDiagnostic(
      ServerLevel level,
      BlockPos sourcePos,
      BlockEntity source,
      BlockEntity receiver,
      IEnergyStorage receiverEnergy,
      int before) {
    BlockEntity currentSource = level.getBlockEntity(sourcePos);
    BlockEntity currentReceiver = level.getBlockEntity(receiver.getBlockPos());
    return "before="
        + before
        + ", sourceSame="
        + (currentSource == source)
        + ", sourceRemoved="
        + source.isRemoved()
        + ", currentSourceRemoved="
        + (currentSource == null || currentSource.isRemoved())
        + ", sourceFE="
        + capabilityEnergy(source)
        + ", currentSourceFE="
        + capabilityEnergy(currentSource)
        + ", receiverSame="
        + (currentReceiver == receiver)
        + ", receiverRemoved="
        + receiver.isRemoved()
        + ", currentReceiverRemoved="
        + (currentReceiver == null || currentReceiver.isRemoved())
        + ", receiverFE="
        + receiverEnergy.getEnergyStored()
        + ", currentReceiverFE="
        + capabilityEnergy(currentReceiver)
        + ", cacheTicks="
        + SiliconicConfig.VALUES.powerNetworkCacheTicks.get();
  }

  private static int capabilityEnergy(@Nullable BlockEntity blockEntity) {
    if (blockEntity == null) return -1;
    IEnergyStorage energy = blockEntity.getCapability(ForgeCapabilities.ENERGY).orElse(null);
    return energy == null ? -1 : energy.getEnergyStored();
  }

  private static void loadInfrastructureTickingArea(ServerLevel level, ChunkPos center) {
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++) level.getChunk(center.x + dx, center.z + dz);
  }

  private static void awaitInfrastructureTicking(
      GameTestHelper helper, List<BlockPos> positions, int ticksRemaining, Runnable action) {
    ServerLevel level = helper.getLevel();
    boolean ready = true;
    for (BlockPos pos : positions)
      ready &=
          level.isLoaded(pos)
              && level.getChunkAt(pos).getFullStatus() == FullChunkStatus.ENTITY_TICKING
              && level.getChunkSource().isPositionTicking(ChunkPos.asLong(pos))
              && level.areEntitiesLoaded(ChunkPos.asLong(pos));
    if (ready) {
      // Entity-manager load completion occurs after block/entity ticks. Let two full server ticks
      // run after every asynchronous readiness gate is open before checking automatic work.
      helper.runAfterDelay(2, action);
      return;
    }
    if (ticksRemaining <= 0) {
      StringBuilder diagnostic = new StringBuilder("Endpoint chunks must become ready for ticking");
      for (BlockPos pos : positions) {
        boolean loaded = level.isLoaded(pos);
        diagnostic
            .append("; ")
            .append(pos)
            .append(" loaded=")
            .append(loaded)
            .append(" status=")
            .append(loaded ? level.getChunkAt(pos).getFullStatus() : "unavailable")
            .append(" tickingFuture=")
            .append(level.getChunkSource().isPositionTicking(ChunkPos.asLong(pos)))
            .append(" entitiesLoaded=")
            .append(level.areEntitiesLoaded(ChunkPos.asLong(pos)))
            .append(" debug=")
            .append(level.getChunkSource().getChunkDebugData(new ChunkPos(pos)));
      }
      helper.assertTrue(false, diagnostic.toString());
    }
    helper.runAfterDelay(
        1, () -> awaitInfrastructureTicking(helper, positions, ticksRemaining - 1, action));
  }

  private static void tickPowerSource(Level level, BlockPos pos, BlockEntity source) {
    if (source instanceof CoalGeneratorBlockEntity generator)
      CoalGeneratorBlockEntity.serverTick(level, pos, source.getBlockState(), generator);
    else if (source instanceof EnergyBufferBlockEntity buffer)
      EnergyBufferBlockEntity.serverTick(level, pos, source.getBlockState(), buffer);
    else throw new IllegalArgumentException("Unsupported power source");
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsNegativeInfrastructureEnergyRequests(GameTestHelper helper) {
    List<BlockEntity> machines =
        List.of(
            new CoalGeneratorBlockEntity(
                BlockPos.ZERO, ModBlocks.COAL_GENERATOR.get().defaultBlockState()),
            new EnergyBufferBlockEntity(
                BlockPos.ZERO, ModBlocks.ENERGY_BUFFER.get().defaultBlockState()),
            new ConditionerBlockEntity(
                BlockPos.ZERO, ModBlocks.CONDITIONER.get().defaultBlockState()));
    for (BlockEntity machine : machines) {
      CompoundTag saved = new CompoundTag();
      saved.putInt("Energy", 1_000);
      machine.load(saved);
      IEnergyStorage storage =
          machine
              .getCapability(ForgeCapabilities.ENERGY)
              .orElseThrow(
                  () -> new IllegalStateException("Infrastructure energy capability missing"));
      for (int invalidAmount : new int[] {0, -1, Integer.MIN_VALUE})
        for (boolean simulate : new boolean[] {true, false}) {
          helper.assertTrue(
              storage.receiveEnergy(invalidAmount, simulate) == 0
                  && storage.extractEnergy(invalidAmount, simulate) == 0
                  && storage.getEnergyStored() == 1_000,
              machine.getClass().getSimpleName()
                  + " must ignore negative and zero energy requests without changing storage");
        }
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesUnloadedRoomDiagnosisWithoutVisibleInterior(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    BlockPos origin = new BlockPos(1_000_015, helper.absolutePos(BlockPos.ZERO).getY(), 1_000_008);
    ChunkPos originChunk = new ChunkPos(origin);
    level.getChunkSource().addRegionTicket(TicketType.FORCED, originChunk, 0, originChunk);
    level.getChunkAt(origin);
    BlockPos hiddenInterior = origin.east();
    for (Direction direction : Direction.values())
      if (direction != Direction.EAST)
        level.setBlock(origin.relative(direction), Blocks.STONE.defaultBlockState(), 2);

    helper.runAfterDelay(
        20,
        () -> {
          try {
            helper.assertTrue(level.isLoaded(origin), "The scan origin chunk must be loaded");
            helper.assertTrue(
                !level.isLoaded(hiddenInterior), "The neighboring room chunk must be unloaded");
            RoomScanResult result = RoomScanner.scan(level, origin);
            helper.assertTrue(
                result.status() == RoomScanResult.Status.UNLOADED,
                "An unavailable interior must preserve the unloaded diagnosis before any air is"
                    + " found");
            helper.assertTrue(
                hiddenInterior.equals(result.diagnosticPosition()),
                "The diagnosis must identify the unavailable room boundary");
            helper.assertTrue(
                !level.isLoaded(hiddenInterior),
                "Scanning must not load the unavailable room chunk");
            helper.succeed();
          } finally {
            level
                .getChunkSource()
                .removeRegionTicket(TicketType.FORCED, originChunk, 0, originChunk);
          }
        });
  }

  private InfrastructureStabilityGameTests() {}
}
