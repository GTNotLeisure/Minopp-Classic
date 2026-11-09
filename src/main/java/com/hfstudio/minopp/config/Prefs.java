package com.hfstudio.minopp.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;

/** The personal settings shown in the table screen's personal tab, each backed by a client config field. */
public final class Prefs {

    private Prefs() {}

    public enum Type {
        BOOL,
        INT,
        CHOICE
    }

    public record Pref(String id, Type type, int def, int min, int max, int step, List<String> choices, IntSupplier get,
        Consumer<Integer> set) {

        public int value() {
            return get.getAsInt();
        }

        /** Sets the value without writing the config file, for slider drags and typing. */
        public void apply(int v) {
            set.accept(type == Type.CHOICE ? Math.floorMod(v, choices.size()) : Math.max(min, Math.min(max, v)));
        }

        public void change(int v) {
            apply(v);
            save();
        }

        public String langKey() {
            return "gui.minopp.pref." + id;
        }
    }

    private static ClientConfig.Display d() {
        return ClientConfig.display;
    }

    public static final List<Pref> ALL = List.of(
        new Pref("hudX", Type.INT, 1, -500, 2000, 1, null, () -> d().guiXPosition, v -> d().guiXPosition = v),
        new Pref("hudY", Type.INT, 30, -500, 2000, 1, null, () -> d().guiYPosition, v -> d().guiYPosition = v),
        new Pref("hudScale", Type.INT, 100, 50, 200, 10, null, () -> d().guiScale, v -> d().guiScale = v),
        new Pref(
            "showGameInfo",
            Type.BOOL,
            1,
            0,
            1,
            1,
            null,
            () -> d().enableGameHistoryGUI ? 1 : 0,
            v -> d().enableGameHistoryGUI = v != 0),
        new Pref(
            "classicInteraction",
            Type.BOOL,
            0,
            0,
            1,
            1,
            null,
            () -> d().classicInteraction ? 1 : 0,
            v -> d().classicInteraction = v != 0),
        new Pref("handScale", Type.INT, 100, 70, 150, 10, null, () -> d().handScale, v -> d().handScale = v),
        new Pref("cardInfo", Type.BOOL, 1, 0, 1, 1, null, () -> d().cardInfo ? 1 : 0, v -> d().cardInfo = v != 0),
        new Pref(
            "tableEffects",
            Type.BOOL,
            1,
            0,
            1,
            1,
            null,
            () -> d().tableEffects ? 1 : 0,
            v -> d().tableEffects = v != 0),
        new Pref(
            "showOthersThemes",
            Type.BOOL,
            1,
            0,
            1,
            1,
            null,
            () -> d().showOthersThemes ? 1 : 0,
            v -> d().showOthersThemes = v != 0));

    public static void save() {
        try {
            ConfigurationManager.save(ClientConfig.class);
        } catch (Exception e) {
            Mino.LOGGER.warn("Could not save the Mino++ client settings: {}", e.toString());
        }
    }
}
