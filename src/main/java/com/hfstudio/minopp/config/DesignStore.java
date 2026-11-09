package com.hfstudio.minopp.config;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.game.TableConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class DesignStore {

    public static final int SLOTS = 8;

    public static final class Design {

        public String name;
        public final TableConfig config;

        public Design(String name, TableConfig config) {
            this.name = name;
            this.config = config;
        }
    }

    private static final Design[] designs = new Design[SLOTS];
    private static boolean loaded = false;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private DesignStore() {}

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/minopp/designs.json");
    }

    public static Design get(int slot) {
        ensureLoaded();
        return slot >= 0 && slot < SLOTS ? designs[slot] : null;
    }

    public static void put(int slot, Design design) {
        ensureLoaded();
        designs[slot] = design;
        save();
    }

    public static void delete(int slot) {
        ensureLoaded();
        designs[slot] = null;
        save();
    }

    public static String nextName() {
        ensureLoaded();
        int n = 1;
        for (Design d : designs) if (d != null) n++;
        return "#" + n;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            JsonArray arr = new JsonParser().parse(r)
                .getAsJsonArray();
            for (int i = 0; i < Math.min(SLOTS, arr.size()); i++) {
                JsonElement e = arr.get(i);
                if (e == null || e.isJsonNull()) continue;
                JsonObject o = e.getAsJsonObject();
                designs[i] = new Design(
                    o.get("name")
                        .getAsString(),
                    fromJson(o.getAsJsonObject("config")));
            }
        } catch (Exception e) {
            Mino.LOGGER.warn("Failed to read Mino++ designs: {}", e.toString());
        }
    }

    private static void save() {
        JsonArray arr = new JsonArray();
        for (Design d : designs) {
            if (d == null) {
                arr.add(JsonNull.INSTANCE);
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", d.name);
            o.add("config", toJson(d.config));
            arr.add(o);
        }
        File f = file();
        f.getParentFile()
            .mkdirs();
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(arr, w);
        } catch (Exception e) {
            Mino.LOGGER.warn("Failed to save Mino++ designs: {}", e.toString());
        }
    }

    private static JsonObject toJson(TableConfig c) {
        JsonObject o = new JsonObject();
        JsonObject rules = new JsonObject();
        for (Map.Entry<String, Integer> e : c.rules.entrySet()) rules.addProperty(e.getKey(), e.getValue());
        o.add("rules", rules);
        JsonArray packs = new JsonArray();
        for (String p : c.packs) packs.add(new JsonPrimitive(p));
        o.add("packs", packs);
        JsonObject counts = new JsonObject();
        for (Map.Entry<String, Integer> e : c.counts.entrySet()) counts.addProperty(e.getKey(), e.getValue());
        o.add("counts", counts);
        return o;
    }

    private static TableConfig fromJson(JsonObject o) {
        TableConfig c = new TableConfig();
        c.packs.clear();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rules")
            .entrySet()) {
            c.rules.put(
                e.getKey(),
                e.getValue()
                    .getAsInt());
        }
        List<String> packs = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("packs")) packs.add(e.getAsString());
        c.packs.addAll(packs);
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("counts")
            .entrySet()) {
            c.counts.put(
                e.getKey(),
                e.getValue()
                    .getAsInt());
        }
        return c;
    }
}
