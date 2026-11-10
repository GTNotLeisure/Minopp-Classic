package com.hfstudio.minopp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;

/**
 * The personal settings shown in the table screen's Personal tab. Other mods add their own with {@link #register}
 * (client side, during init); each setting brings its getter, setter and a way to save itself.
 * <p>
 * Names come from the lang key {@link Pref#langKey()}: {@code gui.minopp.pref.<id>} for Mino++'s own settings and
 * {@code gui.<modid>.pref.<name>} for an id written as {@code modid:name}, plus {@code .desc} for the description and
 * {@code .<choice>} for each choice.
 */
public class Prefs {

    private Prefs() {}

    public enum Type {
        BOOL,
        INT,
        CHOICE
    }

    /**
     * @param unit shown after an int value, such as "%"
     */
    public record Pref(String id, Type type, int def, int min, int max, int step, List<String> choices, String unit,
        IntSupplier get, IntConsumer set, Runnable saver) {

        public int value() {
            return get.getAsInt();
        }

        /**
         * Sets the value without saving, for slider drags and typing.
         */
        public void apply(int v) {
            set.accept(type == Type.CHOICE ? Math.floorMod(v, choices.size()) : Math.max(min, Math.min(max, v)));
        }

        public void change(int v) {
            apply(v);
            saver.run();
        }

        public String langKey() {
            int colon = id.indexOf(':');
            return colon < 0 ? "gui.minopp.pref." + id
                : "gui." + id.substring(0, colon) + ".pref." + id.substring(colon + 1);
        }

        public String format(int v) {
            return type == Type.INT ? v + unit : String.valueOf(v);
        }
    }

    private static final List<Pref> REGISTRY = new ArrayList<>();

    /**
     * Every registered setting, in the order they show.
     */
    public static List<Pref> all() {
        return Collections.unmodifiableList(REGISTRY);
    }

    public static Pref get(String id) {
        for (Pref p : REGISTRY) if (p.id.equals(id)) return p;
        return null;
    }

    public static Pref register(Pref pref) {
        if (get(pref.id) != null) throw new IllegalArgumentException("Personal setting already registered: " + pref.id);
        REGISTRY.add(pref);
        return pref;
    }

    public static Pref bool(String id, boolean def, BooleanSupplier get, Consumer<Boolean> set, Runnable saver) {
        return register(
            new Pref(
                id,
                Type.BOOL,
                def ? 1 : 0,
                0,
                1,
                1,
                null,
                "",
                () -> get.getAsBoolean() ? 1 : 0,
                v -> set.accept(v != 0),
                saver));
    }

    public static Pref integer(String id, int def, int min, int max, int step, String unit, IntSupplier get,
        IntConsumer set, Runnable saver) {
        return register(new Pref(id, Type.INT, def, min, max, step, null, unit, get, set, saver));
    }

    public static Pref choice(String id, List<String> choices, int def, IntSupplier get, IntConsumer set,
        Runnable saver) {
        return register(new Pref(id, Type.CHOICE, def, 0, choices.size() - 1, 1, choices, "", get, set, saver));
    }

    private static ClientConfig.Display d() {
        return ClientConfig.display;
    }

    static {
        integer("hudX", 1, -500, 2000, 1, "", () -> d().guiXPosition, v -> d().guiXPosition = v, Prefs::save);
        integer("hudY", 30, -500, 2000, 1, "", () -> d().guiYPosition, v -> d().guiYPosition = v, Prefs::save);
        integer("hudScale", 100, 50, 200, 10, "%", () -> d().guiScale, v -> d().guiScale = v, Prefs::save);
        bool("showGameInfo", true, () -> d().enableGameHistoryGUI, v -> d().enableGameHistoryGUI = v, Prefs::save);
        bool("classicInteraction", false, () -> d().classicInteraction, v -> d().classicInteraction = v, Prefs::save);
        integer("handScale", 100, 70, 150, 10, "%", () -> d().handScale, v -> d().handScale = v, Prefs::save);
        bool("cardInfo", true, () -> d().cardInfo, v -> d().cardInfo = v, Prefs::save);
        bool("tableEffects", true, () -> d().tableEffects, v -> d().tableEffects = v, Prefs::save);
        bool("showOthersThemes", true, () -> d().showOthersThemes, v -> d().showOthersThemes = v, Prefs::save);
    }

    /**
     * Saves Mino++'s own client settings.
     */
    public static void save() {
        try {
            ConfigurationManager.save(ClientConfig.class);
        } catch (Exception | LinkageError e) {
            Mino.LOGGER.warn("Could not save the Mino++ client settings: {}", e.toString());
        }
    }
}
