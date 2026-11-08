package com.hfstudio.minopp.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;

/**
 * Client configuration, using GTNHLib's config framework (the 1.20.1 version used ForgeConfigSpec + YACL).
 */
@Config(modid = Mino.MOD_ID, filename = "minopp-client", configSubDirectory = "minopp")
@Config.LangKeyPattern(pattern = "minopp.gui.config.%cat.%field", fullyQualified = true)
@Config.Comment("Mino++: Classic client configuration")
public class ClientConfig {

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(ClientConfig.class);
    }

    public static final Display display = new Display();

    @Config.Comment("Display settings")
    public static class Display {

        @Config.Comment("X position of the game GUI. Negative goes left, positive goes right. Default is 20.")
        @Config.DefaultInt(20)
        @Config.RangeInt(min = -10000, max = 10000)
        public int guiXPosition = 20;

        @Config.Comment("Y position of the game GUI. Negative goes up, positive goes down. Default is 60.")
        @Config.DefaultInt(60)
        @Config.RangeInt(min = -10000, max = 10000)
        public int guiYPosition = 60;

        @Config.Comment("For a more realistic experience, you can choose to disable the game GUI and any on screen reminders "
            + "(drawing from the deck, turn reminders), the only element that will be displayed is the top card text & "
            + "jump-in reminders. Default is true (enabled).")
        @Config.DefaultBoolean(true)
        public boolean enableGameHistoryGUI = true;

        @Config.Comment("Card theme used for your cards. Themes come from resource packs (assets/minopp/themes.json). "
            + "Other players who have the same theme installed see your cards in it.")
        @Config.DefaultString("classic")
        public String cardTheme = "classic";

        @Config.Comment("Show the cards played by other players in their own theme when you have it installed.")
        @Config.DefaultBoolean(true)
        public boolean showOthersThemes = true;
    }
}
