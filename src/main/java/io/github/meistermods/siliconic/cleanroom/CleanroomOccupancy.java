package io.github.meistermods.siliconic.cleanroom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Runtime index of positions covered by currently sealed conditioner rooms. */
@SuppressWarnings({"null"})
public final class CleanroomOccupancy {
  private static final Map<Level, LevelClaims> LEVEL_CLAIMS = new WeakHashMap<>();

  public static void update(
      Level level, BlockPos conditionerPos, RoomScanResult scan, int cleanliness) {
    if (level.isClientSide) return;
    LevelClaims claims = LEVEL_CLAIMS.computeIfAbsent(level, ignored -> new LevelClaims());
    claims.update(
        conditionerPos.asLong(),
        scan.isSealed() ? scan.interiorPositions() : Set.of(),
        cleanliness);
    if (claims.isEmpty()) LEVEL_CLAIMS.remove(level);
  }

  public static void remove(Level level, BlockPos conditionerPos) {
    if (level.isClientSide) return;
    LevelClaims claims = LEVEL_CLAIMS.get(level);
    if (claims == null) return;
    claims.remove(conditionerPos.asLong());
    if (claims.isEmpty()) LEVEL_CLAIMS.remove(level);
  }

  /**
   * Solid machines are scan surfaces rather than traversable positions, so a machine is considered
   * inside when its own position or any directly adjacent position belongs to a claimed interior.
   */
  public static boolean isMachineInside(Level level, BlockPos machinePos) {
    if (level == null || level.isClientSide) return false;
    LevelClaims claims = LEVEL_CLAIMS.get(level);
    if (claims == null) return false;
    if (claims.contains(machinePos.asLong())) return true;
    for (Direction direction : Direction.values())
      if (claims.contains(machinePos.relative(direction).asLong())) return true;
    return false;
  }

  /** Returns the best cleanliness available when multiple sealed rooms cover a machine. */
  public static int cleanlinessAtMachine(Level level, BlockPos machinePos) {
    if (level == null || level.isClientSide) return 0;
    LevelClaims claims = LEVEL_CLAIMS.get(level);
    return claims == null ? 0 : claims.cleanlinessAt(machinePos);
  }

  private static final class LevelClaims {
    private final Map<Long, Set<Long>> positionsByConditioner = new HashMap<>();
    private final Map<Long, Integer> claimCounts = new HashMap<>();
    private final Map<Long, Integer> cleanlinessByConditioner = new HashMap<>();

    void update(long conditionerPos, Set<Long> nextPositions, int cleanliness) {
      remove(conditionerPos);
      if (nextPositions.isEmpty()) return;
      Set<Long> copiedPositions = new HashSet<>(nextPositions);
      positionsByConditioner.put(conditionerPos, copiedPositions);
      cleanlinessByConditioner.put(conditionerPos, Math.max(0, Math.min(100, cleanliness)));
      copiedPositions.forEach(pos -> claimCounts.merge(pos, 1, Integer::sum));
    }

    void remove(long conditionerPos) {
      Set<Long> previousPositions = positionsByConditioner.remove(conditionerPos);
      if (previousPositions == null) return;
      cleanlinessByConditioner.remove(conditionerPos);
      for (long pos : previousPositions) {
        int remaining = claimCounts.getOrDefault(pos, 0) - 1;
        if (remaining <= 0) claimCounts.remove(pos);
        else claimCounts.put(pos, remaining);
      }
    }

    boolean contains(long pos) {
      return claimCounts.containsKey(pos);
    }

    int cleanlinessAt(BlockPos machinePos) {
      int best = 0;
      for (Map.Entry<Long, Set<Long>> entry : positionsByConditioner.entrySet())
        if (touches(entry.getValue(), machinePos))
          best = Math.max(best, cleanlinessByConditioner.getOrDefault(entry.getKey(), 0));
      return best;
    }

    private boolean touches(Set<Long> positions, BlockPos machinePos) {
      if (positions.contains(machinePos.asLong())) return true;
      for (Direction direction : Direction.values())
        if (positions.contains(machinePos.relative(direction).asLong())) return true;
      return false;
    }

    boolean isEmpty() {
      return positionsByConditioner.isEmpty();
    }
  }

  private CleanroomOccupancy() {}
}
