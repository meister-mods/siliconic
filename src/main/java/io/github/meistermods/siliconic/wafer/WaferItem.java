package io.github.meistermods.siliconic.wafer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public class WaferItem extends Item {
  public WaferItem(Properties properties) {
    super(properties.stacksTo(1));
  }

  @Override
  public boolean isFoil(ItemStack stack) {
    return stack.hasTag() && stack.getTag().getBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG);
  }
}
