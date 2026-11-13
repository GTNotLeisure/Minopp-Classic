package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.ColorPicker3D;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.PlayerIdentity;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RevealedHandTargets {

    private static final List<CardPlayer> players = new ArrayList<>();
    private static final Map<UUID, Entity> entities = new HashMap<>();
    private static final Map<UUID, float[]> worldBounds = new HashMap<>();
    private static final Map<UUID, int[]> heads = new HashMap<>();
    private static CardGame shownGame;
    private static BlockPos tablePos;
    private static UUID hovered;
    private static long expires, entityTick;
    private static int page, pageSize, left, top, panelWidth, panelHeight;

    private RevealedHandTargets() {}

    public static void beginFrame() {
        worldBounds.clear();
        heads.clear();
        panelHeight = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || ItemHandCards.getHandCardGamePos(mc.thePlayer) == null) {
            players.clear();
            entities.clear();
            shownGame = null;
            tablePos = null;
            hovered = null;
        }
    }

    private static boolean refresh(BlockEntityMinoTable table) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || table.game == null) return false;
        UUID viewer = PlayerIdentity.of(mc.thePlayer);
        long now = System.currentTimeMillis();
        if (shownGame != table.game || now >= expires) {
            if (shownGame == null || shownGame.id != table.game.id) page = 0;
            shownGame = table.game;
            tablePos = table.getBlockPos();
            players.clear();
            entities.clear();
            expires = Long.MAX_VALUE;
            for (CardPlayer player : shownGame.players) {
                if (!RevealedHandsScreen.canView(shownGame, viewer, player.uuid)) continue;
                players.add(player);
                expires = Math.min(expires, player.handVisibleUntil);
            }
            entityTick = Long.MIN_VALUE;
        }
        return !players.isEmpty();
    }

    public static void renderWorld(BlockEntityMinoTable table, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof HandScreen) || mc.thePlayer == null
            || !table.getBlockPos()
                .equals(ItemHandCards.getHandCardGamePos(mc.thePlayer))
            || !refresh(table)) return;
        long tick = mc.theWorld.getTotalWorldTime();
        if (entityTick != tick) {
            entities.clear();
            for (CardPlayer player : players) {
                Entity entity = WorldUtil.getEntityByUUID(mc.theWorld, player.uuid);
                if (entity != null && entity.isEntityAlive()) entities.put(player.uuid, entity);
            }
            entityTick = tick;
        }
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        for (Map.Entry<UUID, Entity> entry : entities.entrySet()) {
            Entity entity = entry.getValue();
            if (entity.isDead || entity.worldObj != mc.theWorld) continue;
            double dx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks
                - entity.posX
                - table.xCoord;
            double dy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks
                - entity.posY
                - table.yCoord;
            double dz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks
                - entity.posZ
                - table.zCoord;
            AxisAlignedBB box = entity.boundingBox.expand(0.08, 0.04, 0.08)
                .getOffsetBoundingBox(dx, dy, dz);
            float[] bounds = ColorPicker3D.projectBoxNow(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (bounds == null) continue;
            worldBounds.put(entry.getKey(), bounds);
            boolean hot = entry.getKey()
                .equals(hovered);
            GL11.glColor4f(hot ? 1f : 0.4f, hot ? 0.86f : 0.75f, hot ? 0.3f : 1f, hot ? 0.95f : 0.45f);
            GL11.glLineWidth(hot ? 2f : 1f);
            RenderGlobal.drawOutlinedBoundingBox(box, -1);
        }
        GL11.glPopAttrib();
    }

    public static void drawHud(BlockEntityMinoTable table, int width, int height, int y, int mouseX, int mouseY) {
        if (!refresh(table)) return;
        Minecraft mc = Minecraft.getMinecraft();
        int columns = Math.max(1, Math.min(10, (width - 80) / 28));
        pageSize = columns * 2;
        int pages = (players.size() + pageSize - 1) / pageSize;
        page = Math.min(page, pages - 1);
        int from = page * pageSize, count = Math.min(pageSize, players.size() - from);
        int rows = (count + columns - 1) / columns;
        panelWidth = Math.min(columns, count) * 28 + 36;
        panelHeight = rows * 32;
        left = (width - panelWidth) / 2;
        top = y;
        UiDraw.roundRect(left, top - 3, left + panelWidth, top + panelHeight, 4, 0x90000000);
        for (int i = 0; i < count; i++) {
            CardPlayer player = players.get(from + i);
            int inRow = Math.min(columns, count - i / columns * columns);
            int x = (width - inRow * 28) / 2 + i % columns * 28 + 4, hy = top + i / columns * 32;
            boolean hot = mouseX >= x - 2 && mouseX < x + 22 && mouseY >= hy - 2 && mouseY < hy + 29;
            if (hot) UiDraw.roundRect(x - 2, hy - 2, x + 22, hy + 29, 3, 0xB02A7FD4);
            GuiDraw.drawPlayerHead(PlayerPicker.skinOf(player.uuid), x, hy, 20);
            UiDraw
                .fittedCentered(mc.fontRenderer, Integer.toString(player.hand.size()), x + 10, hy + 21, 26, 0xFFFFFFFF);
            heads.put(player.uuid, new int[] { x - 2, hy - 2, x + 22, hy + 29 });
        }
        if (pages > 1) {
            GuiDraw.drawString(mc.fontRenderer, "<", left + 4, top + 6, page > 0 ? 0xFFFFFFFF : 0xFF666666);
            GuiDraw.drawString(
                mc.fontRenderer,
                ">",
                left + panelWidth - 10,
                top + 6,
                page + 1 < pages ? 0xFFFFFFFF : 0xFF666666);
        }
        if (mouseX < 0 || mouseY < 0) {
            hovered = null;
            return;
        }
        hovered = hitHud(mouseX, mouseY);
        if (hovered == null && !overHud(mouseX, mouseY)) hovered = hitWorld(mouseX, mouseY);
        if (hovered == null) return;
        CardPlayer player = shownGame.deAmputate(hovered);
        if (player == null) return;
        List<String> tip = new ArrayList<>();
        tip.add(
            Chat.translatable("gui.minopp.reveal.player", player.name, player.hand.size())
                .getFormattedText());
        tip.add(
            Chat.translatable("gui.minopp.reveal.click")
                .getFormattedText());
        if (player.handVisibleUntil < Long.MAX_VALUE)
            tip.add(Math.max(0, (player.handVisibleUntil - System.currentTimeMillis() + 999) / 1000) + "s");
        UiDraw.tooltip(mc.fontRenderer, tip, mouseX, mouseY, width, height);
    }

    public static int hudHeight(BlockEntityMinoTable table, int width) {
        if (!refresh(table)) return 0;
        int columns = Math.max(1, Math.min(10, (width - 80) / 28));
        return Math.min(2, (players.size() + columns - 1) / columns) * 32;
    }

    private static UUID hitHud(int x, int y) {
        for (Map.Entry<UUID, int[]> entry : heads.entrySet()) {
            int[] b = entry.getValue();
            if (x >= b[0] && x < b[2] && y >= b[1] && y < b[3]) return entry.getKey();
        }
        return null;
    }

    private static UUID hitWorld(int x, int y) {
        UUID best = null;
        double nearest = Double.MAX_VALUE;
        for (Map.Entry<UUID, float[]> entry : worldBounds.entrySet()) {
            float[] b = entry.getValue();
            if (x < b[0] || x > b[2] || y < b[1] || y > b[3]) continue;
            Entity entity = entities.get(entry.getKey());
            if (entity == null) continue;
            double distance = entity
                .getDistanceSq(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
            if (distance < nearest) {
                nearest = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean overHud(int x, int y) {
        return panelHeight > 0 && x >= left && x < left + panelWidth && y >= top - 3 && y < top + panelHeight;
    }

    public static boolean clickHud(GuiScreen parent, int x, int y) {
        if (!overHud(x, y)) return false;
        UUID target = hitHud(x, y);
        if (target != null) open(parent, target);
        else if (x < left + 16) scrollHud(x, y, -1);
        else if (x >= left + panelWidth - 16) scrollHud(x, y, 1);
        return true;
    }

    public static boolean clickWorld(GuiScreen parent, int x, int y) {
        UUID target = hitWorld(x, y);
        return target != null && open(parent, target);
    }

    private static boolean open(GuiScreen parent, UUID target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || shownGame == null) return false;
        BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, tablePos);
        if (table == null || table.game == null
            || table.game.id != shownGame.id
            || !RevealedHandsScreen.canView(table.game, PlayerIdentity.of(mc.thePlayer), target)) return false;
        mc.displayGuiScreen(new RevealedHandsScreen(parent, tablePos, table.game.id, target));
        return true;
    }

    public static boolean scrollHud(int x, int y, int direction) {
        if (!overHud(x, y)) return false;
        int pages = Math.max(1, (players.size() + pageSize - 1) / Math.max(1, pageSize));
        page = Math.max(0, Math.min(pages - 1, page + direction));
        return true;
    }
}
