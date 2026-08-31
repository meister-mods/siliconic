package io.github.meistermods.siliconic.silicon;

import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null", "deprecation"})
public class SiliconProcessorBlock extends BaseEntityBlock {
  public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
  private final MachineKind machineKind;

  public SiliconProcessorBlock(MachineKind machineKind, Properties properties) {
    super(properties);
    this.machineKind = machineKind;
    registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
  }

  public MachineKind machineKind() {
    return machineKind;
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new SiliconProcessorBlockEntity(pos, state);
  }

  @Override
  protected void createBlockStateDefinition(
      StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
    builder.add(ACTIVE);
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
        && level.getBlockEntity(pos) instanceof SiliconProcessorBlockEntity processor)
      NetworkHooks.openScreen(serverPlayer, processor, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  @Override
  public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    return level.getBlockEntity(pos) instanceof SiliconProcessorBlockEntity processor
        ? processor.analogSignal()
        : 0;
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type,
            ModBlockEntities.SILICON_PROCESSOR.get(),
            SiliconProcessorBlockEntity::serverTick);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    if (!state.is(newState.getBlock())
        && level.getBlockEntity(pos) instanceof SiliconProcessorBlockEntity processor)
      for (int slot = 0; slot < SiliconProcessorBlockEntity.SLOT_COUNT; slot++) {
        var stack = processor.items().getStackInSlot(slot);
        if (!stack.isEmpty())
          Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
      }
    super.onRemove(state, level, pos, newState, moving);
  }
}
