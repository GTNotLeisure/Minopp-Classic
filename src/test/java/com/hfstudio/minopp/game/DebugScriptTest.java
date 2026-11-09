package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class DebugScriptTest {

    private static final String[] SCRIPT = { "wild", "wild_draw4", "draw2", "skip", "reverse", "wild_sudden_death",
        "flip", "wild", "flip", "wild_final_attack", "play_again_10" };

    @Test
    public void theDebuggerNeverRunsOutOfCards() {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            CardPlayer p = new CardPlayer(UUID.randomUUID(), "P" + i);
            p.seat = i;
            players.add(p);
        }
        CardGame game = new CardGame(players);
        TableConfig config = new TableConfig();
        config.set(GameRules.FLIP_MODE, 1);
        config.set(GameRules.COINS, 1);
        game.detachedConfig = config;
        game.debug = true;
        game.debugHold = true;
        game.debugger = players.get(0).uuid;
        game.initiate(players.get(0), 7);
        CardPlayer me = game.players.get(0);
        for (int step = 0; step < 60; step++) {
            assertFalse(me.hand.isEmpty(), "empty hand before step " + step);
            String id = SCRIPT[step % SCRIPT.length];
            Card pick = me.hand.stream()
                .filter(
                    c -> game.face(c)
                        .type().id.equals(id))
                .findFirst()
                .orElse(me.hand.get(0));
            PlayContext.PlayChoices ch = new PlayContext.PlayChoices();
            ch.color = CardColor.forSide(game.darkSide)
                .get(step % 4);
            game.playCard(me, pick.uid, ch);
            if (!game.decisions.isEmpty()) game.tickDecisions(Long.MAX_VALUE, u -> false);
        }
    }
}
