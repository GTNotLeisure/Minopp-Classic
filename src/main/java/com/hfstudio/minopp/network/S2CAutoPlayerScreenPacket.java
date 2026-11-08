package com.hfstudio.minopp.network;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.gui.AutoPlayerScreen;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public class S2CAutoPlayerScreenPacket {

    public static final String ID = "minopp:auto_player_screen";

    public static void sendS2C(EntityPlayerMP target, EntityAutoPlayer autoPlayer) {
        ByteBuf packet = MinoNetwork.buffer();
        packet.writeInt(autoPlayer.getEntityId());
        Buf.writeNbt(packet, autoPlayer.writeConfigToTag());
        MinoNetwork.sendPacketToPlayer(target, ID, packet);
    }

    public static class Client {

        public static void handleS2C(ByteBuf packet) {
            int entityId = packet.readInt();
            NBTTagCompound config = Buf.readNbt(packet);
            Minecraft mc = Minecraft.getMinecraft();
            Entity entity = mc.theWorld.getEntityByID(entityId);
            if (entity instanceof EntityAutoPlayer autoPlayer) {
                autoPlayer.readConfigFromTag(config);
                mc.displayGuiScreen(new AutoPlayerScreen(autoPlayer, mc.currentScreen));
            }
        }
    }
}
