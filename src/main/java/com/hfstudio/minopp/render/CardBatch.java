package com.hfstudio.minopp.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.render.SpriteContour.Edge;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CardBatch {

    private static final Map<String, Map<CardFace, ResourceLocation[]>> FACE_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, SpriteContour> SPRITE_CONTOURS = new HashMap<>();
    private static long cacheGeneration;

    public static void clearCache() {
        FACE_TEXTURES.clear();
        SPRITE_CONTOURS.clear();
        cacheGeneration++;
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
        ResourceLocation texture;

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

    private final List<Map<ResourceLocation, Quads>> textured = new ArrayList<>();
    private final Quads plain = new Quads();
    private final List<List<Quads>> active = new ArrayList<>();
    private long generation = cacheGeneration;

    private float ox, oy, oz, cos = 1, sin = 0;
    private float layerDepth = 0.002f;

    public CardBatch() {
        for (int i = 0; i < 5; i++) {
            textured.add(new HashMap<>());
            active.add(new ArrayList<>());
        }
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

    /**
     * Places a local pivot at the target position and rotates the card around it.
     */
    public CardBatch atPivot(float x, float y, float z, float angleDeg, float pivotX, float pivotY) {
        at(x, y, z, angleDeg);
        ox -= pivotX * cos - pivotY * sin;
        oy -= pivotX * sin + pivotY * cos;
        return this;
    }

    public CardBatch layerDepth(float depth) {
        if (!Float.isFinite(depth) || depth <= 0) throw new IllegalArgumentException("Invalid texture layer depth");
        layerDepth = depth;
        return this;
    }

    private void vertex(Quads q, float lx, float ly, float lz, float u, float v, int color, int normal) {
        q.add(ox + lx * cos - ly * sin, oy + lx * sin + ly * cos, oz + lz, u, v, color, normal);
    }

    private Quads quadsFor(int level, ResourceLocation tex) {
        if (generation != cacheGeneration) {
            for (Map<ResourceLocation, Quads> map : textured) map.clear();
            generation = cacheGeneration;
        }
        while (level >= textured.size()) {
            textured.add(new HashMap<>());
            active.add(new ArrayList<>());
        }
        Map<ResourceLocation, Quads> map = textured.get(level);
        Quads q = map.get(tex);
        if (q == null) {
            q = new Quads();
            q.texture = tex;
            map.put(tex, q);
        }
        if (q.size == 0) active.get(level)
            .add(q);
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
            int tint = ThemeManager.INSTANCE.layerTint(theme, face, i, tex[i]);
            int c = tint == 0xFFFFFF ? color
                : ((color >> 16 & 255) * (tint >> 16 & 255) / 255) << 16
                    | ((color >> 8 & 255) * (tint >> 8 & 255) / 255) << 8
                    | (color & 255) * (tint & 255) / 255;
            quad(i, tex[i], hw, hh, z + (flipped ? -layerDepth : layerDepth) * i, c, flipped);
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

    private static SpriteContour contour(ResourceLocation texture) {
        return SPRITE_CONTOURS.computeIfAbsent(texture, location -> {
            try (InputStream stream = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) throw new IOException("Unsupported sprite image");
                return SpriteContour.fromImage(image);
            } catch (IOException e) {
                Mino.LOGGER.warn("Unable to read card sprite contour {}", location, e);
                return SpriteContour.EMPTY;
            }
        });
    }

    /**
     * Extrudes the base artwork along its opaque contour; a null face uses the standard card back.
     */
    public void extrude(CardFace face, String theme, float hw, float hh, float z0, float z1, int color,
        boolean flipped) {
        ResourceLocation texture = face == null ? ThemeManager.INSTANCE.texture(theme, "back") : null;
        int tint = 0xFFFFFF;
        if (face != null) {
            ResourceLocation[] layers = textures(face, theme);
            for (int i = 0; i < layers.length; i++) {
                if (layers[i] == null) continue;
                texture = layers[i];
                tint = ThemeManager.INSTANCE.layerTint(theme, face, i, texture);
                break;
            }
        }
        if (texture == null) return;
        int shaded = tint == 0xFFFFFF ? color
            : ((color >> 16 & 255) * (tint >> 16 & 255) / 255) << 16
                | ((color >> 8 & 255) * (tint >> 8 & 255) / 255) << 8
                | (color & 255) * (tint & 255) / 255;
        SpriteContour contour = contour(texture);
        if (contour.edges()
            .isEmpty()) return;
        Quads q = quadsFor(0, texture);
        float mirror = flipped ? -1 : 1;
        for (Edge edge : contour.edges()) {
            float x0 = (edge.x0() * 2 - 1) * hw * mirror, y0 = (edge.y0() * 2 - 1) * hh;
            float x1 = (edge.x1() * 2 - 1) * hw * mirror, y1 = (edge.y1() * 2 - 1) * hh;
            float nx = edge.nx() * mirror;
            int normal = packNormal(nx * cos - edge.ny() * sin, nx * sin + edge.ny() * cos, 0);
            if (flipped) {
                vertex(q, x1, y1, z0, edge.u1(), edge.v1(), shaded, normal);
                vertex(q, x0, y0, z0, edge.u0(), edge.v0(), shaded, normal);
                vertex(q, x0, y0, z1, edge.u0(), edge.v0(), shaded, normal);
                vertex(q, x1, y1, z1, edge.u1(), edge.v1(), shaded, normal);
            } else {
                vertex(q, x0, y0, z0, edge.u0(), edge.v0(), shaded, normal);
                vertex(q, x1, y1, z0, edge.u1(), edge.v1(), shaded, normal);
                vertex(q, x1, y1, z1, edge.u1(), edge.v1(), shaded, normal);
                vertex(q, x0, y0, z1, edge.u0(), edge.v0(), shaded, normal);
            }
        }
    }

    public void slab(float hw, float hh, float z0, float z1, int color) {
        int darker = (color >> 16 & 0xFF) * 9 / 10 << 16 | (color >> 8 & 0xFF) * 9 / 10 << 8 | (color & 0xFF) * 9 / 10;
        int down = packNormal(sin, -cos, 0), up = packNormal(-sin, cos, 0);
        int left = packNormal(-cos, -sin, 0), right = packNormal(cos, sin, 0);
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
        for (List<Quads> level : active) {
            for (Quads q : level) {
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(q.texture);
                emit(q, true);
                q.size = 0;
            }
            level.clear();
        }
    }
}
