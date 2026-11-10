package com.hfstudio.minopp.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

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
    private static final Map<String, String[]> CARD_RULES = new LinkedHashMap<>(
        Map.ofEntries(
            Map.entry("minus_one", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_minus_one", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_minus_two", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_halve", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_recycle", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_lucky_seven", new String[] { GameRules.DISCARD_PILE }),
            Map.entry("wild_infinity", new String[] { GameRules.MERCY }),
            Map.entry("wild_draw6", new String[] { GameRules.MERCY }),
            Map.entry("wild_draw10", new String[] { GameRules.MERCY }),
            Map.entry("wild_color_roulette", new String[] { GameRules.MERCY }),
            Map.entry("wild_plus6", new String[] { GameRules.MERCY }),
            Map.entry("wild_reverse_plus6", new String[] { GameRules.MERCY }),
            Map.entry("wild_reverse_draw8", new String[] { GameRules.MERCY }),
            Map.entry("wild_sudden_death", new String[] { GameRules.MERCY }),
            Map.entry("wild_final_attack", new String[] { GameRules.MERCY }),
            Map.entry("power_number", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_skip", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_reverse", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_draw2", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_wild_all_draw", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_wild_target_draw2", new String[] { GameRules.FLEX_POWER }),
            Map.entry("flex_wild_draw4", new String[] { GameRules.FLEX_POWER })));
    private static final Map<String, String[]> RULE_REQUIRES = new LinkedHashMap<>(
        Map.of(
            GameRules.STACK_ANY_DRAW,
            new String[] { GameRules.STACKING },
            GameRules.STACK_DRAW4_ON_DRAW2,
            new String[] { GameRules.STACKING },
            GameRules.TEAM_RELAY,
            new String[] { GameRules.TEAM_MODE }));
    private static Map<String, String[]> ruleDependents = buildDependents();

    private static long dependencyRevision;

    public static long dependencyRevision() {
        return dependencyRevision;
    }

    /**
     * Adds boolean rules enabled when this card is included in a deck.
     */
    public static void registerCardRules(String typeId, String... rules) {
        if (!CardTypes.exists(typeId)) throw new IllegalArgumentException("Unknown card type: " + typeId);
        addRequirements(CARD_RULES, typeId, rules);
    }

    /**
     * Adds boolean prerequisites. Enabling the dependent enables them; disabling one disables its dependents.
     */
    public static void registerRequiredRules(String rule, String... requirements) {
        requireBooleanRule(rule);
        for (String requirement : requirements) {
            if (rule.equals(requirement) || dependsOn(requirement, rule)) {
                throw new IllegalArgumentException("Cyclic rule dependency: " + rule + " -> " + requirement);
            }
        }
        if (addRequirements(RULE_REQUIRES, rule, requirements)) ruleDependents = buildDependents();
    }

    private static boolean dependsOn(String rule, String target) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.push(rule);
        while (!pending.isEmpty()) {
            String next = pending.pop();
            if (next.equals(target)) return true;
            if (!visited.add(next)) continue;
            for (String requirement : RULE_REQUIRES.getOrDefault(next, NONE)) pending.push(requirement);
        }
        return false;
    }

    private static Map<String, String[]> buildDependents() {
        Map<String, List<String>> grouped = new HashMap<>();
        for (Map.Entry<String, String[]> entry : RULE_REQUIRES.entrySet()) {
            for (String requirement : entry.getValue()) {
                grouped.computeIfAbsent(requirement, key -> new ArrayList<>())
                    .add(entry.getKey());
            }
        }
        Map<String, String[]> result = new HashMap<>();
        grouped.forEach((key, values) -> result.put(key, values.toArray(String[]::new)));
        return result;
    }

    private static void requireBooleanRule(String id) {
        GameRules.Rule rule = GameRules.get(id);
        if (rule == null || !rule.bool())
            throw new IllegalArgumentException("Expected a registered boolean rule: " + id);
    }

    private static boolean addRequirements(Map<String, String[]> registry, String id, String[] rules) {
        for (String rule : rules) requireBooleanRule(rule);
        String[] previous = registry.getOrDefault(id, NONE);
        Set<String> combined = new LinkedHashSet<>(Arrays.asList(previous));
        combined.addAll(Arrays.asList(rules));
        if (combined.size() == previous.length) return false;
        registry.put(id, combined.toArray(String[]::new));
        dependencyRevision++;
        return true;
    }

    public static String[] rulesForCard(String typeId) {
        return CARD_RULES.getOrDefault(typeId, NONE)
            .clone();
    }

    public static String[] requiredRules(String rule) {
        return RULE_REQUIRES.getOrDefault(rule, NONE)
            .clone();
    }

    public void set(String rule, int value) {
        GameRules.Rule def = GameRules.get(rule);
        if (def == null) return;
        int v = def.clamp(value);
        if (v == 0 && rule.equals(GameRules.DISCARD_PILE) && requiresDiscardPile()) v = 1;
        rules.put(rule, v);
        if (!def.bool()) return;
        Map<String, String[]> links = v == 0 ? ruleDependents : RULE_REQUIRES;
        String[] related = links.getOrDefault(rule, NONE);
        if (related.length == 0) return;
        ArrayDeque<String> pending = new ArrayDeque<>();
        pushReverse(pending, related);
        while (!pending.isEmpty()) {
            String next = pending.pop();
            if (getInt(next) == v) continue;
            rules.put(next, v);
            pushReverse(pending, links.getOrDefault(next, NONE));
        }
    }

    private boolean requiresDiscardPile() {
        for (CardPack pack : CardPack.all()) for (CardPack.Entry entry : pack.entries) {
            if (getCount(pack, entry) > 0 && Arrays.asList(rulesForCard(entry.typeId()))
                .contains(GameRules.DISCARD_PILE)) return true;
        }
        return false;
    }

    private static void pushReverse(ArrayDeque<String> pending, String[] values) {
        for (int i = values.length - 1; i >= 0; i--) pending.push(values[i]);
    }

    private void enableRulesFor(CardPack.Entry entry) {
        for (String rule : CARD_RULES.getOrDefault(entry.typeId(), NONE)) if (getInt(rule) == 0) set(rule, 1);
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
            for (CardPack back : CardPack.backPacks(packId)) {
                set(GameRules.FLIP_MODE, 1);
                for (CardPack.Entry entry : back.entries) if (getCount(back, entry) > 0) enableRulesFor(entry);
            }
        } else {
            packs.remove(packId);
            if (packId.equals(CardPack.FLIP)) set(GameRules.FLIP_MODE, 0);
            if (packId.equals(CardPack.FLEX)) set(GameRules.FLEX_POWER, 0);
        }
    }

    public static String countKey(CardPack pack, CardPack.Entry entry) {
        return pack.countKey(entry.key());
    }

    public int getCount(CardPack pack, CardPack.Entry entry) {
        Integer override = counts.get(countKey(pack, entry));
        if (override != null) return override;
        boolean enabled = pack.backSideOnly
            ? isPackEnabled(pack.backSideOf) || CardPack.FLIP.equals(pack.backSideOf) && defaultFlipBackEnabled()
            : isPackEnabled(pack.id);
        return enabled ? entry.count() : 0;
    }

    private boolean defaultFlipBackEnabled() {
        if (!getBool(GameRules.FLIP_MODE)) return false;
        for (String pack : packs) {
            if (!CardPack.FLIP.equals(pack) && !CardPack.backPacks(pack)
                .isEmpty()) return false;
        }
        return true;
    }

    public boolean hasOverride(CardPack pack, CardPack.Entry entry) {
        return counts.containsKey(countKey(pack, entry));
    }

    public void setCount(String key, int count) {
        setCount(key, count, true);
    }

    private void setCount(String key, int count, boolean updateRules) {
        if (key == null) return;
        int colon = key.lastIndexOf(':');
        if (colon <= 0) return;
        CardPack pack = CardPack.get(CardPack.alias(key.substring(0, colon)));
        if (pack == null) return;
        CardPack.Entry entry = pack.entry(key.substring(colon + 1));
        if (entry == null) return;
        key = countKey(pack, entry);
        if (count < 0) {
            counts.remove(key);
        } else {
            counts.put(key, Math.min(count, 64));
        }
        if (updateRules && getCount(pack, entry) > 0) {
            enableRulesFor(entry);
            if (pack.backSideOnly && !CardPack.FLIP.equals(pack.backSideOf)) set(GameRules.FLIP_MODE, 1);
        }
    }

    public void resetToPreset(String presetPack) {
        rules.clear();
        packs.clear();
        counts.clear();
        presetPack = CardPack.alias(presetPack);
        CardPack pack = CardPack.get(presetPack);
        enableBasePacks(pack, new LinkedHashSet<>());
        setPackEnabled(presetPack, true);
    }

    private void enableBasePacks(CardPack pack, Set<String> visited) {
        if (pack == null || !pack.isAddon()) return;
        if (!visited.add(pack.id)) throw new IllegalStateException("Cyclic base pack dependency: " + pack.id);
        CardPack base = CardPack.get(pack.basePackId);
        if (base == null) throw new IllegalStateException("Unknown base pack: " + pack.basePackId);
        enableBasePacks(base, visited);
        setPackEnabled(base.id, true);
    }

    public List<CardFace> buildFaces(boolean backSide) {
        List<CardFace> faces = new ArrayList<>();
        for (Map.Entry<CardFace, Integer> e : faceCounts(backSide).entrySet()) {
            for (int i = 0; i < e.getValue(); i++) faces.add(e.getKey());
        }
        return faces;
    }

    /**
     * Returns merged face counts in deck order, including explicit zero overrides.
     */
    public Map<CardFace, Integer> faceCounts(boolean backSide) {
        return mergedCounts(backSide);
    }

    private Object2IntLinkedOpenHashMap<CardFace> mergedCounts(boolean backSide) {
        boolean expandedColors = getBool(GameRules.EIGHT_COLORS);
        Object2IntLinkedOpenHashMap<CardFace> defaults = new Object2IntLinkedOpenHashMap<>();
        Object2IntLinkedOpenHashMap<CardFace> overridden = new Object2IntLinkedOpenHashMap<>();
        for (CardPack pack : CardPack.all()) {
            if (pack.backSideOnly != backSide) continue;
            for (CardPack.Entry entry : pack.entries) {
                boolean override = hasOverride(pack, entry);
                int count = getCount(pack, entry);
                if (count <= 0 && !override) continue;
                Object2IntMap<CardFace> target = override ? overridden : defaults;
                for (CardFace face : pack.faces(entry.key(), expandedColors)) target.mergeInt(face, count, Math::max);
            }
        }
        defaults.putAll(overridden);
        return defaults;
    }

    public static List<CardFace> entryFaces(CardPack.Entry entry) {
        return entryFaces(entry, false);
    }

    public static List<CardFace> entryFaces(CardPack.Entry entry, boolean expandedColors) {
        List<CardFace> faces = new ArrayList<>();
        CardType type = CardTypes.get(entry.typeId());
        int[] numbers = entry.numbers();
        if (numbers.length == 0) numbers = new int[] { -1 };
        for (int number : numbers) {
            if (type.wild) {
                faces.add(new CardFace(type.id, CardColor.WILD, number));
            } else if (type.fixedColor != null) {
                faces.add(new CardFace(type.id, type.fixedColor, number));
            } else {
                for (CardColor color : CardColor.forSide(entry.dark(), expandedColors)) {
                    faces.add(new CardFace(type.id, color, number, entry.flexColor() ? color.flexPartner() : null));
                }
            }
        }
        return faces;
    }

    public List<CardColor> colors(boolean darkSide) {
        return CardColor.forSide(darkSide, getBool(GameRules.EIGHT_COLORS));
    }

    public int countDeckSize() {
        int total = 0;
        for (int count : faceCounts(false).values()) total += Math.max(0, count);
        return total;
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

    /**
     * The faces the cards' other sides come from in a Flip game, never empty.
     */
    public List<CardFace> darkFaces() {
        List<CardFace> base = buildFaces(true);
        return base.isEmpty() ? buildDefaultDarkFaces() : base;
    }

    private List<CardFace> buildDefaultDarkFaces() {
        TableConfig def = new TableConfig();
        def.set(GameRules.FLIP_MODE, 1);
        def.set(GameRules.EIGHT_COLORS, getInt(GameRules.EIGHT_COLORS));
        return def.buildFaces(true);
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", 1);
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
            if (!tag.hasKey("version") && key.equals(GameRules.SCORE_TARGET) && value > 0 && value <= 20) value *= 100;
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
            setCount(k, countsTag.getInteger(k), false);
        }
        if (requiresDiscardPile()) set(GameRules.DISCARD_PILE, 1);
    }
}
