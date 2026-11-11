package com.hfstudio.minopp.render;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Renders an extruded downward arrow with shaded edges and beveled faces. */
@SideOnly(Side.CLIENT)
public class TurnArrowRenderer {

    private static final double HALF_DEPTH = 0.07;
    private static final double BEVEL = 0.018;
    private static final double[][] OUTLINE = { { -0.08, 0.58 }, { 0.08, 0.58 }, { 0.08, 0.24 }, { 0.24, 0.24 },
        { 0, 0 }, { -0.24, 0.24 }, { -0.08, 0.24 } };
    private static final double[][] FACE = { { -0.062, 0.562 }, { 0.062, 0.562 }, { 0.062, 0.222 }, { 0.196, 0.222 },
        { 0, 0.026 }, { -0.196, 0.222 }, { -0.062, 0.222 } };

    private TurnArrowRenderer() {}

    /** Draws in local coordinates, with the tip at the origin and the shaft extending upward. */
    public static void draw(int rgb) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        try {
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            for (int i = 0; i < OUTLINE.length; i++) {
                int next = (i + 1) % OUTLINE.length;
                double dx = OUTLINE[next][0] - OUTLINE[i][0];
                double dy = OUTLINE[next][1] - OUTLINE[i][1];
                float shade = (float) (0.58 + 0.17 * dx / Math.hypot(dx, dy));
                color(tess, rgb, shade);
                vertex(tess, OUTLINE[i], -HALF_DEPTH + BEVEL);
                vertex(tess, OUTLINE[next], -HALF_DEPTH + BEVEL);
                vertex(tess, OUTLINE[next], HALF_DEPTH - BEVEL);
                vertex(tess, OUTLINE[i], HALF_DEPTH - BEVEL);
                for (int side = -1; side <= 1; side += 2) {
                    color(tess, rgb, Math.min(1.15f, shade + 0.3f));
                    vertex(tess, OUTLINE[i], side * (HALF_DEPTH - BEVEL));
                    vertex(tess, OUTLINE[next], side * (HALF_DEPTH - BEVEL));
                    vertex(tess, FACE[next], side * HALF_DEPTH);
                    vertex(tess, FACE[i], side * HALF_DEPTH);
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                color(tess, rgb, side > 0 ? 1 : 0.88f);
                vertex(tess, FACE[0], side * HALF_DEPTH);
                vertex(tess, FACE[1], side * HALF_DEPTH);
                vertex(tess, FACE[2], side * HALF_DEPTH);
                vertex(tess, FACE[6], side * HALF_DEPTH);
                vertex(tess, FACE[3], side * HALF_DEPTH);
                vertex(tess, FACE[4], side * HALF_DEPTH);
                vertex(tess, FACE[4], side * HALF_DEPTH);
                vertex(tess, FACE[5], side * HALF_DEPTH);
            }
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void vertex(Tessellator tess, double[] point, double z) {
        tess.addVertex(point[0], point[1], z);
    }

    private static void color(Tessellator tess, int rgb, float shade) {
        tess.setColorOpaque_F(
            Math.min(1, (rgb >> 16 & 255) / 255f * shade),
            Math.min(1, (rgb >> 8 & 255) / 255f * shade),
            Math.min(1, (rgb & 255) / 255f * shade));
    }
}
