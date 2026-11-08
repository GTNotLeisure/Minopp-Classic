package com.hfstudio.minopp.gui.ui;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
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
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
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
        if (w * scale <= maxWidth) {
            scaledCentered(font, text, cx, y, scale, color);
        } else {
            marquee(font, text, cx - maxWidth / 2f, y, maxWidth, scale, color);
        }
    }

    private static final java.nio.IntBuffer SCISSOR_BOX = org.lwjgl.BufferUtils.createIntBuffer(16);
    private static final java.nio.FloatBuffer MODELVIEW = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final long MARQUEE_PAUSE = 1200;
    private static final float MARQUEE_SPEED = 0.022f;

    public static void marquee(FontRenderer font, String text, float x, float y, float maxWidth, float scale,
        int color) {
        float w = font.getStringWidth(text) * scale;
        if (w <= maxWidth) {
            drawScaled(font, text, x, y, scale, color);
            return;
        }
        float overflow = w - maxWidth;
        long travel = (long) (overflow / MARQUEE_SPEED);
        long cycle = 2 * travel + 2 * MARQUEE_PAUSE;
        long t = System.currentTimeMillis() % cycle;
        float offset;
        if (t < MARQUEE_PAUSE) offset = 0;
        else if (t < MARQUEE_PAUSE + travel) offset = overflow * (t - MARQUEE_PAUSE) / travel;
        else if (t < 2 * MARQUEE_PAUSE + travel) offset = overflow;
        else offset = overflow * (1 - (t - 2 * MARQUEE_PAUSE - travel) / (float) travel);
        boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        SCISSOR_BOX.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
        int px = SCISSOR_BOX.get(0), py = SCISSOR_BOX.get(1), pw = SCISSOR_BOX.get(2), ph = SCISSOR_BOX.get(3);
        clipTo(x, y - 1, maxWidth, 9 * scale + 2, hadScissor, px, py, pw, ph);
        drawScaled(font, text, x - offset, y, scale, color);
        if (hadScissor) GL11.glScissor(px, py, pw, ph);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void drawScaled(FontRenderer font, String text, float x, float y, float scale, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static void clipTo(float x, float y, float w, float h, boolean intersect, int px, int py, int pw, int ph) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int f = res.getScaleFactor();
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        float sx = MODELVIEW.get(0), sy = MODELVIEW.get(5), tx = MODELVIEW.get(12), ty = MODELVIEW.get(13);
        int x1 = (int) Math.floor((x * sx + tx) * f), x2 = (int) Math.ceil(((x + w) * sx + tx) * f);
        int yTop = (int) Math.floor((y * sy + ty) * f), yBottom = (int) Math.ceil(((y + h) * sy + ty) * f);
        int bx = x1, by = mc.displayHeight - yBottom, bw = x2 - x1, bh = yBottom - yTop;
        if (intersect) {
            int ix1 = Math.max(bx, px), iy1 = Math.max(by, py);
            int ix2 = Math.min(bx + bw, px + pw), iy2 = Math.min(by + bh, py + ph);
            bx = ix1;
            by = iy1;
            bw = Math.max(0, ix2 - ix1);
            bh = Math.max(0, iy2 - iy1);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bx, by, bw, bh);
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

    private static final long START = System.currentTimeMillis();

    public static void background(int width, int height) {
        long time = System.currentTimeMillis();
        double el = time - START;
        float cx = width * 0.5f, cy = height * 0.45f;
        float radius = (float) Math.hypot(width, height) * 0.62f;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        Tessellator t = Tessellator.instance;

        gradient(0, 0, width, height, 0xFF8E0A10, 0xFF5A0408);
        float breathe = 0.5f + 0.5f * (float) Math.sin(time / 2600.0 * Math.PI * 2);
        radial(t, cx, cy, radius, lerpColor(0xFFE8401C, 0xFFF25A22, breathe), 0x00A0141A, 48);

        float spin = (time % 120000L) / 120000f * 360f;
        int rays = 12;
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int i = 0; i < rays; i++) {
            double a0 = Math.toRadians(spin + i * 360.0 / rays), a1 = a0 + Math.toRadians(360.0 / rays * 0.4);
            t.setColorRGBA(255, 214, 160, 18);
            t.addVertex(cx, cy, 0);
            t.setColorRGBA(255, 170, 120, 0);
            t.addVertex(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, 0);
            t.addVertex(cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, 0);
        }
        t.draw();

        float swirlX = width * 0.58f, swirlY = height * 0.55f;
        for (int k = 0; k < 5; k++) {
            float r = Math.max(width, height) * (0.22f + k * 0.13f);
            double speed = (k % 2 == 0 ? 1 : -1) * (0.0024 - k * 0.0003);
            float start = (float) ((el * speed + k * 70) % 360);
            arc(t, swirlX, swirlY, r, r * 0.78f, start, start + 200f, 6 + k * 2.5f, 255, 225, 225, 26 - k * 3);
        }

        Random rnd = new Random(SEED);
        for (int i = 0; i < 34; i++) {
            float size = 4 + (float) Math.pow(rnd.nextFloat(), 2) * 30;
            double vx = 0.004 + rnd.nextFloat() * 0.006, vy = 0.006 + rnd.nextFloat() * 0.01;
            float x = (float) ((rnd.nextFloat() * (width + 80) + el * vx) % (width + 80)) - 40;
            float y = height + 40 - (float) ((rnd.nextFloat() * (height + 80) + el * vy) % (height + 80));
            float angle = (float) ((rnd.nextFloat() * 360 + el * 0.004 * (rnd.nextBoolean() ? 1 : -1)) % 360);
            int alpha = 10 + rnd.nextInt(size > 18 ? 18 : 34);
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            GL11.glRotatef(angle, 0, 0, 1);
            t.startDrawingQuads();
            t.setColorRGBA(255, 226, 222, alpha);
            t.addVertex(-size * 0.36, size / 2, 0);
            t.addVertex(size * 0.36, size / 2, 0);
            t.addVertex(size * 0.36, -size / 2, 0);
            t.addVertex(-size * 0.36, -size / 2, 0);
            t.draw();
            GL11.glPopMatrix();
        }

        t.startDrawingQuads();
        for (int i = 0; i < 16; i++) {
            double rise = 0.008 + rnd.nextFloat() * 0.01;
            float x = rnd.nextFloat() * width + (float) Math.sin(el / 1300.0 + i * 1.7) * 8;
            float y = height + 10 - (float) ((rnd.nextFloat() * (height + 20) + el * rise) % (height + 20));
            float s = 1f + rnd.nextFloat() * 1.4f;
            int alpha = (int) (60 + 50 * (0.5 + 0.5 * Math.sin(el / 1500.0 + i)));
            t.setColorRGBA(255, 236, 200, alpha);
            t.addVertex(x, y - s, 0);
            t.addVertex(x - s, y, 0);
            t.addVertex(x, y + s, 0);
            t.addVertex(x + s, y, 0);
        }
        t.draw();

        gradient(0, 0, width, height * 0.18f, 0x50000000, 0x00000000);
        gradient(0, height * 0.6f, width, height, 0x00000000, 0x58000000);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void radial(Tessellator t, float cx, float cy, float r, int inner, int outer, int segments) {
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            t.setColorRGBA(inner >> 16 & 255, inner >> 8 & 255, inner & 255, inner >>> 24);
            t.addVertex(cx, cy, 0);
            t.setColorRGBA(outer >> 16 & 255, outer >> 8 & 255, outer & 255, outer >>> 24);
            t.addVertex(cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, 0);
            t.addVertex(cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, 0);
        }
        t.draw();
    }

    private static void arc(Tessellator t, float cx, float cy, float rx, float ry, float fromDeg, float toDeg,
        float thickness, int r, int g, int b, int alpha) {
        int steps = 36;
        t.startDrawingQuads();
        for (int i = 0; i < steps; i++) {
            double a0 = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            double a1 = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 1) / steps);
            float fade0 = (float) i / steps, fade1 = (float) (i + 1) / steps;
            t.setColorRGBA(r, g, b, (int) (alpha * fade0));
            t.addVertex(cx + Math.cos(a0) * rx, cy + Math.sin(a0) * ry, 0);
            t.addVertex(cx + Math.cos(a0) * (rx + thickness), cy + Math.sin(a0) * (ry + thickness), 0);
            t.setColorRGBA(r, g, b, (int) (alpha * fade1));
            t.addVertex(cx + Math.cos(a1) * (rx + thickness), cy + Math.sin(a1) * (ry + thickness), 0);
            t.addVertex(cx + Math.cos(a1) * rx, cy + Math.sin(a1) * ry, 0);
        }
        t.draw();
    }

    public static void tooltip(FontRenderer font, List<String> lines, int mouseX, int mouseY, int screenW,
        int screenH) {
        if (lines.isEmpty()) return;
        int maxW = Math.max(80, Math.min(240, screenW - 24));
        List<String> wrapped = new java.util.ArrayList<>();
        List<Boolean> title = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (Object o : font.listFormattedStringToWidth(lines.get(i), maxW)) {
                wrapped.add((String) o);
                title.add(i == 0);
            }
        }
        int w = 0;
        for (String l : wrapped) w = Math.max(w, font.getStringWidth(l));
        int h = wrapped.size() * 10 - 2;
        int x = mouseX + 12, y = mouseY - 12;
        if (x + w + 4 > screenW) x = mouseX - 16 - w;
        x = Math.max(4, Math.min(x, screenW - w - 4));
        y = Math.max(4, Math.min(y, screenH - h - 6));
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 400);
        roundFrame(x - 4, y - 4, x + w + 4, y + h + 4, 4, 1, GLOW, 0xF0300808, 0xF0180404);
        for (int i = 0; i < wrapped.size(); i++) {
            font.drawStringWithShadow(wrapped.get(i), x, y + i * 10, title.get(i) ? TITLE_YELLOW : 0xFFE8E8E8);
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
