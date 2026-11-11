package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

public class AutoPlayer {

    public static final int MIN_REACTION_TICKS = 5;

    public boolean aiNoWin = false;
    public boolean aiNoPlayerDraw = false;
    public float aiForgetChance = 0.2f;
    public byte aiNoDelay = 0;
    public boolean aiStartGame = false;

    private static final Random RANDOM = new Random();

    public static boolean isOnlinePlayer(MinecraftServer server, UUID uuid) {
        if (server == null) return false;
        for (EntityPlayerMP o : server.getConfigurationManager().playerEntityList) {
            if (o.getGameProfile()
                .getId()
                .equals(uuid)) return true;
        }
        return false;
    }

    public static PlayContext.PlayChoices defaultChoices(CardGame game, CardPlayer player, Card card) {
        return defaultChoices(game, player, card, false);
    }

    public static PlayContext.PlayChoices defaultChoices(CardGame game, CardPlayer player, Card card, boolean useFlex) {
        BotDifficulty difficulty = BotDifficulty.forPlayer(game, player);
        return choices(
            game,
            player,
            card,
            useFlex,
            difficulty,
            difficulty == BotDifficulty.HIGH ? new BotStrategy(game, player) : null);
    }

    private static PlayContext.PlayChoices choices(CardGame game, CardPlayer player, Card card, boolean useFlex,
        BotDifficulty difficulty, BotStrategy strategy) {
        PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
        choices.useFlex = useFlex;
        choices.color = game.mostCommonColor(player);
        List<CardPlayer> others = game.activePlayers();
        others.remove(player);
        others.removeIf(p -> player.team >= 0 && p.team == player.team && others.size() > 1);
        CardPlayer first = null, second = null;
        for (CardPlayer candidate : others) {
            if (first == null || candidate.hand.size() < first.hand.size()) {
                second = first;
                first = candidate;
            } else if (second == null || candidate.hand.size() < second.hand.size()) {
                second = candidate;
            }
        }
        if (first != null) choices.targets.add(first.uuid);
        if (second != null) choices.targets.add(second.uuid);
        if (difficulty == BotDifficulty.LOW) {
            List<CardColor> colors = game.colors();
            choices.color = colors.get(RANDOM.nextInt(colors.size()));
            choices.number = 1 + RANDOM.nextInt(9);
            Collections.shuffle(others, RANDOM);
            choices.targets.clear();
            for (int i = 0; i < Math.min(2, others.size()); i++) choices.targets.add(others.get(i).uuid);
        } else if (strategy != null) strategy.choose(card, choices);
        CardType.Automation automation = game.face(card)
            .type().automation;
        if (automation != null) automation.choose(game, player, card, choices);
        return choices;
    }

    private int score(CardGame game, Card card, boolean useFlex, int[] colorCounts) {
        CardFace face = game.face(card);
        CardType type = face.type();
        CardFace top = game.topFace();
        int score = 0;
        if (!face.isWild() && face.symbolKey()
            .equals(top.symbolKey()) && face.color != game.currentColor) score += 50;
        if (face.isWild() && !type.isDraw()) score += 40;
        if (face.isNumber() && face.number == 7
            && game.config()
                .getBool(GameRules.SEVEN_ZERO))
            score += 30;
        if (!face.isWild() && face.color == game.currentColor) score += 20;
        if (type.isDraw()) score += 5 + type.drawValue;
        if (type == CardTypes.DISCARD_ALL || type == CardTypes.DOUBLE_DISCARD_ALL) {
            score += 10 * colorCounts[face.color.ordinal()];
        }
        if (useFlex) score += 8;
        return score;
    }

    public ActionReport playAtGame(CardGame game, CardPlayer realPlayer, MinecraftServer server) {
        if (game.awaitingCutIn || !game.decisions.isEmpty()) return game.playNoCard(realPlayer);
        boolean forgetsMino = RANDOM.nextFloat() < aiForgetChance;
        boolean shoutsMino = !forgetsMino && realPlayer.hand.size() <= 2;

        CardPlayer nextPlayer = game.nextPlayer();
        boolean canPlayDrawCard = !aiNoPlayerDraw || !isOnlinePlayer(server, nextPlayer.uuid);

        if (aiNoWin && realPlayer.hand.size() <= 1) {
            return game.playNoCard(realPlayer);
        }

        boolean flexAvailable = game.config()
            .getBool(GameRules.FLEX_POWER) && realPlayer.powerOn;
        Card best = null;
        boolean bestFlex = false;
        int bestScore = Integer.MIN_VALUE;
        int legalMoves = 0;
        BotDifficulty difficulty = BotDifficulty.forPlayer(game, realPlayer);
        BotStrategy strategy = difficulty == BotDifficulty.HIGH ? new BotStrategy(game, realPlayer) : null;
        int[] colorCounts = null;
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            CardType type = game.face(card)
                .type();
            if (!canPlayDrawCard && type.isDraw() && game.pendingDraw == 0) continue;
            for (int f = 0; f < (flexAvailable ? 2 : 1); f++) {
                boolean useFlex = f == 1;
                if (game.checkPlayable(realPlayer, card, useFlex) != null) continue;
                if (difficulty == BotDifficulty.LOW) {
                    if (RANDOM.nextInt(++legalMoves) == 0) {
                        best = card;
                        bestFlex = useFlex;
                    }
                    continue;
                }
                if (colorCounts == null && (type == CardTypes.DISCARD_ALL || type == CardTypes.DOUBLE_DISCARD_ALL)) {
                    colorCounts = new int[CardColor.values().length];
                    for (Card held : realPlayer.hand) colorCounts[game.face(held).color.ordinal()]++;
                }
                int s = score(game, card, useFlex, colorCounts);
                if (strategy != null) s = strategy.score(card, useFlex, s);
                if (type.automation != null) s = type.automation.score(game, realPlayer, card, useFlex, s);
                if (difficulty == BotDifficulty.MEDIUM) s += RANDOM.nextInt(6);
                if (s > bestScore) {
                    bestScore = s;
                    best = card;
                    bestFlex = useFlex;
                }
            }
        }
        if (best != null) {
            PlayContext.PlayChoices choices = choices(game, realPlayer, best, bestFlex, difficulty, strategy);
            choices.useFlex = bestFlex;
            choices.shout = shoutsMino;
            ActionReport result = game.playCard(realPlayer, best.uid, choices);
            if (!result.isFail) return result;
        }

        return game.playNoCard(realPlayer);
    }

    public void useConfigNbt(NBTTagCompound aiConfig) {
        aiNoWin = aiConfig.getBoolean("NoWin");
        aiNoPlayerDraw = aiConfig.getBoolean("NoPlayerDraw");
        aiForgetChance = aiConfig.hasKey("ForgetChance", 5) ? aiConfig.getFloat("ForgetChance") : 0.2f;
        aiNoDelay = aiConfig.getByte("NoDelay");
        aiStartGame = aiConfig.getBoolean("StartGame");
    }

    public NBTTagCompound toConfigNbt() {
        NBTTagCompound aiConfig = new NBTTagCompound();
        aiConfig.setBoolean("NoWin", aiNoWin);
        aiConfig.setBoolean("NoPlayerDraw", aiNoPlayerDraw);
        aiConfig.setFloat("ForgetChance", aiForgetChance);
        aiConfig.setByte("NoDelay", aiNoDelay);
        aiConfig.setBoolean("StartGame", aiStartGame);
        return aiConfig;
    }
}
