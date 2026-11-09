package com.hfstudio.minopp.render;

import java.util.Map;
import java.util.Random;
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

        GL11.glPushMatrix();
        GL11.glTranslated(0.5, 0.94, 0.5);
        GL11.glScalef(0.5f, 0.4f, 0.5f);
        GL11.glRotatef(-90, 1, 0, 0);
        int layers = com.hfstudio.minopp.block.BlockMinoTable.deckLayers(game.deck.size());
        float layerH = com.hfstudio.minopp.block.BlockMinoTable.DECK_LAYER_H;
        // one solid block of a fixed height: the pile always looks the same, however many cards are left
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

        // the color picker faces the chooser's seat, for the chooser and for everyone watching
        EntityLivingBase viewer = Minecraft.getMinecraft().renderViewEntity;
        com.hfstudio.minopp.util.BlockPos pos = blockEntity.getBlockPos();
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
                com.hfstudio.minopp.game.CardColor.forSide(game.darkSide),
                blockEntity.choosingRing,
                blockEntity.choosingHover,
                blockEntity.choosingSeenAt);
        }

        renderLabel(topLabel(blockEntity, game), 1.5, 0.94 + 0.17, 1.5, 0.009f);
        TableFx.render(blockEntity, game);

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

    private static final CardBatch BATCH = new CardBatch();

    /** From the table's center toward a player's seat, or null when they are not seated in this game. */
    static double[] seatDir(BlockEntityMinoTable table, CardGame game, java.util.UUID uuid) {
        if (uuid == null) return null;
        for (com.hfstudio.minopp.game.CardPlayer p : game.players) {
            if (!p.uuid.equals(uuid)) continue;
            com.hfstudio.minopp.util.Vec3d s = table.getSeatLocalPos(p.seat);
            double dx = s.x - 1.5, dz = s.z - 1.5;
            return dx * dx + dz * dz < 1e-6 ? null : new double[] { dx, dz };
        }
        return null;
    }

    /** The viewer's own table's draw pile on screen last frame {minX, minY, maxX, maxY, cx, cy}, or null. */
    private static float[] deckBounds;
    private static long deckBoundsAt;
    /** Set by the hand screen while the mouse is over the draw pile. */
    public static boolean deckHot;

    public static float[] deckOnScreen() {
        return System.currentTimeMillis() - deckBoundsAt < 500 && deckBounds != null
            ? new float[] { deckBounds[4], deckBounds[5] }
            : null;
    }

    public static boolean isOverDeck(int mouseX, int mouseY) {
        float[] b = System.currentTimeMillis() - deckBoundsAt < 500 ? deckBounds : null;
        return b != null && mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
    }

    // the draw pile glows gold while you could draw from it and are pointing at it
    private static void deckHighlight(BlockEntityMinoTable table, CardGame game, int layers, float layerH) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        com.hfstudio.minopp.game.CardPlayer me = game
            .deAmputate(com.hfstudio.minopp.item.ItemHandCards.getCardPlayer(mc.thePlayer));
        if (me == null) return;
        double deckH = layers * layerH * 0.4, x0 = 0.24, x1 = 0.76, z0 = 0.24, z1 = 0.76, y0 = 0.94,
            y1 = 0.94 + deckH + 0.03;
        float[] b = ColorPicker3D.projectBoxNow(x0, y0, z0, x1, y1, z1);
        if (b != null) {
            deckBounds = b;
            deckBoundsAt = System.currentTimeMillis();
        }
        boolean myTurn = game.players.indexOf(me) == game.currentPlayerIndex && !game.awaitingCutIn;
        com.hfstudio.minopp.util.BlockPos aimed = com.hfstudio.minopp.block.BlockMinoTable.Client.getCursorPickedGame();
        boolean pointed = deckHot || mc.currentScreen == null && aimed != null
            && aimed.equals(table.getBlockPos())
            && com.hfstudio.minopp.block.BlockMinoTable.Client.isCursorHittingPile();
        if (!myTurn || !pointed) return;
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(1f, 0.82f, 0.2f, 0.18f * pulse);
        double[][] faces = { { x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0 },
            { x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0 }, { x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 },
            { x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 }, { x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1 } };
        for (double[] f : faces) for (int i = 0; i < 12; i += 3) t.addVertex(f[i], f[i + 1], f[i + 2]);
        t.draw();
        GL11.glLineWidth(3f);
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(1f, 0.85f, 0.25f, pulse);
        double[][] corners = { { x0, z0 }, { x1, z0 }, { x1, z1 }, { x0, z1 } };
        for (int i = 0; i < 4; i++) {
            double[] c = corners[i], n = corners[(i + 1) % 4];
            t.addVertex(c[0], y1, c[1]);
            t.addVertex(n[0], y1, n[1]);
            t.addVertex(c[0], y0 + 0.002, c[1]);
            t.addVertex(n[0], y0 + 0.002, n[1]);
            t.addVertex(c[0], y0, c[1]);
            t.addVertex(c[0], y1, c[1]);
        }
        t.draw();
        GL11.glLineWidth(1f);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
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
