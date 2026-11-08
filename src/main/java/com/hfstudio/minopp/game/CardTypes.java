package com.hfstudio.minopp.game;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hfstudio.minopp.game.CardType.Choice;

public final class CardTypes {

    private CardTypes() {}

    private static final Map<String, CardType> REGISTRY = new LinkedHashMap<>();

    public static CardType register(CardType type) {
        REGISTRY.put(type.id, type);
        return type;
    }

    public static CardType get(String id) {
        CardType type = REGISTRY.get(id);
        return type != null ? type : NUMBER;
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }

    public static Collection<CardType> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static final CardType NUMBER = register(
        CardType.builder("number", CardPack.CLASSIC)
            .numbered()
            .build());
    public static final CardType SKIP = register(
        CardType.builder("skip", CardPack.CLASSIC)
            .label("Ø")
            .sort(10)
            .effect(ctx -> ctx.game.skipNext(1))
            .build());
    public static final CardType REVERSE = register(
        CardType.builder("reverse", CardPack.CLASSIC)
            .label("⇆")
            .sort(11)
            .effect(ctx -> ctx.game.reverse())
            .build());
    public static final CardType DRAW2 = register(
        CardType.builder("draw2", CardPack.CLASSIC)
            .label("+2")
            .sort(12)
            .draw(2)
            .match("draw2")
            .effect(ctx -> ctx.game.addPendingDraw(2, ctx.player))
            .build());
    public static final CardType WILD = register(
        CardType.builder("wild", CardPack.CLASSIC)
            .wild()
            .label("W")
            .sort(40)
            .build());
    public static final CardType WILD_DRAW4 = register(
        CardType.builder("wild_draw4", CardPack.CLASSIC)
            .wild()
            .label("+4")
            .sort(41)
            .draw(4)
            .effect(ctx -> {
                ctx.game.markChallengeable(ctx.player);
                ctx.game.addPendingDraw(4, ctx.player);
            })
            .build());

    public static final CardType DRAW4 = register(
        CardType.builder("draw4", CardPack.NO_MERCY)
            .label("+4")
            .sort(13)
            .draw(4)
            .effect(ctx -> ctx.game.addPendingDraw(4, ctx.player))
            .build());
    public static final CardType DISCARD_ALL = register(
        CardType.builder("discard_all", CardPack.NO_MERCY)
            .label("DA")
            .sort(14)
            .effect(ctx -> ctx.game.discardAllOfColor(ctx.player, ctx.face.color, ctx.report))
            .build());
    public static final CardType SKIP_EVERYONE = register(
        CardType.builder("skip_everyone", CardPack.NO_MERCY)
            .label("ØØ")
            .sort(15)
            .effect(ctx -> ctx.game.giveExtraTurn())
            .build());
    public static final CardType WILD_REVERSE_DRAW4 = register(
        CardType.builder("wild_reverse_draw4", CardPack.NO_MERCY)
            .wild()
            .label("⇆+4")
            .sort(42)
            .draw(4)
            .effect(ctx -> {
                ctx.game.reverse();
                ctx.game.addPendingDraw(4, ctx.player);
            })
            .build());
    public static final CardType WILD_DRAW6 = register(
        CardType.builder("wild_draw6", CardPack.NO_MERCY)
            .wild()
            .label("+6")
            .sort(43)
            .draw(6)
            .effect(ctx -> ctx.game.addPendingDraw(6, ctx.player))
            .build());
    public static final CardType WILD_DRAW10 = register(
        CardType.builder("wild_draw10", CardPack.NO_MERCY)
            .wild()
            .label("+10")
            .sort(44)
            .draw(10)
            .effect(ctx -> ctx.game.addPendingDraw(10, ctx.player))
            .build());
    public static final CardType WILD_COLOR_ROULETTE = register(
        CardType.builder("wild_color_roulette", CardPack.NO_MERCY)
            .wild()
            .choice(Choice.NONE)
            .label("?")
            .sort(45)
            .effect(ctx -> ctx.game.startColorRoulette(ctx.player))
            .build());

