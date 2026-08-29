package io.github.meistermods.siliconic.cleanroom;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import io.github.meistermods.siliconic.cleanroom.RoomScanResult.OpenableStats;
import io.github.meistermods.siliconic.cleanroom.RoomScanResult.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings({"null"})
public final class RoomScanner {
  public static final Limits DEFAULT_LIMITS = new Limits(4_096, 32);

  public record Limits(int maxVolume, int maxDistance) {
    public Limits {
      if (maxVolume < 1) throw new IllegalArgumentException("maxVolume must be positive");
      if (maxDistance < 1) throw new IllegalArgumentException("maxDistance must be positive");
    }
  }

  public static RoomScanResult scan(Level level, BlockPos origin) {
    return scan(level, origin, DEFAULT_LIMITS);
  }

  /**
   * Scans traversable space around an origin without loading chunks. The origin itself is treated as
   * part of the room boundary, so scanners are intended to be placed inside the room rather than
   * embedded between the room and the outdoors.
   */
  public static RoomScanResult scan(Level level, BlockPos origin, Limits limits) {
    Queue<BlockPos> pending = new ArrayDeque<>();
    Set<Long> discovered = new HashSet<>();
    Set<Long> countedOpenables = new HashSet<>();
    Map<ResourceLocation, Integer> surfaces = new HashMap<>();
    Map<ResourceLocation, MutableOpenableStats> openables = new HashMap<>();
    ScanFlags flags = new ScanFlags();

    for (Direction direction : Direction.values())
      inspectNeighbor(
          level,
          origin.relative(direction),
          origin,
          limits,
          pending,
          discovered,
          countedOpenables,
          surfaces,
          openables,
          flags);

    int volume = 0;
    while (!pending.isEmpty()) {
      BlockPos current = pending.remove();
      BlockState state = level.getBlockState(current);
      if (!isOpenable(state)) volume++;
      else recordOpenable(level, current, state, countedOpenables, openables);
      for (Direction direction : Direction.values())
        inspectNeighbor(
            level,
            current.relative(direction),
            origin,
            limits,
            pending,
            discovered,
            countedOpenables,
            surfaces,
            openables,
            flags);
    }

    Map<ResourceLocation, Integer> sortedSurfaces = new LinkedHashMap<>();
    new TreeMap<>(surfaces).forEach(sortedSurfaces::put);
    Map<ResourceLocation, OpenableStats> sortedOpenables = new LinkedHashMap<>();
    new TreeMap<>(openables)
        .forEach(
            (id, stats) ->
                sortedOpenables.put(id, new OpenableStats(stats.total, stats.open)));
    int openCount = sortedOpenables.values().stream().mapToInt(OpenableStats::open).sum();
    Status status =
        determineStatus(flags, discovered.isEmpty(), openCount);
    return new RoomScanResult(
        status, volume, discovered.size(), sortedSurfaces, sortedOpenables, discovered);
  }

  private static void inspectNeighbor(
      Level level,
      BlockPos candidate,
      BlockPos origin,
      Limits limits,
      Queue<BlockPos> pending,
      Set<Long> discovered,
      Set<Long> countedOpenables,
      Map<ResourceLocation, Integer> surfaces,
      Map<ResourceLocation, MutableOpenableStats> openables,
      ScanFlags flags) {
    if (candidate.equals(origin)) return;
    if (level.isOutsideBuildHeight(candidate)) {
      flags.worldOpen = true;
      return;
    }
    if (!level.isLoaded(candidate)) {
      flags.unloaded = true;
      return;
    }
    BlockState state = level.getBlockState(candidate);
    if (!isPassable(level, candidate, state)) {
      if (isOpenable(state))
        recordOpenable(level, candidate, state, countedOpenables, openables);
      else recordSurface(state, surfaces);
      return;
    }
    if (distance(origin, candidate) > limits.maxDistance()) {
      flags.limitReached = true;
      return;
    }
    long key = candidate.asLong();
    if (discovered.contains(key)) return;
    if (discovered.size() >= limits.maxVolume()) {
      flags.limitReached = true;
      return;
    }
    discovered.add(key);
    pending.add(candidate.immutable());
  }

  private static boolean isPassable(Level level, BlockPos pos, BlockState state) {
    if (isOpenable(state)) return state.getValue(BlockStateProperties.OPEN);
    return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
  }

  private static boolean isOpenable(BlockState state) {
    return state.hasProperty(BlockStateProperties.OPEN);
  }

  private static void recordSurface(
      BlockState state, Map<ResourceLocation, Integer> surfaces) {
    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
    if (id != null) surfaces.merge(id, 1, Integer::sum);
  }

  private static void recordOpenable(
      Level level,
      BlockPos pos,
      BlockState state,
      Set<Long> counted,
      Map<ResourceLocation, MutableOpenableStats> openables) {
    BlockPos normalized = normalizeOpenablePosition(pos, state);
    if (!counted.add(normalized.asLong())) return;
    BlockState normalizedState = level.getBlockState(normalized);
    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(normalizedState.getBlock());
    if (id == null) return;
    MutableOpenableStats stats = openables.computeIfAbsent(id, ignored -> new MutableOpenableStats());
    stats.total++;
    if (normalizedState.hasProperty(BlockStateProperties.OPEN)
        && normalizedState.getValue(BlockStateProperties.OPEN)) stats.open++;
  }

  private static BlockPos normalizeOpenablePosition(BlockPos pos, BlockState state) {
    if (state.getBlock() instanceof DoorBlock
        && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return pos.below();
    return pos;
  }

  private static int distance(BlockPos origin, BlockPos pos) {
    return Math.max(
        Math.max(Math.abs(origin.getX() - pos.getX()), Math.abs(origin.getY() - pos.getY())),
        Math.abs(origin.getZ() - pos.getZ()));
  }

  private static Status determineStatus(ScanFlags flags, boolean noInterior, int openCount) {
    if (noInterior) return Status.NO_INTERIOR;
    if (flags.worldOpen) return Status.WORLD_OPEN;
    if (flags.unloaded) return Status.UNLOADED;
    if (flags.limitReached) return Status.LIMIT_REACHED;
    if (openCount > 0) return Status.OPENABLE_OPEN;
    return Status.SEALED;
  }

  private static final class MutableOpenableStats {
    int total;
    int open;
  }

  private static final class ScanFlags {
    boolean limitReached;
    boolean unloaded;
    boolean worldOpen;
  }

  private RoomScanner() {}
}
