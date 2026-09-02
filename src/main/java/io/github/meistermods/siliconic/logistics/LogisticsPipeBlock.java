package io.github.meistermods.siliconic.logistics;

import io.github.meistermods.siliconic.registry.ModBlocks;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A free-standing, six-pixel-wide item pipe that connects on all six axes. */
@SuppressWarnings({"null", "deprecation"})
public class LogisticsPipeBlock extends Block {
  public static final BooleanProperty NORTH = BooleanProperty.create("north");
  public static final BooleanProperty EAST = BooleanProperty.create("east");
  public static final BooleanProperty SOUTH = BooleanProperty.create("south");
  public static final BooleanProperty WEST = BooleanProperty.create("west");
  public static final BooleanProperty UP = BooleanProperty.create("up");
  public static final BooleanProperty DOWN = BooleanProperty.create("down");
  private static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);
  private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
  private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

  static {
    CONNECTIONS.put(Direction.NORTH, NORTH);
    CONNECTIONS.put(Direction.EAST, EAST);
    CONNECTIONS.put(Direction.SOUTH, SOUTH);
    CONNECTIONS.put(Direction.WEST, WEST);
    CONNECTIONS.put(Direction.UP, UP);
    CONNECTIONS.put(Direction.DOWN, DOWN);
    ARMS.put(Direction.NORTH, box(5, 5, 0, 11, 11, 5));
    ARMS.put(Direction.EAST, box(11, 5, 5, 16, 11, 11));
    ARMS.put(Direction.SOUTH, box(5, 5, 11, 11, 11, 16));
    ARMS.put(Direction.WEST, box(0, 5, 5, 5, 11, 11));
    ARMS.put(Direction.UP, box(5, 11, 5, 11, 16, 11));
    ARMS.put(Direction.DOWN, box(5, 0, 5, 11, 5, 11));
  }

  public LogisticsPipeBlock(Properties properties) {
    super(properties);
    BlockState state = stateDefinition.any();
    for (BooleanProperty property : CONNECTIONS.values()) state = state.setValue(property, false);
    registerDefaultState(state);
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
  }

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighborState,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    return state.setValue(
        property(direction), connects(level, neighborPos, neighborState, direction.getOpposite()));
  }

  @Override
  public void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
    super.onPlace(state, level, pos, oldState, moving);
    if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, 1);
  }

  @Override
  public void neighborChanged(
      BlockState state,
      Level level,
      BlockPos pos,
      Block neighborBlock,
      BlockPos neighborPos,
      boolean moving) {
    super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moving);
    if (!level.isClientSide) level.scheduleTick(pos, this, 1);
  }

  @Override
  public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    BlockState updated = connections(state, level, pos);
    if (updated != state) level.setBlock(pos, updated, 2);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    VoxelShape shape = CORE;
    for (Direction direction : Direction.values())
      if (state.getValue(property(direction))) shape = Shapes.or(shape, ARMS.get(direction));
    return shape;
  }

  private static BlockState connections(BlockState state, LevelAccessor level, BlockPos pos) {
    for (Direction direction : Direction.values()) {
      BlockPos neighborPos = pos.relative(direction);
      if (level instanceof Level loadedLevel && !loadedLevel.isLoaded(neighborPos)) continue;
      state =
          state.setValue(
              property(direction),
              connects(
                  level, neighborPos, level.getBlockState(neighborPos), direction.getOpposite()));
    }
    return state;
  }

  private static boolean connects(
      LevelAccessor level, BlockPos pos, BlockState state, Direction sideFacingPipe) {
    if (state.getBlock() instanceof LogisticsPipeBlock
        || state.is(ModBlocks.LOGISTICS_CONTROLLER.get())) return true;
    if (level instanceof Level loadedLevel && !loadedLevel.isLoaded(pos)) return false;
    BlockEntity blockEntity = level.getBlockEntity(pos);
    return blockEntity != null && LogisticsItemHandlerAccess.isPresent(blockEntity, sideFacingPipe);
  }

  private static BooleanProperty property(Direction direction) {
    return CONNECTIONS.get(direction);
  }
}
