package com.hfstudio.minopp.gui.ui;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ChoiceButton extends UiButton {

    public ChoiceButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    public ChoiceButton(int id, int x, int y, int width, int height, String text, int accent) {
        super(id, x, y, width, height, text, accent);
    }

    @Override
    protected void drawLabel(Minecraft mc, int y, int color) {
        UiDraw.fittedCentered(
            mc.fontRenderer,
            displayString,
            xPosition + width / 2f,
            y + (height - 8) / 2f,
            width - 10,
            color);
    }
}
