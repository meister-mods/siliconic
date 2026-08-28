package io.github.meistermods.siliconic.power;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/** A thin, surface-mounted Forge Energy cable with redstone-like visual connections. */
@SuppressWarnings({"null", "deprecation"})
public class PowerCableBlock extends Block {
  public static final DirectionProperty SUPPORT = DirectionProperty.create("support");
  public static final BooleanProperty NORTH = BooleanProperty.create("north");
  public static final BooleanProperty EAST = BooleanProperty.create("east");
  public static final BooleanProperty SOUTH = BooleanProperty.create("south");
  public static final BooleanProperty WEST = BooleanProperty.create("west");
  public static final BooleanProperty UP = BooleanProperty.create("up");
  public static final BooleanProperty DOWN = BooleanProperty.create("down");
  private static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);

  static {
    CONNECTIONS.put(Direction.NORTH, NORTH);
    CONNECTIONS.put(Direction.EAST, EAST);
    CONNECTIONS.put(Direction.SOUTH, SOUTH);
    CONNECTIONS.put(Direction.WEST, WEST);
    CONNECTIONS.put(Direction.UP, UP);
    CONNECTIONS.put(Direction.DOWN, DOWN);
  }

  public PowerCableBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(SUPPORT, Direction.DOWN)
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false));
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    Direction support = context.getClickedFace().getOpposite();
    BlockState state = defaultBlockState().setValue(SUPPORT, support);
    if (!canSurvive(state, context.getLevel(), context.getClickedPos())) return null;
    return connections(state, context.getLevel(), context.getClickedPos());
  }

  @Override
  public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    Direction support = state.getValue(SUPPORT);
    BlockPos supportPos = pos.relative(support);
    return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, support.getOpposite());
  }

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighborState,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    if (direction == state.getValue(SUPPORT) && !canSurvive(state, level, pos))
      return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    return connections(state, level, pos);
  }

  @Override
  public void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
    super.onPlace(state, level, pos, oldState, moving);
    if (!oldState.is(this)) scheduleNearbyCableUpdates(level, pos);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    super.onRemove(state, level, pos, newState, moving);
    if (!newState.is(this)) scheduleNearbyCableUpdates(level, pos);
  }

  @Override
  public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!state.is(this)) return;
    if (!canSurvive(state, level, pos)) {
      level.destroyBlock(pos, true);
      return;
    }
    BlockState updated = connections(state, level, pos);
    if (updated != state) level.setBlock(pos, updated, 2);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(SUPPORT, NORTH, EAST, SOUTH, WEST, UP, DOWN);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    Direction support = state.getValue(SUPPORT);
    VoxelShape shape = dotShape(support);
    for (Direction direction : tangentDirections(support))
      if (state.getValue(property(direction)))
        shape = Shapes.or(shape, armShape(support, direction));
    return shape;
  }

  static List<BlockPos> connectedCables(Level level, BlockPos pos, BlockState state) {
    List<BlockPos> result = new ArrayList<>();
    Direction support = state.getValue(SUPPORT);
    for (Direction tangent : tangentDirections(support)) {
      BlockPos directPos = pos.relative(tangent);
      BlockState direct = level.getBlockState(directPos);
      if (direct.getBlock() instanceof PowerCableBlock && direct.getValue(SUPPORT) == support)
        result.add(directPos);

      BlockPos cornerPos = directPos.relative(support);
      BlockState corner = level.getBlockState(cornerPos);
      if (corner.getBlock() instanceof PowerCableBlock
          && corner.getValue(SUPPORT) == tangent.getOpposite()) result.add(cornerPos);
    }
    return result;
  }

  private BlockState connections(BlockState state, LevelAccessor level, BlockPos pos) {
    Direction support = state.getValue(SUPPORT);
    for (Direction direction : Direction.values()) {
      boolean connected =
          direction.getAxis() != support.getAxis()
              && (connectsDirectly(level, pos, support, direction)
                  || connectsAroundCorner(level, pos, support, direction));
      state = state.setValue(property(direction), connected);
    }
    return state;
  }

  private boolean connectsDirectly(
      LevelAccessor level, BlockPos pos, Direction support, Direction direction) {
    BlockPos neighborPos = pos.relative(direction);
    BlockState neighbor = level.getBlockState(neighborPos);
    if (neighbor.getBlock() instanceof PowerCableBlock)
      return neighbor.getValue(SUPPORT) == support;
    BlockEntity blockEntity = level.getBlockEntity(neighborPos);
    return blockEntity != null
        && blockEntity.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).isPresent();
  }

  private boolean connectsAroundCorner(
      LevelAccessor level, BlockPos pos, Direction support, Direction direction) {
    BlockState corner = level.getBlockState(pos.relative(direction).relative(support));
    return corner.getBlock() instanceof PowerCableBlock
        && corner.getValue(SUPPORT) == direction.getOpposite();
  }

  private void scheduleNearbyCableUpdates(Level level, BlockPos pos) {
    if (level.isClientSide) return;
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        for (int dz = -1; dz <= 1; dz++) {
          int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
          if (distance < 1 || distance > 2) continue;
          BlockPos candidate = pos.offset(dx, dy, dz);
          if (level.getBlockState(candidate).is(this)) level.scheduleTick(candidate, this, 1);
        }
  }

  private static BooleanProperty property(Direction direction) {
    return CONNECTIONS.get(direction);
  }

  private static Direction[] tangentDirections(Direction support) {
    return switch (support.getAxis()) {
      case X -> new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
      case Y -> new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
      case Z -> new Direction[] {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
    };
  }

  private static VoxelShape dotShape(Direction support) {
    return switch (support) {
      case DOWN -> box(6, 0, 6, 10, 2, 10);
      case UP -> box(6, 14, 6, 10, 16, 10);
      case NORTH -> box(6, 6, 0, 10, 10, 2);
      case SOUTH -> box(6, 6, 14, 10, 10, 16);
      case WEST -> box(0, 6, 6, 2, 10, 10);
      case EAST -> box(14, 6, 6, 16, 10, 10);
    };
  }

  private static VoxelShape armShape(Direction support, Direction direction) {
    return switch (support) {
      case DOWN ->
          switch (direction) {
            case NORTH -> box(7, 0, 0, 9, 2, 8);
            case EAST -> box(8, 0, 7, 16, 2, 9);
            case SOUTH -> box(7, 0, 8, 9, 2, 16);
            default -> box(0, 0, 7, 8, 2, 9);
          };
      case UP ->
          switch (direction) {
            case NORTH -> box(7, 14, 0, 9, 16, 8);
            case EAST -> box(8, 14, 7, 16, 16, 9);
            case SOUTH -> box(7, 14, 8, 9, 16, 16);
            default -> box(0, 14, 7, 8, 16, 9);
          };
      case NORTH ->
          switch (direction) {
            case EAST -> box(8, 7, 0, 16, 9, 2);
            case WEST -> box(0, 7, 0, 8, 9, 2);
            case UP -> box(7, 8, 0, 9, 16, 2);
            default -> box(7, 0, 0, 9, 8, 2);
          };
      case SOUTH ->
          switch (direction) {
            case EAST -> box(8, 7, 14, 16, 9, 16);
            case WEST -> box(0, 7, 14, 8, 9, 16);
            case UP -> box(7, 8, 14, 9, 16, 16);
            default -> box(7, 0, 14, 9, 8, 16);
          };
      case WEST ->
          switch (direction) {
            case NORTH -> box(0, 7, 0, 2, 9, 8);
            case SOUTH -> box(0, 7, 8, 2, 9, 16);
            case UP -> box(0, 8, 7, 2, 16, 9);
            default -> box(0, 0, 7, 2, 8, 9);
          };
      case EAST ->
          switch (direction) {
            case NORTH -> box(14, 7, 0, 16, 9, 8);
            case SOUTH -> box(14, 7, 8, 16, 9, 16);
            case UP -> box(14, 8, 7, 16, 16, 9);
            default -> box(14, 0, 7, 16, 8, 9);
          };
    };
  }
}
