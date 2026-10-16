package io.github.meistermods.siliconic.gametest;

import com.mojang.authlib.GameProfile;
import io.github.meistermods.siliconic.Siliconic;
import io.github.meistermods.siliconic.cleanroom.CleanroomOccupancy;
import io.github.meistermods.siliconic.network.NetworkNbtSize;
import io.github.meistermods.siliconic.network.WaferShareTag;
import io.github.meistermods.siliconic.recipe.MachineKind;
import io.github.meistermods.siliconic.recipe.MachineProcess;
import io.github.meistermods.siliconic.recipe.ProcessInput;
import io.github.meistermods.siliconic.recipe.ProcessRecipeState;
import io.github.meistermods.siliconic.registry.ModBlocks;
import io.github.meistermods.siliconic.registry.ModItems;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity.CellType;
import io.github.meistermods.siliconic.wafer.WaferDuplicatorBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferDuplicatorMenu;
import io.github.meistermods.siliconic.wafer.WaferInverterBlockEntity;
import io.github.meistermods.siliconic.wafer.WaferInverterMenu;
import io.github.meistermods.siliconic.wafer.WaferMenu;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/** Regression coverage for legacy wafer designs and circuit persistence. */
@SuppressWarnings({"null"})
@GameTestHolder(Siliconic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaferStabilityGameTests {
  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesCompressedWaferNetworkContracts(GameTestHelper helper)
      throws IOException {
    ItemStack source = networkWaferFixture(18, 18, false);
    CompoundTag custom = new CompoundTag();
    custom.putInt("Aa", 1);
    custom.putInt("BB", 2);
    ListTag typedEmpty =
        ListTag.TYPE.load(
            new DataInputStream(new ByteArrayInputStream(new byte[] {3, 0, 0, 0, 0})),
            0,
            new NbtAccounter(NetworkNbtSize.LIMIT));
    custom.put("EmptyList", typedEmpty);
    source.getOrCreateTag().put("Custom", custom);
    // A raw full item may legitimately use the marker among unrelated custom fields.
    source.getOrCreateTag().putInt(WaferShareTag.MARKER, 99);
    source.getOrCreateTag().putString("Payload", "ordinary custom data");
    ItemStack equivalent = source.copy();
    CompoundTag otherCustom = new CompoundTag();
    otherCustom.putInt("BB", 2);
    otherCustom.putInt("Aa", 1);
    otherCustom.put("EmptyList", new ListTag());
    equivalent.getOrCreateTag().put("Custom", otherCustom);
    CompoundTag before = source.save(new CompoundTag());
    CompoundTag shared = source.getShareTag();
    helper.assertTrue(
        shared != source.getTag()
            && shared.equals(equivalent.getShareTag())
            && shared.equals(source.getShareTag())
            && typedEmpty.getElementType() == 3,
        "Compression must be deterministic for equal nested NBT without mutating typed empty"
            + " lists");
    ItemStack alreadyShared = source.copy();
    alreadyShared.setTag(shared);
    helper.assertTrue(
        shared.equals(alreadyShared.getShareTag()),
        "Forge share->setTag->share must not wrap an already compressed wafer again");
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    ItemStack restored;
    try {
      buffer.writeItem(source);
      restored = buffer.readItem();
      helper.assertTrue(
          ItemStack.matches(source, restored), "Item reads must restore the full wafer");
      buffer.clear();
      new ServerboundSetCreativeModeSlotPacket(36, restored).write(buffer);
      ItemStack creative = new ServerboundSetCreativeModeSlotPacket(buffer).getItem();
      helper.assertTrue(
          ItemStack.matches(source, creative),
          "Creative cloning must preserve completed status, names, custom data and every child");
      buffer.clear();
      buffer.writeItemStack(source, false);
      helper.assertTrue(
          ItemStack.matches(source, buffer.readItem()),
          "Uncompressed full-item packets with custom marker fields must remain lossless");
    } finally {
      buffer.release();
    }
    helper.assertTrue(
        StrictNBTIngredient.of(source).test(equivalent)
            && StrictNBTIngredient.of(source).test(restored),
        "Actual Forge strict ingredients must preserve equal-NBT matching after compression");
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "wafer_wire_test");
    ProcessRecipeState original = new ProcessRecipeState();
    original.select(networkRecipe(id, source));
    CompoundTag saved = new CompoundTag();
    original.save(saved, true);
    ProcessRecipeState reloaded = new ProcessRecipeState();
    reloaded.load(saved);
    helper.assertTrue(
        reloaded.select(networkRecipe(id, equivalent))
            && before.equals(source.save(new CompoundTag())),
        "Equal network tags must preserve persisted recipe signatures and the complete source");
    CompoundTag nan = source.getTag().copy();
    nan.putFloat("CustomNaN", Float.NaN);
    helper.assertTrue(
        WaferShareTag.encode(nan) == nan,
        "Custom NaN tags must retain vanilla exact-equality semantics through raw synchronization");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", timeoutTicks = 1200)
  public static void encodesInventoriesOfRepeatedNestedWafers(GameTestHelper helper) {
    for (ItemStack source :
        List.of(networkWaferFixture(18, 18, false), networkWaferFixture(21, 81, true))) {
      CompoundTag before = source.save(new CompoundTag());
      for (int filled : new int[] {36, 164}) {
        NonNullList<ItemStack> items =
            NonNullList.withSize(filled == 36 ? 46 : 164, ItemStack.EMPTY);
        int first = filled == 36 ? 9 : 0;
        for (int slot = 0; slot < filled; slot++) items.set(first + slot, source);
        var packet = new ClientboundContainerSetContentPacket(1, 0, items, ItemStack.EMPTY);
        EmbeddedChannel channel = new EmbeddedChannel(new PacketEncoder(PacketFlow.CLIENTBOUND));
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        try {
          helper.assertTrue(
              channel.writeOutbound(packet),
              "Repeated normal wafer inventories must pass the actual 8MiB packet encoder");
        } finally {
          channel.finishAndReleaseAll();
        }
      }
      helper.assertTrue(
          before.equals(source.save(new CompoundTag())),
          "Encoding a large inventory must not alter any original circuit data");
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void boundsCompressedWaferDecoding(GameTestHelper helper) throws IOException {
    CompoundTag source = new CompoundTag();
    source.putByteArray("Padding", new byte[8192]);
    CompoundTag shared = WaferShareTag.encode(source);
    for (String kind : List.of("version", "type", "empty", "gzip", "truncated", "length")) {
      CompoundTag malformed = shared.copy();
      switch (kind) {
        case "version" -> malformed.putInt(WaferShareTag.MARKER, 2);
        case "type" -> malformed.putString(WaferShareTag.MARKER, "1");
        case "empty" -> malformed.putByteArray("Payload", new byte[0]);
        case "gzip" -> malformed.putByteArray("Payload", new byte[] {1, 2, 3, 4});
        case "truncated" -> {
          byte[] data = malformed.getByteArray("Payload");
          malformed.putByteArray("Payload", java.util.Arrays.copyOf(data, data.length - 1));
        }
        case "length" ->
            malformed.putByteArray("Payload", new byte[(int) NetworkNbtSize.LIMIT + 1]);
      }
      assertCompressedTagRejected(helper, malformed, kind);
    }
    CompoundTag boundary = new CompoundTag();
    boundary.putByteArray("Boundary", new byte[0]);
    int length = (int) (NetworkNbtSize.LIMIT - NetworkNbtSize.accountedSize(boundary));
    boundary.putByteArray("Boundary", new byte[length]);
    helper.assertTrue(
        boundary.equals(WaferShareTag.decode(WaferShareTag.encode(boundary))),
        "Decoded NBT at the exact 2MiB accounting boundary must remain valid");
    boundary.putByteArray("Boundary", new byte[length + 1]);
    assertCompressedTagRejected(helper, uncheckedCompressedTag(boundary, false), "2MiB+1");
    helper.assertTrue(
        WaferShareTag.encode(boundary) == boundary,
        "Previously unshareable full tags must retain raw behavior rather than bypass the bound");
    CompoundTag deep = new CompoundTag();
    for (int level = 0; level < 512; level++) {
      CompoundTag parent = new CompoundTag();
      parent.put("Child", deep);
      deep = parent;
    }
    helper.assertTrue(
        deep.equals(WaferShareTag.decode(WaferShareTag.encode(deep))),
        "Depth 512 remains valid after compression");
    CompoundTag tooDeep = new CompoundTag();
    tooDeep.put("Child", deep);
    assertCompressedTagRejected(helper, uncheckedCompressedTag(tooDeep, false), "depth 513");
    assertCompressedTagRejected(helper, uncheckedCompressedTag(source, true), "trailing data");
    ByteArrayOutputStream emptyMember = new ByteArrayOutputStream();
    new GZIPOutputStream(emptyMember).close();
    byte[] originalPayload = shared.getByteArray("Payload");
    for (int members : new int[] {1, 100_000}) {
      ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
      concatenated.write(originalPayload);
      for (int member = 0; member < members; member++)
        concatenated.write(emptyMember.toByteArray());
      CompoundTag malformed = shared.copy();
      malformed.putByteArray("Payload", concatenated.toByteArray());
      assertCompressedTagRejected(helper, malformed, "concatenated empty gzip members " + members);
    }
    byte[] trailingCompressed =
        java.util.Arrays.copyOf(originalPayload, originalPayload.length + 1);
    trailingCompressed[trailingCompressed.length - 1] = 42;
    CompoundTag compressedTail = shared.copy();
    compressedTail.putByteArray("Payload", trailingCompressed);
    assertCompressedTagRejected(helper, compressedTail, "trailing compressed bytes");
    CompoundTag ordinary = shared.copy();
    ordinary.putString("ExtraCustomField", "raw full NBT");
    helper.assertTrue(
        WaferShareTag.decode(ordinary) == ordinary,
        "A marker plus unrelated extra fields must be preserved as an ordinary raw tag");
    byte[] entropy = new byte[32768];
    new java.util.Random(12345).nextBytes(entropy);
    CompoundTag opaque = new CompoundTag();
    opaque.putByteArray("Opaque", entropy);
    helper.assertTrue(
        WaferShareTag.encode(opaque) == opaque,
        "Incompressible custom tags must avoid expanding the original item representation");
    helper.succeed();
  }

  private static ItemStack networkWaferFixture(int width, int children, boolean blankLeaf) {
    ItemStack leaf = new ItemStack(ModItems.SSI_WAFER.get());
    leaf.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    if (!blankLeaf) {
      CompoundTag design = leaf.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
      byte[] cells = new byte[81];
      cells[40] = (byte) CellType.COPPER.ordinal();
      design.putByteArray("Cells", cells);
      design.putByteArray("Rotations", new byte[81]);
      design.putByteArray("ConductorModes", new byte[81]);
      design.putByteArray("DropAmounts", new byte[81]);
    }
    ItemStack child = waferWithChips(new ItemStack(ModItems.MSI_WAFER.get()), leaf, children);
    child.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    CompoundTag childDesign = child.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    for (String key : List.of("Rotations", "ConductorModes", "DropAmounts"))
      childDesign.putByteArray(key, new byte[81]);
    ItemStack source = waferWithChips(new ItemStack(ModItems.LSI_WAFER.get()), child, width);
    CompoundTag sourceDesign = source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    for (String key : List.of("Rotations", "ConductorModes", "DropAmounts"))
      sourceDesign.putByteArray(key, new byte[81]);
    source.setHoverName(Component.literal("Network archive"));
    source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    PrototypeWaferBlockEntity.mirrorHorizontally(source);
    return source;
  }

  private static MachineProcess networkRecipe(ResourceLocation id, ItemStack wafer) {
    return new MachineProcess(
        id,
        MachineKind.SILICON_ARC_FURNACE,
        List.of(new ProcessInput(-1, StrictNBTIngredient.of(wafer), 1)),
        Items.PAPER,
        1,
        List.of(wafer),
        10,
        1,
        false);
  }

  private static CompoundTag uncheckedCompressedTag(CompoundTag source, boolean trailing)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(bytes))) {
      NbtIo.write(source, output);
      if (trailing) output.writeByte(42);
    }
    CompoundTag envelope = new CompoundTag();
    envelope.putInt(WaferShareTag.MARKER, WaferShareTag.VERSION);
    envelope.putByteArray("Payload", bytes.toByteArray());
    return envelope;
  }

  private static void assertCompressedTagRejected(
      GameTestHelper helper, CompoundTag malformed, String context) {
    ItemStack target = new ItemStack(ModItems.SSI_WAFER.get());
    target.getOrCreateTag().putString("Original", "preserve on failure");
    CompoundTag before = target.getTag().copy();
    boolean rejected = false;
    try {
      target.readShareTag(malformed);
    } catch (RuntimeException expected) {
      rejected = true;
    }
    helper.assertTrue(
        rejected && before.equals(target.getTag()),
        "Compressed NBT must reject " + context + " without replacing existing item data");
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void countsLegacyTracesBeforeDuplicating(GameTestHelper helper) {
    ItemStack wafer = legacyWafer(0, 9);
    wafer.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    List<ItemStack> requirements = PrototypeWaferBlockEntity.requiredComponents(wafer);
    helper.assertTrue(
        requirements.size() == 1
            && requirements.get(0).is(ModItems.COPPER_NUGGET.get())
            && requirements.get(0).getCount() == 2,
        "Legacy trace designs must require their physical copper components before duplication");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void mirrorsLegacyTracesBeforeMounting(GameTestHelper helper) {
    ItemStack wafer = legacyWafer(0, 9);
    PrototypeWaferBlockEntity.mirrorHorizontally(wafer);
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    station.insertWafer(wafer);
    helper.assertTrue(
        station.getCellType(8) == CellType.COPPER
            && station.getCellType(17) == CellType.COPPER
            && station.getCellType(0) == CellType.EMPTY
            && station.getCellType(9) == CellType.EMPTY,
        "Legacy traces must mirror into the same cells as current-format copper conductors");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void mirrorsDefaultWaferPinModes(GameTestHelper helper) {
    assertMirroredDefaultPins(helper, false);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void mirrorsDefaultNestedWaferPinModes(GameTestHelper helper) {
    assertMirroredDefaultPins(helper, true);
  }

  private static void assertMirroredDefaultPins(GameTestHelper helper, boolean nested) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack source = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells = new byte[81];
    for (int x = 0; x < 9; x++) cells[36 + x] = (byte) CellType.COPPER.ordinal();
    source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).putByteArray("Cells", cells);
    source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    if (nested) {
      ItemStack child = source;
      source = new ItemStack(ModItems.MSI_WAFER.get());
      cells = cells.clone();
      cells[40] = (byte) CellType.CHIP.ordinal();
      CompoundTag design = source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
      design.putByteArray("Cells", cells.clone());
      design.putIntArray("PinModes", new int[] {1, 2, 2, 1});
      CompoundTag chips = new CompoundTag();
      chips.put("40", child.save(new CompoundTag()));
      design.put("Chips", chips);
      source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    }
    ItemStack original = source.copy();
    for (int mirror = 0; mirror < 2; mirror++) {
      PrototypeWaferBlockEntity.mirrorHorizontally(source);
      station.insertWafer(source.copy());
      Direction input = mirror == 0 ? Direction.EAST : Direction.WEST;
      BlockPos relativeInput = GameTestFixtures.ORIGIN.relative(input);
      helper.setBlock(relativeInput, Blocks.REDSTONE_BLOCK);
      try {
        station.refreshSignals();
        helper.assertTrue(
            station.getPinMode(1)
                    == (mirror == 0
                        ? PrototypeWaferBlockEntity.PinMode.INPUT
                        : PrototypeWaferBlockEntity.PinMode.OUTPUT)
                && station.getPinMode(3)
                    == (mirror == 0
                        ? PrototypeWaferBlockEntity.PinMode.OUTPUT
                        : PrototypeWaferBlockEntity.PinMode.INPUT)
                && station.getOutput(input) == (nested ? 9 : 11),
            "Mirroring must flip implicit default ports and a second mirror must restore the"
                + " original circuit logic, including nested chips");
      } finally {
        helper.setBlock(relativeInput, Blocks.AIR);
        station.removeWafer();
      }
    }
    helper.assertTrue(
        PrototypeWaferBlockEntity.isSameComponent(original, source),
        "Mirroring twice must restore physical component matching as well as signal behavior");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void protectsLegacyTracesFromBlankWaferConsumption(GameTestHelper helper) {
    ItemStack wafer = legacyWafer(0);
    CompoundTag original = wafer.save(new CompoundTag());
    helper.assertTrue(
        !PrototypeWaferBlockEntity.isBlankWafer(wafer),
        "An unfinished legacy circuit must not be consumed as an empty wafer substrate");
    helper.assertTrue(
        original.equals(wafer.save(new CompoundTag())),
        "Checking whether a wafer is blank must not mutate the design being inspected");
    helper.assertTrue(
        PrototypeWaferBlockEntity.isBlankWafer(new ItemStack(ModItems.SSI_WAFER.get())),
        "Fresh wafers must remain valid duplication substrates");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsSpectatorWaferMutations(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    station.insertWafer(new ItemStack(ModItems.SSI_WAFER.get()));
    FakePlayer player =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wafer_observer"));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    WaferMenu menu = new WaferMenu(1, player.getInventory(), station);
    player.containerMenu = menu;
    player.setGameMode(GameType.SPECTATOR);
    helper.assertTrue(
        menu.stillValid(player),
        "Spectator inspection should keep an otherwise valid wafer menu open");
    helper.assertTrue(
        !menu.tryBeginMutation(player, station.getBlockPos()),
        "Custom wafer packets must respect the spectator mutation restriction used by vanilla"
            + " menus");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void locksPendingDuplicatorMaterialsAgainstShiftClick(GameTestHelper helper) {
    WaferDuplicatorBlockEntity duplicator =
        new WaferDuplicatorBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_DUPLICATOR.get().defaultBlockState());
    ItemStackHandler savedItems = new ItemStackHandler(WaferDuplicatorBlockEntity.SLOT_COUNT);
    savedItems.setStackInSlot(
        WaferDuplicatorBlockEntity.MATERIAL_START, new ItemStack(ModItems.COPPER_NUGGET.get(), 2));
    CompoundTag saved = new CompoundTag();
    saved.put("Items", savedItems.serializeNBT());
    saved.put("PendingResult", new ItemStack(Items.EMERALD).save(new CompoundTag()));
    duplicator.load(saved);
    FakePlayer player =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wafer_supplier"));
    player.getInventory().setItem(9, new ItemStack(ModItems.COPPER_NUGGET.get(), 3));
    WaferDuplicatorMenu menu = new WaferDuplicatorMenu(1, player.getInventory(), duplicator);
    menu.quickMoveStack(player, WaferDuplicatorBlockEntity.SLOT_COUNT);
    helper.assertTrue(
        duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.MATERIAL_START).getCount() == 2
            && player.getInventory().getItem(9).getCount() == 3,
        "Shift-click merging must not bypass pending duplicate material locks");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void boundsRestoredDuplicatorOutputCount(GameTestHelper helper)
      throws ReflectiveOperationException {
    ItemStack source = new ItemStack(ModItems.SSI_WAFER.get());
    source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    ItemStackHandler storedItems = new ItemStackHandler(WaferDuplicatorBlockEntity.SLOT_COUNT);
    storedItems.setStackInSlot(WaferDuplicatorBlockEntity.SOURCE_SLOT, source);
    storedItems.setStackInSlot(
        WaferDuplicatorBlockEntity.BLANK_SLOT, new ItemStack(ModItems.SSI_WAFER.get()));
    CompoundTag saved = new CompoundTag();
    saved.put("Items", storedItems.serializeNBT());
    saved.putInt("Energy", 10_000);
    WaferDuplicatorBlockEntity duplicator =
        new WaferDuplicatorBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_DUPLICATOR.get().defaultBlockState());
    var complete = WaferDuplicatorBlockEntity.class.getDeclaredMethod("tryDuplicate");
    complete.setAccessible(true);
    for (ItemStack pending :
        List.of(source.copyWithCount(2), new ItemStack(ModItems.CONTAMINATED_WAFER.get(), 2))) {
      pending.setHoverName(Component.literal("Fixed pending result"));
      pending.getOrCreateTag().putInt("CustomData", 7);
      saved.put("PendingResult", pending.save(new CompoundTag()));
      duplicator.load(saved);
      complete.invoke(duplicator);
      complete.invoke(duplicator);
      ItemStack output = duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.OUTPUT_SLOT);
      helper.assertTrue(
          output.getCount() == 1
              && ItemStack.isSameItemSameTags(output, pending)
              && duplicator
                      .items()
                      .getStackInSlot(WaferDuplicatorBlockEntity.SOURCE_SLOT)
                      .getCount()
                  == 1
              && duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.BLANK_SLOT).isEmpty()
              && duplicator
                      .getCapability(ForgeCapabilities.ENERGY)
                      .orElseThrow(IllegalStateException::new)
                      .getEnergyStored()
                  == 0
              && ItemStack.of(duplicator.saveWithoutMetadata().getCompound("PendingResult"))
                  .isEmpty(),
          "A restored duplicate must deliver one exact saved normal or contaminated result for one"
              + " substrate and one energy charge");
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void consumesEquivalentWaferMaterialsWithoutChangingDistinctOnes(
      GameTestHelper helper) throws ReflectiveOperationException {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    BlockPos conditionerPos = pos.east(2);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.WAFER_DUPLICATOR.get());
    helper.setBlock(GameTestFixtures.ORIGIN.east(2), ModBlocks.CONDITIONER.get());
    WaferDuplicatorBlockEntity duplicator =
        (WaferDuplicatorBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack legacy = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells = new byte[81];
    cells[0] = (byte) CellType.COPPER.ordinal();
    legacy.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).putByteArray("Cells", cells);
    legacy.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    ItemStack current = legacy.copy();
    current
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putInt(PrototypeWaferBlockEntity.DESIGN_VERSION_TAG, 2);
    current
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putIntArray("PinModes", new int[] {1, 2, 2, 1});
    ItemStack latched = current.copy();
    CompoundTag runtime = latched.getOrCreateTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    runtime.putInt("Size", 9);
    byte[] signals = new byte[81];
    signals[0] = 15;
    runtime.putByteArray("Signals", signals);
    ItemStack custom = legacy.copy(), named = legacy.copy(), configured = legacy.copy();
    custom.getOrCreateTag().putInt("CustomData", 7);
    named.setHoverName(Component.literal("Keep this named wafer"));
    configured
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putIntArray("PinModes", new int[4]);
    List<ItemStack> distinct = List.of(custom, named, configured);
    List<ItemStack> equivalent = List.of(legacy, current, latched);
    ItemStack source = waferWithChips(new ItemStack(ModItems.MSI_WAFER.get()), legacy, 3);
    source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    source
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putInt(PrototypeWaferBlockEntity.DESIGN_VERSION_TAG, 2);
    duplicator.items().setStackInSlot(0, source.copy());
    duplicator.items().setStackInSlot(1, new ItemStack(ModItems.MSI_WAFER.get()));
    for (int index = 0; index < 3; index++) {
      duplicator.items().setStackInSlot(3 + index, distinct.get(index).copy());
      duplicator.items().setStackInSlot(6 + index, equivalent.get(index).copy());
    }
    var energy =
        duplicator.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    for (int transfer = 0; transfer < 10; transfer++) energy.receiveEnergy(2_000, false);
    CleanroomOccupancy.update(helper.getLevel(), conditionerPos, Set.of(pos.asLong()), 100);
    try {
      helper.assertTrue(
          duplicator.status() == 6,
          "Legacy, explicit-default and runtime-only variants must satisfy the same requirement");
      var complete = WaferDuplicatorBlockEntity.class.getDeclaredMethod("tryDuplicate");
      complete.setAccessible(true);
      complete.invoke(duplicator);
      complete.invoke(duplicator);
      helper.assertTrue(
          ItemStack.matches(source, duplicator.items().getStackInSlot(0))
              && duplicator.items().getStackInSlot(1).isEmpty()
              && ItemStack.matches(source, duplicator.items().getStackInSlot(2))
              && energy.getEnergyStored() == 0,
          "One duplicate must preserve the source, consume one substrate and charge energy once");
      for (int index = 0; index < 3; index++)
        helper.assertTrue(
            ItemStack.matches(distinct.get(index), duplicator.items().getStackInSlot(3 + index))
                && duplicator.items().getStackInSlot(6 + index).isEmpty(),
            "Matching must consume only equivalent components and preserve custom, named and"
                + " non-default-pin materials");
    } finally {
      CleanroomOccupancy.remove(helper.getLevel(), conditionerPos);
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void excludesDuplicatorOutputsFromShiftClickInsertion(GameTestHelper helper) {
    WaferDuplicatorBlockEntity duplicator =
        new WaferDuplicatorBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_DUPLICATOR.get().defaultBlockState());
    duplicator
        .items()
        .setStackInSlot(
            WaferDuplicatorBlockEntity.OUTPUT_SLOT,
            new ItemStack(ModItems.CONTAMINATED_WAFER.get(), 2));
    FakePlayer player =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wafer_loader"));
    player.getInventory().setItem(9, new ItemStack(ModItems.CONTAMINATED_WAFER.get(), 3));
    WaferDuplicatorMenu menu = new WaferDuplicatorMenu(1, player.getInventory(), duplicator);
    menu.quickMoveStack(player, WaferDuplicatorBlockEntity.SLOT_COUNT);
    helper.assertTrue(
        duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.OUTPUT_SLOT).getCount() == 2,
        "Shift-click insertion must exclude the duplicator's output-only slots");
    helper.assertTrue(
        duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.MATERIAL_START).getCount()
            == 3,
        "Player materials should still move into an unlocked material slot");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void persistsShiftClickDuplicatorMaterialMerges(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.WAFER_DUPLICATOR.get());
    WaferDuplicatorBlockEntity duplicator =
        (WaferDuplicatorBlockEntity) helper.getLevel().getBlockEntity(pos);
    duplicator
        .items()
        .setStackInSlot(
            WaferDuplicatorBlockEntity.MATERIAL_START,
            new ItemStack(ModItems.COPPER_NUGGET.get(), 2));
    FakePlayer player =
        new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wafer_merger"));
    player.getInventory().setItem(9, new ItemStack(ModItems.COPPER_NUGGET.get(), 3));
    WaferDuplicatorMenu menu = new WaferDuplicatorMenu(1, player.getInventory(), duplicator);
    helper.getLevel().getChunkAt(pos).setUnsaved(false);
    menu.quickMoveStack(player, WaferDuplicatorBlockEntity.SLOT_COUNT);
    helper.assertTrue(
        duplicator.items().getStackInSlot(WaferDuplicatorBlockEntity.MATERIAL_START).getCount()
            == 5,
        "Unlocked shift-click insertion must merge compatible material stacks");
    helper.assertTrue(
        helper.getLevel().getChunkAt(pos).isUnsaved(),
        "A shift-click material merge must mark the duplicator chunk for saving");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsWaferMenusFromOtherDimensions(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    helper.setBlock(GameTestFixtures.ORIGIN.east(2), ModBlocks.WAFER_DUPLICATOR.get());
    helper.setBlock(GameTestFixtures.ORIGIN.east(4), ModBlocks.WAFER_INVERTER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    WaferDuplicatorBlockEntity duplicator =
        (WaferDuplicatorBlockEntity) helper.getLevel().getBlockEntity(pos.east(2));
    WaferInverterBlockEntity inverter =
        (WaferInverterBlockEntity) helper.getLevel().getBlockEntity(pos.east(4));
    var otherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
    helper.assertTrue(otherLevel != null, "The server must provide a second dimension");
    FakePlayer player =
        new FakePlayer(otherLevel, new GameProfile(UUID.randomUUID(), "wafer_traveler"));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    helper.assertTrue(
        !new WaferMenu(1, player.getInventory(), station).stillValid(player)
            && !new WaferDuplicatorMenu(2, player.getInventory(), duplicator).stillValid(player)
            && !new WaferInverterMenu(3, player.getInventory(), inverter).stillValid(player),
        "Wafer menus must close when their player is in a different dimension");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void matchesMigratedWaferComponents(GameTestHelper helper) {
    ItemStack legacy = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells =
        new byte[PrototypeWaferBlockEntity.GRID_SIZE * PrototypeWaferBlockEntity.GRID_SIZE];
    cells[0] = (byte) CellType.COPPER.ordinal();
    legacy.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).putByteArray("Cells", cells);
    legacy.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    station.insertWafer(legacy.copy());
    station.getCellType(0);
    ItemStack migrated = station.removeWafer();
    CompoundTag legacyBefore = legacy.save(new CompoundTag()).copy();
    CompoundTag migratedBefore = migrated.save(new CompoundTag()).copy();
    helper.assertTrue(
        PrototypeWaferBlockEntity.isSameComponent(legacy, migrated),
        "Migrating an unchanged wafer design must not make it a different duplication component");
    helper.assertTrue(
        legacyBefore.equals(legacy.save(new CompoundTag()))
            && migratedBefore.equals(migrated.save(new CompoundTag())),
        "Component comparison must normalize copies while preserving both original items");
    ItemStack different = migrated.copy();
    byte[] changedCells = cells.clone();
    changedCells[0] = (byte) CellType.NOT.ordinal();
    different
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putByteArray("Cells", changedCells);
    helper.assertTrue(
        !PrototypeWaferBlockEntity.isSameComponent(legacy, different),
        "Component normalization must still distinguish physically different circuit designs");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void matchesRestoredDefaultPinComponents(GameTestHelper helper) {
    ItemStack source = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells = new byte[81];
    cells[0] = (byte) CellType.COPPER.ordinal();
    source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).putByteArray("Cells", cells);
    source.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    station.insertWafer(source.copy());
    for (int cycle = 0; cycle < 3; cycle++) station.cyclePinMode(1);
    station.completeWafer("");
    helper.assertTrue(
        station.getPinMode(1) == PrototypeWaferBlockEntity.PinMode.OUTPUT
            && PrototypeWaferBlockEntity.isSameComponent(source, station.getWafer()),
        "Cycling a port back to its implicit default must preserve physical component matching");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void matchesSimulatedBlankWaferComponents(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack blank = new ItemStack(ModItems.SSI_WAFER.get());
    station.insertWafer(waferWithChip(new ItemStack(ModItems.MSI_WAFER.get()), blank));
    List<ItemStack> components = PrototypeWaferBlockEntity.requiredComponents(station.getWafer());
    helper.assertTrue(
        components.size() == 1
            && PrototypeWaferBlockEntity.isSameComponent(blank, components.get(0))
            && !blank.hasTag(),
        "Simulating an embedded blank wafer must not make an identical blank fail material matching"
            + " or mutate the inspected material");
    ItemStack custom = components.get(0).copy();
    custom.getOrCreateTag().putInt("CustomData", 1);
    ItemStack customDesign = components.get(0).copy();
    customDesign
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putInt("CustomData", 1);
    ItemStack completed = blank.copy();
    completed.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    ItemStack named = blank.copy();
    named.setHoverName(Component.literal("Named component"));
    ItemStack configured = components.get(0).copy();
    configured
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putIntArray("PinModes", new int[4]);
    helper.assertTrue(
        !PrototypeWaferBlockEntity.isSameComponent(blank, custom)
            && !PrototypeWaferBlockEntity.isSameComponent(blank, customDesign)
            && !PrototypeWaferBlockEntity.isSameComponent(blank, completed)
            && !PrototypeWaferBlockEntity.isSameComponent(blank, named)
            && !PrototypeWaferBlockEntity.isSameComponent(blank, configured),
        "Blank component normalization must preserve custom tags, completion, names, and configured"
            + " pins");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void omitsEmptyWaferRuntime(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack wafer = new ItemStack(ModItems.SSI_WAFER.get());
    wafer
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putByteArray("Cells", new byte[81]);
    CompoundTag oldRuntime = wafer.getOrCreateTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    oldRuntime.putInt("Size", 9);
    oldRuntime.putByteArray("Signals", new byte[81]);
    oldRuntime.putByteArray("WireStrength", new byte[324]);
    oldRuntime.putByteArray("WireRemaining", new byte[324]);
    oldRuntime.putByteArray("ChipOutputs", new byte[324]);
    station.insertWafer(wafer);
    helper.assertTrue(
        station.getWafer().getTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG) == null,
        "All-zero legacy runtime arrays must be omitted without changing their zero signal state");
    for (int cell = 0; cell < 81; cell++)
      helper.assertTrue(
          station.getCellSignal(cell) == 0, "An empty circuit must keep zero signals");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesLatchMemoryWithCompactRuntime(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    station.insertWafer(latchedWafer());
    helper.assertTrue(
        station.getCellSignal(1) == 15 && station.getCellSignal(79) == 0,
        "The two-inverter feedback loop must retain its initial asymmetric memory state");
    CompoundTag runtime = station.getWafer().getTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    helper.assertTrue(
        runtime != null
            && runtime.contains("Signals")
            && runtime.contains("WireStrength")
            && !runtime.contains("ChipOutputs"),
        "Compact runtime must keep nonzero memory and omit all-zero chip output matrices");
    PrototypeWaferBlockEntity restored =
        new PrototypeWaferBlockEntity(pos, station.getBlockState());
    restored.load(station.saveWithoutMetadata());
    restored.setLevel(helper.getLevel());
    restored.refreshSignals();
    helper.assertTrue(
        restored.getCellSignal(1) == 15 && restored.getCellSignal(79) == 0,
        "A compact-runtime circuit must retain feedback memory after saving and reloading");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void syncsWaferDisplayWithoutNestedRuntime(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack child = waferWithChip(new ItemStack(ModItems.MSI_WAFER.get()), latchedWafer());
    child.setHoverName(Component.literal("Memory module"));
    child.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    station.insertWafer(waferWithChip(new ItemStack(ModItems.LSI_WAFER.get()), child));
    CompoundTag saved = station.saveWithoutMetadata().copy();
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    CompoundTag update;
    try {
      station.getUpdatePacket().write(buffer);
      update = new ClientboundBlockEntityDataPacket(buffer).getTag();
    } finally {
      buffer.release();
    }
    PrototypeWaferBlockEntity client = new PrototypeWaferBlockEntity(pos, station.getBlockState());
    client.load(update);
    ItemStack displayedChild = client.getEmbeddedWafer(0);
    helper.assertTrue(
        client.getCellType(0) == CellType.CHIP
            && displayedChild.is(ModItems.MSI_WAFER.get())
            && displayedChild.getHoverName().getString().equals("Memory module")
            && PrototypeWaferBlockEntity.isCompleted(displayedChild),
        "Client synchronization must preserve visible circuit cells and embedded item details");
    helper.assertTrue(
        displayedChild.getTagElement(PrototypeWaferBlockEntity.DESIGN_TAG) == null
            && displayedChild.getTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG) == null,
        "Block updates must exclude nested circuit designs and runtime data unused by the screen");
    helper.assertTrue(
        saved.equals(station.saveWithoutMetadata()),
        "Client projection must preserve the complete server-side saved circuit and latch memory");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void isolatesWaferStateSnapshots(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    ItemStack source = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells = new byte[81];
    for (int y = 0; y < 9; y++) cells[y * 9 + 4] = (byte) CellType.COPPER.ordinal();
    CompoundTag design = source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    design.putByteArray("Cells", cells);
    design.putIntArray("PinModes", new int[] {1, 0, 2, 0});
    station.insertWafer(source);
    CompoundTag update = station.getUpdateTag(), saved = station.saveWithoutMetadata();
    CompoundTag expectedUpdate = update.copy(), expectedSaved = saved.copy();
    helper.setBlock(GameTestFixtures.ORIGIN.north(), Blocks.REDSTONE_BLOCK);
    try {
      station.refreshSignals();
      helper.assertTrue(
          station.getCellSignal(4) == 15 && station.getOutput(Direction.NORTH) == 11,
          "The genuine redstone input must change the station's inputs and outputs");
      helper.assertTrue(
          update.equals(expectedUpdate) && saved.equals(expectedSaved),
          "Published update and save tags must not change when the live station advances");
      update = station.getUpdateTag();
      saved = station.saveWithoutMetadata();
      for (CompoundTag snapshot : new CompoundTag[] {update, saved}) {
        snapshot.getIntArray("Inputs")[0] = 99;
        snapshot.getIntArray("Outputs")[2] = 99;
        for (String key : new String[] {"Signals", "HorizontalSignals", "VerticalSignals"})
          snapshot.getIntArray(key)[4] = 99;
      }
      helper.assertTrue(
          station.getCellSignal(4) == 15
              && station.getHorizontalSignal(4) == 15
              && station.getVerticalSignal(4) == 15
              && station.getOutput(Direction.NORTH) == 11,
          "Changing a returned NBT snapshot must not mutate the live circuit state");
    } finally {
      helper.setBlock(GameTestFixtures.ORIGIN.north(), Blocks.AIR);
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void boundsRestoredWaferRedstoneOutputs(GameTestHelper helper) {
    ItemStack source = new ItemStack(ModItems.SSI_WAFER.get());
    source
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putIntArray("PinModes", new int[] {2, 2, 2, 2});
    CompoundTag saved = new CompoundTag();
    saved.put("Wafer", source.save(new CompoundTag()));
    saved.putIntArray("Outputs", new int[] {Integer.MIN_VALUE, -1, 16, Integer.MAX_VALUE});
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_GUARD.get().defaultBlockState());
    station.load(saved);
    helper.assertTrue(
        station.getOutput(Direction.SOUTH) == 0
            && station.getOutput(Direction.WEST) == 0
            && station.getOutput(Direction.NORTH) == 15
            && station.getOutput(Direction.EAST) == 15,
        "Restored wafer outputs must remain in vanilla redstone's 0..15 range before the first"
            + " tick");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void boundsMalformedWaferPreviewData(GameTestHelper helper) {
    ItemStack source = new ItemStack(ModItems.LSI_WAFER.get());
    CompoundTag design = source.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    design.putInt(
        PrototypeWaferBlockEntity.DESIGN_VERSION_TAG,
        PrototypeWaferBlockEntity.CURRENT_DESIGN_VERSION);
    design.putByteArray("Cells", new byte[(int) NetworkNbtSize.LIMIT]);
    design.putByteArray("Rotations", new byte[1_024]);
    design.putByteArray("ConductorModes", new byte[1_024]);
    design.putLongArray("DropAmounts", new long[1_024]);
    design.putIntArray("PinModes", new int[1_024]);
    design.putLongArray("Traces", new long[1_024]);
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    station.insertWafer(source);
    CompoundTag storedBefore = station.saveWithoutMetadata();
    CompoundTag preview = station.getUpdateTag();
    assertNetworkReadability(helper, preview, true);
    helper.assertTrue(
        NetworkNbtSize.accountedSize(preview) < 16_384
            && storedBefore.equals(station.saveWithoutMetadata()),
        "Malformed preview arrays must be bounded without changing the stored wafer");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void boundsMalformedWaferPreviewNames(GameTestHelper helper) {
    ItemStack child = new ItemStack(ModItems.MSI_WAFER.get());
    child.setHoverName(Component.literal("a".repeat(60_000)));
    ItemStack source = waferWithChips(new ItemStack(ModItems.LSI_WAFER.get()), child, 81);
    source.setHoverName(Component.literal("b".repeat(60_000)));
    ItemStack normal = new ItemStack(ModItems.MSI_WAFER.get());
    String normalName = "한\u0001".repeat(25);
    normal.setHoverName(Component.literal(normalName));
    source
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .getCompound("Chips")
        .put("40", normal.save(new CompoundTag()));
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    station.insertWafer(source);
    CompoundTag storedBefore = station.saveWithoutMetadata();
    CompoundTag preview = station.getUpdateTag();
    assertNetworkReadability(helper, preview, true);
    PrototypeWaferBlockEntity client =
        new PrototypeWaferBlockEntity(BlockPos.ZERO, station.getBlockState());
    client.load(preview);
    helper.assertTrue(
        NetworkNbtSize.accountedSize(preview) < 262_144
            && client.getEmbeddedWafer(40).getHoverName().getString().equals(normalName)
            && storedBefore.equals(station.saveWithoutMetadata()),
        "Oversized custom preview names must be bounded while preserving normal 50-character"
            + " names and the complete saved wafer");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void mapsLegacyWaferChipPreviews(GameTestHelper helper) {
    ItemStack oldWafer = new ItemStack(ModItems.LSI_WAFER.get());
    ItemStack child = new ItemStack(ModItems.MSI_WAFER.get());
    child.setHoverName(Component.literal("Legacy module"));
    CompoundTag design = oldWafer.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] cells = new byte[13 * 13];
    cells[6 * 13 + 6] = (byte) CellType.CHIP.ordinal();
    design.putByteArray("Cells", cells);
    CompoundTag chips = new CompoundTag();
    chips.put(Integer.toString(6 * 13 + 6), child.save(new CompoundTag()));
    design.put("Chips", chips);
    CompoundTag saved = new CompoundTag();
    saved.put("Wafer", oldWafer.save(new CompoundTag()));
    PrototypeWaferBlockEntity server =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get().defaultBlockState());
    server.load(saved);
    CompoundTag before = server.saveWithoutMetadata().copy();
    PrototypeWaferBlockEntity client =
        new PrototypeWaferBlockEntity(BlockPos.ZERO, server.getBlockState());
    client.load(server.getUpdateTag());
    helper.assertTrue(
        client.getCellType(40) == CellType.CHIP
            && client.getEmbeddedWafer(40).getHoverName().getString().equals("Legacy module"),
        "Client previews must preserve embedded items when centering a legacy 13-by-13 design");
    helper.assertTrue(
        before.equals(server.saveWithoutMetadata()),
        "Projecting a legacy design must not mutate the saved server-side circuit");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void matchesForgeNetworkNbtAccounting(GameTestHelper helper) {
    CompoundTag tag = new CompoundTag();
    tag.putByte("byte", (byte) 1);
    tag.putShort("short", (short) 2);
    tag.putInt("int", 3);
    tag.putLong("long", 4L);
    tag.putFloat("float", 5.5F);
    tag.putDouble("double", 6.5D);
    tag.putByteArray("bytes", new byte[] {1, 2});
    tag.putIntArray("ints", new int[] {3, 4});
    tag.putLongArray("longs", new long[] {5, 6});
    tag.putString("mixed\0한글💎", "ascii\0한글💎");
    ListTag strings = new ListTag();
    strings.add(StringTag.valueOf("ascii"));
    strings.add(StringTag.valueOf("한글💎"));
    tag.put("strings", strings);
    ListTag compounds = new ListTag();
    CompoundTag child = new CompoundTag();
    child.putString("name", "value");
    compounds.add(child);
    tag.put("compounds", compounds);
    tag.put("empty_list", new ListTag());
    for (CompoundTag fixture : new CompoundTag[] {new CompoundTag(), tag}) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      NbtAccounter actual = new NbtAccounter(Long.MAX_VALUE);
      try {
        buffer.writeNbt(fixture);
        buffer.readNbt(actual);
      } finally {
        buffer.release();
      }
      helper.assertTrue(
          NetworkNbtSize.accountedSize(fixture) == actual.getUsage(),
          "The allocation-free size check must match Forge's modified-UTF and tag accounting"
              + " exactly");
    }
    CompoundTag boundary = new CompoundTag();
    boundary.putByteArray("b", new byte[(int) NetworkNbtSize.LIMIT - 156]);
    helper.assertTrue(
        NetworkNbtSize.accountedSize(boundary) == NetworkNbtSize.LIMIT
            && NetworkNbtSize.fits(boundary),
        "A payload at the exact vanilla allocation limit must remain readable");
    FriendlyByteBuf boundaryBuffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      boundaryBuffer.writeNbt(boundary);
      boundaryBuffer.readNbt();
    } finally {
      boundaryBuffer.release();
    }
    boundary.putByteArray("b", new byte[(int) NetworkNbtSize.LIMIT - 155]);
    helper.assertTrue(
        !NetworkNbtSize.fits(boundary),
        "One byte beyond the vanilla allocation limit must be rejected");
    assertNetworkReadability(helper, boundary, false);
    for (int depth : new int[] {512, 513}) {
      CompoundTag nestedCompounds = new CompoundTag();
      CompoundTag compoundCursor = nestedCompounds;
      for (int index = 0; index < depth; index++) {
        CompoundTag next = new CompoundTag();
        compoundCursor.put("n", next);
        compoundCursor = next;
      }
      assertNetworkReadability(helper, nestedCompounds, depth == 512);
      CompoundTag nestedLists = new CompoundTag();
      ListTag listCursor = new ListTag();
      nestedLists.put("list", listCursor);
      for (int index = 1; index < depth; index++) {
        ListTag next = new ListTag();
        listCursor.add(next);
        listCursor = next;
      }
      assertNetworkReadability(helper, nestedLists, depth == 512);
    }
    for (int length : new int[] {65_535, 65_536}) {
      CompoundTag utfBoundary = new CompoundTag();
      utfBoundary.putString("name", "x".repeat(length));
      assertNetworkReadability(helper, utfBoundary, length == 65_535);
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty", timeoutTicks = 1200)
  public static void rejectsOversizedEmbeddingWithoutConsumingMaterials(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    station.insertWafer(new ItemStack(ModItems.LSI_WAFER.get()));
    ItemStack leaf = new ItemStack(ModItems.SSI_WAFER.get());
    CompoundTag leafDesign = leaf.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] copperCells = new byte[81];
    for (int cell = 0; cell < copperCells.length; cell++)
      copperCells[cell] = (byte) CellType.COPPER.ordinal();
    leafDesign.putByteArray("Cells", copperCells);
    leafDesign.putByteArray("Rotations", new byte[81]);
    leafDesign.putByteArray("ConductorModes", new byte[81]);
    leafDesign.putByteArray("DropAmounts", new byte[81]);
    leaf.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    ItemStack child = waferWithChips(new ItemStack(ModItems.MSI_WAFER.get()), leaf, 81);
    child.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    helper.assertTrue(
        NetworkNbtSize.fits(child.getShareTag()),
        "The completed MSI supplied as a component must itself be a readable network item");
    FakePlayer player =
        new FakePlayer(
            helper.getLevel(), new GameProfile(UUID.randomUUID(), "wafer_guarded_builder"));
    player.setGameMode(GameType.SURVIVAL);
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    WaferMenu menu = new WaferMenu(1, player.getInventory(), station);
    player.containerMenu = menu;
    int accepted = 0;
    boolean rejected = false;
    for (int cell = 0; cell < 81; cell++) {
      menu.setCarried(child.copy());
      ItemStack before = station.getWafer().copy();
      menu.interactCell(player, cell, false);
      if (station.getCellType(cell) == CellType.CHIP) {
        accepted++;
        helper.assertTrue(
            menu.getCarried().isEmpty(),
            "An accepted survival embedding must consume one component");
        continue;
      }
      rejected = true;
      helper.assertTrue(
          ItemStack.matches(before, station.getWafer())
              && ItemStack.matches(child, menu.getCarried()),
          "Rejecting a circuit beyond the network limit must preserve its design and carried"
              + " component");
      break;
    }
    helper.assertTrue(
        accepted > 0 && rejected,
        "Valid small circuits must be accepted before growth reaches the safe network limit");
    helper.assertTrue(
        NetworkNbtSize.fits(station.getWafer().getShareTag()),
        "The last accepted circuit must remain a readable inventory item");
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      buffer.writeItem(station.getWafer());
      ItemStack decoded = buffer.readItem();
      helper.assertTrue(
          ItemStack.isSameItemSameTags(station.getWafer(), decoded),
          "Accepted circuits must round trip through the real item decoder without data loss");
    } finally {
      buffer.release();
    }
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void rejectsNonpositiveWaferEnergyReceipts(GameTestHelper helper) {
    PrototypeWaferBlockEntity station =
        new PrototypeWaferBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_ASSEMBLER.get().defaultBlockState());
    WaferDuplicatorBlockEntity duplicator =
        new WaferDuplicatorBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_DUPLICATOR.get().defaultBlockState());
    WaferInverterBlockEntity inverter =
        new WaferInverterBlockEntity(
            BlockPos.ZERO, ModBlocks.WAFER_INVERTER.get().defaultBlockState());
    for (var machine : List.of(station, duplicator, inverter)) {
      var storage =
          machine.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
      helper.assertTrue(
          storage.receiveEnergy(100, false) == 100,
          "Positive wafer machine energy input must remain supported");
      for (int amount : new int[] {0, -1, Integer.MIN_VALUE}) {
        for (boolean simulate : new boolean[] {false, true}) {
          helper.assertTrue(
              storage.receiveEnergy(amount, simulate) == 0 && storage.getEnergyStored() == 100,
              "Nonpositive energy receipts must not remove stored wafer machine power or return"
                  + " negative transfers");
        }
      }
      helper.assertTrue(
          storage.receiveEnergy(Integer.MAX_VALUE, true) == 2_000
              && storage.getEnergyStored() == 100,
          "A simulated large positive receipt must respect the per-call transfer limit without"
              + " changing energy");
    }
    helper.assertTrue(
        station.addEnergy(-1) == 0
            && station.addEnergy(Integer.MIN_VALUE) == 0
            && station.getEnergyStored() == 100,
        "The assembler's direct energy wrapper must also reject nonpositive transfers");
    helper.succeed();
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void refreshesInverterResultAfterInputReplacement(GameTestHelper helper) {
    assertInverterTransition(helper, InverterTransition.REPLACE);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void refreshesInverterResultAfterDirectNbtChange(GameTestHelper helper) {
    assertInverterTransition(helper, InverterTransition.MODIFY_NBT);
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void restoresInverterProgressWithFreshResult(GameTestHelper helper) {
    assertInverterTransition(helper, InverterTransition.RELOAD);
  }

  private enum InverterTransition {
    REPLACE,
    MODIFY_NBT,
    RELOAD
  }

  private static void assertInverterTransition(
      GameTestHelper helper, InverterTransition transition) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    BlockPos conditionerPos = pos.east(2);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.WAFER_INVERTER.get());
    helper.setBlock(GameTestFixtures.ORIGIN.east(2), ModBlocks.CONDITIONER.get());
    WaferInverterBlockEntity inverter =
        (WaferInverterBlockEntity) helper.getLevel().getBlockEntity(pos);
    var energy =
        inverter.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
    CleanroomOccupancy.update(helper.getLevel(), conditionerPos, Set.of(pos.asLong()), 100);
    try {
      inverter.items().setStackInSlot(0, inverterInput(CellType.COPPER, 0, "first"));
      energy.receiveEnergy(2_000, false);
      WaferInverterBlockEntity.serverTick(
          helper.getLevel(), pos, inverter.getBlockState(), inverter);
      helper.assertTrue(
          inverter.data().get(2) == 1 && inverter.data().get(5) == 4,
          "The first input must advance and populate the status result");
      CompoundTag saved = inverter.saveWithoutMetadata();
      ItemStack replacement = inverterInput(CellType.REDSTONE, 9, "second");
      if (transition == InverterTransition.MODIFY_NBT) {
        inverter.items().getStackInSlot(0).setTag(replacement.getTag().copy());
      } else {
        inverter.items().setStackInSlot(0, replacement);
        helper.assertTrue(inverter.data().get(5) == 4, "The replacement must be ready to process");
        if (transition == InverterTransition.RELOAD) inverter.load(saved);
      }
      if (transition != InverterTransition.RELOAD) energy.receiveEnergy(2_000, false);
      int remaining = WaferInverterBlockEntity.PROCESS_TICKS - inverter.data().get(2);
      for (int tick = 0; tick < remaining; tick++)
        WaferInverterBlockEntity.serverTick(
            helper.getLevel(), pos, inverter.getBlockState(), inverter);
      ItemStack result = inverter.items().getStackInSlot(1);
      byte[] cells =
          result.getTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).getByteArray("Cells");
      boolean reloaded = transition == InverterTransition.RELOAD;
      helper.assertTrue(
          result.getCount() == 1
              && cells[reloaded ? 8 : 17]
                  == (reloaded ? CellType.COPPER : CellType.REDSTONE).ordinal()
              && cells[reloaded ? 17 : 8] == CellType.EMPTY.ordinal()
              && result.getHoverName().getString().equals(reloaded ? "first" : "second")
              && result.getTag().getInt("CustomData") == 7
              && result.getTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG) == null
              && PrototypeWaferBlockEntity.isCompleted(result)
              && inverter.items().getStackInSlot(0).isEmpty()
              && inverter.data().get(2) == 0,
          "The completed output must mirror the latest input exactly once and preserve its"
              + " metadata");
      int remainingEnergy = energy.getEnergyStored();
      inverter.items().extractItem(1, 1, false);
      WaferInverterBlockEntity.serverTick(
          helper.getLevel(), pos, inverter.getBlockState(), inverter);
      helper.assertTrue(
          inverter.items().getStackInSlot(1).isEmpty()
              && energy.getEnergyStored() == remainingEnergy,
          "Consuming the output must not allow a cached result to appear or consume energy again");
    } finally {
      CleanroomOccupancy.remove(helper.getLevel(), conditionerPos);
    }
    helper.succeed();
  }

  private static ItemStack inverterInput(CellType type, int cell, String name) {
    ItemStack input = new ItemStack(ModItems.SSI_WAFER.get());
    byte[] cells = new byte[81];
    cells[cell] = (byte) type.ordinal();
    input.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG).putByteArray("Cells", cells);
    input.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    input.getOrCreateTag().putInt("CustomData", 7);
    input.setHoverName(Component.literal(name));
    input.getOrCreateTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG).putInt("Size", 9);
    return input;
  }

  @GameTest(templateNamespace = Siliconic.MOD_ID, template = "empty")
  public static void preservesChangedChildAtParentCycleBoundary(GameTestHelper helper) {
    BlockPos pos = helper.absolutePos(GameTestFixtures.ORIGIN);
    helper.setBlock(GameTestFixtures.ORIGIN, ModBlocks.CREATIVE_WAFER_ASSEMBLER.get());
    PrototypeWaferBlockEntity station =
        (PrototypeWaferBlockEntity) helper.getLevel().getBlockEntity(pos);
    station.insertWafer(childRollbackCircuit());
    CompoundTag childRuntime =
        station.getEmbeddedWafer(0).getTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    helper.assertTrue(
        station.getCellSignal(0) == 0 && childRuntime == null,
        "A repeated parent phase must restore the child runtime from the last unique phase");
    helper.succeed();
  }

  static ItemStack childRollbackCircuit() {
    ItemStack child = new ItemStack(ModItems.SSI_WAFER.get());
    CompoundTag childDesign = child.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] childCells = new byte[81], childModes = new byte[81];
    for (int y = 4; y < 9; y++) childCells[y * 9 + 4] = (byte) CellType.COPPER.ordinal();
    for (int x = 4; x < 9; x++) childCells[36 + x] = (byte) CellType.COPPER.ordinal();
    childModes[40] = (byte) PrototypeWaferBlockEntity.ConductorMode.CORNER_ES.ordinal();
    childDesign.putByteArray("Cells", childCells);
    childDesign.putByteArray("ConductorModes", childModes);
    childDesign.putIntArray("PinModes", new int[] {0, 2, 1, 0});
    child.getOrCreateTag().putBoolean(PrototypeWaferBlockEntity.COMPLETED_TAG, true);
    ItemStack parent = new ItemStack(ModItems.MSI_WAFER.get());
    CompoundTag parentDesign = parent.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] cells = new byte[81], rotations = new byte[81];
    for (int y = 0; y < 9; y++) {
      for (int x = 0; x < 9; x++) {
        if (x > 0 && x < 8 && y > 0 && y < 8) continue;
        int cell = y * 9 + x;
        boolean corner = (x == 0 || x == 8) && (y == 0 || y == 8);
        cells[cell] = (byte) (corner ? CellType.COPPER : CellType.BUFFER).ordinal();
        rotations[cell] = (byte) (y == 0 ? 1 : x == 8 ? 2 : y == 8 ? 3 : 0);
      }
    }
    cells[0] = (byte) CellType.CHIP.ordinal();
    rotations[0] = 0;
    cells[1] = (byte) CellType.NOT.ordinal();
    CompoundTag chips = new CompoundTag();
    chips.put("0", child.save(new CompoundTag()));
    parentDesign.putByteArray("Cells", cells);
    parentDesign.putByteArray("Rotations", rotations);
    parentDesign.putIntArray("PinModes", new int[4]);
    parentDesign.put("Chips", chips);
    // Start immediately after the child rises. Returning to this phase must undo that child's
    // next rise, rather than ending on a pass whose child input was already cached.
    byte[] signals = new byte[81], wires = new byte[324], remaining = new byte[324];
    byte[] outputs = new byte[324];
    for (int cell = 0; cell < 81; cell++) {
      if (cells[cell] == CellType.EMPTY.ordinal()) continue;
      signals[cell] = (byte) (cell == 0 ? 11 : 15);
      if (cells[cell] == CellType.COPPER.ordinal()) {
        for (int side = 0; side < 4; side++) {
          wires[cell * 4 + side] = 15;
          remaining[cell * 4 + side] = 1;
        }
      }
    }
    outputs[1] = 11;
    CompoundTag runtime = parent.getOrCreateTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    runtime.putInt("Size", 9);
    runtime.putByteArray("Signals", signals);
    runtime.putByteArray("WireStrength", wires);
    runtime.putByteArray("WireRemaining", remaining);
    runtime.putByteArray("ChipOutputs", outputs);
    return parent;
  }

  private static void assertNetworkReadability(
      GameTestHelper helper, CompoundTag tag, boolean expected) {
    boolean readable;
    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
    try {
      buffer.writeNbt(tag);
      readable = tag.equals(buffer.readNbt());
    } catch (RuntimeException failure) {
      readable = false;
    } finally {
      buffer.release();
    }
    helper.assertTrue(
        readable == expected && NetworkNbtSize.fits(tag) == readable,
        "The allocation-free NBT check must agree with the real decoder at size and depth"
            + " boundaries");
  }

  private static ItemStack waferWithChip(ItemStack wafer, ItemStack child) {
    return waferWithChips(wafer, child, 1);
  }

  private static ItemStack waferWithChips(ItemStack wafer, ItemStack child, int count) {
    CompoundTag design = wafer.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    byte[] cells = new byte[81];
    CompoundTag chips = new CompoundTag();
    for (int cell = 0; cell < count; cell++) {
      cells[cell] = (byte) CellType.CHIP.ordinal();
      chips.put(Integer.toString(cell), child.save(new CompoundTag()));
    }
    design.putByteArray("Cells", cells);
    design.put("Chips", chips);
    return wafer;
  }

  private static ItemStack latchedWafer() {
    ItemStack wafer = new ItemStack(ModItems.SSI_WAFER.get());
    List<Integer> ring = new ArrayList<>();
    for (int x = 0; x < 9; x++) ring.add(x);
    for (int y = 1; y < 9; y++) ring.add(y * 9 + 8);
    for (int x = 7; x >= 0; x--) ring.add(72 + x);
    for (int y = 7; y > 0; y--) ring.add(y * 9);
    byte[] cells = new byte[81], rotations = new byte[81], signals = new byte[81];
    byte[] wires = new byte[324], remaining = new byte[324];
    boolean high = false;
    for (int index = 0; index < ring.size(); index++) {
      int cell = ring.get(index);
      int x = cell % 9, y = cell / 9;
      boolean corner = (x == 0 || x == 8) && (y == 0 || y == 8);
      boolean inverter = cell == 1 || cell == 79;
      cells[cell] =
          (byte) (corner ? CellType.COPPER : inverter ? CellType.NOT : CellType.BUFFER).ordinal();
      int step = ring.get((index + 1) % ring.size()) - cell;
      rotations[cell] = (byte) (step == 1 ? 1 : step == 9 ? 2 : step == -1 ? 3 : 0);
      if (inverter) high = !high;
      signals[cell] = (byte) (high ? 15 : 0);
      if (corner && high) {
        for (int side = 0; side < 4; side++) {
          wires[cell * 4 + side] = 15;
          remaining[cell * 4 + side] = 1;
        }
      }
    }
    CompoundTag design = wafer.getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG);
    design.putByteArray("Cells", cells);
    design.putByteArray("Rotations", rotations);
    design.putIntArray("PinModes", new int[4]);
    CompoundTag runtime = wafer.getOrCreateTagElement(PrototypeWaferBlockEntity.RUNTIME_TAG);
    runtime.putInt("Size", 9);
    runtime.putByteArray("Signals", signals);
    runtime.putByteArray("WireStrength", wires);
    runtime.putByteArray("WireRemaining", remaining);
    runtime.putByteArray("ChipOutputs", new byte[324]);
    return wafer;
  }

  private static ItemStack legacyWafer(int... traceCells) {
    ItemStack wafer = new ItemStack(ModItems.SSI_WAFER.get());
    BitSet traces = new BitSet();
    for (int cell : traceCells) traces.set(cell);
    wafer
        .getOrCreateTagElement(PrototypeWaferBlockEntity.DESIGN_TAG)
        .putLongArray("Traces", traces.toLongArray());
    return wafer;
  }

  private WaferStabilityGameTests() {}
}
