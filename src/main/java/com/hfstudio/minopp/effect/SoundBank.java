package com.hfstudio.minopp.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.config.Prefs;

/**
 * Which sound every game event makes for this player. Each sound belongs to a slot, and each slot plays from a sound
 * pack of the player's choice or stays silent.
 * <p>
 * Other mods register their own packs (client side, during init) instead of shipping resource packs:
 *
 * <pre>
 * SoundBank.pack("mymod:arcade", new ResourceLocation("mymod", "textures/gui/arcade.png"))
 *     .sound("play", "mymod:arcade.play", 1f)
 *     .sound("mino_shout", "mymod:arcade.shout", 1.2f)
 *     .register();
 * </pre>
 *
 * A pack only lists the slots it replaces; the others keep the default sound. Mods can also add slots for their own
 * sounds with {@link #registerSlot}, so players can swap or silence them too. A pack is named by the lang key
 * {@code gui.<modid>.sound.pack.<name>} (and {@code .desc}), a slot by {@code gui.<modid>.sound.<name>}.
 */
public class SoundBank {

    private SoundBank() {}

    public record Sound(String name, float volume, float pitch) {}

    /**
     * A sound the player can change; {@code sample} is what it plays when previewed.
     */
    public record Slot(String id, String group, String sample) {

        public String langKey() {
            return key(id, "sound.");
        }
    }

    public record Pack(String id, ResourceLocation icon, Map<String, Sound> sounds) {

        public String langKey() {
            return key(id, "sound.pack.");
        }
    }

    public static class PackBuilder {

        private final String id;
        private final ResourceLocation icon;
        private final Map<String, Sound> sounds = new HashMap<>();

        private PackBuilder(String id, ResourceLocation icon) {
            this.id = id;
            this.icon = icon;
        }

        /**
         * What the pack plays for a slot, at a pitch relative to the game's own.
         */
        public PackBuilder sound(String slot, String name, float pitch) {
            return sound(slot, name, 1f, pitch);
        }

        public PackBuilder sound(String slot, String name, float volume, float pitch) {
            sounds.put(slot, new Sound(name, volume, pitch));
            return this;
        }

        public Pack register() {
            return registerPack(new Pack(id, icon, Collections.unmodifiableMap(sounds)));
        }
    }

    public static final String DEFAULT = "mino", SILENT = "off";

    private static final List<Slot> SLOTS = new ArrayList<>();
    private static final Map<String, Slot> SLOT_BY_ID = new HashMap<>();
    private static final List<Pack> PACKS = new ArrayList<>();
    private static final Map<String, Pack> PACK_BY_ID = new HashMap<>();
    private static final Map<String, String> SOUND_SLOT = new HashMap<>();

