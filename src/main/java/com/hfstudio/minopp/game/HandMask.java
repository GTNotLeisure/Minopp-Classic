package com.hfstudio.minopp.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.minopp.util.NbtUtil;

public class HandMask {

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
        return clientTableTag(tableTag, viewer, showBacks, false);
    }

    /**
     * Creates a client snapshot, excluding server-only challenge evidence even when all hands are revealed.
     */
    public static NBTTagCompound clientTableTag(NBTTagCompound tableTag, UUID viewer, boolean showBacks,
        boolean revealHands) {
        if (!tableTag.hasKey("game", 10)) return tableTag;
        NBTTagCompound copy = (NBTTagCompound) tableTag.copy();
        NBTTagCompound game = copy.getCompoundTag("game");
        game.removeTag("challengeableHadColor");
        if (revealHands) return copy;
        boolean darkSide = game.getBoolean("darkSide");
        UUID revealed = game.getInteger("revealOneTurns") > 0 && NbtUtil.hasUUID(game, "revealedOne")
            ? NbtUtil.getUUID(game, "revealedOne")
            : null;
        Set<UUID> shown = new HashSet<>();
        NBTTagList decisions = game.getTagList("decisions", 10);
        for (int i = 0; i < decisions.tagCount(); i++) {
            NBTTagCompound decision = decisions.getCompoundTagAt(i);
            if (CardDecisions.SWAP.equals(decision.getString("typeId")))
                shown.add(NbtUtil.getUUID(decision.getCompoundTag("data"), "target"));
        }
        NBTTagList players = game.getTagList("players", 10);
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            UUID holder = NbtUtil.hasUUID(player, "uuid") ? NbtUtil.getUUID(player, "uuid") : null;
            if (holder != null && (holder.equals(viewer) || holder.equals(revealed) || shown.contains(holder)))
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
