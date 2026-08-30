package io.github.meistermods.siliconic.registry;

import io.github.meistermods.siliconic.Siliconic;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings({"null"})
public final class ModCreativeTabs {
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Siliconic.MOD_ID);
  public static final RegistryObject<CreativeModeTab> MATERIALS =
      TABS.register(
          "materials",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.siliconic.materials"))
                  .icon(() -> new ItemStack(ModItems.SILVER_INGOT.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.LEAD_ORE.get());
                        output.accept(ModItems.SILVER_ORE.get());
                        output.accept(ModItems.DEEPSLATE_LEAD_ORE.get());
                        output.accept(ModItems.DEEPSLATE_SILVER_ORE.get());
                        output.accept(ModItems.LEAD_BLOCK.get());
                        output.accept(ModItems.SILVER_BLOCK.get());
                        output.accept(ModItems.RAW_LEAD.get());
                        output.accept(ModItems.RAW_SILVER.get());
                        output.accept(ModItems.LEAD_INGOT.get());
                        output.accept(ModItems.SILVER_INGOT.get());
                        output.accept(ModItems.COPPER_NUGGET.get());
                        output.accept(ModItems.LEAD_NUGGET.get());
                        output.accept(ModItems.SILVER_NUGGET.get());
                        output.accept(ModItems.CRUDE_SILICON.get());
                        output.accept(ModItems.PURE_SILICON.get());
                        output.accept(ModItems.CONTAMINATED_CRUDE_SILICON.get());
                        output.accept(ModItems.CONTAMINATED_PURE_SILICON.get());
                        output.accept(ModItems.CONTAMINATED_WAFER.get());
                        output.accept(ModItems.CONTAMINATED_GATE.get());
                        output.accept(ModItems.COATED_BLOCK.get());
                        output.accept(ModItems.CABLE_COATED_BLOCK.get());
                        output.accept(ModItems.CLEANROOM_HOOD.get());
                        output.accept(ModItems.CLEANROOM_CHESTPLATE.get());
                        output.accept(ModItems.CLEANROOM_LEGGINGS.get());
                        output.accept(ModItems.CLEANROOM_BOOTS.get());
                        output.accept(ModItems.ANTISTATIC_FABRIC.get());
                      })
                  .build());
  public static final RegistryObject<CreativeModeTab> CIRCUITS =
      TABS.register(
          "circuits",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.siliconic.circuits"))
                  .icon(() -> new ItemStack(ModItems.SSI_WAFER.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.SSI_WAFER.get());
                        output.accept(ModItems.MSI_WAFER.get());
                        output.accept(ModItems.LSI_WAFER.get());
                        output.accept(ModItems.VLSI_WAFER.get());
                        output.accept(ModItems.ULSI_WAFER.get());
                        output.accept(ModItems.NOT_GATE.get());
                        output.accept(ModItems.AND_GATE.get());
                        output.accept(ModItems.OR_GATE.get());
                        output.accept(ModItems.XOR_GATE.get());
                        output.accept(ModItems.BUFFER_GATE.get());
                        output.accept(ModItems.DROP_GATE.get());
                        output.accept(ModItems.SWITCH_GATE.get());
                      })
                  .build());
  public static final RegistryObject<CreativeModeTab> MACHINES =
      TABS.register(
          "machines",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("itemGroup.siliconic.machines"))
                  .icon(() -> new ItemStack(ModItems.WAFER_ASSEMBLER.get()))
                  .displayItems(
                      (parameters, output) -> {
                        output.accept(ModItems.COAL_GENERATOR.get());
                        output.accept(ModItems.SILICON_ARC_FURNACE.get());
                        output.accept(ModItems.SILICON_PURIFIER.get());
                        output.accept(ModItems.CONDITIONER.get());
                        output.accept(ModItems.WAFER_ASSEMBLER.get());
                        output.accept(ModItems.CREATIVE_WAFER_ASSEMBLER.get());
                        output.accept(ModItems.GATE_ASSEMBLER.get());
                        output.accept(ModItems.WAFER_FABRICATOR.get());
                        output.accept(ModItems.WAFER_INVERTER.get());
                        output.accept(ModItems.WAFER_DUPLICATOR.get());
                        output.accept(ModItems.REPROCESSOR.get());
                        output.accept(ModItems.WAFER_GUARD.get());
                        output.accept(ModItems.CREATIVE_WAFER_GUARD.get());
                        output.accept(ModItems.REDSTONE_CLOCK.get());
                        output.accept(ModItems.POWER_CABLE.get());
                      })
                  .build());

  private ModCreativeTabs() {}
}
