package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.effect.GrantRewardEffectEvent;
import com.hfstudio.minopp.effect.PlayerFireworkEffectEvent;
import com.hfstudio.minopp.effect.PlayerGlowEffectEvent;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

public class CardGame {

    private static final ResourceLocation NOTE_BASS = new ResourceLocation("minecraft", "note.bass");

    public List<CardPlayer> players;
    public int currentPlayerIndex;

    public int drawCount;
    public boolean isSkipping;
    public PlayerActionPhase currentPlayerPhase;

    public BlockEntityMinoTable tableEntity;

    public boolean anyOtherPlayerCanCutIn() {
        for (int i = 0; i < players.size(); i++) {
            if (i == currentPlayerIndex) continue;
            CardPlayer other = players.get(i);
            if (tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true) && other.hand.stream()
                .anyMatch(c -> c.getEquivFamily() == topCard.getEquivFamily()
                    && c.getEquivSuit() == topCard.getEquivSuit()
                    && c.number == topCard.number)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAntiClockwise;
    public boolean awaitingCutIn;

    public UUID lastSwapFromUuid;
    public UUID lastSwapToUuid;

    public List<Card> deck = new ArrayList<>();
    public List<Card> discardDeck = new ArrayList<>();
    public Card topCard;

    private void cardsPermute(int[] indexMap, ActionReport report) {
        List<List<Card>> oldHands = new ArrayList<>();
        for (CardPlayer p : players) {
            oldHands.add(new ArrayList<>(p.hand));
            p.hand.clear();
        }
        for (int i = 0; i < players.size(); i++) {
            players.get(indexMap[i]).hand.addAll(oldHands.get(i));
        }
    }

    private void cardsRotate(int steps, ActionReport report) {
        int size = players.size();
        if (size <= 1) return;
        int[] indexMap = new int[size];
        for (int i = 0; i < size; i++) {
            indexMap[i] = isAntiClockwise ? (i - steps + size) % size : (i + steps) % size;
        }
        cardsPermute(indexMap, report);
    }

    public void swapHands(CardPlayer initiator, CardPlayer target) {
        int a = players.indexOf(initiator);
        int b = players.indexOf(target);
        if (a == -1 || b == -1) return;

        int size = players.size();
        int[] indexMap = new int[size];
        for (int i = 0; i < size; i++) indexMap[i] = i;
        indexMap[a] = b;
        indexMap[b] = a;
        cardsPermute(indexMap, null);
        lastSwapFromUuid = initiator.uuid;
        lastSwapToUuid = target.uuid;
    }

    public CardGame(List<CardPlayer> players) {
        this.players = players;
    }

    public ActionReport initiate(CardPlayer cardPlayer, int initialCardCount) {
        if (players.size() < 2) return ActionReport.NO_GAME;
        currentPlayerIndex = new Random().nextInt(players.size());
        drawCount = 0;
        isSkipping = false;
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        isAntiClockwise = false;
        deck = Card.createDeck();
        Collections.shuffle(deck);
        for (int i = 0; i < initialCardCount; i++) {
            for (CardPlayer player : players) {
                player.hand.add(deck.remove(deck.size() - 1));
            }
        }
        Card tobeTopCard = deck.remove(deck.size() - 1);
        while (tobeTopCard.family != Card.Family.NUMBER || tobeTopCard.suit == Card.Suit.WILD) {
            deck.add(tobeTopCard);
            Collections.shuffle(deck);
            tobeTopCard = deck.remove(deck.size() - 1);
        }
        topCard = tobeTopCard;
        return ActionReport.builder(this, cardPlayer)
            .sound(Mino.id("game.play"), 0)
            .sound(NOTE_BASS, 500, players.get(currentPlayerIndex))
            .gameStarted();
    }

    public ActionReport playCard(CardPlayer cardPlayer, Card card, Card.Suit wildSelection, boolean shout) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        int playerIndex = players.indexOf(cardPlayer);

        // early check for jump in to stop players playing during the cut-in window
        if (awaitingCutIn && !(topCard.equals(card) && tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true))) {
            return report.fail(Chat.translatable("game.minopp.play.not_match_cut"));
        }
        // game start check
        if (playerIndex == -1) return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (!cardPlayer.hand.contains(card)) return report.fail(Chat.translatable("game.minopp.play.not_your_card"));
        boolean isCut = false;

        // cut in
        if (awaitingCutIn && playerIndex != currentPlayerIndex) {
            if (!tableEntity.getRule(BlockEntityMinoTable.RULE_JUMP_IN, true)) {
                return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
            }
            isCut = true;
        } else {
            if (awaitingCutIn || playerIndex != currentPlayerIndex)
                return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
        }
        // If there's a draw penalty, player must stack with matching draw card
        if (drawCount > 0) {
            boolean isMatchingDraw = card.family == Card.Family.DRAW && card.number == topCard.number;
            if (!tableEntity.getRule(BlockEntityMinoTable.RULE_STACKING, true) || !isMatchingDraw) {
                return report.fail(Chat.translatable("game.minopp.play.must_stack_or_draw"));
            }
        }
        if (!card.canPlayOn(topCard)) return report.fail(Chat.translatable("game.minopp.play.invalid_card"));

        if (isCut) currentPlayerIndex = playerIndex;
        doDiscardCard(cardPlayer, card, report);
        if (cardPlayer.hand.isEmpty()) {
            report.effect(new PlayerGlowEffectEvent(cardPlayer.uuid, 6 * 20));
            report.effect(new GrantRewardEffectEvent(cardPlayer.uuid));
            for (int i = 0; i < 5; i++) {
                report.effect(new PlayerFireworkEffectEvent(i * 1000 + 500, cardPlayer.uuid));
            }
            return report.gameWon();
        }
        if (card.suit == Card.Suit.WILD) {
            topCard = topCard.withEquivSuit(wildSelection);
        }
        switch (card.family) {
            case SKIP -> isSkipping = true;
            case REVERSE -> {
                if (players.size() == 2) {
                    isSkipping = true;
                } else {
                    isAntiClockwise = !isAntiClockwise;
                }
            }
            case DRAW -> drawCount -= card.number;
            default -> {}
        }
        if (shout) {
            report.combineWith(shoutMino(cardPlayer));
        }

        if (card.number == 0 && !cardPlayer.hand.isEmpty()) {
            if (tableEntity.getRule(BlockEntityMinoTable.RULE_SEVEN0, false)) {
                for (CardPlayer p : players) {
                    report.sound(Mino.id("game.hand_change"), 500, p);
                }
                cardsRotate(1, report);
            }
        }

        if (card.number == 7 && !cardPlayer.hand.isEmpty()) {
            if (tableEntity.getRule(BlockEntityMinoTable.RULE_SEVEN0, false)) {
                for (CardPlayer p : players) {
                    report.sound(Mino.id("game.hand_change"), 500, p);
                }
            }
        }
        advanceTurn(report);
        return isCut ? report.cut() : report.played();
    }

