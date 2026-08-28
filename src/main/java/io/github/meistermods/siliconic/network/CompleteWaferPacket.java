package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.wafer.WaferMenu;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

@SuppressWarnings({"null"})
public record CompleteWaferPacket(BlockPos pos, String name) {
  static void encode(CompleteWaferPacket packet, FriendlyByteBuf buf) {
    buf.writeBlockPos(packet.pos);
    buf.writeUtf(packet.name, 50);
  }

  static CompleteWaferPacket decode(FriendlyByteBuf buf) {
    return new CompleteWaferPacket(buf.readBlockPos(), buf.readUtf(50));
  }

  static void handle(CompleteWaferPacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () -> {
          var sender = context.getSender();
          if (sender != null
              && sender.containerMenu instanceof WaferMenu menu
              && menu.tryBeginMutation(sender, packet.pos)) menu.wafer().completeWafer(packet.name);
        });
    context.setPacketHandled(true);
  }
}
