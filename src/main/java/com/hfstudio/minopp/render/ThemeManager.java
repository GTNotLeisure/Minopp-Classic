package com.hfstudio.minopp.render;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.hfstudio.minopp.util.PlayerIdentity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ThemeManager implements IResourceManagerReloadListener {

    public static final ThemeManager INSTANCE = new ThemeManager();
    public static final String DEFAULT = "classic";

    /**
     * {@code tint}: number layers take the card's color; illustrated layers retain their printed colors.
     */
    public record ThemeInfo(String id, String name, String fallback, boolean tint) {

        public ThemeInfo {
            Objects.requireNonNull(name, "name");
            if (!ThemeSyncPackets.validThemeId(id) || fallback != null && !ThemeSyncPackets.validThemeId(fallback)) {
                throw new IllegalArgumentException("Invalid theme id or fallback: " + id);
            }
        }
    }

    private final Map<String, ThemeInfo> themes = new LinkedHashMap<>();
    private final Map<String, ThemeInfo> registeredThemes = new LinkedHashMap<>();
    private final Map<ResourceLocation, Boolean> existsCache = new HashMap<>();
    private final Map<String, Map<String, ResourceLocation>> textureCache = new HashMap<>();
    private static final ResourceLocation MISSING = new ResourceLocation(Mino.MOD_ID, "missing");
    public final Map<UUID, String> playerThemes = new HashMap<>();
    private String lastSentTheme = null;
    private long resourceRevision;

    public long resourceRevision() {
        return resourceRevision;
    }

    private ThemeManager() {
        themes.put(DEFAULT, new ThemeInfo(DEFAULT, "Classic", null, false));
    }

    /**
     * Registers a client theme that survives resource reloads. Resource packs may override its metadata.
     */
    public void registerTheme(ThemeInfo theme) {
        Objects.requireNonNull(theme, "theme");
        if (theme.id() == null || theme.id()
            .isBlank() || themes.containsKey(theme.id())) {
            throw new IllegalArgumentException("Invalid or duplicate theme id: " + theme.id());
        }
        registeredThemes.put(theme.id(), theme);
        themes.put(theme.id(), theme);
        textureCache.clear();
        CardBatch.clearCache();
        resourceRevision++;
    }

    @Override
    public void onResourceManagerReload(IResourceManager rm) {
        resourceRevision++;
        themes.clear();
        existsCache.clear();
        textureCache.clear();
        CardBatch.clearCache();
        themes.put(DEFAULT, new ThemeInfo(DEFAULT, "Classic", null, false));
        themes.putAll(registeredThemes);
        List<String> domains = new ArrayList<>(rm.getResourceDomains());
        domains.sort(String::compareTo);
        domains.remove(Mino.MOD_ID);
        domains.add(0, Mino.MOD_ID);
        for (String domain : domains) loadThemes(rm, domain);
    }

    private void loadThemes(IResourceManager rm, String domain) {
        try {
            List<?> resources = rm.getAllResources(new ResourceLocation(domain, "themes.json"));
            for (Object o : resources) {
                try (Reader reader = new InputStreamReader(((IResource) o).getInputStream(), StandardCharsets.UTF_8)) {
                    JsonElement root = new JsonParser().parse(reader);
                    JsonArray list = root.getAsJsonObject()
                        .getAsJsonArray("themes");
                    for (JsonElement e : list) {
                        JsonObject t = e.getAsJsonObject();
                        String id = t.get("id")
                            .getAsString();
                        if (!domain.equals(Mino.MOD_ID) && id.indexOf(':') < 0) id = domain + ":" + id;
                        String name = t.has("name") ? t.get("name")
                            .getAsString() : id;
                        String fallback = t.has("fallback") ? t.get("fallback")
                            .getAsString() : DEFAULT;
                        boolean tint = t.has("tint") && t.get("tint")
                            .getAsBoolean();
                        themes.put(id, new ThemeInfo(id, name, fallback, tint));
                    }
                } catch (Exception e) {
                    Mino.LOGGER.warn("Invalid {}/themes.json in a resource pack: {}", domain, e.toString());
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
        if (owner.equals(PlayerIdentity.of(mc.thePlayer))) return selected();
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

    /**
     * Resolves the layer texture before applying the theme's number tint.
     */
    public int layerTint(String theme, CardFace face, int layer) {
        if (face == null || face.color.isWild()) return 0xFFFFFF;
        ThemeInfo info = themes.get(theme);
        if (info == null || !info.tint()) return 0xFFFFFF;
        ResourceLocation[] textures = CardBatch.textures(face, theme);
        return layer < 0 || layer >= textures.length ? 0xFFFFFF : layerTint(theme, face, layer, textures[layer]);
    }

    /**
     * Tints only num_ and corner_num_ textures, preserving illustrations regardless of layer order or resource domain.
     */
    public int layerTint(String theme, CardFace face, int layer, ResourceLocation texture) {
        if (texture == null || face == null || face.color.isWild()) return 0xFFFFFF;
        ThemeInfo info = themes.get(theme);
        if (info == null || !info.tint()) return 0xFFFFFF;
        String path = texture.getResourcePath();
        int nameStart = path.lastIndexOf('/') + 1;
        if (!path.startsWith("num_", nameStart) && !path.startsWith("corner_num_", nameStart)) return 0xFFFFFF;
        return face.color.rgb & 0xFFFFFF;
    }

    public ResourceLocation texture(String theme, String layer) {
        Map<String, ResourceLocation> byLayer = textureCache.computeIfAbsent(theme, k -> new HashMap<>());
        ResourceLocation cached = byLayer.get(layer);
        if (cached != null) return cached == MISSING ? null : cached;
        ResourceLocation result = null;
        String t = theme;
        Set<String> visited = new HashSet<>();
        while (t != null && result == null && visited.add(t)) {
            ResourceLocation themeId = new ResourceLocation(t.indexOf(':') < 0 ? Mino.MOD_ID + ":" + t : t);
            ResourceLocation layerId = new ResourceLocation(
                layer.indexOf(':') < 0 ? themeId.getResourceDomain() + ":" + layer : layer);
            ResourceLocation loc = new ResourceLocation(
                layerId.getResourceDomain(),
                "textures/cards/" + themeId.getResourcePath() + "/" + layerId.getResourcePath() + ".png");
            if (exists(loc)) result = loc;
            ThemeInfo info = themes.get(t);
            t = info == null ? (t.equals(DEFAULT) ? null : DEFAULT) : info.fallback();
        }
        if (result == null && !theme.equals(DEFAULT)) result = texture(DEFAULT, layer);
        byLayer.put(layer, result == null ? MISSING : result);
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
