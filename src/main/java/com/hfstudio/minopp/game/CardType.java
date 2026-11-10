package com.hfstudio.minopp.game;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

public class CardType {

    public enum Choice {

        NONE,
        COLOR,
        TARGET,
        COLOR_AND_TARGET,
        TWO_TARGETS,
        COLOR_AND_TWO_TARGETS,
        NUMBER;

        public boolean needsNumber() {
            return this == NUMBER;
        }

        public boolean needsColor() {
            return this == COLOR || this == COLOR_AND_TARGET || this == COLOR_AND_TWO_TARGETS;
        }

        public int targetCount() {
            return switch (this) {
                case TARGET, COLOR_AND_TARGET -> 1;
                case TWO_TARGETS, COLOR_AND_TWO_TARGETS -> 2;
                default -> 0;
            };
        }
    }

    @FunctionalInterface
    public interface Effect {

        void apply(PlayContext ctx);
    }

    @FunctionalInterface
    public interface Condition {

        boolean test(CardGame game, CardPlayer player, CardFace face);
    }

    public interface Automation {

        default int score(CardGame game, CardPlayer player, Card card, boolean useFlex, int defaultScore) {
            return defaultScore;
        }

        default void choose(CardGame game, CardPlayer player, Card card, PlayContext.PlayChoices choices) {}
    }

    public final String id;
    public final String packId;
    public final boolean wild;
    public final boolean numbered;
    public final String matchKey;
    public final Set<String> matchAliases;
    public final String shortLabel;
    public final String symbol;
    public final boolean customSymbol;
    public final int sortOrder;
    public final int drawValue;
    public final boolean keepsColor;
    public final Choice choice;
    public final Choice flexChoice;
    public final Effect effect;
    public final Effect flexEffect;
    public final Condition condition;
    public final boolean answersDraw;
    public final int points;
    /**
     * Cards the player picks from their hand to discard: a count, {@link #PICK_HALF}, or 0 for none.
     */
    public final int pick;
    /**
     * Fast cards: the player may play another card right after.
     */
    public final boolean fast;
    /**
     * Cards printed in one color only, such as the colorless numbers; null for the usual four.
     */
    public final CardColor fixedColor;
    public final Automation automation;
    public final BiConsumer<CardGame, CardFace> opening;

    public static final int PICK_HALF = -1;
    public static final int INFINITE_POINTS = Integer.MAX_VALUE;

    private CardType(Builder b) {
        this.id = b.id;
        this.packId = b.packId;
        this.wild = b.wild;
        this.numbered = b.numbered;
        this.matchKey = b.matchKey == null ? b.id : b.matchKey;
        this.matchAliases = b.matchAliases;
        this.shortLabel = b.shortLabel == null ? "" : b.shortLabel;
        this.symbol = b.symbol == null ? b.id : b.symbol;
        this.customSymbol = b.symbol != null;
        this.sortOrder = b.sortOrder;
        this.drawValue = b.drawValue;
        this.keepsColor = b.keepsColor;
        this.choice = b.choice;
        this.flexChoice = b.flexChoice;
        this.effect = b.effect;
        this.flexEffect = b.flexEffect;
        this.condition = b.condition;
        this.answersDraw = b.answersDraw;
        this.points = b.points;
        this.pick = b.pick;
        this.fast = b.fast;
        this.fixedColor = b.fixedColor;
        this.automation = b.automation;
        this.opening = b.opening;
    }

    /**
     * How many cards a player holding {@code handAfter} cards picks to discard.
     */
    public int pickCount(int handAfter) {
        int n = pick == PICK_HALF ? handAfter / 2 : pick;
        return Math.max(0, Math.min(n, handAfter));
    }

    public boolean sharesSymbol(CardType other) {
        if (matchKey.equals(other.matchKey) || matchAliases.contains(other.matchKey)
            || other.matchAliases.contains(matchKey)) return true;
        for (String alias : matchAliases) if (other.matchAliases.contains(alias)) return true;
        return false;
    }

    public boolean isDraw() {
        return drawValue > 0;
    }

    public boolean hasFlex() {
        return flexEffect != null;
    }

    public Choice choiceFor(boolean flex) {
        return flex && hasFlex() ? flexChoice : choice;
    }

    public static Builder builder(String id, String packId) {
        return new Builder(id, packId);
    }

    public static class Builder {

        private final String id;
        private final String packId;
        private boolean wild, numbered, keepsColor, answersDraw, fast;
        private int pick;
        private CardColor fixedColor;
        private String matchKey, shortLabel, symbol;
        private Set<String> matchAliases = Set.of();
        private int sortOrder = 100, drawValue, points = 20;
        private Choice choice = Choice.NONE, flexChoice = Choice.NONE;
        private Effect effect = ctx -> {};
        private Effect flexEffect;
        private Condition condition;
        private Automation automation;
        private BiConsumer<CardGame, CardFace> opening;

        private Builder(String id, String packId) {
            this.id = Objects.requireNonNull(id, "id");
            this.packId = Objects.requireNonNull(packId, "packId");
            if (id.isBlank() || packId.isBlank())
                throw new IllegalArgumentException("Card and pack ids cannot be blank");
        }

        public Builder wild() {
            this.wild = true;
            this.choice = Choice.COLOR;
            this.points = 50;
            return this;
        }

        public Builder allWild() {
            this.wild = true;
            this.keepsColor = true;
            this.choice = Choice.NONE;
            this.points = 50;
            return this;
        }

        public Builder numbered() {
            this.numbered = true;
            this.points = 0;
            this.sortOrder = 0;
            return this;
        }

        public Builder match(String matchKey) {
            this.matchKey = matchKey;
            return this;
        }

        public Builder matchAliases(String... aliases) {
            for (String alias : aliases) if (alias == null || alias.isBlank())
                throw new IllegalArgumentException("Match aliases cannot be blank");
            this.matchAliases = Set.of(aliases);
            return this;
        }

        public Builder label(String shortLabel) {
            this.shortLabel = shortLabel;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder sort(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public Builder draw(int drawValue) {
            this.drawValue = drawValue;
            return this;
        }

        public Builder choice(Choice choice) {
            this.choice = choice;
            return this;
        }

        public Builder effect(Effect effect) {
            this.effect = effect;
            return this;
        }

        public Builder flex(Choice flexChoice, Effect flexEffect) {
            this.flexChoice = flexChoice;
            this.flexEffect = flexEffect;
            return this;
        }

        public Builder condition(Condition condition) {
            this.condition = condition;
            return this;
        }

        public Builder answersDraw() {
            this.answersDraw = true;
            return this;
        }

        public Builder points(int points) {
            this.points = points;
            return this;
        }

        public Builder pick(int pick) {
            this.pick = pick;
            return this;
        }

        public Builder fast() {
            this.fast = true;
            return this;
        }

        public Builder fixedColor(CardColor color) {
            this.fixedColor = color;
            return this;
        }

        public Builder automation(Automation automation) {
            this.automation = Objects.requireNonNull(automation, "automation");
            return this;
        }

        public Builder opening(BiConsumer<CardGame, CardFace> opening) {
            this.opening = Objects.requireNonNull(opening, "opening");
            return this;
        }

        public CardType build() {
            return new CardType(this);
        }
    }
}
