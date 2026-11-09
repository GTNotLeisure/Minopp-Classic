package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rules and cards that are not recommended together: they still work, but one makes the other pointless or drags
 * games out. Keys are {@code rule:<id>} and {@code card:<typeId>}; every link goes both ways.
 */
public final class Conflicts {

    private Conflicts() {}

    private static final Map<String, Set<String>> LINKS = new LinkedHashMap<>();

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

    /** Marks every key in {@code a} as not recommended with every key in {@code b}. */
    public static void link(String[] a, String... b) {
        for (String x : a) for (String y : b) {
            if (x.equals(y)) continue;
            LINKS.computeIfAbsent(x, k -> new LinkedHashSet<>())
                .add(y);
            LINKS.computeIfAbsent(y, k -> new LinkedHashSet<>())
                .add(x);
        }
    }

    public static void link(String a, String... b) {
        link(new String[] { a }, b);
    }

    static {
        // any draw stacks on any draw: blocking and bouncing a penalty is never needed
        link(
            rule(GameRules.STACK_ANY_DRAW),
            cards(
                "wild_block",
                "wild_shield",
                "wild_no_u",
                "wild_rebound",
                "wild_block_rebound",
                "wild_block_deflect",
                "wild_redirect"));
        // Skip and Reverse already send penalties on; these cards do the same
        link(
            rule(GameRules.DEFLECT_DRAW),
            cards("wild_no_u", "wild_rebound", "wild_block_rebound", "wild_block_deflect", "wild_redirect"));
        // 7-0 swaps hands on every 7 and 0; these cards already trade hands
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
        // extra plays on top of extra plays end games too quickly
        String[] extraPlays = cards(
            "skip_everyone",
            "bonus_turn",
            "play_again_10",
            "speed_up",
            "wild_double_speed",
            "wild_power_reverse");
        link(rule(GameRules.MULTI_PLAY), extraPlays);
        link(rule(GameRules.MULTI_PLAY), rule(GameRules.BRING_CARD));
        // with bluffing ruled out there is nothing left to challenge
        link(rule(GameRules.CHALLENGE), rule(GameRules.NO_BLUFFING));
    }

    /** What a rule or card is not recommended with, in a stable order. */
    public static List<String> of(String key) {
        Set<String> s = LINKS.get(key);
        return s == null ? Collections.emptyList() : new ArrayList<>(s);
    }

    public static Set<String> keys() {
        return Collections.unmodifiableSet(LINKS.keySet());
    }

    /** Whether a rule is on or a card is in the deck of this table. */
    public static boolean isActive(String key, TableConfig cfg) {
        if (key.startsWith("rule:")) return cfg.getBool(key.substring(5));
        if (key.startsWith("card:")) {
            String type = key.substring(5);
            for (CardPack pack : CardPack.all()) {
                for (CardPack.Entry e : pack.entries) {
                    if (e.typeId()
                        .equals(type) && cfg.getCount(pack, e) > 0) return true;
                }
            }
        }
        return false;
    }

    /** The keys {@code key} clashes with that are active right now, when {@code key} itself is active too. */
    public static List<String> clashes(String key, TableConfig cfg) {
        List<String> out = new ArrayList<>();
        if (!isActive(key, cfg)) return out;
        for (String o : of(key)) if (isActive(o, cfg)) out.add(o);
        return out;
    }
}
