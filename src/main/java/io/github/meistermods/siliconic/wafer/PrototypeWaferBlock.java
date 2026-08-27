package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import io.github.meistermods.siliconic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null", "deprecation"})
public class PrototypeWaferBlock extends BaseEntityBlock {
  public static final BooleanProperty HAS_WAFER = BooleanProperty.create("has_wafer");

  public PrototypeWaferBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(HAS_WAFER, false));
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
    if (level.getBlockEntity(pos) instanceof PrototypeWaferBlockEntity station) {
      if (player.getItemInHand(hand).is(ModItems.SILVER_LEAD_POWER_CELL.get())) {
        if (!level.isClientSide) {
          if (station.addEnergy(20_000) > 0 && !player.getAbilities().instabuild)
            player.getItemInHand(hand).shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
      }
      if ((player.getItemInHand(hand).is(ModItems.SILICON_WAFER.get())
              || player.getItemInHand(hand).is(ModItems.LEVEL_2_WAFER.get()))
          && !station.hasWafer()) {
        if (!level.isClientSide) station.insertWafer(player.getItemInHand(hand));
        return InteractionResult.sidedSuccess(level.isClientSide);
      }
      if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty() && station.hasWafer()) {
        if (!level.isClientSide)
          player.getInventory().placeItemBackInInventory(station.removeWafer());
        return InteractionResult.sidedSuccess(level.isClientSide);
      }
      if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
        NetworkHooks.openScreen(serverPlayer, station, pos);
      }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type, ModBlockEntities.PROTOTYPE_WAFER.get(), PrototypeWaferBlockEntity::serverTick);
  }

  @Override
  protected void createBlockStateDefinition(
      StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
    builder.add(HAS_WAFER);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    if (!state.is(newState.getBlock())
        && level.getBlockEntity(pos) instanceof PrototypeWaferBlockEntity station
        && station.hasWafer()) {
      Containers.dropItemStack(
          level, pos.getX(), pos.getY(), pos.getZ(), station.takeWaferOnBreak());
    }
    super.onRemove(state, level, pos, newState, moving);
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
