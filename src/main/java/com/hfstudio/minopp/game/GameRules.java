package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameRules {

    private GameRules() {}

    public enum Category {
        BASIC,
        HOUSE,
        NO_MERCY,
        MODES,
        TABLE
    }

    public record Rule(String id, Category category, int defaultValue, int min, int max, boolean bool) {

        public String langKey() {
            return "gui.minopp.rule." + id;
        }

        public int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static final Map<String, Rule> REGISTRY = new LinkedHashMap<>();

    private static String bool(String id, Category category, boolean def) {
        REGISTRY.put(id, new Rule(id, category, def ? 1 : 0, 0, 1, true));
        return id;
    }

    private static String integer(String id, Category category, int def, int min, int max) {
        REGISTRY.put(id, new Rule(id, category, def, min, max, false));
        return id;
    }

    // Rules of the original mod (rule ids kept for compatibility with existing tables)
    public static final String JUMP_IN = bool("allowJumpIn", Category.HOUSE, true);
    public static final String STACKING = bool("allowStacking", Category.HOUSE, true);
    public static final String SEVEN_ZERO = bool("allowSeven0", Category.HOUSE, false);

    public static final String DRAW_TO_MATCH = bool("drawToMatch", Category.HOUSE, false);
    public static final String FORCED_PLAY = bool("forcedPlay", Category.HOUSE, false);
    public static final String NO_BLUFFING = bool("noBluffing", Category.HOUSE, false);
    public static final String CHALLENGE = bool("challengeDraw4", Category.HOUSE, false);
    public static final String TEAM_MODE = bool("teamMode", Category.MODES, false);
    public static final String TEAM_COUNT = integer("teamCount", Category.MODES, 2, 2, Teams.MAX_TEAMS);
    public static final String TEAM_RELAY = bool("teamRelay", Category.MODES, false);
    public static final String PLAY_DRAWN = bool("playDrawnCard", Category.BASIC, false);
    public static final String STACK_DRAW4_ON_DRAW2 = bool("stackDraw4OnDraw2", Category.HOUSE, false);
    public static final String DEFLECT_DRAW = bool("deflectDraw", Category.HOUSE, false);
    public static final String MULTI_PLAY = bool("multiPlay", Category.HOUSE, false);
    public static final String NUMBER_FINISH = bool("numberFinish", Category.HOUSE, false);

    public static final String MERCY = bool("mercyRule", Category.NO_MERCY, false);
    public static final String MERCY_LIMIT = integer("mercyLimit", Category.NO_MERCY, 25, 10, 60);
    public static final String STACK_ANY_DRAW = bool("stackAnyDraw", Category.NO_MERCY, false);

    public static final String FLIP_MODE = bool("flipMode", Category.MODES, false);
    public static final String FLEX_POWER = bool("flexPower", Category.MODES, false);

    public static final String MAX_PLAYERS = integer("maxPlayers", Category.TABLE, 4, 2, 20);
    public static final String INITIAL_HAND = integer("initialHand", Category.TABLE, 7, 1, 15);
    public static final String DECK_COPIES = integer("deckCopies", Category.TABLE, 0, 0, 8);
    public static final String JUMP_IN_SECONDS = integer("jumpInSeconds", Category.TABLE, 5, 1, 15);
    public static final String SHOUT_PENALTY = integer("shoutPenalty", Category.TABLE, 2, 0, 6);
    public static final String BALLOON_PENALTY = integer("balloonPenalty", Category.TABLE, 4, 1, 10);
    public static final String SCORE_TARGET = integer("scoreTarget", Category.TABLE, 0, 0, 20);

    public static Rule get(String id) {
        return REGISTRY.get(id);
    }

    public static List<Rule> all() {
        return List.copyOf(REGISTRY.values());
    }

    public static List<Rule> byCategory(Category category) {
        List<Rule> list = new ArrayList<>();
        for (Rule r : REGISTRY.values()) if (r.category == category) list.add(r);
        return list;
    }
}