    static {
        for (String id : new String[] { "play", "draw", "draw_multi", "pass", "turn_notice", "turn_notice_again",
            "turn_notice_continuous", "win", "mino_shout", "mino_shout_invalid", "doubt_success", "hand_change" }) {
            registerSlot(id, "game", "minopp:game." + id, "minopp:game." + id);
        }
        registerSlot("skip", "effects", "note.snare", "note.snare");
        registerSlot("reverse", "effects", "random.bow", "random.bow");
        registerSlot("draw_hit", "effects", "random.bowhit", "random.bowhit");
        registerSlot("flip", "effects", "mob.enderdragon.wings", "mob.enderdragon.wings");
        registerSlot("wild", "effects", "random.orb", "random.orb");
        registerSlot("bonus", "effects", "random.levelup", "random.levelup");
        registerSlot(
            "blast",
            "effects",
            "fireworks.blast",
            "fireworks.blast",
            "fireworks.largeBlast",
            "random.explode");
        registerSlot("fuse", "effects", "random.fuse", "random.fuse");
        registerSlot("pop", "effects", "random.pop", "random.pop");
        registerSlot("click", "effects", "random.click", "random.click");
        registerSlot("jingle", "effects", "note.pling", "note.pling", "note.harp", "note.hat");
        registerSlot("misc", "effects", "random.fizz");

        registerPack(new Pack(DEFAULT, icon("mino"), Collections.emptyMap()));
        pack("minecraft", icon("minecraft")).sound("play", "dig.cloth", 1.4f)
            .sound("draw", "random.wood_click", 1.6f)
            .sound("draw_multi", "random.chestopen", 1.2f)
            .sound("pass", "random.door_close", 1.4f)
            .sound("turn_notice", "random.orb", 1.0f)
            .sound("turn_notice_again", "random.orb", 1.4f)
            .sound("turn_notice_continuous", "note.pling", 1.6f)
            .sound("win", "random.levelup", 1.0f)
            .sound("mino_shout", "mob.villager.yes", 1.0f)
            .sound("mino_shout_invalid", "mob.villager.no", 1.0f)
            .sound("doubt_success", "random.successful_hit", 1.0f)
            .sound("hand_change", "mob.endermen.portal", 1.4f)
            .register();
        pack("note", icon("note")).sound("play", "note.hat", 1.4f)
            .sound("draw", "note.hat", 1.8f)
            .sound("draw_multi", "note.bassattack", 1.0f)
            .sound("pass", "note.bass", 0.8f)
            .sound("turn_notice", "note.pling", 1.0f)
            .sound("turn_notice_again", "note.pling", 1.26f)
            .sound("turn_notice_continuous", "note.pling", 1.5f)
            .sound("win", "note.harp", 1.5f)
            .sound("mino_shout", "note.pling", 2.0f)
            .sound("mino_shout_invalid", "note.bass", 0.6f)
            .sound("doubt_success", "note.harp", 1.26f)
            .sound("hand_change", "note.harp", 0.8f)
            .sound("reverse", "note.harp", 1.6f)
            .sound("draw_hit", "note.bassattack", 0.8f)
            .sound("flip", "note.harp", 0.7f)
            .sound("wild", "note.pling", 1.4f)
            .sound("bonus", "note.pling", 1.8f)
            .sound("blast", "note.bd", 0.8f)
            .sound("fuse", "note.hat", 0.6f)
            .sound("pop", "note.hat", 1.2f)
            .sound("click", "note.hat", 1.6f)
            .sound("misc", "note.harp", 1.0f)
            .register();
        registerPack(new Pack(SILENT, icon("off"), Collections.emptyMap()));
    }

    private static ResourceLocation icon(String pack) {
        return Mino.id("textures/gui/ui/icon_sound_" + pack + ".png");
    }

    private static String key(String id, String kind) {
        int colon = id.indexOf(':');
        return colon < 0 ? "gui.minopp." + kind + id
            : "gui." + id.substring(0, colon) + "." + kind + id.substring(colon + 1);
    }

    private static String bare(String sound) {
        return sound.startsWith("minecraft:") ? sound.substring("minecraft:".length()) : sound;
    }

    public static PackBuilder pack(String id, ResourceLocation icon) {
        return new PackBuilder(id, icon);
    }

    /**
     * Adds a pack; it shows before the silent pack.
     */
    public static Pack registerPack(Pack pack) {
        if (PACK_BY_ID.containsKey(pack.id()))
            throw new IllegalArgumentException("Sound pack already registered: " + pack.id());
        int at = PACK_BY_ID.containsKey(SILENT) ? PACKS.size() - 1 : PACKS.size();
        PACKS.add(at, pack);
        PACK_BY_ID.put(pack.id(), pack);
        return pack;
    }

    /**
     * Adds a slot players can swap or silence. {@code group} picks the section it shows in ("game", "effects" or a
     * group of your own, named by {@code gui.minopp.sound.group.<group>}); {@code sounds} are the sound names the game
     * plays for it.
     */
    public static Slot registerSlot(String id, String group, String sample, String... sounds) {
        if (SLOT_BY_ID.containsKey(id)) throw new IllegalArgumentException("Sound slot already registered: " + id);
        Slot s = new Slot(id, group, sample);
        SLOTS.add(s);
        SLOT_BY_ID.put(id, s);
        for (String sound : sounds) SOUND_SLOT.put(bare(sound), id);
        return s;
    }

