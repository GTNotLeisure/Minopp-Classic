package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayContext {

    public final CardGame game;
    public final CardPlayer player;
    public final Card card;
    public final CardFace face;
    public final PlayChoices choices;
    public final ActionReport report;

    public PlayContext(CardGame game, CardPlayer player, Card card, CardFace face, PlayChoices choices,
        ActionReport report) {
        this.game = game;
        this.player = player;
        this.card = card;
        this.face = face;
        this.choices = choices;
        this.report = report;
    }

    public CardPlayer target(int index) {
        if (index >= choices.targets.size()) return null;
        CardPlayer p = game.deAmputate(choices.targets.get(index));
        return p != null && !p.equals(player) && !p.eliminated ? p : null;
    }

    public static final class PlayChoices {

        public CardColor color;
        public final List<UUID> targets = new ArrayList<>();
        public boolean useFlex;
        public boolean shout;
        /** Number Sign cards: the number named, 1 to 9, or -1. */
        public int number = -1;
        /** Cards picked from the hand to discard, by uid. */
        public final List<Integer> picks = new ArrayList<>();

        public static PlayChoices of(CardColor color) {
            PlayChoices c = new PlayChoices();
            c.color = color;
            return c;
        }
    }
}
