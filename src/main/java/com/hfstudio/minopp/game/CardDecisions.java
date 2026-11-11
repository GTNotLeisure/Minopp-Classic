package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class CardDecisions {

    public static final String RECYCLE = "minopp:recycle";
    public static final String SWAP = "minopp:card_swap";
    public static final String REPLAY = "minopp:replay";
    public static final String DISCARD = "minopp:discard_cards";

    private CardDecisions() {}

    static {
        for (String id : List.of(RECYCLE, SWAP, DISCARD)) DecisionTypes
            .register(new DecisionTypes.Definition(id, "gui.minopp.decision." + id.substring(7), new Handler()));
    }

    static {
        DecisionTypes.register(new DecisionTypes.Definition(REPLAY, "gui.minopp.decision.replay", new ReplayHandler()));
    }

    private static class ReplayHandler implements DecisionTypes.Handler {

        @Override
        public List<DecisionTypes.Option> options(CardGame game, PendingDecision decision) {
            return List.of(
                new DecisionTypes.Option(0, "gui.minopp.decision.replay_again"),
                new DecisionTypes.Option(1, "gui.minopp.decision.replay_reverse"));
        }

        @Override
        public void resolve(CardGame game, List<PendingDecision> decisions, ActionReport report) {
            if (decisions.get(0).answer == 1) game.reverse();
            else game.giveExtraTurn();
        }
    }

    public static void init() {}

    public static void replay(CardGame game, CardPlayer player) {
        open(game, player, REPLAY, new NBTTagCompound());
    }

    public static void recycle(CardGame game, CardPlayer player, CardPlayer target) {
        if (game.discardDeck.isEmpty()) return;
        NBTTagCompound data = new NBTTagCompound();
        if (target != null) NbtUtil.putUUID(data, "target", target.uuid);
        open(game, player, RECYCLE, data);
    }

    public static void swap(CardGame game, CardPlayer player, CardPlayer target) {
        player = game.deAmputate(player);
        target = game.deAmputate(target);
        if (player == null || player.eliminated
            || target == null
            || target.equals(player)
            || target.eliminated
            || target.hand.isEmpty()
            || player.hand.isEmpty()) return;
        NBTTagCompound data = new NBTTagCompound();
        NbtUtil.putUUID(data, "target", target.uuid);
        open(game, player, SWAP, data);
    }

    public static void discard(CardGame game, CardPlayer player, int count) {
        player = game.deAmputate(player);
        if (player == null || player.eliminated) return;
        count = Math.min(count, player.hand.size());
        if (count <= 0) return;
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("count", count);
        open(game, player, DISCARD, data);
    }

    private static void open(CardGame game, CardPlayer player, String id, NBTTagCompound data) {
        player = game.deAmputate(player);
        if (player == null || player.eliminated) return;
        game.openDecisions(
            List.of(new PendingDecision(id, player.uuid, player.uuid, System.currentTimeMillis() + 45_000, data)));
    }

    public static int count(PendingDecision decision) {
        return DISCARD.equals(decision.typeId) ? Math.max(1, decision.data.getInteger("count")) : 1;
    }

    public static UUID revealedPlayer(PendingDecision decision) {
        return SWAP.equals(decision.typeId) ? NbtUtil.getUUID(decision.data, "target") : null;
    }

    private static List<Card> sourceCards(CardGame game, PendingDecision decision) {
        if (RECYCLE.equals(decision.typeId)) return game.discardDeck;
        UUID holder = SWAP.equals(decision.typeId) && decision.data.hasKey("offered")
            ? NbtUtil.getUUID(decision.data, "target")
            : decision.player;
        CardPlayer player = game.deAmputate(holder);
        return player == null || player.eliminated ? List.of() : player.hand;
    }

    public static List<Card> cards(CardGame game, PendingDecision decision) {
        List<Card> cards = unpickedCards(game, decision);
        cards.sort((a, b) -> a.compareTo(b, game.darkSide));
        return cards;
    }

    private static List<Card> unpickedCards(CardGame game, PendingDecision decision) {
        List<Card> source = sourceCards(game, decision);
        int[] previous = decision.data.getIntArray("picked");
        if (previous.length == 0) return new ArrayList<>(source);
        IntSet picked = new IntOpenHashSet(previous);
        List<Card> cards = new ArrayList<>(source.size());
        for (Card card : source) if (!picked.contains(card.uid)) cards.add(card);
        return cards;
    }

    private static class Handler implements DecisionTypes.Handler {

        @Override
        public List<DecisionTypes.Option> options(CardGame game, PendingDecision decision) {
            return List.of(new DecisionTypes.Option(0, "gui.minopp.choice.confirm"));
        }

        @Override
        public boolean answer(CardGame game, PendingDecision decision, int answer, UUID target) {
            if (DISCARD.equals(decision.typeId)) return answerAll(game, decision, new int[] { answer }, target);
            if (find(sourceCards(game, decision), answer) == null) return false;
            for (int picked : decision.data.getIntArray("picked")) if (picked == answer) return false;
            if (RECYCLE.equals(decision.typeId)) {
                CardPlayer recipient = game.deAmputate(target == null ? decision.player : target);
                if (recipient == null || recipient.eliminated) return false;
                decision.answerTarget = recipient.uuid;
            } else if (target != null) return false;
            if (SWAP.equals(decision.typeId) && !decision.data.hasKey("offered")) {
                decision.data.setInteger("offered", answer);
                return true;
            }
            decision.answer = answer;
            return true;
        }

        @Override
        public boolean answerAll(CardGame game, PendingDecision decision, int[] answers, UUID target) {
            if (!DISCARD.equals(decision.typeId))
                return DecisionTypes.Handler.super.answerAll(game, decision, answers, target);
            int[] previous = decision.data.getIntArray("picked");
            if (target != null || answers.length == 0 || answers.length > count(decision) - previous.length)
                return false;
            IntSet available = new IntOpenHashSet();
            for (Card card : sourceCards(game, decision)) available.add(card.uid);
            for (int uid : previous) available.remove(uid);
            for (int uid : answers) if (!available.remove(uid)) return false;
            IntList picked = new IntArrayList(previous.length + answers.length);
            picked.addElements(0, previous);
            picked.addElements(picked.size(), answers);
            decision.data.setIntArray("picked", picked.toIntArray());
            if (picked.size() == count(decision)) decision.answer = answers[answers.length - 1];
            return true;
        }

        @Override
        public void answerAutomatically(CardGame game, PendingDecision decision) {
            if (DISCARD.equals(decision.typeId)) {
                List<Card> available = unpickedCards(game, decision);
                available.sort(
                    Comparator.comparingInt(game::cardPoints)
                        .reversed()
                        .thenComparing((a, b) -> a.compareTo(b, game.darkSide)));
                int remaining = count(decision) - decision.data.getIntArray("picked").length;
                int[] answers = new int[Math.max(0, Math.min(remaining, available.size()))];
                for (int i = 0; i < answers.length; i++) answers[i] = available.get(i).uid;
                if (answers.length > 0) answerAll(game, decision, answers, null);
                if (!decision.isAnswered()) decision.answer = 0;
                return;
            }
            while (!decision.isAnswered()) {
                List<Card> available = unpickedCards(game, decision);
                if (available.isEmpty()) {
                    decision.answer = 0;
                    break;
                }
                Comparator<Card> order = Comparator.comparingInt(game::cardPoints);
                boolean take = RECYCLE.equals(decision.typeId)
                    || SWAP.equals(decision.typeId) && decision.data.hasKey("offered");
                available
                    .sort((take ? order : order.reversed()).thenComparing((a, b) -> a.compareTo(b, game.darkSide)));
                UUID target = RECYCLE.equals(decision.typeId) && NbtUtil.hasUUID(decision.data, "target")
                    ? NbtUtil.getUUID(decision.data, "target")
                    : null;
                if (!answer(game, decision, available.get(0).uid, target)) {
                    decision.answer = 0;
                    break;
                }
            }
        }

        @Override
        public void resolve(CardGame game, List<PendingDecision> decisions, ActionReport report) {
            for (PendingDecision decision : decisions) {
                CardPlayer player = game.deAmputate(decision.player);
                if (player == null || player.eliminated) continue;
                if (RECYCLE.equals(decision.typeId)) {
                    CardPlayer target = game.deAmputate(decision.answerTarget);
                    Card card = find(game.discardDeck, decision.answer);
                    if (card == null || target == null || target.eliminated) continue;
                    NBTTagCompound data = new NBTTagCompound();
                    data.setInteger("card", card.uid);
                    TargetedEffects.apply(game, player, List.of(target), "recycle", data, report);
                } else if (SWAP.equals(decision.typeId)) {
                    CardPlayer target = game.deAmputate(NbtUtil.getUUID(decision.data, "target"));
                    if (target == null || target.eliminated) continue;
                    Card offered = find(player.hand, decision.data.getInteger("offered"));
                    Card taken = find(target.hand, decision.answer);
                    if (offered == null || taken == null) continue;
                    player.hand.remove(offered);
                    target.hand.remove(taken);
                    offered.owner = target.uuid;
                    taken.owner = player.uuid;
                    player.hand.add(taken);
                    target.hand.add(offered);
                    player.hasShoutedMino = false;
                    target.hasShoutedMino = false;
                    game.handMoveKind = 1;
                    game.handMoveCounter++;
                    report.messageAll(Chat.translatable("game.minopp.play.swap_one", player.name, target.name));
                } else {
                    int removed = 0;
                    int[] picked = decision.data.getIntArray("picked");
                    Int2ObjectOpenHashMap<Card> chosen = new Int2ObjectOpenHashMap<>(picked.length);
                    for (int uid : picked) chosen.put(uid, null);
                    player.hand.removeIf(card -> {
                        if (!chosen.containsKey(card.uid)) return false;
                        chosen.put(card.uid, card);
                        return true;
                    });
                    for (int uid : picked) {
                        Card card = chosen.remove(uid);
                        if (card == null) continue;
                        card.owner = player.uuid;
                        game.discardDeck.add(card);
                        removed++;
                    }
                    if (player.hand.size() == 1) player.hasShoutedMino = true;
                    report.messageAll(Chat.translatable("game.minopp.play.discarded", player.name, removed));
                }
            }
        }

        private static Card find(List<Card> cards, int uid) {
            for (Card card : cards) if (card.uid == uid) return card;
            return null;
        }
    }
}