    public static List<Slot> slots() {
        return Collections.unmodifiableList(SLOTS);
    }

    public static List<Pack> packs() {
        return Collections.unmodifiableList(PACKS);
    }

    public static Pack getPack(String id) {
        return PACK_BY_ID.get(id);
    }

    private static boolean covers(Pack pack, String slot) {
        return pack.id()
            .equals(DEFAULT)
            || pack.id()
                .equals(SILENT)
            || pack.sounds()
                .containsKey(slot);
    }

    /**
     * The packs a slot can play from: the default, the packs that replace it, and silence.
     */
    public static List<String> options(String slot) {
        List<String> out = new ArrayList<>();
        for (Pack p : PACKS) if (covers(p, slot)) out.add(p.id());
        return out;
    }

    private static String parsedFrom;
    private static final Map<String, String> choices = new LinkedHashMap<>();

    private static Map<String, String> choices() {
        String raw = ClientConfig.display.soundPacks;
        if (raw == null) raw = "";
        if (!raw.equals(parsedFrom)) {
            parsedFrom = raw;
            choices.clear();
            for (String part : raw.split(";")) {
                String[] kv = part.split("=");
                // kept even when the slot or pack is not registered right now, so removing a mod loses nothing
                if (kv.length == 2) choices.put(kv[0], kv[1]);
            }
        }
        return choices;
    }

    public static String choice(String slot) {
        String c = choices().get(slot);
        Pack p = c == null ? null : PACK_BY_ID.get(c);
        return p != null && covers(p, slot) ? c : DEFAULT;
    }

    public static void set(String slot, String pack) {
        Map<String, String> c = choices();
        if (pack.equals(DEFAULT)) c.remove(slot);
        else c.put(slot, pack);
        store(c);
    }

    /**
     * Uses a pack for every sound it has; the others go back to the default.
     */
    public static void setAll(String pack) {
        Map<String, String> c = choices();
        Pack p = PACK_BY_ID.get(pack);
        for (Slot s : SLOTS) {
            if (p != null && !pack.equals(DEFAULT) && covers(p, s.id())) c.put(s.id(), pack);
            else c.remove(s.id());
        }
        store(c);
    }

    /**
     * True when every slot uses the pack where it can, and the default elsewhere.
     */
    public static boolean allUse(String pack) {
        Pack p = PACK_BY_ID.get(pack);
        if (p == null) return false;
        for (Slot s : SLOTS) {
            if (!choice(s.id()).equals(covers(p, s.id()) ? pack : DEFAULT)) return false;
        }
        return true;
    }

    private static void store(Map<String, String> c) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : c.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey())
                .append('=')
                .append(e.getValue());
        }
        ClientConfig.display.soundPacks = sb.toString();
        parsedFrom = null;
        Prefs.save();
    }

    public static String slotOf(String sound) {
        return SOUND_SLOT.getOrDefault(bare(sound), "misc");
    }

    /**
     * What a sound sent by the game actually plays for this player, or null for silence.
     */
    public static Sound resolve(String sound, float volume, float pitch) {
        String slot = slotOf(sound);
        String pack = choice(slot);
        if (pack.equals(SILENT)) return null;
        Sound alt = PACK_BY_ID.get(pack)
            .sounds()
            .get(slot);
        if (alt == null) return new Sound(sound, volume, pitch);
        return new Sound(alt.name(), volume * alt.volume(), pitch * alt.pitch());
    }

    public static Sound preview(String slot) {
        Slot s = SLOT_BY_ID.get(slot);
        return s == null ? null : resolve(s.sample(), 1f, 1f);
    }
}
