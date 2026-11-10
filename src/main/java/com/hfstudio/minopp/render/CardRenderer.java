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

@SideOnly(Side.CLIENT)
public class CardRenderer {

    private CardRenderer() {}

    public static List<String> layers(CardFace face) {
        List<String> custom = CardRenderRegistry.layers(face);
        if (custom != null) return custom;
        List<String> layers = new ArrayList<>(4);
        layers.add("frame_" + face.color.id());
        if (face.flexColor != null) layers.add("flex_" + face.flexColor.id());
        CardType type = face.type();
        if (type.fast) layers.add("fast_overlay");
        if (type.numbered) {
            layers.add(symbolLayer(face));
            if (type == CardTypes.POWER_NUMBER) layers.add("mark_power");
        } else {
            layers.add(symbolLayer(face));
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

    public static String symbolLayer(CardFace face) {
        String custom = CardRenderRegistry.symbol(face);
        if (custom != null) return custom;
        CardType type = face.type();
        if (type.numbered && !type.customSymbol) return "num_" + Math.max(0, face.number);
        int colon = type.symbol.indexOf(':');
        return colon < 0 ? "symbol_" + type.symbol
            : type.symbol.substring(0, colon + 1) + "symbol_" + type.symbol.substring(colon + 1);
    }

    public static void drawFace2D(CardFace face, String theme, float x, float y, float w, float h) {
        ResourceLocation[] layers = CardBatch.textures(face, theme);
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] == null) continue;
            int tint = ThemeManager.INSTANCE.layerTint(theme, face, i, layers[i]);
            GL11.glColor4f((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(layers[i]);
            GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void drawCornerSymbol(CardFace face, String theme, float x, float y, float size) {
        String layer = symbolLayer(face);
        int colon = layer.indexOf(':');
        String corner = colon < 0 ? "corner_" + layer
            : layer.substring(0, colon + 1) + "corner_" + layer.substring(colon + 1);
        GL11.glColor4f(1, 1, 1, 1);
        if (bind(theme, corner)) {
            GuiDraw.blitBound(x, y, size, size, 0, 0, 1, 1, 1, 1);
        } else if (bind(theme, layer)) {
            GuiDraw.blitBound(x, y, size, size, 5, 14, 22, 22, 32, 50);
        }
    }

    public static void drawBack2D(String theme, boolean dark, float x, float y, float w, float h) {
        GL11.glColor4f(1, 1, 1, 1);
        if (bind(theme, dark ? "back_dark" : "back")) GuiDraw.blitBound(x, y, w, h, 0, 0, 1, 1, 1, 1);
    }

    public static void drawCardBack2D(Card card, boolean darkSide, String theme, float x, float y, float w, float h) {
        CardFace back = card.backFace(darkSide);
        if (back != null) drawFace2D(back, theme, x, y, w, h);
        else drawBack2D(theme, false, x, y, w, h);
    }

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

    public static void renderBack3D(String theme, boolean dark, float hw, float hh, float z, int color) {
        if (bind(theme, dark ? "back_dark" : "back")) quad(hw, hh, z, color);
    }

}
