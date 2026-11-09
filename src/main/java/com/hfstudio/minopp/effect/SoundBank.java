package com.hfstudio.minopp.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.config.Prefs;

/**
 * Which sound every game event makes for this player. Each sound has a slot, and each slot plays from a pack of the
 * player's choice or stays silent. Packs other than the default only list the slots they replace.
 */
public final class SoundBank {

    private SoundBank() {}

    public record Sound(String name, float volume, float pitch) {}

    /** A sound the player can change; {@code sample} is what it plays when previewed. */
    public record Slot(String id, String group, String sample) {}

    public static final String DEFAULT = "mino", SILENT = "off";
    public static final List<String> PACKS = List.of(DEFAULT, "minecraft", "note", SILENT);

    public static final List<Slot> SLOTS = new ArrayList<>();
    private static final Map<String, Slot> BY_ID = new HashMap<>();
    // the vanilla sounds the special cards make, by the slot they belong to
    private static final Map<String, String> EFFECT_SLOT = new HashMap<>();
    private static final Map<String, Map<String, Sound>> PACK_SOUNDS = new HashMap<>();

    static {
        for (String id : new String[] { "play", "draw", "draw_multi", "pass", "turn_notice", "turn_notice_again",
            "turn_notice_continuous", "win", "mino_shout", "mino_shout_invalid", "doubt_success", "hand_change" }) {
            slot(id, "game", "minopp:game." + id);
        }
        effect("skip", "note.snare");
        effect("reverse", "random.bow");
        effect("draw_hit", "random.bowhit");
        effect("flip", "mob.enderdragon.wings");
        effect("wild", "random.orb");
        effect("bonus", "random.levelup");
        effect("blast", "fireworks.blast", "fireworks.largeBlast", "random.explode");
        effect("fuse", "random.fuse");
        effect("pop", "random.pop");
        effect("click", "random.click");
        effect("jingle", "note.pling", "note.harp", "note.hat");
        slot("misc", "effects", "random.fizz");

        pack(
            "minecraft",
            "play=dig.cloth:1.4",
            "draw=random.wood_click:1.6",
            "draw_multi=random.chestopen:1.2",
            "pass=random.door_close:1.4",
            "turn_notice=random.orb:1.0",
            "turn_notice_again=random.orb:1.4",
            "turn_notice_continuous=note.pling:1.6",
            "win=random.levelup:1.0",
            "mino_shout=mob.villager.yes:1.0",
            "mino_shout_invalid=mob.villager.no:1.0",
            "doubt_success=random.successful_hit:1.0",
            "hand_change=mob.endermen.portal:1.4");
        pack(
            "note",
            "play=note.hat:1.4",
            "draw=note.hat:1.8",
            "draw_multi=note.bassattack:1.0",
            "pass=note.bass:0.8",
            "turn_notice=note.pling:1.0",
            "turn_notice_again=note.pling:1.26",
            "turn_notice_continuous=note.pling:1.5",
            "win=note.harp:1.5",
            "mino_shout=note.pling:2.0",
            "mino_shout_invalid=note.bass:0.6",
            "doubt_success=note.harp:1.26",
            "hand_change=note.harp:0.8",
            "reverse=note.harp:1.6",
            "draw_hit=note.bassattack:0.8",
            "flip=note.harp:0.7",
            "wild=note.pling:1.4",
            "bonus=note.pling:1.8",
            "blast=note.bd:0.8",
            "fuse=note.hat:0.6",
            "pop=note.hat:1.2",
            "click=note.hat:1.6",
            "misc=note.harp:1.0");
    }

    private static void slot(String id, String group, String sample) {
        Slot s = new Slot(id, group, sample);
        SLOTS.add(s);
        BY_ID.put(id, s);
    }

    private static void effect(String id, String... sounds) {
        slot(id, "effects", sounds[0]);
        for (String s : sounds) EFFECT_SLOT.put(s, id);
    }

    private static void pack(String pack, String... entries) {
        Map<String, Sound> map = new HashMap<>();
        for (String e : entries) {
            String[] kv = e.split("=");
            String[] sp = kv[1].split(":");
            map.put(kv[0], new Sound(sp[0], 1f, Float.parseFloat(sp[1])));
        }
        PACK_SOUNDS.put(pack, map);
    }

    /** The packs a slot can play from: the default, the packs that replace it, and silence. */
    public static List<String> options(String slot) {
        List<String> out = new ArrayList<>();
        for (String p : PACKS) {
            if (p.equals(DEFAULT) || p.equals(SILENT)
                || PACK_SOUNDS.get(p)
                    .containsKey(slot))
                out.add(p);
        }
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
                if (kv.length == 2 && BY_ID.containsKey(kv[0]) && PACKS.contains(kv[1])) choices.put(kv[0], kv[1]);
            }
        }
        return choices;
    }

    public static String choice(String slot) {
        return choices().getOrDefault(slot, DEFAULT);
    }

    public static void set(String slot, String pack) {
        Map<String, String> c = choices();
        if (pack.equals(DEFAULT)) c.remove(slot);
        else c.put(slot, pack);
        store(c);
    }

    /** Uses a pack for every sound it has; the others keep the default (or go silent for the silent pack). */
    public static void setAll(String pack) {
        Map<String, String> c = choices();
        c.clear();
        if (!pack.equals(DEFAULT)) for (Slot s : SLOTS) if (options(s.id()).contains(pack)) c.put(s.id(), pack);
        store(c);
    }

    /** True when every slot that the pack covers uses it. */
    public static boolean allUse(String pack) {
        for (Slot s : SLOTS) {
            if (options(s.id()).contains(pack) ? !choice(s.id()).equals(pack) : !choice(s.id()).equals(DEFAULT))
                return false;
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
        if (sound.startsWith("minopp:game.")) {
            String id = sound.substring("minopp:game.".length());
            return BY_ID.containsKey(id) ? id : "misc";
        }
        String bare = sound.startsWith("minecraft:") ? sound.substring("minecraft:".length()) : sound;
        return EFFECT_SLOT.getOrDefault(bare, "misc");
    }

    /** What a sound sent by the game actually plays for this player, or null for silence. */
    public static Sound resolve(String sound, float volume, float pitch) {
        String slot = slotOf(sound);
        String pack = choice(slot);
        if (pack.equals(SILENT)) return null;
        Sound alt = pack.equals(DEFAULT) ? null
            : PACK_SOUNDS.get(pack)
                .get(slot);
        if (alt == null) return new Sound(sound, volume, pitch);
        return new Sound(alt.name(), volume * alt.volume(), pitch * alt.pitch());
    }

    public static Sound preview(String slot) {
        Slot s = BY_ID.get(slot);
        return s == null ? null : resolve(s.sample(), 1f, 1f);
    }
}
