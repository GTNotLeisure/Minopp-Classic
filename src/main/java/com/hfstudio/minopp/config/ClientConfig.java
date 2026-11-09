package com.hfstudio.minopp.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;

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

        @Config.Comment("X position of the game GUI. Negative goes left, positive goes right. Default is 1 (the left edge).")
        @Config.DefaultInt(1)
        @Config.RangeInt(min = -10000, max = 10000)
        public int guiXPosition = 1;

        @Config.Comment("Y position of the game GUI. Negative goes up, positive goes down. Default is 30.")
        @Config.DefaultInt(30)
        @Config.RangeInt(min = -10000, max = 10000)
        public int guiYPosition = 30;

        @Config.Comment("Use the classic interaction: the hand is fanned vertically on the right, scroll to pick a card and "
            + "right-click the table to play it (W/S pick cards in interaction mode). When false (default), the hand is "
            + "spread along the bottom of the screen; right-click opens interaction mode to point at cards (A/D to pick, "
            + "click to play), right-click again to move freely.")
        @Config.DefaultBoolean(false)
        public boolean classicInteraction = false;

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

        @Config.Comment("Size of the game info text, in percent. Default is 100.")
        @Config.DefaultInt(100)
        @Config.RangeInt(min = 50, max = 200)
        public int guiScale = 100;

        @Config.Comment("Size of the cards in your hand, in percent. Default is 100.")
        @Config.DefaultInt(100)
        @Config.RangeInt(min = 70, max = 150)
        public int handScale = 100;

        @Config.Comment("Show the name, points and effect of the selected special card. Shift-click a card to toggle.")
        @Config.DefaultBoolean(true)
        public boolean cardInfo = true;

        @Config.Comment("Sound pack per game sound, as slot=pack;... (packs: mino, minecraft, note, off). Set in the table's Sounds tab.")
        @Config.DefaultString("")
        public String soundPacks = "";

        @Config.Comment("Play the animations on the table (skip signs, color pillars, coins...). Turn off on slow machines.")
        @Config.DefaultBoolean(true)
        public boolean tableEffects = true;
    }
}
