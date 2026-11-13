package com.hfstudio.minopp.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.PlayerIdentity;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public record S2CPlayerIdentityPacket(UUID playerId) {

    public static final String ID = "minopp:player_identity";

    public static void send(EntityPlayerMP player) {
        ByteBuf packet = MinoNetwork.buffer();
        Buf.writeUUID(packet, PlayerIdentity.of(player));
        MinoNetwork.sendPacketToPlayer(player, ID, packet);
    }

    @SideOnly(Side.CLIENT)
    public static void handle(ByteBuf packet) {
        S2CPlayerIdentityPacket identity = new S2CPlayerIdentityPacket(Buf.readUUID(packet));
        PlayerIdentity.Client.bind(identity.playerId());
    }

    public static class ServerEvents {

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP player) send(player);
        }
    }
}
