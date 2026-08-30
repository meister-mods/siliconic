package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public final class CleanroomSuitItem extends ArmorItem {
  public CleanroomSuitItem(ArmorItem.Type type, Item.Properties properties) {
    super(ArmorMaterials.LEATHER, type, properties.stacksTo(1));
  }

  @Override
  public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
    int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
    return Siliconic.MOD_ID + ":textures/models/armor/cleanroom_suit_layer_" + layer + ".png";
  }

  public static boolean isFullyProtected(Entity entity) {
    if (!(entity instanceof LivingEntity living)) return false;
    return living.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CLEANROOM_HOOD.get())
        && living.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.CLEANROOM_CHESTPLATE.get())
        && living.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.CLEANROOM_LEGGINGS.get())
        && living.getItemBySlot(EquipmentSlot.FEET).is(ModItems.CLEANROOM_BOOTS.get());
  }
}
