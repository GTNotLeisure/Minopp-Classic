package com.hfstudio.minopp.game;

import net.minecraft.nbt.NBTTagCompound;

public record TurnTransition(int fromSeat, int toSeat, boolean antiClockwise, int remainingTicks) {

    public static final int DURATION_TICKS = 18;

    public TurnTransition {
        remainingTicks = Math.max(0, Math.min(DURATION_TICKS, remainingTicks));
    }

    public TurnTransition advance() {
        return new TurnTransition(fromSeat, toSeat, antiClockwise, remainingTicks - 1);
    }

    public float progress(float elapsedTicks) {
        return Math.max(0, Math.min(1, (DURATION_TICKS - remainingTicks + elapsedTicks) / DURATION_TICKS));
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("fromSeat", fromSeat);
        tag.setInteger("toSeat", toSeat);
        tag.setBoolean("antiClockwise", antiClockwise);
        tag.setInteger("remainingTicks", remainingTicks);
        return tag;
    }

    public static TurnTransition fromTag(NBTTagCompound tag) {
        return new TurnTransition(
            tag.getInteger("fromSeat"),
            tag.getInteger("toSeat"),
            tag.getBoolean("antiClockwise"),
            tag.getInteger("remainingTicks"));
    }
}
