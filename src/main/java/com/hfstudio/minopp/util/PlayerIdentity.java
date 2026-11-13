package com.hfstudio.minopp.util;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class PlayerIdentity {

    private PlayerIdentity() {}

    public static UUID of(Entity entity) {
        if (entity instanceof EntityPlayer player) {
            return player.worldObj != null && player.worldObj.isRemote ? Client.of(player)
                : player.getGameProfile()
                    .getId();
        }
        return entity.getUniqueID();
    }

    @SideOnly(Side.CLIENT)
    public static class Client {

        private static NetHandlerPlayClient connection;
        private static UUID serverId;

        private Client() {}

        /**
         * Login packets arrive after JoinGame; keep the identity across respawns and dimension changes.
         */
        public static void bind(UUID playerId) {
            connection = Minecraft.getMinecraft()
                .getNetHandler();
            serverId = Objects.requireNonNull(playerId, "playerId");
        }

        private static UUID of(EntityPlayer player) {
            Minecraft mc = Minecraft.getMinecraft();
            if (player == mc.thePlayer && connection != null && connection == mc.getNetHandler() && serverId != null) {
                return serverId;
            }
            return player.getGameProfile()
                .getId();
        }

        public static void clearDisconnected() {
            if (connection != null && !connection.getNetworkManager()
                .isChannelOpen()) {
                connection = null;
                serverId = null;
            }
        }
    }
}
