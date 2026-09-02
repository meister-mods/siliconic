package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.wafer.WaferMenu;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

@SuppressWarnings({"null"})
public record CyclePinModePacket(BlockPos pos, int pin) {
  static void encode(CyclePinModePacket packet, FriendlyByteBuf buf) {
    buf.writeBlockPos(packet.pos);
    buf.writeByte(packet.pin);
  }

  static CyclePinModePacket decode(FriendlyByteBuf buf) {
    return new CyclePinModePacket(buf.readBlockPos(), buf.readUnsignedByte());
  }

  static void handle(CyclePinModePacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () -> {
          var sender = context.getSender();
          if (sender != null
              && sender.containerMenu instanceof WaferMenu menu
              && menu.tryBeginMutation(sender, packet.pos)) menu.cyclePinMode(packet.pin);
        });
    context.setPacketHandled(true);
  }
}
