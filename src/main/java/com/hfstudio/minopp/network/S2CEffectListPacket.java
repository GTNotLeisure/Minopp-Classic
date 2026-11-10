package com.hfstudio.minopp.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.effect.EffectEvent;
import com.hfstudio.minopp.effect.EffectEvents;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public class S2CEffectListPacket {

    public static final String ID = "minopp:effect_list";

    public static void sendS2C(EntityPlayerMP target, List<EffectEvent> events, BlockPos origin,
        boolean playerPartOfSourceGame) {
        ByteBuf packet = MinoNetwork.buffer();
        packet.writeInt(events.size());
        for (EffectEvent event : events) {
            Buf.writeString(
                packet,
                event.type()
                    .id());
            event.encode(event, packet);
        }
        Buf.writeBlockPos(packet, origin);
        packet.writeBoolean(playerPartOfSourceGame);
        MinoNetwork.sendPacketToPlayer(target, ID, packet);
    }

    public static class Client {

        public static void handleS2C(ByteBuf packet) {
            int size = packet.readInt();
            List<EffectEvent> sounds = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String id = Buf.readString(packet);
                EffectEvent.Type<?> type = EffectEvents.get(id);
                sounds.add(
                    type.function()
                        .apply(packet));
            }
            BlockPos origin = Buf.readBlockPos(packet);
            boolean playerPartOfSourceGame = packet.readBoolean();
            MinoClient.SOUND_QUEUE.addAll(sounds, origin, Minecraft.getMinecraft().thePlayer, playerPartOfSourceGame);
        }
    }
}
