package com.hfstudio.minopp.gui.ui;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.gui.GuiDraw;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class UiDraw {

    private UiDraw() {}

    public static final int TITLE_YELLOW = 0xFFFFD43A;
    public static final int OUTLINE_DARK = 0xFF3A0A08;
    public static final int PANEL = 0xC05A0C0C;
    public static final int PANEL_DARK = 0xD0380606;
    public static final int TILE_TOP = 0xFFF0864A;
    public static final int TILE_BOTTOM = 0xFFD9472C;
    public static final int GLOW = 0xFFFFC21E;

    public static ResourceLocation ui(String name) {
        return Mino.id("textures/gui/ui/" + name + ".png");
    }

    public static void gradient(float x1, float y1, float x2, float y2, int top, int bottom) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(top & 0xFFFFFF, top >>> 24);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.setColorRGBA_I(bottom & 0xFFFFFF, bottom >>> 24);
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void roundRect(int x1, int y1, int x2, int y2, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            gradient(x1, y1, x2, y2, top, bottom);
            return;
        }
        int h = y2 - y1;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            float t0 = i / (float) h, t1 = (i + 1f) / h;
            gradient(
                x1 + inset,
                y1 + i,
                x2 - inset,
                y1 + i + 1,
                lerpColor(top, bottom, t0),
                lerpColor(top, bottom, t1));
            float b0 = (h - i - 1f) / h, b1 = (h - i) / (float) h;
            gradient(
                x1 + inset,
                y2 - i - 1,
                x2 - inset,
                y2 - i,
                lerpColor(top, bottom, b0),
                lerpColor(top, bottom, b1));
        }
        gradient(
            x1,
            y1 + r,
            x2,
            y2 - r,
            lerpColor(top, bottom, r / (float) h),
            lerpColor(top, bottom, (h - r) / (float) h));
    }

    public static void roundRect(int x1, int y1, int x2, int y2, int r, int color) {
        roundRect(x1, y1, x2, y2, r, color, color);
    }

    public static void roundFrame(int x1, int y1, int x2, int y2, int r, int border, int borderColor, int top,
        int bottom) {
        roundRect(x1, y1, x2, y2, r, borderColor);
        roundRect(x1 + border, y1 + border, x2 - border, y2 - border, Math.max(0, r - border), top, bottom);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16
            | (int) (ag + (bg - ag) * t) << 8
            | (int) (ab + (bb - ab) * t);
    }

    public static void panel(int x1, int y1, int x2, int y2) {
        roundRect(x1 + 2, y1 + 3, x2 + 2, y2 + 3, 6, 0x40000000);
        roundRect(x1, y1, x2, y2, 6, PANEL, PANEL_DARK);
    }

    public static void panelHeader(FontRenderer font, int x1, int y1, int x2, String caption) {
        roundRect(x1, y1, x2, y1 + 16, 6, 0xE0200404, 0xE0300606);
        font.drawStringWithShadow(caption, x1 + 8, y1 + 4, TITLE_YELLOW);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void outlinedText(FontRenderer font, String text, float x, float y, float scale, int color,
        int outline) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        String plain = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(text);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) font.drawString(plain, dx, dy, outline);
            }
        }
        font.drawString(plain, 1, 2, outline);
        font.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void scaledCentered(FontRenderer font, String text, float cx, float y, float scale, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, y, 0);
        GL11.glScalef(scale, scale, 1);
        // getStringWidth includes the spacing pixel after the last glyph
        GL11.glTranslatef(-(font.getStringWidth(text) - 1) / 2f, 0, 0);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void fittedCentered(FontRenderer font, String text, float cx, float y, int maxWidth, int color) {
        int w = font.getStringWidth(text);
        float scale = w <= maxWidth ? 1f : Math.max(0.6f, maxWidth / (float) w);
        if (w * scale > maxWidth) text = font.trimStringToWidth(text, (int) (maxWidth / scale) - 4) + "..";
        scaledCentered(font, text, cx, y, scale, color);
    }

    public static void count(FontRenderer font, String text, int right, int bottom) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        font.drawStringWithShadow(text, right - font.getStringWidth(text), bottom - 8, 0xFFFFFFFF);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void image(String name, float x, float y, float w, float h) {
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(ui(name));
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
    }

    public static void icon(String id, float x, float y, float size) {
        image("icon_" + id + (size <= 24 ? "_s" : ""), x, y, size, size);
    }

    private static final long SEED = 1425;

    public static void background(int width, int height) {
        gradient(0, 0, width, height, 0xFFC4161C, 0xFF7A0A0E);
        long time = System.currentTimeMillis();
        Random rnd = new Random(SEED);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        Tessellator t = Tessellator.instance;
        for (int i = 0; i < 22; i++) {
            float speed = 0.004f + rnd.nextFloat() * 0.01f;
            float x = (rnd.nextFloat() * (width + 80) + time * speed) % (width + 80) - 40;
            float y = rnd.nextFloat() * height;
            float size = 10 + rnd.nextFloat() * 22;
            float angle = rnd.nextFloat() * 360 + time * 0.005f * (rnd.nextBoolean() ? 1 : -1);
            int alpha = 18 + rnd.nextInt(28);
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glRotatef(angle, 0, 0, 1);
            t.startDrawingQuads();
            t.setColorRGBA(255, 220, 220, alpha);
            t.addVertex(-size * 0.32, size / 2, 0);
            t.addVertex(size * 0.32, size / 2, 0);
            t.addVertex(size * 0.32, -size / 2, 0);
            t.addVertex(-size * 0.32, -size / 2, 0);
            t.draw();
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        gradient(0, height * 0.55f, width, height, 0x00000000, 0x40000000);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void tooltip(FontRenderer font, List<String> lines, int mouseX, int mouseY, int screenW,
        int screenH) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (String l : lines) w = Math.max(w, font.getStringWidth(l));
        int h = lines.size() * 10 + 4;
        int x = mouseX + 12, y = mouseY - 6;
        if (x + w + 10 > screenW) x = mouseX - w - 16;
        if (y + h + 6 > screenH) y = screenH - h - 6;
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        roundFrame(x - 4, y - 4, x + w + 4, y + h, 4, 1, GLOW, 0xF0300808, 0xF0180404);
        for (int i = 0; i < lines.size(); i++) {
            font.drawStringWithShadow(lines.get(i), x, y + i * 10, i == 0 ? TITLE_YELLOW : 0xFFE8E8E8);
        }
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void scissor(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int f = res.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * f, mc.displayHeight - (y + h) * f, Math.max(0, w * f), Math.max(0, h * f));
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static int pill(FontRenderer font, int x, int y, String key, String label, boolean hover) {
        int kw = font.getStringWidth(key) + 8;
        int w = kw + font.getStringWidth(label) + 14;
        roundFrame(x, y, x + w, y + 18, 8, 1, hover ? GLOW : 0xFF5A5A5A, hover ? 0xFF4A4A4A : 0xFF2E2E2E, 0xFF1A1A1A);
        roundFrame(x + 4, y + 3, x + 4 + kw, y + 15, 2, 1, 0xFF8A8A8A, 0xFF3A3A3A, 0xFF2A2A2A);
        font.drawString(key, x + 8, y + 5, 0xFFE0E0E0);
        font.drawStringWithShadow(label, x + kw + 9, y + 5, 0xFFFFFFFF);
        GL11.glColor4f(1, 1, 1, 1);
        return w;
    }

    public static int pillWidth(FontRenderer font, String key, String label) {
        return font.getStringWidth(key) + 8 + font.getStringWidth(label) + 14;
    }
}
