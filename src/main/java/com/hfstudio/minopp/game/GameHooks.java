package com.hfstudio.minopp.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered game callbacks. Register on both sides during mod initialization; callbacks run on the calling game thread.
 */
public class GameHooks {

    private static final Map<String, Listener> LISTENERS = new LinkedHashMap<>();
    private static volatile Listener[] listeners = new Listener[0];

    private GameHooks() {}

    public interface Listener {

        /**
         * Returns a translation key to reject a play, or null. This query must not mutate game state.
         */
        default String checkPlayable(CardGame game, CardPlayer player, Card card, boolean useFlex) {
            return null;
        }

        default void started(CardGame game, ActionReport report) {}

        default void played(PlayContext context) {}

        default void turnStarted(CardGame game, CardPlayer player, ActionReport report) {}

        /**
         * Observes one completed draw batch. Decisions pause at engine boundaries, not inside callback code.
         */
        default void drawn(CardGame game, CardPlayer player, List<Card> cards, ActionReport report) {}

        default void finished(CardGame game, List<CardPlayer> winners, ActionReport report) {}
    }

    public static void register(String id, Listener listener) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listener, "listener");
        if (id.isBlank() || LISTENERS.putIfAbsent(id, listener) != null) {
            throw new IllegalArgumentException("Invalid or duplicate game hook: " + id);
        }
        listeners = LISTENERS.values()
            .toArray(Listener[]::new);
    }

    static String checkPlayable(CardGame game, CardPlayer player, Card card, boolean useFlex) {
        for (Listener listener : listeners) {
            String reason = listener.checkPlayable(game, player, card, useFlex);
            if (reason != null) return reason;
        }
        return null;
    }

    static void started(CardGame game, ActionReport report) {
        for (Listener listener : listeners) listener.started(game, report);
    }

    static void played(PlayContext context) {
        for (Listener listener : listeners) listener.played(context);
    }

    static void turnStarted(CardGame game, CardPlayer player, ActionReport report) {
        for (Listener listener : listeners) listener.turnStarted(game, player, report);
    }

    static void drawn(CardGame game, CardPlayer player, List<Card> cards, ActionReport report) {
        if (listeners.length == 0 || cards.isEmpty() || report == null) return;
        List<Card> snapshot = List.copyOf(cards);
        for (Listener listener : listeners) listener.drawn(game, player, snapshot, report);
    }

    static void finished(CardGame game, List<CardPlayer> winners, ActionReport report) {
        if (listeners.length == 0) return;
        List<CardPlayer> snapshot = List.copyOf(winners);
        for (Listener listener : listeners) listener.finished(game, snapshot, report);
    }
}