    public ActionReport playNoCard(CardPlayer cardPlayer) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        int playerIndex = players.indexOf(cardPlayer);
        if (playerIndex == -1) return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (playerIndex != currentPlayerIndex || awaitingCutIn)
            return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));

        if (currentPlayerPhase == PlayerActionPhase.DISCARD_HAND) {
            int drawCount = this.drawCount == 0 ? 1 : this.drawCount;
            if (!doDrawCard(cardPlayer, drawCount, report)) {
                return report.panic(Chat.translatable("game.minopp.play.deck_depleted"));
            }
            if (this.drawCount > 0) {
                this.topCard = topCard.withEquivFamily(Card.Family.NUMBER);
                this.drawCount = 0;
            }
            report.sound(NOTE_BASS, 500 * (drawCount > 1 ? drawCount + 1 : 1), cardPlayer);
            advanceTurn(report); // immediately advance, no play after draw
            return report.drew(drawCount);
        } else if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN) {
            report.sound(Mino.id("game.pass"), 0);
            advanceTurn(report);
        }

        return report.playedNoCard(false);
    }

    public ActionReport shoutMino(CardPlayer realPlayer) {
        ActionReport report = ActionReport.builder(this, realPlayer);
        if (!realPlayer.hasShoutedMino) {
            if (realPlayer.hand.size() <= 1) {
                realPlayer.hasShoutedMino = true;
                report.sound(Mino.id("game.mino_shout"), 0);
                return report.messageAll(Chat.translatable("game.minopp.play.mino_shout", realPlayer.name));
            } else {
                if (!doDrawCard(realPlayer, 2, report)) {
                    return report.panic(Chat.translatable("game.minopp.play.deck_depleted"));
                }
                realPlayer.hasShoutedMino = true; // Avoid penalty again and again
                report.sound(Mino.id("game.mino_shout"), 0);
                report.sound(Mino.id("game.mino_shout_invalid"), 500);
                return report.messageAll(Chat.translatable("game.minopp.play.mino_shout_invalid", realPlayer.name));
            }
        }
        return null;
    }

    public ActionReport doubtMino(CardPlayer srcPlayer, UUID targetPlayerWithoutHand) {
        ActionReport report = ActionReport.builder(this, srcPlayer);
        CardPlayer targetPlayer = deAmputate(targetPlayerWithoutHand);
        if (targetPlayer == null) return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (players.get(currentPlayerIndex).equals(targetPlayer)) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_playing"));
        } else if (srcPlayer.equals(targetPlayer)) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_self"));
        } else if (targetPlayer.hasShoutedMino) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_shouted"));
        } else if (targetPlayer.hand.size() > 1) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_hand"));
        } else {
            if (!doDrawCard(targetPlayer, 2, report)) {
                return report.panic(Chat.translatable("game.minopp.play.deck_depleted"));
            }
            targetPlayer.hasShoutedMino = true;
            report.sound(Mino.id("game.doubt_success"), 0);
            return report.messageAll(Chat.translatable("game.minopp.play.doubt_success", srcPlayer.name, targetPlayer.name));
        }
    }

    public ActionReport resolveDrawPenalty() {
        if (drawCount <= 0) return null;
        CardPlayer currentPlayer = players.get(currentPlayerIndex);

        // Checks if current top card is a draw card and player has matching one
        boolean canStack = tableEntity.getRule(BlockEntityMinoTable.RULE_STACKING, true)
            && topCard.family == Card.Family.DRAW
            && currentPlayer.hand.stream().anyMatch(c -> c.family == Card.Family.DRAW && c.number == topCard.number);

        if (canStack) return null;

        ActionReport report = ActionReport.builder(this, currentPlayer);
        int penalty = drawCount;
        if (!doDrawCard(currentPlayer, penalty, report)) {
            return report.panic(Chat.translatable("game.minopp.play.deck_depleted"));
        }
        topCard = topCard.withEquivFamily(Card.Family.NUMBER);
        drawCount = 0;
        advanceTurn(report);
        return report;
    }

    public void doDiscardCard(CardPlayer player, Card card, ActionReport report) {
        discardDeck.add(topCard.eraseEquiv());
        topCard = card;
        player.hand.remove(card);
        report.sound(Mino.id("game.play"), 0);
    }

    public boolean doDrawCard(CardPlayer cardPlayer, int drawCount, ActionReport report) {
        if (deck.size() < drawCount) {
            Collections.shuffle(discardDeck);
            deck.addAll(discardDeck);
            discardDeck.clear();
        }
        if (deck.size() < drawCount) {
            return false;
        }
        for (int i = 0; i < drawCount; i++) {
            cardPlayer.hand.add(deck.remove(deck.size() - 1));
            report.sound(Mino.id("game.draw"), 500 * i);
            if (drawCount > 1) {
                report.sound(Mino.id("game.draw"), 500 * i + 200);
            }
        }
        return true;
    }

    public void advanceTurn(ActionReport report) {
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        if (isSkipping) currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
        currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
        if (currentPlayerIndex < 0) currentPlayerIndex += players.size();
        isSkipping = false;

        // trigger for jump-in, if true then it locks for 5 seconds to allow jump-ins
        if (anyOtherPlayerCanCutIn()) {
            awaitingCutIn = true;
            TaskScheduler.Holder.INSTANCE.schedule(100, () -> {
                awaitingCutIn = false;
                tableEntity.sync();
            });
            return;
        }

        CardPlayer currentPlayer = players.get(currentPlayerIndex);
        currentPlayer.hasShoutedMino = false;

        if (topCard.number != 0) {
            report.sound(NOTE_BASS, 500, currentPlayer);
        }

        // Auto-draw if next player has penalty and can't stack
        if (drawCount < 0) {
            boolean canStack = currentPlayer.hand.stream()
                .anyMatch(c -> c.family == Card.Family.DRAW && c.number == topCard.number);
            if (!canStack) {
                int penalty = -drawCount;
                doDrawCard(currentPlayer, penalty, report);
                topCard = topCard.withEquivFamily(Card.Family.NUMBER);
                drawCount = 0;
                // Now advance again since their turn is forfeit
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                if (isSkipping) currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
                currentPlayerIndex = (currentPlayerIndex + (isAntiClockwise ? -1 : 1)) % players.size();
                if (currentPlayerIndex < 0) currentPlayerIndex += players.size();
                isSkipping = false;
                CardPlayer nextPlayer = players.get(currentPlayerIndex);
                nextPlayer.hasShoutedMino = false;
                if (topCard.number != 0) {
                    report.sound(NOTE_BASS, 1000, currentPlayer);
                }
            }
        }
    }

    public CardPlayer deAmputate(CardPlayer playerWithoutHand) {
        return players.stream().filter(p -> p.equals(playerWithoutHand)).findFirst().orElse(null);
    }

    public CardPlayer deAmputate(UUID uuid) {
        return players.stream().filter(p -> p.uuid.equals(uuid)).findFirst().orElse(null);
    }

    public enum PlayerActionPhase {
        DISCARD_HAND,
        DISCARD_DRAWN,
    }

    private static List<Card> readCards(NBTTagList list) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) cards.add(new Card(list.getCompoundTagAt(i)));
        return cards;
    }

    private static NBTTagList writeCards(List<Card> cards) {
        NBTTagList list = new NBTTagList();
        for (Card card : cards) list.appendTag(card.toTag());
        return list;
    }

    public CardGame(NBTTagCompound tag) {
        currentPlayerIndex = tag.getInteger("currentPlayer");
        drawCount = tag.getInteger("drawCount");
        isSkipping = tag.getBoolean("isSkipping");
        awaitingCutIn = tag.getBoolean("awaitingCutIn");
        currentPlayerPhase = PlayerActionPhase.valueOf(tag.getString("currentPlayerPhase"));
        isAntiClockwise = tag.getBoolean("isAntiClockwise");
        deck = readCards(tag.getTagList("deck", 10));
        discardDeck = readCards(tag.getTagList("discardDeck", 10));
        topCard = new Card(tag.getCompoundTag("topCard"));
        players = new ArrayList<>();
        NBTTagList playersTag = tag.getTagList("players", 10);
        for (int i = 0; i < playersTag.tagCount(); i++) players.add(new CardPlayer(playersTag.getCompoundTagAt(i)));
        lastSwapFromUuid = NbtUtil.hasUUID(tag, "lastSwapFrom") ? NbtUtil.getUUID(tag, "lastSwapFrom") : null;
        lastSwapToUuid = NbtUtil.hasUUID(tag, "lastSwapTo") ? NbtUtil.getUUID(tag, "lastSwapTo") : null;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("currentPlayer", currentPlayerIndex);
        tag.setInteger("drawCount", drawCount);
        tag.setBoolean("isSkipping", isSkipping);
        tag.setBoolean("awaitingCutIn", awaitingCutIn);
        tag.setString("currentPlayerPhase", currentPlayerPhase.name());
        tag.setBoolean("isAntiClockwise", isAntiClockwise);
        tag.setTag("deck", writeCards(deck));
        tag.setTag("discardDeck", writeCards(discardDeck));
        tag.setTag("topCard", topCard.toTag());
        NBTTagList playersTag = new NBTTagList();
        for (CardPlayer p : players) playersTag.appendTag(p.toTag());
        tag.setTag("players", playersTag);
        if (lastSwapFromUuid != null) NbtUtil.putUUID(tag, "lastSwapFrom", lastSwapFromUuid);
        if (lastSwapToUuid != null) NbtUtil.putUUID(tag, "lastSwapTo", lastSwapToUuid);
        return tag;
    }
}
