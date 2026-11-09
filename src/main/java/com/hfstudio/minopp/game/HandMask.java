package com.hfstudio.minopp.game;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.minopp.util.NbtUtil;

public final class HandMask {

    private HandMask() {}

    public static final String HIDDEN_TYPE = "hidden";

    private static final NBTTagCompound HIDDEN_FACE = new CardFace(HIDDEN_TYPE, CardColor.WILD, -1).toTag();

    public static NBTTagCompound maskTableTag(NBTTagCompound tableTag, UUID viewer) {
        return maskTableTag(tableTag, viewer, true);
    }

    /**
     * Hides the faces of everyone else's hand and of the deck. With {@code showBacks} the side facing away from its
     * holder stays visible, as with real Flip cards; without it both sides are hidden.
     */
    public static NBTTagCompound maskTableTag(NBTTagCompound tableTag, UUID viewer, boolean showBacks) {
        if (!tableTag.hasKey("game", 10)) return tableTag;
        NBTTagCompound copy = (NBTTagCompound) tableTag.copy();
        NBTTagCompound game = copy.getCompoundTag("game");
        boolean darkSide = game.getBoolean("darkSide");
        NBTTagList players = game.getTagList("players", 10);
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            if (viewer != null && NbtUtil.hasUUID(player, "uuid") && viewer.equals(NbtUtil.getUUID(player, "uuid")))
                continue;
            maskCards(player.getTagList("hand", 10), darkSide, showBacks);
        }
        maskCards(game.getTagList("deck", 10), darkSide, showBacks);
        return copy;
    }

    private static void maskCards(NBTTagList cards, boolean darkSide, boolean showBacks) {
        for (int i = 0; i < cards.tagCount(); i++) {
            NBTTagCompound card = cards.getCompoundTagAt(i);
            String active = darkSide && card.hasKey("d") ? "d" : "l";
            card.setTag(active, HIDDEN_FACE.copy());
            if (!showBacks && card.hasKey("d")) card.setTag(active.equals("d") ? "l" : "d", HIDDEN_FACE.copy());
        }
    }
}
