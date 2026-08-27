package io.github.meistermods.siliconic.power;

import io.github.meistermods.siliconic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
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
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null", "deprecation"})
public class CoalGeneratorBlock extends BaseEntityBlock {
  public static final BooleanProperty LIT = BooleanProperty.create("lit");

  public CoalGeneratorBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(LIT, false));
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CoalGeneratorBlockEntity(pos, state);
  }

  @Override
  protected void createBlockStateDefinition(
      StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
    builder.add(LIT);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator))
      return InteractionResult.PASS;
    ItemStack held = player.getItemInHand(hand);
    if (ForgeHooks.getBurnTime(held, RecipeType.SMELTING) > 0 && generator.canInsertFuel(held)) {
      if (!level.isClientSide) {
        generator.insertFuel(held);
        if (!player.getAbilities().instabuild) held.shrink(1);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
    if (player.isShiftKeyDown() && held.isEmpty() && generator.hasFuel()) {
      if (!level.isClientSide)
        player.getInventory().placeItemBackInInventory(generator.removeFuel());
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
    if (!level.isClientSide)
      player.displayClientMessage(
          Component.translatable(
              "message.siliconic.generator_status",
              generator.getEnergyStored(),
              generator.getEnergyCapacity(),
              generator.getBurnTime(),
              generator.getFuelCount()),
          true);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
    if (!state.is(newState.getBlock())
        && level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator
        && generator.hasFuel())
      Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), generator.removeFuel());
    super.onRemove(state, level, pos, newState, moving);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type, ModBlockEntities.COAL_GENERATOR.get(), CoalGeneratorBlockEntity::serverTick);
  }
}
