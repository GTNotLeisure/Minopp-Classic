package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class AddonPacksTest {

    private int nextUid = 40_000;

    private Card card(String type, CardColor color, int number) {
        return new Card(nextUid++, new CardFace(type, color, number), null);
    }

    private Card num(CardColor color, int number) {
        return card("number", color, number);
    }

    private Card wild(String type) {
        return card(type, CardColor.WILD, -1);
    }

    private CardGame setup(int n) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < n; i++) players.add(new CardPlayer(UUID.randomUUID(), "P" + i));
        CardGame game = new CardGame(players);
        TableConfig config = new TableConfig();
        config.set(GameRules.JUMP_IN, 0);
        config.set(GameRules.CHALLENGE, 0);
        game.detachedConfig = config;
        game.initiate(players.get(0), 7);
        for (CardPlayer p : players) p.hand.clear();
        game.deck.clear();
        game.discardDeck.clear();
        for (int i = 0; i < 40; i++) game.deck.add(num(CardColor.YELLOW, i % 10));
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
    public void stackCardsAnswerOnlyTheirColor() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        game.pendingDraw = 2;
        game.pendingDrawSource = game.players.get(2).uuid;
        Card blue = card("stack1", CardColor.BLUE, -1), red = card("stack1", CardColor.RED, -1);
        me.hand.add(blue);
        me.hand.add(red);
        assertEquals("game.minopp.play.invalid_card", game.whyCannotPlay(me, blue, false));
        assertNull(game.whyCannotPlay(me, red, false));
        CardPlayer next = game.players.get(1);
        int before = next.hand.size();
        assertFalse(game.playCard(me, red.uid, color(null)).isFail);
        assertEquals(before + 3, next.hand.size() + game.pendingDraw, "the stacked 2+1 reaches the next player");
    }

    @Test
    public void stackNumberFlipsUntilANumber() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        game.deck.add(card("skip", CardColor.GREEN, -1));
        game.deck.add(num(CardColor.GREEN, 6));
        game.deck.add(card("reverse", CardColor.GREEN, -1));
        Card s = wild("wild_stack_number");
        me.hand.add(s);
        CardPlayer next = game.players.get(1);
        int before = next.hand.size();
        assertFalse(game.playCard(me, s.uid, color(CardColor.GREEN)).isFail);
        assertEquals(before + 6, next.hand.size() + game.pendingDraw);
    }

    @Test
    public void noUBouncesThePenalty() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0), source = game.players.get(2);
        game.pendingDraw = 4;
        game.pendingDrawSource = source.uuid;
        Card noU = wild("wild_no_u");
        me.hand.add(noU);
        int before = source.hand.size();
        assertFalse(game.playCard(me, noU.uid, color(CardColor.BLUE)).isFail);
        assertEquals(before + 4, source.hand.size());
        assertEquals(0, game.pendingDraw);
    }

    @Test
    public void blockCancelsThePenalty() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        game.pendingDraw = 4;
        game.pendingDrawSource = game.players.get(2).uuid;
        Card block = wild("wild_block");
        me.hand.add(block);
        assertFalse(game.playCard(me, block.uid, color(CardColor.BLUE)).isFail);
        assertEquals(0, game.pendingDraw);
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void speedMakesPlayersPlayMoreOrCrash() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0), next = game.players.get(1);
        Card speed = card("speed_up", CardColor.RED, -1);
        me.hand.add(speed);
        assertFalse(game.playCard(me, speed.uid, color(null)).isFail);
        assertEquals(2, game.speedLevel);
        assertEquals(1, game.currentPlayerIndex);
        Card r1 = num(CardColor.RED, 5);
        next.hand.add(r1);
        assertFalse(game.playCard(next, r1.uid, color(null)).isFail);
        assertEquals(CardGame.PlayerActionPhase.SPEED, game.currentPlayerPhase);
        int before = next.hand.size();
        assertFalse(game.playNoCard(next).isFail);
        assertEquals(before + 2, next.hand.size(), "a crash draws the speed level");
        assertEquals(1, game.speedLevel);
        assertEquals(2, game.currentPlayerIndex);
    }

    @Test
    public void passColorMovesOnlyThatColor() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0), next = game.players.get(1);
        Card pass = wild("wild_pass_color");
        me.hand.add(pass);
        int nextBefore = next.hand.size();
        assertFalse(game.playCard(me, pass.uid, color(CardColor.BLUE)).isFail);
        assertEquals(
            0,
            me.hand.stream()
                .filter(c -> c.light.color == CardColor.BLUE)
                .count() - 2);
        assertEquals(nextBefore, next.hand.size(), "everyone passed two blue cards and received two");
    }

    @Test
    public void colorChallengeBackfiresWhenEveryoneShows() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card ch = wild("wild_color_challenge");
        me.hand.add(ch);
        me.hand.add(num(CardColor.GREEN, 1));
        int before = me.hand.size();
        assertFalse(game.playCard(me, ch.uid, color(CardColor.BLUE)).isFail);
        assertEquals(before - 1 + 3, me.hand.size());
    }

    @Test
    public void eliminatorStripsOpponentWilds() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0), other = game.players.get(1);
        other.hand.add(wild("wild"));
        Card el = wild("wild_eliminator");
        me.hand.add(el);
        assertFalse(game.playCard(me, el.uid, color(CardColor.BLUE)).isFail);
        assertTrue(
            other.hand.stream()
                .noneMatch(c -> c.light.isWild()));
    }
}
