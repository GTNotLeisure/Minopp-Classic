package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A card pack: a named set of deck entries (card type + how many copies) plus the rules the original game mode turns
 * on. Packs can be enabled whole, or individual entries can be mixed into any game with custom counts.
 */
public final class CardPack {

    public static final String CLASSIC = "classic";
    public static final String NO_MERCY = "no_mercy";
    public static final String FLIP = "flip";
    public static final String FLIP_DARK = "flip_dark";
    public static final String FLEX = "flex";
    public static final String ALL_WILD = "all_wild";
    public static final String PARTY = "party";
    public static final String HERO = "hero";
    public static final String RHYTHM = "rhythm";
    public static final String MAYHEM = "mayhem";
    public static final String LEGEND = "legend";
    public static final String ANNIVERSARY = "anniversary";
    public static final String WEEKEND = "weekend";

    /**
     * One line of a deck composition.
     *
     * @param key       unique key within the pack, used for count overrides ("pack:key")
     * @param typeId    card type
     * @param numbers   printed numbers for number cards (one entry per number), empty otherwise
     * @param count     copies per color for colored cards, total copies for wild cards
     * @param dark      whether colored copies use the dark (Flip) colors
     * @param flexColor whether colored copies carry a Flex partner color
     */
    public record Entry(String key, String typeId, int[] numbers, int count, boolean dark, boolean flexColor) {

        public boolean isWild() {
            return CardTypes.get(typeId).wild;
        }

        /** Number of physical cards this entry contributes with the given count. */
        public int totalCards(int count) {
            int perVariant = isWild() ? count : count * 4;
            return perVariant * Math.max(1, numbers.length);
        }
    }

    public final String id;
    /** Packs that are a "side" of another game (Flip dark side) are only used for back faces. */
    public final boolean backSideOnly;
    public final List<Entry> entries;
    /** Rules switched on by the "enable whole pack" preset. */
    public final Map<String, Integer> presetRules;

    private CardPack(String id, boolean backSideOnly, List<Entry> entries, Map<String, Integer> presetRules) {
        this.id = id;
        this.backSideOnly = backSideOnly;
        this.entries = Collections.unmodifiableList(entries);
        this.presetRules = Collections.unmodifiableMap(presetRules);
    }

    public String langKey() {
        return "gui.minopp.pack." + id;
    }

    public Entry entry(String key) {
        for (Entry e : entries) if (e.key.equals(key)) return e;
        return null;
    }

    // ------------------------------------------------------------------ registry

    private static final Map<String, CardPack> REGISTRY = new LinkedHashMap<>();

    public static CardPack get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<CardPack> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static CardPack register(CardPack pack) {
        REGISTRY.put(pack.id, pack);
        return pack;
    }

    private static final int[] ZERO = { 0 };
    private static final int[] ONE_TO_NINE = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int[] ZERO_TO_NINE = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int[] NONE = {};

    private static final class Builder {

        private final String id;
        private boolean backSideOnly;
        private final List<Entry> entries = new ArrayList<>();
        private final Map<String, Integer> rules = new LinkedHashMap<>();

        Builder(String id) {
            this.id = id;
        }

        Builder numbers(String key, int[] numbers, int count) {
            entries.add(new Entry(key, "number", numbers, count, false, false));
            return this;
        }

        Builder entry(String typeId, int count) {
            entries.add(new Entry(typeId, typeId, NONE, count, false, false));
            return this;
        }

        Builder entry(String key, String typeId, int[] numbers, int count, boolean dark, boolean flex) {
            entries.add(new Entry(key, typeId, numbers, count, dark, flex));
            return this;
        }

        Builder rule(String rule, int value) {
            rules.put(rule, value);
            return this;
        }

        Builder backSideOnly() {
            backSideOnly = true;
            return this;
        }

        CardPack build() {
            return register(new CardPack(id, backSideOnly, entries, rules));
        }
    }

    // 108 cards: 0 x1, 1-9 x2, Skip/Reverse/+2 x2 per color, Wild x4, Wild +4 x4
    public static final CardPack PACK_CLASSIC = new Builder(CLASSIC).numbers("zero", ZERO, 1)
        .numbers("numbers", ONE_TO_NINE, 2)
        .entry("skip", 2)
        .entry("reverse", 2)
        .entry("draw2", 2)
        .entry("wild", 4)
        .entry("wild_draw4", 4)
        .build();

