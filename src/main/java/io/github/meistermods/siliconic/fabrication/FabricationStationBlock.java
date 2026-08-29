package io.github.meistermods.siliconic.fabrication;

import io.github.meistermods.siliconic.machine.HorizontalFacingEntityBlock;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null", "deprecation"})
public class FabricationStationBlock extends HorizontalFacingEntityBlock {
  public FabricationStationBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new FabricationStationBlockEntity(pos, state);
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
        && level.getBlockEntity(pos) instanceof FabricationStationBlockEntity station)
      NetworkHooks.openScreen(serverPlayer, station, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type,
            ModBlockEntities.FABRICATION_STATION.get(),
            FabricationStationBlockEntity::serverTick);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    if (!state.is(newState.getBlock())
        && level.getBlockEntity(pos) instanceof FabricationStationBlockEntity station)
      for (int slot = 0; slot < FabricationStationBlockEntity.SLOT_COUNT; slot++) {
        ItemStack stack = station.items().getStackInSlot(slot);
        if (!stack.isEmpty())
          Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
      }
    super.onRemove(state, level, pos, newState, moving);
  }
}
