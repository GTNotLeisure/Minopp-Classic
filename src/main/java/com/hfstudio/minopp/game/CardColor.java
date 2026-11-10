package com.hfstudio.minopp.game;

import java.util.List;
import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;

public enum CardColor {

    RED(0xFFBE0C00, EnumChatFormatting.RED, Side.LIGHT),
    YELLOW(0xFFE7D004, EnumChatFormatting.GOLD, Side.LIGHT),
    GREEN(0xFF328A10, EnumChatFormatting.GREEN, Side.LIGHT),
    BLUE(0xFF1254AB, EnumChatFormatting.AQUA, Side.LIGHT),

    PINK(0xFFE0389A, EnumChatFormatting.LIGHT_PURPLE, Side.DARK),
    TEAL(0xFF14A39A, EnumChatFormatting.DARK_AQUA, Side.DARK),
    ORANGE(0xFFE8740C, EnumChatFormatting.GOLD, Side.DARK),
    PURPLE(0xFF6A2BB0, EnumChatFormatting.DARK_PURPLE, Side.DARK),

    WILD(0xFF1E1A19, EnumChatFormatting.WHITE, Side.ANY),
    /**
     * Infinity's rare grey numbers: they only match each other or the same number, and no effect can name them.
     */
    COLORLESS(0xFF8E8E96, EnumChatFormatting.GRAY, Side.ANY),

    TOPAZ(0xFFD99A32, EnumChatFormatting.GOLD, Side.LIGHT),
    AQUAMARINE(0xFF65CFE8, EnumChatFormatting.AQUA, Side.LIGHT),
    RUBY(0xFFAA1859, EnumChatFormatting.DARK_RED, Side.LIGHT),
    FLUORITE(0xFFB28ADD, EnumChatFormatting.LIGHT_PURPLE, Side.LIGHT),

    COPPER(0xFF8C522D, EnumChatFormatting.GOLD, Side.DARK),
    LAPIS(0xFF293663, EnumChatFormatting.BLUE, Side.DARK),
    PERIDOT(0xFFACCB32, EnumChatFormatting.GREEN, Side.DARK),
    QUARTZ(0xFFDFDFCE, EnumChatFormatting.WHITE, Side.DARK);

    public enum Side {
        LIGHT,
        DARK,
        ANY
    }

    public final int rgb;
    public final EnumChatFormatting chatColor;
    public final Side side;

    CardColor(int rgb, EnumChatFormatting chatColor, Side side) {
        this.rgb = rgb;
        this.chatColor = chatColor;
        this.side = side;
    }

    public static final List<CardColor> LIGHT = List.of(RED, YELLOW, GREEN, BLUE);
    public static final List<CardColor> DARK = List.of(PINK, TEAL, ORANGE, PURPLE);
    public static final List<CardColor> INFINITY_LIGHT = List
        .of(RED, YELLOW, GREEN, BLUE, TOPAZ, AQUAMARINE, RUBY, FLUORITE);
    public static final List<CardColor> INFINITY_DARK = List
        .of(PINK, TEAL, ORANGE, PURPLE, COPPER, LAPIS, PERIDOT, QUARTZ);

    public static List<CardColor> forSide(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    public static List<CardColor> forSide(boolean dark, boolean expanded) {
        return expanded ? dark ? INFINITY_DARK : INFINITY_LIGHT : forSide(dark);
    }

    public CardColor flipPartner() {
        if (side == Side.ANY) return this;
        boolean dark = side == Side.DARK;
        return forSide(!dark, true).get(forSide(dark, true).indexOf(this));
    }

    public boolean isWild() {
        return this == WILD;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "game.minopp.card.suit." + id();
    }

    public static CardColor byId(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public CardColor flexPartner() {
        return switch (this) {
            case RED -> BLUE;
            case BLUE -> RED;
            case YELLOW -> GREEN;
            case GREEN -> YELLOW;
            case PINK -> TEAL;
            case TEAL -> PINK;
            case ORANGE -> PURPLE;
            case PURPLE -> ORANGE;
            case TOPAZ -> AQUAMARINE;
            case AQUAMARINE -> TOPAZ;
            case RUBY -> FLUORITE;
            case FLUORITE -> RUBY;
            case COPPER -> LAPIS;
            case LAPIS -> COPPER;
            case PERIDOT -> QUARTZ;
            case QUARTZ -> PERIDOT;
            default -> WILD;
        };
    }
}
