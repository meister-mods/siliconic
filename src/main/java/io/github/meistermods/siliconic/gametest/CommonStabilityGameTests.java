package io.github.meistermods.siliconic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CommonStabilityGameTests {
  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preventsMenusWithoutRequiredOpeningData(GameTestHelper helper) {
    List<Block> machines =
        List.of(
            ModBlocks.COAL_GENERATOR.get(),
            ModBlocks.CONDITIONER.get(),
            ModBlocks.WAFER_ASSEMBLER.get(),
            ModBlocks.WAFER_DUPLICATOR.get(),
            ModBlocks.WAFER_INVERTER.get(),
            ModBlocks.WAFER_FABRICATOR.get(),
            ModBlocks.GATE_FABRICATOR.get(),
            ModBlocks.SILICON_ARC_FURNACE.get(),
            ModBlocks.REPROCESSOR.get(),
            ModBlocks.LOGISTICS_CONTROLLER.get());
    for (Block machine : machines) {
      helper.setBlock(GameTestFixtures.ORIGIN, machine);
      BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
      helper.assertTrue(
          helper.getLevel().getBlockEntity(pos) instanceof MenuProvider,
          "A machine must retain its provider for the Forge opening path");
      helper.assertTrue(
          helper.getLevel().getBlockState(pos).getMenuProvider(helper.getLevel(), pos) == null,
          "Vanilla spectator opening must not omit the machine's required extra menu data");
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void skipsSnapshotsForInvalidWaferEditIndices(GameTestHelper helper) {
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack wafer = new ItemStack(ModItems.SSI_WAFER.get());
    wafer.getOrCreateTag().putByteArray("AuditPayload", new byte[512 * 1024]);
    station.insertWafer(wafer);
    FakePlayer player =
        new FakePlayer(
            helper.getLevel(), new GameProfile(UUID.randomUUID(), "invalid_wafer_edits"));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    WaferMenu menu = new WaferMenu(1, player.getInventory(), station);
    player.containerMenu = menu;
    ItemStack before = station.getWafer().copy();
    com.sun.management.ThreadMXBean meter =
        ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean extended
                && extended.isThreadAllocatedMemorySupported()
                && extended.isThreadAllocatedMemoryEnabled()
            ? extended
            : null;
    long allocatedBefore =
        meter == null ? 0 : meter.getThreadAllocatedBytes(Thread.currentThread().getId());
    for (int repeat = 0; repeat < 4; repeat++) {
      menu.interactCell(player, -1, false);
      menu.interactCell(
          player, PrototypeWaferBlockEntity.GRID_SIZE * PrototypeWaferBlockEntity.GRID_SIZE, true);
      menu.cyclePinMode(-1);
      menu.cyclePinMode(4);
    }
    long allocated =
        meter == null
            ? 0
            : meter.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedBefore;
    helper.assertTrue(
        ItemStack.matches(before, station.getWafer()),
        "Invalid cell and pin edits must preserve the wafer design and metadata");
    helper.assertTrue(
        meter == null || allocated < 1_048_576,
        "Invalid edit indices must be rejected before copying a large wafer; allocated="
            + allocated);
    helper.succeed();
  }

  private CommonStabilityGameTests() {}
}
