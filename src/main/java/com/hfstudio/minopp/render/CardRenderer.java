package com.hfstudio.minopp.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardType;
import com.hfstudio.minopp.game.CardTypes;
import com.hfstudio.minopp.gui.GuiDraw;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws cards from themed layers: frame (by color) + optional Flex partner overlay + number/symbol + marks.
 */
@SideOnly(Side.CLIENT)
public final class CardRenderer {

    private CardRenderer() {}

    public static List<String> layers(CardFace face) {
        List<String> layers = new ArrayList<>(4);
        layers.add("frame_" + face.color.id());
        if (face.flexColor != null) layers.add("flex_" + face.flexColor.id());
        CardType type = face.type();
        if (type.numbered) {
            layers.add("num_" + Math.max(0, Math.min(9, face.number)));
            if (type == CardTypes.POWER_NUMBER) layers.add("mark_power");
        } else {
            layers.add("symbol_" + type.symbol);
        }
        return layers;
    }

    private static boolean bind(String theme, String layer) {
        ResourceLocation tex = ThemeManager.INSTANCE.texture(theme, layer);
        if (tex == null) return false;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);
        return true;
    }

    // ------------------------------------------------------------------ 2D (GUI)

    public static void drawFace2D(CardFace face, String theme, float x, float y, float w, float h) {
        GL11.glColor4f(1, 1, 1, 1);
        for (String layer : layers(face)) {
            if (bind(theme, layer)) GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
        }
    }

    public static void drawBack2D(String theme, boolean dark, float x, float y, float w, float h) {
        GL11.glColor4f(1, 1, 1, 1);
        if (bind(theme, dark ? "back_dark" : "back")) GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
    }

    /** Back side as seen by others: the other face for Flip cards, the theme back otherwise. */
    public static void drawCardBack2D(Card card, boolean darkSide, String theme, float x, float y, float w, float h) {
        CardFace back = card.backFace(darkSide);
        if (back != null) drawFace2D(back, theme, x, y, w, h);
        else drawBack2D(theme, false, x, y, w, h);
    }

    // ------------------------------------------------------------------ 3D (world), card centered on the origin in XY

    private static void quad(float hw, float hh, float z, int color) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorOpaque_I(color);
        t.setNormal(0, 0, 1);
        t.addVertexWithUV(-hw, hh, z, 0, 0);
        t.addVertexWithUV(-hw, -hh, z, 0, 1);
        t.addVertexWithUV(hw, -hh, z, 1, 1);
        t.addVertexWithUV(hw, hh, z, 1, 0);
        t.draw();
    }

    public static void renderFace3D(CardFace face, String theme, float hw, float hh, float z, int color) {
        float dz = 0;
        for (String layer : layers(face)) {
            if (bind(theme, layer)) quad(hw, hh, z + dz, color);
            dz += 0.002f;
        }
    }

    public static void renderBack3D(String theme, boolean dark, float hw, float hh, float z, int color) {
        if (bind(theme, dark ? "back_dark" : "back")) quad(hw, hh, z, color);
    }

    public static void renderCardBack3D(Card card, boolean darkSide, String theme, float hw, float hh, float z,
        int color) {
        CardFace back = card == null ? null : card.backFace(darkSide);
        if (back != null) renderFace3D(back, theme, hw, hh, z, color);
        else renderBack3D(theme, false, hw, hh, z, color);
    }
}
