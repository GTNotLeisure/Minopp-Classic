package com.hfstudio.minopp.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.hfstudio.minopp.render.SpriteContour.Edge;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public record SpriteContour(List<Edge> edges) {

    public static final SpriteContour EMPTY = new SpriteContour(List.of());
    private static final int ALPHA_THRESHOLD = 25;

    public SpriteContour {
        edges = List.copyOf(edges);
    }

    /**
     * A directed boundary in normalized sprite coordinates, with UVs inside the adjacent opaque pixels.
     */
    public record Edge(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int nx,
        int ny) {}

    /**
     * Merges adjacent boundary pixels into spans without filling transparent corners or holes.
     */
    public static SpriteContour fromImage(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        List<Edge> edges = new ArrayList<>();
        horizontal(pixels, width, height, -1, edges);
        horizontal(pixels, width, height, 1, edges);
        vertical(pixels, width, height, -1, edges);
        vertical(pixels, width, height, 1, edges);
        return new SpriteContour(edges);
    }

    private static boolean opaque(int[] pixels, int width, int height, int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && (pixels[y * width + x] >>> 24) > ALPHA_THRESHOLD;
    }

    private static boolean boundary(int[] pixels, int width, int height, int x, int y, int dx, int dy) {
        return opaque(pixels, width, height, x, y) && !opaque(pixels, width, height, x + dx, y + dy);
    }

    private static void horizontal(int[] pixels, int width, int height, int dy, List<Edge> edges) {
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!boundary(pixels, width, height, x, y, 0, dy)) {
                    x++;
                    continue;
                }
                int start = x++;
                while (x < width && boundary(pixels, width, height, x, y, 0, dy)) x++;
                float yEdge = 1 - (y + (dy > 0 ? 1 : 0)) / (float) height;
                float v = (y + 0.5f) / height;
                float x0 = start / (float) width, x1 = x / (float) width;
                float u0 = (start + 0.5f) / width, u1 = (x - 0.5f) / width;
                edges.add(
                    dy < 0 ? new Edge(x1, yEdge, x0, yEdge, u1, v, u0, v, 0, 1)
                        : new Edge(x0, yEdge, x1, yEdge, u0, v, u1, v, 0, -1));
            }
        }
    }

    private static void vertical(int[] pixels, int width, int height, int dx, List<Edge> edges) {
        for (int x = 0; x < width; x++) {
            int y = 0;
            while (y < height) {
                if (!boundary(pixels, width, height, x, y, dx, 0)) {
                    y++;
                    continue;
                }
                int start = y++;
                while (y < height && boundary(pixels, width, height, x, y, dx, 0)) y++;
                float xEdge = (x + (dx > 0 ? 1 : 0)) / (float) width;
                float u = (x + 0.5f) / width;
                float y0 = 1 - start / (float) height, y1 = 1 - y / (float) height;
                float v0 = (start + 0.5f) / height, v1 = (y - 0.5f) / height;
                edges.add(
                    dx < 0 ? new Edge(xEdge, y0, xEdge, y1, u, v0, u, v1, -1, 0)
                        : new Edge(xEdge, y1, xEdge, y0, u, v1, u, v0, 1, 0));
            }
        }
    }
}
