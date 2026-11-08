package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.effect.GrantRewardEffectEvent;
import com.hfstudio.minopp.effect.PlayerFireworkEffectEvent;
import com.hfstudio.minopp.effect.PlayerGlowEffectEvent;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

/**
 * Server-authoritative game state and rules engine. Card behaviour lives in {@link CardTypes}; switches live in
 * {@link TableConfig} / {@link GameRules}.
 */
public class CardGame {

    private static final long DECISION_TIMEOUT_MS = 15000;

    /** Turn order (sorted by seat). Eliminated players stay in the list but are skipped. */
    public List<CardPlayer> players;
    public int currentPlayerIndex;

    /** Accumulated draw penalty for the current player. */
    public int pendingDraw;
    public int lastDrawValue;
    public UUID pendingDrawSource;
    public PlayerActionPhase currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
    public int drawnCardUid = -1;

    public boolean isAntiClockwise;
    public boolean awaitingCutIn;

    public UUID lastSwapFromUuid;
    public UUID lastSwapToUuid;
    /** Incremented whenever hands move between players, so clients can animate it. */
    public int handMoveCounter;
    public int handMoveKind;

    public List<Card> deck = new ArrayList<>();
    public List<Card> discardDeck = new ArrayList<>();
    public Card topCard;
    public CardColor currentColor = CardColor.WILD;
    public boolean darkSide;

    // Special mechanics state
    public UUID challengeable;
    public boolean challengeableHadColor;
    public final List<PendingDecision> decisions = new ArrayList<>();
    public final Map<UUID, UUID> links = new HashMap<>();
    public UUID danceOwner;
    public UUID balloonHolder;
    public int balloonFuse;
    public UUID forcedNext;

    // Per-turn transient state
    private int skipCount;
    private boolean extraTurn;
    private boolean deferAdvance;
    private CardColor colorBeforePlay;

    public BlockEntityMinoTable tableEntity;
    private final Random random = new Random();

    public CardGame(List<CardPlayer> players) {
        this.players = players;
    }

    /** Config used when the game is not attached to a table (tests / simulations). */
    public TableConfig detachedConfig;

    public TableConfig config() {
        if (tableEntity != null) return tableEntity.config;
        if (detachedConfig == null) detachedConfig = new TableConfig();
        return detachedConfig;
    }

    private boolean rule(String id) {
        return config().getBool(id);
    }

    public CardFace topFace() {
        return topCard.face(darkSide);
    }

    public CardFace face(Card card) {
        return card.face(darkSide);
    }

    public CardPlayer currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<CardPlayer> activePlayers() {
        return players.stream()
            .filter(p -> !p.eliminated)
            .collect(Collectors.toList());
    }

    private int step() {
        return isAntiClockwise ? -1 : 1;
    }

    private int nextActiveIndex(int from) {
        int size = players.size();
        int idx = from;
        for (int i = 0; i < size; i++) {
            idx = ((idx + step()) % size + size) % size;
            if (!players.get(idx).eliminated) return idx;
        }
        return from;
    }

    public CardPlayer nextPlayer() {
        return players.get(nextActiveIndex(currentPlayerIndex));
    }

    // ===================================================================================== setup

    public ActionReport initiate(CardPlayer cardPlayer, int initialCardCount) {
        if (players.size() < 2) return ActionReport.NO_GAME;
        TableConfig config = config();
        players.sort(Comparator.comparingInt(p -> p.seat));
        currentPlayerIndex = random.nextInt(players.size());
        pendingDraw = 0;
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        isAntiClockwise = false;
        darkSide = false;

        // Team mode: players keep the teams chosen in the lobby (copied from their seats); otherwise no teams
        if (!Teams.enabled(config)) {
            for (CardPlayer p : players) p.team = -1;
        } else {
            Teams.normalize(players, Teams.count(config));
        }

        deck = config.buildDeck(players.size(), random);
        for (int i = 0; i < initialCardCount; i++) {
            for (CardPlayer player : players) {
                if (deck.isEmpty()) break;
                player.hand.add(deck.remove(deck.size() - 1));
            }
        }
        // Start with a plain number card when the deck has any
        boolean hasNumber = deck.stream()
            .anyMatch(c -> c.light.isNumber() && !c.light.isWild());
        Card tobeTopCard = deck.remove(deck.size() - 1);
        int guard = 0;
        while (hasNumber && (!tobeTopCard.light.isNumber() || tobeTopCard.light.isWild()) && guard++ < 500) {
            deck.add(0, tobeTopCard);
            tobeTopCard = deck.remove(deck.size() - 1);
        }
        topCard = tobeTopCard;
        currentColor = topFace().color;
        ActionReport report = ActionReport.builder(this, cardPlayer);
        GameSounds.deal(report, initialCardCount + 3);
        report.sound(GameSounds.PLAY, report.soundTail() + 250);
        GameSounds.turn(report, players.get(currentPlayerIndex), false, report.soundTail() + 500);
        return report.gameStarted();
    }

    // ===================================================================================== validation

