package com.hfstudio.minopp.game;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.game.CardType.Choice;

public class CardTypes {

    private CardTypes() {}

    private static final Map<String, CardType> REGISTRY = new LinkedHashMap<>();
    private static final Collection<CardType> ALL = Collections.unmodifiableCollection(REGISTRY.values());

    private static long revision;

    public static long revision() {
        return revision;
    }

    public static CardType register(CardType type) {
        Objects.requireNonNull(type, "type");
        if (HandMask.HIDDEN_TYPE.equals(type.id)) throw new IllegalArgumentException("Reserved card type: " + type.id);
        if (REGISTRY.putIfAbsent(type.id, type) != null)
            throw new IllegalArgumentException("Card type already registered: " + type.id);
        revision++;
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
        return ALL;
    }

    public static final CardType NUMBER = register(
        CardType.builder("number", CardPack.CLASSIC)
            .numbered()
            .build());
    public static final CardType SKIP = register(
        CardType.builder("skip", CardPack.CLASSIC)
            .label("Ø")
            .sort(10)
            .effect(
                ctx -> TargetedEffects.apply(
                    ctx.game,
                    ctx.player,
                    List.of(ctx.game.nextPlayer()),
                    "skip",
                    new NBTTagCompound(),
                    ctx.report))
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
            .matchAliases("draw4")
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
            .matchAliases("draw2")
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
            .matchAliases("skip")
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
                if (target != null) TargetedEffects
                    .apply(ctx.game, ctx.player, List.of(target), "swap", new NBTTagCompound(), ctx.report);
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
        CardType.builder("wild_rebound", CardPack.JUNGLE)
            .wild()
            .answersDraw()
            .label("↩")
            .sort(54)
            .condition((game, player, face) -> game.pendingDraw > 0)
            .effect(ctx -> ctx.game.rebound(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_DANCE_FLOOR = register(
        CardType.builder("wild_dance_floor", CardPack.DANCE)
            .wild()
            .label("♫")
            .sort(55)
            .effect(ctx -> ctx.game.startDanceFloor(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_HAND_SHUFFLE = register(
        CardType.builder("wild_hand_shuffle", CardPack.JUNGLE)
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
            .choice(Choice.COLOR_AND_TWO_TARGETS)
            .label("50")
            .sort(58)
            .effect(ctx -> ctx.game.fiftyFifty(ctx.player, ctx.target(0), ctx.target(1), 4, ctx.report))
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
                if (target != null) TargetedEffects
                    .apply(ctx.game, ctx.player, List.of(target), "swap", new NBTTagCompound(), ctx.report);
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
                if (target != null) TargetedEffects
                    .apply(ctx.game, ctx.player, List.of(target), "swap", new NBTTagCompound(), ctx.report);
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

    public static final CardType WILD_SMOKE = register(
        CardType.builder("wild_smoke", CardPack.JUNGLE)
            .wild()
            .label("~")
            .sort(77)
            .effect(ctx -> ctx.game.smoke(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_ECHO = register(
        CardType.builder("wild_echo", CardPack.JUNGLE)
            .wild()
            .label("≈")
            .sort(78)
            .effect(ctx -> ctx.game.echo(ctx))
            .build());
    public static final CardType WILD_CROSSFADE = register(
        CardType.builder("wild_crossfade", CardPack.DANCE)
            .wild()
            .label("⇋")
            .sort(79)
            .effect(ctx -> ctx.game.crossfade(ctx.report))
            .build());
    public static final CardType WILD_EVEN_OUT = register(
        CardType.builder("wild_even_out", CardPack.DANCE)
            .wild()
            .label("=")
            .sort(80)
            .effect(ctx -> ctx.game.evenOut(ctx.report))
            .build());
    public static final CardType WILD_SURPRISE = register(
        CardType.builder("wild_surprise", CardPack.DANCE)
            .wild()
            .label("?!")
            .sort(81)
            .effect(ctx -> ctx.game.surprise(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_CARD_STORM = register(
        CardType.builder("wild_card_storm", CardPack.RASCAL)
            .wild()
            .label("≋")
            .sort(82)
            .effect(ctx -> ctx.game.cardStorm(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_BOOBY_TRAP = register(
        CardType.builder("wild_booby_trap", CardPack.RASCAL)
            .wild()
            .label("!")
            .sort(83)
            .effect(ctx -> ctx.game.armTrap(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_HURRY_UP = register(
        CardType.builder("wild_hurry_up", CardPack.RASCAL)
            .wild()
            .label("⏱")
            .sort(84)
            .effect(ctx -> ctx.game.hurryUp(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_SHIELD = register(
        CardType.builder("wild_shield", CardPack.RASCAL)
            .wild()
            .answersDraw()
            .label("◇")
            .sort(85)
            .effect(ctx -> ctx.game.shield(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_TRICKSTER = register(
        CardType.builder("wild_trickster", CardPack.MYTH)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("T")
            .sort(86)
            .effect(ctx -> {
                CardPlayer target = ctx.target(0);
                if (target != null) ctx.game.trickster(ctx.player, target, ctx.report);
            })
            .build());
    public static final CardType WILD_SURGE = register(
        CardType.builder("wild_surge", CardPack.MYTH)
            .wild()
            .label("ϟ")
            .sort(87)
            .effect(ctx -> ctx.game.surge(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_TEAM_PLAY = register(
        CardType.builder("wild_team_play", CardPack.TEAMS)
            .wild()
            .label("TP")
            .sort(88)
            .effect(ctx -> ctx.game.teamPlay(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_TEAM_DRAW4 = register(
        CardType.builder("wild_team_draw4", CardPack.TEAMS)
            .wild()
            .label("T+4")
            .sort(89)
            .effect(ctx -> ctx.game.teamDraw(ctx.player, 2, ctx.report))
            .build());

    // Add-On packs
    public static final CardType STACK1 = register(
        CardType.builder("stack1", CardPack.STACK)
            .label("S1")
            .sort(90)
            .draw(1)
            .answersDraw()
            .condition((game, player, face) -> game.pendingDraw == 0 || face.color == game.currentColor)
            .effect(ctx -> ctx.game.addPendingDraw(1, ctx.player))
            .build());
    public static final CardType STACK2 = register(
        CardType.builder("stack2", CardPack.STACK)
            .label("S2")
            .sort(91)
            .draw(2)
            .answersDraw()
            .condition((game, player, face) -> game.pendingDraw == 0 || face.color == game.currentColor)
            .effect(ctx -> ctx.game.addPendingDraw(2, ctx.player))
            .build());
    public static final CardType WILD_STACK3 = register(
        CardType.builder("wild_stack3", CardPack.STACK)
            .wild()
            .label("S3")
            .sort(92)
            .draw(3)
            .answersDraw()
            .effect(ctx -> ctx.game.addPendingDraw(3, ctx.player))
            .build());
    public static final CardType WILD_STACK_NUMBER = register(
        CardType.builder("wild_stack_number", CardPack.STACK)
            .wild()
            .label("S#")
            .sort(93)
            .answersDraw()
            .effect(ctx -> ctx.game.stackNumber(ctx.player, ctx.report))
            .build());
    public static final CardType SWAP_ONE = register(
        CardType.builder("swap_one", CardPack.SWAP)
            .choice(Choice.TARGET)
            .label("⇄1")
            .sort(94)
            .effect(ctx -> ctx.game.swapOne(ctx.player, ctx.target(0), ctx.report))
            .build());
    public static final CardType REFRESH_HAND = register(
        CardType.builder("refresh_hand", CardPack.SWAP)
            .label("↻")
            .sort(95)
            .effect(ctx -> ctx.game.refreshHand(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_FORCE_TRADE = register(
        CardType.builder("wild_force_trade", CardPack.SWAP)
            .wild()
            .choice(Choice.COLOR_AND_TWO_TARGETS)
            .label("W⇋")
            .sort(96)
            .effect(ctx -> ctx.game.forceTrade(ctx.target(0), ctx.target(1), ctx.report))
            .build());
    public static final CardType WILD_PASS_HANDS = register(
        CardType.builder("wild_pass_hands", CardPack.SWAP)
            .wild()
            .label("W⟳")
            .sort(97)
            .effect(ctx -> ctx.game.passHands(ctx.report))
            .build());
    public static final CardType REVERSE_SKIP = register(
        CardType.builder("reverse_skip", CardPack.REVERSE_PACK)
            .matchAliases("skip", "reverse")
            .match("skip")
            .label("⇆Ø")
            .sort(98)
            .effect(ctx -> ctx.game.reverseSkip())
            .build());
    public static final CardType REVERSE_DRAW2 = register(
        CardType.builder("reverse_draw2", CardPack.REVERSE_PACK)
            .match("draw2")
            .label("⇆2")
            .sort(99)
            .draw(2)
            .effect(ctx -> ctx.game.reverseDraw2(ctx.player))
            .build());
    public static final CardType WILD_POWER_REVERSE = register(
        CardType.builder("wild_power_reverse", CardPack.REVERSE_PACK)
            .wild()
            .label("W⇆")
            .sort(100)
            .effect(ctx -> ctx.game.powerReverse())
            .build());
    public static final CardType WILD_NO_U = register(
        CardType.builder("wild_no_u", CardPack.REVERSE_PACK)
            .wild()
            .label("NO U")
            .sort(101)
            .answersDraw()
            .effect(ctx -> ctx.game.noU(ctx.player, ctx.report))
            .build());
    public static final CardType SPEED_UP = register(
        CardType.builder("speed_up", CardPack.SPEED)
            .label("»")
            .sort(102)
            .effect(ctx -> ctx.game.speedUp(1, ctx.report))
            .build());
    public static final CardType WILD_DOUBLE_SPEED = register(
        CardType.builder("wild_double_speed", CardPack.SPEED)
            .wild()
            .label("W»»")
            .sort(103)
            .effect(ctx -> ctx.game.speedUp(2, ctx.report))
            .build());
    public static final CardType WILD_PETTY_PLAY = register(
        CardType.builder("wild_petty_play", CardPack.FRENEMIES)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W+3")
            .sort(104)
            .effect(ctx -> ctx.game.targetDraws(ctx.target(0), 3, ctx.report))
            .build());
    public static final CardType WILD_ELIMINATOR = register(
        CardType.builder("wild_eliminator", CardPack.FRENEMIES)
            .wild()
            .label("W✕")
            .sort(105)
            .effect(ctx -> ctx.game.eliminator(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_INSTIGATOR = register(
        CardType.builder("wild_instigator", CardPack.FRENEMIES)
            .wild()
            .choice(Choice.COLOR_AND_TWO_TARGETS)
            .label("W2+2")
            .sort(106)
            .effect(ctx -> {
                ctx.game.targetDraws(ctx.target(0), 2, ctx.report);
                ctx.game.targetDraws(ctx.target(1), 2, ctx.report);
            })
            .build());
    public static final CardType WILD_NEMESIS = register(
        CardType.builder("wild_nemesis", CardPack.FRENEMIES)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W☠")
            .sort(107)
            .effect(ctx -> ctx.game.markNemesis(ctx.player, ctx.target(0), ctx.report))
            .build());
    public static final CardType WILD_BLOCK = register(
        CardType.builder("wild_block", CardPack.DEFENSE)
            .wild()
            .label("W▣")
            .sort(108)
            .answersDraw()
            .effect(ctx -> ctx.game.block(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_BLOCK_REBOUND = register(
        CardType.builder("wild_block_rebound", CardPack.DEFENSE)
            .wild()
            .label("W▣↩")
            .sort(109)
            .answersDraw()
            .effect(ctx -> ctx.game.blockRebound(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_BLOCK_DEFLECT = register(
        CardType.builder("wild_block_deflect", CardPack.DEFENSE)
            .wild()
            .label("W▣→")
            .sort(110)
            .answersDraw()
            .effect(ctx -> ctx.game.blockDeflect(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_REDIRECT = register(
        CardType.builder("wild_redirect", CardPack.DEFENSE)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W➶")
            .sort(111)
            .answersDraw()
            .effect(ctx -> ctx.game.redirect(ctx.player, ctx.target(0), ctx.report))
            .build());
    public static final CardType WILD_MATCH_MAX = register(
        CardType.builder("wild_match_max", CardPack.COMEBACK)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W=")
            .sort(112)
            .effect(ctx -> ctx.game.matchTheMax(ctx.target(0), ctx.report))
            .build());
    public static final CardType WILD_MISERY = register(
        CardType.builder("wild_misery", CardPack.COMEBACK)
            .wild()
            .label("W☹")
            .sort(113)
            .effect(ctx -> ctx.game.misery(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_EGGS_BASKET = register(
        CardType.builder("wild_eggs_basket", CardPack.LOVE_ISLAND)
            .wild()
            .choice(Choice.COLOR_AND_TWO_TARGETS)
            .label("W♥")
            .sort(114)
            .effect(ctx -> ctx.game.linkPlayers(ctx.target(0), ctx.target(1), ctx.report))
            .build());
    public static final CardType WILD_PULL_CHAT = register(
        CardType.builder("wild_pull_chat", CardPack.LOVE_ISLAND)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W☏")
            .sort(115)
            .effect(ctx -> ctx.game.pullForChat(ctx.player, ctx.target(0), ctx.report))
            .build());
    public static final CardType WILD_GOT_TEXT = register(
        CardType.builder("wild_got_text", CardPack.LOVE_ISLAND)
            .wild()
            .label("W✉")
            .sort(116)
            .effect(ctx -> ctx.game.gotAText(ctx.player, ctx.choices.color, ctx.report))
            .build());
    public static final CardType WILD_PASS_COLOR = register(
        CardType.builder("wild_pass_color", CardPack.BILLIE)
            .wild()
            .label("W→")
            .sort(117)
            .effect(ctx -> ctx.game.passColor(ctx.choices.color, ctx.report))
            .build());
    public static final CardType TRICK_WILD = register(
        CardType.builder("trick_wild", CardPack.BILLIE)
            .wild()
            .label("W?")
            .sort(118)
            .effect(ctx -> ctx.game.trickWild(ctx.player, ctx.choices.color, ctx.report))
            .build());
    public static final CardType WILD_COLOR_CHALLENGE = register(
        CardType.builder("wild_color_challenge", CardPack.BILLIE)
            .wild()
            .label("W!")
            .sort(119)
            .effect(ctx -> ctx.game.colorChallenge(ctx.player, ctx.choices.color, ctx.report))
            .build());
    public static final CardType REVEAL_HANDS = register(
        CardType.builder("reveal_hands", CardPack.BILLIE)
            .label("◉")
            .sort(120)
            .effect(ctx -> ctx.game.revealHands(ctx.player, ctx.report))
            .build());

    // Show 'Em No Mercy expansion
    public static final CardType PLAY_AGAIN_10 = register(
        CardType.builder("play_again_10", CardPack.SHOW_EM)
            .label("10")
            .sort(121)
            .effect(ctx -> ctx.game.giveExtraTurn())
            .build());
    public static final CardType WILD_DISCARD_ALL = register(
        CardType.builder("wild_discard_all", CardPack.SHOW_EM)
            .wild()
            .label("WDA")
            .sort(122)
            .effect(ctx -> ctx.game.discardAllOfColor(ctx.player, ctx.choices.color, ctx.report))
            .build());
    public static final CardType WILD_REVERSE_DRAW8 = register(
        CardType.builder("wild_reverse_draw8", CardPack.SHOW_EM)
            .wild()
            .label("⇆+8")
            .sort(123)
            .draw(8)
            .effect(ctx -> {
                ctx.game.reverse();
                ctx.game.addPendingDraw(8, ctx.player);
            })
            .build());
    public static final CardType WILD_FINAL_ATTACK = register(
        CardType.builder("wild_final_attack", CardPack.SHOW_EM)
            .wild()
            .label("W⚔")
            .sort(124)
            .effect(ctx -> ctx.game.finalAttack(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_SUDDEN_DEATH = register(
        CardType.builder("wild_sudden_death", CardPack.SHOW_EM)
            .wild()
            .label("W24")
            .sort(125)
            .effect(ctx -> ctx.game.suddenDeath(ctx.report))
            .build());

    // UNO Infinity: only the cards no other pack already has
    public static final CardType FAST_NUMBER = register(
        CardType.builder("fast_number", CardPack.INFINITY)
            .numbered()
            .fast()
            .sort(1)
            .build());
    public static final CardType COLORLESS_NUMBER = register(
        CardType.builder("colorless_number", CardPack.INFINITY)
            .numbered()
            .fixedColor(CardColor.COLORLESS)
            .sort(2)
            .build());
    public static final CardType MINUS_ONE = register(
        CardType.builder("minus_one", CardPack.INFINITY)
            .label("-1")
            .sort(130)
            .pick(1)
            .effect(ctx -> ctx.game.discardPicked(ctx.player, ctx.face.type(), ctx.choices, ctx.report))
            .build());
    public static final CardType NUMBER_SIGN = register(
        CardType.builder("number_sign", CardPack.INFINITY)
            .label("#")
            .sort(131)
            .choice(Choice.NUMBER)
            .effect(ctx -> ctx.game.numberSign(ctx.player, ctx.choices.number, false, ctx.report))
            .build());
    public static final CardType WILD_SKIP = register(
        CardType.builder("wild_skip", CardPack.INFINITY)
            .matchAliases("skip")
            .wild()
            .label("WØ")
            .sort(132)
            .effect(
                ctx -> TargetedEffects.apply(
                    ctx.game,
                    ctx.player,
                    List.of(ctx.game.nextPlayer()),
                    "skip",
                    new NBTTagCompound(),
                    ctx.report))
            .build());
    public static final CardType WILD_REVERSE = register(
        CardType.builder("wild_reverse", CardPack.INFINITY)
            .matchAliases("reverse")
            .wild()
            .label("W⇆")
            .sort(133)
            .effect(ctx -> ctx.game.reverse())
            .build());
    public static final CardType WILD_DRAW8 = register(
        CardType.builder("wild_draw8", CardPack.INFINITY)
            .wild()
            .label("+8")
            .sort(134)
            .draw(8)
            .effect(ctx -> ctx.game.addPendingDraw(8, ctx.player))
            .build());
    public static final CardType WILD_TIMES_TWO = register(
        CardType.builder("wild_times_two", CardPack.INFINITY)
            .wild()
            .label("×2")
            .sort(135)
            .effect(ctx -> ctx.game.timesTwo(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_TARGET_DRAW1 = register(
        CardType.builder("wild_target_draw1", CardPack.INFINITY)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("➶1")
            .sort(136)
            .effect(
                ctx -> TargetedEffects.draw(
                    ctx.game,
                    ctx.player,
                    ctx.choices.targets.stream()
                        .map(ctx.game::deAmputate)
                        .toList(),
                    1,
                    false,
                    ctx.report))
            .build());
    public static final CardType WILD_TARGET_DRAW4 = register(
        CardType.builder("wild_target_draw4", CardPack.INFINITY)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("➶4")
            .sort(137)
            .effect(
                ctx -> TargetedEffects.draw(
                    ctx.game,
                    ctx.player,
                    ctx.choices.targets.stream()
                        .map(ctx.game::deAmputate)
                        .toList(),
                    4,
                    false,
                    ctx.report))
            .build());
    public static final CardType WILD_ALL_DRAW2 = register(
        CardType.builder("wild_all_draw2", CardPack.INFINITY)
            .wild()
            .label("2E")
            .sort(138)
            .effect(
                ctx -> TargetedEffects.draw(
                    ctx.game,
                    ctx.player,
                    ctx.game.activePlayers()
                        .stream()
                        .filter(p -> !p.equals(ctx.player))
                        .toList(),
                    2,
                    false,
                    ctx.report))
            .build());
    public static final CardType WILD_ALL_DRAW4 = register(
        CardType.builder("wild_all_draw4", CardPack.INFINITY)
            .wild()
            .label("4E")
            .sort(139)
            .effect(
                ctx -> TargetedEffects.draw(
                    ctx.game,
                    ctx.player,
                    ctx.game.activePlayers()
                        .stream()
                        .filter(p -> !p.equals(ctx.player))
                        .toList(),
                    4,
                    false,
                    ctx.report))
            .build());
    public static final CardType WILD_D4 = dice(4, 140);
    public static final CardType WILD_D6 = dice(6, 141);
    public static final CardType WILD_D8 = dice(8, 142);
    public static final CardType WILD_D12 = dice(12, 143);
    public static final CardType WILD_D20 = dice(20, 144);
    public static final CardType WILD_HALVE = register(
        CardType.builder("wild_halve", CardPack.INFINITY)
            .wild()
            .label("÷2")
            .sort(145)
            .pick(CardType.PICK_HALF)
            .effect(ctx -> ctx.game.discardPicked(ctx.player, ctx.face.type(), ctx.choices, ctx.report))
            .build());
    public static final CardType WILD_MINUS_ONE = register(
        CardType.builder("wild_minus_one", CardPack.INFINITY)
            .matchAliases("minus_one")
            .wild()
            .label("W-1")
            .sort(146)
            .pick(1)
            .effect(ctx -> ctx.game.discardPicked(ctx.player, ctx.face.type(), ctx.choices, ctx.report))
            .build());
    public static final CardType WILD_MINUS_TWO = register(
        CardType.builder("wild_minus_two", CardPack.INFINITY)
            .wild()
            .label("-2")
            .sort(147)
            .pick(2)
            .effect(ctx -> ctx.game.discardPicked(ctx.player, ctx.face.type(), ctx.choices, ctx.report))
            .build());
    public static final CardType WILD_REVEAL_ONE = register(
        CardType.builder("wild_reveal_one", CardPack.INFINITY)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("W◉")
            .sort(148)
            .effect(
                ctx -> TargetedEffects.apply(
                    ctx.game,
                    ctx.player,
                    ctx.choices.targets.stream()
                        .map(ctx.game::deAmputate)
                        .toList(),
                    "reveal",
                    new NBTTagCompound(),
                    ctx.report))
            .build());
    public static final CardType JOKER = register(
        CardType.builder("joker", CardPack.INFINITY)
            .wild()
            .choice(Choice.NONE)
            .label("★")
            .sort(149)
            .effect(ctx -> ctx.game.joker(ctx.player, ctx.report))
            .build());
    public static final CardType WILD_NUMBER_SIGN = register(
        CardType.builder("wild_number_sign", CardPack.INFINITY)
            .wild()
            .choice(Choice.NUMBER)
            .label("W#")
            .sort(150)
            .effect(ctx -> ctx.game.numberSign(ctx.player, ctx.choices.number, true, ctx.report))
            .build());
    public static final CardType WILD_RECYCLE = register(
        CardType.builder("wild_recycle", CardPack.INFINITY)
            .wild()
            .label("♻")
            .sort(151)
            .effect(ctx -> ctx.game.recycle(ctx.player, null, ctx.report))
            .build());
    public static final CardType WILD_LUCKY_SEVEN = register(
        CardType.builder("wild_lucky_seven", CardPack.INFINITY)
            .wild()
            .label("777")
            .sort(152)
            .effect(ctx -> ctx.game.luckySeven(ctx.player, ctx.report))
            .build());

    public static final CardType WILD_TARGET_DRAW2 = register(
        CardType.builder("wild_target_draw2", CardPack.INFINITY)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("➶2")
            .sort(153)
            .effect(
                ctx -> TargetedEffects.draw(
                    ctx.game,
                    ctx.player,
                    ctx.choices.targets.stream()
                        .map(ctx.game::deAmputate)
                        .toList(),
                    2,
                    false,
                    ctx.report))
            .build());
    public static final CardType WILD_CARD_SWAP = register(
        CardType.builder("wild_card_swap", CardPack.INFINITY)
            .wild()
            .choice(Choice.COLOR_AND_TARGET)
            .label("⇄1")
            .sort(154)
            .effect(
                ctx -> TargetedEffects.apply(
                    ctx.game,
                    ctx.player,
                    ctx.choices.targets.stream()
                        .map(ctx.game::deAmputate)
                        .toList(),
                    "card_swap",
                    new NBTTagCompound(),
                    ctx.report))
            .build());

    public static final CardType REPLAY = register(
        CardType.builder("replay", CardPack.INFINITY)
            .match("reverse")
            .label("↻")
            .sort(155)
            .effect(ctx -> CardDecisions.replay(ctx.game, ctx.player))
            .build());
    public static final CardType WILD_REPLAY = register(
        CardType.builder("wild_replay", CardPack.INFINITY)
            .matchAliases("reverse")
            .wild()
            .match("reverse")
            .label("W↻")
            .sort(156)
            .effect(ctx -> CardDecisions.replay(ctx.game, ctx.player))
            .build());

    public static final CardType WILD_INFINITY = register(
        CardType.builder("wild_infinity", CardPack.INFINITY)
            .wild()
            .points(CardType.INFINITE_POINTS)
            .label("∞")
            .sort(157)
            .effect(ctx -> ctx.game.nextDrawsUntilColor(ctx.choices.color, ctx.report))
            .build());

    public static final CardType WILD_INFINITY_NO_U = register(
        CardType.builder("wild_infinity_no_u", CardPack.INFINITY)
            .wild()
            .label("NO U")
            .sort(158)
            .answersDraw()
            .effect(ctx -> ctx.game.reflectDraw(ctx.player, ctx.report))
            .build());

    private static CardType dice(int sides, int sort) {
        return register(
            CardType.builder("wild_d" + sides, CardPack.INFINITY)
                .wild()
                .label("D" + sides)
                .sort(sort)
                .effect(ctx -> ctx.game.rollDice(ctx.player, sides, ctx.report))
                .build());
    }

    public static void init() {}
}
