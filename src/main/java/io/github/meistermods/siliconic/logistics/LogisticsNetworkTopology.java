package io.github.meistermods.siliconic.logistics;

import java.util.Map;
import java.util.WeakHashMap;
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

  private LogisticsNetworkTopology() {}
}
