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

public class InfinityTest {

    private int nextUid = 70_000;

    private Card card(String type, CardColor color, int number) {
        return new Card(nextUid++, new CardFace(type, color, number), null);
    }

    private Card num(CardColor color, int number) {
        return card("number", color, number);
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
        for (int i = 0; i < 200; i++) game.deck.add(num(CardColor.YELLOW, i % 10));
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
    public void minusOneDiscardsThePickedCard() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card keep = num(CardColor.GREEN, 1);
        Card drop = num(CardColor.GREEN, 2);
        me.hand.add(keep);
        me.hand.add(drop);
        Card minus = card("minus_one", CardColor.RED, -1);
        me.hand.add(minus);
        PlayContext.PlayChoices ch = new PlayContext.PlayChoices();
        ch.picks.add(drop.uid);
        assertFalse(game.playCard(me, minus.uid, ch).isFail);
        assertFalse(me.hand.contains(drop));
        assertTrue(me.hand.contains(keep));
        assertTrue(game.discardDeck.contains(drop));
    }

    @Test
    public void missingPicksFallBackToTheHighestCards() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card wild = card("wild", CardColor.WILD, -1);
        me.hand.add(wild);
        Card minus2 = card("wild_minus_two", CardColor.WILD, -1);
        me.hand.add(minus2);
        int before = me.hand.size();
        assertFalse(game.playCard(me, minus2.uid, color(CardColor.BLUE)).isFail);
        assertEquals(before - 3, me.hand.size());
        assertFalse(me.hand.contains(wild), "the 50 point wild goes first");
    }

    @Test
    public void halveDiscardsHalfTheHandRoundedDown() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        for (int i = 1; i <= 5; i++) me.hand.add(num(CardColor.GREEN, i));
        Card halve = card("wild_halve", CardColor.WILD, -1);
        me.hand.add(halve);
        assertFalse(game.playCard(me, halve.uid, color(CardColor.BLUE)).isFail);
        assertEquals(4, me.hand.size());
    }

    @Test
    public void numberSignMatchesTheNamedNumber() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card sign = card("number_sign", CardColor.RED, -1);
        me.hand.add(sign);
        PlayContext.PlayChoices ch = new PlayContext.PlayChoices();
        ch.number = 8;
        assertFalse(game.playCard(me, sign.uid, ch).isFail);
        assertEquals(8, game.signNumber);
        CardPlayer next = game.currentPlayer();
        Card blue8 = next.hand.stream()
            .filter(c -> c.light.number == 8)
            .findFirst()
            .orElse(null);
        assertNotNull(blue8);
        assertNull(game.checkPlayable(next, blue8, false), "a blue 8 matches the red Number Sign that named 8");
        assertFalse(game.playCard(next, blue8.uid, new PlayContext.PlayChoices()).isFail);
        assertEquals(-1, game.signNumber);
    }

    @Test
    public void colorlessOnlyMatchesItsNumberOrColorless() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card grey3 = card("colorless_number", CardColor.COLORLESS, 3);
        Card grey5 = card("colorless_number", CardColor.COLORLESS, 5);
        me.hand.add(grey3);
        me.hand.add(grey5);
        assertNull(game.checkPlayable(me, grey3, false), "same number as the red 3");
        assertNotNull(game.checkPlayable(me, grey5, false));
        assertFalse(game.playCard(me, grey3.uid, new PlayContext.PlayChoices()).isFail);
        assertEquals(CardColor.COLORLESS, game.currentColor);
        CardPlayer next = game.currentPlayer();
        Card blue9 = next.hand.get(0);
        assertNotNull(game.checkPlayable(next, blue9, false));
    }

    @Test
    public void jokerLetsAnyColorFollow() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card joker = card("joker", CardColor.WILD, -1);
        me.hand.add(joker);
        assertFalse(game.playCard(me, joker.uid, new PlayContext.PlayChoices()).isFail);
        CardPlayer next = game.currentPlayer();
        assertNull(game.checkPlayable(next, next.hand.get(0), false));
    }

    @Test
    public void diceDrawTheRollAndSkip() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        CardPlayer victim = game.players.get(1);
        Card d20 = card("wild_d20", CardColor.WILD, -1);
        me.hand.add(d20);
        int before = victim.hand.size();
        assertFalse(game.playCard(me, d20.uid, color(CardColor.BLUE)).isFail);
        assertEquals(20, game.diceSides);
        assertTrue(game.diceResult >= 1 && game.diceResult <= 20);
        assertEquals(before + game.diceResult, victim.hand.size());
        assertEquals(victim.uuid, game.diceRoller);
        assertEquals(game.players.get(2), game.currentPlayer());
    }

    @Test
    public void timesTwoDoublesTheNextHand() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        CardPlayer victim = game.players.get(1);
        Card x2 = card("wild_times_two", CardColor.WILD, -1);
        me.hand.add(x2);
        int before = victim.hand.size();
        assertFalse(game.playCard(me, x2.uid, color(CardColor.BLUE)).isFail);
        assertEquals(before * 2, victim.hand.size());
    }

    @Test
    public void fastCardsOfferAnotherPlayOrAPass() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        Card fast = card("fast_number", CardColor.RED, 4);
        me.hand.add(fast);
        assertFalse(game.playCard(me, fast.uid, new PlayContext.PlayChoices()).isFail);
        assertEquals(me, game.currentPlayer());
        assertEquals(CardGame.PlayerActionPhase.FAST, game.currentPlayerPhase);
        assertFalse(game.playNoCard(me).isFail);
        assertEquals(game.players.get(1), game.currentPlayer());
    }

    @Test
    public void revealOneShowsOnlyThatHand() {
        CardGame game = setup(3);
        CardPlayer me = game.players.get(0);
        CardPlayer target = game.players.get(2);
        Card eye = card("wild_reveal_one", CardColor.WILD, -1);
        me.hand.add(eye);
        PlayContext.PlayChoices ch = color(CardColor.BLUE);
        ch.targets.add(target.uuid);
        assertFalse(game.playCard(me, eye.uid, ch).isFail);
        assertEquals(target.uuid, game.revealedOne);
        net.minecraft.nbt.NBTTagCompound table = new net.minecraft.nbt.NBTTagCompound();
        table.setTag("game", game.toTag());
        net.minecraft.nbt.NBTTagCompound masked = HandMask.maskTableTag(table, me.uuid, true);
        net.minecraft.nbt.NBTTagList players = masked.getCompoundTag("game")
            .getTagList("players", 10);
        for (int i = 0; i < players.tagCount(); i++) {
            net.minecraft.nbt.NBTTagCompound p = players.getCompoundTagAt(i);
            String first = p.getTagList("hand", 10)
                .getCompoundTagAt(0)
                .getCompoundTag("l")
                .getString("t");
            boolean hidden = first.equals(HandMask.HIDDEN_TYPE);
            UUID id = com.hfstudio.minopp.util.NbtUtil.getUUID(p, "uuid");
            if (id.equals(target.uuid) || id.equals(me.uuid)) assertFalse(hidden);
            else assertTrue(hidden);
        }
    }

    @Test
    public void infinityPackStaysNearTheWildShareOfTheOtherPacks() {
        TableConfig cfg = new TableConfig();
        cfg.resetToPreset(CardPack.INFINITY);
        List<CardFace> faces = cfg.buildFaces(false);
        long wild = faces.stream()
            .filter(f -> f.type().wild)
            .count();
        assertTrue(wild * 100 / faces.size() <= 14, wild + " of " + faces.size());
        assertEquals(
            10,
            faces.stream()
                .filter(f -> f.color == CardColor.COLORLESS)
                .count());
    }
}
