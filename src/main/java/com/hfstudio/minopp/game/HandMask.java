package com.hfstudio.minopp.game;

import java.util.HashMap;
import java.util.Map;
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
        NBTTagCompound copy = (NBTTagCompound) tableTag.copy();
        copy.removeTag("handAccess");
        copy.removeTag("bots");
        NBTTagCompound seats = copy.getCompoundTag("seats");
        for (String key : seats.func_150296_c()) seats.getCompoundTag(key)
            .removeTag("hand");
        if (!copy.hasKey("game", 10)) return copy;
        NBTTagCompound game = copy.getCompoundTag("game");
        game.removeTag("challengeableHadColor");
        long now = System.currentTimeMillis();
        boolean darkSide = game.getBoolean("darkSide");
        NBTTagList players = game.getTagList("players", 10);
        boolean participant = false;
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            if (viewer != null && NbtUtil.hasUUID(player, "uuid")
                && viewer.equals(NbtUtil.getUUID(player, "uuid"))
                && !player.getBoolean("eliminated")) {
                participant = true;
                break;
            }
        }
        long allUntil = revealHands || game.getInteger("revealTurns") > 0 ? Long.MAX_VALUE
            : Math.max(game.getLong("revealUntil"), deadline(game.getTagList("peekUntil", 10), viewer));
        UUID revealed = game.getInteger("revealOneTurns") > 0 && NbtUtil.hasUUID(game, "revealedOne")
            ? NbtUtil.getUUID(game, "revealedOne")
            : null;
        Map<UUID, Long> shown = new HashMap<>();
        NBTTagList timed = game.getTagList("shownUntil", 10);
        for (int i = 0; i < timed.tagCount(); i++) {
            NBTTagCompound entry = timed.getCompoundTagAt(i);
            if (NbtUtil.hasUUID(entry, "player")) shown.put(NbtUtil.getUUID(entry, "player"), entry.getLong("until"));
        }
        if (revealed != null) shown.put(revealed, Long.MAX_VALUE);
        NBTTagList peeks = game.getTagList("targetedPeeks", 10);
        if (participant) for (int i = 0; i < peeks.tagCount(); i++) {
            NBTTagCompound peek = peeks.getCompoundTagAt(i);
            if (NbtUtil.hasUUID(peek, "viewer") && viewer.equals(NbtUtil.getUUID(peek, "viewer"))
                && NbtUtil.hasUUID(peek, "target")) {
                shown.merge(NbtUtil.getUUID(peek, "target"), peek.getLong("until"), Math::max);
            }
        }
        NBTTagList decisions = game.getTagList("decisions", 10);
        for (int i = 0; i < decisions.tagCount(); i++) {
            NBTTagCompound decision = decisions.getCompoundTagAt(i);
            NBTTagCompound data = decision.getCompoundTag("data");
            if (CardDecisions.SWAP.equals(decision.getString("typeId")) && viewer != null
                && NbtUtil.hasUUID(decision, "player")
                && viewer.equals(NbtUtil.getUUID(decision, "player"))
                && data.hasKey("offered")
                && NbtUtil.hasUUID(data, "target"))
                shown.merge(NbtUtil.getUUID(data, "target"), decision.getLong("deadline"), Math::max);
        }
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            UUID holder = NbtUtil.hasUUID(player, "uuid") ? NbtUtil.getUUID(player, "uuid") : null;
            boolean own = holder != null && holder.equals(viewer);
            long until = own ? Long.MAX_VALUE
                : participant && holder != null && !player.getBoolean("eliminated")
                    ? Math.max(allUntil, shown.getOrDefault(holder, 0L))
                    : 0;
            player.setLong("handVisibleFor", until == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, until - now));
            if (own) continue;
            NBTTagList hand = player.getTagList("hand", 10);
            for (int j = 0; j < hand.tagCount(); j++)
                maskCard(hand.getCompoundTagAt(j), darkSide, showBacks, until <= now);
        }
        NBTTagList deck = game.getTagList("deck", 10);
        for (int i = 0; i < deck.tagCount(); i++)
            maskCard(deck.getCompoundTagAt(i), darkSide, showBacks && i == deck.tagCount() - 1, true);
        game.removeTag("peekUntil");
        game.removeTag("shownUntil");
        game.removeTag("targetedPeeks");
        if (viewer == null || !NbtUtil.hasUUID(game, "debugger") || !viewer.equals(NbtUtil.getUUID(game, "debugger"))) {
            game.removeTag("debugHand");
            game.removeTag("debugDrawn");
        }
        return copy;
    }

    private static long deadline(NBTTagList list, UUID viewer) {
        if (viewer == null) return 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (NbtUtil.hasUUID(entry, "player") && viewer.equals(NbtUtil.getUUID(entry, "player")))
                return entry.getLong("until");
        }
        return 0;
    }

    private static void maskCard(NBTTagCompound card, boolean darkSide, boolean showBacks, boolean hideActive) {
        String active = darkSide && card.hasKey("d") ? "d" : "l";
        if (hideActive) card.setTag(active, HIDDEN_FACE.copy());
        if (!showBacks && card.hasKey("d")) card.setTag(active.equals("d") ? "l" : "d", HIDDEN_FACE.copy());
    }
}
