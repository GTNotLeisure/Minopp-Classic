package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class GameRules {

    private GameRules() {}

    public enum Category {
        BASIC,
        HOUSE,
        NO_MERCY,
        MODES,
        TABLE
    }

    public record Rule(String id, Category category, int defaultValue, int min, int max, boolean bool) {

        public Rule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            if (id.isBlank() || min > max
                || defaultValue < min
                || defaultValue > max
                || bool && (min != 0 || max != 1)) {
                throw new IllegalArgumentException("Invalid rule: " + id);
            }
        }

        public String langKey() {
            return "gui.minopp.rule." + id;
        }

        public int clamp(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            return clamped <= 0 ? clamped : Math.max(MINIMUM_ENABLED.getInt(this), clamped);
        }
    }

    private static final Map<String, Rule> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, List<String>> VALUE_LABELS = new LinkedHashMap<>();
    private static final Map<String, int[]> PRESETS = new LinkedHashMap<>();
    private static final Reference2IntMap<Rule> MINIMUM_ENABLED = new Reference2IntOpenHashMap<>();
    private static final List<String> BOOLEAN_IDS = new ArrayList<>();
    private static final List<String> BOOLEAN_ID_VIEW = Collections.unmodifiableList(BOOLEAN_IDS);
    private static volatile List<Rule> rules = List.of();
    private static long revision;

    public static Rule register(Rule rule) {
        Objects.requireNonNull(rule, "rule");
        if (REGISTRY.putIfAbsent(rule.id(), rule) != null)
            throw new IllegalArgumentException("Rule already registered: " + rule.id());
        if (rule.bool()) BOOLEAN_IDS.add(rule.id());
        rules = null;
        revision++;
        return rule;
    }

    public static long revision() {
        return revision;
    }

    public static String bool(String id, Category category, boolean def) {
        register(new Rule(id, category, def ? 1 : 0, 0, 1, true));
        return id;
    }

    public static String integer(String id, Category category, int def, int min, int max) {
        register(new Rule(id, category, def, min, max, false));
        return id;
    }

    public static String choice(String id, Category category, int def, String... valueLangKeys) {
        List<String> labels = List.of(valueLangKeys);
        if (labels.isEmpty() || labels.stream()
            .anyMatch(String::isBlank)) throw new IllegalArgumentException("Choice labels cannot be empty");
        integer(id, category, def, 0, labels.size() - 1);
        VALUE_LABELS.put(id, labels);
        int[] values = new int[labels.size()];
        for (int i = 0; i < values.length; i++) values[i] = i;
        registerPresets(id, values);
        return id;
    }

    public static boolean isChoice(String id) {
        return VALUE_LABELS.containsKey(id);
    }

    public static String valueLangKey(String id, int value) {
        List<String> labels = VALUE_LABELS.get(id);
        return labels == null ? null : labels.get(get(id).clamp(value));
    }

    /**
     * Registers an integer rule whose zero value disables it; positive values have a separate minimum.
     */
    public static String zeroOrInteger(String id, Category category, int def, int minEnabled, int max) {
        if (minEnabled < 1 || minEnabled > max || def != 0 && def < minEnabled) {
            throw new IllegalArgumentException("Invalid enabled range: " + id);
        }
        Rule rule = register(new Rule(id, category, def, 0, max, false));
        MINIMUM_ENABLED.put(rule, minEnabled);
        return id;
    }

    // Rules of the original mod (rule ids kept for compatibility with existing tables)
    public static final String JUMP_IN = bool("allowJumpIn", Category.HOUSE, true);
    public static final String STACKING = bool("allowStacking", Category.HOUSE, true);
    public static final String SEVEN_ZERO = bool("allowSeven0", Category.HOUSE, false);

    public static final String DRAW_TO_MATCH = bool("drawToMatch", Category.HOUSE, false);
    public static final String FORCED_PLAY = bool("forcedPlay", Category.HOUSE, false);
    public static final String NO_BLUFFING = bool("noBluffing", Category.HOUSE, false);
    public static final String CHALLENGE = bool("challengeDraw4", Category.HOUSE, true);
    public static final String TEAM_MODE = bool("teamMode", Category.MODES, false);
    public static final String TEAM_COUNT = integer("teamCount", Category.MODES, 2, 2, Teams.MAX_TEAMS);
    public static final String TEAM_RELAY = bool("teamRelay", Category.MODES, false);
    public static final String PLAY_DRAWN = bool("playDrawnCard", Category.BASIC, true);
    public static final String DISCARD_PILE = bool("separateDiscardPile", Category.BASIC, false);
    public static final String OFFICIAL_START = bool("officialStart", Category.BASIC, true);
    public static final String STACK_DRAW4_ON_DRAW2 = bool("stackDraw4OnDraw2", Category.HOUSE, false);
    public static final String DEFLECT_DRAW = bool("deflectDraw", Category.HOUSE, false);
    public static final String MULTI_PLAY = bool("multiPlay", Category.HOUSE, false);
    public static final String BRING_CARD = bool("bringCard", Category.HOUSE, false);
    public static final String NUMBER_FINISH = bool("numberFinish", Category.HOUSE, false);

    public static final String MERCY = bool("mercyRule", Category.NO_MERCY, false);
    public static final String MERCY_LIMIT = integer("mercyLimit", Category.NO_MERCY, 25, 10, 60);
    public static final String STACK_ANY_DRAW = bool("stackAnyDraw", Category.NO_MERCY, false);
    public static final String COINS = bool("coins", Category.MODES, false);

    public static final String FLIP_MODE = bool("flipMode", Category.MODES, false);
    public static final String EIGHT_COLORS = bool("eightColors", Category.MODES, false);
    public static final String FLEX_POWER = bool("flexPower", Category.MODES, false);

    public static final String MAX_PLAYERS = integer("maxPlayers", Category.TABLE, 4, 2, 20);
    public static final String INITIAL_HAND = integer("initialHand", Category.TABLE, 7, 1, 15);
    public static final String BOT_DIFFICULTY = choice(
        "botDifficulty",
        Category.TABLE,
        1,
        "gui.minopp.rule.botDifficulty.low",
        "gui.minopp.rule.botDifficulty.medium",
        "gui.minopp.rule.botDifficulty.high");
    public static final String DECK_COPIES = integer("deckCopies", Category.TABLE, 0, 0, 8);
    public static final String JUMP_IN_SECONDS = integer("jumpInSeconds", Category.TABLE, 5, 1, 15);
    public static final String TURN_TIME_LIMIT = zeroOrInteger("turnTimeLimit", Category.TABLE, 0, 5, 99999);
    public static final String SHOUT_PENALTY = integer("shoutPenalty", Category.TABLE, 2, 0, 6);
    public static final String BALLOON_PENALTY = integer("balloonPenalty", Category.TABLE, 4, 1, 10);
    public static final String SCORE_TARGET = integer("scoreTarget", Category.TABLE, 0, 0, 10000);
    public static final String PEEK_HANDS = bool("peekHands", Category.TABLE, false);
    public static final String FLIP_BACKS = bool("flipBacks", Category.TABLE, true);

    static {
        registerPresets(SCORE_TARGET, 0, 200, 300, 500, 750, 1000);
        registerPresets(TURN_TIME_LIMIT, 0, 10, 15, 20, 30, 60);
    }

    public static void registerPresets(String id, int... values) {
        Rule rule = Objects.requireNonNull(get(id), "Unknown rule: " + id);
        if (values.length == 0) throw new IllegalArgumentException("Presets cannot be empty");
        int[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            if (rule.clamp(copy[i]) != copy[i] || i > 0 && copy[i] <= copy[i - 1]) {
                throw new IllegalArgumentException("Presets must be ordered, unique and within the rule range");
            }
        }
        PRESETS.put(id, copy);
        revision++;
    }

    public static int[] presets(String id) {
        int[] values = PRESETS.get(id);
        return values == null ? null : values.clone();
    }

    public static int stepPreset(String id, int value, int dir) {
        int[] p = PRESETS.get(id);
        if (p == null) return value;
        if (dir > 0) {
            for (int v : p) if (v > value) return v;
            return p[0];
        }
        for (int i = p.length - 1; i >= 0; i--) if (p[i] < value) return p[i];
        return p[p.length - 1];
    }

    public static Rule get(String id) {
        return REGISTRY.get(id);
    }

    /**
     * Returns a read-only live view of boolean rule IDs in registration order.
     */
    public static List<String> booleanIds() {
        return BOOLEAN_ID_VIEW;
    }

    public static List<Rule> all() {
        List<Rule> snapshot = rules;
        if (snapshot == null) {
            snapshot = List.copyOf(REGISTRY.values());
            rules = snapshot;
        }
        return snapshot;
    }

    public static List<Rule> byCategory(Category category) {
        List<Rule> list = new ArrayList<>();
        for (Rule r : REGISTRY.values()) if (r.category == category) list.add(r);
        return list;
    }
}
