package com.hfstudio.minopp.config;

import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.SimpleGuiConfig;
import com.hfstudio.minopp.Mino;

public class MinoGuiConfig extends SimpleGuiConfig {

    public MinoGuiConfig(GuiScreen parentScreen) throws ConfigException {
        super(parentScreen, Mino.MOD_ID, Mino.MOD_NAME, true, ClientConfig.class);
    }
}
