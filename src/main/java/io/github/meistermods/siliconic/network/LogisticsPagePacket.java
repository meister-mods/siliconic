package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.logistics.LogisticsControllerMenu;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Carries search page selections that cannot fit in vanilla's signed-byte menu buttons. */
@SuppressWarnings("null")
public record LogisticsPagePacket(BlockPos pos, int containerId, int page) {
  public static void encode(LogisticsPagePacket packet, FriendlyByteBuf buffer) {
    buffer.writeBlockPos(packet.pos);
    buffer.writeVarInt(packet.containerId);
    buffer.writeVarInt(packet.page);
  }

  public static LogisticsPagePacket decode(FriendlyByteBuf buffer) {
    return new LogisticsPagePacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
  }

  public boolean apply(ServerPlayer sender) {
    return !sender.isSpectator()
        && sender.containerMenu instanceof LogisticsControllerMenu menu
        && menu.containerId == containerId
        && pos.equals(menu.blockPos())
        && menu.selectPage(sender, page);
  }

  static void handle(LogisticsPagePacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () -> {
          var sender = context.getSender();
          if (sender != null) packet.apply(sender);
        });
    context.setPacketHandled(true);
  }
}
