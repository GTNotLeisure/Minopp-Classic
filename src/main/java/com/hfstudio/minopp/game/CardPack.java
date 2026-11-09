package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public static final String BLASTER = "blaster";
    public static final String MODERN = "modern";
    public static final String WHIRLWIND = "whirlwind";
    public static final String JUNGLE = "jungle";
    public static final String DANCE = "dance";
    public static final String RASCAL = "rascal";
    public static final String MYTH = "myth";
    public static final String TEAMS = "teams";
    public static final String STACK = "stack";
    public static final String SWAP = "swap";
    public static final String REVERSE_PACK = "reverse_pack";
    public static final String SPEED = "speed";
    public static final String FRENEMIES = "frenemies";
    public static final String DEFENSE = "defense";
    public static final String COMEBACK = "comeback";
    public static final String LOVE_ISLAND = "love_island";
    public static final String BILLIE = "billie";
    public static final String SHOW_EM = "show_em";

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

        public int totalCards(int count) {
            int perVariant = isWild() ? count : count * 4;
            return perVariant * Math.max(1, numbers.length);
        }
    }

    public final String id;
    public final boolean backSideOnly;
    public final List<Entry> entries;
    public final Map<String, Integer> presetRules;
    public final int minPlayers, maxPlayers;

    private CardPack(String id, boolean backSideOnly, List<Entry> entries, Map<String, Integer> presetRules,
        int minPlayers, int maxPlayers) {
        this.id = id;
        this.backSideOnly = backSideOnly;
        this.entries = Collections.unmodifiableList(entries);
        this.presetRules = Collections.unmodifiableMap(presetRules);
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public boolean isAddon() {
        if (backSideOnly) return false;
        for (Entry e : entries) if (CardTypes.get(e.typeId).numbered) return false;
        return true;
    }

    public static String alias(String id) {
        return switch (id) {
            case HERO, MAYHEM -> JUNGLE;
            case RHYTHM -> DANCE;
            default -> id;
        };
    }

    public String langKey() {
        return "gui.minopp.pack." + id;
    }

    public Entry entry(String key) {
        for (Entry e : entries) if (e.key.equals(key)) return e;
        return null;
    }

    private static final Map<String, CardPack> REGISTRY = new LinkedHashMap<>();

    public static CardPack get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean isEntryKey(String countKey) {
        int colon = countKey.indexOf(':');
        if (colon < 0) return false;
        CardPack pack = get(countKey.substring(0, colon));
        if (pack == null) return false;
        String key = countKey.substring(colon + 1);
        for (Entry e : pack.entries) if (e.key()
            .equals(key)) return true;
        return false;
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
        private int minPlayers = 2, maxPlayers = 10;

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

        Builder players(int min, int max) {
            minPlayers = min;
            maxPlayers = max;
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
            return register(new CardPack(id, backSideOnly, entries, rules, minPlayers, maxPlayers));
        }
    }

    public static final CardPack PACK_CLASSIC = new Builder(CLASSIC).numbers("zero", ZERO, 1)
        .numbers("numbers", ONE_TO_NINE, 2)
        .entry("skip", 2)
        .entry("reverse", 2)
        .entry("draw2", 2)
        .entry("wild", 4)
        .entry("wild_draw4", 4)
        .players(2, 10)
        .build();

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
        .players(2, 6)
        .build();

    public static final CardPack PACK_FLIP = new Builder(FLIP).numbers("numbers", ONE_TO_NINE, 2)
        .entry("draw1", 2)
        .entry("reverse", 2)
        .entry("skip", 2)
        .entry("flip", 2)
        .entry("wild", 4)
        .entry("wild_draw2", 4)
        .rule(GameRules.FLIP_MODE, 1)
        .players(2, 10)
        .build();

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
        .players(2, 6)
        .build();

    public static final CardPack PACK_ALL_WILD = new Builder(ALL_WILD).entry("aw_wild", 26)
        .entry("aw_reverse", 14)
        .entry("aw_skip", 14)
        .entry("aw_skip_two", 8)
        .entry("aw_draw2", 14)
        .entry("aw_draw4", 8)
        .entry("aw_targeted_draw2", 14)
        .entry("aw_forced_swap", 14)
        .players(2, 10)
        .build();

    public static final CardPack PACK_PARTY = new Builder(PARTY).entry("point_taken", 1)
        .entry("wild_drawn_together", 3)
        .entry("wild_pile_up", 3)
        .rule(GameRules.JUMP_IN, 1)
        .rule(GameRules.MAX_PLAYERS, 16)
        .players(6, 16)
        .build();

    public static final CardPack PACK_LEGEND = new Builder(LEGEND).entry("wild_trial", 4)
        .players(2, 8)
        .build();
    public static final CardPack PACK_ANNIVERSARY = new Builder(ANNIVERSARY).entry("wild_fifty_fifty", 4)
        .players(2, 8)
        .build();
    public static final CardPack PACK_WEEKEND = new Builder(WEEKEND).entry("wild_plus6", 2)
        .entry("wild_reverse_plus6", 2)
        .entry("double_discard_all", 1)
        .entry("wild_time_balloon", 2)
        .players(2, 8)
        .build();
    public static final CardPack PACK_BLASTER = new Builder(BLASTER).entry("blaster", 2)
        .entry("trade_hands", 1)
        .entry("discard_all", 1)
        .entry("wild_blast_all", 4)
        .players(2, 8)
        .build();
    public static final CardPack PACK_MODERN = new Builder(MODERN).entry("gift", 1)
        .entry("bonus_turn", 1)
        .entry("wild_swap_hands", 2)
        .entry("wild_hand_shuffle", 1)
        .players(2, 10)
        .build();
    public static final CardPack PACK_JUNGLE = new Builder(JUNGLE).entry("wild_hand_shuffle", 2)
        .entry("wild_rebound", 2)
        .entry("wild_smoke", 1)
        .entry("wild_echo", 1)
        .players(2, 8)
        .build();
    public static final CardPack PACK_DANCE = new Builder(DANCE).entry("wild_crossfade", 2)
        .entry("wild_dance_floor", 2)
        .entry("wild_even_out", 1)
        .entry("wild_surprise", 1)
        .players(3, 10)
        .build();
    public static final CardPack PACK_RASCAL = new Builder(RASCAL).entry("wild_card_storm", 1)
        .entry("wild_booby_trap", 2)
        .entry("wild_hurry_up", 1)
        .entry("wild_shield", 2)
        .players(2, 8)
        .build();
    public static final CardPack PACK_MYTH = new Builder(MYTH).entry("wild_trickster", 3)
        .entry("wild_surge", 3)
        .players(2, 8)
        .build();
    public static final CardPack PACK_TEAMS = new Builder(TEAMS).entry("wild_team_play", 4)
        .entry("wild_team_draw4", 2)
        .rule(GameRules.TEAM_MODE, 1)
        .rule(GameRules.TEAM_RELAY, 1)
        .players(4, 20)
        .build();
    public static final CardPack PACK_WHIRLWIND = new Builder(WHIRLWIND).entry("wild_cyclone", 2)
        .entry("wild_thief", 2)
        .players(3, 8)
        .build();
    public static final CardPack PACK_STACK = new Builder(STACK).entry("stack1", 1)
        .entry("stack2", 1)
        .entry("wild_stack3", 3)
        .entry("wild_stack_number", 3)
        .rule(GameRules.STACKING, 1)
        .players(2, 10)
        .build();
    public static final CardPack PACK_SWAP = new Builder(SWAP).entry("swap_one", 1)
        .entry("refresh_hand", 1)
        .entry("wild_force_trade", 3)
        .entry("wild_pass_hands", 3)
        .players(3, 10)
        .build();
    public static final CardPack PACK_REVERSE = new Builder(REVERSE_PACK).entry("reverse_skip", 1)
        .entry("reverse_draw2", 1)
        .entry("wild_power_reverse", 3)
        .entry("wild_no_u", 3)
        .players(3, 10)
        .build();
    public static final CardPack PACK_SPEED = new Builder(SPEED).entry("speed_up", 3)
        .entry("wild_double_speed", 4)
        .players(2, 8)
        .build();
    public static final CardPack PACK_FRENEMIES = new Builder(FRENEMIES).entry("wild_petty_play", 2)
        .entry("wild_eliminator", 1)
        .entry("wild_instigator", 2)
        .entry("wild_nemesis", 1)
        .players(3, 10)
        .build();
    public static final CardPack PACK_DEFENSE = new Builder(DEFENSE).entry("wild_block", 2)
        .entry("wild_block_rebound", 2)
        .entry("wild_block_deflect", 1)
        .entry("wild_redirect", 1)
        .players(2, 10)
        .build();
    public static final CardPack PACK_COMEBACK = new Builder(COMEBACK).entry("wild_swap_hands", 2)
        .entry("wild_match_max", 2)
        .entry("wild_misery", 2)
        .players(2, 10)
        .build();
    public static final CardPack PACK_LOVE_ISLAND = new Builder(LOVE_ISLAND).entry("wild_eggs_basket", 2)
        .entry("wild_pull_chat", 2)
        .entry("wild_got_text", 2)
        .players(3, 10)
        .build();
    public static final CardPack PACK_BILLIE = new Builder(BILLIE).entry("wild_pass_color", 2)
        .entry("trick_wild", 2)
        .entry("wild_color_challenge", 2)
        .entry("reveal_hands", 1)
        .players(2, 10)
        .build();
    public static final CardPack PACK_SHOW_EM = new Builder(SHOW_EM).entry("play_again_10", 2)
        .entry("wild_discard_all", 2)
        .entry("wild_reverse_draw8", 2)
        .entry("wild_final_attack", 1)
        .entry("wild_sudden_death", 1)
        .rule(GameRules.COINS, 1)
        .players(2, 6)
        .build();

    public static void init() {
        CardTypes.init();
    }
}
