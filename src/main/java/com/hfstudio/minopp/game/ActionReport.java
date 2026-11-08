package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.effect.EffectEvent;
import com.hfstudio.minopp.effect.SoundEffectEvent;
import com.hfstudio.minopp.util.Chat;

public class ActionReport {

    private CardGame game;
    private CardPlayer initiator;

    public ActionMessage state;
    public List<ActionMessage> messages = new ArrayList<>();
    public List<EffectEvent> effects = new ArrayList<>();

    public boolean shouldDestroyGame = false;
    public boolean isFail = false;
    private int soundTail = 0;

    private ActionReport(CardGame game, CardPlayer player) {
        this.initiator = player;
        this.game = game;
    }

    public static ActionReport builder(CardGame game, CardPlayer player) {
        return new ActionReport(game, player);
    }

    public static ActionReport builder(CardGame game) {
        return builder(game, null);
    }

    public static ActionReport builder(CardPlayer player) {
        return builder(null, player);
    }

    public static ActionReport builder() {
        return builder(null, null);
    }

    public ActionReport state(IChatComponent message) {
        this.state = new ActionMessage(ActionMessage.Type.STATE, message);
        return this;
    }

    public ActionReport fail(IChatComponent message) {
        this.isFail = true;
        this.messages.add(new ActionMessage(ActionMessage.Type.FAIL, message));
        return this;
    }

    public ActionReport messageAll(IChatComponent message) {
        this.messages.add(new ActionMessage(ActionMessage.Type.MESSAGE_ALL, message));
        return this;
    }

    public ActionReport combineWith(ActionReport other) {
        if (other == null) return this;
        if (this.state == null) this.state = other.state;
        this.messages.addAll(other.messages);
        this.shouldDestroyGame |= other.shouldDestroyGame;
        this.effects.addAll(other.effects);
        if (this.winners == null) this.winners = other.winners;
        return this;
    }

    public ActionReport sound(ResourceLocation sound, int timeOffset, CardPlayer target) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.of(target.uuid), sound));
        return this;
    }

    public ActionReport sound(ResourceLocation sound, int timeOffset) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.empty(), sound));
        soundTail = Math.max(soundTail, timeOffset);
        return this;
    }

    public ActionReport sound(ResourceLocation sound, int timeOffset, float volume, float pitch) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.empty(), sound, volume, pitch));
        soundTail = Math.max(soundTail, timeOffset);
        return this;
    }

    public ActionReport sound(ResourceLocation sound, int timeOffset, CardPlayer target, float volume, float pitch) {
        effects.add(new SoundEffectEvent(timeOffset, Optional.of(target.uuid), sound, volume, pitch));
        return this;
    }

    public int soundTail() {
        return soundTail;
    }

    public ActionReport effect(EffectEvent event) {
        effects.add(event);
        return this;
    }

    private IChatComponent topName() {
        return game.topCard.getDisplayName(game.darkSide);
    }

    public ActionReport played() {
        return state(Chat.translatable("game.minopp.play.played", initiator.name, topName()));
    }

    public ActionReport speed(int remaining, int level) {
        return state(Chat.translatable("game.minopp.play.speed", initiator.name, remaining, level));
    }

    public ActionReport bring() {
        return state(Chat.translatable("game.minopp.play.bring", initiator.name));
    }

    public ActionReport chain(int number) {
        return state(Chat.translatable("game.minopp.play.chain", initiator.name, number));
    }

    public ActionReport cut() {
        return state(Chat.translatable("game.minopp.play.cut", initiator.name, topName()));
    }

    public ActionReport drew(int drawCount) {
        return state(Chat.translatable("game.minopp.play.drew", initiator.name, drawCount));
    }

    public ActionReport playedNoCard(boolean drawn) {
        if (drawn) {
            return state(Chat.translatable("game.minopp.play.played_no_drawn_card", initiator.name));
        } else {
            return state(Chat.translatable("game.minopp.play.played_no_card", initiator.name));
        }
    }

    public ActionReport gameDestroyed() {
        return state(Chat.translatable("game.minopp.play.game_destroyed", initiator.name));
    }

    public ActionReport gameStarted() {
        return state(Chat.translatable("game.minopp.play.game_started", initiator.name));
    }

    public ActionReport gameWon() {
        return gameWon(initiator, List.of(initiator));
    }

    public List<CardPlayer> winners;

    public ActionReport gameWon(CardPlayer winner, List<CardPlayer> winners) {
        shouldDestroyGame = true;
        this.winners = winners;
        IChatComponent result;
        if (winners.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (CardPlayer p : winners) {
                if (names.length() > 0) names.append(" & ");
                names.append(p.name);
            }
            result = Chat.translatable("game.minopp.play.game_won_team", names.toString());
        } else {
            result = Chat.translatable("game.minopp.play.game_won", winner.name);
        }
        for (CardPlayer player : game.players) {
            if (!winners.contains(player)) {
                result = result.appendText("\n")
                    .appendSibling(
                        player.eliminated ? Chat.translatable("game.minopp.play.game_eliminated", player.name)
                            : Chat.translatable("game.minopp.play.game_nearly_won", player.name, player.hand.size()));
            }
        }
        return state(result);
    }

    public ActionReport panic(IChatComponent message) {
        shouldDestroyGame = true;
        return state(message);
    }

    public static final ActionReport NO_GAME = ActionReport.builder()
        .state(Chat.translatable("game.minopp.play.no_game"));
}
