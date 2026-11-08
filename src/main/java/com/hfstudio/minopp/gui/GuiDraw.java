package com.hfstudio.minopp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** GuiGraphics-like helpers for 1.7.10. */
@SideOnly(Side.CLIENT)
public final class GuiDraw {

    private GuiDraw() {}

    public static void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Blits a region of a texture: dest (x, y, w, h), source (u, v, uw, vh) in pixels of a (texW x texH) texture.
     */
    public static void blit(ResourceLocation texture, float x, float y, float w, float h, float u, float v, float uw, float vh,
        float texW, float texH) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        blitBound(x, y, w, h, u, v, uw, vh, texW, texH);
    }

    public static void blitBound(float x, float y, float w, float h, float u, float v, float uw, float vh, float texW, float texH) {
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        float u0 = u / texW, u1 = (u + uw) / texW, v0 = v / texH, v1 = (v + vh) / texH;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, 0, u0, v1);
        t.addVertexWithUV(x + w, y + h, 0, u1, v1);
        t.addVertexWithUV(x + w, y, 0, u1, v0);
        t.addVertexWithUV(x, y, 0, u0, v0);
        t.draw();
    }

    public static void drawString(FontRenderer font, String text, int x, int y, int color) {
        font.drawStringWithShadow(text, x, y, color);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void drawStringNoShadow(FontRenderer font, String text, int x, int y, int color) {
        font.drawString(text, x, y, color);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void drawCenteredString(FontRenderer font, String text, int cx, int y, int color) {
        font.drawStringWithShadow(text, cx - font.getStringWidth(text) / 2, y, color);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Draws an 8x8 head (face + hat layer) from a 64x32 player skin texture. */
    public static void drawPlayerHead(ResourceLocation skin, int x, int y, int size) {
        GL11.glColor4f(1, 1, 1, 1);
        blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 32);
        blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 32);
    }
}
