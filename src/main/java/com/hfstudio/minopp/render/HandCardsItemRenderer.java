package com.hfstudio.minopp.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 counterpart of HandCardsWithoutLevelRenderer: shows the held card fan in third person, nothing in first
 * person (the HUD overlay shows the hand instead), and the plain sprite elsewhere.
 */
@SideOnly(Side.CLIENT)
public class HandCardsItemRenderer implements IItemRenderer {

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
        BlockPos tablePos = ItemDataUtils.getBlockPos(itemStack);
        UUID bearerId = ItemDataUtils.getBearerId(itemStack);
        Long hideUntil = BlockEntityMinoTable.hideHandUntil.get(bearerId);
        if (hideUntil != null && System.currentTimeMillis() < hideUntil) return;
        if (tablePos == null || level == null) return;
        tablePos = BlockMinoTable.getCore(level, tablePos.x, tablePos.y, tablePos.z);
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(level, tablePos);
        if (tableEntity == null || tableEntity.game == null) return;
        CardPlayer realPlayer = tableEntity.game.players.stream()
            .filter(p -> p.uuid.equals(bearerId))
            .findFirst()
            .orElse(null);
        if (realPlayer == null) return;

        IIcon icon = Mino.ITEM_HAND_CARDS.getIconFromDamage(0);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationItemsTexture);
        GL11.glPushMatrix();
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glTranslated(0, 0, 0.18);
        for (int k = 0; k < realPlayer.hand.size(); k++) {
            ItemRenderer.renderItemIn2D(
                Tessellator.instance,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                icon.getIconWidth(),
                icon.getIconHeight(),
                0.0625F);
            GL11.glTranslated(0, 0.02, 0.08);
        }

        // Render arrow texture above the cards of the current player
        if (tableEntity.game.currentPlayerIndex == tableEntity.game.players.indexOf(realPlayer)) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(ARROW);
            float v0 = ((int) (System.currentTimeMillis() / 100L) % 5) * 0.2f;
            float v1 = v0 + 0.2f;
            GL11.glTranslated(0.5, 1.3, 0);
            GL11.glDisable(GL11.GL_CULL_FACE);
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
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }
}
