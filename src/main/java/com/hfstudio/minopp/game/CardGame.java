package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.effect.GrantRewardEffectEvent;
import com.hfstudio.minopp.effect.PlayerFireworkEffectEvent;
import com.hfstudio.minopp.effect.PlayerGlowEffectEvent;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

public class CardGame {

    private static final long DECISION_TIMEOUT_MS = 15000;

    public List<CardPlayer> players;
    public int currentPlayerIndex;

    public int pendingDraw;
    public int lastDrawValue;
    public UUID pendingDrawSource;
    public PlayerActionPhase currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
    public int drawnCardUid = -1;
    public int chainNumber = -1;

    public boolean isAntiClockwise;
    public boolean awaitingCutIn;

    public UUID lastSwapFromUuid;
    public UUID lastSwapToUuid;
    public int handMoveCounter;
    public int handMoveKind;

    public List<Card> deck = new ArrayList<>();
    public List<Card> discardDeck = new ArrayList<>();
    public Card topCard;
    public CardColor currentColor = CardColor.WILD;
    public boolean darkSide;

    public UUID challengeable;
    public boolean challengeableHadColor;
    public final List<PendingDecision> decisions = new ArrayList<>();
    public final Map<UUID, UUID> links = new HashMap<>();
    public UUID danceOwner;
    public UUID balloonHolder;
    public int balloonFuse;
    public UUID forcedNext;

    private int skipCount;
    private boolean extraTurn;
    private boolean deferAdvance;
    private CardColor colorBeforePlay;

    public BlockEntityMinoTable tableEntity;
    private final Random random = new Random();

    /** Stays the same for the whole game, also on clients that rebuild the game object on every update. */
    public long id = new Random().nextLong();

    public CardGame(List<CardPlayer> players) {
        this.players = players;
    }

    public boolean debug;
    public boolean debugHold;
    public int debugNext = -1;
    public UUID debugger;
    public int debugDrawSeq;
    public final List<Card> debugDrawn = new ArrayList<>();
    private final List<Card> debugHand = new ArrayList<>();
    private int debugUid = 2_000_000;
    private boolean debugSide;

    // every face of the side in play, each backed by its counterpart on the other side (same type where it exists,
    // the color in the same position: red and pink, yellow and teal, ...)
    private List<Card> debugCards(boolean dark) {
        Set<CardFace> light = new java.util.LinkedHashSet<>(), darkFaces = new java.util.LinkedHashSet<>();
        for (CardFace[] pair : debugCatalog()) {
            light.add(pair[0]);
            if (pair[1] != null) darkFaces.add(pair[1]);
        }
        Set<CardFace> active = dark ? darkFaces : light, other = dark ? light : darkFaces;
        List<Card> out = new ArrayList<>();
        for (CardFace f : active) {
            CardFace back = other.isEmpty() ? null : counterpart(f, other, !dark);
            out.add(dark ? new Card(debugUid++, back == null ? f : back, f) : new Card(debugUid++, f, back));
        }
        return out;
    }

    private static CardFace counterpart(CardFace f, Set<CardFace> other, boolean toDark) {
        CardColor color = f.color.isWild() ? CardColor.WILD
            : CardColor.forSide(toDark)
                .get(
                    Math.max(
                        0,
                        CardColor.forSide(!toDark)
                            .indexOf(f.color)));
        CardFace same = new CardFace(f.type().id, color, f.number);
        if (other.contains(same)) return same;
        for (CardFace o : other) if (o.color == color && o.isNumber() && o.number == Math.max(0, f.number)) return o;
        for (CardFace o : other) if (o.color == color) return o;
        return other.iterator()
            .next();
    }

    public int debugShoutSeq;
    public UUID debugShoutBy;
    private int debugShoutCursor;

    public static final List<String> DEBUG_ACTIONS = java.util.Arrays.asList(
        "turn",
        "next",
        "hold",
        "flip",
        "reverse",
        "color",
        "penalty",
        "others_draw",
        "draw",
        "shout",
        "clear",
        "refill",
        "win",
        "stop");

    // the debug toolbar: bends the game state so any situation can be set up and watched
    public ActionReport debugAction(String action) {
        CardPlayer me = deAmputate(debugger);
        ActionReport report = ActionReport.builder(this, me);
        if (!debug || me == null) return report.fail(Chat.translatable("game.minopp.debug.not_debug"));
        int mine = players.indexOf(me);
        int next = nextActiveIndex(mine);
        switch (action) {
            case "turn" -> {
                decisions.clear();
                awaitingCutIn = false;
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                drawnCardUid = -1;
                chainNumber = -1;
                currentPlayerIndex = mine;
            }
            case "next" -> {
                if (next == mine) return report.fail(Chat.translatable("game.minopp.debug.no_others"));
                decisions.clear();
                awaitingCutIn = false;
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                currentPlayerIndex = next;
                debugNext = -1;
            }
            case "hold" -> debugHold = !debugHold;
            case "flip" -> {
                darkSide = !darkSide;
                CardFace top = topCard == null ? null : topFace();
                currentColor = top != null && !top.isWild() ? top.color
                    : CardColor.forSide(darkSide)
                        .get(0);
            }
            case "reverse" -> isAntiClockwise = !isAntiClockwise;
            case "color" -> {
                List<CardColor> colors = CardColor.forSide(darkSide);
                currentColor = colors.get((colors.indexOf(currentColor) + 1) % colors.size());
            }
            case "penalty" -> {
                pendingDraw += 2;
                lastDrawValue = 2;
                pendingDrawSource = null;
            }
            case "others_draw" -> {
                if (next == mine) return report.fail(Chat.translatable("game.minopp.debug.no_others"));
                drawCards(players.get(next), 2, report);
            }
            case "draw" -> drawCards(me, 3, report);
            case "shout" -> {
                debugShoutBy = players.get(debugShoutCursor++ % players.size()).uuid;
                debugShoutSeq++;
            }
            case "clear" -> {
                pendingDraw = 0;
                lastDrawValue = 0;
                pendingDrawSource = null;
                challengeable = null;
                decisions.clear();
                awaitingCutIn = false;
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                speedLevel = 1;
                pendingSpeed = 0;
                balloonHolder = null;
                danceOwner = null;
                forcedNext = null;
                links.clear();
            }
            case "refill" -> {
                debugHand.clear();
                debugHand.addAll(debugCards(darkSide));
                debugSide = darkSide;
                me.hand.clear();
                me.hand.addAll(debugHand);
            }
            case "win" -> {
                return win(me, report);
            }
            default -> {
                return report.fail(Chat.translatable("game.minopp.debug.unknown", action));
            }
        }
        restoreDebugHand();
        return report
            .state(Chat.translatable("game.minopp.debug.applied", Chat.translatable("gui.minopp.debug.btn." + action)));
    }

    public boolean isDebugger(CardPlayer p) {
        return debug && p != null && p.uuid.equals(debugger);
    }

