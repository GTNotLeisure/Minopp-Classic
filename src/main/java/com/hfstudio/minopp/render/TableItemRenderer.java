package com.hfstudio.minopp.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TableItemRenderer implements IItemRenderer {

    private static final CardBatch BATCH = new CardBatch();

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return helper == ItemRendererHelper.INVENTORY_BLOCK || helper == ItemRendererHelper.ENTITY_ROTATION
            || helper == ItemRendererHelper.ENTITY_BOBBING;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        }
        if (type == ItemRenderType.INVENTORY) GL11.glScalef(1.15f, 1.15f, 1.15f);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        IIcon top = Blocks.planks.getIcon(1, 1);
        IIcon leg = Blocks.log2.getIcon(2, 1);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        box(t, top, -0.5, 0.05, -0.5, 0.5, 0.25, 0.5);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            double x = sx * 0.36, z = sz * 0.36;
            box(t, leg, x - 0.07, -0.5, z - 0.07, x + 0.07, 0.05, z + 0.07);
        }
        t.draw();

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0.26f, 0);
        GL11.glRotatef(-90, 1, 0, 0);
        String theme = ThemeManager.INSTANCE.selected();
        BATCH.at(-0.12f, 0.02f, 0.002f, -14);
        BATCH.face(new CardFace("number", CardColor.BLUE, 7), theme, 0.13f, 0.2f, 0, 0xFFFFFF, false);
        BATCH.at(0.1f, -0.04f, 0.006f, 12);
        BATCH.face(new CardFace("number", CardColor.RED, 5), theme, 0.13f, 0.2f, 0, 0xFFFFFF, false);
        BATCH.at(0.02f, 0.12f, 0.01f, 3);
        BATCH.face(new CardFace("wild_draw4", CardColor.WILD, -1), theme, 0.13f, 0.2f, 0, 0xFFFFFF, false);
        BATCH.flush();
        GL11.glPopMatrix();

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    private static void box(Tessellator t, IIcon icon, double x0, double y0, double z0, double x1, double y1,
        double z1) {
        double u0 = icon.getMinU(), v0 = icon.getMinV(), u1 = icon.getMaxU(), v1 = icon.getMaxV();
        t.setNormal(0, 1, 0);
        t.addVertexWithUV(x0, y1, z1, u0, v1);
        t.addVertexWithUV(x1, y1, z1, u1, v1);
        t.addVertexWithUV(x1, y1, z0, u1, v0);
        t.addVertexWithUV(x0, y1, z0, u0, v0);
        t.setNormal(0, -1, 0);
        t.addVertexWithUV(x0, y0, z0, u0, v0);
        t.addVertexWithUV(x1, y0, z0, u1, v0);
        t.addVertexWithUV(x1, y0, z1, u1, v1);
        t.addVertexWithUV(x0, y0, z1, u0, v1);
        t.setNormal(0, 0, -1);
        t.addVertexWithUV(x0, y1, z0, u0, v0);
        t.addVertexWithUV(x1, y1, z0, u1, v0);
        t.addVertexWithUV(x1, y0, z0, u1, v1);
        t.addVertexWithUV(x0, y0, z0, u0, v1);
        t.setNormal(0, 0, 1);
        t.addVertexWithUV(x0, y0, z1, u0, v1);
        t.addVertexWithUV(x1, y0, z1, u1, v1);
        t.addVertexWithUV(x1, y1, z1, u1, v0);
        t.addVertexWithUV(x0, y1, z1, u0, v0);
        t.setNormal(-1, 0, 0);
        t.addVertexWithUV(x0, y0, z0, u0, v1);
        t.addVertexWithUV(x0, y0, z1, u1, v1);
        t.addVertexWithUV(x0, y1, z1, u1, v0);
        t.addVertexWithUV(x0, y1, z0, u0, v0);
        t.setNormal(1, 0, 0);
        t.addVertexWithUV(x1, y1, z0, u0, v0);
        t.addVertexWithUV(x1, y1, z1, u1, v0);
        t.addVertexWithUV(x1, y0, z1, u1, v1);
        t.addVertexWithUV(x1, y0, z0, u0, v1);
    }
}
