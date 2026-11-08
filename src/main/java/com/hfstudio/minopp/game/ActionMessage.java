package com.hfstudio.minopp.game;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IChatComponent;

import com.hfstudio.minopp.util.Chat;

public final class ActionMessage {

    private final Type type;
    private final IChatComponent message;

    public ActionMessage(Type type, IChatComponent message) {
        this.type = type;
        this.message = message;
    }

    public Type type() {
        return type;
    }

    public IChatComponent message() {
        return message;
    }

    public ActionMessage(NBTTagCompound tag) {
        this(Type.valueOf(tag.getString("type")), Chat.fromJson(tag.getString("message")));
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", type.name());
        tag.setString("message", Chat.toJson(message));
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionMessage)) return false;
        ActionMessage that = (ActionMessage) o;
        return type == that.type && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        // ChatStyle#hashCode NPEs in 1.7.10 when no color is set, so hash the text instead
        return type.hashCode() * 31 + message.getUnformattedText()
            .hashCode();
    }

    public enum Type {

        STATE,
        FAIL,
        MESSAGE_ALL;

        public boolean isEphemeral() {
            return this == FAIL || this == MESSAGE_ALL;
        }
    }

    public static final ActionMessage NO_GAME = new ActionMessage(
        Type.STATE,
        Chat.translatable("game.minopp.play.no_game"));
}
