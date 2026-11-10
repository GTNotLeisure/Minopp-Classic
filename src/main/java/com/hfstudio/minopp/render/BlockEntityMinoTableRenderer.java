package com.hfstudio.minopp.render;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;
import com.hfstudio.minopp.util.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BlockEntityMinoTableRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTick) {
        BlockEntityMinoTable blockEntity = (BlockEntityMinoTable) te;
        blockEntity.activeAnimations.removeIf(BlockEntityMinoTable.HandSwapAnimation::isDone);
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

        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.94, 0.5);
        GL11.glScalef(0.5f, 0.4f, 0.5f);
        GL11.glRotatef(-90, 1, 0, 0);
        int layers = BlockMinoTable.deckLayers(game.deck.size());
        float layerH = BlockMinoTable.DECK_LAYER_H;
        float total = layers * layerH;
        BATCH.at(0, 0, total, 0);
        BATCH.slab(0.32f, 0.5f, -total, 0, 0xECEAE2);
        Card shown = !game.deck.isEmpty() ? game.deck.get(game.deck.size() - 1) : game.topCard;
        if (shown != null) BATCH.back(shown, game.darkSide, viewerTheme, 0.32f, 0.5f, 0.001f, 0xFFFFFF, false);
        BATCH.flush();
        GL11.glPopMatrix();
        deckHighlight(blockEntity, game, layers, layerH);

        GL11.glPushMatrix();
        GL11.glTranslated(1.5, 0.94, 1.5);
        GL11.glScalef(0.3f, 0.3f, 0.3f);
        GL11.glRotatef(-90, 1, 0, 0);
        int discardSize = game.discardDeck.size();
        int startIndex = Math.max(0, discardSize - 20);
        for (int ci = startIndex; ci <= discardSize; ci++) {
            int visualIndex = ci - startIndex;
            boolean isTop = ci == discardSize;
            if (isTop) {
                BATCH.at(0, 0, visualIndex * (1f / 56f) + 0.01f, 0);
            } else {
                BATCH.at(
                    jitter(ci, 0) * 0.3f - 0.15f,
                    jitter(ci, 1) * 0.3f - 0.15f,
                    visualIndex * (1f / 56f),
                    (float) Math.toDegrees(jitter(ci, 2) * 0.8f - 0.4f));
            }
            Card card = isTop ? game.topCard : game.discardDeck.get(ci);
            BATCH.face(
                card.face(game.darkSide),
                ThemeManager.INSTANCE.themeFor(card.owner),
                0.52f,
                0.8f,
                0,
                isTop ? 0xFFFFFF : 0xBBBBBB,
                false);
        }
        BATCH.flush();
        GL11.glPopMatrix();

        EntityLivingBase viewer = Minecraft.getMinecraft().renderViewEntity;
        BlockPos pos = blockEntity.getBlockPos();
        if (ColorPicker3D.isActiveFor(pos)) {
            double[] dir = seatDir(
                blockEntity,
                game,
                Minecraft.getMinecraft().thePlayer == null ? null
                    : Minecraft.getMinecraft().thePlayer.getGameProfile()
                        .getId());
            if (dir != null) ColorPicker3D.render(pos, pos.x + 1.5 + dir[0], pos.z + 1.5 + dir[1]);
            else if (viewer != null) ColorPicker3D.render(pos, viewer.posX, viewer.posZ);
        } else if (blockEntity.choosingUuid != null) {
            double[] dir = seatDir(blockEntity, game, blockEntity.choosingUuid);
            if (dir != null) ColorPicker3D.renderRemote(
                dir[0],
                dir[1],
                CardColor.forSide(game.darkSide),
                blockEntity.choosingRing,
                blockEntity.choosingHover,
                blockEntity.choosingSeenAt);
        }

        renderLabel(topLabel(blockEntity, game), 1.5, 0.94 + 0.17, 1.5, 0.009f);
        TableFx.render(blockEntity, game, partialTick);

        for (BlockEntityMinoTable.HandSwapAnimation anim : blockEntity.activeAnimations) {
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
                Card back = anim.cards.isEmpty() ? null : anim.cards.get(ci % anim.cards.size());
                if (back != null && back.dark != null) {
                    BATCH.at(0, 0, 0, 0);
                    BATCH.back(back, game.darkSide, viewerTheme, 0.32f, 0.5f, 0, 0xFFFFFF, false);
                    BATCH.flush();
                } else {
                    CardRenderer.renderBack3D(viewerTheme, game.darkSide, 0.32f, 0.5f, 0, 0xFFFFFF);
                }
                GL11.glPopMatrix();
            }
        }

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private static final CardBatch BATCH = new CardBatch();

    /**
     * From the table's center toward a player's seat, or null when they are not seated in this game.
     */
    static double[] seatDir(BlockEntityMinoTable table, CardGame game, UUID uuid) {
        if (uuid == null) return null;
        for (CardPlayer p : game.players) {
            if (!p.uuid.equals(uuid)) continue;
            Vec3d s = table.getSeatLocalPos(p.seat);
            double dx = s.x - 1.5, dz = s.z - 1.5;
            return dx * dx + dz * dz < 1e-6 ? null : new double[] { dx, dz };
        }
        return null;
    }

    /**
     * The viewer's own table's draw pile on screen last frame {minX, minY, maxX, maxY, cx, cy}, or null.
     */
    private static float[] deckBounds;
    /**
     * Set by the hand screen while the mouse is over the draw pile.
     */
    public static boolean deckHot;

    /**
     * Clears the draw pile hit region before world rendering.
     */
    public static void beginFrame() {
        deckBounds = null;
    }

    public static float[] deckOnScreen() {
        return deckBounds == null ? null : new float[] { deckBounds[4], deckBounds[5] };
    }

    public static boolean isOverDeck(int mouseX, int mouseY) {
        float[] b = deckBounds;
        return b != null && mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
    }

    private static void deckHighlight(BlockEntityMinoTable table, CardGame game, int layers, float layerH) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!table.getBlockPos()
            .equals(ItemHandCards.getHandCardGamePos(mc.thePlayer))) return;
        CardPlayer me = game.deAmputate(ItemHandCards.getCardPlayer(mc.thePlayer));
        if (me == null) return;
        double deckH = layers * layerH * 0.4, x0 = 0.24, x1 = 0.76, z0 = 0.24, z1 = 0.76, y0 = 0.94,
            y1 = 0.94 + deckH + 0.03;
        deckBounds = ColorPicker3D.projectBoxNow(x0, y0, z0, x1, y1, z1);
        boolean myTurn = game.players.indexOf(me) == game.currentPlayerIndex && !game.awaitingCutIn;
        BlockPos aimed = BlockMinoTable.Client.getCursorPickedGame();
        boolean pointed = deckHot || mc.currentScreen == null && aimed != null
            && aimed.equals(table.getBlockPos())
            && BlockMinoTable.Client.isCursorHittingPile();
        if (!myTurn || !pointed) return;
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        double cx0 = 0.335, cx1 = 0.665, cz0 = 0.245, cz1 = 0.755, w = 0.022;
        frame(t, cx0, cz0, cx1, cz1, w, y1 + 0.003, pulse);
        frame(t, cx0, cz0, cx1, cz1, w, y0 + 0.003, pulse * 0.7f);
        t.setColorRGBA_F(1f, 0.85f, 0.25f, pulse);
        for (int corner = 0; corner < 4; corner++) {
            double cx = corner == 0 || corner == 3 ? cx0 : cx1, cz = corner < 2 ? cz0 : cz1;
            double ox = cx < 0.5 ? -w : 0, oz = cz < 0.5 ? -w : 0;
            t.addVertex(cx + ox, y0, cz + oz + w / 2);
            t.addVertex(cx + ox + w, y0, cz + oz + w / 2);
            t.addVertex(cx + ox + w, y1, cz + oz + w / 2);
            t.addVertex(cx + ox, y1, cz + oz + w / 2);
        }
        t.draw();
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void frame(Tessellator t, double x0, double z0, double x1, double z1, double w, double y, float a) {
        t.setColorRGBA_F(1f, 0.85f, 0.25f, a);
        frameBand(t, x0 - w, z0 - w, x1 + w, z0, y);
        frameBand(t, x0 - w, z1, x1 + w, z1 + w, y);
        frameBand(t, x0 - w, z0, x0, z1, y);
        frameBand(t, x1, z0, x1 + w, z1, y);
    }

    private static void frameBand(Tessellator t, double x0, double z0, double x1, double z1, double y) {
        t.addVertex(x0, y, z0);
        t.addVertex(x0, y, z1);
        t.addVertex(x1, y, z1);
        t.addVertex(x1, y, z0);
    }

    private static final float[] JITTER = new float[512 * 3];
    static {
        Random r = new Random(1);
        for (int i = 0; i < JITTER.length; i++) JITTER[i] = r.nextFloat();
    }

    private static float jitter(int index, int axis) {
        return JITTER[(index & 511) * 3 + axis];
    }

    private static final Map<BlockEntityMinoTable, Object[]> LABELS = new WeakHashMap<>();

    private static String topLabel(BlockEntityMinoTable table, CardGame game) {
        Object[] cached = LABELS.get(table);
        if (cached != null && cached[0] == game.topCard
            && cached[1] == game.currentColor
            && (Boolean) cached[2] == game.darkSide) return (String) cached[3];
        CardFace top = game.topFace();
        IChatComponent component = top.getDisplayName();
        if (top.isWild() || game.currentColor != top.color) {
            component.appendText(" → ")
                .appendSibling(Chat.translatable(game.currentColor.langKey()));
        }
        String text = component.getFormattedText();
        LABELS.put(table, new Object[] { game.topCard, game.currentColor, game.darkSide, text });
        return text;
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
