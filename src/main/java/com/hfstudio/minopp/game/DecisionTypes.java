package com.hfstudio.minopp.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DecisionTypes {

    private static final Map<String, Definition> REGISTRY = new LinkedHashMap<>();

    private DecisionTypes() {}

    public enum Continuation {
        ADVANCE_TURN,
        RESUME_TURN
    }

    public record Option(int value, String labelKey) {

        public Option {
            if (value < 0) throw new IllegalArgumentException("Decision values must be nonnegative");
            Objects.requireNonNull(labelKey, "labelKey");
        }
    }

    public record Definition(String id, String titleKey, Handler handler) {

        public Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(handler, "handler");
            if (id.isBlank()) throw new IllegalArgumentException("Decision id cannot be blank");
        }
    }

    public interface Handler {

        /**
         * Returns stable, nonempty options. Called on both sides; do not mutate the game here.
         */
        List<Option> options(CardGame game, PendingDecision decision);

        /**
         * Validates an answer on the server. Accepted intermediate steps may update data without completing the
         * decision.
         */
        default boolean answer(CardGame game, PendingDecision decision, int answer, UUID target) {
            if (target != null) return false;
            for (Option option : options(game, decision)) {
                if (option.value() == answer) {
                    decision.answer = answer;
                    return true;
                }
            }
            return false;
        }

        /**
         * Validates one batch for a single decision. Override to accept multiple values atomically.
         */
        default boolean answerAll(CardGame game, PendingDecision decision, int[] answers, UUID target) {
            return answers.length == 1 && answer(game, decision, answers[0], target);
        }

        /**
         * Stores a valid answer for bots and expired decisions. Custom answer formats must override this too.
         */
        default void answerAutomatically(CardGame game, PendingDecision decision) {
            List<Option> options = options(game, decision);
            if (options.isEmpty() || !answer(
                game,
                decision,
                options.get(0)
                    .value(),
                null)) {
                throw new IllegalStateException("No automatic answer for decision: " + decision.typeId);
            }
        }

        /**
         * Resolves the whole batch. The engine checks the result and follows the requested continuation afterward.
         */
        void resolve(CardGame game, List<PendingDecision> decisions, ActionReport report);
    }

    public static Definition register(Definition definition) {
        Objects.requireNonNull(definition, "definition");
        if (REGISTRY.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Decision already registered: " + definition.id());
        }
        return definition;
    }

    public static Definition get(String id) {
        Definition definition = REGISTRY.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown decision type: " + id);
        return definition;
    }
}
