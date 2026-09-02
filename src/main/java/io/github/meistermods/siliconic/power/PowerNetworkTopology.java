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

  static List<Receiver> discover(Level level, BlockPos source, int maxCables) {
    Set<Receiver> receivers = new LinkedHashSet<>();
    ArrayDeque<BlockPos> pendingCables = new ArrayDeque<>();
    Set<BlockPos> visitedCables = new HashSet<>();

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

      for (BlockPos connected : PowerCableBlock.connectedCables(level, cablePos, cableState))
        if (!visitedCables.contains(connected)) pendingCables.addLast(connected);

      for (Direction direction : Direction.values()) {
        BlockPos receiverPos = cablePos.relative(direction);
        if (!level.isLoaded(receiverPos) || receiverPos.equals(source)) continue;
        if (level.getBlockState(receiverPos).getBlock() instanceof PowerCableBlock) continue;
        if (!PowerCableBlock.connectsToward(cableState, direction)) continue;
        BlockEntity blockEntity = level.getBlockEntity(receiverPos);
        if (blockEntity != null) receivers.add(new Receiver(receiverPos, direction.getOpposite()));
      }
    }

    return List.copyOf(receivers);
  }

  private PowerNetworkTopology() {}
}
