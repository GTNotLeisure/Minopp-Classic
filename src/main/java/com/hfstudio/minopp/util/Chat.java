package com.hfstudio.minopp.util;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/** Small helpers mirroring 1.20's Component factory methods. */
public final class Chat {

    private Chat() {}

    public static IChatComponent translatable(String key, Object... args) {
        return new ChatComponentTranslation(key, args);
    }

    public static IChatComponent literal(String text) {
        return new ChatComponentText(text);
    }

    public static IChatComponent empty() {
        return new ChatComponentText("");
    }

    public static String toJson(IChatComponent component) {
        return IChatComponent.Serializer.func_150696_a(component);
    }

    public static IChatComponent fromJson(String json) {
        return IChatComponent.Serializer.func_150699_a(json);
    }
}
