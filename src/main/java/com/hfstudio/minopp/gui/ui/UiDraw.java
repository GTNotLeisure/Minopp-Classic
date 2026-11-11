package com.hfstudio.minopp.gui.ui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.gui.GuiDraw;
import com.hfstudio.minopp.render.ThemeManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class UiDraw {

    private UiDraw() {}

    public static final int TITLE_YELLOW = 0xFFFFD43A;
    public static final int OUTLINE_DARK = 0xFF3A0A08;
    public static final int PANEL = 0xC05A0C0C;
    public static final int PANEL_DARK = 0xD0380606;
    public static final int TILE_TOP = 0xFFF0864A;
    public static final int TILE_BOTTOM = 0xFFD9472C;
    public static final int GLOW = 0xFFFFC21E;

    public static void drawStringWithBackdrop(FontRenderer font, String text, int x, int y, int color) {
        int alpha = (int) (((color >>> 24) & 0xFF) * 0.4f);
        GuiDraw.fill(x - 2, y, x + font.getStringWidth(text) + 2, y + font.FONT_HEIGHT, alpha << 24);
        GuiDraw.drawString(font, text, x, y, color);
    }

    public static ResourceLocation ui(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? Mino.id("textures/gui/ui/" + name + ".png")
            : new ResourceLocation(name.substring(0, colon), "textures/gui/ui/" + name.substring(colon + 1) + ".png");
    }

    public static void gradient(float x1, float y1, float x2, float y2, int top, int bottom) {
        beginGradient();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        gradientQuad(t, x1, y1, x2, y2, top, bottom);
        t.draw();
        endGradient();
    }

    private static void beginGradient() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    private static void endGradient() {
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void gradientQuad(Tessellator t, float x1, float y1, float x2, float y2, int top, int bottom) {
        t.setColorRGBA_I(top & 0xFFFFFF, top >>> 24);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.setColorRGBA_I(bottom & 0xFFFFFF, bottom >>> 24);
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
    }

    // Cache common UI radii; larger corners are computed without retaining extra geometry.
    private static final int[][] CORNER_INSETS = new int[65][];

    private static int[] cornerInsets(int radius) {
        if (radius >= CORNER_INSETS.length) return null;
        if (CORNER_INSETS[radius] != null) return CORNER_INSETS[radius];
        int[] insets = new int[radius];
        for (int i = 0; i < radius; i++) insets[i] = cornerInset(radius, i);
        CORNER_INSETS[radius] = insets;
        return insets;
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        return (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
    }

    public static void roundRect(int x1, int y1, int x2, int y2, int r, int top, int bottom) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) {
            gradient(x1, y1, x2, y2, top, bottom);
            return;
        }
        int h = y2 - y1;
        int[] insets = cornerInsets(r);
        beginGradient();
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int i = 0; i < r; i++) {
            int inset = insets == null ? cornerInset(r, i) : insets[i];
            float t0 = i / (float) h, t1 = (i + 1f) / h;
            gradientQuad(
                tessellator,
                x1 + inset,
                y1 + i,
                x2 - inset,
                y1 + i + 1,
                lerpColor(top, bottom, t0),
                lerpColor(top, bottom, t1));
            float b0 = (h - i - 1f) / h, b1 = (h - i) / (float) h;
            gradientQuad(
                tessellator,
                x1 + inset,
                y2 - i - 1,
                x2 - inset,
                y2 - i,
                lerpColor(top, bottom, b0),
                lerpColor(top, bottom, b1));
        }
        gradientQuad(
            tessellator,
            x1,
            y1 + r,
            x2,
            y2 - r,
            lerpColor(top, bottom, r / (float) h),
            lerpColor(top, bottom, (h - r) / (float) h));
        tessellator.draw();
        endGradient();
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

    public static void centeredHint(FontRenderer font, String text, int centerX, int top, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        int width = Math.min(maxWidth, font.getStringWidth(text));
        int backgroundAlpha = (int) ((color >>> 24) * (144f / 255));
        roundRect(
            centerX - width / 2 - 4,
            top - 2,
            centerX + width / 2 + 3,
            top + font.FONT_HEIGHT + 2,
            3,
            backgroundAlpha << 24);
        fittedCentered(font, text, centerX, top, maxWidth, color);
    }

    private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
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

    /**
     * Draws any texture, such as an icon another mod registered.
     */
    public static void texture(ResourceLocation tex, float x, float y, float w, float h) {
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
    }

    public static void icon(String id, float x, float y, float size) {
        int colon = id.indexOf(':');
        String name = colon < 0 ? "icon_" + id : id.substring(0, colon + 1) + "icon_" + id.substring(colon + 1);
        image(name + (size <= 24 ? "_s" : ""), x, y, size, size);
    }

    private static final long SEED = 1425;

    private static final long START = System.currentTimeMillis();

    private static final int[] CARD_TINTS = { 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFE27A, 0xFFC2B0 };

    private static final int[][] CARD_LAYERS = { { 16, 5, 9, 14, 22 }, { 12, 12, 20, 18, 30 }, { 6, 28, 42, 12, 18 } };

    public static void background(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        long time = System.currentTimeMillis();
        double el = time - START;
        float cx = width * 0.5f, cy = height * 0.45f;
        float radius = (float) Math.hypot(width, height) * 0.62f;
        float mx = mc.displayWidth > 0 ? Math.max(-0.5f, Math.min(0.5f, Mouse.getX() / (float) mc.displayWidth - 0.5f))
            : 0;
        float my = mc.displayHeight > 0
            ? Math.max(-0.5f, Math.min(0.5f, 0.5f - Mouse.getY() / (float) mc.displayHeight))
            : 0;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        Tessellator t = Tessellator.instance;

        fill(t, 0, 0, width, height, 0xFF9A0E14, 0xFF4C0306);
        float breathe = 0.5f + 0.5f * (float) Math.sin(time / 2600.0 * Math.PI * 2);
        radial(t, cx, cy, radius, lerpColor(0xFFE8401C, 0xFFF4602A, breathe), 0x00A0141A, BACKGROUND_CIRCLE);
        vortex(t, cx - mx * 8, cy - my * 6, radius * 1.15f, (float) ((el * 0.0045) % 360));
        sweep(t, width, height, el);
        cards(t, width, height, el, mx, my);
        sparkles(t, width, height, el);
        vignette(t, cx, cy, width, height);
        fill(t, 0, 0, width, height * 0.16f, 0x40000000, 0x00000000);

        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void fill(Tessellator t, float x1, float y1, float x2, float y2, int top, int bottom) {
        t.startDrawingQuads();
        t.setColorRGBA_I(top & 0xFFFFFF, top >>> 24);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.setColorRGBA_I(bottom & 0xFFFFFF, bottom >>> 24);
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
        t.draw();
    }

    private static void vortex(Tessellator t, float cx, float cy, float reach, float rotDeg) {
        int arms = 3, steps = 56;
        double turns = Math.PI * 2.4, start = 12;
        double grow = Math.log(reach / start) / turns;
        double base0 = Math.toRadians(rotDeg);
        t.startDrawingQuads();
        for (int k = 0; k < arms; k++) {
            double base = base0 + k * Math.PI * 2 / arms;
            for (int s = 0; s < steps; s++) {
                double u0 = (double) s / steps, u1 = (double) (s + 1) / steps;
                double th0 = u0 * turns, th1 = u1 * turns;
                double r0 = start * Math.exp(grow * th0), r1 = start * Math.exp(grow * th1);
                double h0 = r0 * 0.24, h1 = r1 * 0.24;
                int a0 = (int) (24 * Math.sin(Math.PI * u0)), a1 = (int) (24 * Math.sin(Math.PI * u1));
                double c0 = Math.cos(base + th0), s0 = Math.sin(base + th0);
                double c1 = Math.cos(base + th1), s1 = Math.sin(base + th1);
                for (int side = -1; side <= 1; side += 2) {
                    t.setColorRGBA(255, 206, 176, a0);
                    t.addVertex(cx + c0 * r0, cy + s0 * r0, 0);
                    t.setColorRGBA(255, 206, 176, 0);
                    t.addVertex(cx + c0 * (r0 + side * h0), cy + s0 * (r0 + side * h0), 0);
                    t.addVertex(cx + c1 * (r1 + side * h1), cy + s1 * (r1 + side * h1), 0);
                    t.setColorRGBA(255, 206, 176, a1);
                    t.addVertex(cx + c1 * r1, cy + s1 * r1, 0);
                }
            }
        }
        t.draw();
    }

    private static void sweep(Tessellator t, int width, int height, double el) {
        double u = (el % 11000) / 11000 / 0.45;
        if (u > 1) return;
        float half = 70, slant = height * 0.55f;
        float x = (float) (-half + u * (width + slant + half * 2));
        int a = (int) (26 * Math.sin(Math.PI * u));
        t.startDrawingQuads();
        for (int side = -1; side <= 1; side += 2) {
            t.setColorRGBA(255, 236, 214, 0);
            t.addVertex(x + side * half - slant, height, 0);
            t.addVertex(x + side * half, 0, 0);
            t.setColorRGBA(255, 236, 214, a);
            t.addVertex(x, 0, 0);
            t.addVertex(x - slant, height, 0);
        }
        t.draw();
    }

    private static double wrap(double v, double m) {
        return ((v % m) + m) % m;
    }

    private static final List<BackgroundCard> BACKGROUND_CARDS = backgroundCards();

    private static List<BackgroundCard> backgroundCards() {
        Random random = new Random(SEED);
        List<BackgroundCard> cards = new ArrayList<>();
        for (int layer = 0; layer < CARD_LAYERS.length; layer++) {
            int[] spec = CARD_LAYERS[layer];
            for (int i = 0; i < spec[0]; i++) cards.add(new BackgroundCard(random, spec, 0.45f + layer * 0.55f));
        }
        return cards;
    }

    private static class BackgroundCard {

        private final float depth, size, alpha, bx, by, initialAngle;
        private final int tint;
        private final double vy, vx, spin, flipPhase, flipSpeed, swayPhase;
        private final float[] px = new float[16], py = new float[16];
        private final double[][] ring = new double[120][2];

        private BackgroundCard(Random rnd, int[] spec, float depth) {
            this.depth = depth;
            size = spec[1] + rnd.nextFloat() * (spec[2] - spec[1]);
            alpha = spec[3] + rnd.nextFloat() * (spec[4] - spec[3]);
            tint = CARD_TINTS[rnd.nextInt(CARD_TINTS.length)];
            vy = (0.004 + rnd.nextFloat() * 0.005) * depth;
            vx = (rnd.nextFloat() - 0.35) * 0.004 * depth;
            bx = rnd.nextFloat();
            by = rnd.nextFloat();
            spin = (rnd.nextBoolean() ? 1 : -1) * (0.002 + rnd.nextFloat() * 0.004);
            initialAngle = rnd.nextFloat() * 360;
            flipPhase = rnd.nextFloat() * Math.PI * 2;
            flipSpeed = 0.0004 + rnd.nextFloat() * 0.0007;
            swayPhase = rnd.nextFloat() * Math.PI * 2;
            float w = size * 0.66f, h = size;
            float hw = w / 2, hh = h / 2, rr = Math.min(w, h) * 0.16f;
            int corner = 4;
            for (int q = 0; q < 4; q++) {
                float ox = q == 0 || q == 3 ? hw - rr : -hw + rr, oy = q < 2 ? hh - rr : -hh + rr;
                for (int j = 0; j < corner; j++) {
                    double a = Math.PI / 2 * (q + (double) j / (corner - 1));
                    px[q * corner + j] = ox + (float) Math.cos(a) * rr;
                    py[q * corner + j] = oy + (float) Math.sin(a) * rr;
                }
            }
            double tilt = Math.toRadians(-32), tc = Math.cos(tilt), ts = Math.sin(tilt);
            float ax = w * 0.40f, ay = h * 0.26f, inner = 1 - Math.max(0.12f, 2.2f / Math.min(ax, ay));
            int steps = 20;
            int[] order = { 0, 1, 2, 0, 2, 3 };
            for (int i = 0; i < steps; i++) {
                double e0 = Math.PI * 2 * i / steps, e1 = Math.PI * 2 * (i + 1) / steps;
                double ox0 = Math.cos(e0) * ax, oy0 = Math.sin(e0) * ay, ox1 = Math.cos(e1) * ax,
                    oy1 = Math.sin(e1) * ay;
                double lx0 = ox0 * tc - oy0 * ts, ly0 = ox0 * ts + oy0 * tc, lx1 = ox1 * tc - oy1 * ts,
                    ly1 = ox1 * ts + oy1 * tc;
                double[][] quad = { { lx0, ly0 }, { lx1, ly1 }, { lx1 * inner, ly1 * inner },
                    { lx0 * inner, ly0 * inner } };
                for (int j = 0; j < order.length; j++) {
                    ring[i * 6 + j][0] = quad[order[j]][0];
                    ring[i * 6 + j][1] = quad[order[j]][1];
                }
            }
        }
    }

    private static void cards(Tessellator t, int width, int height, double el, float mx, float my) {
        t.startDrawing(GL11.GL_TRIANGLES);
        for (BackgroundCard card : BACKGROUND_CARDS) {
            float depth = card.depth, margin = card.size + 24;
            double bx = card.bx * (width + margin * 2), by = card.by * (height + margin * 2);
            double angle = Math.toRadians(card.initialAngle + el * card.spin);
            double flip = card.flipPhase + el * card.flipSpeed;
            float x = (float) (wrap(bx + el * card.vx, width + margin * 2) - margin
                + Math.sin(el / 1900.0 + card.swayPhase) * 6 * depth) - mx * 16 * depth;
            float y = (float) (height + margin - wrap(by + el * card.vy, height + margin * 2)) - my * 10 * depth;
            card(t, card, x, y, angle, (float) Math.cos(flip));
        }
        t.draw();
    }

    private static void card(Tessellator t, BackgroundCard card, float x, float y, double angle, float sx) {
        double c = Math.cos(angle), s = Math.sin(angle);
        int r = card.tint >> 16 & 255, g = card.tint >> 8 & 255, b = card.tint & 255;
        int fill = (int) (sx < 0 ? card.alpha * 0.7f : card.alpha);
        float[] px = card.px, py = card.py;
        int n = px.length;
        t.setColorRGBA(r, g, b, fill);
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            t.addVertex(x, y, 0);
            t.addVertex(x + c * px[i] * sx - s * py[i], y + s * px[i] * sx + c * py[i], 0);
            t.addVertex(x + c * px[k] * sx - s * py[k], y + s * px[k] * sx + c * py[k], 0);
        }
        if (sx <= 0.15f) return;
        t.setColorRGBA(r, g, b, Math.min(255, (int) (card.alpha * 1.8f)));
        for (double[] vertex : card.ring) {
            double lx = vertex[0] * sx, ly = vertex[1];
            t.addVertex(x + c * lx - s * ly, y + s * lx + c * ly, 0);
        }
    }

    private record Sparkle(double rise, float x, float y, double period, float arm) {}

    private static final Sparkle[] SPARKLES = sparkles();

    private static Sparkle[] sparkles() {
        Random rnd = new Random(SEED * 31);
        Sparkle[] out = new Sparkle[22];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Sparkle(
                0.006 + rnd.nextFloat() * 0.01,
                rnd.nextFloat(),
                rnd.nextFloat(),
                700.0 + rnd.nextFloat() * 900,
                1.4f + rnd.nextFloat() * 1.6f);
        }
        return out;
    }

    private static void sparkles(Tessellator t, int width, int height, double el) {
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int i = 0; i < 22; i++) {
            Sparkle sparkle = SPARKLES[i];
            float x = sparkle.x * width + (float) Math.sin(el / 1300.0 + i * 1.7) * 8;
            float y = height + 10 - (float) wrap(sparkle.y * (height + 20) + el * sparkle.rise, height + 20);
            float twinkle = (float) Math.pow(0.5 + 0.5 * Math.sin(el / sparkle.period + i * 2.3), 3);
            float arm = sparkle.arm + twinkle * 3f, thin = 0.7f;
            int alpha = (int) (40 + 150 * twinkle);
            t.setColorRGBA(255, 238, 206, alpha);
            for (int d = 0; d < 4; d++) {
                float dx = d == 0 ? arm : d == 2 ? -arm : 0, dy = d == 1 ? arm : d == 3 ? -arm : 0;
                float nx = dy != 0 ? thin : 0, ny = dx != 0 ? thin : 0;
                t.addVertex(x + dx, y + dy, 0);
                t.addVertex(x + nx, y + ny, 0);
                t.addVertex(x - nx, y - ny, 0);
            }
        }
        t.draw();
    }

    private record Circle(double[] cos, double[] sin) {

        private static Circle of(int segments) {
            double[] cos = new double[segments + 1], sin = new double[segments + 1];
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2 * i / segments;
                cos[i] = Math.cos(angle);
                sin[i] = Math.sin(angle);
            }
            return new Circle(cos, sin);
        }
    }

    private static final Circle BACKGROUND_CIRCLE = Circle.of(48);
    private static final Circle RING_CIRCLE = Circle.of(40);

    private static void vignette(Tessellator t, float cx, float cy, int width, int height) {
        float ix = width * 0.30f, iy = height * 0.30f, ox = width * 0.80f, oy = height * 0.88f;
        Circle circle = BACKGROUND_CIRCLE;
        int segments = circle.cos.length - 1;
        t.startDrawingQuads();
        for (int i = 0; i < segments; i++) {
            double c0 = circle.cos[i], s0 = circle.sin[i], c1 = circle.cos[i + 1], s1 = circle.sin[i + 1];
            t.setColorRGBA(20, 0, 2, 0);
            t.addVertex(cx + c0 * ix, cy + s0 * iy, 0);
            t.addVertex(cx + c1 * ix, cy + s1 * iy, 0);
            t.setColorRGBA(20, 0, 2, 150);
            t.addVertex(cx + c1 * ox, cy + s1 * oy, 0);
            t.addVertex(cx + c0 * ox, cy + s0 * oy, 0);
        }
        t.draw();
    }

    public static void ring(float cx, float cy, float r1, float r2, int argb) {
        if ((argb >>> 24) == 0) return;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        Circle circle = RING_CIRCLE;
        int segments = circle.cos.length - 1;
        for (int i = 0; i < segments; i++) {
            t.addVertex(cx + circle.cos[i] * r1, cy + circle.sin[i] * r1, 0);
            t.addVertex(cx + circle.cos[i + 1] * r1, cy + circle.sin[i + 1] * r1, 0);
            t.addVertex(cx + circle.cos[i + 1] * r2, cy + circle.sin[i + 1] * r2, 0);
            t.addVertex(cx + circle.cos[i] * r2, cy + circle.sin[i] * r2, 0);
        }
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void radial(Tessellator t, float cx, float cy, float r, int inner, int outer, Circle circle) {
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int i = 0; i < circle.cos.length - 1; i++) {
            t.setColorRGBA(inner >> 16 & 255, inner >> 8 & 255, inner & 255, inner >>> 24);
            t.addVertex(cx, cy, 0);
            t.setColorRGBA(outer >> 16 & 255, outer >> 8 & 255, outer & 255, outer >>> 24);
            t.addVertex(cx + circle.cos[i + 1] * r, cy + circle.sin[i + 1] * r, 0);
            t.addVertex(cx + circle.cos[i] * r, cy + circle.sin[i] * r, 0);
        }
        t.draw();
    }

    private record TooltipLayout(List<String> source, List<String> wrapped, List<Boolean> titles, int width,
        int maxWidth, FontRenderer font, long revision) {}

    private static TooltipLayout tooltipLayout;

    public static void tooltip(FontRenderer font, List<String> lines, int mouseX, int mouseY, int screenW,
        int screenH) {
        if (lines.isEmpty()) return;
        int maxW = Math.max(80, Math.min(240, screenW - 24));
        long revision = ThemeManager.INSTANCE.resourceRevision();
        TooltipLayout layout = tooltipLayout;
        if (layout == null || layout.font != font
            || layout.maxWidth != maxW
            || layout.revision != revision
            || !layout.source.equals(lines)) {
            List<String> wrapped = new ArrayList<>();
            List<Boolean> title = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                for (String text : font.listFormattedStringToWidth(lines.get(i), maxW)) {
                    wrapped.add(text);
                    title.add(i == 0);
                }
            }
            int width = 0;
            for (String text : wrapped) width = Math.max(width, font.getStringWidth(text));
            layout = new TooltipLayout(new ArrayList<>(lines), wrapped, title, width, maxW, font, revision);
            tooltipLayout = layout;
        }
        List<String> wrapped = layout.wrapped;
        List<Boolean> title = layout.titles;
        int w = layout.width;
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
