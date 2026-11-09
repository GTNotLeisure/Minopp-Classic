package com.hfstudio.minopp.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ColorPicker3D {

    private ColorPicker3D() {}

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer WIN = BufferUtils.createFloatBuffer(3);

    private static BlockPos tablePos;
    private static List<CardColor> colors;
    public static int hovered = -1;
    private static long openedAt;
    private static boolean ring;
    private static final float[] height = new float[4];
    private static long lastNanos = System.nanoTime();
    /** Screen-space (GUI scale) bounds of each pillar of the last frame: {minX, minY, maxX, maxY, centerX, centerY}. */
    private static final float[][] screenBounds = new float[4][];

    public static void open(BlockPos pos, List<CardColor> choices) {
        open(pos, choices, false);
    }

    /** {@code asRing}: a plain wild picks from a see-through ring cut in four; draw wilds pick from four blocks. */
    public static void open(BlockPos pos, List<CardColor> choices, boolean asRing) {
        ring = asRing;
        tablePos = pos;
        colors = choices;
        hovered = -1;
        openedAt = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) {
            height[i] = 0;
            screenBounds[i] = null;
        }
    }

    public static void close() {
        tablePos = null;
        colors = null;
        hovered = -1;
    }

    public static boolean isActiveFor(BlockPos pos) {
        return tablePos != null && tablePos.equals(pos) && colors != null;
    }

    public static int hitTest(int mouseX, int mouseY) {
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float[] b = screenBounds[i];
            if (b == null || mouseX < b[0] || mouseX > b[2] || mouseY < b[1] || mouseY > b[3]) continue;
            double d = Math.hypot(mouseX - b[4], mouseY - b[5]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    public static float[] center(int i) {
        float[] b = i >= 0 && i < 4 ? screenBounds[i] : null;
        return b == null ? null : new float[] { b[4], b[5] };
    }

    /** Screen bounds (GUI scale) of a box in the current model space, or null when it is off screen. */
    public static float[] projectBoxNow(double x0, double y0, double z0, double x1, double y1, double z1) {
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return project(x0, y0, z0, x1, y1, z1, MODELVIEW, PROJECTION, VIEWPORT, res.getScaleFactor(), mc.displayHeight);
    }

    public static boolean visible() {
        for (int i = 0; i < 4; i++) if (screenBounds[i] == null) return false;
        return true;
    }

    public static void render(BlockPos pos, double viewX, double viewZ) {
        if (!isActiveFor(pos)) return;
        draw(viewX - (pos.x + 1.5), viewZ - (pos.z + 1.5), colors, ring, hovered, openedAt, height, true);
    }

    private static final float[] REMOTE_HEIGHT = new float[4];
    private static long remoteNanos = System.nanoTime();

    /** Another player choosing: the same picker, laid out toward their seat, with no hit testing. */
    public static void renderRemote(double dirX, double dirZ, List<CardColor> choices, boolean asRing, int hover,
        long since) {
        draw(dirX, dirZ, choices, asRing, hover, since, REMOTE_HEIGHT, false);
    }

    private static void draw(double fx, double fz, List<CardColor> colors, boolean ring, int hovered, long openedAt,
        float[] height, boolean local) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - (local ? lastNanos : remoteNanos)) / 1e9f);
        if (local) lastNanos = now;
        else remoteNanos = now;
        float ease = 1 - (float) Math.exp(-dt * 12);
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 250f);

        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-3) {
            fx = 0;
            fz = 1;
        } else {
            fx /= len;
            fz /= len;
        }
        double rx = fz, rz = -fx;
        float spread = 0.44f, half = 0.17f;
        double[][] offsets = { { fx * spread, fz * spread }, { rx * spread, rz * spread },
            { -fx * spread, -fz * spread }, { -rx * spread, -rz * spread } };

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        FloatBuffer modelview = MODELVIEW, projection = PROJECTION;
        IntBuffer viewport = VIEWPORT;
        modelview.clear();
        projection.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = res.getScaleFactor();

        int count = Math.min(4, colors.size());
        for (int i = 0; i < count; i++) {
            boolean hot = i == hovered;
            float target = (hot ? 0.55f : 0.24f) * appear;
            height[i] += (target - height[i]) * ease;
            float h = Math.max(0.01f, height[i]);
            double cx = 1.5 + offsets[i][0], cz = 1.5 + offsets[i][1];
            float y0 = 0.945f, y1 = y0 + h;
            int rgb = colors.get(i).rgb;
            if (ring) {
                double mid = Math.atan2(offsets[i][1], offsets[i][0]);
                double a0 = mid - Math.toRadians(45), a1 = mid + Math.toRadians(45);
                double ox = 1.5 + Math.cos(mid) * 0.05, oz = 1.5 + Math.sin(mid) * 0.05;
                drawQuarter(ox, oz, a0, a1, 0, 0.56 * (hot ? 1.16 : 1.04), 0.5, y0, y1, rgb, hot ? 0.9f : 0.7f, hot);
                arrow(ox, oz, mid, 0.18, 0.36, 0.085, y1 + 0.004, hot ? 0.95f : 0.8f);
                double[][] pts = new double[14][];
                for (int k = 0; k < 6; k++) {
                    double a = a0 + (a1 - a0) * k / 5;
                    pts[k] = new double[] { 1.5 + Math.cos(a) * 0.56, y0, 1.5 + Math.sin(a) * 0.56 };
                    pts[k + 6] = new double[] { 1.5 + Math.cos(a) * 0.56, y1, 1.5 + Math.sin(a) * 0.56 };
                }
                pts[12] = new double[] { 1.5, y0, 1.5 };
                pts[13] = new double[] { 1.5, y1, 1.5 };
                if (local)
                    screenBounds[i] = projectPoints(pts, modelview, projection, viewport, scale, mc.displayHeight);
                continue;
            }
            float alpha = hot ? 0.95f : 0.78f;
            drawFrustum(cx, cz, 0.15, hot ? 0.21 : 0.18, y0, y1, rgb, alpha, hot);
            arrow(cx, cz, Math.atan2(offsets[i][1], offsets[i][0]), -0.07, 0.09, 0.07, y1 + 0.004, alpha);
            if (local) screenBounds[i] = project(
                cx - half,
                y0,
                cz - half,
                cx + half,
                y1,
                cz + half,
                modelview,
                projection,
                viewport,
                scale,
                mc.displayHeight);
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private static void drawBox(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha,
        boolean hot) {
        Tessellator t = Tessellator.instance;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        if (hot) {
            r = Math.min(1, r * 1.15f + 0.08f);
            g = Math.min(1, g * 1.15f + 0.08f);
            b = Math.min(1, b * 1.15f + 0.08f);
        }
        t.startDrawingQuads();
        t.setColorRGBA_F(
            Math.min(1, r * 1.2f + 0.1f),
            Math.min(1, g * 1.2f + 0.1f),
            Math.min(1, b * 1.2f + 0.1f),
            alpha);
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        float[] shade = { 0.85f, 0.7f, 0.85f, 0.7f };
        double[][] sides = { { x0, z0, x1, z0 }, { x1, z0, x1, z1 }, { x1, z1, x0, z1 }, { x0, z1, x0, z0 } };
        for (int s = 0; s < 4; s++) {
            double[] e = sides[s];
            t.setColorRGBA_F(r * shade[s], g * shade[s], b * shade[s], alpha);
            t.addVertex(e[0], y1, e[1]);
            t.addVertex(e[2], y1, e[3]);
            t.setColorRGBA_F(r * shade[s] * 0.8f, g * shade[s] * 0.8f, b * shade[s] * 0.8f, alpha * 0.45f);
            t.addVertex(e[2], y0, e[3]);
            t.addVertex(e[0], y0, e[1]);
        }
        t.draw();
        if (hot) {
            GL11.glLineWidth(2f);
            t.startDrawing(GL11.GL_LINE_LOOP);
            t.setColorRGBA_F(1, 1, 1, 0.9f);
            double e = 0.004;
            t.addVertex(x0 - e, y1 + e, z0 - e);
            t.addVertex(x0 - e, y1 + e, z1 + e);
            t.addVertex(x1 + e, y1 + e, z1 + e);
            t.addVertex(x1 + e, y1 + e, z0 - e);
            t.draw();
            GL11.glLineWidth(1f);
        }
    }

    // one quarter of the ring, with a gap to its neighbours; its top flares out wider than its foot, more so when it
    // is the one pointed at
    private static void drawQuarter(double ox, double oz, double a0, double a1, double r0, double r1Top,
        double r1Bottom, double y0, double y1, int rgb, float alpha, boolean hot) {
        Tessellator t = Tessellator.instance;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        if (hot) {
            r = Math.min(1, r * 1.15f + 0.08f);
            g = Math.min(1, g * 1.15f + 0.08f);
            b = Math.min(1, b * 1.15f + 0.08f);
        }
        int steps = 12;
        t.startDrawingQuads();
        for (int i = 0; i < steps; i++) {
            double u0 = a0 + (a1 - a0) * i / steps, u1 = a0 + (a1 - a0) * (i + 1) / steps;
            double c0 = Math.cos(u0), s0 = Math.sin(u0), c1 = Math.cos(u1), s1 = Math.sin(u1);
            t.setColorRGBA_F(
                Math.min(1, r * 1.2f + 0.1f),
                Math.min(1, g * 1.2f + 0.1f),
                Math.min(1, b * 1.2f + 0.1f),
                alpha);
            t.addVertex(ox + c0 * r0, y1, oz + s0 * r0);
            t.addVertex(ox + c0 * r1Top, y1, oz + s0 * r1Top);
            t.addVertex(ox + c1 * r1Top, y1, oz + s1 * r1Top);
            t.addVertex(ox + c1 * r0, y1, oz + s1 * r0);
            t.setColorRGBA_F(r * 0.85f, g * 0.85f, b * 0.85f, alpha);
            t.addVertex(ox + c0 * r1Top, y1, oz + s0 * r1Top);
            t.addVertex(ox + c1 * r1Top, y1, oz + s1 * r1Top);
            t.setColorRGBA_F(r * 0.65f, g * 0.65f, b * 0.65f, alpha * 0.45f);
            t.addVertex(ox + c1 * r1Bottom, y0, oz + s1 * r1Bottom);
            t.addVertex(ox + c0 * r1Bottom, y0, oz + s0 * r1Bottom);
        }
        for (double a : new double[] { a0, a1 }) {
            double c = Math.cos(a), s = Math.sin(a);
            t.setColorRGBA_F(r * 0.7f, g * 0.7f, b * 0.7f, alpha);
            t.addVertex(ox + c * r0, y1, oz + s * r0);
            t.addVertex(ox + c * r1Top, y1, oz + s * r1Top);
            t.setColorRGBA_F(r * 0.55f, g * 0.55f, b * 0.55f, alpha * 0.45f);
            t.addVertex(ox + c * r1Bottom, y0, oz + s * r1Bottom);
            t.addVertex(ox + c * r0, y0, oz + s * r0);
        }
        t.draw();
        if (hot) {
            GL11.glLineWidth(2f);
            t.startDrawing(GL11.GL_LINE_STRIP);
            t.setColorRGBA_F(1, 1, 1, 0.9f);
            t.addVertex(ox + Math.cos(a0) * r0, y1 + 0.004, oz + Math.sin(a0) * r0);
            for (int i = 0; i <= steps; i++) {
                double a = a0 + (a1 - a0) * i / steps;
                t.addVertex(ox + Math.cos(a) * r1Top, y1 + 0.004, oz + Math.sin(a) * r1Top);
            }
            t.addVertex(ox + Math.cos(a1) * r0, y1 + 0.004, oz + Math.sin(a1) * r0);
            t.draw();
            GL11.glLineWidth(1f);
        }
    }

    // a block whose top is a little wider than its foot, so its sides lean out
    private static void drawFrustum(double cx, double cz, double hwBottom, double hwTop, double y0, double y1, int rgb,
        float alpha, boolean hot) {
        Tessellator t = Tessellator.instance;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        if (hot) {
            r = Math.min(1, r * 1.15f + 0.08f);
            g = Math.min(1, g * 1.15f + 0.08f);
            b = Math.min(1, b * 1.15f + 0.08f);
        }
        t.startDrawingQuads();
        t.setColorRGBA_F(
            Math.min(1, r * 1.2f + 0.1f),
            Math.min(1, g * 1.2f + 0.1f),
            Math.min(1, b * 1.2f + 0.1f),
            alpha);
        t.addVertex(cx - hwTop, y1, cz - hwTop);
        t.addVertex(cx - hwTop, y1, cz + hwTop);
        t.addVertex(cx + hwTop, y1, cz + hwTop);
        t.addVertex(cx + hwTop, y1, cz - hwTop);
        double[][] top = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        float[] shade = { 0.85f, 0.7f, 0.85f, 0.7f };
        for (int k = 0; k < 4; k++) {
            double[] p = top[k], q = top[(k + 1) % 4];
            t.setColorRGBA_F(r * shade[k], g * shade[k], b * shade[k], alpha);
            t.addVertex(cx + p[0] * hwTop, y1, cz + p[1] * hwTop);
            t.addVertex(cx + q[0] * hwTop, y1, cz + q[1] * hwTop);
            t.setColorRGBA_F(r * shade[k] * 0.8f, g * shade[k] * 0.8f, b * shade[k] * 0.8f, alpha * 0.45f);
            t.addVertex(cx + q[0] * hwBottom, y0, cz + q[1] * hwBottom);
            t.addVertex(cx + p[0] * hwBottom, y0, cz + p[1] * hwBottom);
        }
        t.draw();
        if (hot) {
            GL11.glLineWidth(2f);
            t.startDrawing(GL11.GL_LINE_LOOP);
            t.setColorRGBA_F(1, 1, 1, 0.9f);
            for (double[] p : top) t.addVertex(cx + p[0] * (hwTop + 0.004), y1 + 0.004, cz + p[1] * (hwTop + 0.004));
            t.draw();
            GL11.glLineWidth(1f);
        }
    }

    // the orange arrow on top of each piece, pointing away from the pile
    private static void arrow(double cx, double cz, double angle, double from, double to, double halfBase, double y,
        float alpha) {
        double nx = Math.cos(angle), nz = Math.sin(angle), px = -nz, pz = nx;
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        t.setColorRGBA_F(1f, 0.55f, 0.12f, alpha);
        t.addVertex(cx + nx * to, y, cz + nz * to);
        t.addVertex(cx + nx * from + px * halfBase, y, cz + nz * from + pz * halfBase);
        t.addVertex(cx + nx * from - px * halfBase, y, cz + nz * from - pz * halfBase);
        t.draw();
    }

    private static float[] projectPoints(double[][] pts, FloatBuffer modelview, FloatBuffer projection,
        IntBuffer viewport, int scale, int displayHeight) {
        FloatBuffer win = WIN;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float sx = 0, sy = 0;
        for (double[] p : pts) {
            win.clear();
            if (!GLU.gluProject((float) p[0], (float) p[1], (float) p[2], modelview, projection, viewport, win))
                return null;
            float wz = win.get(2);
            if (wz < 0 || wz > 1) return null;
            float gx = win.get(0) / scale, gy = (displayHeight - win.get(1)) / scale;
            minX = Math.min(minX, gx);
            maxX = Math.max(maxX, gx);
            minY = Math.min(minY, gy);
            maxY = Math.max(maxY, gy);
            sx += gx;
            sy += gy;
        }
        return new float[] { minX, minY, maxX, maxY, sx / pts.length, sy / pts.length };
    }

    private static float[] project(double x0, double y0, double z0, double x1, double y1, double z1,
        FloatBuffer modelview, FloatBuffer projection, IntBuffer viewport, int scale, int displayHeight) {
        FloatBuffer win = WIN;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            double x = (c & 1) == 0 ? x0 : x1, y = (c & 2) == 0 ? y0 : y1, z = (c & 4) == 0 ? z0 : z1;
            win.clear();
            if (!GLU.gluProject((float) x, (float) y, (float) z, modelview, projection, viewport, win)) return null;
            float wz = win.get(2);
            if (wz < 0 || wz > 1) return null;
            float gx = win.get(0) / scale, gy = (displayHeight - win.get(1)) / scale;
            minX = Math.min(minX, gx);
            maxX = Math.max(maxX, gx);
            minY = Math.min(minY, gy);
            maxY = Math.max(maxY, gy);
        }
        return new float[] { minX, minY, maxX, maxY, (minX + maxX) / 2, (minY + maxY) / 2 };
    }
}
