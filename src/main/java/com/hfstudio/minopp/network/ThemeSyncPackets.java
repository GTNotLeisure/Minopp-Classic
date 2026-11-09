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

public final class ThemeSyncPackets {

    private ThemeSyncPackets() {}

    public static final String C2S_ID = "minopp:theme_select";
    public static final String S2C_ID = "minopp:theme_sync";

    private static final Map<UUID, String> SERVER_THEMES = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHANGE = new HashMap<>();
    private static final java.util.regex.Pattern THEME_ID = java.util.regex.Pattern.compile("[a-z0-9_.:-]{1,64}");

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        String theme = Buf.readString(packet);
        if (!THEME_ID.matcher(theme)
            .matches()) return;
        UUID id = player.getGameProfile()
            .getId();
        // each change goes to every player: ignore repeats and changes faster than one a second
        long now = System.currentTimeMillis();
        if (theme.equals(SERVER_THEMES.get(id)) || now - LAST_CHANGE.getOrDefault(id, 0L) < 1000) return;
        LAST_CHANGE.put(id, now);
        SERVER_THEMES.put(id, theme);
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
        for (EntityPlayerMP o : server.getConfigurationManager().playerEntityList) {
            MinoNetwork.sendPacketToPlayer(o, S2C_ID, encode(themes));
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
            UUID id = event.player.getGameProfile()
                .getId();
            SERVER_THEMES.remove(id);
            LAST_CHANGE.remove(id);
        }
    }
}
