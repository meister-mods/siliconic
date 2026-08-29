package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.machine.HorizontalFacingEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class WaferInverterBlock extends HorizontalFacingEntityBlock {
  public WaferInverterBlock(Properties properties) {
    super(properties);
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new WaferInverterBlockEntity(pos, state);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof WaferInverterBlockEntity inverter))
      return InteractionResult.PASS;
    var held = player.getItemInHand(hand);
    if (PrototypeWaferBlockEntity.levelOf(held) > 0) {
      if (!level.isClientSide) {
        if (!inverter.isInsideCleanroom())
          player.displayClientMessage(
              Component.translatable("message.siliconic.machine.outside_cleanroom"), true);
        else if (!PrototypeWaferBlockEntity.isCompleted(held))
          player.displayClientMessage(
              Component.translatable("message.siliconic.wafer_inverter.require_completed"), true);
        else {
          boolean success = inverter.invert(held);
          if (success) player.getInventory().setChanged();
          player.displayClientMessage(
              Component.translatable(
                  success
                      ? "message.siliconic.wafer_inverter.success"
                      : "message.siliconic.wafer_inverter.no_energy",
                  inverter.costFor(held)),
              true);
        }
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
    if (!level.isClientSide) {
      if (!inverter.isInsideCleanroom())
        player.displayClientMessage(
            Component.translatable("message.siliconic.machine.outside_cleanroom"), true);
      else
        player.displayClientMessage(
            Component.translatable(
                "message.siliconic.wafer_inverter.status", inverter.getEnergyStored(), 50_000),
            true);
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
