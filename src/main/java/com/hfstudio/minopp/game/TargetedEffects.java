package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

public class TargetedEffects {

    public static final String DECISION = "minopp:respond_effect";
    private static final Map<String, Effect> EFFECTS = new HashMap<>();

    private TargetedEffects() {}

    @FunctionalInterface
    public interface Effect {

        void apply(CardGame game, CardPlayer source, CardPlayer target, NBTTagCompound data, ActionReport report);
    }

    public static void register(String id, Effect effect) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(effect, "effect");
        if (id.isBlank() || EFFECTS.putIfAbsent(id, effect) != null)
            throw new IllegalArgumentException("Invalid or duplicate targeted effect: " + id);
    }

    static {
        DecisionTypes
            .register(new DecisionTypes.Definition(DECISION, "gui.minopp.decision.respond_effect", new Handler()));
        register("draw", (game, source, target, data, report) -> {
            game.drawCards(target, data.getInteger("amount"), report);
            if (data.getBoolean("skip") && !source.equals(target)) game.skipNext(1);
        });
        register("draw_color", (game, source, target, data, report) -> {
            CardColor color = CardColor.byId(data.getString("color"));
            game.drawUntilColor(target, color, report);
            if (!source.equals(target)) game.skipNext(1);
        });
        register("skip", (game, source, target, data, report) -> { if (!source.equals(target)) game.skipNext(1); });
        register(
            "swap",
            (game, source, target, data, report) -> {
                if (!source.equals(target)) game.swapHands(source, target, report);
            });
        register("card_swap", (game, source, target, data, report) -> CardDecisions.swap(game, source, target));
        register("reveal", (game, source, target, data, report) -> game.revealOne(source, target, report));
        register("recycle", (game, source, target, data, report) -> {
            int uid = data.getInteger("card");
            for (int i = 0; i < game.discardDeck.size(); i++) {
                Card card = game.discardDeck.get(i);
                if (card.uid != uid) continue;
                game.discardDeck.remove(i);
                card.owner = target.uuid;
                target.hand.add(card);
                target.hasShoutedMino = false;
                report.messageAll(Chat.translatable("game.minopp.play.recycle", source.name, target.name));
                return;
            }
        });
    }

    public static void init() {}

    public static void draw(CardGame game, CardPlayer source, List<CardPlayer> targets, int amount, boolean skip,
        ActionReport report) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("amount", amount);
        data.setBoolean("skip", skip);
        apply(game, source, targets, "draw", data, report);
    }

    public static void apply(CardGame game, CardPlayer source, List<CardPlayer> targets, String effectId,
        NBTTagCompound data, ActionReport report) {
        Effect effect = EFFECTS.get(effectId);
        if (effect == null) throw new IllegalArgumentException("Unknown targeted effect: " + effectId);
        if (!game.decisions.isEmpty()) throw new IllegalStateException("Another decision is pending");
        NBTTagCompound state = game.extensionData("minopp:targeted_effects");
        NBTTagList pending = state.getTagList("pending", 10);
        Set<UUID> seen = new HashSet<>();
        for (CardPlayer candidate : targets) {
            CardPlayer target = candidate == null ? null : game.deAmputate(candidate.uuid);
            if (target == null || target.eliminated || !seen.add(target.uuid)) continue;
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString("effect", effectId);
            payload.setTag("arguments", data.copy());
            NbtUtil.putUUID(payload, "source", source.uuid);
            NbtUtil.putUUID(payload, "target", target.uuid);
            pending.appendTag(payload);
        }
        state.setTag("pending", pending);
        resume(game, report);
    }

    public static void resume(CardGame game, ActionReport report) {
        NBTTagList pending = game.extensionData("minopp:targeted_effects")
            .getTagList("pending", 10);
        while (pending.tagCount() > 0 && game.decisions.isEmpty() && !report.shouldDestroyGame) {
            NBTTagCompound payload = (NBTTagCompound) pending.removeTag(0);
            CardPlayer source = game.deAmputate(NbtUtil.getUUID(payload, "source"));
            CardPlayer target = game.deAmputate(NbtUtil.getUUID(payload, "target"));
            if (source == null || target == null || target.eliminated) continue;
            if (source.equals(target) || defense(game, target, 1) == null && defense(game, target, 2) == null) {
                EFFECTS.get(payload.getString("effect"))
                    .apply(game, source, target, payload.getCompoundTag("arguments"), report);
            } else {
                game.openDecisions(
                    List.of(
                        new PendingDecision(
                            DECISION,
                            target.uuid,
                            source.uuid,
                            System.currentTimeMillis() + 15_000,
                            payload)));
            }
        }
    }

    private static Card defense(CardGame game, CardPlayer player, int kind) {
        String type = kind == 1 ? "wild_shield" : "wild_infinity_no_u";
        for (Card card : player.hand) if (game.face(card).typeId.equals(type)) return card;
        return null;
    }

    private static class Handler implements DecisionTypes.Handler {

        @Override
        public List<DecisionTypes.Option> options(CardGame game, PendingDecision decision) {
            List<DecisionTypes.Option> options = new ArrayList<>();
            options.add(new DecisionTypes.Option(0, "gui.minopp.decision.accept_effect"));
            CardPlayer player = game.deAmputate(decision.player);
            if (player == null || player.eliminated) return options;
            for (int kind = 1; kind <= 2; kind++) {
                Card card = defense(game, player, kind);
                if (card == null) continue;
                for (CardColor color : CardColor.forSide(game.darkSide)) {
                    String label = game.face(card)
                        .getFaceName()
                        .getUnformattedText() + " / "
                        + Chat.translatable(color.langKey())
                            .getUnformattedText();
                    options.add(new DecisionTypes.Option(kind * 16 + color.ordinal(), label));
                }
            }
            return options;
        }

        @Override
        public boolean answer(CardGame game, PendingDecision decision, int answer, UUID target) {
            if (!DecisionTypes.Handler.super.answer(game, decision, answer, target)) return false;
            if (answer != 0) {
                Card card = defense(game, game.deAmputate(decision.player), answer / 16);
                decision.data.setInteger("defense", card.uid);
            }
            return true;
        }

        @Override
        public void resolve(CardGame game, List<PendingDecision> decisions, ActionReport report) {
            for (PendingDecision decision : decisions) {
                CardPlayer source = game.deAmputate(decision.source), target = game.deAmputate(decision.player);
                if (source == null || target == null || target.eliminated) continue;
                int response = decision.answer;
                if (response != 0) {
                    Card card = defense(game, target, response / 16);
                    if (card == null || card.uid != decision.data.getInteger("defense")) response = 0;
                    else {
                        game.doDiscardCard(target, card, report);
                        game.currentColor = CardColor.values()[response % 16];
                        if (target.hand.size() == 1) target.hasShoutedMino = true;
                        report.messageAll(
                            Chat.translatable(
                                "game.minopp.play.effect_defended",
                                target.name,
                                game.face(card)
                                    .getFaceName()));
                    }
                }
                if (response / 16 == 1) continue;
                CardPlayer recipient = response / 16 == 2 ? source : target;
                if (recipient.eliminated) continue;
                EFFECTS.get(decision.data.getString("effect"))
                    .apply(game, source, recipient, decision.data.getCompoundTag("arguments"), report);
            }
        }
    }
}
