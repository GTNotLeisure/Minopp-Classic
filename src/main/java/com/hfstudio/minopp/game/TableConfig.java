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

public class TableConfig {

    public final Map<String, Integer> rules = new HashMap<>();
    public final Set<String> packs = new LinkedHashSet<>();
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

    private static final String[] NONE = {};
    // cards that need a rule to play well: heavy draws would otherwise let hands grow without end
    private static final Map<String, String[]> CARD_RULES = Map.ofEntries(
        Map.entry("wild_draw6", new String[] { GameRules.MERCY }),
        Map.entry("wild_draw10", new String[] { GameRules.MERCY }),
        Map.entry("wild_color_roulette", new String[] { GameRules.MERCY }),
        Map.entry("wild_plus6", new String[] { GameRules.MERCY }),
        Map.entry("wild_reverse_plus6", new String[] { GameRules.MERCY }),
        Map.entry("power_number", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_skip", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_reverse", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_draw2", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_wild_all_draw", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_wild_target_draw2", new String[] { GameRules.FLEX_POWER }),
        Map.entry("flex_wild_draw4", new String[] { GameRules.FLEX_POWER }));
    private static final Map<String, String[]> RULE_REQUIRES = Map.of(
        GameRules.STACK_ANY_DRAW,
        new String[] { GameRules.STACKING },
        GameRules.STACK_DRAW4_ON_DRAW2,
        new String[] { GameRules.STACKING },
        GameRules.TEAM_RELAY,
        new String[] { GameRules.TEAM_MODE });

    public static String[] rulesForCard(String typeId) {
        return CARD_RULES.getOrDefault(typeId, NONE);
    }

    public static String[] requiredRules(String rule) {
        return RULE_REQUIRES.getOrDefault(rule, NONE);
    }

    public void set(String rule, int value) {
        GameRules.Rule def = GameRules.get(rule);
        if (def == null) return;
        int v = def.clamp(value);
        rules.put(rule, v);
        if (!def.bool()) return;
        if (v != 0) {
            for (String req : requiredRules(rule)) if (getInt(req) == 0) set(req, 1);
        } else {
            for (Map.Entry<String, String[]> e : RULE_REQUIRES.entrySet()) {
                if (java.util.Arrays.asList(e.getValue())
                    .contains(rule) && getInt(e.getKey()) != 0) set(e.getKey(), 0);
            }
        }
    }

    private void enableRulesFor(CardPack.Entry entry) {
        for (String rule : rulesForCard(entry.typeId())) if (getInt(rule) == 0) set(rule, 1);
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
            for (CardPack.Entry entry : pack.entries) if (getCount(pack, entry) > 0) enableRulesFor(entry);
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
        if (key == null) return;
        int colon = key.indexOf(':');
        if (colon > 0) key = CardPack.alias(key.substring(0, colon)) + key.substring(colon);
        if (!CardPack.isEntryKey(key)) return;
        if (count < 0) {
            counts.remove(key);
        } else {
            counts.put(key, Math.min(count, 64));
        }
        CardPack pack = CardPack.get(key.substring(0, key.indexOf(':')));
        if (pack == null) return;
        for (CardPack.Entry entry : pack.entries) {
            if (countKey(pack, entry).equals(key) && getCount(pack, entry) > 0) enableRulesFor(entry);
        }
    }

    public void resetToPreset(String presetPack) {
        rules.clear();
        packs.clear();
        counts.clear();
        presetPack = CardPack.alias(presetPack);
        CardPack pack = CardPack.get(presetPack);
        if (pack != null && pack.isAddon()) setPackEnabled(CardPack.CLASSIC, true);
        setPackEnabled(presetPack, true);
    }

    public List<CardFace> buildFaces(boolean backSide) {
        Map<CardFace, Integer> defaults = new java.util.LinkedHashMap<>();
        Map<CardFace, Integer> overridden = new java.util.LinkedHashMap<>();
        for (CardPack pack : CardPack.all()) {
            if (pack.backSideOnly != backSide) continue;
            for (CardPack.Entry entry : pack.entries) {
                boolean override = hasOverride(pack, entry);
                int count = getCount(pack, entry);
                if (count <= 0 && !override) continue;
                Map<CardFace, Integer> target = override ? overridden : defaults;
                for (CardFace face : entryFaces(entry)) target.merge(face, count, Math::max);
            }
        }
        Map<CardFace, Integer> merged = new java.util.LinkedHashMap<>(defaults);
        merged.putAll(overridden);
        List<CardFace> faces = new ArrayList<>();
        for (Map.Entry<CardFace, Integer> e : merged.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) faces.add(e.getKey());
        }
        return faces;
    }

