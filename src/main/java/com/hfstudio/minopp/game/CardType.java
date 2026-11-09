package com.hfstudio.minopp.game;

public final class CardType {

    public enum Choice {

        NONE,
        COLOR,
        TARGET,
        COLOR_AND_TARGET,
        TWO_TARGETS,
        COLOR_AND_TWO_TARGETS;

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

    public final String id;
    public final String packId;
    public final boolean wild;
    public final boolean numbered;
    public final String matchKey;
    public final String shortLabel;
    public final String symbol;
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

    private CardType(Builder b) {
        this.id = b.id;
        this.packId = b.packId;
        this.wild = b.wild;
        this.numbered = b.numbered;
        this.matchKey = b.matchKey == null ? b.id : b.matchKey;
        this.shortLabel = b.shortLabel == null ? "" : b.shortLabel;
        this.symbol = b.symbol == null ? b.id : b.symbol;
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

    public static final class Builder {

        private final String id;
        private final String packId;
        private boolean wild, numbered, keepsColor, answersDraw;
        private String matchKey, shortLabel, symbol;
        private int sortOrder = 100, drawValue, points = 20;
        private Choice choice = Choice.NONE, flexChoice = Choice.NONE;
        private Effect effect = ctx -> {};
        private Effect flexEffect;
        private Condition condition;

        private Builder(String id, String packId) {
            this.id = id;
            this.packId = packId;
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

        public CardType build() {
            return new CardType(this);
        }
    }
}
