package com.hfstudio.minopp.gui.ui;

import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.Loader;

public final class SearchMatch {

    private static Object necApi;
    private static Method necContains;
    private static boolean resolved;

    private SearchMatch() {}

    // routes through NeverEnoughCharacters when present so pinyin queries match Chinese text
    public static boolean matches(String text, String query) {
        if (query == null || query.isEmpty()) return true;
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        if (plain == null) return false;
        String lower = plain.toLowerCase(Locale.ROOT), q = query.toLowerCase(Locale.ROOT);
        if (lower.contains(q)) return true;
        resolve();
        if (necContains == null) return false;
        try {
            return (Boolean) necContains.invoke(necApi, lower, q);
        } catch (ReflectiveOperationException | RuntimeException e) {
            necContains = null;
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        if (!Loader.isModLoaded("nech")) return;
        try {
            Class<?> api = Class.forName("com.asdflj.nech.API");
            necApi = api.getField("INSTANCE")
                .get(null);
            necContains = api.getMethod("contains", String.class, String.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            necContains = null;
        }
    }
}
