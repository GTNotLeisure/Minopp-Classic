package com.hfstudio.minopp.render;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BlockEntityMinoTableRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTick) {
        BlockEntityMinoTable blockEntity = (BlockEntityMinoTable) te;
        BlockEntityMinoTable.activeAnimations.removeIf(BlockEntityMinoTable.HandSwapAnimation::isDone);
        CardGame game = blockEntity.game;
        if (game == null) return;
        String viewerTheme = ThemeManager.INSTANCE.selected();

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1, 1, 1, 1);

        // Draw deck pile (face-down cards stacked at core +0.5)
        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.94, 0.5);
        GL11.glScalef(0.5f, 0.4f, 0.5f);
        GL11.glRotatef(-90, 1, 0, 0);
        Random deckRandom = new Random(1);
        int layers = (int) Math.ceil(game.deck.size() / 5f);
        float layerH = 1f / 48f;
        for (int ci = 0; ci < layers; ci++) {
            GL11.glPushMatrix();
            GL11.glTranslated(deckRandom.nextFloat() * 0.06 - 0.03, deckRandom.nextFloat() * 0.06 - 0.03, ci * layerH);
            // card edges of this layer: alternate shades read as individual cards
            CardRenderer.renderSlabSides(0.32f, 0.5f, -layerH, 0, ci % 2 == 0 ? 0xF4F2EC : 0xD6D2C8);
            if (ci == layers - 1) {
                Card top = game.deck.isEmpty() ? null : game.deck.get(game.deck.size() - 1);
                CardRenderer.renderCardBack3D(top, game.darkSide, viewerTheme, 0.32f, 0.5f, 0.001f, 0xFFFFFF);
            }
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();

        // Draw discard pile (face-up cards stacked in center)
        GL11.glPushMatrix();
        GL11.glTranslated(1.5, 0.94, 1.5);
        GL11.glScalef(0.3f, 0.3f, 0.3f);
        GL11.glRotatef(-90, 1, 0, 0);
        Random discardRandom = new Random(1);

        int discardSize = game.discardDeck.size();
        int maxVisualCards = 20; // max cards rendered (excluding top card)
        int startIndex = Math.max(0, discardSize - maxVisualCards);
        for (int s = 0; s < startIndex; s++) {
            discardRandom.nextFloat();
            discardRandom.nextFloat();
            discardRandom.nextFloat();
        }

        for (int ci = startIndex; ci <= discardSize; ci++) {
            GL11.glPushMatrix();
            // controls card variation in the middle pile
            float offsetX = discardRandom.nextFloat() * 0.3f - 0.15f;
            float offsetY = discardRandom.nextFloat() * 0.3f - 0.15f;
            float cardYaw = discardRandom.nextFloat() * 0.8f - 0.4f;
            int visualIndex = ci - startIndex;
            if (ci == discardSize) {
                GL11.glTranslated(0, 0, visualIndex * (1f / 56f) + 0.01f);
            } else {
                GL11.glTranslated(offsetX, offsetY, visualIndex * (1f / 56f));
                GL11.glRotatef((float) Math.toDegrees(cardYaw), 0, 0, 1);
            }
            Card card = ci == discardSize ? game.topCard : game.discardDeck.get(ci);
            int color = (ci == discardSize) ? 0xFFFFFF : 0xBBBBBB;
            CardRenderer.renderFace3D(
                card.face(game.darkSide),
                ThemeManager.INSTANCE.themeFor(card.owner),
                0.52f,
                0.8f,
                0,
                color);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();

        // 3D wild color choice of this client, standing around the pile
        net.minecraft.entity.EntityLivingBase viewer = Minecraft.getMinecraft().renderViewEntity;
        if (viewer != null) ColorPicker3D.render(blockEntity.getBlockPos(), viewer.posX, viewer.posZ);

        // Top card label
        CardFace top = game.topFace();
        IChatComponent component = top.getDisplayName();
        if (top.isWild() || game.currentColor != top.color) {
            component.appendText(" → ")
                .appendSibling(Chat.translatable(game.currentColor.langKey()));
        }
        renderLabel(component.getFormattedText(), 1.5, 0.94 + 0.17, 1.5, 0.009f);

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
                CardRenderer.renderBack3D(viewerTheme, game.darkSide, 0.32f, 0.5f, 0, 0xFFFFFF);
                GL11.glPopMatrix();
            }
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
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
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
    }
}
