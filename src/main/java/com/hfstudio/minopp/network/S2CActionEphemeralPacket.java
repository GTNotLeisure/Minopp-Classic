package com.hfstudio.minopp.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.Pair;

import io.netty.buffer.ByteBuf;

public class S2CActionEphemeralPacket {

    public static final String ID = "minopp:action_ephemeral";

    public static void sendS2C(EntityPlayerMP target, BlockPos gamePos, ActionMessage message) {
        ByteBuf packet = MinoNetwork.buffer();
        Buf.writeBlockPos(packet, gamePos);
        Buf.writeNbt(packet, message.toTag());
        MinoNetwork.sendPacketToPlayer(target, ID, packet);
    }

    public static class Client {

        public static void handleS2C(ByteBuf packet) {
            BlockPos gamePos = Buf.readBlockPos(packet);
            ActionMessage message = new ActionMessage(Buf.readNbt(packet));
            BlockEntityMinoTable tableEntity = ItemHandCards.getTable(Minecraft.getMinecraft().theWorld, gamePos);
            if (tableEntity != null) {
                tableEntity.clientMessageList.add(new Pair<>(message, System.currentTimeMillis() + 8000));
            }
        }
    }
}
