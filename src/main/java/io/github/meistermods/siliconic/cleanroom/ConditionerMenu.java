package io.github.meistermods.siliconic.cleanroom;

import io.github.meistermods.siliconic.network.MenuDataSync;
import io.github.meistermods.siliconic.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"null"})
public class ConditionerMenu extends AbstractContainerMenu {
  private final ConditionerBlockEntity conditioner;

  public ConditionerMenu(int id, Inventory inventory, FriendlyByteBuf data) {
    this(
        id,
        inventory,
        (ConditionerBlockEntity) inventory.player.level().getBlockEntity(data.readBlockPos()));
  }

  public ConditionerMenu(int id, Inventory inventory, ConditionerBlockEntity conditioner) {
    super(ModMenus.CONDITIONER.get(), id);
    this.conditioner = conditioner;
    addDataSlots(conditioner.data());
  }

  public int energy() {
    return MenuDataSync.combine(conditioner.data().get(0), conditioner.data().get(1));
  }

  public int capacity() {
    return ConditionerBlockEntity.ENERGY_CAPACITY;
  }

  public int energyPerTick() {
    return ConditionerBlockEntity.ENERGY_PER_TICK;
  }

  public boolean powered() {
    return conditioner.data().get(2) != 0;
  }

  public int cleanliness() {
    return conditioner.data().get(3);
  }

  public int cleanlinessLimit() {
    return conditioner.data().get(4);
  }

  public int coatingCoverage() {
    return conditioner.data().get(5);
  }

  public int unprotectedEntities() {
    return conditioner.data().get(6);
  }

  public int conditionerCount() {
    return conditioner.data().get(7);
  }

  public int equipmentPollutionSources() {
    return conditioner.data().get(8);
  }

  public int blockPollutionSources() {
    return conditioner.data().get(9);
  }

  public int totalContaminationPerScan() {
    return Math.min(
        ConditionerBlockEntity.MAX_CLEANLINESS,
        unprotectedEntities() * ConditionerBlockEntity.CONTAMINATION_PER_UNPROTECTED_ENTITY
            + (equipmentPollutionSources() + blockPollutionSources())
                * ConditionerBlockEntity.CONTAMINATION_PER_POLLUTION_SOURCE);
  }

  public RoomScanResult lastScan() {
    return conditioner.lastScan();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    return !conditioner.isRemoved()
        && player.distanceToSqr(conditioner.getBlockPos().getCenter()) <= 64;
  }
}