    public static final CardType DRAW1 = register(
        CardType.builder("draw1", CardPack.FLIP)
            .label("+1")
            .sort(16)
            .draw(1)
            .effect(ctx -> ctx.game.addPendingDraw(1, ctx.player))
            .build());
    public static final CardType FLIP = register(
        CardType.builder("flip", CardPack.FLIP)
            .label("F")
            .sort(17)
            .effect(ctx -> ctx.game.flipSides(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_DRAW2 = register(
        CardType.builder("wild_draw2", CardPack.FLIP)
            .wild()
            .label("+2")
            .sort(46)
            .draw(2)
            .effect(ctx -> ctx.game.addPendingDraw(2, ctx.player))
            .build());
    public static final CardType DRAW5 = register(
        CardType.builder("draw5", CardPack.FLIP)
            .label("+5")
            .sort(18)
            .draw(5)
            .effect(ctx -> ctx.game.addPendingDraw(5, ctx.player))
            .build());
    public static final CardType WILD_DRAW_COLOR = register(
        CardType.builder("wild_draw_color", CardPack.FLIP)
            .wild()
            .label("+?")
            .sort(47)
            .effect(ctx -> ctx.game.nextDrawsUntilColor(ctx.choices.color, ctx.report))
            .build());

    public static final CardType POWER_NUMBER = register(
        CardType.builder("power_number", CardPack.FLEX)
            .numbered()
            .effect(ctx -> ctx.game.togglePower(ctx.player, ctx.report))
            .build());
    public static final CardType FLEX_SKIP = register(
        CardType.builder("flex_skip", CardPack.FLEX)
            .match("skip")
            .label("Ø")
            .sort(20)
            .effect(ctx -> ctx.game.skipNext(1))
            .flex(Choice.NONE, ctx -> ctx.game.giveExtraTurn())
            .build());
    public static final CardType FLEX_REVERSE = register(
        CardType.builder("flex_reverse", CardPack.FLEX)
            .match("reverse")
            .label("⇆")
            .sort(21)
            .effect(ctx -> ctx.game.reverse())
            .flex(Choice.NONE, ctx -> {
                ctx.game.reverse();
                ctx.game.skipNext(1);
            })
            .build());
    public static final CardType FLEX_DRAW2 = register(
        CardType.builder("flex_draw2", CardPack.FLEX)
            .match("draw2")
            .label("+2")
            .sort(22)
            .draw(2)
            .effect(ctx -> ctx.game.addPendingDraw(2, ctx.player))
            .flex(Choice.NONE, ctx -> ctx.game.everyoneElseDraws(ctx.player, 1, ctx.report))
            .build());
    public static final CardType WILD_ALL_FLIP = register(
        CardType.builder("wild_all_flip", CardPack.FLEX)
            .wild()
            .label("W↻")
            .sort(48)
            .effect(ctx -> ctx.game.toggleAllPower(ctx.report))
            .build());
    public static final CardType FLEX_WILD_ALL_DRAW = register(
        CardType.builder("flex_wild_all_draw", CardPack.FLEX)
            .wild()
            .label("W+")
            .sort(49)
            .flex(Choice.COLOR, ctx -> ctx.game.everyoneElseDraws(ctx.player, 2, ctx.report))
            .build());
    public static final CardType FLEX_WILD_TARGET_DRAW2 = register(
        CardType.builder("flex_wild_target_draw2", CardPack.FLEX)
            .wild()
            .label("W➶2")
            .sort(50)
            .flex(Choice.COLOR_AND_TARGET, ctx -> ctx.game.targetDraws(ctx.target(0), 2, ctx.report))
            .build());
    public static final CardType FLEX_WILD_DRAW4 = register(
        CardType.builder("flex_wild_draw4", CardPack.FLEX)
            .wild()
            .label("+4")
            .match("wild_draw4")
            .sort(51)
            .draw(4)
            .effect(ctx -> {
                ctx.game.markChallengeable(ctx.player);
                ctx.game.addPendingDraw(4, ctx.player);
            })
            .flex(Choice.COLOR_AND_TARGET, ctx -> ctx.game.targetDraws(ctx.target(0), 4, ctx.report))
            .build());

    public static final CardType AW_WILD = register(
        CardType.builder("aw_wild", CardPack.ALL_WILD)
            .allWild()
            .label("W")
            .sort(60)
            .build());
    public static final CardType AW_REVERSE = register(
        CardType.builder("aw_reverse", CardPack.ALL_WILD)
            .allWild()
            .label("⇆")
            .sort(61)
            .effect(ctx -> ctx.game.reverse())
            .build());
    public static final CardType AW_SKIP = register(
        CardType.builder("aw_skip", CardPack.ALL_WILD)
            .allWild()
            .label("Ø")
            .sort(62)
            .effect(ctx -> ctx.game.skipNext(1))
            .build());
    public static final CardType AW_SKIP_TWO = register(
        CardType.builder("aw_skip_two", CardPack.ALL_WILD)
            .allWild()
            .label("Ø2")
            .sort(63)
            .effect(ctx -> ctx.game.skipNext(2))
            .build());
    public static final CardType AW_DRAW2 = register(
        CardType.builder("aw_draw2", CardPack.ALL_WILD)
            .allWild()
            .label("+2")
            .sort(64)
            .draw(2)
            .effect(ctx -> ctx.game.addPendingDraw(2, ctx.player))
            .build());
    public static final CardType AW_DRAW4 = register(
        CardType.builder("aw_draw4", CardPack.ALL_WILD)
            .allWild()
            .label("+4")
            .sort(65)
            .draw(4)
            .effect(ctx -> ctx.game.addPendingDraw(4, ctx.player))
            .build());
    public static final CardType AW_TARGETED_DRAW2 = register(
        CardType.builder("aw_targeted_draw2", CardPack.ALL_WILD)
            .allWild()
            .choice(Choice.TARGET)
            .label("➶2")
            .sort(66)
            .effect(ctx -> ctx.game.targetDraws(ctx.target(0), 2, ctx.report))
            .build());
    public static final CardType AW_FORCED_SWAP = register(
        CardType.builder("aw_forced_swap", CardPack.ALL_WILD)
            .allWild()
            .choice(Choice.TARGET)
            .label("⇄")
            .sort(67)
            .effect(ctx -> {
                CardPlayer target = ctx.target(0);
                if (target != null) ctx.game.swapHands(ctx.player, target, ctx.report);
            })
            .build());

    public static final CardType POINT_TAKEN = register(
        CardType.builder("point_taken", CardPack.PARTY)
            .label("☛")
            .sort(23)
            .effect(ctx -> ctx.game.startPointTaken(ctx.player))
            .build());
    public static final CardType WILD_DRAWN_TOGETHER = register(
        CardType.builder("wild_drawn_together", CardPack.PARTY)
            .wild()
            .choice(Choice.COLOR_AND_TWO_TARGETS)
            .label("∞")
            .sort(52)
            .effect(ctx -> ctx.game.linkPlayers(ctx.target(0), ctx.target(1), ctx.report))
            .build());
    public static final CardType WILD_PILE_UP = register(
        CardType.builder("wild_pile_up", CardPack.PARTY)
            .wild()
            .choice(Choice.NONE)
            .label("≡")
            .sort(53)
            .effect(ctx -> ctx.game.pileUp(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_REBOUND = register(
        CardType.builder("wild_rebound", CardPack.HERO)
            .wild()
            .answersDraw()
            .label("↩")
            .sort(54)
            .condition((game, player, face) -> game.pendingDraw > 0)
            .effect(ctx -> ctx.game.rebound(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_DANCE_FLOOR = register(
        CardType.builder("wild_dance_floor", CardPack.RHYTHM)
            .wild()
            .label("♫")
            .sort(55)
            .effect(ctx -> ctx.game.startDanceFloor(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_HAND_SHUFFLE = register(
        CardType.builder("wild_hand_shuffle", CardPack.MAYHEM)
            .wild()
            .label("☄")
            .sort(56)
            .effect(ctx -> ctx.game.shuffleHands(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_TRIAL = register(
        CardType.builder("wild_trial", CardPack.LEGEND)
            .wild()
            .label("⚔")
            .sort(57)
            .effect(ctx -> ctx.game.trial(ctx.player, ctx.choices.color, ctx.report))
            .build());

    public static final CardType WILD_FIFTY_FIFTY = register(
        CardType.builder("wild_fifty_fifty", CardPack.ANNIVERSARY)
            .wild()
            .label("50")
            .sort(58)
            .effect(ctx -> ctx.game.fiftyFifty(ctx.player, 4, ctx.report))
            .build());

    public static final CardType WILD_PLUS6 = register(
        CardType.builder("wild_plus6", CardPack.WEEKEND)
            .wild()
            .label("+6")
            .sort(70)
            .draw(6)
            .effect(ctx -> ctx.game.addPendingDraw(6, ctx.player))
            .build());
    public static final CardType WILD_REVERSE_PLUS6 = register(
        CardType.builder("wild_reverse_plus6", CardPack.WEEKEND)
            .wild()
            .label("⇆+6")
            .sort(71)
            .draw(6)
            .effect(ctx -> {
                ctx.game.reverse();
                ctx.game.addPendingDraw(6, ctx.player);
            })
            .build());
    public static final CardType DOUBLE_DISCARD_ALL = register(
        CardType.builder("double_discard_all", CardPack.WEEKEND)
            .choice(Choice.COLOR)
            .label("DA2")
            .sort(24)
            .effect(ctx -> {
                ctx.game.discardAllOfColor(ctx.player, ctx.face.color, ctx.report);
                if (ctx.choices.color != null && ctx.choices.color != ctx.face.color) {
                    ctx.game.discardAllOfColor(ctx.player, ctx.choices.color, ctx.report);
                }
                ctx.game.currentColor = ctx.face.color;
            })
            .build());
    public static final CardType WILD_TIME_BALLOON = register(
        CardType.builder("wild_time_balloon", CardPack.WEEKEND)
            .wild()
            .label("●")
            .sort(72)
            .effect(ctx -> ctx.game.attachBalloon(ctx.player, ctx.report))
            .build());

    public static final CardType BLASTER = register(
        CardType.builder("blaster", CardPack.BLASTER)
            .label("✸")
            .sort(20)
            .effect(ctx -> ctx.game.blaster(ctx.player, ctx.report))
            .build());
    public static final CardType TRADE_HANDS = register(
        CardType.builder("trade_hands", CardPack.BLASTER)
            .choice(Choice.TARGET)
            .label("⇄")
            .sort(21)
            .effect(ctx -> {
                CardPlayer target = ctx.target(0);
                if (target != null) ctx.game.swapHands(ctx.player, target, ctx.report);
            })
            .build());
    public static final CardType WILD_BLAST_ALL = register(
        CardType.builder("wild_blast_all", CardPack.BLASTER)
            .wild()
            .label("✸✸")
            .sort(73)
            .effect(ctx -> ctx.game.blastAll(ctx.player, ctx.report))
            .build());

    public static final CardType GIFT = register(
        CardType.builder("gift", CardPack.MODERN)
            .label("→1")
            .sort(22)
            .effect(ctx -> ctx.game.gift(ctx.player, ctx.report))
            .build());
    public static final CardType BONUS_TURN = register(
        CardType.builder("bonus_turn", CardPack.MODERN)
            .label("↺")
            .sort(23)
            .effect(ctx -> ctx.game.giveExtraTurn())
            .build());
    public static final CardType WILD_SWAP_HANDS = register(
        CardType.builder("wild_swap_hands", CardPack.MODERN)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W⇄")
            .sort(74)
            .effect(ctx -> {
                CardPlayer target = ctx.target(0);
                if (target != null) ctx.game.swapHands(ctx.player, target, ctx.report);
            })
            .build());

    public static final CardType WILD_CYCLONE = register(
        CardType.builder("wild_cyclone", CardPack.WHIRLWIND)
            .wild()
            .label("@")
            .sort(75)
            .effect(ctx -> ctx.game.cyclone(ctx.report))
            .build());
    public static final CardType WILD_THIEF = register(
        CardType.builder("wild_thief", CardPack.WHIRLWIND)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("$")
            .sort(76)
            .effect(ctx -> {
                CardPlayer target = ctx.target(0);
                if (target != null) ctx.game.steal(ctx.player, target, ctx.report);
            })
            .build());

    public static void init() {}
}
