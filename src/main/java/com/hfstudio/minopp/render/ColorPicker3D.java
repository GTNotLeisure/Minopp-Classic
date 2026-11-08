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
    private static final float[] height = new float[4];
    private static long lastNanos = System.nanoTime();
    /** Screen-space (GUI scale) bounds of each pillar of the last frame: {minX, minY, maxX, maxY, centerX, centerY}. */
    private static final float[][] screenBounds = new float[4][];

    public static void open(BlockPos pos, List<CardColor> choices) {
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

    public static boolean visible() {
        for (int i = 0; i < 4; i++) if (screenBounds[i] == null) return false;
        return true;
    }

    public static void render(BlockPos pos, double viewX, double viewZ) {
        if (!isActiveFor(pos)) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float ease = 1 - (float) Math.exp(-dt * 12);
        float appear = Math.min(1f, (System.currentTimeMillis() - openedAt) / 250f);

        double fx = viewX - (pos.x + 1.5), fz = viewZ - (pos.z + 1.5);
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
            float alpha = hot ? 0.95f : 0.78f;
            drawBox(cx - half, y0, cz - half, cx + half, y1, cz + half, rgb, alpha, hot);
            screenBounds[i] = project(
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
