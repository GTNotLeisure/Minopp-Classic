package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class NewContentTest {

    private int nextUid = 20_000;

    private Card card(String type, CardColor color, int number) {
        return new Card(nextUid++, new CardFace(type, color, number), null);
    }

    private Card num(CardColor color, int number) {
        return card("number", color, number);
    }

    private CardGame setup(String... enabledRules) {
        return setupN(3, enabledRules);
    }

    private CardGame setupN(int n, String... enabledRules) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < n; i++) players.add(new CardPlayer(UUID.randomUUID(), "P" + i));
        CardGame game = new CardGame(players);
        TableConfig config = new TableConfig();
        config.set(GameRules.JUMP_IN, 0);
        for (String rule : enabledRules) config.set(rule, 1);
        game.detachedConfig = config;
        game.initiate(players.get(0), 7);
        for (CardPlayer p : players) p.hand.clear();
        game.deck.clear();
        game.discardDeck.clear();
        for (int i = 0; i < 30; i++) game.deck.add(num(CardColor.YELLOW, i % 10));
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

    private CardPlayer p(CardGame game, int i) {
        return game.players.get(i);
    }

    private static PlayContext.PlayChoices none() {
        return new PlayContext.PlayChoices();
    }

    @Test
    public void reverseDeflectsAPendingDraw() {
        CardGame game = setup(GameRules.DEFLECT_DRAW);
        game.topCard = card("draw2", CardColor.RED, -1);
        game.pendingDraw = 2;
        game.lastDrawValue = 2;
        Card reverse = card("reverse", CardColor.RED, -1);
        p(game, 0).hand.add(reverse);
        assertNull(game.whyCannotPlay(p(game, 0), reverse, false));
        assertFalse(game.playCard(p(game, 0), reverse.uid, none()).isFail);
        assertEquals(4, p(game, 2).hand.size());
        assertEquals(0, game.pendingDraw);
    }

    @Test
    public void deflectNeedsTheRule() {
        CardGame game = setup();
        game.topCard = card("draw2", CardColor.RED, -1);
        game.pendingDraw = 2;
        game.lastDrawValue = 2;
        Card reverse = card("reverse", CardColor.RED, -1);
        p(game, 0).hand.add(reverse);
        assertEquals("game.minopp.play.must_stack_or_draw", game.whyCannotPlay(p(game, 0), reverse, false));
    }

    @Test
    public void doublesChainSameNumbers() {
        CardGame game = setup(GameRules.MULTI_PLAY);
        CardPlayer me = p(game, 0);
        Card red5 = num(CardColor.RED, 5), blue5 = num(CardColor.BLUE, 5), green5 = num(CardColor.GREEN, 5);
        me.hand.add(red5);
        me.hand.add(blue5);
        me.hand.add(green5);
        assertFalse(game.playCard(me, red5.uid, none()).isFail);
        assertEquals(CardGame.PlayerActionPhase.CHAIN, game.currentPlayerPhase);
        assertEquals(0, game.currentPlayerIndex);
        assertEquals("game.minopp.play.chain_only", game.whyCannotPlay(me, me.hand.get(0), false));
        assertFalse(game.playCard(me, blue5.uid, none()).isFail);
        assertEquals(CardGame.PlayerActionPhase.CHAIN, game.currentPlayerPhase);
        assertFalse(game.playCard(me, green5.uid, none()).isFail);
        assertEquals(1, game.currentPlayerIndex, "no more 5s: the turn moves on");
        assertEquals(CardColor.GREEN, game.currentColor);
    }

    @Test
    public void doublesChainCanBeEnded() {
        CardGame game = setup(GameRules.MULTI_PLAY);
        CardPlayer me = p(game, 0);
        Card red5 = num(CardColor.RED, 5), blue5 = num(CardColor.BLUE, 5);
        me.hand.add(red5);
        me.hand.add(blue5);
        game.playCard(me, red5.uid, none());
        int before = me.hand.size();
        assertFalse(game.playNoCard(me).isFail);
        assertEquals(before, me.hand.size(), "ending a chain draws nothing");
        assertEquals(1, game.currentPlayerIndex);
    }

    @Test
    public void lastCardMustBeANumber() {
        CardGame game = setup(GameRules.NUMBER_FINISH);
        CardPlayer me = p(game, 0);
        me.hand.clear();
        Card skip = card("skip", CardColor.RED, -1);
        me.hand.add(skip);
        assertEquals("game.minopp.play.number_finish", game.whyCannotPlay(me, skip, false));
    }

    @Test
    public void blasterHitsAndSkipsTheNextPlayer() {
        CardGame game = setup();
        Card blaster = card("blaster", CardColor.RED, -1);
        p(game, 0).hand.add(blaster);
        assertFalse(game.playCard(p(game, 0), blaster.uid, none()).isFail);
        int drawn = p(game, 1).hand.size() - 2;
        assertTrue(drawn >= 0 && drawn <= 6);
        assertEquals(2, game.currentPlayerIndex);
    }

    @Test
    public void giftMovesACardToTheNextPlayer() {
        CardGame game = setup();
        Card gift = card("gift", CardColor.RED, -1);
        p(game, 0).hand.add(gift);
        game.playCard(p(game, 0), gift.uid, none());
        assertEquals(1, p(game, 0).hand.size());
        assertEquals(3, p(game, 1).hand.size());
    }

    @Test
    public void bonusTurnKeepsTheTurn() {
        CardGame game = setup();
        Card bonus = card("bonus_turn", CardColor.RED, -1);
        p(game, 0).hand.add(bonus);
        game.playCard(p(game, 0), bonus.uid, none());
        assertEquals(0, game.currentPlayerIndex);
    }

    @Test
    public void cycloneKeepsEveryHandSize() {
        CardGame game = setup();
        Card cyclone = card("wild_cyclone", CardColor.WILD, -1);
        p(game, 0).hand.add(cyclone);
        game.playCard(p(game, 0), cyclone.uid, PlayContext.PlayChoices.of(CardColor.RED));
        for (CardPlayer pl : game.players) assertEquals(2, pl.hand.size());
    }

    @Test
    public void thiefTakesACard() {
        CardGame game = setup();
        Card thief = card("wild_thief", CardColor.WILD, -1);
        p(game, 0).hand.add(thief);
        PlayContext.PlayChoices choices = PlayContext.PlayChoices.of(CardColor.RED);
        choices.targets.add(p(game, 2).uuid);
        game.playCard(p(game, 0), thief.uid, choices);
        assertEquals(3, p(game, 0).hand.size());
        assertEquals(1, p(game, 2).hand.size());
    }

    @Test
    public void officialHandPoints() {
        CardGame game = setup();
        CardPlayer loser = p(game, 1);
        loser.hand.clear();
        loser.hand.add(num(CardColor.RED, 7));
        loser.hand.add(card("skip", CardColor.BLUE, -1));
        loser.hand.add(card("wild", CardColor.WILD, -1));
        assertEquals(77, game.handPoints(List.of(loser)));
    }

    private Card play(CardGame game, int who, String type, PlayContext.PlayChoices choices) {
        Card c = card(type, type.startsWith("wild") ? CardColor.WILD : CardColor.RED, -1);
        p(game, who).hand.add(c);
        assertFalse(game.playCard(p(game, who), c.uid, choices).isFail, type);
        return c;
    }

    private static PlayContext.PlayChoices red(CardPlayer... targets) {
        PlayContext.PlayChoices c = PlayContext.PlayChoices.of(CardColor.RED);
        for (CardPlayer t : targets) c.targets.add(t.uuid);
        return c;
    }

    @Test
    public void shieldBlocksAPenalty() {
        CardGame game = setup();
        game.topCard = card("draw2", CardColor.RED, -1);
        game.pendingDraw = 6;
        game.lastDrawValue = 2;
        play(game, 0, "wild_shield", red());
        assertEquals(0, game.pendingDraw);
        assertEquals(2, p(game, 1).hand.size());
    }

    @Test
    public void echoCopiesTheCardBelow() {
        CardGame game = setup();
        game.topCard = card("skip", CardColor.RED, -1);
        play(game, 0, "wild_echo", red());
        assertEquals(2, game.currentPlayerIndex, "the copied Skip skipped P1");
    }

    @Test
    public void crossfadeSwapsAcrossTheTable() {
        CardGame game = setupN(4);
        Card marker = num(CardColor.GREEN, 1);
        p(game, 0).hand.add(marker);
        play(game, 0, "wild_crossfade", red());
        assertTrue(p(game, 2).hand.contains(marker));
    }

    @Test
    public void evenOutLevelsHands() {
        CardGame game = setup();
        p(game, 1).hand.add(num(CardColor.BLUE, 1));
        p(game, 1).hand.add(num(CardColor.BLUE, 2));
        play(game, 0, "wild_even_out", red());
        for (CardPlayer pl : game.players) assertEquals(2, pl.hand.size());
    }

    @Test
    public void boobyTrapAddsThreeToTheNextDraw() {
        CardGame game = setup();
        play(game, 0, "wild_booby_trap", red());
        game.playNoCard(p(game, 1));
        assertEquals(2 + 1 + 3, p(game, 1).hand.size());
        assertFalse(game.trapArmed);
    }

    @Test
    public void tricksterNetsTwoCards() {
        CardGame game = setup();
        play(game, 0, "wild_trickster", red(p(game, 2)));
        assertEquals(4, p(game, 2).hand.size());
    }

    @Test
    public void surgeDiscardsTheTwoMostValuableCards() {
        CardGame game = setup();
        CardPlayer me = p(game, 0);
        Card wild = card("wild", CardColor.WILD, -1), skip = card("skip", CardColor.BLUE, -1);
        me.hand.add(wild);
        me.hand.add(skip);
        play(game, 0, "wild_surge", red());
        assertFalse(me.hand.contains(wild));
        assertFalse(me.hand.contains(skip));
        assertEquals(2, me.hand.size());
    }

    private CardGame teamGame(String... rules) {
        CardGame game = setupN(4, rules);
        TableConfig c = (TableConfig) game.detachedConfig;
        c.set(GameRules.TEAM_MODE, 1);
        for (int i = 0; i < 4; i++) p(game, i).team = i % 2;
        return game;
    }

    @Test
    public void teamPlayHandsTheTurnToTheTeammate() {
        CardGame game = teamGame();
        play(game, 0, "wild_team_play", red());
        assertEquals(2, game.currentPlayerIndex);
    }

    @Test
    public void teamDrawHitsOnlyOpponents() {
        CardGame game = teamGame();
        play(game, 0, "wild_team_draw4", red());
        assertEquals(4, p(game, 1).hand.size());
        assertEquals(4, p(game, 3).hand.size());
        assertEquals(2, p(game, 2).hand.size());
        assertEquals(2, game.currentPlayerIndex, "the next opponent lost the turn");
    }

    @Test
    public void relayTakesHalfOfThePartnersCards() {
        CardGame game = teamGame(GameRules.TEAM_RELAY);
        CardPlayer me = p(game, 0), partner = p(game, 2);
        me.hand.clear();
        Card last = num(CardColor.RED, 4);
        me.hand.add(last);
        partner.hand.add(num(CardColor.GREEN, 1));
        partner.hand.add(num(CardColor.GREEN, 2));
        ActionReport r = game.playCard(me, last.uid, none());
        assertFalse(r.shouldDestroyGame, "the game goes on");
        assertEquals(2, me.hand.size());
        assertEquals(2, partner.hand.size());
    }

    @Test
    public void fiftyFiftyHitsOneOfTheTwoChosen() {
        CardGame game = setupN(4);
        play(game, 0, "wild_fifty_fifty", red(p(game, 1), p(game, 3)));
        int a = p(game, 1).hand.size(), b = p(game, 3).hand.size();
        assertTrue(a == 6 && b == 2 || a == 2 && b == 6);
        assertEquals(2, p(game, 2).hand.size());
    }

    @Test
    public void officialOpeningCards() {
        CardGame game = setupN(4);
        game.currentPlayerIndex = 0;
        game.applyOpening(new CardFace("skip", CardColor.RED, -1));
        assertEquals(1, game.currentPlayerIndex, "Skip: the first player is skipped");

        game.currentPlayerIndex = 0;
        game.applyOpening(new CardFace("draw2", CardColor.RED, -1));
        assertEquals(4, p(game, 0).hand.size(), "Draw Two: the first player draws 2");
        assertEquals(1, game.currentPlayerIndex);

        game.currentPlayerIndex = 0;
        game.isAntiClockwise = false;
        game.applyOpening(new CardFace("reverse", CardColor.RED, -1));
        assertTrue(game.isAntiClockwise);

        game.applyOpening(new CardFace("wild", CardColor.WILD, -1));
        assertEquals(CardColor.WILD, game.currentColor, "Wild: the first player may play any color");
    }

    @Test
    public void openingNeverStartsOnWildDrawFour() {
        for (int seed = 0; seed < 300; seed++) {
            List<CardPlayer> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) players.add(new CardPlayer(UUID.randomUUID(), "P" + i));
            CardGame game = new CardGame(players);
            game.detachedConfig = new TableConfig();
            game.initiate(players.get(0), 7);
            CardType top = game.topFace()
                .type();
            assertTrue(
                top == CardTypes.NUMBER || top == CardTypes.SKIP
                    || top == CardTypes.REVERSE
                    || top == CardTypes.DRAW2
                    || top == CardTypes.WILD,
                top.id);
        }
    }

    @Test
    public void successfulChallengeCanEndTheGame() {
        CardGame game = setup(GameRules.MERCY, GameRules.CHALLENGE);
        ((TableConfig) game.detachedConfig).set(GameRules.MERCY_LIMIT, 10);
        CardPlayer bluffer = p(game, 0), victim = p(game, 1);
        game.players.get(2).eliminated = true;
        game.players.get(2).hand.clear();
        for (int i = 0; i < 6; i++) bluffer.hand.add(num(CardColor.RED, i));
        Card draw4 = card("wild_draw4", CardColor.WILD, -1);
        bluffer.hand.add(draw4);
        assertFalse(game.playCard(bluffer, draw4.uid, red()).isFail);
        assertEquals(1, game.decisions.size(), "the victim is asked to challenge");
        ActionReport r = game.answerDecision(victim, 1, null);
        assertTrue(bluffer.eliminated, "the bluffer drew 4 and hit the mercy limit");
        assertTrue(r.shouldDestroyGame, "only the victim is left: the game ends");
    }
}