    // one card of every face in every pack; the shorter side repeats so both sides show all their faces
    public static List<CardFace[]> debugCatalog() {
        Set<CardFace> light = new java.util.LinkedHashSet<>(), dark = new java.util.LinkedHashSet<>();
        for (CardPack pack : CardPack.all()) {
            for (CardPack.Entry entry : pack.entries) {
                (entry.dark() || pack.backSideOnly ? dark : light).addAll(TableConfig.entryFaces(entry));
            }
        }
        List<CardFace> l = new ArrayList<>(light), d = new ArrayList<>(dark);
        Comparator<CardFace> order = Comparator.comparingInt((CardFace f) -> f.type().sortOrder)
            .thenComparingInt(f -> f.color.ordinal())
            .thenComparingInt(f -> f.number);
        l.sort(order);
        d.sort(order);
        List<CardFace[]> out = new ArrayList<>();
        int n = Math.max(l.size(), d.size());
        for (int i = 0; i < n; i++) {
            out.add(new CardFace[] { l.get(i % l.size()), d.isEmpty() ? null : d.get(i % d.size()) });
        }
        return out;
    }

    private void dealDebugHand() {
        CardPlayer me = deAmputate(debugger);
        if (me == null) return;
        deck.addAll(0, me.hand);
        me.hand.clear();
        debugHand.clear();
        debugHand.addAll(debugCards(darkSide));
        debugSide = darkSide;
        me.hand.addAll(debugHand);
        currentPlayerIndex = players.indexOf(me);
    }

    // the debugger's cards never run out: whatever left the hand comes back as a fresh copy, and whatever came in
    // goes back to the deck
    private void restoreDebugHand() {
        if (!debug) return;
        CardPlayer me = deAmputate(debugger);
        if (me == null) return;
        if (debugHand.isEmpty()) {
            debugHand.addAll(debugCards(darkSide));
            debugSide = darkSide;
        }
        if (debugSide != darkSide) {
            for (Card c : me.hand) if (debugHand.stream()
                .noneMatch(d -> d.uid == c.uid)) deck.add(0, c);
            me.hand.clear();
            debugHand.clear();
            debugHand.addAll(debugCards(darkSide));
            debugSide = darkSide;
        }
        Set<Integer> held = new HashSet<>();
        for (Card c : me.hand) held.add(c.uid);
        Set<Integer> own = new HashSet<>();
        for (int i = 0; i < debugHand.size(); i++) {
            Card c = debugHand.get(i);
            if (!held.contains(c.uid)) debugHand.set(i, c = new Card(debugUid++, c.light, c.dark));
            own.add(c.uid);
        }
        for (Card c : me.hand) if (!own.contains(c.uid)) deck.add(0, c);
        me.hand.clear();
        me.hand.addAll(debugHand);
        me.eliminated = false;
        // debug cards handed to others (hand swaps) are taken back; they get a normal hand of at most 7 instead
        for (CardPlayer p : players) {
            if (p == me) continue;
            int taken = 0;
            for (java.util.Iterator<Card> it = p.hand.iterator(); it.hasNext();) {
                if (it.next().uid >= 2_000_000) {
                    it.remove();
                    taken++;
                }
            }
            for (int i = 0; i < Math.min(taken, 7) && !deck.isEmpty(); i++) {
                Card c = deck.remove(deck.size() - 1);
                c.owner = p.uuid;
                p.hand.add(c);
            }
        }
    }

    private ActionReport debugged(ActionReport report) {
        restoreDebugHand();
        return report;
    }

    public ActionReport playCard(CardPlayer cardPlayer, int cardUid, PlayContext.PlayChoices choices) {
        return debugged(playCardInner(cardPlayer, cardUid, choices));
    }

    public ActionReport playNoCard(CardPlayer cardPlayer) {
        return debugged(playNoCardInner(cardPlayer));
    }

    public ActionReport answerDecision(CardPlayer player, int answer, UUID target) {
        return debugged(answerDecisionInner(player, answer, target));
    }

    public ActionReport tickDecisions(long now, Predicate<UUID> isHuman) {
        return debugged(tickDecisionsInner(now, isHuman));
    }

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

    public ActionReport initiate(CardPlayer cardPlayer, int initialCardCount) {
        if (players.size() < (debug ? 1 : 2)) return ActionReport.NO_GAME;
        TableConfig config = config();
        players.sort(Comparator.comparingInt(p -> p.seat));
        currentPlayerIndex = random.nextInt(players.size());
        pendingDraw = 0;
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        isAntiClockwise = false;
        darkSide = false;

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
        boolean official = config.getBool(GameRules.OFFICIAL_START);
        boolean hasNumber = deck.stream()
            .anyMatch(c -> c.light.isNumber() && !c.light.isWild());
        Card tobeTopCard = deck.remove(deck.size() - 1);
        int guard = 0;
        while (hasNumber && !acceptableOpening(tobeTopCard.light, official) && guard++ < 500) {
            deck.add(0, tobeTopCard);
            tobeTopCard = deck.remove(deck.size() - 1);
        }
        topCard = tobeTopCard;
        currentColor = topFace().color;
        if (official) applyOpening(tobeTopCard.light);
        if (debug) dealDebugHand();
        ActionReport report = ActionReport.builder(this, cardPlayer);
        GameSounds.deal(report, initialCardCount + 3);
        report.sound(GameSounds.PLAY, report.soundTail() + 250);
        GameSounds.turn(report, players.get(currentPlayerIndex), false, report.soundTail() + 500);
        return report.gameStarted();
    }

    private static boolean acceptableOpening(CardFace face, boolean official) {
        if (face.isNumber() && !face.isWild()) return true;
        if (!official) return false;
        CardType type = face.type();
        return type == CardTypes.SKIP || type == CardTypes.REVERSE || type == CardTypes.DRAW2 || type == CardTypes.WILD;
    }

    void applyOpening(CardFace face) {
        CardType type = face.type();
        CardPlayer first = players.get(currentPlayerIndex);
        if (type == CardTypes.SKIP) {
            currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        } else if (type == CardTypes.REVERSE) {
            if (activePlayers().size() == 2) currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
            else isAntiClockwise = true;
        } else if (type == CardTypes.DRAW2) {
            drawCardsRaw(first, 2, null);
            currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        } else if (type == CardTypes.WILD) {
            currentColor = CardColor.WILD;
        }
    }

