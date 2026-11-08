package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;

public class AutoPlayer {

    public boolean aiNoWin = false;
    public boolean aiNoPlayerDraw = false;
    public float aiForgetChance = 0.2f;
    public byte aiNoDelay = 0;
    public boolean aiStartGame = false;

    public static boolean isOnlinePlayer(MinecraftServer server, UUID uuid) {
        if (server == null) return false;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (((EntityPlayerMP) o).getGameProfile().getId().equals(uuid)) return true;
        }
        return false;
    }

    public ActionReport playAtGame(CardGame game, CardPlayer realPlayer, MinecraftServer server) {
        Card topCard = game.topCard;
        boolean forgetsMino = new Random().nextFloat() < aiForgetChance;
        boolean shoutsMino = !forgetsMino && realPlayer.hand.size() <= 2;

        // If the next player is a human player, we should not play Draw cards
        CardPlayer nextPlayer = game.players.get((game.currentPlayerIndex + (game.isAntiClockwise ? -1 : 1) + game.players.size()) % game.players.size());
        boolean canPlayDrawCard = !aiNoPlayerDraw || !isOnlinePlayer(server, nextPlayer.uuid);

        if (aiNoWin) {
            if (realPlayer.hand.size() <= 1) {
                return game.playNoCard(realPlayer);
            }
        }

        // Prioritize switching suit to make things more interesting
        // If we have a card of same number but different suit
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            if (card.number == topCard.number && card.suit != topCard.getEquivSuit() && card.suit != Card.Suit.WILD) {
                if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
                ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                if (!result.isFail) return result;
            }
        }
        // If we have a Wild card
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            if (card.suit == Card.Suit.WILD && card.family != Card.Family.DRAW) {
                ActionReport result = game.playCard(realPlayer, card, getMostCommonSuit(realPlayer), shoutsMino);
                if (!result.isFail) return result;
            }
        }

        // If we have a 7
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            if (card.number == 7 && game.tableEntity.getRule(BlockEntityMinoTable.RULE_SEVEN0, false)) {
                if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
                if (!card.canPlayOn(topCard)) continue;
                ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                if (!result.isFail) {
                    List<CardPlayer> others = new ArrayList<>(game.players);
                    others.remove(realPlayer);
                    if (!others.isEmpty()) {
                        game.swapHands(realPlayer, others.get(new Random().nextInt(others.size())));
                    }
                    return result;
                }
            }
        }

        // If we have a card of same suit
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            if (card.suit == topCard.getEquivSuit() && card.suit != Card.Suit.WILD) {
                if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
                ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                if (!result.isFail) return result;
            }
        }
        // If we have any other card
        for (Card card : new ArrayList<>(realPlayer.hand)) {
            if (!canPlayDrawCard && card.family == Card.Family.DRAW) continue;
            if (card.canPlayOn(topCard)) {
                if (card.suit == Card.Suit.WILD) {
                    ActionReport result = game.playCard(realPlayer, card, getMostCommonSuit(realPlayer), shoutsMino);
                    if (!result.isFail) return result;
                } else {
                    ActionReport result = game.playCard(realPlayer, card, null, shoutsMino);
                    if (!result.isFail) return result;
                }
            }
        }

        // We're out of option
        return game.playNoCard(realPlayer);
    }

    private Card.Suit getMostCommonSuit(CardPlayer realPlayer) {
        int[] suitCount = new int[4];
        for (Card handCard : realPlayer.hand) {
            if (handCard.suit != Card.Suit.WILD) {
                suitCount[handCard.suit.ordinal()]++;
            }
        }
        Card.Suit mostCommonSuit = Card.Suit.values()[new Random().nextInt(4)];
        for (int i = 1; i < 4; i++) {
            if (suitCount[i] > suitCount[mostCommonSuit.ordinal()]) {
                mostCommonSuit = Card.Suit.values()[i];
            }
        }
        return mostCommonSuit;
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
