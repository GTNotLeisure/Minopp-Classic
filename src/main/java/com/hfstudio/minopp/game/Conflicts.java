package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Conflicts {

    private Conflicts() {}

    private static final Map<String, Set<String>> LINKS = new LinkedHashMap<>();

    private static long revision;

    public static long revision() {
        return revision;
    }

    public static String rule(String id) {
        return "rule:" + id;
    }

    public static String card(String typeId) {
        return "card:" + typeId;
    }

    private static String[] cards(String... ids) {
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = card(ids[i]);
        return out;
    }

    /**
     * Marks every key in {@code a} as not recommended with every key in {@code b}.
     */
    public static void link(String[] a, String... b) {
        boolean changed = false;
        for (String x : a) for (String y : b) {
            if (x.equals(y)) continue;
            changed |= LINKS.computeIfAbsent(x, k -> new LinkedHashSet<>())
                .add(y);
            changed |= LINKS.computeIfAbsent(y, k -> new LinkedHashSet<>())
                .add(x);
        }
        if (changed) revision++;
    }

    public static void link(String a, String... b) {
        link(new String[] { a }, b);
    }

    static {
        // Broad stacking overlaps with draw defenses without replacing their targeted-effect responses.
        link(
            rule(GameRules.STACK_ANY_DRAW),
            cards(
                "wild_block",
                "wild_shield",
                "wild_no_u",
                "wild_infinity_no_u",
                "wild_rebound",
                "wild_block_rebound",
                "wild_block_deflect",
                "wild_redirect"));
        // Skip and Reverse can also pass draw penalties on.
        link(
            rule(GameRules.DEFLECT_DRAW),
            cards(
                "wild_no_u",
                "wild_infinity_no_u",
                "wild_rebound",
                "wild_block_rebound",
                "wild_block_deflect",
                "wild_redirect"));
        // Keep distinct matching symbols, scoring and response rules for these overlapping effects.
        link(card("wild_draw6"), card("wild_plus6"));
        String[] repeatTurns = cards("skip_everyone", "bonus_turn", "play_again_10");
        link(repeatTurns, repeatTurns);
        link(card("wild_drawn_together"), card("wild_eggs_basket"));
        link(card("wild_draw_color"), card("wild_infinity"));
        link(card("wild_block"), card("wild_shield"));
        link(card("wild_no_u"), card("wild_infinity_no_u"));
        // The 7-0 rule already swaps and rotates whole hands.
        link(
            rule(GameRules.SEVEN_ZERO),
            cards(
                "trade_hands",
                "wild_swap_hands",
                "aw_forced_swap",
                "wild_hand_shuffle",
                "wild_crossfade",
                "wild_pass_hands",
                "wild_force_trade"));
        // Extra plays compound with cards that grant another play or increase the turn quota.
        String[] extraPlays = cards(
            "skip_everyone",
            "bonus_turn",
            "play_again_10",
            "speed_up",
            "wild_double_speed",
            "wild_power_reverse",
            "flex_skip",
            "replay",
            "wild_replay",
            "trick_wild");
        link(rule(GameRules.MULTI_PLAY), extraPlays);
        link(rule(GameRules.MULTI_PLAY), card("fast_number"));
        link(rule(GameRules.MULTI_PLAY), rule(GameRules.BRING_CARD));
        // Public hands remove the information gained from temporary reveals.
        link(rule(GameRules.PEEK_HANDS), cards("reveal_hands", "wild_reveal_one"));
        link(rule(GameRules.STACK_ANY_DRAW), rule(GameRules.STACK_DRAW4_ON_DRAW2));
        // No-bluffing prevents the illegal plays that a challenge detects.
        link(rule(GameRules.CHALLENGE), rule(GameRules.NO_BLUFFING));
    }

    /**
     * What a rule or card is not recommended with, in a stable order.
     */
    public static List<String> of(String key) {
        Set<String> s = LINKS.get(key);
        return s == null ? Collections.emptyList() : new ArrayList<>(s);
    }

    public static Set<String> keys() {
        return Collections.unmodifiableSet(LINKS.keySet());
    }

    /**
     * Returns an immutable snapshot of active rule and card keys, using merged counts and enabled card sides.
     */
    public static Set<String> activeKeys(TableConfig cfg) {
        Set<String> active = new LinkedHashSet<>();
        for (GameRules.Rule rule : GameRules.all()) if (cfg.getBool(rule.id())) active.add(rule(rule.id()));
        addActiveCards(active, cfg.faceCounts(false));
        if (cfg.getBool(GameRules.FLIP_MODE)) addActiveCards(active, cfg.faceCounts(true));
        return Collections.unmodifiableSet(active);
    }

    private static void addActiveCards(Set<String> active, Map<CardFace, Integer> counts) {
        for (Map.Entry<CardFace, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) active.add(card(entry.getKey().typeId));
        }
    }

    /**
     * Whether a rule is on or a card is in the deck of this table.
     */
    public static boolean isActive(String key, TableConfig cfg) {
        if (key.startsWith("rule:")) return cfg.getBool(key.substring(5));
        return key.startsWith("card:") && activeKeys(cfg).contains(key);
    }

    /**
     * The keys {@code key} clashes with that are active right now, when {@code key} itself is active too.
     */
    public static List<String> clashes(String key, TableConfig cfg) {
        List<String> out = new ArrayList<>();
        Set<String> others = LINKS.get(key);
        if (others == null) return out;
        Set<String> active = activeKeys(cfg);
        if (!active.contains(key)) return out;
        for (String other : others) {
            if (active.contains(other)) out.add(other);
        }
        return out;
    }
}
