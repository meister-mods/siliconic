package io.github.meistermods.siliconic.network;

import io.github.meistermods.siliconic.wafer.PrototypeWaferBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

@SuppressWarnings({"null"})
public record ToggleTracePacket(BlockPos pos, int cell) {
  static void encode(ToggleTracePacket packet, FriendlyByteBuf buf) {
    buf.writeBlockPos(packet.pos);
    buf.writeByte(packet.cell);
  }

  static ToggleTracePacket decode(FriendlyByteBuf buf) {
    return new ToggleTracePacket(buf.readBlockPos(), buf.readUnsignedByte());
  }

  static void handle(ToggleTracePacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () -> {
          var sender = context.getSender();
          if (sender != null
              && sender.distanceToSqr(packet.pos.getCenter()) <= 64
              && sender.level().getBlockEntity(packet.pos)
                  instanceof PrototypeWaferBlockEntity wafer) wafer.toggleTrace(packet.cell);
        });
    context.setPacketHandled(true);
  }
}
