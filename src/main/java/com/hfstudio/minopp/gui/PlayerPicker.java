package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Grid of player tiles (head, name, hand size) for choosing targets; the look follows the hand swap screen of the
 * original mod, but scales to 20 players.
 */
@SideOnly(Side.CLIENT)
public class PlayerPicker {

    public static final int TILE_W = 52;
    public static final int TILE_H = 64;
    private static final int GAP = 8;
    private static final int HEAD = 18;

    private final List<CardPlayer> players;
    private final List<Integer> handSizes;
    private final int maxSelected;
    public final List<UUID> selected = new ArrayList<>();
    private int x0, y0, cols;

    public PlayerPicker(List<CardPlayer> players, List<Integer> handSizes, int maxSelected) {
        this.players = players;
        this.handSizes = handSizes;
        this.maxSelected = Math.max(1, maxSelected);
    }

    public void layout(int cx, int top, int maxWidth) {
        cols = Math.max(1, Math.min(players.size(), (maxWidth + GAP) / (TILE_W + GAP)));
        int totalW = cols * TILE_W + (cols - 1) * GAP;
        x0 = cx - totalW / 2;
        y0 = top;
    }

    public int height() {
        int rows = (players.size() + cols - 1) / Math.max(1, cols);
        return rows * TILE_H + Math.max(0, rows - 1) * GAP;
    }

    private int tileX(int i) {
        return x0 + (i % cols) * (TILE_W + GAP);
    }

    private int tileY(int i) {
        return y0 + (i / cols) * (TILE_H + GAP);
    }

    public static ResourceLocation skinOf(UUID uuid) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer levelPlayer = WorldUtil.getPlayerByUUID(mc.theWorld, uuid);
        if (levelPlayer instanceof AbstractClientPlayer clientPlayer) return clientPlayer.getLocationSkin();
        Entity e = WorldUtil.getEntityByUUID(mc.theWorld, uuid);
        if (e instanceof EntityAutoPlayer bot && bot.clientSkinLocation != null) return bot.clientSkinLocation;
        return AbstractClientPlayer.locationStevePng;
    }

    public void draw(FontRenderer font, int mouseX, int mouseY) {
        for (int i = 0; i < players.size(); i++) {
            CardPlayer p = players.get(i);
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
            int hand = i < handSizes.size() ? handSizes.get(i) : 0;
            // pips like the original swap screen, capped
            int pips = Math.min(hand, 8);
            for (int k = 0; k < pips; k++) {
                int px = x + 6 + (k % 4) * 10, py = y + HEAD + 18 + (k / 4) * 9;
                GuiDraw.fill(px, py, px + 7, py + 7, sel ? 0xFFc9a84c : 0xFF555555);
            }
            if (hand > 8) GuiDraw.drawString(font, "+" + (hand - 8), x + TILE_W - 18, y + TILE_H - 10, 0xFF888888);
        }
    }

    /** Returns true if a tile was clicked. */
    public boolean click(int mouseX, int mouseY) {
        for (int i = 0; i < players.size(); i++) {
            int x = tileX(i), y = tileY(i);
            if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y - 4 && mouseY < y + TILE_H) {
                UUID id = players.get(i).uuid;
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