    /** Returns null if the card may be played, else the lang key of the reason. */
    public String checkPlayable(CardPlayer player, Card card, boolean useFlex) {
        CardFace face = face(card);
        CardType type = face.type();
        if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN && card.uid != drawnCardUid) {
            return "game.minopp.play.only_drawn_card";
        }
        if (useFlex) {
            if (!rule(GameRules.FLEX_POWER) || !player.powerOn || (!type.hasFlex() && face.flexColor == null)) {
                return "game.minopp.play.no_flex";
            }
        }
        if (type.condition != null && !type.condition.test(this, player, face)) {
            return "game.minopp.play.invalid_card";
        }
        if (pendingDraw > 0) {
            if (type.answersDraw) return null;
            if (!type.isDraw() || useFlex) return "game.minopp.play.must_stack_or_draw";
            if (!rule(GameRules.STACKING)) return "game.minopp.play.must_stack_or_draw";
            boolean canStack;
            if (rule(GameRules.STACK_ANY_DRAW)) {
                canStack = type.drawValue >= lastDrawValue;
            } else if (rule(GameRules.STACK_DRAW4_ON_DRAW2)) {
                canStack = type.drawValue >= lastDrawValue;
            } else {
                canStack = type.drawValue == lastDrawValue;
            }
            return canStack ? null : "game.minopp.play.must_stack_or_draw";
        }
        if (face.isWild()) {
            if (rule(GameRules.NO_BLUFFING) && type.matchKey.equals("wild_draw4") && !useFlex) {
                boolean hasColor = player.hand.stream()
                    .anyMatch(c -> c != card && face(c).color == currentColor && currentColor != CardColor.WILD);
                if (hasColor) return "game.minopp.play.rule_forbid";
            }
            return null;
        }
        CardColor matchColor = useFlex && face.flexColor != null ? face.flexColor : face.color;
        if (currentColor == CardColor.WILD || matchColor == currentColor) return null;
        CardFace top = topFace();
        if (!top.isWild() || top.type().keepsColor) {
            if (face.symbolKey()
                .equals(top.symbolKey())) return null;
        }
        return "game.minopp.play.invalid_card";
    }

    /**
     * Every check {@link #playCard} makes before playing, without side effects: null when the card can be played
     * right now (on its turn or as a jump-in), else the lang key of the reason. Used by clients so they never open a
     * color/target choice for a card the server would refuse.
     */
    public String whyCannotPlay(CardPlayer player, Card card, boolean useFlex) {
        int idx = players.indexOf(player);
        if (idx == -1 || players.get(idx).eliminated) return "game.minopp.play.no_player";
        CardPlayer real = players.get(idx);
        if (real.hand.stream()
            .noneMatch(c -> c.uid == card.uid)) return "game.minopp.play.not_your_card";
        if (!decisions.isEmpty()) return "game.minopp.play.waiting_decision";
        boolean identical = isIdenticalToTop(card);
        if (awaitingCutIn && !(identical && rule(GameRules.JUMP_IN))) return "game.minopp.play.not_match_cut";
        if (awaitingCutIn && idx != currentPlayerIndex) return null;
        if (awaitingCutIn || idx != currentPlayerIndex) {
            boolean jumpIn = rule(GameRules.JUMP_IN) && pendingDraw == 0 && identical && idx != currentPlayerIndex;
            return jumpIn ? null : "game.minopp.play.not_your_turn";
        }
        return checkPlayable(real, card, useFlex);
    }

    public boolean canPlay(CardPlayer player, Card card) {
        return checkPlayable(player, card, false) == null
            || (rule(GameRules.FLEX_POWER) && player.powerOn && checkPlayable(player, card, true) == null);
    }

    public boolean isIdenticalToTop(Card card) {
        return face(card).equals(topFace());
    }

    public boolean anyOtherPlayerCanCutIn() {
        if (!rule(GameRules.JUMP_IN)) return false;
        for (int i = 0; i < players.size(); i++) {
            if (i == currentPlayerIndex || players.get(i).eliminated) continue;
            if (players.get(i).hand.stream()
                .anyMatch(this::isIdenticalToTop)) return true;
        }
        return false;
    }

    // ===================================================================================== actions

    public ActionReport playCard(CardPlayer cardPlayer, int cardUid, PlayContext.PlayChoices choices) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        int playerIndex = players.indexOf(cardPlayer);
        if (playerIndex == -1 || cardPlayer.eliminated)
            return report.fail(Chat.translatable("game.minopp.play.no_player"));
        Card card = cardPlayer.hand.stream()
            .filter(c -> c.uid == cardUid)
            .findFirst()
            .orElse(null);
        if (card == null) return report.fail(Chat.translatable("game.minopp.play.not_your_card"));
        if (!decisions.isEmpty()) return report.fail(Chat.translatable("game.minopp.play.waiting_decision"));

        // early check for jump in to stop players playing during the cut-in window
        if (awaitingCutIn && !(isIdenticalToTop(card) && rule(GameRules.JUMP_IN))) {
            return report.fail(Chat.translatable("game.minopp.play.not_match_cut"));
        }
        boolean isCut = false;
        if (awaitingCutIn && playerIndex != currentPlayerIndex) {
            isCut = true;
        } else if (awaitingCutIn || playerIndex != currentPlayerIndex) {
            // Jump-in outside the window: an identical card may always be slapped down (house rule)
            if (rule(GameRules.JUMP_IN) && pendingDraw == 0
                && decisions.isEmpty()
                && isIdenticalToTop(card)
                && playerIndex != currentPlayerIndex) {
                isCut = true;
            } else {
                return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
            }
        }
        if (!isCut) {
            String reason = checkPlayable(cardPlayer, card, choices.useFlex);
            if (reason != null) return report.fail(Chat.translatable(reason));
        }
        if (isCut) {
            currentPlayerIndex = playerIndex;
            awaitingCutIn = false;
            currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        }

        CardFace face = face(card);
        CardType type = face.type();
        boolean useFlex = choices.useFlex && !isCut;
        CardType.Choice choice = type.choiceFor(useFlex);
        if (choice.needsColor()) {
            CardColor.Side side = darkSide ? CardColor.Side.DARK : CardColor.Side.LIGHT;
            if (choices.color == null || choices.color.side != side) {
                choices.color = mostCommonColor(cardPlayer);
            }
        }
        if (choice.targetCount() > 0) {
            List<CardPlayer> others = activePlayers();
            others.remove(cardPlayer);
            choices.targets.removeIf(
                u -> others.stream()
                    .noneMatch(p -> p.uuid.equals(u)));
            while (choices.targets.size() < choice.targetCount() && !others.isEmpty()) {
                CardPlayer pick = others.stream()
                    .filter(p -> !choices.targets.contains(p.uuid))
                    .min(Comparator.comparingInt(p -> p.hand.size()))
                    .orElse(null);
                if (pick == null) break;
                choices.targets.add(pick.uuid);
            }
        }

        colorBeforePlay = currentColor;
        skipCount = 0;
        extraTurn = false;
        deferAdvance = false;
        doDiscardCard(cardPlayer, card, report);
        GameSounds.cardPlayed(report, type, useFlex);

        // New color
        if (type.keepsColor) {
            // unchanged
        } else if (face.isWild()) {
            currentColor = choices.color != null ? choices.color : currentColor;
        } else {
            currentColor = useFlex && face.flexColor != null ? face.flexColor : face.color;
        }
        if (useFlex) {
            cardPlayer.powerOn = false;
            normalizePower();
        }
        if (type.drawValue == 0 || useFlex) {
            // a non-draw card ends any challenge window of a previous Wild +4
            if (pendingDraw == 0) challengeable = null;
        }

        if (cardPlayer.hand.isEmpty()) {
            return win(cardPlayer, report);
        }

        PlayContext ctx = new PlayContext(this, cardPlayer, card, face, choices, report);
        CardType.Effect effect = useFlex ? type.flexEffect : type.effect;
        if (effect != null) effect.apply(ctx);

        // Dance floor: only number cards until the turn comes back to whoever started it
        if (danceOwner != null && !cardPlayer.uuid.equals(danceOwner) && !face.isNumber()) {
            drawCards(cardPlayer, 2, report);
            report.messageAll(Chat.translatable("game.minopp.play.dance_penalty", cardPlayer.name));
        }

        if (choices.shout && cardPlayer.hand.size() < 2 && !cardPlayer.hasShoutedMino) {
            // shouted together with this card (we had 2 or fewer before playing it, and it is our play)
            cardPlayer.hasShoutedMino = true;
            report.sound(Mino.id("game.mino_shout"), 0);
            report.messageAll(Chat.translatable("game.minopp.play.mino_shout", cardPlayer.name));
        }

        if (rule(GameRules.SEVEN_ZERO) && face.isNumber() && !cardPlayer.hand.isEmpty()) {
            if (face.number == 0) {
                for (CardPlayer p : players) report.sound(Mino.id("game.hand_change"), 500, p);
                rotateHands(report);
            } else if (face.number == 7) {
                CardPlayer target = choices.targets.isEmpty() ? null : deAmputate(choices.targets.get(0));
                if (target == null || target.equals(cardPlayer) || target.eliminated) {
                    List<CardPlayer> others = activePlayers();
                    others.remove(cardPlayer);
                    target = others.stream()
                        .min(Comparator.comparingInt(p -> p.hand.size()))
                        .orElse(null);
                }
                if (target != null) {
                    for (CardPlayer p : players) report.sound(Mino.id("game.hand_change"), 500, p);
                    swapHands(cardPlayer, target, report);
                }
            }
        }

        ActionReport end = checkEndConditions(cardPlayer, report);
        if (end != null) return end;

        if (!deferAdvance) advanceTurn(report);
        return isCut ? report.cut() : report.played();
    }

    /** Win / elimination checks after any action. Returns the final report when the game ended. */
    private ActionReport checkEndConditions(CardPlayer actor, ActionReport report) {
        for (CardPlayer p : players) {
            if (!p.eliminated && p.hand.isEmpty()) return win(p, report);
        }
        applyMercy(report);
        List<CardPlayer> active = activePlayers();
        if (active.size() == 1) return win(active.get(0), report);
        if (active.isEmpty()) return report.panic(Chat.translatable("game.minopp.play.deck_depleted"));
        if (players.get(currentPlayerIndex).eliminated) {
            currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        }
        return null;
    }

    private ActionReport win(CardPlayer winner, ActionReport report) {
        List<CardPlayer> winners = new ArrayList<>();
        winners.add(winner);
        if (winner.team >= 0) {
            for (CardPlayer p : players) if (p.team == winner.team && !p.equals(winner)) winners.add(p);
        }
        for (CardPlayer w : winners) {
            report.effect(new PlayerGlowEffectEvent(w.uuid, 6 * 20));
            report.effect(new GrantRewardEffectEvent(w.uuid));
            for (int i = 0; i < 5; i++) report.effect(new PlayerFireworkEffectEvent(i * 1000 + 500, w.uuid));
        }
        report.sound(Mino.id("game.win"), 0);
        return report.gameWon(winner, winners);
    }

    public ActionReport playNoCard(CardPlayer cardPlayer) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        int playerIndex = players.indexOf(cardPlayer);
        if (playerIndex == -1 || cardPlayer.eliminated)
            return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (playerIndex != currentPlayerIndex || awaitingCutIn)
            return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
        if (!decisions.isEmpty()) return report.fail(Chat.translatable("game.minopp.play.waiting_decision"));

        if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN) {
            report.sound(Mino.id("game.pass"), 0);
            advanceTurn(report);
            return report.playedNoCard(true);
        }

        if (pendingDraw > 0) {
            int amount = pendingDraw;
            takePendingDraw(cardPlayer, report);
            ActionReport end = checkEndConditions(cardPlayer, report);
            if (end != null) return end;
            advanceTurn(report);
            return report.drew(amount);
        }

        int drawn = 0;
        Card last = null;
        if (rule(GameRules.DRAW_TO_MATCH)) {
            while (drawn < 40) {
                List<Card> got = drawCards(cardPlayer, 1, report);
                if (got.isEmpty()) break;
                drawn++;
                last = got.get(0);
                if (canPlay(cardPlayer, last)) break;
            }
        } else {
            List<Card> got = drawCards(cardPlayer, 1, report);
            drawn = got.size();
            if (!got.isEmpty()) last = got.get(0);
        }
        if (drawn == 0 && deck.isEmpty() && discardDeck.isEmpty()) {
            report.messageAll(Chat.translatable("game.minopp.play.deck_empty"));
        }
        ActionReport end = checkEndConditions(cardPlayer, report);
        if (end != null) return end;

        boolean playable = last != null && canPlay(cardPlayer, last);
        if (playable && rule(GameRules.FORCED_PLAY)) {
            PlayContext.PlayChoices choices = AutoPlayer.defaultChoices(this, cardPlayer, last);
            currentPlayerPhase = PlayerActionPhase.DISCARD_DRAWN;
            drawnCardUid = last.uid;
            ActionReport playReport = playCard(cardPlayer, last.uid, choices);
            if (!playReport.isFail) {
                report.combineWith(playReport);
                report.state = playReport.state;
                return report;
            }
        }
        if (playable && (rule(GameRules.PLAY_DRAWN) || rule(GameRules.DRAW_TO_MATCH))) {
            currentPlayerPhase = PlayerActionPhase.DISCARD_DRAWN;
            drawnCardUid = last.uid;
            return report.drew(drawn);
        }
        advanceTurn(report); // immediately advance, no play after draw
        return report.drew(drawn);
    }

    public ActionReport shoutMino(CardPlayer realPlayer) {
        ActionReport report = ActionReport.builder(this, realPlayer);
        if (!realPlayer.hasShoutedMino) {
            // MINO can only be shouted on our own turn with 2 cards or fewer; anything else is simply refused
            int idx = players.indexOf(realPlayer);
            if (idx != currentPlayerIndex || awaitingCutIn) {
                return report.fail(Chat.translatable("game.minopp.play.mino_shout_not_turn"));
            }
            if (realPlayer.hand.size() > 2) {
                return report.fail(Chat.translatable("game.minopp.play.mino_shout_too_early"));
            }
            realPlayer.hasShoutedMino = true;
            report.sound(Mino.id("game.mino_shout"), 0);
            return report.messageAll(Chat.translatable("game.minopp.play.mino_shout", realPlayer.name));
        }
        return null;
    }

    public ActionReport doubtMino(CardPlayer srcPlayer, UUID targetPlayerWithoutHand) {
        ActionReport report = ActionReport.builder(this, srcPlayer);
        CardPlayer targetPlayer = deAmputate(targetPlayerWithoutHand);
        if (targetPlayer == null) return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (players.get(currentPlayerIndex)
            .equals(targetPlayer)) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_playing"));
        } else if (srcPlayer.equals(targetPlayer)) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_self"));
        } else if (targetPlayer.hasShoutedMino) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_shouted"));
        } else if (targetPlayer.hand.size() > 1) {
            return report.fail(Chat.translatable("game.minopp.play.doubt_target_hand"));
        } else {
            drawCards(targetPlayer, config().getInt(GameRules.SHOUT_PENALTY), report);
            targetPlayer.hasShoutedMino = true;
            report.sound(Mino.id("game.doubt_success"), 0);
            return report
                .messageAll(Chat.translatable("game.minopp.play.doubt_success", srcPlayer.name, targetPlayer.name));
        }
    }

    // ===================================================================================== decisions

    public ActionReport answerDecision(CardPlayer player, int answer, UUID target) {
        ActionReport report = ActionReport.builder(this, player);
        for (PendingDecision d : decisions) {
            if (d.player.equals(player.uuid) && !d.isAnswered()) {
                if (d.type == PendingDecision.Type.POINT_TAKEN) {
                    if (target == null || target.equals(player.uuid) || deAmputate(target) == null) {
                        return report.fail(Chat.translatable("game.minopp.play.invalid_target"));
                    }
                    d.answerTarget = target;
                } else {
                    d.answer = Math.max(0, answer);
                }
                return resolveDecisionsIfDone(report);
            }
        }
        return report.fail(Chat.translatable("game.minopp.play.no_decision"));
    }

    /** Called every server tick; answers for bots and timed out players. */
    public ActionReport tickDecisions(long now, java.util.function.Predicate<UUID> isHuman) {
        if (decisions.isEmpty()) return null;
        boolean changed = false;
        for (PendingDecision d : decisions) {
            if (d.isAnswered()) continue;
            if (!isHuman.test(d.player) || now >= d.deadline) {
                answerAutomatically(d);
                changed = true;
            }
        }
        if (!changed) return null;
        return resolveDecisionsIfDone(ActionReport.builder(this, currentPlayer()));
    }

    private void answerAutomatically(PendingDecision d) {
        CardPlayer p = deAmputate(d.player);
        switch (d.type) {
            case CHALLENGE -> d.answer = p != null && p.hand.size() > 6 && random.nextFloat() < 0.35f ? 1 : 0;
            case ROULETTE_COLOR -> d.answer = (p != null ? mostCommonColor(p)
                : CardColor.forSide(darkSide)
                    .get(0)).ordinal();
            case POINT_TAKEN -> {
                List<CardPlayer> others = activePlayers();
                others.removeIf(o -> o.uuid.equals(d.player));
                d.answerTarget = others.isEmpty() ? d.player
                    : others.stream()
                        .min(Comparator.comparingInt(o -> o.hand.size()))
                        .get().uuid;
            }
        }
    }

    private ActionReport resolveDecisionsIfDone(ActionReport report) {
        if (decisions.stream()
            .anyMatch(d -> !d.isAnswered()))
            return report.state(Chat.translatable("game.minopp.play.waiting_decision"));
        List<PendingDecision> done = new ArrayList<>(decisions);
        decisions.clear();
        PendingDecision first = done.get(0);
        switch (first.type) {
            case CHALLENGE -> resolveChallenge(first, report);
            case ROULETTE_COLOR -> {
                CardPlayer victim = deAmputate(first.player);
                CardColor color = CardColor.values()[Math.min(first.answer, CardColor.values().length - 1)];
                if (color.isWild()) color = CardColor.forSide(darkSide)
                    .get(0);
                int count = drawUntilColor(victim, color, report);
                currentColor = color;
                report.messageAll(
                    Chat.translatable(
                        "game.minopp.play.roulette",
                        victim.name,
                        Chat.translatable(color.langKey()),
                        count));
                // the roulette victim loses their turn
                currentPlayerIndex = players.indexOf(victim);
                ActionReport end = checkEndConditions(victim, report);
                if (end != null) return end;
                advanceTurn(report);
            }
            case POINT_TAKEN -> {
                Map<UUID, Integer> votes = new HashMap<>();
                for (PendingDecision d : done) {
                    if (d.answerTarget != null) votes.merge(d.answerTarget, 1, Integer::sum);
                }
                for (Map.Entry<UUID, Integer> e : votes.entrySet()) {
                    CardPlayer p = deAmputate(e.getKey());
                    if (p == null || p.eliminated) continue;
                    drawCards(p, e.getValue(), report);
                    report.messageAll(Chat.translatable("game.minopp.play.point_taken", p.name, e.getValue()));
                }
                links.clear();
                ActionReport end = checkEndConditions(currentPlayer(), report);
                if (end != null) return end;
                advanceTurn(report);
            }
        }
        if (report.state == null) report.state(Chat.translatable("game.minopp.play.decision_resolved"));
        return report;
    }

    private void resolveChallenge(PendingDecision d, ActionReport report) {
        CardPlayer victim = deAmputate(d.player);
        CardPlayer source = deAmputate(d.source);
        challengeable = null;
        if (d.answer == 1 && source != null) {
            if (challengeableHadColor) {
                // Bluff caught: the source draws instead, the victim plays normally
                report.messageAll(Chat.translatable("game.minopp.play.challenge_success", victim.name, source.name));
                drawCards(source, pendingDraw, report);
                pendingDraw = 0;
                lastDrawValue = 0;
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                checkEndConditions(victim, report);
                return;
            } else {
                report.messageAll(Chat.translatable("game.minopp.play.challenge_fail", victim.name));
                pendingDraw += 2;
            }
        }
        takePendingDraw(victim, report);
        if (checkEndConditions(victim, report) != null) return;
        advanceTurn(report);
    }

    // ===================================================================================== turn flow

    public void advanceTurn(ActionReport report) {
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        drawnCardUid = -1;
        int previousIndex = currentPlayerIndex;
        if (forcedNext != null) {
            CardPlayer target = deAmputate(forcedNext);
            forcedNext = null;
            if (target != null && !target.eliminated) currentPlayerIndex = players.indexOf(target);
            else currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        } else if (extraTurn) {
            extraTurn = false;
        } else {
            for (int i = 0; i <= skipCount; i++) currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        }
        skipCount = 0;
        CardPlayer current = players.get(currentPlayerIndex);

        if (danceOwner != null && danceOwner.equals(current.uuid)) {
            danceOwner = null;
            report.messageAll(Chat.translatable("game.minopp.play.dance_end"));
        }
        if (balloonHolder != null) {
            balloonHolder = current.uuid;
            balloonFuse--;
            if (balloonFuse <= 0) {
                balloonHolder = null;
                int penalty = config().getInt(GameRules.BALLOON_PENALTY);
                drawCards(current, penalty, report);
                GameSounds.balloonPop(report);
                report.messageAll(Chat.translatable("game.minopp.play.balloon_pop", current.name, penalty));
            }
        }

        // trigger for jump-in, if true then it locks for a few seconds to allow jump-ins
        if (anyOtherPlayerCanCutIn()) {
            awaitingCutIn = true;
            final CardGame self = this;
            TaskScheduler.Holder.INSTANCE.schedule(config().getInt(GameRules.JUMP_IN_SECONDS) * 20, () -> {
                if (tableEntity == null || tableEntity.game != self || !awaitingCutIn) return;
                awaitingCutIn = false;
                tableEntity.handleActionResult(resolveStuckPenalty(), null, null);
            });
            return;
        }

        current.hasShoutedMino = false;
        GameSounds.turn(report, current, currentPlayerIndex == previousIndex, Math.max(450, report.soundTail() + 300));
        resolvePenaltyFor(current, report);
    }

    /** Called once a jump-in window closes. */
    public ActionReport resolveStuckPenalty() {
        ActionReport report = ActionReport.builder(this, currentPlayer());
        CardPlayer current = currentPlayer();
        current.hasShoutedMino = false;
        if (pendingDraw <= 0) GameSounds.turn(report, current, false, 0);
        resolvePenaltyFor(current, report);
        return report;
    }

    /** If the current player owes a draw penalty and cannot answer it, draw it (or offer a challenge). */
    private void resolvePenaltyFor(CardPlayer current, ActionReport report) {
        if (pendingDraw <= 0) return;
        boolean canAnswer = current.hand.stream()
            .anyMatch(c -> checkPlayable(current, c, false) == null);
        if (canAnswer) return;
        if (rule(GameRules.CHALLENGE) && challengeable != null && !challengeable.equals(current.uuid)) {
            decisions.add(
                new PendingDecision(
                    PendingDecision.Type.CHALLENGE,
                    current.uuid,
                    challengeable,
                    System.currentTimeMillis() + DECISION_TIMEOUT_MS));
            return;
        }
        int penalty = pendingDraw;
        takePendingDraw(current, report);
        if (checkEndConditions(current, report) != null) return;
        report.state(Chat.translatable("game.minopp.play.drew", current.name, penalty));
        // Now advance again since their turn is forfeit
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        CardPlayer nextPlayer = players.get(currentPlayerIndex);
        nextPlayer.hasShoutedMino = false;
        GameSounds.turn(report, nextPlayer, false, Math.max(900, report.soundTail() + 300));
    }

    private void takePendingDraw(CardPlayer victim, ActionReport report) {
        int amount = pendingDraw;
        pendingDraw = 0;
        lastDrawValue = 0;
        pendingDrawSource = null;
        challengeable = null;
        drawCards(victim, amount, report);
    }

    // ===================================================================================== effect API

    public void skipNext(int count) {
        skipCount += count;
    }

    public void reverse() {
        if (activePlayers().size() == 2) {
            skipCount += 1;
        } else {
            isAntiClockwise = !isAntiClockwise;
        }
    }

    public void giveExtraTurn() {
        extraTurn = true;
    }

    public void addPendingDraw(int amount, CardPlayer source) {
        pendingDraw += amount;
        lastDrawValue = amount;
        pendingDrawSource = source.uuid;
    }

    public void markChallengeable(CardPlayer player) {
        if (!rule(GameRules.CHALLENGE)) return;
        challengeable = player.uuid;
        CardColor before = colorBeforePlay;
        challengeableHadColor = before != null && before != CardColor.WILD
            && player.hand.stream()
                .anyMatch(c -> face(c).color == before);
    }

    public void targetDraws(CardPlayer target, int amount, ActionReport report) {
        if (target == null) return;
        drawCards(target, amount, report);
        report.messageAll(Chat.translatable("game.minopp.play.target_draw", target.name, amount));
    }

    public void everyoneElseDraws(CardPlayer player, int amount, ActionReport report) {
        for (CardPlayer p : activePlayers()) {
            if (!p.equals(player)) drawCards(p, amount, report);
        }
    }

    public void discardAllOfColor(CardPlayer player, CardColor color, ActionReport report) {
        List<Card> toDiscard = player.hand.stream()
            .filter(c -> face(c).color == color)
            .collect(Collectors.toList());
        if (toDiscard.isEmpty()) return;
        player.hand.removeAll(toDiscard);
        for (Card c : toDiscard) {
            c.owner = player.uuid;
            discardDeck.add(c);
        }
        report.messageAll(
            Chat.translatable(
                "game.minopp.play.discard_all",
                player.name,
                toDiscard.size(),
                Chat.translatable(color.langKey())));
    }

    public void startColorRoulette(CardPlayer player) {
        CardPlayer victim = nextPlayer();
        decisions.add(
            new PendingDecision(
                PendingDecision.Type.ROULETTE_COLOR,
                victim.uuid,
                player.uuid,
                System.currentTimeMillis() + DECISION_TIMEOUT_MS));
        currentPlayerIndex = players.indexOf(victim);
        deferAdvance = true;
    }

    public void nextDrawsUntilColor(CardColor color, ActionReport report) {
        CardPlayer victim = nextPlayer();
        if (color == null || color.isWild()) color = currentColor;
        int count = drawUntilColor(victim, color, report);
        report.messageAll(
            Chat.translatable("game.minopp.play.roulette", victim.name, Chat.translatable(color.langKey()), count));
        skipNext(1);
    }

    private int drawUntilColor(CardPlayer victim, CardColor color, ActionReport report) {
        int count = 0;
        while (count < 40) {
            List<Card> got = drawCards(victim, 1, report);
            if (got.isEmpty()) break;
            count++;
            if (face(got.get(0)).color == color) break;
        }
        return count;
    }

    public void flipSides(CardPlayer player, ActionReport report) {
        if (topCard.dark == null) return;
        darkSide = !darkSide;
        Collections.reverse(deck);
        CardFace top = topFace();
        currentColor = top.isWild() ? mostCommonColor(player) : top.color;
        // A dark/light side draw card on top does not carry over a penalty
        report.messageAll(Chat.translatable(darkSide ? "game.minopp.play.flip_dark" : "game.minopp.play.flip_light"));
        handMoveKind = 2;
        handMoveCounter++;
    }

    public void togglePower(CardPlayer player, ActionReport report) {
        if (!rule(GameRules.FLEX_POWER)) return;
        player.powerOn = !player.powerOn;
        normalizePower();
    }

    public void toggleAllPower(ActionReport report) {
        if (!rule(GameRules.FLEX_POWER)) return;
        for (CardPlayer p : players) p.powerOn = !p.powerOn;
        normalizePower();
        report.messageAll(Chat.translatable("game.minopp.play.all_flip"));
    }

    private void normalizePower() {
        if (activePlayers().stream()
            .noneMatch(p -> p.powerOn)) {
            for (CardPlayer p : players) p.powerOn = true;
        }
    }

    public void startPointTaken(CardPlayer player) {
        long deadline = System.currentTimeMillis() + DECISION_TIMEOUT_MS;
        for (CardPlayer p : activePlayers()) {
            if (p.equals(player)) continue;
            decisions.add(new PendingDecision(PendingDecision.Type.POINT_TAKEN, p.uuid, player.uuid, deadline));
        }
        if (!decisions.isEmpty()) deferAdvance = true;
    }

    public void linkPlayers(CardPlayer a, CardPlayer b, ActionReport report) {
        if (a == null || b == null || a.equals(b)) return;
        links.remove(a.uuid);
        links.remove(b.uuid);
        links.values()
            .removeIf(u -> u.equals(a.uuid) || u.equals(b.uuid));
        links.put(a.uuid, b.uuid);
        links.put(b.uuid, a.uuid);
        report.messageAll(Chat.translatable("game.minopp.play.linked", a.name, b.name));
    }

    public void pileUp(CardPlayer player, ActionReport report) {
        for (int i = 0; i < 20; i++) {
            if (deck.isEmpty()) reshuffle();
            if (deck.isEmpty()) break;
            Card revealed = deck.remove(deck.size() - 1);
            revealed.owner = player.uuid;
            discardDeck.add(revealed);
            CardFace f = face(revealed);
            if (!f.isWild()) {
                currentColor = f.color;
                report.messageAll(Chat.translatable("game.minopp.play.pile_up", f.getDisplayName()));
                return;
            }
        }
    }

    public void rebound(CardPlayer player, ActionReport report) {
        if (pendingDrawSource == null || pendingDrawSource.equals(player.uuid)) return;
        CardPlayer source = deAmputate(pendingDrawSource);
        if (source == null || source.eliminated) return;
        pendingDraw *= 2;
        pendingDrawSource = player.uuid;
        forcedNext = source.uuid;
        report.messageAll(Chat.translatable("game.minopp.play.rebound", player.name, source.name, pendingDraw));
    }

    public void startDanceFloor(CardPlayer player, ActionReport report) {
        danceOwner = player.uuid;
        report.messageAll(Chat.translatable("game.minopp.play.dance_start", player.name));
    }

    public void shuffleHands(CardPlayer player, ActionReport report) {
        List<CardPlayer> active = activePlayers();
        List<Card> pool = new ArrayList<>();
        int[] sizes = new int[active.size()];
        for (int i = 0; i < active.size(); i++) {
            sizes[i] = active.get(i).hand.size();
            pool.addAll(active.get(i).hand);
            active.get(i).hand.clear();
        }
        Collections.shuffle(pool, random);
        int k = 0;
        for (int i = 0; i < active.size(); i++) {
            for (int j = 0; j < sizes[i]; j++) active.get(i).hand.add(pool.get(k++));
        }
        handMoveKind = 1;
        handMoveCounter++;
        for (CardPlayer p : players) report.sound(Mino.id("game.hand_change"), 500, p);
        report.messageAll(Chat.translatable("game.minopp.play.hand_shuffle", player.name));
    }

    public void trial(CardPlayer player, CardColor color, ActionReport report) {
        if (color == null || color.isWild()) return;
        for (CardPlayer p : activePlayers()) {
            if (p.equals(player)) continue;
            Card match = p.hand.stream()
                .filter(c -> face(c).color == color)
                .findFirst()
                .orElse(null);
            if (match != null) {
                p.hand.remove(match);
                match.owner = p.uuid;
                discardDeck.add(match);
            } else {
                drawCards(p, 1, report);
            }
        }
        report.messageAll(Chat.translatable("game.minopp.play.trial", player.name, Chat.translatable(color.langKey())));
    }

    public void fiftyFifty(CardPlayer player, int amount, ActionReport report) {
        CardPlayer loser = random.nextBoolean() ? player : nextPlayer();
        drawCards(loser, amount, report);
        report.messageAll(Chat.translatable("game.minopp.play.fifty_fifty", loser.name, amount));
    }

    public void attachBalloon(CardPlayer player, ActionReport report) {
        balloonHolder = player.uuid;
        balloonFuse = 3 + random.nextInt(6);
        report.messageAll(Chat.translatable("game.minopp.play.balloon", player.name));
    }

    // ===================================================================================== hand movement

    public void swapHands(CardPlayer initiator, CardPlayer target, ActionReport report) {
        if (initiator == null || target == null) return;
        List<Card> a = new ArrayList<>(initiator.hand);
        initiator.hand.clear();
        initiator.hand.addAll(target.hand);
        target.hand.clear();
        target.hand.addAll(a);
        lastSwapFromUuid = initiator.uuid;
        lastSwapToUuid = target.uuid;
        handMoveKind = 7;
        handMoveCounter++;
    }

    /** 1:1 API of the original mod. */
    public void swapHands(CardPlayer initiator, CardPlayer target) {
        swapHands(initiator, target, null);
    }

    private void rotateHands(ActionReport report) {
        List<CardPlayer> active = activePlayers();
        int size = active.size();
        if (size <= 1) return;
        List<List<Card>> oldHands = new ArrayList<>();
        for (CardPlayer p : active) {
            oldHands.add(new ArrayList<>(p.hand));
            p.hand.clear();
        }
        for (int i = 0; i < size; i++) {
            int to = isAntiClockwise ? (i - 1 + size) % size : (i + 1) % size;
            active.get(to).hand.addAll(oldHands.get(i));
        }
        handMoveKind = 0;
        handMoveCounter++;
    }

    // ===================================================================================== deck

    public void doDiscardCard(CardPlayer player, Card card, ActionReport report) {
        discardDeck.add(topCard);
        topCard = card;
        card.owner = player.uuid;
        player.hand.remove(card);
        report.sound(GameSounds.PLAY, 0);
    }

    private void reshuffle() {
        if (discardDeck.isEmpty()) return;
        Collections.shuffle(discardDeck, random);
        deck.addAll(0, discardDeck);
        discardDeck.clear();
    }

    /** Draws up to {@code count} cards (Drawn Together partners draw too). Returns the cards drawn. */
    public List<Card> drawCards(CardPlayer cardPlayer, int count, ActionReport report) {
        List<Card> drawn = drawCardsRaw(cardPlayer, count, report);
        UUID partner = links.get(cardPlayer.uuid);
        if (partner != null && !drawn.isEmpty()) {
            CardPlayer p = deAmputate(partner);
            if (p != null && !p.eliminated) drawCardsRaw(p, drawn.size(), report);
        }
        return drawn;
    }

    private List<Card> drawCardsRaw(CardPlayer cardPlayer, int count, ActionReport report) {
        List<Card> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (deck.isEmpty()) reshuffle();
            if (deck.isEmpty()) break;
            Card c = deck.remove(deck.size() - 1);
            c.owner = cardPlayer.uuid;
            cardPlayer.hand.add(c);
            drawn.add(c);
        }
        GameSounds.draws(report, drawn.size(), 0);
        // a shout only protects a last card: drawing more takes it back
        if (!drawn.isEmpty() && cardPlayer.hand.size() > 1) cardPlayer.hasShoutedMino = false;
        return drawn;
    }

    /** Original API: returns false when not every card could be drawn. */
    public boolean doDrawCard(CardPlayer cardPlayer, int drawCount, ActionReport report) {
        return drawCards(cardPlayer, drawCount, report).size() == drawCount;
    }

    private void applyMercy(ActionReport report) {
        if (!rule(GameRules.MERCY)) return;
        int limit = config().getInt(GameRules.MERCY_LIMIT);
        for (CardPlayer p : players) {
            if (p.eliminated || p.hand.size() < limit) continue;
            p.eliminated = true;
            discardDeck.addAll(0, p.hand);
            p.hand.clear();
            links.remove(p.uuid);
            links.values()
                .removeIf(u -> u.equals(p.uuid));
            report.messageAll(Chat.translatable("game.minopp.play.mercy", p.name, limit));
            GameSounds.elimination(report);
        }
    }

    // ===================================================================================== helpers

    public CardColor mostCommonColor(CardPlayer player) {
        List<CardColor> colors = CardColor.forSide(darkSide);
        int[] counts = new int[CardColor.values().length];
        for (Card c : player.hand) counts[face(c).color.ordinal()]++;
        CardColor best = colors.get(random.nextInt(colors.size()));
        for (CardColor c : colors) if (counts[c.ordinal()] > counts[best.ordinal()]) best = c;
        return best;
    }

    public PendingDecision decisionFor(UUID uuid) {
        for (PendingDecision d : decisions) if (d.player.equals(uuid) && !d.isAnswered()) return d;
        return null;
    }

    public CardPlayer deAmputate(CardPlayer playerWithoutHand) {
        return players.stream()
            .filter(p -> p.equals(playerWithoutHand))
            .findFirst()
            .orElse(null);
    }

    public CardPlayer deAmputate(UUID uuid) {
        if (uuid == null) return null;
        return players.stream()
            .filter(p -> p.uuid.equals(uuid))
            .findFirst()
            .orElse(null);
    }

    public enum PlayerActionPhase {
        DISCARD_HAND,
        DISCARD_DRAWN,
    }

    // ===================================================================================== NBT

    private static List<Card> readCards(NBTTagList list) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) cards.add(Card.fromTag(list.getCompoundTagAt(i)));
        return cards;
    }

    private static NBTTagList writeCards(List<Card> cards) {
        NBTTagList list = new NBTTagList();
        for (Card card : cards) list.appendTag(card.toTag());
        return list;
    }

    public CardGame(NBTTagCompound tag) {
        currentPlayerIndex = tag.getInteger("currentPlayer");
        pendingDraw = tag.getInteger("pendingDraw");
        lastDrawValue = tag.getInteger("lastDrawValue");
        pendingDrawSource = NbtUtil.hasUUID(tag, "pendingDrawSource") ? NbtUtil.getUUID(tag, "pendingDrawSource")
            : null;
        awaitingCutIn = tag.getBoolean("awaitingCutIn");
        currentPlayerPhase = PlayerActionPhase.valueOf(tag.getString("currentPlayerPhase"));
        drawnCardUid = tag.hasKey("drawnCard") ? tag.getInteger("drawnCard") : -1;
        isAntiClockwise = tag.getBoolean("isAntiClockwise");
        deck = readCards(tag.getTagList("deck", 10));
        discardDeck = readCards(tag.getTagList("discardDeck", 10));
        topCard = Card.fromTag(tag.getCompoundTag("topCard"));
        CardColor color = CardColor.byId(tag.getString("color"));
        currentColor = color == null ? CardColor.WILD : color;
        darkSide = tag.getBoolean("darkSide");
        players = new ArrayList<>();
        NBTTagList playersTag = tag.getTagList("players", 10);
        for (int i = 0; i < playersTag.tagCount(); i++) players.add(new CardPlayer(playersTag.getCompoundTagAt(i)));
        lastSwapFromUuid = NbtUtil.hasUUID(tag, "lastSwapFrom") ? NbtUtil.getUUID(tag, "lastSwapFrom") : null;
        lastSwapToUuid = NbtUtil.hasUUID(tag, "lastSwapTo") ? NbtUtil.getUUID(tag, "lastSwapTo") : null;
        handMoveCounter = tag.getInteger("handMove");
        handMoveKind = tag.getInteger("handMoveKind");
        challengeable = NbtUtil.hasUUID(tag, "challengeable") ? NbtUtil.getUUID(tag, "challengeable") : null;
        challengeableHadColor = tag.getBoolean("challengeableHadColor");
        NBTTagList decisionsTag = tag.getTagList("decisions", 10);
        for (int i = 0; i < decisionsTag.tagCount(); i++)
            decisions.add(PendingDecision.fromTag(decisionsTag.getCompoundTagAt(i)));
        NBTTagList linksTag = tag.getTagList("links", 10);
        for (int i = 0; i < linksTag.tagCount(); i++) {
            NBTTagCompound l = linksTag.getCompoundTagAt(i);
            links.put(NbtUtil.getUUID(l, "a"), NbtUtil.getUUID(l, "b"));
        }
        danceOwner = NbtUtil.hasUUID(tag, "danceOwner") ? NbtUtil.getUUID(tag, "danceOwner") : null;
        balloonHolder = NbtUtil.hasUUID(tag, "balloonHolder") ? NbtUtil.getUUID(tag, "balloonHolder") : null;
        balloonFuse = tag.getInteger("balloonFuse");
        forcedNext = NbtUtil.hasUUID(tag, "forcedNext") ? NbtUtil.getUUID(tag, "forcedNext") : null;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("currentPlayer", currentPlayerIndex);
        tag.setInteger("pendingDraw", pendingDraw);
        tag.setInteger("lastDrawValue", lastDrawValue);
        if (pendingDrawSource != null) NbtUtil.putUUID(tag, "pendingDrawSource", pendingDrawSource);
        tag.setBoolean("awaitingCutIn", awaitingCutIn);
        tag.setString("currentPlayerPhase", currentPlayerPhase.name());
        tag.setInteger("drawnCard", drawnCardUid);
        tag.setBoolean("isAntiClockwise", isAntiClockwise);
        tag.setTag("deck", writeCards(deck));
        tag.setTag("discardDeck", writeCards(discardDeck));
        tag.setTag("topCard", topCard.toTag());
        tag.setString("color", currentColor.id());
        tag.setBoolean("darkSide", darkSide);
        NBTTagList playersTag = new NBTTagList();
        for (CardPlayer p : players) playersTag.appendTag(p.toTag());
        tag.setTag("players", playersTag);
        if (lastSwapFromUuid != null) NbtUtil.putUUID(tag, "lastSwapFrom", lastSwapFromUuid);
        if (lastSwapToUuid != null) NbtUtil.putUUID(tag, "lastSwapTo", lastSwapToUuid);
        tag.setInteger("handMove", handMoveCounter);
        tag.setInteger("handMoveKind", handMoveKind);
        if (challengeable != null) NbtUtil.putUUID(tag, "challengeable", challengeable);
        tag.setBoolean("challengeableHadColor", challengeableHadColor);
        NBTTagList decisionsTag = new NBTTagList();
        for (PendingDecision d : decisions) decisionsTag.appendTag(d.toTag());
        tag.setTag("decisions", decisionsTag);
        NBTTagList linksTag = new NBTTagList();
        for (Map.Entry<UUID, UUID> e : links.entrySet()) {
            NBTTagCompound l = new NBTTagCompound();
            NbtUtil.putUUID(l, "a", e.getKey());
            NbtUtil.putUUID(l, "b", e.getValue());
            linksTag.appendTag(l);
        }
        tag.setTag("links", linksTag);
        if (danceOwner != null) NbtUtil.putUUID(tag, "danceOwner", danceOwner);
        if (balloonHolder != null) NbtUtil.putUUID(tag, "balloonHolder", balloonHolder);
        tag.setInteger("balloonFuse", balloonFuse);
        if (forcedNext != null) NbtUtil.putUUID(tag, "forcedNext", forcedNext);
        return tag;
    }
}
