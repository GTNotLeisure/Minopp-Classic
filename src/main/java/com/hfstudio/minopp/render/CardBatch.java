package com.hfstudio.minopp.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CardBatch {

    private static final int LEVELS = 5;
    private static final Map<String, Map<CardFace, ResourceLocation[]>> FACE_TEXTURES = new HashMap<>();

    public static void clearCache() {
        FACE_TEXTURES.clear();
    }

    static ResourceLocation[] textures(CardFace face, String theme) {
        Map<CardFace, ResourceLocation[]> byFace = FACE_TEXTURES.computeIfAbsent(theme, k -> new HashMap<>());
        ResourceLocation[] tex = byFace.get(face);
        if (tex == null) {
            List<String> layers = CardRenderer.layers(face);
            tex = new ResourceLocation[layers.size()];
            for (int i = 0; i < tex.length; i++) tex[i] = ThemeManager.INSTANCE.texture(theme, layers.get(i));
            byFace.put(face, tex);
        }
        return tex;
    }

    private static class Quads {

        float[] data = new float[16 * 9];
        int size;

        void add(float x, float y, float z, float u, float v, int color, int normal) {
            if (size + 7 > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size++] = x;
            data[size++] = y;
            data[size++] = z;
            data[size++] = u;
            data[size++] = v;
            data[size++] = Float.intBitsToFloat(color);
            data[size++] = Float.intBitsToFloat(normal);
        }
    }

    @SuppressWarnings("unchecked")
    private final Map<ResourceLocation, Quads>[] textured = new Map[LEVELS];
    private final Quads plain = new Quads();
    private final List<Quads> pool = new ArrayList<>();

    private float ox, oy, oz, cos = 1, sin = 0;

    public CardBatch() {
        for (int i = 0; i < LEVELS; i++) textured[i] = new LinkedHashMap<>();
    }

    public CardBatch at(float x, float y, float z, float angleDeg) {
        ox = x;
        oy = y;
        oz = z;
        double a = Math.toRadians(angleDeg);
        cos = (float) Math.cos(a);
        sin = (float) Math.sin(a);
        return this;
    }

    private void vertex(Quads q, float lx, float ly, float lz, float u, float v, int color, int normal) {
        q.add(ox + lx * cos - ly * sin, oy + lx * sin + ly * cos, oz + lz, u, v, color, normal);
    }

    private Quads quadsFor(int level, ResourceLocation tex) {
        Map<ResourceLocation, Quads> map = textured[Math.min(level, LEVELS - 1)];
        Quads q = map.get(tex);
        if (q == null) {
            q = pool.isEmpty() ? new Quads() : pool.remove(pool.size() - 1);
            map.put(tex, q);
        }
        return q;
    }

    private void quad(int level, ResourceLocation tex, float hw, float hh, float z, int color, boolean flipped) {
        if (tex == null) return;
        Quads q = quadsFor(level, tex);
        int normal = flipped ? packNormal(0, 0, -1) : packNormal(0, 0, 1);
        if (!flipped) {
            vertex(q, -hw, hh, z, 0, 0, color, normal);
            vertex(q, -hw, -hh, z, 0, 1, color, normal);
            vertex(q, hw, -hh, z, 1, 1, color, normal);
            vertex(q, hw, hh, z, 1, 0, color, normal);
        } else {
            // seen from -Z: mirror X so the face reads correctly from the other side
            vertex(q, hw, hh, z, 0, 0, color, normal);
            vertex(q, hw, -hh, z, 0, 1, color, normal);
            vertex(q, -hw, -hh, z, 1, 1, color, normal);
            vertex(q, -hw, hh, z, 1, 0, color, normal);
        }
    }

    public void face(CardFace face, String theme, float hw, float hh, float z, int color, boolean flipped) {
        ResourceLocation[] tex = textures(face, theme);
        for (int i = 0; i < tex.length; i++) {
            int tint = ThemeManager.INSTANCE.layerTint(theme, face, i);
            int c = tint == 0xFFFFFF ? color
                : ((color >> 16 & 255) * (tint >> 16 & 255) / 255) << 16
                    | ((color >> 8 & 255) * (tint >> 8 & 255) / 255) << 8
                    | (color & 255) * (tint & 255) / 255;
            quad(i, tex[i], hw, hh, z + (flipped ? -0.002f : 0.002f) * i, c, flipped);
        }
    }

    public void back(Card card, boolean darkSide, String theme, float hw, float hh, float z, int color,
        boolean flipped) {
        CardFace backFace = card == null ? null : card.backFace(darkSide);
        if (backFace != null) {
            face(backFace, theme, hw, hh, z, color, flipped);
        } else {
            quad(0, ThemeManager.INSTANCE.texture(theme, "back"), hw, hh, z, color, flipped);
        }
    }

    public void slab(float hw, float hh, float z0, float z1, int color) {
        int darker = (color >> 16 & 0xFF) * 9 / 10 << 16 | (color >> 8 & 0xFF) * 9 / 10 << 8 | (color & 0xFF) * 9 / 10;
        int down = packNormal(0, -1, 0), up = packNormal(0, 1, 0);
        int left = packNormal(-1, 0, 0), right = packNormal(1, 0, 0);
        vertex(plain, -hw, -hh, z0, 0, 0, color, down);
        vertex(plain, hw, -hh, z0, 0, 0, color, down);
        vertex(plain, hw, -hh, z1, 0, 0, color, down);
        vertex(plain, -hw, -hh, z1, 0, 0, color, down);
        vertex(plain, -hw, hh, z1, 0, 0, color, up);
        vertex(plain, hw, hh, z1, 0, 0, color, up);
        vertex(plain, hw, hh, z0, 0, 0, color, up);
        vertex(plain, -hw, hh, z0, 0, 0, color, up);
        vertex(plain, -hw, -hh, z1, 0, 0, darker, left);
        vertex(plain, -hw, hh, z1, 0, 0, darker, left);
        vertex(plain, -hw, hh, z0, 0, 0, darker, left);
        vertex(plain, -hw, -hh, z0, 0, 0, darker, left);
        vertex(plain, hw, -hh, z0, 0, 0, darker, right);
        vertex(plain, hw, hh, z0, 0, 0, darker, right);
        vertex(plain, hw, hh, z1, 0, 0, darker, right);
        vertex(plain, hw, -hh, z1, 0, 0, darker, right);
    }

    private static int packNormal(float x, float y, float z) {
        byte bx = (byte) (int) (x * 127), by = (byte) (int) (y * 127), bz = (byte) (int) (z * 127);
        return bx & 0xFF | (by & 0xFF) << 8 | (bz & 0xFF) << 16;
    }

    private static void emit(Quads q, boolean textured) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float[] d = q.data;
        for (int i = 0; i < q.size; i += 7) {
            t.setColorOpaque_I(Float.floatToRawIntBits(d[i + 5]));
            int n = Float.floatToRawIntBits(d[i + 6]);
            t.setNormal((byte) n / 127f, (byte) (n >> 8) / 127f, (byte) (n >> 16) / 127f);
            if (textured) t.addVertexWithUV(d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4]);
            else t.addVertex(d[i], d[i + 1], d[i + 2]);
        }
        t.draw();
    }

    /**
     * Draws everything collected: slab edges first, then card layers level by level (frames before symbols).
     */
    public void flush() {
        if (plain.size > 0) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            emit(plain, false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            plain.size = 0;
        }
        GL11.glColor4f(1, 1, 1, 1);
        for (Map<ResourceLocation, Quads> level : textured) {
            for (Map.Entry<ResourceLocation, Quads> e : level.entrySet()) {
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(e.getKey());
                emit(e.getValue(), true);
                e.getValue().size = 0;
                pool.add(e.getValue());
            }
            level.clear();
        }
    }
}
