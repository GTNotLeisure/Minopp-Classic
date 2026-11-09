package com.hfstudio.minopp.render;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.network.ThemeSyncPackets;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ThemeManager implements IResourceManagerReloadListener {

    public static final ThemeManager INSTANCE = new ThemeManager();
    public static final String DEFAULT = "classic";

    /** {@code tint}: the theme's number and symbol layers are white and take the card's color. */
    public record ThemeInfo(String id, String name, String fallback, boolean tint) {}

    private final Map<String, ThemeInfo> themes = new LinkedHashMap<>();
    private final Map<ResourceLocation, Boolean> existsCache = new HashMap<>();
    private final Map<String, ResourceLocation> textureCache = new HashMap<>();
    public final Map<UUID, String> playerThemes = new HashMap<>();
    private String lastSentTheme = null;

    private ThemeManager() {
        themes.put(DEFAULT, new ThemeInfo(DEFAULT, "Classic", null, false));
    }

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        themes.clear();
        existsCache.clear();
        textureCache.clear();
        CardBatch.clearCache();
        themes.put(DEFAULT, new ThemeInfo(DEFAULT, "Classic", null, false));
        try {
            List<?> resources = rm.getAllResources(new ResourceLocation(Mino.MOD_ID, "themes.json"));
            for (Object o : resources) {
                try (Reader reader = new InputStreamReader(((IResource) o).getInputStream(), StandardCharsets.UTF_8)) {
                    JsonElement root = new JsonParser().parse(reader);
                    JsonArray list = root.getAsJsonObject()
                        .getAsJsonArray("themes");
                    for (JsonElement e : list) {
                        JsonObject t = e.getAsJsonObject();
                        String id = t.get("id")
                            .getAsString();
                        String name = t.has("name") ? t.get("name")
                            .getAsString() : id;
                        String fallback = t.has("fallback") ? t.get("fallback")
                            .getAsString() : DEFAULT;
                        boolean tint = t.has("tint") && t.get("tint")
                            .getAsBoolean();
                        themes.put(id, new ThemeInfo(id, name, fallback, tint));
                    }
                } catch (Exception e) {
                    Mino.LOGGER.warn("Invalid minopp themes.json in a resource pack: {}", e.toString());
                }
            }
        } catch (Exception ignored) {}
    }

    public List<ThemeInfo> getThemes() {
        return new ArrayList<>(themes.values());
    }

    public boolean isInstalled(String id) {
        return id != null && themes.containsKey(id);
    }

    public String selected() {
        String id = ClientConfig.display.cardTheme;
        return isInstalled(id) ? id : DEFAULT;
    }

    public ThemeInfo selectedInfo() {
        return themes.get(selected());
    }

    public String themeFor(UUID owner) {
        Minecraft mc = Minecraft.getMinecraft();
        if (owner == null || mc.thePlayer == null) return selected();
        if (owner.equals(
            mc.thePlayer.getGameProfile()
                .getId()))
            return selected();
        if (!ClientConfig.display.showOthersThemes) return selected();
        String theirs = playerThemes.get(owner);
        return isInstalled(theirs) ? theirs : selected();
    }

    public void select(String id) {
        if (!isInstalled(id)) id = DEFAULT;
        ClientConfig.display.cardTheme = id;
        try {
            ConfigurationManager.save(ClientConfig.class);
        } catch (Exception e) {
            Mino.LOGGER.warn("Failed to save theme selection", e);
        }
        sendSelection(true);
    }

    public String cycle() {
        List<ThemeInfo> list = getThemes();
        int idx = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i)
            .id()
            .equals(selected())) idx = i;
        String next = list.get((idx + 1) % list.size())
            .id();
        select(next);
        return next;
    }

    public void sendSelection(boolean force) {
        String current = selected();
        if (Minecraft.getMinecraft()
            .getNetHandler() == null) return;
        if (!force && current.equals(lastSentTheme)) return;
        lastSentTheme = current;
        ThemeSyncPackets.sendSelectionC2S(current);
    }

    public void onDisconnect() {
        lastSentTheme = null;
        playerThemes.clear();
    }

    /** The color a card layer is drawn in: white, or the card's color for a tinting theme's glyph layers. */
    public int layerTint(String theme, CardFace face, int layer) {
        if (layer == 0 || face == null || face.color.isWild()) return 0xFFFFFF;
        ThemeInfo info = themes.get(theme);
        return info != null && info.tint() ? face.color.rgb & 0xFFFFFF : 0xFFFFFF;
    }

    public ResourceLocation texture(String theme, String layer) {
        String cacheKey = theme + "/" + layer;
        if (textureCache.containsKey(cacheKey)) return textureCache.get(cacheKey);
        ResourceLocation result = null;
        String t = theme;
        for (int depth = 0; depth < 5 && t != null && result == null; depth++) {
            ResourceLocation loc = new ResourceLocation(Mino.MOD_ID, "textures/cards/" + t + "/" + layer + ".png");
            if (exists(loc)) result = loc;
            ThemeInfo info = themes.get(t);
            t = info == null ? (t.equals(DEFAULT) ? null : DEFAULT) : info.fallback();
        }
        if (result == null && !theme.equals(DEFAULT)) result = texture(DEFAULT, layer);
        textureCache.put(cacheKey, result);
        return result;
    }

    private boolean exists(ResourceLocation loc) {
        Boolean cached = existsCache.get(loc);
        if (cached != null) return cached;
        boolean ok;
        try {
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(loc)
                .getInputStream()
                .close();
            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        existsCache.put(loc, ok);
        return ok;
    }
}
