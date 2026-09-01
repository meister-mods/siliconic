package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.cleanroom.CableCoatedBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/** A thin, surface-mounted Forge Energy cable with redstone-like visual connections. */
@SuppressWarnings({"null", "deprecation"})
public class PowerCableBlock extends Block {
  public static final EnumProperty<Attachment> ATTACHMENT =
      EnumProperty.create("attachment", Attachment.class);
  public static final BooleanProperty NORTH = BooleanProperty.create("north");
  public static final BooleanProperty EAST = BooleanProperty.create("east");
  public static final BooleanProperty SOUTH = BooleanProperty.create("south");
  public static final BooleanProperty WEST = BooleanProperty.create("west");
  public static final BooleanProperty UP = BooleanProperty.create("up");
  public static final BooleanProperty DOWN = BooleanProperty.create("down");
  public static final BooleanProperty SHARED_POSITIVE_SECOND =
      BooleanProperty.create("shared_positive_second");
  public static final BooleanProperty SHARED_NEGATIVE_SECOND =
      BooleanProperty.create("shared_negative_second");
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
            .setValue(ATTACHMENT, Attachment.DOWN)
            .setValue(NORTH, false)
            .setValue(EAST, false)
            .setValue(SOUTH, false)
            .setValue(WEST, false)
            .setValue(UP, false)
            .setValue(DOWN, false)
            .setValue(SHARED_POSITIVE_SECOND, false)
            .setValue(SHARED_NEGATIVE_SECOND, false));
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    Direction support = context.getClickedFace().getOpposite();
    BlockPos pos = context.getClickedPos();
    if (!canAttachTo(context.getLevel(), pos, support)) return null;

    BlockState existing = context.getLevel().getBlockState(pos);
    Attachment attachment =
        existing.is(this)
            ? existing.getValue(ATTACHMENT).with(support)
            : Attachment.single(support);
    if (attachment == null) return null;
    BlockState state =
        (existing.is(this) ? existing : defaultBlockState()).setValue(ATTACHMENT, attachment);
    return connections(state, context.getLevel(), pos);
  }

  @Override
  public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
    if (!(context.getItemInHand().getItem() instanceof BlockItem item) || item.getBlock() != this)
      return false;
    Direction support = context.getClickedFace().getOpposite();
    Attachment attachment = state.getValue(ATTACHMENT);
    return !attachment.contains(support)
        && attachment.with(support) != null
        && canAttachTo(context.getLevel(), context.getClickedPos(), support);
  }

  @Override
  public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
    for (Direction support : state.getValue(ATTACHMENT).faces())
      if (canAttachTo(level, pos, support)) return true;
    return false;
  }

  @Override
  public BlockState updateShape(
      BlockState state,
      Direction direction,
      BlockState neighborState,
      LevelAccessor level,
      BlockPos pos,
      BlockPos neighborPos) {
    Attachment valid = validAttachment(state.getValue(ATTACHMENT), level, pos);
    if (valid == null) return Blocks.AIR.defaultBlockState();
    return connections(state.setValue(ATTACHMENT, valid), level, pos);
  }

  @Override
  public void setPlacedBy(
      Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    scheduleNearbyCableUpdates(level, pos);
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
    Attachment valid = validAttachment(state.getValue(ATTACHMENT), level, pos);
    if (valid == null) {
      level.destroyBlock(pos, true);
      return;
    }
    BlockState updated = connections(state.setValue(ATTACHMENT, valid), level, pos);
    if (updated != state) {
      level.setBlock(pos, updated, 2);
      if (updated.getValue(ATTACHMENT) != state.getValue(ATTACHMENT))
        scheduleNearbyCableUpdates(level, pos);
    }
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(
        ATTACHMENT,
        NORTH,
        EAST,
        SOUTH,
        WEST,
        UP,
        DOWN,
        SHARED_POSITIVE_SECOND,
        SHARED_NEGATIVE_SECOND);
  }

  @Override
  public VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    VoxelShape shape = Shapes.empty();
    Attachment attachment = state.getValue(ATTACHMENT);
    Direction[] supports = attachment.faces();
    for (int supportIndex = 0; supportIndex < supports.length; supportIndex++) {
      Direction support = supports[supportIndex];
      shape = Shapes.or(shape, dotShape(support));
      for (Direction direction : tangentDirections(support))
        if (state.getValue(property(direction))
            && usesSurface(state, attachment, direction, supportIndex))
          shape = Shapes.or(shape, armShape(support, direction));
    }
    return shape;
  }

  @Override
  public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    return List.of(new ItemStack(this, state.getValue(ATTACHMENT).faceCount()));
  }

  static List<BlockPos> connectedCables(Level level, BlockPos pos, BlockState state) {
    Set<BlockPos> result = new LinkedHashSet<>();
    Attachment attachment = state.getValue(ATTACHMENT);
    for (Direction support : attachment.faces())
      for (Direction tangent : tangentDirections(support)) {
        if (!state.getValue(property(tangent))) continue;

        BlockPos directPos = pos.relative(tangent);
        BlockState direct = level.getBlockState(directPos);
        if (direct.getBlock() instanceof PowerCableBlock
            && direct.getValue(ATTACHMENT).contains(support)
            && direct.getValue(property(tangent.getOpposite()))) result.add(directPos);

        BlockPos cornerPos = directPos.relative(support);
        BlockState corner = level.getBlockState(cornerPos);
        if (corner.getBlock() instanceof PowerCableBlock
            && corner.getValue(ATTACHMENT).contains(tangent.getOpposite())
            && corner.getValue(property(support.getOpposite()))) result.add(cornerPos);
      }
    addCableCoatedBlockConnections(level, pos, attachment, result);
    return new ArrayList<>(result);
  }

  static boolean connectsToward(BlockState state, Direction direction) {
    return state.getBlock() instanceof PowerCableBlock
        && (state.getValue(property(direction))
            || state.getValue(ATTACHMENT).contains(direction));
  }

  private static void addCableCoatedBlockConnections(
      Level level, BlockPos pos, Attachment attachment, Set<BlockPos> result) {
    for (Direction support : attachment.faces()) {
      BlockPos cleanroomBlockPos = pos.relative(support);
      if (!(level.getBlockState(cleanroomBlockPos).getBlock() instanceof CableCoatedBlock))
        continue;

      for (Direction candidateSupport : Direction.values()) {
        BlockPos candidatePos = cleanroomBlockPos.relative(candidateSupport.getOpposite());
        if (candidatePos.equals(pos)) continue;
        BlockState candidate = level.getBlockState(candidatePos);
        if (candidate.getBlock() instanceof PowerCableBlock
            && candidate.getValue(ATTACHMENT).contains(candidateSupport)) result.add(candidatePos);
      }
    }
  }

  private BlockState connections(BlockState state, LevelAccessor level, BlockPos pos) {
    Attachment attachment = state.getValue(ATTACHMENT);
    Direction[] supports = attachment.faces();
    state = state.setValue(SHARED_POSITIVE_SECOND, false).setValue(SHARED_NEGATIVE_SECOND, false);
    for (Direction direction : Direction.values()) {
      boolean connected = attachment.faceCount() == 2 && attachment.contains(direction);
      boolean sharedDirection = isSharedDirection(attachment, direction);
      boolean useSecondSurface = false;
      for (int supportIndex = 0; supportIndex < supports.length; supportIndex++) {
        Direction support = supports[supportIndex];
        if (direction.getAxis() == support.getAxis()) continue;
        boolean faceConnected =
            connectsDirectly(level, pos, support, direction)
                || connectsAroundCorner(level, pos, support, direction);
        connected |= faceConnected;
        if (sharedDirection && supportIndex == 1 && faceConnected) useSecondSurface = true;
      }
      if (sharedDirection)
        state = state.setValue(sharedSurfaceProperty(direction), useSecondSurface);
      state = state.setValue(property(direction), connected);
    }
    return state;
  }

  private static boolean usesSurface(
      BlockState state, Attachment attachment, Direction direction, int supportIndex) {
    if (!isSharedDirection(attachment, direction)) return true;
    boolean useSecond = state.getValue(sharedSurfaceProperty(direction));
    return supportIndex == (useSecond ? 1 : 0);
  }

  private static boolean isSharedDirection(Attachment attachment, Direction direction) {
    if (attachment.faceCount() != 2) return false;
    for (Direction support : attachment.faces())
      if (direction.getAxis() == support.getAxis()) return false;
    return true;
  }

  private static BooleanProperty sharedSurfaceProperty(Direction direction) {
    return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
        ? SHARED_POSITIVE_SECOND
        : SHARED_NEGATIVE_SECOND;
  }

  private boolean connectsDirectly(
      LevelAccessor level, BlockPos pos, Direction support, Direction direction) {
    BlockPos neighborPos = pos.relative(direction);
    BlockState neighbor = level.getBlockState(neighborPos);
    if (neighbor.getBlock() instanceof PowerCableBlock)
      return neighbor.getValue(ATTACHMENT).contains(support);
    BlockEntity blockEntity = level.getBlockEntity(neighborPos);
    return blockEntity != null
        && blockEntity.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).isPresent();
  }

  private boolean connectsAroundCorner(
      LevelAccessor level, BlockPos pos, Direction support, Direction direction) {
    BlockState corner = level.getBlockState(pos.relative(direction).relative(support));
    return corner.getBlock() instanceof PowerCableBlock
        && corner.getValue(ATTACHMENT).contains(direction.getOpposite());
  }

  @Nullable
  private Attachment validAttachment(Attachment attachment, LevelReader level, BlockPos pos) {
    List<Direction> valid = new ArrayList<>();
    for (Direction support : attachment.faces())
      if (canAttachTo(level, pos, support)) valid.add(support);
    return Attachment.from(valid);
  }

  private boolean canAttachTo(LevelReader level, BlockPos pos, Direction support) {
    BlockPos supportPos = pos.relative(support);
    return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, support.getOpposite());
  }

  private void scheduleNearbyCableUpdates(Level level, BlockPos pos) {
    if (level.isClientSide) return;
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        for (int dz = -1; dz <= 1; dz++) {
          int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
          if (distance > 2) continue;
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

  public enum Attachment implements StringRepresentable {
    DOWN("down", Direction.DOWN),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST),
    DOWN_NORTH("down_north", Direction.DOWN, Direction.NORTH),
    DOWN_SOUTH("down_south", Direction.DOWN, Direction.SOUTH),
    DOWN_WEST("down_west", Direction.DOWN, Direction.WEST),
    DOWN_EAST("down_east", Direction.DOWN, Direction.EAST),
    UP_NORTH("up_north", Direction.UP, Direction.NORTH),
    UP_SOUTH("up_south", Direction.UP, Direction.SOUTH),
    UP_WEST("up_west", Direction.UP, Direction.WEST),
    UP_EAST("up_east", Direction.UP, Direction.EAST),
    NORTH_WEST("north_west", Direction.NORTH, Direction.WEST),
    NORTH_EAST("north_east", Direction.NORTH, Direction.EAST),
    SOUTH_WEST("south_west", Direction.SOUTH, Direction.WEST),
    SOUTH_EAST("south_east", Direction.SOUTH, Direction.EAST);

    private final String name;
    private final Direction[] faces;

    Attachment(String name, Direction... faces) {
      this.name = name;
      this.faces = faces;
    }

    @Override
    public String getSerializedName() {
      return name;
    }

    public Direction[] faces() {
      return faces.clone();
    }

    public int faceCount() {
      return faces.length;
    }

    public boolean contains(Direction direction) {
      for (Direction face : faces) if (face == direction) return true;
      return false;
    }

    @Nullable
    public Attachment with(Direction direction) {
      if (contains(direction)) return this;
      if (faces.length != 1 || faces[0].getAxis() == direction.getAxis()) return null;
      return pair(faces[0], direction);
    }

    public static Attachment single(Direction direction) {
      return switch (direction) {
        case DOWN -> DOWN;
        case UP -> UP;
        case NORTH -> NORTH;
        case SOUTH -> SOUTH;
        case WEST -> WEST;
        case EAST -> EAST;
      };
    }

    @Nullable
    public static Attachment from(List<Direction> faces) {
      if (faces.isEmpty()) return null;
      if (faces.size() == 1) return single(faces.get(0));
      return pair(faces.get(0), faces.get(1));
    }

    @Nullable
    private static Attachment pair(Direction first, Direction second) {
      for (Attachment attachment : values())
        if (attachment.faces.length == 2
            && attachment.contains(first)
            && attachment.contains(second)) return attachment;
      return null;
    }
  }
}
