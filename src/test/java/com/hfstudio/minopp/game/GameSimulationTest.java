package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class GameSimulationTest {

    private static final int MAX_ACTIONS = 6000;

    private static List<CardPlayer> makePlayers(int n) {
        List<CardPlayer> players = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CardPlayer p = new CardPlayer(UUID.randomUUID(), "Bot" + i);
            p.seat = i;
            players.add(p);
        }
        return players;
    }

    private static int totalCards(CardGame game) {
        int total = game.deck.size() + game.discardDeck.size() + 1;
        for (CardPlayer p : game.players) total += p.hand.size();
        return total;
    }

    private static Set<Integer> uids(CardGame game) {
        Set<Integer> set = new HashSet<>();
        List<Card> all = new ArrayList<>(game.deck);
        all.addAll(game.discardDeck);
        all.add(game.topCard);
        for (CardPlayer p : game.players) all.addAll(p.hand);
        for (Card c : all) {
            if (!set.add(c.uid)) fail("Duplicated card " + c);
        }
        return set;
    }

    private static boolean playOut(TableConfig config, int playerCount, long seed) {
        CardGame game = new CardGame(makePlayers(playerCount));
        game.detachedConfig = config;
        game.initiate(game.players.get(0), config.getInt(GameRules.INITIAL_HAND));
        int expected = totalCards(game);
        uids(game);
        AutoPlayer ai = new AutoPlayer();
        ai.aiForgetChance = 0.3f;
        for (int step = 0; step < MAX_ACTIONS; step++) {
            if (game.awaitingCutIn) {
                boolean cut = false;
                if (step % 3 == 0) {
                    for (CardPlayer p : game.players) {
                        if (p.eliminated || p.equals(game.currentPlayer())) continue;
                        Card same = p.hand.stream()
                            .filter(game::isIdenticalToTop)
                            .findFirst()
                            .orElse(null);
                        if (same != null) {
                            ActionReport r = game.playCard(p, same.uid, PlayContext.PlayChoices.of(null));
                            if (r.shouldDestroyGame) return true;
                            cut = !r.isFail;
                            break;
                        }
                    }
                }
                if (!cut) {
                    game.awaitingCutIn = false;
                    ActionReport r = game.resolveStuckPenalty();
                    if (r.shouldDestroyGame) return true;
                }
                continue;
            }
            if (!game.decisions.isEmpty()) {
                ActionReport r = game.tickDecisions(Long.MAX_VALUE, u -> false);
                if (r != null && r.shouldDestroyGame) return true;
                continue;
            }
            CardPlayer current = game.currentPlayer();
            assertTrue(!current.eliminated, "Eliminated player has the turn");
            ActionReport report = ai.playAtGame(game, current, null);
            if (report.shouldDestroyGame) return true;
            assertEquals(
                expected,
                totalCards(game),
                () -> "Card count changed after " + report.state.message()
                    .getUnformattedText());
            uids(game);
            if (step % 50 == 0) {
                CardGame copy = new CardGame(game.toTag());
                assertEquals(expected, totalCards(copy));
            }
        }
        return false;
    }

    private static void runMany(String name, TableConfig config, int players, int games) {
        int finished = 0;
        for (int i = 0; i < games; i++) {
            if (playOut(config, players, i)) finished++;
        }
        System.out.println(name + " (" + players + " players): " + finished + "/" + games + " games finished");
        assertTrue(finished >= games * 0.9, name + ": too many games did not finish (" + finished + "/" + games + ")");
    }

    @Test
    public void classicOriginalRules() {
        runMany("classic", new TableConfig(), 4, 200);
    }

    @Test
    public void classicAllHouseRules() {
        TableConfig c = new TableConfig();
        for (String r : new String[] { GameRules.SEVEN_ZERO, GameRules.DRAW_TO_MATCH, GameRules.FORCED_PLAY,
            GameRules.CHALLENGE, GameRules.PLAY_DRAWN, GameRules.STACK_DRAW4_ON_DRAW2 }) c.set(r, 1);
        runMany("house rules", c, 4, 200);
        c.set(GameRules.NO_BLUFFING, 1);
        c.set(GameRules.TEAM_MODE, 1);
        runMany("2v2 no bluff", c, 4, 200);
        c.set(GameRules.TEAM_COUNT, 3);
        runMany("3 teams of 2", c, 6, 100);
    }

    @Test
    public void noMercy() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.NO_MERCY);
        runMany("no mercy", c, 4, 200);
    }

    @Test
    public void flip() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.FLIP);
        runMany("flip", c, 4, 200);
    }

    @Test
    public void flex() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.FLEX);
        c.set(GameRules.CHALLENGE, 1);
        runMany("flex", c, 4, 200);
    }

    @Test
    public void allWild() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.ALL_WILD);
        runMany("all wild", c, 4, 200);
    }

    @Test
    public void partyTwentyPlayers() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.PARTY);
        c.set(GameRules.MAX_PLAYERS, 20);
        runMany("party", c, 20, 60);
    }

    @Test
    public void everythingMixed() {
        TableConfig c = new TableConfig();
        for (CardPack pack : CardPack.all()) {
            if (!pack.backSideOnly) c.setPackEnabled(pack.id, true);
        }
        c.set(GameRules.MAX_PLAYERS, 20);
        c.set(GameRules.CHALLENGE, 1);
        c.set(GameRules.SEVEN_ZERO, 1);
        runMany("everything", c, 8, 150);
        runMany("everything", c, 20, 40);
    }

    @Test
    public void mixedSingleCards() {
        TableConfig c = new TableConfig();
        c.setCount("no_mercy:discard_all", 2);
        c.setCount("no_mercy:wild_draw6", 4);
        c.setCount("weekend:wild_time_balloon", 3);
        c.setCount("hero:wild_rebound", 4);
        assertTrue(c.countDeckSize() > 108);
        runMany("mixed", c, 5, 200);
    }

    @Test
    public void handMaskHidesOthers() {
        TableConfig c = new TableConfig();
        c.resetToPreset(CardPack.FLIP);
        CardGame game = new CardGame(makePlayers(4));
        game.detachedConfig = c;
        game.initiate(game.players.get(0), 7);
        net.minecraft.nbt.NBTTagCompound table = new net.minecraft.nbt.NBTTagCompound();
        table.setTag("game", game.toTag());
        UUID viewer = game.players.get(1).uuid;
        CardGame seen = new CardGame(
            HandMask.maskTableTag(table, viewer)
                .getCompoundTag("game"));
        for (int p = 0; p < game.players.size(); p++) {
            CardPlayer real = game.players.get(p), masked = seen.players.get(p);
            assertEquals(real.hand.size(), masked.hand.size());
            for (int k = 0; k < real.hand.size(); k++) {
                Card a = real.hand.get(k), b = masked.hand.get(k);
                assertEquals(a.uid, b.uid);
                if (real.uuid.equals(viewer)) assertEquals(a.light, b.light);
                else assertTrue(b.light.isHidden());
                assertEquals(a.dark, b.dark);
            }
        }
        assertEquals(game.deck.size(), seen.deck.size());
        assertTrue(
            seen.deck.stream()
                .allMatch(card -> card.light.isHidden()));
        assertEquals(game.topCard.light, seen.topCard.light);
        assertEquals(
            game.players.get(0).hand.get(0).light,
            new CardGame(table.getCompoundTag("game")).players.get(0).hand.get(0).light);
    }
}