    public static List<CardFace> entryFaces(CardPack.Entry entry) {
        List<CardFace> faces = new ArrayList<>();
        CardType type = CardTypes.get(entry.typeId());
        int[] numbers = entry.numbers().length == 0 ? new int[] { -1 } : entry.numbers();
        for (int number : numbers) {
            if (type.wild) {
                faces.add(new CardFace(type.id, CardColor.WILD, number));
            } else {
                for (CardColor color : CardColor.forSide(entry.dark())) {
                    faces.add(new CardFace(type.id, color, number, entry.flexColor() ? color.flexPartner() : null));
                }
            }
        }
        return faces;
    }

    public int countDeckSize() {
        return buildFaces(false).size();
    }

    public int effectiveCopies(int playerCount, int deckSize) {
        int copies = getInt(GameRules.DECK_COPIES);
        if (copies > 0) return copies;
        int byPlayers = (int) Math.ceil(Math.max(4, playerCount) / 4.0);
        int needed = playerCount * getInt(GameRules.INITIAL_HAND) * 2 + 40;
        int byCards = (int) Math.ceil(needed / (double) Math.max(1, deckSize));
        return Math.max(1, Math.max(byPlayers, byCards));
    }

    public TableConfig copy() {
        TableConfig c = new TableConfig();
        c.fromTag(toTag());
        return c;
    }

    public boolean sameAs(TableConfig other) {
        if (!packs.equals(other.packs) || !counts.equals(other.counts)) return false;
        for (GameRules.Rule r : GameRules.all()) if (getInt(r.id()) != other.getInt(r.id())) return false;
        return true;
    }

    public List<String> enabledPackIds() {
        return new ArrayList<>(packs);
    }

    public List<Card> buildDeck(int playerCount, Random random) {
        List<CardFace> lightFaces = buildFaces(false);
        if (lightFaces.isEmpty()) lightFaces = new TableConfig().buildFaces(false);
        int copies = effectiveCopies(playerCount, lightFaces.size());
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
        for (String k : rulesTag.func_150296_c()) {
            String key = k;
            GameRules.Rule def = GameRules.get(key);
            if (def == null) continue;
            // Rules of the original mod were stored as booleans
            int value = rulesTag.func_150299_b(key) == 1 ? (rulesTag.getBoolean(key) ? 1 : 0)
                : rulesTag.getInteger(key);
            // the points target used to be stored in hundreds
            if (key.equals(GameRules.SCORE_TARGET) && value > 0 && value <= 20) value *= 100;
            rules.put(key, def.clamp(value));
        }
        if (tag.hasKey("packs")) {
            NBTTagList packsTag = tag.getTagList("packs", 8);
            for (int i = 0; i < packsTag.tagCount(); i++) {
                CardPack pack = CardPack.get(CardPack.alias(packsTag.getStringTagAt(i)));
                if (pack != null && !pack.backSideOnly) packs.add(pack.id);
            }
        } else {
            packs.add(CardPack.CLASSIC);
        }
        NBTTagCompound countsTag = tag.getCompoundTag("counts");
        for (String k : countsTag.func_150296_c()) {
            if (counts.size() >= 512) break;
            setCount(k, countsTag.getInteger(k));
        }
    }
}
