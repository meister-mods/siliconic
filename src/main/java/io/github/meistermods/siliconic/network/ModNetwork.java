package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.Siliconic;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
  private static final String VERSION = "1";
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.fromNamespaceAndPath(Siliconic.MOD_ID, "main"),
          () -> VERSION,
          VERSION::equals,
          VERSION::equals);

  public static void register() {
    CHANNEL.registerMessage(
        0,
        CyclePinModePacket.class,
        CyclePinModePacket::encode,
        CyclePinModePacket::decode,
        CyclePinModePacket::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        1,
        CellInteractionPacket.class,
        CellInteractionPacket::encode,
        CellInteractionPacket::decode,
        CellInteractionPacket::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        2,
        CompleteWaferPacket.class,
        CompleteWaferPacket::encode,
        CompleteWaferPacket::decode,
        CompleteWaferPacket::handle,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  private ModNetwork() {}
}
