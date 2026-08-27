package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

@SuppressWarnings({"null"})
public record CellInteractionPacket(BlockPos pos, int cell, boolean rotate) {
  static void encode(CellInteractionPacket packet, FriendlyByteBuf buf) {
    buf.writeBlockPos(packet.pos);
    buf.writeByte(packet.cell);
    buf.writeBoolean(packet.rotate);
  }

  static CellInteractionPacket decode(FriendlyByteBuf buf) {
    return new CellInteractionPacket(buf.readBlockPos(), buf.readUnsignedByte(), buf.readBoolean());
  }

  static void handle(CellInteractionPacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () -> {
          var sender = context.getSender();
          if (sender != null
              && sender.distanceToSqr(packet.pos.getCenter()) <= 64
              && sender.level().getBlockEntity(packet.pos)
                  instanceof PrototypeWaferBlockEntity wafer
              && wafer.isEditable()) wafer.interactCell(packet.cell, packet.rotate, sender);
        });
    context.setPacketHandled(true);
  }
}
