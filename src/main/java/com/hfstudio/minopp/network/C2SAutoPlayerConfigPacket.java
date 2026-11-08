package com.hfstudio.minopp.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public class C2SAutoPlayerConfigPacket {

    public static final String ID = "minopp:auto_player_config";

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        int entityId = packet.readInt();
        boolean shouldDelete = packet.readBoolean();
        NBTTagCompound config = shouldDelete ? null : Buf.readNbt(packet);

        if (!player.canCommandSenderUseCommand(2, "")) return;
        Entity entity = player.worldObj.getEntityByID(entityId);
        if (entity instanceof EntityAutoPlayer autoPlayer) {
            if (shouldDelete) {
                autoPlayer.setDead();
            } else {
                autoPlayer.readConfigFromTag(config);
            }
        }
    }

    public static class Client {

        public static void sendC2S(EntityAutoPlayer autoPlayer) {
            ByteBuf packet = MinoNetwork.buffer();
            packet.writeInt(autoPlayer.getEntityId());
            packet.writeBoolean(false);
            Buf.writeNbt(packet, autoPlayer.writeConfigToTag());
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendDeleteC2S(EntityAutoPlayer autoPlayer) {
            ByteBuf packet = MinoNetwork.buffer();
            packet.writeInt(autoPlayer.getEntityId());
            packet.writeBoolean(true);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
