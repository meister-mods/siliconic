package io.github.meistermods.siliconic.cleanroom;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Runtime index of the last valid sealed positions claimed by each live conditioner. */
@SuppressWarnings({"null"})
public final class CleanroomOccupancy {
  private static final Map<Level, LevelClaims> LEVEL_CLAIMS = new WeakHashMap<>();

  public static Set<Long> update(
      Level level, BlockPos conditionerPos, Set<Long> claimedPositions, int cleanliness) {
    if (level.isClientSide) return Set.of(conditionerPos.asLong());
    LevelClaims claims = LEVEL_CLAIMS.computeIfAbsent(level, ignored -> new LevelClaims());
    claims.update(conditionerPos.asLong(), claimedPositions, cleanliness);
    Set<Long> group = claims.group(conditionerPos.asLong());
    if (claims.isEmpty()) LEVEL_CLAIMS.remove(level);
    return group;
  }

  /** Keeps every claim for one connected cleanroom on the same shared cleanliness value. */
  public static void synchronizeCleanliness(
      Level level, Set<Long> conditionerPositions, int cleanliness) {
    if (level.isClientSide) return;
    LevelClaims claims = LEVEL_CLAIMS.get(level);
    if (claims != null) claims.setCleanliness(conditionerPositions, cleanliness);
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

  /** Returns the best current cleanliness when multiple live conditioner claims cover a machine. */
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

    Set<Long> group(long origin) {
      Set<Long> originPositions = positionsByConditioner.get(origin);
      if (originPositions == null) return Set.of(origin);

      Set<Long> connected = new HashSet<>();
      ArrayDeque<Long> pending = new ArrayDeque<>();
      connected.add(origin);
      pending.add(origin);
      while (!pending.isEmpty()) {
        Set<Long> currentPositions = positionsByConditioner.get(pending.removeFirst());
        if (currentPositions == null) continue;
        for (Map.Entry<Long, Set<Long>> entry : positionsByConditioner.entrySet()) {
          if (connected.contains(entry.getKey())) continue;
          if (!overlaps(currentPositions, entry.getValue())) continue;
          connected.add(entry.getKey());
          pending.addLast(entry.getKey());
        }
      }
      return Set.copyOf(connected);
    }

    void setCleanliness(Set<Long> conditionerPositions, int cleanliness) {
      int clamped = Math.max(0, Math.min(100, cleanliness));
      for (long conditionerPos : conditionerPositions)
        if (positionsByConditioner.containsKey(conditionerPos))
          cleanlinessByConditioner.put(conditionerPos, clamped);
    }

    private boolean overlaps(Set<Long> first, Set<Long> second) {
      Set<Long> smaller = first.size() <= second.size() ? first : second;
      Set<Long> larger = smaller == first ? second : first;
      for (long position : smaller) if (larger.contains(position)) return true;
      return false;
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
