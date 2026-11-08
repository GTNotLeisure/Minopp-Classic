package com.hfstudio.minopp.game;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;

import com.hfstudio.minopp.util.NbtUtil;

/**
 * A physical card. Every card has a light face; cards of a Flip deck also have a dark face on the back.
 * Identity is the per-game unique id, as several physical cards may print the same face.
 */
public final class Card implements Comparable<Card> {

    public final int uid;
    public final CardFace light;
    public final CardFace dark;
    /** Player who last held/played this card; used to pick the card theme on other clients. Not game relevant. */
    public UUID owner;

    public Card(int uid, CardFace light, CardFace dark) {
        this.uid = uid;
        this.light = light;
        this.dark = dark;
    }

    public CardFace face(boolean darkSide) {
        return darkSide && dark != null ? dark : light;
    }

    /** The face that is not currently active (shown on the back). */
    public CardFace backFace(boolean darkSide) {
        if (dark == null) return null;
        return darkSide ? light : dark;
    }

    public IChatComponent getDisplayName(boolean darkSide) {
        return face(darkSide).getDisplayName();
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("u", uid);
        tag.setTag("l", light.toTag());
        if (dark != null) tag.setTag("d", dark.toTag());
        if (owner != null) NbtUtil.putUUID(tag, "o", owner);
        return tag;
    }

    public static Card fromTag(NBTTagCompound tag) {
        Card card = new Card(
            tag.getInteger("u"),
            CardFace.fromTag(tag.getCompoundTag("l")),
            tag.hasKey("d") ? CardFace.fromTag(tag.getCompoundTag("d")) : null);
        if (NbtUtil.hasUUID(tag, "o")) card.owner = NbtUtil.getUUID(tag, "o");
        return card;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Card c && c.uid == uid);
    }

    @Override
    public int hashCode() {
        return uid;
    }

    @Override
    public int compareTo(Card o) {
        int c = light.compareTo(o.light);
        return c != 0 ? c : Integer.compare(uid, o.uid);
    }

    public int compareTo(Card o, boolean darkSide) {
        int c = face(darkSide).compareTo(o.face(darkSide));
        return c != 0 ? c : Integer.compare(uid, o.uid);
    }

    @Override
    public String toString() {
        return "Card#" + uid + "[" + light + (dark != null ? " | " + dark : "") + "]";
    }
}
