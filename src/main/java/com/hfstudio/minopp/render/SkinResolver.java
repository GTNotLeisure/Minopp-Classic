package com.hfstudio.minopp.render;

import java.net.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.Mino;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Resolves a player name or UUID into a skin texture asynchronously (the 1.7.10 counterpart of
 * SkullBlockEntity.updateGameprofile used by the 1.20.1 version).
 */
@SideOnly(Side.CLIENT)
public final class SkinResolver {

    private SkinResolver() {}

    private static final Map<String, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

    public static void resolve(String nameOrUuid, Consumer<ResourceLocation> callback) {
        GameProfile cached = PROFILE_CACHE.get(nameOrUuid);
        if (cached != null) {
            applyProfile(cached, callback);
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                GameProfile profile = lookupProfile(nameOrUuid);
                if (profile == null || profile.getId() == null) return;
                Minecraft mc = Minecraft.getMinecraft();
                profile = mc.func_152347_ac()
                    .fillProfileProperties(profile, false);
                PROFILE_CACHE.put(nameOrUuid, profile);
                final GameProfile filled = profile;
                mc.func_152344_a(() -> applyProfile(filled, callback));
            } catch (Exception e) {
                Mino.LOGGER.warn("Failed to resolve MinoBot skin {}: {}", nameOrUuid, e.toString());
            }
        }, "MinoBot Skin Resolver");
        thread.setDaemon(true);
        thread.start();
    }

    private static GameProfile lookupProfile(String nameOrUuid) {
        try {
            return new GameProfile(UUID.fromString(nameOrUuid), null);
        } catch (IllegalArgumentException ignored) {}
        final GameProfile[] result = new GameProfile[1];
        new YggdrasilAuthenticationService(
            Proxy.NO_PROXY,
            UUID.randomUUID()
                .toString()).createProfileRepository()
                    .findProfilesByNames(new String[] { nameOrUuid }, Agent.MINECRAFT, new ProfileLookupCallback() {

                        @Override
                        public void onProfileLookupSucceeded(GameProfile profile) {
                            result[0] = profile;
                        }

                        @Override
                        public void onProfileLookupFailed(GameProfile profile, Exception exception) {}
                    });
        return result[0];
    }

    @SuppressWarnings("unchecked")
    private static void applyProfile(GameProfile profile, Consumer<ResourceLocation> callback) {
        Minecraft mc = Minecraft.getMinecraft();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = mc.func_152342_ad()
            .func_152788_a(profile);
        if (textures.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            callback.accept(
                mc.func_152342_ad()
                    .func_152792_a(textures.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN));
        }
    }
}
