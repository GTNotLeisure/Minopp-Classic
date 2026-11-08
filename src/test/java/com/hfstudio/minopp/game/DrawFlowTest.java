package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class DrawFlowTest {

    private int nextUid = 10_000;

    private Card card(CardColor color, int number) {
        return new Card(nextUid++, new CardFace("number", color, number), null);
    }

    private CardGame setup(String... enabledRules) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < 3; i++) players.add(new CardPlayer(UUID.randomUUID(), "P" + i));
        CardGame game = new CardGame(players);
        TableConfig config = new TableConfig();
        config.set(GameRules.JUMP_IN, 0);
        for (String rule : enabledRules) config.set(rule, 1);
        game.detachedConfig = config;
        game.initiate(players.get(0), 7);
        for (CardPlayer p : players) p.hand.clear();
        game.deck.clear();
        game.discardDeck.clear();
        game.topCard = card(CardColor.RED, 5);
        game.currentColor = CardColor.RED;
        game.currentPlayerIndex = 0;
        game.pendingDraw = 0;
        game.currentPlayerPhase = CardGame.PlayerActionPhase.DISCARD_HAND;
        game.decisions.clear();
        game.awaitingCutIn = false;
        for (CardPlayer p : players) {
            p.hand.add(card(CardColor.BLUE, 9));
            p.hand.add(card(CardColor.BLUE, 8));
            p.hand.add(card(CardColor.BLUE, 7));
        }
        return game;
    }

    private CardPlayer p(CardGame game, int i) {
        return game.players.get(i);
    }

    @Test
    public void drawOffTurnIsRefused() {
        CardGame game = setup();
        game.deck.add(card(CardColor.GREEN, 1));
        ActionReport r = game.playNoCard(p(game, 1));
        assertTrue(r.isFail);
        assertEquals(3, p(game, 1).hand.size());
        assertEquals(1, game.deck.size());
        assertEquals(0, game.currentPlayerIndex);
    }

    @Test
    public void unplayableDrawPassesTheTurn() {
        CardGame game = setup();
        game.deck.add(card(CardColor.GREEN, 1));
        ActionReport r = game.playNoCard(p(game, 0));
        assertFalse(r.isFail);
        assertEquals(4, p(game, 0).hand.size());
        assertEquals(1, game.currentPlayerIndex);
        assertEquals(CardGame.PlayerActionPhase.DISCARD_HAND, game.currentPlayerPhase);
    }

    @Test
    public void playableDrawCanBePlayedOrPassed() {
        CardGame game = setup(GameRules.PLAY_DRAWN);
        Card drawnRed = card(CardColor.RED, 2);
        game.deck.add(drawnRed);
        Card otherRed = card(CardColor.RED, 3);
        p(game, 0).hand.add(otherRed);

        ActionReport r = game.playNoCard(p(game, 0));
        assertFalse(r.isFail);
        assertEquals(CardGame.PlayerActionPhase.DISCARD_DRAWN, game.currentPlayerPhase);
        assertEquals(drawnRed.uid, game.drawnCardUid);
        assertEquals(0, game.currentPlayerIndex, "still our turn after a playable draw");
        assertEquals("game.minopp.play.only_drawn_card", game.whyCannotPlay(p(game, 0), otherRed, false));
        assertNull(game.whyCannotPlay(p(game, 0), drawnRed, false));
        assertTrue(game.playCard(p(game, 0), otherRed.uid, new PlayContext.PlayChoices()).isFail);

        ActionReport pass = game.playNoCard(p(game, 0));
        assertFalse(pass.isFail);
        assertEquals(1, game.currentPlayerIndex);
        assertTrue(p(game, 0).hand.contains(drawnRed));
    }

    @Test
    public void playableDrawIsPlayedFromTheDrawnPhase() {
        CardGame game = setup(GameRules.PLAY_DRAWN);
        Card drawnRed = card(CardColor.RED, 2);
        game.deck.add(drawnRed);
        game.playNoCard(p(game, 0));
        ActionReport r = game.playCard(p(game, 0), drawnRed.uid, new PlayContext.PlayChoices());
        assertFalse(r.isFail);
        assertEquals(drawnRed, game.topCard);
        assertEquals(3, p(game, 0).hand.size());
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void drawToMatchStopsAtThePlayableCard() {
        CardGame game = setup(GameRules.DRAW_TO_MATCH);
        Card match = card(CardColor.RED, 1);
        game.deck.add(match); // bottom
        game.deck.add(card(CardColor.BLUE, 1));
        game.deck.add(card(CardColor.GREEN, 2));
        game.deck.add(card(CardColor.YELLOW, 3)); // top, drawn first
        game.playNoCard(p(game, 0));
        assertEquals(3 + 4, p(game, 0).hand.size());
        assertTrue(game.deck.isEmpty());
        assertEquals(CardGame.PlayerActionPhase.DISCARD_DRAWN, game.currentPlayerPhase);
        assertEquals(match.uid, game.drawnCardUid);
    }

    @Test
    public void forcedPlayPlaysTheDrawnCard() {
        CardGame game = setup(GameRules.FORCED_PLAY);
        Card drawnRed = card(CardColor.RED, 8);
        game.deck.add(drawnRed);
        ActionReport r = game.playNoCard(p(game, 0));
        assertFalse(r.isFail);
        assertEquals(drawnRed, game.topCard);
        assertEquals(3, p(game, 0).hand.size());
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void drawingTakesAPendingPenalty() {
        CardGame game = setup();
        for (int i = 0; i < 6; i++) game.deck.add(card(CardColor.GREEN, i));
        game.pendingDraw = 4;
        game.lastDrawValue = 4;
        ActionReport r = game.playNoCard(p(game, 0));
        assertFalse(r.isFail);
        assertEquals(3 + 4, p(game, 0).hand.size());
        assertEquals(0, game.pendingDraw);
        assertEquals(2, game.deck.size());
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void emptyDeckReshufflesTheDiscardPile() {
        CardGame game = setup();
        for (int i = 0; i < 5; i++) game.discardDeck.add(card(CardColor.GREEN, i));
        Card top = game.topCard;
        game.playNoCard(p(game, 0));
        assertEquals(4, p(game, 0).hand.size());
        assertEquals(4, game.deck.size(), "the 5 discarded cards became the deck, one was drawn");
        assertTrue(game.discardDeck.isEmpty());
        assertEquals(top, game.topCard, "the top card stays on the table");
    }

    @Test
    public void exhaustedDeckStillPassesTheTurn() {
        CardGame game = setup();
        ActionReport r = game.playNoCard(p(game, 0));
        assertFalse(r.isFail);
        assertEquals(3, p(game, 0).hand.size());
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void clientChecksMirrorTheServer() {
        CardGame game = setup();
        Card red = card(CardColor.RED, 1);
        p(game, 1).hand.add(red);
        assertEquals("game.minopp.play.not_your_turn", game.whyCannotPlay(p(game, 1), red, false));
        Card blue = p(game, 0).hand.get(0);
        assertEquals("game.minopp.play.invalid_card", game.whyCannotPlay(p(game, 0), blue, false));
        assertEquals("game.minopp.play.not_your_card", game.whyCannotPlay(p(game, 0), red, false));
        game.decisions
            .add(new PendingDecision(PendingDecision.Type.CHALLENGE, p(game, 0).uuid, p(game, 2).uuid, Long.MAX_VALUE));
        Card myRed = card(CardColor.RED, 4);
        p(game, 0).hand.add(myRed);
        assertEquals("game.minopp.play.waiting_decision", game.whyCannotPlay(p(game, 0), myRed, false));
        game.decisions.clear();
        assertNull(game.whyCannotPlay(p(game, 0), myRed, false));
    }

    @Test
    public void minoOnlyOnOwnTurnWithTwoOrFewerCards() {
        CardGame game = setup();
        CardPlayer me = p(game, 0), other = p(game, 1);

        ActionReport early = game.shoutMino(me);
        assertNotNull(early);
        assertTrue(early.isFail);
        assertFalse(me.hasShoutedMino);
        assertEquals(3, me.hand.size(), "no penalty for shouting early, it is just refused");

        other.hand.remove(0);
        ActionReport offTurn = game.shoutMino(other);
        assertTrue(offTurn.isFail);
        assertFalse(other.hasShoutedMino);

        me.hand.remove(0);
        assertEquals(2, me.hand.size());
        ActionReport ok = game.shoutMino(me);
        assertFalse(ok.isFail);
        assertTrue(me.hasShoutedMino);

        game.deck.add(card(CardColor.GREEN, 1));
        game.playNoCard(me);
        assertFalse(me.hasShoutedMino);
    }

    @Test
    public void minoShoutedTogetherWithTheCard() {
        CardGame game = setup();
        CardPlayer me = p(game, 0);
        me.hand.remove(0);
        Card red = card(CardColor.RED, 6);
        me.hand.set(0, red);
        PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
        choices.shout = true;
        assertFalse(game.playCard(me, red.uid, choices).isFail);
        assertEquals(1, me.hand.size());
        assertTrue(me.hasShoutedMino);
    }

    @Test
    public void minoWithThreeCardsIsNotShoutedByThePlay() {
        CardGame game = setup();
        CardPlayer me = p(game, 0);
        Card red = card(CardColor.RED, 6);
        me.hand.add(red);
        PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
        choices.shout = true;
        assertFalse(game.playCard(me, red.uid, choices).isFail);
        assertEquals(3, me.hand.size());
        assertFalse(me.hasShoutedMino);
    }
}
