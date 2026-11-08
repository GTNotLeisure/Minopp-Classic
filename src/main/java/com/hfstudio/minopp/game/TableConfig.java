package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * Per-table configuration: rules, enabled packs and per-card count overrides. Card counts work like this: an entry of
 * an enabled pack uses its default count unless overridden; an entry of a disabled pack is only included when it has
 * an override, which lets single cards of a pack (e.g. Discard All) be mixed into any game.
 */
public class TableConfig {

    public final Map<String, Integer> rules = new HashMap<>();
    public final Set<String> packs = new LinkedHashSet<>();
    /** "packId:entryKey" -> count */
    public final Map<String, Integer> counts = new HashMap<>();

    public TableConfig() {
        packs.add(CardPack.CLASSIC);
    }

    public int getInt(String rule) {
        Integer v = rules.get(rule);
        if (v != null) return v;
        GameRules.Rule def = GameRules.get(rule);
        return def == null ? 0 : def.defaultValue();
    }

    public boolean getBool(String rule) {
        return getInt(rule) != 0;
    }

    public void set(String rule, int value) {
        GameRules.Rule def = GameRules.get(rule);
        if (def == null) return;
        rules.put(rule, def.clamp(value));
    }

    public boolean isPackEnabled(String packId) {
        return packs.contains(packId);
    }

    public void setPackEnabled(String packId, boolean enabled) {
        CardPack pack = CardPack.get(packId);
        if (pack == null || pack.backSideOnly) return;
        if (enabled) {
            packs.add(packId);
            for (Map.Entry<String, Integer> e : pack.presetRules.entrySet()) set(e.getKey(), e.getValue());
        } else {
            packs.remove(packId);
            if (packId.equals(CardPack.FLIP)) set(GameRules.FLIP_MODE, 0);
            if (packId.equals(CardPack.FLEX)) set(GameRules.FLEX_POWER, 0);
        }
    }

    public static String countKey(CardPack pack, CardPack.Entry entry) {
        return pack.id + ":" + entry.key();
    }

    public int getCount(CardPack pack, CardPack.Entry entry) {
        Integer override = counts.get(countKey(pack, entry));
        if (override != null) return override;
        boolean enabled = pack.backSideOnly ? isPackEnabled(CardPack.FLIP) || getBool(GameRules.FLIP_MODE)
            : isPackEnabled(pack.id);
        return enabled ? entry.count() : 0;
    }

    public boolean hasOverride(CardPack pack, CardPack.Entry entry) {
        return counts.containsKey(countKey(pack, entry));
    }

    public void setCount(String key, int count) {
        if (count < 0) {
            counts.remove(key);
        } else {
            counts.put(key, Math.min(count, 64));
        }
    }

    public void resetToPreset(String presetPack) {
        rules.clear();
        packs.clear();
        counts.clear();
        if (presetPack.equals(CardPack.PARTY) || presetPack.equals(CardPack.HERO)
            || presetPack.equals(CardPack.RHYTHM)
            || presetPack.equals(CardPack.MAYHEM)
            || presetPack.equals(CardPack.LEGEND)
            || presetPack.equals(CardPack.ANNIVERSARY)
            || presetPack.equals(CardPack.WEEKEND)) {
            setPackEnabled(CardPack.CLASSIC, true);
        }
        setPackEnabled(presetPack, true);
    }

    /** Light-side faces of the deck, before copies are applied. */
    public List<CardFace> buildFaces(boolean backSide) {
        List<CardFace> faces = new ArrayList<>();
        for (CardPack pack : CardPack.all()) {
            if (pack.backSideOnly != backSide) continue;
            for (CardPack.Entry entry : pack.entries) {
                int count = getCount(pack, entry);
                if (count <= 0) continue;
                CardType type = CardTypes.get(entry.typeId());
                int[] numbers = entry.numbers().length == 0 ? new int[] { -1 } : entry.numbers();
                for (int number : numbers) {
                    if (type.wild) {
                        for (int i = 0; i < count; i++) faces.add(new CardFace(type.id, CardColor.WILD, number));
                    } else {
                        for (CardColor color : CardColor.forSide(entry.dark())) {
                            for (int i = 0; i < count; i++) {
                                faces.add(
                                    new CardFace(
                                        type.id,
                                        color,
                                        number,
                                        entry.flexColor() ? color.flexPartner() : null));
                            }
                        }
                    }
                }
            }
        }
        return faces;
    }

    public int countDeckSize() {
        return buildFaces(false).size();
    }

    /** Builds and shuffles the physical deck for a game with the given number of players. */
    public List<Card> buildDeck(int playerCount, Random random) {
        List<CardFace> lightFaces = buildFaces(false);
        if (lightFaces.isEmpty()) lightFaces = new TableConfig().buildFaces(false);
        int copies = getInt(GameRules.DECK_COPIES);
        if (copies <= 0) {
            // Auto: enough cards for the opening hands plus a healthy draw pile
            int needed = playerCount * getInt(GameRules.INITIAL_HAND) * 2 + 40;
            copies = Math.max(1, (int) Math.ceil(needed / (double) lightFaces.size()));
        }
        List<CardFace> allLight = new ArrayList<>();
        for (int i = 0; i < copies; i++) allLight.addAll(lightFaces);

        List<CardFace> darkFaces = null;
        if (getBool(GameRules.FLIP_MODE)) {
            List<CardFace> base = buildFaces(true);
            if (base.isEmpty()) base = buildDefaultDarkFaces();
            darkFaces = new ArrayList<>();
            while (darkFaces.size() < allLight.size()) darkFaces.addAll(base);
            Collections.shuffle(darkFaces, random);
        }

        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < allLight.size(); i++) {
            deck.add(new Card(i + 1, allLight.get(i), darkFaces == null ? null : darkFaces.get(i)));
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    private static List<CardFace> buildDefaultDarkFaces() {
        TableConfig def = new TableConfig();
        def.set(GameRules.FLIP_MODE, 1);
        return def.buildFaces(true);
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound rulesTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : rules.entrySet()) rulesTag.setInteger(e.getKey(), e.getValue());
        tag.setTag("rules", rulesTag);
        NBTTagList packsTag = new NBTTagList();
        for (String p : packs) packsTag.appendTag(new NBTTagString(p));
        tag.setTag("packs", packsTag);
        NBTTagCompound countsTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : counts.entrySet()) countsTag.setInteger(e.getKey(), e.getValue());
        tag.setTag("counts", countsTag);
        return tag;
    }

    public void fromTag(NBTTagCompound tag) {
        rules.clear();
        packs.clear();
        counts.clear();
        NBTTagCompound rulesTag = tag.getCompoundTag("rules");
        for (Object k : rulesTag.func_150296_c()) {
            String key = (String) k;
            // Rules of the original mod were stored as booleans
            rules.put(
                key,
                rulesTag.func_150299_b(key) == 1 ? (rulesTag.getBoolean(key) ? 1 : 0) : rulesTag.getInteger(key));
        }
        if (tag.hasKey("packs")) {
            NBTTagList packsTag = tag.getTagList("packs", 8);
            for (int i = 0; i < packsTag.tagCount(); i++) packs.add(packsTag.getStringTagAt(i));
        } else {
            packs.add(CardPack.CLASSIC);
        }
        NBTTagCompound countsTag = tag.getCompoundTag("counts");
        for (Object k : countsTag.func_150296_c()) counts.put((String) k, countsTag.getInteger((String) k));
    }
}
