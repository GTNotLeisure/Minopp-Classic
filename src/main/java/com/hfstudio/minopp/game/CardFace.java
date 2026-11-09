package com.hfstudio.minopp.game;

import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import com.hfstudio.minopp.util.Chat;

public final class CardFace implements Comparable<CardFace> {

    public final String typeId;
    public final CardColor color;
    public final int number;
    public final CardColor flexColor;

    private final int hash;

    public CardFace(String typeId, CardColor color, int number, CardColor flexColor) {
        this.typeId = typeId;
        this.color = color;
        this.number = number;
        this.flexColor = flexColor;
        this.hash = Objects.hash(typeId, color, number, flexColor);
    }

    public CardFace(String typeId, CardColor color, int number) {
        this(typeId, color, number, null);
    }

    public CardType type() {
        return CardTypes.get(typeId);
    }

    public boolean isHidden() {
        return HandMask.HIDDEN_TYPE.equals(typeId);
    }

    public boolean isWild() {
        return color == CardColor.WILD;
    }

    public boolean isNumber() {
        return type().numbered;
    }

    public String symbolKey() {
        return type().numbered ? "n" + number : type().matchKey;
    }

    public IChatComponent getDisplayName() {
        if (isWild()) {
            IChatComponent c = Chat.translatable("game.minopp.card.wild_prefix");
            c.appendSibling(getFaceName());
            return c;
        }
        IChatComponent c = Chat.translatable(color.langKey());
        c.appendText(" ");
        c.appendSibling(getFaceName());
        if (flexColor != null) {
            c.appendText(" / ");
            c.appendSibling(Chat.translatable(flexColor.langKey()));
        }
        return c;
    }

    public IChatComponent getFaceName() {
        CardType type = type();
        if (type.numbered) return new ChatComponentText(String.valueOf(number));
        return Chat.translatable("game.minopp.card.type." + typeId);
    }

    public String shortLabel() {
        CardType type = type();
        if (type.numbered) return String.valueOf(number);
        return type.shortLabel;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("t", typeId);
        tag.setString("c", color.id());
        if (number >= 0) tag.setByte("n", (byte) number);
        if (flexColor != null) tag.setString("f", flexColor.id());
        return tag;
    }

    public static CardFace fromTag(NBTTagCompound tag) {
        CardColor color = CardColor.byId(tag.getString("c"));
        return new CardFace(
            tag.getString("t"),
            color == null ? CardColor.WILD : color,
            tag.hasKey("n") ? tag.getByte("n") : -1,
            CardColor.byId(tag.getString("f")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CardFace f)) return false;
        return number == f.number && typeId.equals(f.typeId) && color == f.color && flexColor == f.flexColor;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public int compareTo(CardFace o) {
        int c = Integer.compare(color.ordinal(), o.color.ordinal());
        if (c != 0) return c;
        CardType a = type(), b = o.type();
        c = Integer.compare(a.sortOrder, b.sortOrder);
        if (c != 0) return c;
        c = Integer.compare(number, o.number);
        if (c != 0) return c;
        return typeId.compareTo(o.typeId);
    }

    @Override
    public String toString() {
        return color.id() + ":" + typeId + (number >= 0 ? ":" + number : "");
    }
}
