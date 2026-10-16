package io.github.meistermods.siliconic.logistics;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Versions logistics topology so controllers stop using endpoints as soon as a pipe changes. */
@SuppressWarnings("null")
final class LogisticsNetworkTopology {
  private static final Map<Level, Long> REVISIONS = new WeakHashMap<>();

  static long revision(Level level) {
    return REVISIONS.getOrDefault(level, 0L);
  }

  static void invalidate(Level level) {
    if (!level.isClientSide) REVISIONS.merge(level, 1L, Long::sum);
  }

  static boolean chunksChanged(Level level, Map<ChunkPos, Boolean> chunks) {
    for (var entry : chunks.entrySet())
      if (level.getChunkSource().hasChunk(entry.getKey().x, entry.getKey().z) != entry.getValue())
        return true;
    return false;
  }

  static void trackChunks(Level level, BlockPos pos, Map<ChunkPos, Boolean> chunks) {
    // Keep unavailable neighbors so a route resumes when FULL status returns, even before the
    // previous chunk has finished its asynchronous unload/save lifecycle.
    for (int chunkX = (pos.getX() - 1) >> 4; chunkX <= (pos.getX() + 1) >> 4; chunkX++)
      for (int chunkZ = (pos.getZ() - 1) >> 4; chunkZ <= (pos.getZ() + 1) >> 4; chunkZ++)
        chunks.computeIfAbsent(
            new ChunkPos(chunkX, chunkZ),
            chunk -> level.getChunkSource().hasChunk(chunk.x, chunk.z));
  }

  private LogisticsNetworkTopology() {}
}
