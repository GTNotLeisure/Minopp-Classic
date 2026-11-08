package com.hfstudio.minopp.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Rounded pill button in the settings UI style; optional color accent (e.g. for color choices). */
@SideOnly(Side.CLIENT)
public class UiButton extends GuiButton {

    private final int accent;
    private float hoverAnim;

    public UiButton(int id, int x, int y, int w, int h, String text) {
        this(id, x, y, w, h, text, 0);
    }

    public UiButton(int id, int x, int y, int w, int h, String text, int accent) {
        super(id, x, y, w, h, text);
        this.accent = accent;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hover = enabled && mouseX >= xPosition
            && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        hoverAnim += ((hover ? 1f : 0f) - hoverAnim) * 0.35f;
        int lift = (int) (hoverAnim * 2);
        int y = yPosition - lift;
        int border = hover ? UiDraw.GLOW : (enabled ? 0xFF5A5A5A : 0xFF3A3A3A);
        int top, bottom;
        if (accent != 0) {
            top = UiDraw.lerpColor(accent | 0xFF000000, 0xFFFFFFFF, 0.15f + hoverAnim * 0.15f);
            bottom = UiDraw.lerpColor(accent | 0xFF000000, 0xFF000000, 0.3f);
        } else {
            top = enabled ? (hover ? 0xFF4A4A4A : 0xFF2E2E2E) : 0xFF242424;
            bottom = 0xFF141414;
        }
        UiDraw.roundFrame(xPosition, y, xPosition + width, y + height, Math.min(8, height / 2), 2, border, top, bottom);
        int color = enabled ? 0xFFFFFFFF : 0xFF777777;
        mc.fontRenderer.drawStringWithShadow(
            displayString,
            xPosition + width / 2 - mc.fontRenderer.getStringWidth(displayString) / 2,
            y + (height - 8) / 2,
            color);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
