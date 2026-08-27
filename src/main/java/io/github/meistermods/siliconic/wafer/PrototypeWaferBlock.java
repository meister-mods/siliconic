package io.github.meistermods.siliconic.wafer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class PrototypeWaferBlock extends BaseEntityBlock {
  public PrototypeWaferBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new PrototypeWaferBlockEntity(pos, state);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!level.isClientSide
        && player instanceof ServerPlayer serverPlayer
        && level.getBlockEntity(pos) instanceof MenuProvider provider) {
      NetworkHooks.openScreen(serverPlayer, provider, pos);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
    return level.getBlockEntity(pos) instanceof PrototypeWaferBlockEntity wafer
        ? wafer.getOutput(direction)
        : 0;
  }

  @Override
  public void neighborChanged(
      BlockState state,
      Level level,
      BlockPos pos,
      net.minecraft.world.level.block.Block block,
      BlockPos fromPos,
      boolean moving) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof PrototypeWaferBlockEntity wafer)
      wafer.refreshSignals();
  }
}
