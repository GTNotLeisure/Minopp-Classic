package com.hfstudio.minopp.gui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.PlayerIdentity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class PlayerPicker {

    public static final int TILE_W = 52;
    public static final int TILE_H = 64;
    private static final int GAP = 8;
    private static final int HEAD = 18;
    private static final Map<UUID, WeakReference<Entity>> SKIN_ENTITIES = new HashMap<>();
    private static WeakReference<World> skinWorld = new WeakReference<>(null);
    private static long skinScanTick = Long.MIN_VALUE;

    public static void clearSkinCache() {
        SKIN_ENTITIES.clear();
        skinWorld.clear();
        skinScanTick = Long.MIN_VALUE;
    }

    private final List<CardPlayer> players;
    private final List<Integer> handSizes;
    private final int maxSelected;
    public final List<UUID> selected = new ArrayList<>();
    private int x0, y0, cols, visibleRows, page;
    private final List<GuiButton> radialTiles = new ArrayList<>();
    private RadialChoiceLayout radialLayout;
    private int radialCapacity;
    private int[] radialBounds;

    public PlayerPicker(List<CardPlayer> players, List<Integer> handSizes, int maxSelected) {
        this.players = players;
        this.handSizes = handSizes;
        this.maxSelected = Math.max(1, maxSelected);
    }

    public void layout(int cx, int top, int maxWidth) {
        layout(cx, top, maxWidth, Integer.MAX_VALUE);
    }

    public void layout(int cx, int top, int maxWidth, int maxHeight) {
        radialBounds = null;
        radialLayout = null;
        cols = Math.max(1, Math.min(players.size(), (maxWidth + GAP) / (TILE_W + GAP)));
        visibleRows = Math.max(
            1,
            Math.min(
                (players.size() + cols - 1) / cols,
                (int) Math.min(Integer.MAX_VALUE, ((long) maxHeight + GAP) / (TILE_H + GAP))));
        page = Math.min(page, pages() - 1);
        int totalW = cols * TILE_W + (cols - 1) * GAP;
        x0 = cx - totalW / 2;
        y0 = top;
    }

    public void layoutRadial(int centerX, int centerY, int left, int top, int right, int bottom) {
        radialBounds = new int[] { centerX, centerY, left, top, right, bottom };
        int requestedPage = page;
        for (int count = Math.min(8, Math.max(1, players.size())); count >= 1; count--) {
            radialCapacity = count;
            page = Math.min(requestedPage, pages() - 1);
            radialTiles.clear();
            int start = page * count;
            for (int i = start; i < start + count; i++) radialTiles.add(new GuiButton(i, 0, 0, TILE_W, TILE_H, ""));
            radialLayout = RadialChoiceLayout.arrangeFixed(radialTiles, centerX, centerY, left, top, right, bottom);
            if (radialLayout != null) {
                int shown = Math.min(count, players.size() - start);
                if (shown < count) {
                    radialTiles.subList(Math.max(0, shown), count)
                        .clear();
                    radialLayout = RadialChoiceLayout
                        .arrangeFixed(radialTiles, centerX, centerY, left, top, right, bottom);
                }
                return;
            }
        }
    }

    public int top() {
        if (radialLayout != null) return radialTiles.stream()
            .mapToInt(button -> button.yPosition)
            .min()
            .orElse(0);
        return y0;
    }

    public int height() {
        if (radialLayout != null) return radialTiles.stream()
            .mapToInt(button -> button.yPosition + button.height)
            .max()
            .orElse(0) - top();
        return visibleRows * TILE_H + Math.max(0, visibleRows - 1) * GAP;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return Math.max(1, (players.size() + capacity() - 1) / capacity());
    }

    public void scroll(int direction) {
        page = Math.max(0, Math.min(pages() - 1, page + direction));
        if (radialBounds != null) layoutRadial(
            radialBounds[0],
            radialBounds[1],
            radialBounds[2],
            radialBounds[3],
            radialBounds[4],
            radialBounds[5]);
    }

    private int capacity() {
        if (radialBounds != null) return Math.max(1, radialCapacity);
        return Math.max(1, cols * visibleRows);
    }

    private int tileX(int i) {
        if (radialLayout != null) return radialTiles.get(i).xPosition;
        return x0 + (i % cols) * (TILE_W + GAP);
    }

    private int tileY(int i) {
        if (radialLayout != null) return radialTiles.get(i).yPosition;
        return y0 + (i / cols) * (TILE_H + GAP);
    }

    public static ResourceLocation skinOf(UUID uuid) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || uuid == null) return AbstractClientPlayer.locationStevePng;
        long tick = world.getTotalWorldTime();
        if (skinWorld.get() != world || skinScanTick != tick) {
            SKIN_ENTITIES.clear();
            skinWorld = new WeakReference<>(world);
            skinScanTick = tick;
            for (Object object : world.loadedEntityList) {
                if (object instanceof EntityAutoPlayer bot)
                    SKIN_ENTITIES.put(bot.getUniqueID(), new WeakReference<>(bot));
            }
            for (EntityPlayer player : world.playerEntities) {
                SKIN_ENTITIES.put(PlayerIdentity.of(player), new WeakReference<>(player));
            }
        }
        WeakReference<Entity> reference = SKIN_ENTITIES.get(uuid);
        Entity e = reference == null ? null : reference.get();
        if (e == null || e.isDead || e.worldObj != world) return AbstractClientPlayer.locationStevePng;
        if (e instanceof AbstractClientPlayer clientPlayer) return clientPlayer.getLocationSkin();
        if (e instanceof EntityAutoPlayer bot && bot.clientSkinLocation != null) return bot.clientSkinLocation;
        return AbstractClientPlayer.locationStevePng;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY) {
        if (radialLayout != null) radialLayout.draw();
        int start = page * capacity();
        for (int i = 0; i < capacity() && start + i < players.size(); i++) {
            CardPlayer p = players.get(start + i);
            boolean sel = selected.contains(p.uuid);
            int x = tileX(i), y = tileY(i);
            boolean hover = mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
            if (sel) y -= 4;
            GuiDraw.fill(x + 3, y + 3, x + TILE_W + 3, y + TILE_H + 3, 0x55000000);
            GuiDraw.fill(x, y, x + TILE_W, y + TILE_H, sel ? 0xFFc9a84c : (hover ? 0xFF666666 : 0xFF3a3a3a));
            GuiDraw.fill(x + 1, y + 1, x + TILE_W - 1, y + TILE_H - 1, 0xFF1e1e1e);
            GuiDraw.drawPlayerHead(skinOf(p.uuid), x + TILE_W / 2 - HEAD / 2, y + 5, HEAD);
            String name = p.name.length() > 9 ? p.name.substring(0, 8) + "." : p.name;
            GL11.glPushMatrix();
            GL11.glTranslatef(x + TILE_W / 2f, y + HEAD + 9, 0);
            GL11.glScalef(0.75f, 0.75f, 1);
            GuiDraw.drawCenteredString(font, name, 0, 0, 0xFFCCCCCC);
            GL11.glPopMatrix();
            int hand = start + i < handSizes.size() ? handSizes.get(start + i) : 0;
            int pips = Math.min(hand, 8);
            for (int k = 0; k < pips; k++) {
                int px = x + 6 + (k % 4) * 10, py = y + HEAD + 18 + (k / 4) * 9;
                GuiDraw.fill(px, py, px + 7, py + 7, sel ? 0xFFc9a84c : 0xFF555555);
            }
            if (hand > 8) GuiDraw.drawString(font, "+" + (hand - 8), x + TILE_W - 18, y + TILE_H - 10, 0xFF888888);
        }
    }

    public boolean click(int mouseX, int mouseY) {
        int start = page * capacity();
        for (int i = 0; i < capacity() && start + i < players.size(); i++) {
            int x = tileX(i), y = tileY(i);
            if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y - 4 && mouseY < y + TILE_H) {
                UUID id = players.get(start + i).uuid;
                if (selected.contains(id)) {
                    selected.remove(id);
                } else {
                    if (selected.size() >= maxSelected) selected.remove(0);
                    selected.add(id);
                }
                return true;
            }
        }
        return false;
    }
}
