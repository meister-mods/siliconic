package io.github.meistermods.siliconic.logistics;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class LogisticsControllerBlockEntity extends BlockEntity implements MenuProvider {
  public static final int MAX_ENDPOINTS = 128;
  private static final int MAX_PIPES = 4_096;
  private static final int SCAN_INTERVAL = 40;
  private static final int TRANSFER_INTERVAL = 10;
  private static final int TRANSFER_LIMIT = 8;

  public record EndpointInfo(BlockPos pos, Component name, boolean supportsForced) {}

  private record MachineEndpoint(
      BlockPos pos, List<Direction> sides, Component name, boolean supportsForced) {}

  private record Destination(MachineEndpoint endpoint, Direction side) {}

  private static final class EndpointConfig {
    private boolean input;
    private boolean output;
    private boolean forced;
    private ItemStack filter = ItemStack.EMPTY;

    int flags() {
      return (input ? 1 : 0) | (output ? 2 : 0) | (forced ? 4 : 0);
    }
  }

  private final Map<BlockPos, EndpointConfig> configurations = new HashMap<>();
  private List<MachineEndpoint> endpoints = List.of();
  private boolean connectedToPipe;
  private ItemStack transferBuffer = ItemStack.EMPTY;
  @Nullable private BlockPos bufferSource;
  private int sourceCursor;
  private int destinationCursor;

  public LogisticsControllerBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.LOGISTICS_CONTROLLER.get(), pos, state);
  }

  public static void serverTick(
      Level level,
      BlockPos pos,
      BlockState state,
      LogisticsControllerBlockEntity controller) {
    long offset = Math.floorMod(pos.asLong(), SCAN_INTERVAL);
    if (Math.floorMod(level.getGameTime(), SCAN_INTERVAL) == offset) controller.refreshNetwork();
    if (Math.floorMod(level.getGameTime(), TRANSFER_INTERVAL)
        != Math.floorMod(pos.asLong(), TRANSFER_INTERVAL)) return;
    if (!controller.connectedToPipe) return;
    if (!controller.transferBuffer.isEmpty()) {
      controller.moveBufferedItem();
      return;
    }
    controller.extractNextItem();
  }

  public boolean refreshNetwork() {
    if (level == null || level.isClientSide) return connectedToPipe;
    ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    Set<BlockPos> visited = new HashSet<>();
    Map<BlockPos, EnumSet<Direction>> machineSides = new LinkedHashMap<>();

    for (Direction direction : Direction.values()) {
      BlockPos neighbor = worldPosition.relative(direction);
      if (level.hasChunkAt(neighbor)
          && level.getBlockState(neighbor).getBlock() instanceof LogisticsPipeBlock)
        pending.addLast(neighbor);
    }
    connectedToPipe = !pending.isEmpty();

    while (!pending.isEmpty() && visited.size() < MAX_PIPES) {
      BlockPos pipePos = pending.removeFirst();
      if (!visited.add(pipePos) || !level.hasChunkAt(pipePos)) continue;
      if (!(level.getBlockState(pipePos).getBlock() instanceof LogisticsPipeBlock)) continue;

      for (Direction direction : Direction.values()) {
        BlockPos neighbor = pipePos.relative(direction);
        if (!level.hasChunkAt(neighbor)) continue;
        BlockState neighborState = level.getBlockState(neighbor);
        if (neighborState.getBlock() instanceof LogisticsPipeBlock) {
          if (!visited.contains(neighbor)) pending.addLast(neighbor);
          continue;
        }
        if (neighbor.equals(worldPosition)
            || neighborState.getBlock() instanceof LogisticsControllerBlock) continue;
        BlockEntity blockEntity = level.getBlockEntity(neighbor);
        Direction machineSide = direction.getOpposite();
        if (blockEntity != null
            && blockEntity
                .getCapability(ForgeCapabilities.ITEM_HANDLER, machineSide)
                .isPresent())
          machineSides
              .computeIfAbsent(neighbor.immutable(), ignored -> EnumSet.noneOf(Direction.class))
              .add(machineSide);
      }
    }

    List<MachineEndpoint> found = new ArrayList<>();
    machineSides.entrySet().stream()
        .sorted(Comparator.comparingLong(entry -> entry.getKey().asLong()))
        .limit(MAX_ENDPOINTS)
        .forEach(
            entry -> {
              if (!level.hasChunkAt(entry.getKey())) return;
              BlockEntity blockEntity = level.getBlockEntity(entry.getKey());
              if (blockEntity == null) return;
              Component name = blockEntity.getBlockState().getBlock().getName();
              found.add(
                  new MachineEndpoint(
                      entry.getKey(),
                      List.copyOf(entry.getValue()),
                      name,
                      blockEntity instanceof LogisticsInventoryAccess));
            });
    endpoints = List.copyOf(found);
    sourceCursor = clampCursor(sourceCursor, endpoints.size());
    destinationCursor = clampCursor(destinationCursor, endpoints.size());
    return connectedToPipe;
  }

  public void writeMenuOpeningData(FriendlyByteBuf buffer) {
    buffer.writeBlockPos(worldPosition);
    List<EndpointInfo> endpointInfos = endpointInfos();
    buffer.writeVarInt(endpointInfos.size());
    for (EndpointInfo endpoint : endpointInfos) {
      buffer.writeBlockPos(endpoint.pos());
      buffer.writeComponent(endpoint.name());
      buffer.writeBoolean(endpoint.supportsForced());
    }
  }

  public List<EndpointInfo> endpointInfos() {
    return endpoints.stream()
        .map(
            endpoint ->
                new EndpointInfo(endpoint.pos(), endpoint.name(), endpoint.supportsForced()))
        .toList();
  }

  int flags(BlockPos pos) {
    EndpointConfig config = configurations.get(pos);
    return config == null ? 0 : config.flags();
  }

  void toggleInput(BlockPos pos) {
    EndpointConfig config = config(pos);
    config.input = !config.input;
    setChanged();
  }

  void toggleOutput(BlockPos pos) {
    EndpointConfig config = config(pos);
    config.output = !config.output;
    setChanged();
  }

  void toggleForced(BlockPos pos, boolean supported) {
    EndpointConfig config = config(pos);
    if (!supported && !config.forced) return;
    config.forced = !config.forced;
    setChanged();
  }

  ItemStack filter(BlockPos pos) {
    EndpointConfig config = configurations.get(pos);
    return config == null ? ItemStack.EMPTY : config.filter.copy();
  }

  void setFilter(BlockPos pos, ItemStack stack) {
    EndpointConfig config = config(pos);
    ItemStack filter = stack.copy();
    if (!filter.isEmpty()) filter.setCount(1);
    if (ItemStack.matches(config.filter, filter)) return;
    config.filter = filter;
    setChanged();
  }

  public void dropContents(Level level) {
    for (EndpointConfig config : configurations.values())
      if (!config.filter.isEmpty())
        Containers.dropItemStack(
            level,
            worldPosition.getX(),
            worldPosition.getY(),
            worldPosition.getZ(),
            config.filter);
    if (!transferBuffer.isEmpty())
      Containers.dropItemStack(
          level,
          worldPosition.getX(),
          worldPosition.getY(),
          worldPosition.getZ(),
          transferBuffer);
  }

  private EndpointConfig config(BlockPos pos) {
    return configurations.computeIfAbsent(pos.immutable(), ignored -> new EndpointConfig());
  }

  private void extractNextItem() {
    if (level == null || endpoints.isEmpty()) return;
    int start = clampCursor(sourceCursor, endpoints.size());
    for (int offset = 0; offset < endpoints.size(); offset++) {
      int index = (start + offset) % endpoints.size();
      MachineEndpoint endpoint = endpoints.get(index);
      EndpointConfig config = configurations.get(endpoint.pos());
      if (config == null || !config.output) continue;

      List<IItemHandler> handlers = extractionHandlers(endpoint, config.forced);
      for (IItemHandler handler : handlers)
        for (int slot = 0; slot < handler.getSlots(); slot++) {
          ItemStack simulated = handler.extractItem(slot, TRANSFER_LIMIT, true);
          if (simulated.isEmpty() || !matchesFilter(config, simulated)) continue;
          if (findDestination(simulated, endpoint.pos()) == null) continue;
          ItemStack extracted = handler.extractItem(slot, simulated.getCount(), false);
          if (extracted.isEmpty()) continue;
          transferBuffer = extracted;
          bufferSource = endpoint.pos();
          sourceCursor = (index + 1) % endpoints.size();
          setChanged();
          moveBufferedItem();
          return;
        }
    }
    sourceCursor = endpoints.isEmpty() ? 0 : (start + 1) % endpoints.size();
  }

  private void moveBufferedItem() {
    if (level == null || transferBuffer.isEmpty()) return;
    Destination destination = findDestination(transferBuffer, bufferSource);
    if (destination == null) return;
    IItemHandler handler = normalHandler(destination.endpoint(), destination.side());
    if (handler == null) return;
    int before = transferBuffer.getCount();
    transferBuffer = insert(handler, transferBuffer, false);
    if (transferBuffer.getCount() == before) return;
    int destinationIndex = endpoints.indexOf(destination.endpoint());
    if (destinationIndex >= 0) destinationCursor = (destinationIndex + 1) % endpoints.size();
    if (transferBuffer.isEmpty()) bufferSource = null;
    setChanged();
  }

  @Nullable
  private Destination findDestination(ItemStack stack, @Nullable BlockPos source) {
    if (endpoints.isEmpty()) return null;
    int start = clampCursor(destinationCursor, endpoints.size());
    for (int offset = 0; offset < endpoints.size(); offset++) {
      MachineEndpoint endpoint = endpoints.get((start + offset) % endpoints.size());
      if (endpoint.pos().equals(source)) continue;
      EndpointConfig config = configurations.get(endpoint.pos());
      if (config == null || !config.input || !matchesFilter(config, stack)) continue;
      for (Direction side : endpoint.sides()) {
        IItemHandler handler = normalHandler(endpoint, side);
        if (handler == null) continue;
        ItemStack remainder = insert(handler, stack, true);
        if (remainder.getCount() < stack.getCount()) return new Destination(endpoint, side);
      }
    }
    return null;
  }

  private List<IItemHandler> extractionHandlers(MachineEndpoint endpoint, boolean forced) {
    if (level == null || !level.hasChunkAt(endpoint.pos())) return List.of();
    List<IItemHandler> handlers = new ArrayList<>();
    for (Direction side : endpoint.sides()) {
      IItemHandler handler = normalHandler(endpoint, side);
      if (handler != null && !containsIdentity(handlers, handler)) handlers.add(handler);
    }
    BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
    if (forced && blockEntity instanceof LogisticsInventoryAccess access) {
      IItemHandler raw = access.logisticsInventory();
      if (!containsIdentity(handlers, raw)) handlers.add(raw);
    }
    return handlers;
  }

  @Nullable
  private IItemHandler normalHandler(MachineEndpoint endpoint, Direction side) {
    if (level == null || !level.hasChunkAt(endpoint.pos())) return null;
    BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
    return blockEntity == null
        ? null
        : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
  }

  private static ItemStack insert(IItemHandler handler, ItemStack stack, boolean simulate) {
    ItemStack remainder = stack.copy();
    for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++)
      remainder = handler.insertItem(slot, remainder, simulate);
    return remainder;
  }

  private static boolean matchesFilter(EndpointConfig config, ItemStack stack) {
    return config.filter.isEmpty() || ItemStack.isSameItem(config.filter, stack);
  }

  private static boolean containsIdentity(List<IItemHandler> handlers, IItemHandler candidate) {
    for (IItemHandler handler : handlers) if (handler == candidate) return true;
    return false;
  }

  private static int clampCursor(int cursor, int size) {
    return size <= 0 ? 0 : Math.floorMod(cursor, size);
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    super.saveAdditional(tag);
    ListTag entries = new ListTag();
    for (Map.Entry<BlockPos, EndpointConfig> entry : configurations.entrySet()) {
      EndpointConfig config = entry.getValue();
      if (config.flags() == 0 && config.filter.isEmpty()) continue;
      CompoundTag configTag = new CompoundTag();
      configTag.putLong("Pos", entry.getKey().asLong());
      configTag.putBoolean("Input", config.input);
      configTag.putBoolean("Output", config.output);
      configTag.putBoolean("Forced", config.forced);
      if (!config.filter.isEmpty())
        configTag.put("Filter", config.filter.save(new CompoundTag()));
      entries.add(configTag);
    }
    tag.put("Configurations", entries);
    if (!transferBuffer.isEmpty()) tag.put("TransferBuffer", transferBuffer.save(new CompoundTag()));
    if (bufferSource != null) tag.putLong("BufferSource", bufferSource.asLong());
    tag.putInt("SourceCursor", sourceCursor);
    tag.putInt("DestinationCursor", destinationCursor);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    configurations.clear();
    ListTag entries = tag.getList("Configurations", Tag.TAG_COMPOUND);
    for (int index = 0; index < entries.size(); index++) {
      CompoundTag configTag = entries.getCompound(index);
      EndpointConfig config = new EndpointConfig();
      config.input = configTag.getBoolean("Input");
      config.output = configTag.getBoolean("Output");
      config.forced = configTag.getBoolean("Forced");
      config.filter = ItemStack.of(configTag.getCompound("Filter"));
      configurations.put(BlockPos.of(configTag.getLong("Pos")), config);
    }
    transferBuffer = ItemStack.of(tag.getCompound("TransferBuffer"));
    bufferSource =
        tag.contains("BufferSource", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("BufferSource"))
            : null;
    sourceCursor = Math.max(0, tag.getInt("SourceCursor"));
    destinationCursor = Math.max(0, tag.getInt("DestinationCursor"));
    connectedToPipe = false;
    endpoints = List.of();
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("container.siliconic.logistics_controller");
  }

  @Nullable
  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new LogisticsControllerMenu(id, inventory, this, endpointInfos());
  }
}
