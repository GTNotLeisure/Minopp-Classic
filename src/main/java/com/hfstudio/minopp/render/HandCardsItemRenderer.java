package com.hfstudio.minopp.render;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

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

    private static final CardBatch BATCH = new CardBatch().layerDepth(0.0002f);
    private static final int MAX_RENDERED_CARDS = 24;
    private static final float CARD_HALF_WIDTH = 0.32f;
    private static final float CARD_HALF_HEIGHT = 0.5f;
    private static final float CARD_THICKNESS = 0.008f;
    private static final float LAYER_STEP = 0.011f;

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

        int total = realPlayer.hand.size(), n = Math.min(total, MAX_RENDERED_CARDS);
        if (n == 0) return;
        boolean dark = tableEntity.game.darkSide;
        UUID viewer = Minecraft.getMinecraft().thePlayer == null ? null
            : Minecraft.getMinecraft().thePlayer.getGameProfile()
                .getId();
        boolean visible = bearerId.equals(viewer) || realPlayer.handVisibleUntil > System.currentTimeMillis();
        float spread = Math.min(110, (n - 1) * 13f);
        float gripRadius = CARD_HALF_HEIGHT * 0.85f;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glTranslated(0.5, 0.5, 0.18);
            // Stand the fan upright within Minecraft's default equipped-item transform.
            GL11.glRotatef(-80, 1, 0, 0);
            for (int k = 0; k < n; k++) {
                Card card = realPlayer.hand.get(total - n + k);
                float angle = n == 1 ? 0 : spread * (k / (float) (n - 1) - 0.5f);
                double radians = Math.toRadians(angle);
                // Rotate around the lower grip and separate each solid card along its thickness.
                BATCH.at(
                    -gripRadius * (float) Math.sin(radians),
                    gripRadius * ((float) Math.cos(radians) - 1),
                    (k - (n - 1) * 0.5f) * LAYER_STEP,
                    angle);
                BATCH.slab(CARD_HALF_WIDTH, CARD_HALF_HEIGHT, -CARD_THICKNESS, 0, 0xF2F0EA);
                CardFace face = card.face(dark);
                if (visible && !bearerId.equals(viewer) && !face.isHidden())
                    BATCH.face(face, theme, CARD_HALF_WIDTH, CARD_HALF_HEIGHT, 0.0003f, 0xFFFFFF, false);
                else BATCH.back(card, dark, theme, CARD_HALF_WIDTH, CARD_HALF_HEIGHT, 0.0003f, 0xFFFFFF, false);
                if (!visible || face.isHidden()) BATCH.back(
                    card,
                    dark,
                    theme,
                    CARD_HALF_WIDTH,
                    CARD_HALF_HEIGHT,
                    -CARD_THICKNESS - 0.0003f,
                    0xFFFFFF,
                    true);
                else BATCH
                    .face(face, theme, CARD_HALF_WIDTH, CARD_HALF_HEIGHT, -CARD_THICKNESS - 0.0003f, 0xFFFFFF, true);
            }
            BATCH.flush();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
