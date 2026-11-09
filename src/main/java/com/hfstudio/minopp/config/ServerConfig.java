package com.hfstudio.minopp.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.hfstudio.minopp.Mino;

@Config(modid = Mino.MOD_ID, filename = "minopp-server", configSubDirectory = "minopp")
@Config.LangKeyPattern(pattern = "minopp.gui.config.%cat.%field", fullyQualified = true)
@Config.Comment("Mino++: Classic server configuration")
public class ServerConfig {

    public static void registerConfig() throws ConfigException {
        ConfigurationManager.registerConfig(ServerConfig.class);
    }

    public static final Gameplay gameplay = new Gameplay();

    @Config.Comment("Gameplay settings (server side)")
    public static class Gameplay {

        @Config.Comment("Let rooms turn on the \"see everyone's hand\" rule. Each table decides for itself (off by "
            + "default); when this is false no table can, and the server never sends the faces of other players' cards "
            + "(or the order of the deck) to clients, so they cannot be seen even with a modified client.")
        @Config.DefaultBoolean(true)
        public boolean allowPeeking = true;

        @Config.Comment("Let every player add and remove bots from the table lobby. When false (default), only the "
            + "room's host and operators can.")
        @Config.DefaultBoolean(false)
        public boolean everyoneCanManageBots = false;
    }
}