    // 168 cards. Show 'Em No Mercy style deck; counts are adjustable per table.
    public static final CardPack PACK_NO_MERCY = new Builder(NO_MERCY).numbers("numbers", ZERO_TO_NINE, 2)
        .entry("skip", 3)
        .entry("reverse", 4)
        .entry("draw2", 2)
        .entry("draw4", 2)
        .entry("discard_all", 3)
        .entry("skip_everyone", 2)
        .entry("wild_reverse_draw4", 8)
        .entry("wild_draw6", 4)
        .entry("wild_draw10", 4)
        .entry("wild_color_roulette", 8)
        .rule(GameRules.MERCY, 1)
        .rule(GameRules.STACKING, 1)
        .rule(GameRules.STACK_ANY_DRAW, 1)
        .rule(GameRules.SEVEN_ZERO, 1)
        .rule(GameRules.DRAW_TO_MATCH, 1)
        .build();

    // Light side of the double-sided deck (112 cards)
    public static final CardPack PACK_FLIP = new Builder(FLIP).numbers("numbers", ONE_TO_NINE, 2)
        .entry("draw1", 2)
        .entry("reverse", 2)
        .entry("skip", 2)
        .entry("flip", 2)
        .entry("wild", 4)
        .entry("wild_draw2", 4)
        .rule(GameRules.FLIP_MODE, 1)
        .build();

    // Dark side faces, paired with every physical card while Flip mode is on
    public static final CardPack PACK_FLIP_DARK = new Builder(FLIP_DARK)
        .entry("numbers", "number", ONE_TO_NINE, 2, true, false)
        .entry("draw5", "draw5", NONE, 2, true, false)
        .entry("reverse", "reverse", NONE, 2, true, false)
        .entry("skip_everyone", "skip_everyone", NONE, 2, true, false)
        .entry("flip", "flip", NONE, 2, true, false)
        .entry("wild", 4)
        .entry("wild_draw_color", 4)
        .backSideOnly()
        .build();

    // Two-colored Flex cards and Power cards (112 cards)
    public static final CardPack PACK_FLEX = new Builder(FLEX).numbers("zero", ZERO, 1)
        .numbers("numbers", new int[] { 1, 2, 3, 4, 6, 7, 8 }, 2)
        .entry("power_numbers", "power_number", new int[] { 5, 9 }, 2, false, false)
        .entry("skip", 1)
        .entry("reverse", 1)
        .entry("draw2", 1)
        .entry("flex_skip", "flex_skip", NONE, 1, false, true)
        .entry("flex_reverse", "flex_reverse", NONE, 1, false, true)
        .entry("flex_draw2", "flex_draw2", NONE, 1, false, true)
        .entry("wild_all_flip", 4)
        .entry("flex_wild_all_draw", 2)
        .entry("flex_wild_target_draw2", 2)
        .entry("flex_wild_draw4", 4)
        .rule(GameRules.FLEX_POWER, 1)
        .build();

    // 112 cards, every card is wild
    public static final CardPack PACK_ALL_WILD = new Builder(ALL_WILD).entry("aw_wild", 26)
        .entry("aw_reverse", 14)
        .entry("aw_skip", 14)
        .entry("aw_skip_two", 8)
        .entry("aw_draw2", 14)
        .entry("aw_draw4", 8)
        .entry("aw_targeted_draw2", 14)
        .entry("aw_forced_swap", 14)
        .build();

    // Large group game: classic deck plus party cards
    public static final CardPack PACK_PARTY = new Builder(PARTY).entry("point_taken", 1)
        .entry("wild_drawn_together", 4)
        .entry("wild_pile_up", 4)
        .rule(GameRules.JUMP_IN, 1)
        .rule(GameRules.MAX_PLAYERS, 16)
        .build();

    public static final CardPack PACK_HERO = new Builder(HERO).entry("wild_rebound", 4)
        .build();
    public static final CardPack PACK_RHYTHM = new Builder(RHYTHM).entry("wild_dance_floor", 4)
        .build();
    public static final CardPack PACK_MAYHEM = new Builder(MAYHEM).entry("wild_hand_shuffle", 4)
        .build();
    public static final CardPack PACK_LEGEND = new Builder(LEGEND).entry("wild_trial", 4)
        .build();
    public static final CardPack PACK_ANNIVERSARY = new Builder(ANNIVERSARY).entry("wild_fifty_fifty", 4)
        .build();
    public static final CardPack PACK_WEEKEND = new Builder(WEEKEND).entry("wild_plus6", 4)
        .entry("wild_reverse_plus6", 4)
        .entry("double_discard_all", 1)
        .entry("wild_time_balloon", 2)
        .build();

    public static void init() {
        CardTypes.init();
    }
}
