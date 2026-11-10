package com.hfstudio.minopp.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HandCardsItemRenderer implements IItemRenderer {

    private static final CardBatch BATCH = new CardBatch();

    private static final ResourceLocation ARROW = Mino.id("textures/gui/arrow_down.png");

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack itemStack, Object... data) {
        if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) return;

        World level = Minecraft.getMinecraft().theWorld;
        BlockPos tablePos = ItemHandCards.getGamePos(level, itemStack);
        UUID bearerId = ItemDataUtils.getBearerId(itemStack);
        Long hideUntil = BlockEntityMinoTable.hideHandUntil.get(bearerId);
        if (hideUntil != null && System.currentTimeMillis() < hideUntil) return;
        if (tablePos == null || level == null) return;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(level, tablePos);
        if (tableEntity == null || tableEntity.game == null) return;
        CardPlayer realPlayer = tableEntity.game.deAmputate(bearerId);
        if (realPlayer == null) return;
        String theme = ThemeManager.INSTANCE.themeFor(bearerId);

        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glTranslated(0.5, 0.5, 0.18);
        int total = realPlayer.hand.size(), n = Math.min(total, 24);
        float step = (float) Math.min(0.08, 1.2 / Math.max(1, n));
        boolean dark = tableEntity.game.darkSide;
        UUID viewer = Minecraft.getMinecraft().thePlayer == null ? null
            : Minecraft.getMinecraft().thePlayer.getGameProfile()
                .getId();
        boolean visible = bearerId.equals(viewer) || realPlayer.handVisibleUntil > System.currentTimeMillis();
        for (int k = 0; k < n; k++) {
            Card card = realPlayer.hand.get(total - n + k);
            BATCH.at(0, 0.02f * k, step * k, 0);
            BATCH.slab(0.32f, 0.5f, -0.035f, 0, k % 2 == 0 ? 0xF2F0EA : 0xDAD6CC);
            CardFace face = card.face(dark);
            if (visible && !bearerId.equals(viewer) && !face.isHidden())
                BATCH.face(face, theme, 0.32f, 0.5f, 0.001f, 0xFFFFFF, false);
            else BATCH.back(card, dark, theme, 0.32f, 0.5f, 0.001f, 0xFFFFFF, false);
            if (!visible || face.isHidden()) BATCH.back(card, dark, theme, 0.32f, 0.5f, -0.036f, 0xFFFFFF, true);
            else BATCH.face(face, theme, 0.32f, 0.5f, -0.036f, 0xFFFFFF, true);
        }
        BATCH.flush();
        GL11.glTranslated(0, 0.02 * n, step * n);

        if (tableEntity.game.currentPlayerIndex == tableEntity.game.players.indexOf(realPlayer)) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(ARROW);
            float v0 = ((int) (System.currentTimeMillis() / 100L) % 5) * 0.2f;
            float v1 = v0 + 0.2f;
            GL11.glTranslated(0, 0.8, 0);
            for (int i = 0; i < 2; i++) {
                GL11.glPushMatrix();
                GL11.glRotatef(i == 0 ? 45 : -45, 0, 1, 0);
                GL11.glScalef(0.2f, 0.2f, 1);
                Tessellator t = Tessellator.instance;
                t.startDrawingQuads();
                t.setNormal(0, 1, 0);
                t.addVertexWithUV(-1, 1, 0, 0, v0);
                t.addVertexWithUV(-1, -1, 0, 0, v1);
                t.addVertexWithUV(1, -1, 0, 1, v1);
                t.addVertexWithUV(1, 1, 0, 1, v0);
                t.draw();
                GL11.glPopMatrix();
            }
        }
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }
}
