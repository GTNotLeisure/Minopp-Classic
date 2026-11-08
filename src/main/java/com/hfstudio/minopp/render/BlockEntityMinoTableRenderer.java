package com.hfstudio.minopp.render;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BlockEntityMinoTableRenderer extends TileEntitySpecialRenderer {

    public static final ResourceLocation DECK_ATLAS = Mino.id("textures/gui/deck.png");

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTick) {
        BlockEntityMinoTable blockEntity = (BlockEntityMinoTable) te;
        BlockEntityMinoTable.activeAnimations.removeIf(BlockEntityMinoTable.HandSwapAnimation::isDone);
        if (blockEntity.game == null) return;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);

        // Draw deck pile (face-down cards stacked at core +0.5)
        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.94, 0.5);
        GL11.glScalef(0.5f, 0.4f, 0.5f);
        GL11.glRotatef(-90, 1, 0, 0);
        Random deckRandom = new Random(1);
        for (int ci = 0; ci < Math.ceil(blockEntity.game.deck.size() / 5f); ci++) {
            GL11.glPushMatrix();
            GL11.glTranslated(
                deckRandom.nextFloat() * 0.1 - 0.05,
                deckRandom.nextFloat() * 0.1 - 0.05,
                ci * (1f / 48f));
            renderCardBack();
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();

        // Draw discard pile (face-up cards stacked in center)
        GL11.glPushMatrix();
        GL11.glTranslated(1.5, 0.94, 1.5);
        GL11.glScalef(0.3f, 0.3f, 0.3f);
        GL11.glRotatef(-90, 1, 0, 0);
        bindTexture(DECK_ATLAS);
        Random discardRandom = new Random(1);

        int discardSize = blockEntity.game.discardDeck.size();
        int maxVisualCards = 20; // max cards rendered (excluding top card)
        int startIndex = Math.max(0, discardSize - maxVisualCards);

        for (int s = 0; s < startIndex; s++) {
            discardRandom.nextFloat();
            discardRandom.nextFloat();
            discardRandom.nextFloat();
        }

        Tessellator t = Tessellator.instance;
        for (int ci = startIndex; ci <= discardSize; ci++) {
            GL11.glPushMatrix();
            // controls card variation in the middle pile
            float offsetX = discardRandom.nextFloat() * 0.3f - 0.15f;
            float offsetY = discardRandom.nextFloat() * 0.3f - 0.15f;
            float cardYaw = discardRandom.nextFloat() * 0.8f - 0.4f;

            // Visual stack index (0 = bottom of visible portion)
            int visualIndex = ci - startIndex;

            if (ci == discardSize) {
                offsetX = 0;
                offsetY = 0;
                cardYaw = 0;
                GL11.glTranslated(offsetX, offsetY, visualIndex * (1f / 56f) + 0.01f);
            } else {
                GL11.glTranslated(offsetX, offsetY, visualIndex * (1f / 56f));
            }
            GL11.glRotatef((float) Math.toDegrees(cardYaw), 0, 0, 1);

            Card card = ci == discardSize ? blockEntity.game.topCard : blockEntity.game.discardDeck.get(ci);
            float cardU = getAtlasU(card) / 256f;
            float cardV = card.suit.ordinal() * 25 / 128f;
            float cardUW = 16 / 256f;
            float cardVH = 25 / 128f;
            int color = (ci == discardSize) ? 0xFFFFFF : 0xBBBBBB;

            // Border quad
            t.startDrawingQuads();
            t.setColorOpaque_I(0xFFFFFF);
            t.setNormal(0, 0, 1);
            t.addVertexWithUV(-0.52f, 0.8f, 0, cardU, cardV);
            t.addVertexWithUV(-0.52f, -0.8f, 0, cardU, cardV + cardVH);
            t.addVertexWithUV(0.52f, -0.8f, 0, cardU + cardUW, cardV + cardVH);
            t.addVertexWithUV(0.52f, 0.8f, 0, cardU + cardUW, cardV);
            // Face quad
            t.setColorOpaque_I(color);
            float fz = 1f / 64f;
            t.addVertexWithUV(-0.5f, 0.78f, fz, cardU, cardV);
            t.addVertexWithUV(-0.5f, -0.78f, fz, cardU, cardV + cardVH);
            t.addVertexWithUV(0.5f, -0.78f, fz, cardU + cardUW, cardV + cardVH);
            t.addVertexWithUV(0.5f, 0.78f, fz, cardU + cardUW, cardV);
            t.draw();

            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();

        // Top card label
        Card card = blockEntity.game.topCard;
        String component = (card.suit == Card.Suit.WILD) ? card.getDisplayName()
            .appendSibling(
                Chat.translatable(
                    "game.minopp.card.suit." + card.getEquivSuit()
                        .name()
                        .toLowerCase()))
            .getFormattedText()
            : card.getDisplayName()
                .getFormattedText();
        renderLabel(component, 1.5, 0.94 + 0.17, 1.5, 0.009f);

        for (BlockEntityMinoTable.HandSwapAnimation anim : BlockEntityMinoTable.activeAnimations) {
            int stackSize = Math.max(1, Math.min(anim.cardCount, 10));
            double dx = anim.toPos.x - anim.fromPos.x;
            double dz = anim.toPos.z - anim.fromPos.z;
            float yaw = (float) Math.atan2(dz, dx);
            for (int ci = 0; ci < stackSize; ci++) {
                float cardRaw = anim.rawProgress() - ci * BlockEntityMinoTable.HandSwapAnimation.STAGGER;
                float cardT = easeInOut(Math.max(0f, Math.min(1f, cardRaw)));
                double cx = Mth.lerp(cardT, anim.fromPos.x, anim.toPos.x);
                double cy = Mth.lerp(cardT, anim.fromPos.y, anim.toPos.y) + Math.sin(cardT * Math.PI) * 0.5;
                double cz = Mth.lerp(cardT, anim.fromPos.z, anim.toPos.z);
                GL11.glPushMatrix();
                GL11.glTranslated(cx, cy, cz);
                GL11.glScalef(0.5f, 0.5f, 0.5f);
                GL11.glRotatef((float) Math.toDegrees(-yaw + (float) Math.PI / 2), 0, 1, 0);
                GL11.glRotatef(-90, 1, 0, 0);
                GL11.glTranslated(0, 0, ci * (1f / 48f));
                renderCardBack();
                GL11.glPopMatrix();
            }
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    public static float getAtlasU(Card card) {
        return switch (card.family) {
            case NUMBER -> Math.abs(card.number) * 16;
            case SKIP -> 160;
            case DRAW -> 176;
            case REVERSE -> 192;
        };
    }

    /** Renders the hand cards item sprite as a thin 3D card centered on the origin (like the 1.20 FIXED transform). */
    public static void renderCardBack() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);
        IIcon icon = Mino.ITEM_HAND_CARDS.getIconFromDamage(0);
        if (icon == null) return;
        GL11.glPushMatrix();
        GL11.glTranslatef(-0.5f, -0.5f, 0.03125f);
        ItemRenderer.renderItemIn2D(
            Tessellator.instance,
            icon.getMaxU(),
            icon.getMinV(),
            icon.getMinU(),
            icon.getMaxV(),
            icon.getIconWidth(),
            icon.getIconHeight(),
            0.0625F);
        GL11.glPopMatrix();
    }

    private void renderLabel(String text, double x, double y, double z, float scale) {
        FontRenderer font = func_147498_b();
        if (font == null) return;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        int w = font.getStringWidth(text) / 2;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(0.0F, 0.0F, 0.0F, Minecraft.getMinecraft().gameSettings.chatOpacity * 0.25f + 0.1f);
        t.addVertex(-w - 1, -1, 0.0D);
        t.addVertex(-w - 1, 8, 0.0D);
        t.addVertex(w + 1, 8, 0.0D);
        t.addVertex(w + 1, -1, 0.0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -w, 0, 0x20FFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        font.drawString(text, -w, 0, 0xFFFFFFFF);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
    }
}
