package com.hfstudio.minopp.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.hfstudio.minopp.util.Buf;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import io.netty.buffer.ByteBuf;

/**
 * Card themes are client-side resource packs. Each client tells the server which theme it uses; the server relays
 * the choices to everyone, so a player who has the same theme installed sees your cards in your theme.
 */
public final class ThemeSyncPackets {

    private ThemeSyncPackets() {}

    public static final String C2S_ID = "minopp:theme_select";
    public static final String S2C_ID = "minopp:theme_sync";

    /** Server side: theme chosen by each online player. */
    private static final Map<UUID, String> SERVER_THEMES = new HashMap<>();

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        String theme = Buf.readString(packet);
        if (theme.length() > 64) return;
        SERVER_THEMES.put(
            player.getGameProfile()
                .getId(),
            theme);
        broadcast(
            Map.of(
                player.getGameProfile()
                    .getId(),
                theme));
    }

    private static ByteBuf encode(Map<UUID, String> themes) {
        ByteBuf packet = MinoNetwork.buffer();
        packet.writeInt(themes.size());
        for (Map.Entry<UUID, String> e : themes.entrySet()) {
            Buf.writeUUID(packet, e.getKey());
            Buf.writeString(packet, e.getValue());
        }
        return packet;
    }

    private static void broadcast(Map<UUID, String> themes) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            MinoNetwork.sendPacketToPlayer((EntityPlayerMP) o, S2C_ID, encode(themes));
        }
    }

    public static Map<UUID, String> decode(ByteBuf packet) {
        Map<UUID, String> themes = new HashMap<>();
        int n = packet.readInt();
        for (int i = 0; i < n; i++) themes.put(Buf.readUUID(packet), Buf.readString(packet));
        return themes;
    }

    public static void sendSelectionC2S(String themeId) {
        ByteBuf packet = MinoNetwork.buffer();
        Buf.writeString(packet, themeId);
        MinoNetwork.sendPacketToServer(C2S_ID, packet);
    }

    public static class ServerEvents {

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP mp && !SERVER_THEMES.isEmpty()) {
                MinoNetwork.sendPacketToPlayer(mp, S2C_ID, encode(new HashMap<>(SERVER_THEMES)));
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            SERVER_THEMES.remove(
                event.player.getGameProfile()
                    .getId());
        }
    }
}
