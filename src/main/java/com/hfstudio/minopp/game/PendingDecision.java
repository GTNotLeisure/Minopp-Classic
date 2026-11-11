package com.hfstudio.minopp.game;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.util.NbtUtil;

public class PendingDecision {

    public enum Type {
        /**
         * Answer: 1 = challenge, 0 = accept.
         */
        CHALLENGE,
        /**
         * Answer: color ordinal.
         */
        ROULETTE_COLOR,
        /**
         * Answer: target uuid.
         */
        POINT_TAKEN,
        CUSTOM
    }

    public final Type type;
    public final UUID id;
    public final String typeId;
    public final NBTTagCompound data;
    public final UUID player;
    public final UUID source;
    public final long deadline;
    private final long automaticAnswerAfter;
    public int answer = -1;
    public UUID answerTarget;

    public PendingDecision(Type type, UUID player, UUID source, long deadline) {
        this(UUID.randomUUID(), type, type.name(), player, source, deadline, automaticAnswerTime(), null);
        if (type == Type.CUSTOM) throw new IllegalArgumentException("Custom decisions require a registered type id");
    }

    public PendingDecision(String typeId, UUID player, UUID source, long deadline, NBTTagCompound data) {
        this(
            UUID.randomUUID(),
            Type.CUSTOM,
            DecisionTypes.get(typeId)
                .id(),
            player,
            source,
            deadline,
            automaticAnswerTime(),
            Objects.requireNonNull(data, "data"));
    }

    private PendingDecision(UUID id, Type type, String typeId, UUID player, UUID source, long deadline,
        long automaticAnswerAfter, NBTTagCompound data) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = type;
        this.typeId = typeId;
        this.data = data == null ? new NBTTagCompound() : (NBTTagCompound) data.copy();
        this.player = Objects.requireNonNull(player, "player");
        this.source = source;
        this.deadline = deadline;
        this.automaticAnswerAfter = automaticAnswerAfter;
    }

    private static long automaticAnswerTime() {
        return System.currentTimeMillis() + AutoPlayer.MIN_REACTION_TICKS * 50L;
    }

    public boolean canAnswerAutomatically(long now) {
        return now >= automaticAnswerAfter;
    }

    public boolean isAnswered() {
        return answer >= 0 || answerTarget != null;
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NbtUtil.putUUID(tag, "id", id);
        tag.setString("type", type.name());
        if (type == Type.CUSTOM) {
            tag.setString("typeId", typeId);
            tag.setTag("data", data.copy());
        }
        NbtUtil.putUUID(tag, "player", player);
        if (source != null) NbtUtil.putUUID(tag, "source", source);
        tag.setLong("deadline", deadline);
        tag.setLong("automaticAnswerAfter", automaticAnswerAfter);
        tag.setInteger("answer", answer);
        if (answerTarget != null) NbtUtil.putUUID(tag, "answerTarget", answerTarget);
        return tag;
    }

    public static PendingDecision fromTag(NBTTagCompound tag) {
        Type type = Type.valueOf(tag.getString("type"));
        String typeId = type == Type.CUSTOM ? DecisionTypes.get(tag.getString("typeId"))
            .id() : type.name();
        UUID player = NbtUtil.getUUID(tag, "player");
        UUID source = NbtUtil.hasUUID(tag, "source") ? NbtUtil.getUUID(tag, "source") : null;
        long deadline = tag.getLong("deadline");
        UUID id = NbtUtil.hasUUID(tag, "id") ? NbtUtil.getUUID(tag, "id")
            : UUID.nameUUIDFromBytes(
                (typeId + ":" + player + ":" + source + ":" + deadline).getBytes(StandardCharsets.UTF_8));
        PendingDecision d = new PendingDecision(
            id,
            type,
            typeId,
            player,
            source,
            deadline,
            tag.hasKey("automaticAnswerAfter") ? tag.getLong("automaticAnswerAfter") : automaticAnswerTime(),
            type == Type.CUSTOM ? tag.getCompoundTag("data") : null);
        d.answer = tag.hasKey("answer") ? tag.getInteger("answer") : -1;
        if (NbtUtil.hasUUID(tag, "answerTarget")) d.answerTarget = NbtUtil.getUUID(tag, "answerTarget");
        return d;
    }
}
