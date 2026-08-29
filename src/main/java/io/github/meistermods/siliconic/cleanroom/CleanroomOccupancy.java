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

  public static void update(Level level, BlockPos conditionerPos, RoomScanResult scan) {
    if (level.isClientSide) return;
    LevelClaims claims = LEVEL_CLAIMS.computeIfAbsent(level, ignored -> new LevelClaims());
    claims.update(
        conditionerPos.asLong(), scan.isSealed() ? scan.interiorPositions() : Set.of());
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

  private static final class LevelClaims {
    private final Map<Long, Set<Long>> positionsByConditioner = new HashMap<>();
    private final Map<Long, Integer> claimCounts = new HashMap<>();

    void update(long conditionerPos, Set<Long> nextPositions) {
      remove(conditionerPos);
      if (nextPositions.isEmpty()) return;
      Set<Long> copiedPositions = new HashSet<>(nextPositions);
      positionsByConditioner.put(conditionerPos, copiedPositions);
      copiedPositions.forEach(pos -> claimCounts.merge(pos, 1, Integer::sum));
    }

    void remove(long conditionerPos) {
      Set<Long> previousPositions = positionsByConditioner.remove(conditionerPos);
      if (previousPositions == null) return;
      for (long pos : previousPositions) {
        int remaining = claimCounts.getOrDefault(pos, 0) - 1;
        if (remaining <= 0) claimCounts.remove(pos);
        else claimCounts.put(pos, remaining);
      }
    }

    boolean contains(long pos) {
      return claimCounts.containsKey(pos);
    }

    boolean isEmpty() {
      return positionsByConditioner.isEmpty();
    }
  }

  private CleanroomOccupancy() {}
}
