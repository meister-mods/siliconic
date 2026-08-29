package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.registry.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public final class CleanroomSuitItem extends DyeableArmorItem {
  public static final int DEFAULT_COLOR = 0xE9F4F6;

  public CleanroomSuitItem(ArmorItem.Type type, Item.Properties properties) {
    super(ArmorMaterials.LEATHER, type, properties.stacksTo(1));
  }

  @Override
  public int getColor(ItemStack stack) {
    return DEFAULT_COLOR;
  }

  public static boolean isFullyProtected(Entity entity) {
    if (!(entity instanceof LivingEntity living)) return false;
    return living.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CLEANROOM_HOOD.get())
        && living.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.CLEANROOM_CHESTPLATE.get())
        && living.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.CLEANROOM_LEGGINGS.get())
        && living.getItemBySlot(EquipmentSlot.FEET).is(ModItems.CLEANROOM_BOOTS.get());
  }
}
