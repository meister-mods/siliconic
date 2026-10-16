package io.github.meistermods.siliconic.power;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Discovers power receivers without loading chunks and versions cached network topology. */
@SuppressWarnings({"null"})
final class PowerNetworkTopology {
  record Receiver(BlockPos pos, Direction side) {
    Receiver {
      pos = pos.immutable();
    }
  }

  private static final Map<Level, Long> REVISIONS = new WeakHashMap<>();

  static long revision(Level level) {
    return REVISIONS.getOrDefault(level, 0L);
  }

  static void invalidate(Level level) {
    if (!level.isClientSide) REVISIONS.merge(level, 1L, Long::sum);
  }

  static List<Receiver> discover(
      Level level, BlockPos source, int maxCables, Map<ChunkPos, Boolean> cableChunks) {
    Set<Receiver> receivers = new LinkedHashSet<>();
    ArrayDeque<BlockPos> pendingCables = new ArrayDeque<>();
    Set<BlockPos> visitedCables = new HashSet<>();
    trackChunks(level, source, cableChunks);

    for (Direction direction : Direction.values()) {
      BlockPos neighborPos = source.relative(direction);
      if (!level.isLoaded(neighborPos)) continue;
      BlockState neighborState = level.getBlockState(neighborPos);
      if (PowerCableBlock.connectsToward(neighborState, direction.getOpposite()))
        pendingCables.add(neighborPos);
      else if (level.getBlockEntity(neighborPos) != null)
        receivers.add(new Receiver(neighborPos, direction.getOpposite()));
    }

    while (!pendingCables.isEmpty() && visitedCables.size() < maxCables) {
      BlockPos cablePos = pendingCables.removeFirst();
      if (!visitedCables.add(cablePos) || !level.isLoaded(cablePos)) continue;
      BlockState cableState = level.getBlockState(cablePos);
      if (!(cableState.getBlock() instanceof PowerCableBlock)) continue;
      trackChunks(level, cablePos, cableChunks);

      for (BlockPos connected : PowerCableBlock.connectedCables(level, cablePos, cableState))
        if (!visitedCables.contains(connected)) pendingCables.addLast(connected);

      for (Direction direction : Direction.values()) {
        BlockPos receiverPos = cablePos.relative(direction);
        if (!level.isLoaded(receiverPos) || receiverPos.equals(source)) continue;
        if (level.getBlockState(receiverPos).getBlock() instanceof PowerCableBlock) continue;
        if (!PowerCableBlock.canConnectReceiver(cableState, direction)) continue;
        BlockEntity blockEntity = level.getBlockEntity(receiverPos);
        if (blockEntity != null) receivers.add(new Receiver(receiverPos, direction.getOpposite()));
      }
    }

    return List.copyOf(receivers);
  }

  static boolean chunksChanged(Level level, Map<ChunkPos, Boolean> chunks) {
    for (var entry : chunks.entrySet())
      if (level.getChunkSource().hasChunk(entry.getKey().x, entry.getKey().z) != entry.getValue())
        return true;
    return false;
  }

  private static void trackChunks(Level level, BlockPos pos, Map<ChunkPos, Boolean> chunks) {
    // Corner and coated-block connections can reach two blocks from a cable. Remember unavailable
    // boundary chunks too: FULL availability may change without a complete unload/load event pair.
    for (int chunkX = (pos.getX() - 2) >> 4; chunkX <= (pos.getX() + 2) >> 4; chunkX++)
      for (int chunkZ = (pos.getZ() - 2) >> 4; chunkZ <= (pos.getZ() + 2) >> 4; chunkZ++)
        chunks.computeIfAbsent(
            new ChunkPos(chunkX, chunkZ),
            chunk -> level.getChunkSource().hasChunk(chunk.x, chunk.z));
  }

  private PowerNetworkTopology() {}
}
