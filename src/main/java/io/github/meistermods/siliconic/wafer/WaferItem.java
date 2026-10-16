package io.github.meistermods.siliconic.wafer;

import io.github.meistermods.siliconic.network.WaferShareTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"null"})
public class WaferItem extends Item {
  public WaferItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  @Nullable
  @Override
  public CompoundTag getShareTag(ItemStack stack) {
    return WaferShareTag.encode(super.getShareTag(stack));
  }

  @Override
  public void readShareTag(ItemStack stack, @Nullable CompoundTag tag) {
    super.readShareTag(stack, WaferShareTag.decode(tag));
  }

  @Override
  public boolean isFoil(ItemStack stack) {
    return stack.hasTag() && stack.getTag().getBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG);
  }
}
