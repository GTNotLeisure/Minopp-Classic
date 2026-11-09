package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class ShowEmTest {

    private int nextUid = 60_000;

    private Card card(String type, CardColor color, int number) {
        return new Card(nextUid++, new CardFace(type, color, number), null);
    }

    private Card num(CardColor color, int number) {
        return card("number", color, number);
    }

    private CardGame setup(int n, boolean coins) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < n; i++) players.add(new CardPlayer(UUID.randomUUID(), "P" + i));
        CardGame game = new CardGame(players);
        TableConfig config = new TableConfig();
        config.set(GameRules.JUMP_IN, 0);
        config.set(GameRules.CHALLENGE, 0);
        config.set(GameRules.COINS, coins ? 1 : 0);
        game.detachedConfig = config;
        game.initiate(players.get(0), 7);
        for (CardPlayer p : players) p.hand.clear();
        game.deck.clear();
        game.discardDeck.clear();
        for (int i = 0; i < 120; i++) game.deck.add(num(CardColor.YELLOW, i % 10));
        game.topCard = num(CardColor.RED, 3);
        game.currentColor = CardColor.RED;
        game.currentPlayerIndex = 0;
        game.pendingDraw = 0;
        game.currentPlayerPhase = CardGame.PlayerActionPhase.DISCARD_HAND;
        game.decisions.clear();
        game.awaitingCutIn = false;
        game.isAntiClockwise = false;
        for (CardPlayer p : players) {
            p.hand.add(num(CardColor.BLUE, 9));
            p.hand.add(num(CardColor.BLUE, 8));
        }
        return game;
    }

    private static PlayContext.PlayChoices color(CardColor c) {
        PlayContext.PlayChoices ch = new PlayContext.PlayChoices();
        ch.color = c;
        return ch;
    }

    @Test
    public void suddenDeathFillsEveryHandTo24() {
        CardGame game = setup(3, false);
        CardPlayer me = game.players.get(0);
        Card sd = card("wild_sudden_death", CardColor.WILD, -1);
        me.hand.add(sd);
        assertFalse(game.playCard(me, sd.uid, color(CardColor.BLUE)).isFail);
        for (CardPlayer p : game.players) assertEquals(24, p.hand.size());
    }

    @Test
    public void finalAttackCountsActionAndWildCards() {
        CardGame game = setup(3, false);
        CardPlayer me = game.players.get(0), next = game.players.get(1);
        me.hand.add(card("skip", CardColor.RED, -1));
        me.hand.add(card("wild", CardColor.WILD, -1));
        Card fa = card("wild_final_attack", CardColor.WILD, -1);
        me.hand.add(fa);
        int before = next.hand.size();
        assertFalse(game.playCard(me, fa.uid, color(CardColor.BLUE)).isFail);
        assertEquals(before + 2, next.hand.size());
        assertEquals(2, game.currentPlayerIndex, "the attacked player loses the turn");
    }

    @Test
    public void finalAttackWithSevenActionsHitsEveryone() {
        CardGame game = setup(3, false);
        CardPlayer me = game.players.get(0), next = game.players.get(1), last = game.players.get(2);
        for (int i = 0; i < 7; i++) me.hand.add(card("skip", CardColor.RED, -1));
        Card fa = card("wild_final_attack", CardColor.WILD, -1);
        me.hand.add(fa);
        int n0 = next.hand.size(), l0 = last.hand.size();
        assertFalse(game.playCard(me, fa.uid, color(CardColor.BLUE)).isFail);
        assertEquals(n0 + 25, next.hand.size());
        assertEquals(l0 + 5, last.hand.size());
    }

    @Test
    public void mercyCoinSwapsTheHand() {
        CardGame game = setup(3, true);
        CardPlayer me = game.players.get(0);
        for (int i = 0; i < 10; i++) me.hand.add(num(CardColor.GREEN, 4));
        assertFalse(game.useCoin(me, false).isFail);
        assertEquals(7, me.hand.size());
        assertTrue(me.coinUsed);
        assertTrue(game.useCoin(me, false).isFail, "a coin works once per game");
    }

    @Test
    public void noMercyCoinDoublesTheNextDrawCard() {
        CardGame game = setup(3, true);
        CardPlayer me = game.players.get(0), next = game.players.get(1);
        assertFalse(game.useCoin(me, true).isFail);
        assertTrue(me.coinNoMercy);
        assertFalse(game.useCoin(me, false).isFail);
        assertTrue(me.coinArmed);
        Card d2 = card("draw2", CardColor.RED, -1);
        me.hand.add(d2);
        int before = next.hand.size();
        assertFalse(game.playCard(me, d2.uid, color(null)).isFail);
        assertEquals(before + 4, next.hand.size() + game.pendingDraw);
        assertFalse(me.coinArmed);
    }

    @Test
    public void coinSideLocksAfterTheFirstMove() {
        CardGame game = setup(3, true);
        CardPlayer me = game.players.get(0);
        Card r = num(CardColor.RED, 5);
        me.hand.add(r);
        assertFalse(game.playCard(me, r.uid, color(null)).isFail);
        assertTrue(game.useCoin(me, true).isFail);
    }

    @Test
    public void coinsAreOffUnlessTheRuleIsOn() {
        CardGame game = setup(3, false);
        assertTrue(game.useCoin(game.players.get(0), false).isFail);
    }
}