    public String checkPlayable(CardPlayer player, Card card, boolean useFlex) {
        if (isDebugger(player)) return null;
        CardFace face = face(card);
        CardType type = face.type();
        if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN && card.uid != drawnCardUid) {
            return "game.minopp.play.only_drawn_card";
        }
        if (currentPlayerPhase == PlayerActionPhase.CHAIN) {
            return face.isNumber() && !face.isWild() && face.number == chainNumber && !useFlex ? null
                : "game.minopp.play.chain_only";
        }
        if (currentPlayerPhase == PlayerActionPhase.BRING) {
            return !face.isWild() && face.color == currentColor && !useFlex ? null : "game.minopp.play.bring_only";
        }
        if (rule(GameRules.NUMBER_FINISH) && player.hand.size() == 1 && !face.isNumber()) {
            return "game.minopp.play.number_finish";
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
            if (rule(GameRules.DEFLECT_DRAW) && !useFlex
                && (type.matchKey.equals("skip") || type.matchKey.equals("reverse"))
                && (face.color == currentColor || face.isWild()
                    || face.symbolKey()
                        .equals(topFace().symbolKey()))) {
                return null;
            }
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

    private ActionReport playCardInner(CardPlayer cardPlayer, int cardUid, PlayContext.PlayChoices choices) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        cardPlayer.coinLocked = true;
        int playerIndex = players.indexOf(cardPlayer);
        if (playerIndex == -1 || cardPlayer.eliminated)
            return report.fail(Chat.translatable("game.minopp.play.no_player"));
        Card card = cardPlayer.hand.stream()
            .filter(c -> c.uid == cardUid)
            .findFirst()
            .orElse(null);
        if (card == null) return report.fail(Chat.translatable("game.minopp.play.not_your_card"));
        if (!decisions.isEmpty()) return report.fail(Chat.translatable("game.minopp.play.waiting_decision"));

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

        boolean brought = currentPlayerPhase == PlayerActionPhase.BRING && !isCut;
        int keptSkip = skipCount;
        boolean keptExtra = extraTurn;
        colorBeforePlay = currentColor;
        skipCount = 0;
        extraTurn = false;
        deferAdvance = false;
        if (brought) {
            // the action card that opened the bring still takes effect when the turn moves on
            skipCount = keptSkip;
            extraTurn = keptExtra;
            currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        }
        doDiscardCard(cardPlayer, card, report);
        GameSounds.cardPlayed(report, type, useFlex);

        if (!type.keepsColor) {
            if (face.isWild()) {
                if (choices.color != null) currentColor = choices.color;
            } else {
                currentColor = useFlex && face.flexColor != null ? face.flexColor : face.color;
            }
        }
        if (useFlex) {
            cardPlayer.powerOn = false;
            normalizePower();
        }
        if (type.drawValue == 0 || useFlex) {
            if (pendingDraw == 0) challengeable = null;
        }

        if (cardPlayer.hand.isEmpty() && !relay(cardPlayer, report)) {
            if (type.isDraw() && !useFlex && config().getInt(GameRules.SCORE_TARGET) > 0) {
                CardPlayer next = nextPlayer();
                if (!next.equals(cardPlayer)) drawCards(next, pendingDraw + type.drawValue, report);
            }
            return win(cardPlayer, report);
        }

        PlayContext ctx = new PlayContext(this, cardPlayer, card, face, choices, report);
        CardType.Effect effect = useFlex ? type.flexEffect : type.effect;
        if (effect != null) effect.apply(ctx);
        if (cardPlayer.coinArmed && type.isDraw() && !useFlex) {
            cardPlayer.coinArmed = false;
            pendingDraw += type.drawValue;
            lastDrawValue = Math.max(lastDrawValue, type.drawValue * 2);
            report.messageAll(Chat.translatable("game.minopp.coin.doubled", cardPlayer.name, pendingDraw));
        }

        if (danceOwner != null && !cardPlayer.uuid.equals(danceOwner) && !face.isNumber()) {
            drawCards(cardPlayer, 2, report);
            report.messageAll(Chat.translatable("game.minopp.play.dance_penalty", cardPlayer.name));
        }

        if (choices.shout && cardPlayer.hand.size() < 2 && !cardPlayer.hasShoutedMino) {
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

        if (!deferAdvance) {
            if (!isCut && speedLevel > 1 && pendingDraw == 0 && decisions.isEmpty()) {
                playedThisTurn++;
                if (playedThisTurn < speedLevel) {
                    currentPlayerPhase = PlayerActionPhase.SPEED;
                    return report.speed(speedLevel - playedThisTurn, speedLevel);
                }
                advanceTurn(report);
                return report.played();
            }
            if (!isCut && !brought
                && speedLevel <= 1
                && rule(GameRules.BRING_CARD)
                && !face.isNumber()
                && !face.isWild()
                && !useFlex
                && decisions.isEmpty()
                && hasColor(cardPlayer, currentColor)) {
                currentPlayerPhase = PlayerActionPhase.BRING;
                return report.bring();
            }
            if (!isCut && rule(GameRules.MULTI_PLAY)
                && face.isNumber()
                && !face.isWild()
                && pendingDraw == 0
                && hasNumber(cardPlayer, face.number)) {
                currentPlayerPhase = PlayerActionPhase.CHAIN;
                chainNumber = face.number;
                return report.chain(face.number);
            }
            advanceTurn(report);
        }
        return isCut ? report.cut() : report.played();
    }

    private ActionReport checkEndConditions(CardPlayer actor, ActionReport report) {
        for (CardPlayer p : players) {
            if (!p.eliminated && p.hand.isEmpty() && !isDebugger(p) && !relay(p, report)) return win(p, report);
        }
        applyMercy(report);
        List<CardPlayer> active = activePlayers();
        if (active.size() == 1 && !debug) return win(active.get(0), report);
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

    private ActionReport playNoCardInner(CardPlayer cardPlayer) {
        ActionReport report = ActionReport.builder(this, cardPlayer);
        cardPlayer.coinLocked = true;
        int playerIndex = players.indexOf(cardPlayer);
        if (playerIndex == -1 || cardPlayer.eliminated)
            return report.fail(Chat.translatable("game.minopp.play.no_player"));
        if (playerIndex != currentPlayerIndex || awaitingCutIn)
            return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
        if (!decisions.isEmpty()) return report.fail(Chat.translatable("game.minopp.play.waiting_decision"));

        if (currentPlayerPhase == PlayerActionPhase.SPEED
            || speedLevel > 1 && pendingDraw == 0 && currentPlayerPhase == PlayerActionPhase.DISCARD_HAND) {
            int amount = speedLevel;
            speedLevel = 1;
            drawCards(cardPlayer, amount, report);
            report.sound(GameSounds.PASS, 0);
            ActionReport end = checkEndConditions(cardPlayer, report);
            if (end != null) return end;
            advanceTurn(report);
            return report.state(Chat.translatable("game.minopp.play.crash", cardPlayer.name, amount));
        }
        if (currentPlayerPhase == PlayerActionPhase.CHAIN || currentPlayerPhase == PlayerActionPhase.BRING) {
            report.sound(GameSounds.PASS, 0);
            advanceTurn(report);
            return report.state(Chat.translatable("game.minopp.play.chain_end", cardPlayer.name));
        }
        if (currentPlayerPhase == PlayerActionPhase.DISCARD_DRAWN) {
            report.sound(GameSounds.PASS, 0);
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

        boolean playable = last != null && !isDebugger(cardPlayer) && canPlay(cardPlayer, last);
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
        advanceTurn(report);
        return report.drew(drawn);
    }

    public ActionReport shoutMino(CardPlayer realPlayer) {
        ActionReport report = ActionReport.builder(this, realPlayer);
        if (!realPlayer.hasShoutedMino) {
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

    private ActionReport answerDecisionInner(CardPlayer player, int answer, UUID target) {
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

    private ActionReport tickDecisionsInner(long now, Predicate<UUID> isHuman) {
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
            case CHALLENGE -> {
                ActionReport end = resolveChallenge(first, report);
                if (end != null) return end;
            }
            case ROULETTE_COLOR -> {
                CardPlayer victim = deAmputate(first.player);
                CardColor color = CardColor.values()[Math.min(first.answer, CardColor.values().length - 1)];
                if (!CardColor.forSide(darkSide)
                    .contains(color)) color = mostCommonColor(victim);
                int count = drawUntilColor(victim, color, report);
                currentColor = color;
                report.messageAll(
                    Chat.translatable(
                        "game.minopp.play.roulette",
                        victim.name,
                        Chat.translatable(color.langKey()),
                        count));
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

    private ActionReport resolveChallenge(PendingDecision d, ActionReport report) {
        CardPlayer victim = deAmputate(d.player);
        CardPlayer source = deAmputate(d.source);
        challengeable = null;
        if (d.answer == 1 && source != null) {
            if (challengeableHadColor) {
                report.messageAll(Chat.translatable("game.minopp.play.challenge_success", victim.name, source.name));
                drawCards(source, pendingDraw, report);
                pendingDraw = 0;
                lastDrawValue = 0;
                currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
                return checkEndConditions(victim, report);
            } else {
                report.messageAll(Chat.translatable("game.minopp.play.challenge_fail", victim.name));
                pendingDraw += 2;
            }
        }
        takePendingDraw(victim, report);
        ActionReport end = checkEndConditions(victim, report);
        if (end != null) return end;
        advanceTurn(report);
        return null;
    }

    public void advanceTurn(ActionReport report) {
        turnSerial++;
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        drawnCardUid = -1;
        chainNumber = -1;
        playedThisTurn = 0;
        if (pendingSpeed > 0) {
            speedLevel = Math.min(5, speedLevel + pendingSpeed);
            pendingSpeed = 0;
        }
        if (revealTurns > 0) revealTurns--;
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
        debugNext = -1;
        if (debug && debugHold) {
            CardPlayer me = deAmputate(debugger);
            if (me != null && !me.eliminated && players.indexOf(me) != currentPlayerIndex) {
                debugNext = currentPlayerIndex;
                // the penalty still lands on whoever would have played, so their draw can be watched
                if (pendingDraw > 0) takePendingDraw(players.get(debugNext), report);
                currentPlayerIndex = players.indexOf(me);
            }
        }
        CardPlayer current = players.get(currentPlayerIndex);

        hiddenCounts.remove(current.uuid);
        if (hurryTurns > 0) {
            hurryTurns--;
            scheduleHurry(current);
        }
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

    public ActionReport resolveStuckPenalty() {
        ActionReport report = ActionReport.builder(this, currentPlayer());
        CardPlayer current = currentPlayer();
        current.hasShoutedMino = false;
        if (pendingDraw <= 0) GameSounds.turn(report, current, false, 0);
        resolvePenaltyFor(current, report);
        return report;
    }

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
        currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        currentPlayerIndex = nextActiveIndex(currentPlayerIndex);
        CardPlayer nextPlayer = players.get(currentPlayerIndex);
        nextPlayer.hasShoutedMino = false;
        GameSounds.turn(report, nextPlayer, false, Math.max(900, report.soundTail() + 300));
    }

    private void takePendingDraw(CardPlayer victim, ActionReport report) {
        int amount = pendingDraw;
        UUID source = pendingDrawSource;
        if (source != null && victim.uuid.equals(nemesis.get(source))) {
            amount += 2;
            report.messageAll(Chat.translatable("game.minopp.play.nemesis_hit", victim.name));
        }
        pendingDraw = 0;
        lastDrawValue = 0;
        pendingDrawSource = null;
        challengeable = null;
        drawCards(victim, amount, report);
    }

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

    // reveal the hand: the next player draws one card per action and wild card in it and loses the turn; seven or
    // more makes it 25 for them and 5 for everyone else
    public void finalAttack(CardPlayer player, ActionReport report) {
        int actions = (int) player.hand.stream()
            .filter(c -> !face(c).isNumber() || face(c).isWild())
            .count();
        int from = players.indexOf(player);
        CardPlayer next = players.get(nextActiveIndex(from < 0 ? currentPlayerIndex : from));
        revealTurns = Math.max(revealTurns, 1);
        report.messageAll(Chat.translatable("game.minopp.play.final_attack", player.name, actions, next.name));
        if (next.equals(player)) return;
        if (actions >= 7) {
            drawCards(next, 25, report);
            for (CardPlayer p : activePlayers()) if (!p.equals(player) && !p.equals(next)) drawCards(p, 5, report);
        } else {
            drawCards(next, actions, report);
        }
        skipNext(1);
    }

    // everyone, the player included, draws up to 24 cards
    public void suddenDeath(ActionReport report) {
        for (CardPlayer p : activePlayers()) {
            if (p.hand.size() < 24) drawCards(p, 24 - p.hand.size(), report);
        }
        report.messageAll(Chat.translatable("game.minopp.play.sudden_death"));
    }

    /**
     * The coin: {@code flip} turns it to the other side while it is still undeclared (before the holder's first
     * action); otherwise it is spent. Mercy swaps the whole hand for 7 fresh cards; No Mercy doubles the next draw
     * card played.
     */
    public ActionReport useCoin(CardPlayer player, boolean flip) {
        ActionReport report = ActionReport.builder(this, player);
        if (!rule(GameRules.COINS)) return report.fail(Chat.translatable("game.minopp.coin.off"));
        if (player.coinUsed) return report.fail(Chat.translatable("game.minopp.coin.used"));
        if (flip) {
            if (player.coinLocked) return report.fail(Chat.translatable("game.minopp.coin.locked"));
            player.coinNoMercy = !player.coinNoMercy;
            report.sound(new ResourceLocation("minecraft", "random.click"), 0, 0.6f, 1.6f);
            return report.state(
                Chat.translatable(
                    player.coinNoMercy ? "game.minopp.coin.side_no_mercy" : "game.minopp.coin.side_mercy"));
        }
        if (players.indexOf(player) != currentPlayerIndex || awaitingCutIn) {
            return report.fail(Chat.translatable("game.minopp.play.not_your_turn"));
        }
        player.coinUsed = true;
        player.coinLocked = true;
        if (player.coinNoMercy) {
            player.coinArmed = true;
            report.sound(new ResourceLocation("minecraft", "mob.wither.shoot"), 0, 0.5f, 1.4f);
            report.messageAll(Chat.translatable("game.minopp.coin.no_mercy", player.name));
        } else {
            mercySwap(player, report);
            report.messageAll(Chat.translatable("game.minopp.coin.mercy", player.name));
        }
        return report.state(Chat.translatable("game.minopp.coin.spent", player.name));
    }

    private void mercySwap(CardPlayer player, ActionReport report) {
        discardDeck.addAll(0, player.hand);
        player.hand.clear();
        drawCards(player, 7, report);
        report.sound(new ResourceLocation("minecraft", "random.levelup"), 0, 0.6f, 1.2f);
    }

    public void giveExtraTurn() {
        extraTurn = true;
    }

    public int turnSerial;
    public boolean trapArmed;
    public int hurryTurns;
    public final Set<UUID> hiddenCounts = new HashSet<>();
    private final Map<UUID, Long> peekUntil = new HashMap<>();

    public boolean isPeeking(UUID viewer) {
        if (revealTurns > 0) return true;
        Long until = peekUntil.get(viewer);
        return until != null && until > System.currentTimeMillis();
    }

    private CardPlayer randomOpponent(CardPlayer player) {
        List<CardPlayer> others = activePlayers();
        others.remove(player);
        return others.isEmpty() ? null : others.get(random.nextInt(others.size()));
    }

    private void toDeckBottom(Card c) {
        c.owner = null;
        deck.add(0, c);
    }

    private boolean relay(CardPlayer player, ActionReport report) {
        if (!rule(GameRules.TEAM_RELAY) || !config().getBool(GameRules.TEAM_MODE) || player.team < 0) return false;
        CardPlayer partner = null;
        for (CardPlayer p : players) {
            if (p != player && !p.eliminated
                && p.team == player.team
                && p.hand.size() >= 2
                && (partner == null || p.hand.size() > partner.hand.size())) partner = p;
        }
        if (partner == null) return false;
        int n = partner.hand.size() / 2;
        for (int i = 0; i < n; i++) {
            Card c = partner.hand.remove(random.nextInt(partner.hand.size()));
            c.owner = player.uuid;
            player.hand.add(c);
        }
        player.hasShoutedMino = false;
        partner.hasShoutedMino = false;
        report.messageAll(Chat.translatable("game.minopp.play.relay", player.name, partner.name, n));
        return true;
    }

    public void smoke(CardPlayer player, ActionReport report) {
        hiddenCounts.add(player.uuid);
        peekUntil.put(player.uuid, System.currentTimeMillis() + 8000);
        report.messageAll(Chat.translatable("game.minopp.play.smoke", player.name));
        if (tableEntity != null) {
            final CardGame self = this;
            TaskScheduler.Holder.INSTANCE.schedule(
                8 * 20 + 2,
                () -> { if (tableEntity != null && tableEntity.game == self) tableEntity.sync(); });
        }
    }

    public void echo(PlayContext ctx) {
        if (discardDeck.isEmpty()) return;
        Card below = discardDeck.get(discardDeck.size() - 1);
        CardFace face = face(below);
        CardType type = face.type();
        if (type == CardTypes.WILD_ECHO || type.effect == null) return;
        PlayContext.PlayChoices choices = AutoPlayer.defaultChoices(this, ctx.player, below);
        if (ctx.choices.color != null) choices.color = ctx.choices.color;
        type.effect.apply(new PlayContext(this, ctx.player, below, face, choices, ctx.report));
        ctx.report.messageAll(Chat.translatable("game.minopp.play.echo", ctx.player.name, face.getDisplayName()));
    }

    public void crossfade(ActionReport report) {
        List<CardPlayer> active = activePlayers();
        int n = active.size();
        if (n < 2) return;
        int offset = n / 2;
        List<List<Card>> hands = new ArrayList<>();
        for (CardPlayer p : active) hands.add(new ArrayList<>(p.hand));
        for (int i = 0; i < n; i++) {
            CardPlayer to = active.get((i + offset) % n);
            to.hand.clear();
        }
        for (int i = 0; i < n; i++) {
            CardPlayer to = active.get((i + offset) % n);
            for (Card c : hands.get(i)) c.owner = to.uuid;
            to.hand.addAll(hands.get(i));
            to.hasShoutedMino = false;
        }
        handMoveKind = 0;
        handMoveCounter++;
        report.messageAll(Chat.translatable("game.minopp.play.crossfade"));
    }

    public void evenOut(ActionReport report) {
        int min = Integer.MAX_VALUE;
        for (CardPlayer p : activePlayers()) if (!p.hand.isEmpty()) min = Math.min(min, p.hand.size());
        if (min == Integer.MAX_VALUE) return;
        for (CardPlayer p : activePlayers()) {
            while (p.hand.size() > min) toDeckBottom(p.hand.remove(random.nextInt(p.hand.size())));
        }
        report.messageAll(Chat.translatable("game.minopp.play.even_out", min));
    }

    public void surprise(CardPlayer player, ActionReport report) {
        String key;
        switch (random.nextInt(6)) {
            case 0 -> {
                skipNext(1);
                key = "skip";
            }
            case 1 -> {
                reverse();
                key = "reverse";
            }
            case 2 -> {
                addPendingDraw(2, player);
                key = "draw2";
            }
            case 3 -> {
                for (CardPlayer p : activePlayers()) if (!p.equals(player)) drawCards(p, 1, report);
                key = "all_draw";
            }
            case 4 -> {
                CardPlayer other = randomOpponent(player);
                if (other != null) swapHands(player, other, report);
                key = "swap";
            }
            default -> {
                if (!player.hand.isEmpty()) toDeckBottom(player.hand.remove(random.nextInt(player.hand.size())));
                key = "discard";
            }
        }
        report.messageAll(
            Chat.translatable(
                "game.minopp.play.surprise",
                player.name,
                Chat.translatable("game.minopp.play.surprise." + key)));
    }

    public void cardStorm(CardPlayer player, ActionReport report) {
        List<CardPlayer> others = activePlayers();
        others.remove(player);
        if (others.isEmpty()) return;
        for (int i = 0; i < 5; i++) drawCards(others.get(random.nextInt(others.size())), 1, report);
        List<CardPlayer> everyone = activePlayers();
        for (int i = 0; i < 5; i++) drawCards(everyone.get(random.nextInt(everyone.size())), 1, report);
        report.messageAll(Chat.translatable("game.minopp.play.card_storm", player.name));
    }

    public void armTrap(CardPlayer player, ActionReport report) {
        trapArmed = true;
        report.messageAll(Chat.translatable("game.minopp.play.trap_armed", player.name));
    }

    public void hurryUp(CardPlayer player, ActionReport report) {
        hurryTurns = 3;
        report.messageAll(Chat.translatable("game.minopp.play.hurry", player.name));
    }

    public void shield(CardPlayer player, ActionReport report) {
        if (pendingDraw <= 0) return;
        int blocked = pendingDraw;
        pendingDraw = 0;
        lastDrawValue = 0;
        pendingDrawSource = null;
        challengeable = null;
        report.messageAll(Chat.translatable("game.minopp.play.shield", player.name, blocked));
    }

    public void trickster(CardPlayer player, CardPlayer target, ActionReport report) {
        if (target.equals(player)) return;
        drawCards(target, 4, report);
        for (int i = 0; i < 2 && !target.hand.isEmpty(); i++) {
            toDeckBottom(target.hand.remove(random.nextInt(target.hand.size())));
        }
        report.messageAll(Chat.translatable("game.minopp.play.trickster", player.name, target.name));
    }

    public void surge(CardPlayer player, ActionReport report) {
        player.hand.sort((a, b) -> Integer.compare(cardPoints(b), cardPoints(a)));
        int n = Math.min(2, player.hand.size());
        for (int i = 0; i < n; i++) discardDeck.add(player.hand.remove(0));
        if (player.hand.size() == 1) player.hasShoutedMino = true;
        report.messageAll(Chat.translatable("game.minopp.play.surge", player.name, n));
    }

    public void teamPlay(CardPlayer player, ActionReport report) {
        if (!config().getBool(GameRules.TEAM_MODE) || player.team < 0) return;
        int idx = players.indexOf(player);
        for (int i = 1; i < players.size(); i++) {
            int j = ((idx + (isAntiClockwise ? -i : i)) % players.size() + players.size()) % players.size();
            CardPlayer p = players.get(j);
            if (!p.eliminated && p.team == player.team) {
                forcedNext = p.uuid;
                report.messageAll(Chat.translatable("game.minopp.play.team_play", player.name, p.name));
                return;
            }
        }
    }

    public void teamDraw(CardPlayer player, int amount, ActionReport report) {
        boolean teams = config().getBool(GameRules.TEAM_MODE) && player.team >= 0;
        int idx = players.indexOf(player);
        int hit = 0;
        for (int i = 1; i < players.size() && hit < 2; i++) {
            int j = ((idx + (isAntiClockwise ? -i : i)) % players.size() + players.size()) % players.size();
            CardPlayer p = players.get(j);
            if (p.eliminated || teams && p.team == player.team) continue;
            drawCards(p, amount, report);
            hit++;
        }
        CardPlayer next = nextPlayer();
        if (!teams || next.team != player.team) skipNext(1);
        report.messageAll(Chat.translatable("game.minopp.play.team_draw", player.name, amount));
    }

    private boolean hasColor(CardPlayer player, CardColor color) {
        for (Card c : player.hand) {
            CardFace f = face(c);
            if (!f.isWild() && f.color == color) return true;
        }
        return false;
    }

    private boolean hasNumber(CardPlayer player, int number) {
        for (Card c : player.hand) {
            CardFace f = face(c);
            if (f.isNumber() && !f.isWild() && f.number == number) return true;
        }
        return false;
    }

    private static final int[] BLAST = { 0, 0, 0, 0, 1, 1, 2, 3, 4, 6 };
    private static final int[] BLAST_ALL = { 0, 0, 0, 1, 1, 2, 3 };

    public void blaster(CardPlayer player, ActionReport report) {
        CardPlayer victim = nextPlayer();
        int n = BLAST[random.nextInt(BLAST.length)];
        drawCards(victim, n, report);
        skipNext(1);
        report.messageAll(Chat.translatable("game.minopp.play.blaster", victim.name, n));
    }

    public void blastAll(CardPlayer player, ActionReport report) {
        for (CardPlayer p : activePlayers()) {
            if (p.equals(player)) continue;
            int n = BLAST_ALL[random.nextInt(BLAST_ALL.length)];
            if (n > 0) drawCards(p, n, report);
            report.messageAll(Chat.translatable("game.minopp.play.blaster", p.name, n));
        }
    }

    public void gift(CardPlayer player, ActionReport report) {
        if (player.hand.isEmpty()) return;
        CardPlayer receiver = nextPlayer();
        Card c = player.hand.remove(random.nextInt(player.hand.size()));
        c.owner = receiver.uuid;
        receiver.hand.add(c);
        report.messageAll(Chat.translatable("game.minopp.play.gift", player.name, receiver.name));
    }

    public void cyclone(ActionReport report) {
        List<CardPlayer> active = activePlayers();
        int size = active.size();
        if (size < 2) return;
        Card[] passed = new Card[size];
        for (int i = 0; i < size; i++) {
            List<Card> hand = active.get(i).hand;
            if (!hand.isEmpty()) passed[i] = hand.remove(random.nextInt(hand.size()));
        }
        for (int i = 0; i < size; i++) {
            if (passed[i] == null) continue;
            int to = isAntiClockwise ? (i - 1 + size) % size : (i + 1) % size;
            passed[i].owner = active.get(to).uuid;
            active.get(to).hand.add(passed[i]);
        }
        handMoveKind = 0;
        handMoveCounter++;
        report.messageAll(Chat.translatable("game.minopp.play.cyclone"));
    }

    public void steal(CardPlayer player, CardPlayer target, ActionReport report) {
        if (target.equals(player) || target.hand.isEmpty()) return;
        Card c = target.hand.remove(random.nextInt(target.hand.size()));
        c.owner = player.uuid;
        player.hand.add(c);
        target.hasShoutedMino = false;
        report.messageAll(Chat.translatable("game.minopp.play.thief", player.name, target.name));
    }

    // ---- Add-On packs

    public int speedLevel = 1, pendingSpeed, playedThisTurn;
    public int revealTurns;
    public final Map<UUID, UUID> nemesis = new HashMap<>();

    public void speedUp(int amount, ActionReport report) {
        pendingSpeed += amount;
        report.messageAll(Chat.translatable("game.minopp.play.speed_up", Math.min(5, speedLevel + pendingSpeed)));
    }

    public void stackNumber(CardPlayer player, ActionReport report) {
        int n = 0;
        for (int guard = 0; guard < 60 && !deck.isEmpty(); guard++) {
            Card c = deck.remove(deck.size() - 1);
            c.owner = null;
            discardDeck.add(0, c);
            CardFace f = face(c);
            if (f.isNumber() && !f.isWild()) {
                n = Math.max(0, f.number);
                break;
            }
        }
        if (n > 0) addPendingDraw(n, player);
        report.messageAll(Chat.translatable("game.minopp.play.stack_number", player.name, n));
    }

    public void swapOne(CardPlayer player, CardPlayer target, ActionReport report) {
        if (target == null || target.equals(player) || target.hand.isEmpty()) return;
        Card taken = target.hand.remove(random.nextInt(target.hand.size()));
        Card give = player.hand.stream()
            .max(Comparator.comparingInt(this::cardPoints))
            .orElse(null);
        taken.owner = player.uuid;
        player.hand.add(taken);
        if (give != null) {
            player.hand.remove(give);
            give.owner = target.uuid;
            target.hand.add(give);
        }
        player.hasShoutedMino = false;
        target.hasShoutedMino = false;
        report.messageAll(Chat.translatable("game.minopp.play.swap_one", player.name, target.name));
    }

    public void refreshHand(CardPlayer player, ActionReport report) {
        int n = player.hand.size();
        if (n == 0) return;
        for (Card c : new ArrayList<>(player.hand)) toDeckBottom(c);
        player.hand.clear();
        player.hasShoutedMino = false;
        drawCards(player, n, report);
        report.messageAll(Chat.translatable("game.minopp.play.refresh_hand", player.name, n));
    }

    public void forceTrade(CardPlayer a, CardPlayer b, ActionReport report) {
        if (a == null || b == null || a.equals(b)) return;
        swapHands(a, b, report);
        a.hasShoutedMino = false;
        b.hasShoutedMino = false;
        report.messageAll(Chat.translatable("game.minopp.play.force_trade", a.name, b.name));
    }

    public void passHands(ActionReport report) {
        rotateHands(report);
        for (CardPlayer p : players) p.hasShoutedMino = false;
        report.messageAll(Chat.translatable("game.minopp.play.pass_hands"));
    }

    private boolean turnReverses() {
        if (activePlayers().size() <= 2) return false;
        isAntiClockwise = !isAntiClockwise;
        return true;
    }

    public void reverseSkip() {
        turnReverses();
        skipNext(1);
    }

    public void reverseDraw2(CardPlayer player) {
        turnReverses();
        addPendingDraw(2, player);
    }

    public void powerReverse() {
        turnReverses();
        giveExtraTurn();
    }

    private CardPlayer drawSource(CardPlayer player) {
        if (pendingDraw <= 0 || pendingDrawSource == null || pendingDrawSource.equals(player.uuid)) return null;
        CardPlayer source = deAmputate(pendingDrawSource);
        return source == null || source.eliminated ? null : source;
    }

    public void noU(CardPlayer player, ActionReport report) {
        CardPlayer source = drawSource(player);
        if (source == null) {
            reverse();
            return;
        }
        int amount = pendingDraw;
        takePendingDraw(source, report);
        report.messageAll(Chat.translatable("game.minopp.play.no_u", player.name, source.name, amount));
    }

    public void block(CardPlayer player, ActionReport report) {
        if (pendingDraw <= 0) return;
        int amount = pendingDraw;
        pendingDraw = 0;
        lastDrawValue = 0;
        pendingDrawSource = null;
        challengeable = null;
        report.messageAll(Chat.translatable("game.minopp.play.block", player.name, amount));
    }

    public void blockRebound(CardPlayer player, ActionReport report) {
        CardPlayer source = drawSource(player);
        if (source == null) return;
        int amount = pendingDraw + 1;
        pendingDraw = amount;
        takePendingDraw(source, report);
        report.messageAll(Chat.translatable("game.minopp.play.no_u", player.name, source.name, amount));
    }

    public void blockDeflect(CardPlayer player, ActionReport report) {
        if (pendingDraw > 0)
            report.messageAll(Chat.translatable("game.minopp.play.deflect_on", player.name, nextPlayer().name));
    }

    public void redirect(CardPlayer player, CardPlayer target, ActionReport report) {
        if (pendingDraw <= 0 || target == null || target.equals(player)) return;
        int amount = pendingDraw;
        takePendingDraw(target, report);
        report.messageAll(Chat.translatable("game.minopp.play.redirect", player.name, target.name, amount));
    }

    public void eliminator(CardPlayer player, ActionReport report) {
        int total = 0;
        for (CardPlayer p : activePlayers()) {
            if (p.equals(player)) continue;
            List<Card> wilds = p.hand.stream()
                .filter(c -> face(c).isWild())
                .toList();
            for (Card c : wilds) {
                p.hand.remove(c);
                c.owner = null;
                discardDeck.add(0, c);
            }
            total += wilds.size();
        }
        report.messageAll(Chat.translatable("game.minopp.play.eliminator", player.name, total));
    }

    public void markNemesis(CardPlayer player, CardPlayer target, ActionReport report) {
        if (target == null || target.equals(player)) return;
        nemesis.put(player.uuid, target.uuid);
        report.messageAll(Chat.translatable("game.minopp.play.nemesis", player.name, target.name));
    }

    private int largestHand() {
        int max = 0;
        for (CardPlayer p : activePlayers()) max = Math.max(max, p.hand.size());
        return max;
    }

    public void matchTheMax(CardPlayer target, ActionReport report) {
        if (target == null) return;
        int n = largestHand() - target.hand.size();
        if (n > 0) drawCards(target, n, report);
        report.messageAll(Chat.translatable("game.minopp.play.match_max", target.name, Math.max(0, n)));
    }

    public void misery(CardPlayer player, ActionReport report) {
        int max = largestHand();
        for (CardPlayer p : activePlayers()) {
            if (!p.equals(player) && p.hand.size() < max) drawCards(p, max - p.hand.size(), report);
        }
        report.messageAll(Chat.translatable("game.minopp.play.misery", player.name, max));
    }

    public void pullForChat(CardPlayer player, CardPlayer target, ActionReport report) {
        if (target == null || target.equals(player)) return;
        peekUntil.put(player.uuid, System.currentTimeMillis() + 6000);
        swapOne(player, target, report);
    }

    public void gotAText(CardPlayer player, CardColor color, ActionReport report) {
        int twist = random.nextInt(4);
        report.messageAll(Chat.translatable("game.minopp.play.text." + twist, player.name));
        switch (twist) {
            case 0 -> everyoneElseDraws(player, 1, report);
            case 1 -> {
                CardPlayer other = randomOpponent(player);
                if (other != null) swapHands(player, other, report);
            }
            case 2 -> passHands(report);
            default -> colorChallenge(player, color, report);
        }
    }

    public void passColor(CardColor color, ActionReport report) {
        List<CardPlayer> active = activePlayers();
        int size = active.size();
        if (size < 2 || color == null) return;
        List<List<Card>> passed = new ArrayList<>();
        for (CardPlayer p : active) {
            List<Card> out = p.hand.stream()
                .filter(c -> !face(c).isWild() && face(c).color == color)
                .toList();
            p.hand.removeAll(out);
            passed.add(out);
        }
        for (int i = 0; i < size; i++) {
            CardPlayer to = active.get(isAntiClockwise ? (i - 1 + size) % size : (i + 1) % size);
            for (Card c : passed.get(i)) c.owner = to.uuid;
            to.hand.addAll(passed.get(i));
            to.hasShoutedMino = false;
        }
        handMoveKind = 0;
        handMoveCounter++;
        report.messageAll(Chat.translatable("game.minopp.play.pass_color", Chat.translatable(color.langKey())));
    }

    private boolean holdsColor(CardPlayer p, CardColor color) {
        return p.hand.stream()
            .anyMatch(c -> !face(c).isWild() && face(c).color == color);
    }

    public void trickWild(CardPlayer player, CardColor color, ActionReport report) {
        CardPlayer next = nextPlayer();
        if (color == null || next == null || next.equals(player) || holdsColor(next, color)) return;
        giveExtraTurn();
        report.messageAll(Chat.translatable("game.minopp.play.trick_wild", player.name, next.name));
    }

    public void colorChallenge(CardPlayer player, CardColor color, ActionReport report) {
        if (color == null) return;
        int others = 0, shown = 0;
        for (CardPlayer p : activePlayers()) {
            if (p.equals(player)) continue;
            others++;
            if (holdsColor(p, color)) shown++;
            else drawCards(p, 2, report);
        }
        if (others > 0 && shown == others) {
            drawCards(player, 3, report);
            report.messageAll(Chat.translatable("game.minopp.play.challenge_backfire", player.name));
        } else {
            report.messageAll(Chat.translatable("game.minopp.play.color_challenge", player.name, others - shown));
        }
    }

    public void revealHands(CardPlayer player, ActionReport report) {
        revealTurns = activePlayers().size();
        report.messageAll(Chat.translatable("game.minopp.play.reveal_hands", player.name));
    }

    public int cardPoints(Card c) {
        CardFace f = face(c);
        return f.isNumber() && !f.isWild() ? Math.max(0, f.number) : f.type().points;
    }

    public int handPoints(Collection<CardPlayer> losers) {
        int total = 0;
        for (CardPlayer p : losers) for (Card c : p.hand) total += cardPoints(c);
        return total;
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
            .toList();
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

    /** The last 50/50 toss, for the table animation: who took heads and tails, which face came up, and a counter. */
    public UUID tossHeads, tossTails;
    public boolean tossHeadsUp;
    public int tossSeq;

    public void fiftyFifty(CardPlayer player, CardPlayer first, CardPlayer second, int amount, ActionReport report) {
        if (first == null) first = nextPlayer();
        if (second == null || second.equals(first)) second = player;
        boolean headsUp = random.nextBoolean();
        // heads up means the tails player draws
        CardPlayer loser = headsUp ? second : first;
        tossHeads = first.uuid;
        tossTails = second.uuid;
        tossHeadsUp = headsUp;
        tossSeq++;
        drawCards(loser, amount, report);
        report.messageAll(Chat.translatable("game.minopp.play.fifty_fifty", loser.name, amount));
    }

    public void attachBalloon(CardPlayer player, ActionReport report) {
        balloonHolder = player.uuid;
        balloonFuse = 3 + random.nextInt(6);
        report.messageAll(Chat.translatable("game.minopp.play.balloon", player.name));
    }

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

    private void scheduleHurry(CardPlayer current) {
        if (tableEntity == null) return;
        final CardGame self = this;
        final int serial = turnSerial;
        final UUID who = current.uuid;
        TaskScheduler.Holder.INSTANCE.schedule(HURRY_TICKS, () -> {
            if (tableEntity == null || tableEntity.game != self
                || turnSerial != serial
                || awaitingCutIn
                || !decisions.isEmpty()) return;
            CardPlayer late = currentPlayer();
            if (!late.uuid.equals(who)) return;
            ActionReport report = ActionReport.builder(this, late);
            drawCards(late, 1, report);
            report.messageAll(Chat.translatable("game.minopp.play.too_slow", late.name));
            ActionReport end = checkEndConditions(late, report);
            if (end == null) advanceTurn(report);
            tableEntity.handleActionResult(end != null ? end : report, late, null);
        });
    }

    public static final int HURRY_TICKS = 6 * 20;

    public List<Card> drawCards(CardPlayer cardPlayer, int count, ActionReport report) {
        if (trapArmed && count > 0) {
            trapArmed = false;
            count += 3;
            if (report != null) {
                report.messageAll(Chat.translatable("game.minopp.play.trap_sprung", cardPlayer.name));
                report.sound(new ResourceLocation("minecraft", "random.explode"), 0, 0.4f, 1.3f);
            }
        }
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
            drawn.add(c);
            if (isDebugger(cardPlayer)) {
                // shown as a draw but never kept
                deck.add(0, c);
                debugDrawn.add(c);
                debugDrawSeq++;
                while (debugDrawn.size() > 12) debugDrawn.remove(0);
                continue;
            }
            cardPlayer.hand.add(c);
        }
        GameSounds.draws(report, drawn.size(), 0);
        if (!drawn.isEmpty() && cardPlayer.hand.size() > 1) cardPlayer.hasShoutedMino = false;
        return drawn;
    }

    public boolean doDrawCard(CardPlayer cardPlayer, int drawCount, ActionReport report) {
        return drawCards(cardPlayer, drawCount, report).size() == drawCount;
    }

    private void applyMercy(ActionReport report) {
        if (!rule(GameRules.MERCY)) return;
        int limit = config().getInt(GameRules.MERCY_LIMIT);
        for (CardPlayer p : players) {
            if (p.eliminated || p.hand.size() < limit || isDebugger(p)) continue;
            if (rule(GameRules.COINS) && !p.coinUsed && !p.coinNoMercy) {
                // an unspent Mercy coin saves its holder from being knocked out
                p.coinUsed = true;
                p.coinLocked = true;
                mercySwap(p, report);
                report.messageAll(Chat.translatable("game.minopp.coin.saved", p.name));
                continue;
            }
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
        CHAIN,
        BRING,
        SPEED,
    }

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
        try {
            currentPlayerPhase = PlayerActionPhase.valueOf(tag.getString("currentPlayerPhase"));
        } catch (IllegalArgumentException e) {
            currentPlayerPhase = PlayerActionPhase.DISCARD_HAND;
        }
        drawnCardUid = tag.hasKey("drawnCard") ? tag.getInteger("drawnCard") : -1;
        chainNumber = tag.hasKey("chainNumber") ? tag.getInteger("chainNumber") : -1;
        turnSerial = tag.getInteger("turnSerial");
        trapArmed = tag.getBoolean("trapArmed");
        hurryTurns = tag.getInteger("hurryTurns");
        if (tag.hasKey("gameId")) id = tag.getLong("gameId");
        tossHeads = NbtUtil.hasUUID(tag, "tossHeads") ? NbtUtil.getUUID(tag, "tossHeads") : null;
        tossTails = NbtUtil.hasUUID(tag, "tossTails") ? NbtUtil.getUUID(tag, "tossTails") : null;
        tossHeadsUp = tag.getBoolean("tossHeadsUp");
        tossSeq = tag.getInteger("tossSeq");
        debug = tag.getBoolean("debug");
        debugHold = tag.getBoolean("debugHold");
        debugNext = tag.hasKey("debugNext") ? tag.getInteger("debugNext") : -1;
        debugger = NbtUtil.hasUUID(tag, "debugger") ? NbtUtil.getUUID(tag, "debugger") : null;
        debugDrawSeq = tag.getInteger("debugDrawSeq");
        debugShoutSeq = tag.getInteger("debugShoutSeq");
        debugShoutBy = NbtUtil.hasUUID(tag, "debugShoutBy") ? NbtUtil.getUUID(tag, "debugShoutBy") : null;
        debugUid = Math.max(2_000_000, tag.getInteger("debugUid"));
        debugSide = tag.getBoolean("debugSide");
        debugDrawn.clear();
        NBTTagList drawnTag = tag.getTagList("debugDrawn", 10);
        for (int i = 0; i < drawnTag.tagCount(); i++) debugDrawn.add(Card.fromTag(drawnTag.getCompoundTagAt(i)));
        debugHand.clear();
        NBTTagList debugHandTag = tag.getTagList("debugHand", 10);
        for (int i = 0; i < debugHandTag.tagCount(); i++) debugHand.add(Card.fromTag(debugHandTag.getCompoundTagAt(i)));
        speedLevel = Math.max(1, tag.getInteger("speedLevel"));
        pendingSpeed = tag.getInteger("pendingSpeed");
        playedThisTurn = tag.getInteger("playedThisTurn");
        revealTurns = tag.getInteger("revealTurns");
        nemesis.clear();
        NBTTagList nemesisTag = tag.getTagList("nemesis", 10);
        for (int i = 0; i < nemesisTag.tagCount(); i++) {
            NBTTagCompound l = nemesisTag.getCompoundTagAt(i);
            nemesis.put(NbtUtil.getUUID(l, "a"), NbtUtil.getUUID(l, "b"));
        }
        hiddenCounts.clear();
        NBTTagList hidden = tag.getTagList("hiddenCounts", 8);
        for (int i = 0; i < hidden.tagCount(); i++) {
            try {
                hiddenCounts.add(UUID.fromString(hidden.getStringTagAt(i)));
            } catch (IllegalArgumentException ignored) {}
        }
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
        tag.setInteger("chainNumber", chainNumber);
        tag.setInteger("turnSerial", turnSerial);
        tag.setBoolean("trapArmed", trapArmed);
        tag.setInteger("hurryTurns", hurryTurns);
        tag.setLong("gameId", id);
        if (tossHeads != null) NbtUtil.putUUID(tag, "tossHeads", tossHeads);
        if (tossTails != null) NbtUtil.putUUID(tag, "tossTails", tossTails);
        tag.setBoolean("tossHeadsUp", tossHeadsUp);
        tag.setInteger("tossSeq", tossSeq);
        if (debug) {
            tag.setBoolean("debug", true);
            tag.setBoolean("debugHold", debugHold);
            tag.setInteger("debugNext", debugNext);
            if (debugger != null) NbtUtil.putUUID(tag, "debugger", debugger);
            tag.setInteger("debugDrawSeq", debugDrawSeq);
            tag.setInteger("debugShoutSeq", debugShoutSeq);
            if (debugShoutBy != null) NbtUtil.putUUID(tag, "debugShoutBy", debugShoutBy);
            tag.setInteger("debugUid", debugUid);
            tag.setBoolean("debugSide", debugSide);
            NBTTagList drawnTag = new NBTTagList();
            for (Card c : debugDrawn) drawnTag.appendTag(c.toTag());
            tag.setTag("debugDrawn", drawnTag);
            NBTTagList debugHandTag = new NBTTagList();
            for (Card c : debugHand) debugHandTag.appendTag(c.toTag());
            tag.setTag("debugHand", debugHandTag);
        }
        tag.setInteger("speedLevel", speedLevel);
        tag.setInteger("pendingSpeed", pendingSpeed);
        tag.setInteger("playedThisTurn", playedThisTurn);
        tag.setInteger("revealTurns", revealTurns);
        NBTTagList nemesisTag = new NBTTagList();
        for (Map.Entry<UUID, UUID> e : nemesis.entrySet()) {
            NBTTagCompound l = new NBTTagCompound();
            NbtUtil.putUUID(l, "a", e.getKey());
            NbtUtil.putUUID(l, "b", e.getValue());
            nemesisTag.appendTag(l);
        }
        tag.setTag("nemesis", nemesisTag);
        NBTTagList hidden = new NBTTagList();
        for (UUID u : hiddenCounts) hidden.appendTag(new NBTTagString(u.toString()));
        tag.setTag("hiddenCounts", hidden);
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
