package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

@SuppressWarnings({"null"})
public class RedstoneClockBlock extends Block {
  public static final BooleanProperty POWERED = BooleanProperty.create("powered");
  private static final int ON_TICKS = 2;
  private static final int OFF_TICKS = 18;

  public RedstoneClockBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(POWERED, false));
  }

  @Override
  public void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
    if (!level.isClientSide && !oldState.is(this)) level.scheduleTick(pos, this, OFF_TICKS);
  }

  @Override
  public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    if (!CleanroomOccupancy.isMachineInside(level, pos)) {
      if (state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, false), 3);
      level.scheduleTick(pos, this, OFF_TICKS);
      return;
    }
    boolean powered = !state.getValue(POWERED);
    level.setBlock(pos, state.setValue(POWERED, powered), 3);
    level.scheduleTick(pos, this, powered ? ON_TICKS : OFF_TICKS);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(POWERED);
  }

  @Override
  public boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return state.getValue(POWERED) ? 15 : 0;
  }

  @Override
  public int getDirectSignal(
      BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return getSignal(state, level, pos, direction);
  }
}
