package com.hfstudio.minopp.game;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.util.NbtUtil;

public final class PendingDecision {

    public enum Type {
        /** Answer: 1 = challenge, 0 = accept. */
        CHALLENGE,
        /** Answer: color ordinal. */
        ROULETTE_COLOR,
        /** Answer: target uuid. */
        POINT_TAKEN
    }

    public final Type type;
    public final UUID player;
    public final UUID source;
    public final long deadline;
    public int answer = -1;
    public UUID answerTarget;

    public PendingDecision(Type type, UUID player, UUID source, long deadline) {
        this.type = type;
        this.player = player;
        this.source = source;
        this.deadline = deadline;
    }

    public boolean isAnswered() {
        return answer >= 0 || answerTarget != null;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", type.name());
        NbtUtil.putUUID(tag, "player", player);
        if (source != null) NbtUtil.putUUID(tag, "source", source);
        tag.setLong("deadline", deadline);
        tag.setInteger("answer", answer);
        if (answerTarget != null) NbtUtil.putUUID(tag, "answerTarget", answerTarget);
        return tag;
    }

    public static PendingDecision fromTag(NBTTagCompound tag) {
        PendingDecision d = new PendingDecision(
            Type.valueOf(tag.getString("type")),
            NbtUtil.getUUID(tag, "player"),
            NbtUtil.hasUUID(tag, "source") ? NbtUtil.getUUID(tag, "source") : null,
            tag.getLong("deadline"));
        d.answer = tag.getInteger("answer");
        if (NbtUtil.hasUUID(tag, "answerTarget")) d.answerTarget = NbtUtil.getUUID(tag, "answerTarget");
        return d;
    }
}
