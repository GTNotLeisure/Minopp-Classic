package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

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
        if (target == null || target.equals(player)
            || target.eliminated
            || target.hand.isEmpty()
            || player.hand.isEmpty()) return;
        NBTTagCompound data = new NBTTagCompound();
        NbtUtil.putUUID(data, "target", target.uuid);
        open(game, player, SWAP, data);
    }

    public static void discard(CardGame game, CardPlayer player, int count) {
        count = Math.min(count, player.hand.size());
        if (count <= 0) return;
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("count", count);
        open(game, player, DISCARD, data);
    }

    private static void open(CardGame game, CardPlayer player, String id, NBTTagCompound data) {
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
        List<Card> source = sourceCards(game, decision);
        IntSet picked = new IntOpenHashSet(decision.data.getIntArray("picked"));
        List<Card> cards = new ArrayList<>(source.size());
        for (Card card : source) if (!picked.contains(card.uid)) cards.add(card);
        cards.sort((a, b) -> a.compareTo(b, game.darkSide));
        return cards;
    }

    private static class Handler implements DecisionTypes.Handler {

        @Override
        public List<DecisionTypes.Option> options(CardGame game, PendingDecision decision) {
            return List.of(new DecisionTypes.Option(0, "gui.minopp.choice.confirm"));
        }

        @Override
        public boolean answer(CardGame game, PendingDecision decision, int answer, UUID target) {
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
            if (DISCARD.equals(decision.typeId)) {
                IntList picked = new IntArrayList(decision.data.getIntArray("picked"));
                picked.add(answer);
                decision.data.setIntArray("picked", picked.toIntArray());
                if (picked.size() < count(decision)) return true;
            }
            decision.answer = answer;
            return true;
        }

        @Override
        public void answerAutomatically(CardGame game, PendingDecision decision) {
            while (!decision.isAnswered()) {
                List<Card> available = cards(game, decision);
                if (available.isEmpty()) {
                    decision.answer = 0;
                    break;
                }
                Comparator<Card> order = Comparator.comparingInt(game::cardPoints);
                boolean take = RECYCLE.equals(decision.typeId)
                    || SWAP.equals(decision.typeId) && decision.data.hasKey("offered");
                available.sort(take ? order : order.reversed());
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
                    game.handMoveKind = 1;
                    game.handMoveCounter++;
                    report.messageAll(Chat.translatable("game.minopp.play.swap_one", player.name, target.name));
                } else {
                    int removed = 0;
                    for (int uid : decision.data.getIntArray("picked")) {
                        Card card = find(player.hand, uid);
                        if (card == null) continue;
                        player.hand.remove(card);
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
