package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class DebugModeTest {

    private CardGame start(int n, boolean hold) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CardPlayer p = new CardPlayer(UUID.randomUUID(), "P" + i);
            p.seat = i;
            players.add(p);
        }
        CardGame game = new CardGame(players);
        game.detachedConfig = new TableConfig();
        game.debug = true;
        game.debugHold = hold;
        game.debugger = players.get(0).uuid;
        assertFalse(game.initiate(players.get(0), 7).isFail);
        return game;
    }

    private static PlayContext.PlayChoices red() {
        PlayContext.PlayChoices ch = new PlayContext.PlayChoices();
        ch.color = CardColor.RED;
        return ch;
    }

    @Test
    public void soloGameStartsWithEveryCard() {
        CardGame game = start(1, true);
        CardPlayer me = game.players.get(0);
        assertEquals(
            CardGame.debugCatalog()
                .size(),
            me.hand.size());
        assertTrue(
            me.hand.stream()
                .anyMatch(c -> c.light.type() == CardTypes.WILD));
        assertEquals(0, game.currentPlayerIndex);
    }

    @Test
    public void playsCostNothingAndDrawsAreOnlyShown() {
        CardGame game = start(1, true);
        CardPlayer me = game.players.get(0);
        int size = me.hand.size();
        Card skip = me.hand.stream()
            .filter(c -> c.light.type() == CardTypes.SKIP)
            .findFirst()
            .orElse(null);
        assertNotNull(skip);
        assertFalse(game.playCard(me, skip.uid, red()).isFail);
        assertEquals(size, me.hand.size());
        assertEquals(skip.uid, game.topCard.uid);
        int seq = game.debugDrawSeq;
        assertFalse(game.playNoCard(me).isFail);
        assertEquals(size, me.hand.size());
        assertEquals(seq + 1, game.debugDrawSeq);
        assertEquals(0, game.currentPlayerIndex);
    }

    @Test
    public void holdKeepsTheTurnAndPenaltiesStillLand() {
        CardGame game = start(3, true);
        CardPlayer me = game.players.get(0), next = game.players.get(1);
        int before = next.hand.size();
        Card draw2 = me.hand.stream()
            .filter(c -> c.light.type() == CardTypes.DRAW2)
            .findFirst()
            .orElse(null);
        assertNotNull(draw2);
        assertFalse(game.playCard(me, draw2.uid, red()).isFail);
        assertEquals(0, game.currentPlayerIndex);
        assertEquals(1, game.debugNext);
        assertEquals(before + 2, next.hand.size());
    }

    @Test
    public void withoutHoldOthersPlay() {
        CardGame game = start(3, false);
        CardPlayer me = game.players.get(0);
        Card number = me.hand.stream()
            .filter(c -> c.light.isNumber() && !c.light.isWild())
            .findFirst()
            .orElse(null);
        assertNotNull(number);
        assertFalse(game.playCard(me, number.uid, red()).isFail);
        assertEquals(1, game.currentPlayerIndex);
    }
}
