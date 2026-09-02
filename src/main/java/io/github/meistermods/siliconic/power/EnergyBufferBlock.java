package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null", "deprecation"})
public class EnergyBufferBlock extends BaseEntityBlock {
  public EnergyBufferBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new EnergyBufferBlockEntity(pos, state);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnergyBufferBlockEntity buffer)
      player.displayClientMessage(
          Component.translatable(
              "message.siliconic.energy_buffer.status",
              buffer.energyStored(),
              buffer.energyCapacity(),
              buffer.comparatorSignal()),
          true);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  @Override
  public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof EnergyBufferBlockEntity buffer
        ? buffer.comparatorSignal()
        : 0;
  }

  @Override
  public void neighborChanged(
      BlockState state,
      Level level,
      BlockPos pos,
      net.minecraft.world.level.block.Block neighborBlock,
      BlockPos neighborPos,
      boolean moving) {
    super.neighborChanged(state, level, pos, neighborBlock, neighborPos, moving);
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof EnergyBufferBlockEntity buffer)
      buffer.invalidateNetworkCache();
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type, ModBlockEntities.ENERGY_BUFFER.get(), EnergyBufferBlockEntity::serverTick);
  }
}
