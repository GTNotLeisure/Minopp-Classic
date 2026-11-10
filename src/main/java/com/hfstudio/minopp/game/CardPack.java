package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CardPack {

    public static final String CLASSIC = "classic";
    public static final String COMBAT = "combat";
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
    public static final String INFINITY = "infinity";

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

        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(typeId, "typeId");
            if (key.isBlank() || key.indexOf(':') >= 0) throw new IllegalArgumentException("Invalid entry key: " + key);
            if (count < 0 || count > 64) throw new IllegalArgumentException("Entry count must be between 0 and 64");
            numbers = numbers.clone();
        }

        @Override
        public int[] numbers() {
            return numbers.clone();
        }

        public boolean isWild() {
            return CardTypes.get(typeId).wild;
        }

        /**
         * Printed once per number instead of once per color: wild cards and one-color cards.
         */
        public boolean single() {
            return isWild() || CardTypes.get(typeId).fixedColor != null;
        }

        public int totalCards(int count) {
            return totalCards(count, false);
        }

        public int totalCards(int count, boolean expandedColors) {
            int perVariant = single() ? count
                : count * CardColor.forSide(dark, expandedColors)
                    .size();
            return perVariant * Math.max(1, numbers.length);
        }
    }

    public final String id;
    public final boolean backSideOnly;
    public final List<Entry> entries;
    public final Map<String, Integer> presetRules;
    public final int minPlayers, maxPlayers;
    public final String basePackId;
    public final String backSideOf;
    private final Map<String, EntryData> entryIndex;

    private static class EntryData {

        private final Entry entry;
        private final String countKey;
        private volatile List<CardFace> faces;
        private volatile List<CardFace> expandedFaces;

        private EntryData(Entry entry, String countKey) {
            this.entry = entry;
            this.countKey = countKey;
        }

        private List<CardFace> faces(boolean expandedColors) {
            List<CardFace> cached = expandedColors ? expandedFaces : faces;
            if (cached != null) return cached;
            if (!CardTypes.exists(entry.typeId))
                throw new IllegalArgumentException("Unknown card type: " + entry.typeId);
            cached = List.copyOf(TableConfig.entryFaces(entry, expandedColors));
            if (expandedColors) expandedFaces = cached;
            else faces = cached;
            return cached;
        }
    }

    private CardPack(String id, boolean backSideOnly, List<Entry> entries, Map<String, Integer> presetRules,
        int minPlayers, int maxPlayers, String basePackId, String backSideOf) {
        this.id = id;
        this.backSideOnly = backSideOnly;
        this.entries = List.copyOf(entries);
        this.presetRules = Collections.unmodifiableMap(new LinkedHashMap<>(presetRules));
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.basePackId = basePackId;
        this.backSideOf = backSideOf;
        Map<String, EntryData> index = new HashMap<>();
        for (Entry entry : this.entries) index.put(entry.key, new EntryData(entry, id + ":" + entry.key));
        this.entryIndex = Collections.unmodifiableMap(index);
    }

    public boolean isAddon() {
        return basePackId != null;
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
        EntryData data = entryIndex.get(key);
        return data == null ? null : data.entry;
    }

    public String countKey(String entryKey) {
        EntryData data = entryIndex.get(entryKey);
        return data == null ? id + ":" + entryKey : data.countKey;
    }

    /**
     * Returns immutable face templates in entry order, shared safely by the client and game threads.
     */
    public List<CardFace> faces(String entryKey) {
        return faces(entryKey, false);
    }

    public List<CardFace> faces(String entryKey, boolean expandedColors) {
        EntryData data = entryIndex.get(entryKey);
        if (data == null) throw new IllegalArgumentException("Unknown entry: " + countKey(entryKey));
        return data.faces(expandedColors);
    }

    private static final Map<String, CardPack> REGISTRY = new LinkedHashMap<>();
    private static final Collection<CardPack> ALL = Collections.unmodifiableCollection(REGISTRY.values());
    private static final Map<String, List<CardPack>> BACKS_BY_FRONT = new HashMap<>();

    /**
     * Returns the immutable, registration-ordered snapshot of back packs associated with a front pack.
     */
    public static List<CardPack> backPacks(String frontPackId) {
        return BACKS_BY_FRONT.getOrDefault(frontPackId, List.of());
    }

    public static CardPack get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean isEntryKey(String countKey) {
        int colon = countKey.lastIndexOf(':');
        if (colon < 0) return false;
        CardPack pack = get(countKey.substring(0, colon));
        if (pack == null) return false;
        return pack.entry(countKey.substring(colon + 1)) != null;
    }

    public static Collection<CardPack> all() {
        return ALL;
    }

    private static long revision;

    public static long revision() {
        return revision;
    }

    public static CardPack register(CardPack pack) {
        Objects.requireNonNull(pack, "pack");
        requireFrontPack(pack.basePackId);
        requireFrontPack(pack.backSideOf);
        for (Entry entry : pack.entries) {
            if (!CardTypes.exists(entry.typeId))
                throw new IllegalArgumentException("Unknown card type: " + entry.typeId);
        }
        for (String rule : pack.presetRules.keySet()) {
            if (GameRules.get(rule) == null) throw new IllegalArgumentException("Unknown rule: " + rule);
        }
        if (REGISTRY.putIfAbsent(pack.id, pack) != null)
            throw new IllegalArgumentException("Card pack already registered: " + pack.id);
        if (pack.backSideOnly) {
            List<CardPack> backs = new ArrayList<>(backPacks(pack.backSideOf));
            backs.add(pack);
            BACKS_BY_FRONT.put(pack.backSideOf, List.copyOf(backs));
        }
        revision++;
        return pack;
    }

    private static void requireFrontPack(String id) {
        if (id == null) return;
        CardPack pack = get(id);
        if (pack == null || pack.backSideOnly)
            throw new IllegalArgumentException("Register the front/base pack first: " + id);
    }

    private static final int[] ZERO = { 0 };
    private static final int[] ONE_TO_NINE = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int[] ZERO_TO_NINE = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
    private static final int[] NONE = {};

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {

        private final String id;
        private boolean backSideOnly;
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> entryKeys = new HashSet<>();
        private final Map<String, Integer> rules = new LinkedHashMap<>();
        private int minPlayers = 2, maxPlayers = 10;
        private String basePackId, backSideOf;
        private boolean standalone;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
            if (id.isBlank() || id.endsWith(":")) throw new IllegalArgumentException("Invalid pack id: " + id);
        }

        public Builder numbers(String key, int[] numbers, int count) {
            return entry(key, "number", numbers, count, false, false);
        }

        public Builder entry(String typeId, int count) {
            return entry(typeId.substring(typeId.indexOf(':') + 1), typeId, NONE, count, false, false);
        }

        public Builder entry(String key, String typeId, int[] numbers, int count, boolean dark, boolean flex) {
            Entry entry = new Entry(key, typeId, numbers, count, dark, flex);
            if (!entryKeys.add(key)) throw new IllegalArgumentException("Duplicate entry key: " + key);
            entries.add(entry);
            return this;
        }

        public Builder players(int min, int max) {
            if (min < 2 || max < min || max > 20) throw new IllegalArgumentException("Invalid player range");
            minPlayers = min;
            maxPlayers = max;
            return this;
        }

        public Builder rule(String rule, int value) {
            rules.put(rule, value);
            return this;
        }

        public Builder backSideOnly() {
            return backSideOf(FLIP);
        }

        public Builder backSideOf(String packId) {
            backSideOnly = true;
            backSideOf = Objects.requireNonNull(packId, "packId");
            return this;
        }

        public Builder basePack(String packId) {
            basePackId = Objects.requireNonNull(packId, "packId");
            standalone = false;
            return this;
        }

        public Builder standalone() {
            basePackId = null;
            standalone = true;
            return this;
        }

        /**
         * Creates an independent pack without registering it.
         */
        public CardPack build() {
            String base = basePackId;
            if (backSideOnly || standalone) base = null;
            else if (base == null && entries.stream()
                .noneMatch(e -> e.typeId.equals("number"))) base = CLASSIC;
            if (id.equals(base) || id.equals(backSideOf))
                throw new IllegalArgumentException("A pack cannot depend on itself");
            return new CardPack(id, backSideOnly, entries, rules, minPlayers, maxPlayers, base, backSideOf);
        }

        public CardPack register() {
            return CardPack.register(build());
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
        .register();

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
        .register();

    public static final CardPack PACK_FLIP = new Builder(FLIP).numbers("numbers", ONE_TO_NINE, 2)
        .entry("draw1", 2)
        .entry("reverse", 2)
        .entry("skip", 2)
        .entry("flip", 2)
        .entry("wild", 4)
        .entry("wild_draw2", 4)
        .rule(GameRules.FLIP_MODE, 1)
        .players(2, 10)
        .register();

    public static final CardPack PACK_FLIP_DARK = new Builder(FLIP_DARK)
        .entry("numbers", "number", ONE_TO_NINE, 2, true, false)
        .entry("draw5", "draw5", NONE, 2, true, false)
        .entry("reverse", "reverse", NONE, 2, true, false)
        .entry("skip_everyone", "skip_everyone", NONE, 2, true, false)
        .entry("flip", "flip", NONE, 2, true, false)
        .entry("wild", 4)
        .entry("wild_draw_color", 4)
        .backSideOnly()
        .register();

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
        .register();

    public static final CardPack PACK_ALL_WILD = new Builder(ALL_WILD).entry("aw_wild", 26)
        .entry("aw_reverse", 14)
        .entry("aw_skip", 14)
        .entry("aw_skip_two", 8)
        .entry("aw_draw2", 14)
        .entry("aw_draw4", 8)
        .entry("aw_targeted_draw2", 14)
        .entry("aw_forced_swap", 14)
        .players(2, 10)
        .register();

    public static final CardPack PACK_PARTY = new Builder(PARTY).entry("point_taken", 1)
        .entry("wild_drawn_together", 3)
        .entry("wild_pile_up", 3)
        .rule(GameRules.JUMP_IN, 1)
        .rule(GameRules.MAX_PLAYERS, 16)
        .players(6, 16)
        .register();

    public static final CardPack PACK_LEGEND = new Builder(LEGEND).entry("wild_trial", 4)
        .players(2, 8)
        .register();
    public static final CardPack PACK_ANNIVERSARY = new Builder(ANNIVERSARY).entry("wild_fifty_fifty", 4)
        .players(2, 8)
        .register();
    public static final CardPack PACK_WEEKEND = new Builder(WEEKEND).entry("wild_plus6", 2)
        .entry("wild_reverse_plus6", 2)
        .entry("double_discard_all", 1)
        .entry("wild_time_balloon", 2)
        .players(2, 8)
        .register();
    public static final CardPack PACK_BLASTER = new Builder(BLASTER).entry("blaster", 2)
        .entry("trade_hands", 1)
        .entry("discard_all", 1)
        .entry("wild_blast_all", 4)
        .players(2, 8)
        .register();
    public static final CardPack PACK_MODERN = new Builder(MODERN).entry("gift", 1)
        .entry("bonus_turn", 1)
        .entry("wild_swap_hands", 2)
        .entry("wild_hand_shuffle", 1)
        .players(2, 10)
        .register();
    public static final CardPack PACK_JUNGLE = new Builder(JUNGLE).entry("wild_hand_shuffle", 2)
        .entry("wild_rebound", 2)
        .entry("wild_smoke", 1)
        .entry("wild_echo", 1)
        .players(2, 8)
        .register();
    public static final CardPack PACK_DANCE = new Builder(DANCE).entry("wild_crossfade", 2)
        .entry("wild_dance_floor", 2)
        .entry("wild_even_out", 1)
        .entry("wild_surprise", 1)
        .players(3, 10)
        .register();
    public static final CardPack PACK_RASCAL = new Builder(RASCAL).entry("wild_card_storm", 1)
        .entry("wild_booby_trap", 2)
        .entry("wild_hurry_up", 1)
        .entry("wild_shield", 2)
        .players(2, 8)
        .register();
    public static final CardPack PACK_MYTH = new Builder(MYTH).entry("wild_trickster", 3)
        .entry("wild_surge", 3)
        .players(2, 8)
        .register();
    public static final CardPack PACK_TEAMS = new Builder(TEAMS).entry("wild_team_play", 4)
        .entry("wild_team_draw4", 2)
        .rule(GameRules.TEAM_MODE, 1)
        .rule(GameRules.TEAM_RELAY, 1)
        .players(4, 20)
        .register();
    public static final CardPack PACK_WHIRLWIND = new Builder(WHIRLWIND).entry("wild_cyclone", 2)
        .entry("wild_thief", 2)
        .players(3, 8)
        .register();
    public static final CardPack PACK_STACK = new Builder(STACK).entry("stack1", 1)
        .entry("stack2", 1)
        .entry("wild_stack3", 3)
        .entry("wild_stack_number", 3)
        .rule(GameRules.STACKING, 1)
        .players(2, 10)
        .register();
    public static final CardPack PACK_SWAP = new Builder(SWAP).entry("swap_one", 1)
        .entry("refresh_hand", 1)
        .entry("wild_force_trade", 3)
        .entry("wild_pass_hands", 3)
        .players(3, 10)
        .register();
    public static final CardPack PACK_REVERSE = new Builder(REVERSE_PACK).entry("reverse_skip", 1)
        .entry("reverse_draw2", 1)
        .entry("wild_power_reverse", 3)
        .entry("wild_no_u", 3)
        .players(3, 10)
        .register();
    public static final CardPack PACK_SPEED = new Builder(SPEED).entry("speed_up", 3)
        .entry("wild_double_speed", 4)
        .players(2, 8)
        .register();
    public static final CardPack PACK_FRENEMIES = new Builder(FRENEMIES).entry("wild_petty_play", 2)
        .entry("wild_eliminator", 1)
        .entry("wild_instigator", 2)
        .entry("wild_nemesis", 1)
        .players(3, 10)
        .register();
    public static final CardPack PACK_DEFENSE = new Builder(DEFENSE).entry("wild_block", 2)
        .entry("wild_block_rebound", 2)
        .entry("wild_block_deflect", 1)
        .entry("wild_redirect", 1)
        .players(2, 10)
        .register();
    public static final CardPack PACK_COMEBACK = new Builder(COMEBACK).entry("wild_swap_hands", 2)
        .entry("wild_match_max", 2)
        .entry("wild_misery", 2)
        .players(2, 10)
        .register();
    public static final CardPack PACK_LOVE_ISLAND = new Builder(LOVE_ISLAND).entry("wild_eggs_basket", 2)
        .entry("wild_pull_chat", 2)
        .entry("wild_got_text", 2)
        .players(3, 10)
        .register();
    public static final CardPack PACK_BILLIE = new Builder(BILLIE).entry("wild_pass_color", 2)
        .entry("trick_wild", 2)
        .entry("wild_color_challenge", 2)
        .entry("reveal_hands", 1)
        .players(2, 10)
        .register();
    public static final CardPack PACK_SHOW_EM = new Builder(SHOW_EM).basePack(NO_MERCY)
        .entry("play_again_10", 2)
        .entry("wild_discard_all", 2)
        .entry("wild_reverse_draw8", 2)
        .entry("wild_final_attack", 1)
        .entry("wild_sudden_death", 1)
        .rule(GameRules.COINS, 1)
        .players(2, 6)
        .register();

    public static final CardPack PACK_INFINITY = new Builder(INFINITY).entry("replay", 1)
        .rule(GameRules.EIGHT_COLORS, 1)
        .entry("draw4", 1)
        .entry("wild_draw2", 1)
        .entry("wild_draw4", 1)
        .entry("wild_draw6", 1)
        .entry("wild_draw10", 1)
        .entry("wild_infinity", 1)
        .entry("wild_swap_hands", 1)
        .entry("wild_shield", 1)
        .entry("wild_infinity_no_u", 1)
        .entry("wild_replay", 1)
        .entry("fast", "fast_number", ONE_TO_NINE, 2, false, false)
        .entry("colorless", "colorless_number", ZERO_TO_NINE, 1, false, false)
        .entry("draw1", 1)
        .entry("minus_one", 1)
        .entry("number_sign", 1)
        .entry("wild_skip", 1)
        .entry("wild_reverse", 1)
        .entry("wild_draw8", 1)
        .entry("wild_times_two", 1)
        .entry("wild_target_draw1", 1)
        .entry("wild_target_draw2", 1)
        .entry("wild_card_swap", 1)
        .entry("wild_target_draw4", 1)
        .entry("wild_all_draw2", 1)
        .entry("wild_all_draw4", 1)
        .entry("wild_d4", 1)
        .entry("wild_d6", 1)
        .entry("wild_d8", 1)
        .entry("wild_d12", 1)
        .entry("wild_d20", 1)
        .entry("wild_halve", 1)
        .entry("wild_minus_one", 1)
        .entry("wild_minus_two", 1)
        .entry("wild_reveal_one", 1)
        .entry("joker", 1)
        .entry("wild_number_sign", 1)
        .entry("wild_recycle", 1)
        .entry("wild_lucky_seven", 1)
        .rule(GameRules.STACKING, 1)
        .rule(GameRules.MERCY, 1)
        .players(2, 10)
        .register();

    public static final CardPack PACK_COMBAT = new Builder(COMBAT).entry("wild_combat", 1)
        .players(2, 10)
        .register();

    public static void init() {
        CardTypes.init();
        CardDecisions.init();
        TargetedEffects.init();
    }
}
