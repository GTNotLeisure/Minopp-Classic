package com.hfstudio.minopp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Direction;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SwapHandSelectionScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final CardPlayer player;
    private final boolean shout;
    private final Card handCard;
    private final String title;

    private Direction frontDir = null;
    private Direction leftDir = null;
    private Direction rightDir = null;

    private int selectedSlot = -1;

    // card dimensions
    private static final int CARD_W = 58;
    private static final int CARD_H = 96;
    private static final int CARD_GAP = 14;
    private static final int TAB_H = 14;
    private static final int SELECTED_LIFT = 8;

    // head
    private static final int HEAD_SIZE = 20;

    // pips
    private static final int PIP_W = 7;
    private static final int PIP_H = 10;
    private static final int PIP_GAP = 2;
    private static final int PIP_COLS = 4;
    private static final int PIP_CAP = 8;

    public SwapHandSelectionScreen(BlockPos gamePos, CardPlayer player, Card handCard, boolean shout, CardGame game) {
        this.title = Chat.translatable("gui.minopp.swap_card_selection.title")
            .getFormattedText();
        this.gamePos = gamePos;
        this.player = player;
        this.shout = shout;
        this.handCard = handCard;
    }

    private BlockEntityMinoTable getTable() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    private static ResourceLocation getPlayerSkin(BlockEntityMinoTable tableEntity, Direction direction) {
        CardPlayer p = tableEntity.players.get(direction);
        if (p == null) return null;
        EntityPlayer levelPlayer = WorldUtil.getPlayerByUUID(Minecraft.getMinecraft().theWorld, p.uuid);
        if (levelPlayer instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getLocationSkin();
        }
        return AbstractClientPlayer.locationStevePng;
    }

    private void calculatePlayerDirections(BlockEntityMinoTable tableEntity) {
        Direction myDir = tableEntity.getPlayerDirection(player.uuid);
        if (myDir != null) {
            int idx = BlockEntityMinoTable.PLAYER_ORDER.indexOf(myDir);
            int size = BlockEntityMinoTable.PLAYER_ORDER.size();
            this.frontDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 2) % size);
            this.leftDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 1) % size);
            this.rightDir = BlockEntityMinoTable.PLAYER_ORDER.get((idx + 3) % size);
        } else {
            this.frontDir = this.leftDir = this.rightDir = null;
        }
    }

    private Direction[] getValidDirs(BlockEntityMinoTable tableEntity) {
        Direction[] all = { leftDir, frontDir, rightDir };
        int count = 0;
        for (Direction d : all) if (d != null && tableEntity.players.get(d) != null) count++;
        Direction[] valid = new Direction[count];
        int i = 0;
        for (Direction d : all) if (d != null && tableEntity.players.get(d) != null) valid[i++] = d;
        return valid;
    }

    private int cardLeft(int slot, int cx, int count) {
        int totalW = count * CARD_W + (count - 1) * CARD_GAP;
        return cx - totalW / 2 + slot * (CARD_W + CARD_GAP);
    }

    private int cardTop(int cy, boolean lifted) {
        return cy - (CARD_H + TAB_H) / 2 - (lifted ? SELECTED_LIFT : 0);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        BlockEntityMinoTable tableEntity = getTable();
        if (tableEntity == null) return;
        calculatePlayerDirections(tableEntity);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        BlockEntityMinoTable tableEntity = getTable();
        if (tableEntity != null) {
            Direction[] dirs = getValidDirs(tableEntity);
            int cx = width / 2;
            int cy = height / 2;

            // card slot clicks
            for (int i = 0; i < dirs.length; i++) {
                boolean lifted = selectedSlot == i;
                int x = cardLeft(i, cx, dirs.length);
                int y = cardTop(cy, lifted);
                if (mouseX >= x && mouseX <= x + CARD_W && mouseY >= y && mouseY <= y + CARD_H + TAB_H) {
                    selectedSlot = i;
                    return;
                }
            }

            // confirm and cancel buttons
            int btnY = cy + (CARD_H + TAB_H) / 2 + 10;
            int btnW = 80;
            int confirmX = cx - btnW / 2;
            int btnH = 14;

            if (mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                if (selectedSlot != -1 && selectedSlot < dirs.length) {
                    CardPlayer target = tableEntity.players.get(dirs[selectedSlot]);
                    if (target != null) {
                        Direction fromDir = tableEntity.getPlayerDirection(player.uuid);
                        Direction toDir = tableEntity.getPlayerDirection(target.uuid);
                        if (fromDir != null && toDir != null && tableEntity.game != null) {
                            CardPlayer fromP = tableEntity.game.deAmputate(player.uuid);
                            CardPlayer toP = tableEntity.game.deAmputate(target.uuid);
                            BlockEntityMinoTable.hideHandUntil.put(
                                player.uuid,
                                System.currentTimeMillis() + BlockEntityMinoTable.HandSwapAnimation.DURATION_MS);
                            int fromCount = fromP != null ? fromP.hand.size() : 5;
                            int toCount = toP != null ? toP.hand.size() : 5;
                            BlockEntityMinoTable.activeAnimations.add(
                                new BlockEntityMinoTable.HandSwapAnimation(
                                    BlockEntityMinoTable.getSeatLocalPos(fromDir),
                                    BlockEntityMinoTable.getSeatLocalPos(toDir),
                                    fromCount));
                            BlockEntityMinoTable.activeAnimations.add(
                                new BlockEntityMinoTable.HandSwapAnimation(
                                    BlockEntityMinoTable.getSeatLocalPos(toDir),
                                    BlockEntityMinoTable.getSeatLocalPos(fromDir),
                                    toCount));
                        }
                        C2SPlayCardPacket.Client.sendSwapHandC2S(gamePos, player, handCard, null, shout, target.uuid);
                        mc.displayGuiScreen(null);
                        return;
                    }
                }
                return;
            }

            int cancelY = btnY + btnH + 6;
            if (mouseX >= confirmX && mouseX <= confirmX + btnW && mouseY >= cancelY && mouseY <= cancelY + btnH) {
                mc.displayGuiScreen(null);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        BlockEntityMinoTable tableEntity = getTable();
        if (tableEntity == null) {
            super.drawScreen(mouseX, mouseY, partialTick);
            return;
        }

        calculatePlayerDirections(tableEntity);

        int cx = width / 2;
        int cy = height / 2;

        // title
        int lineHeight = fontRendererObj.FONT_HEIGHT;
        int titleY = cy - (CARD_H + TAB_H) / 2 - SELECTED_LIFT - 28;
        int titlePadX = 8;
        int titlePadY = 4;
        int titleW = fontRendererObj.getStringWidth(title);
        int titleBgX1 = cx - titleW / 2 - titlePadX;
        int titleBgX2 = cx + titleW / 2 + titlePadX;
        int titleBgY1 = titleY - titlePadY;
        int titleBgY2 = titleY + lineHeight + titlePadY;
        GuiDraw.fill(titleBgX1, titleBgY1, titleBgX2, titleBgY2, 0xFF3a3a3a);
        GuiDraw.fill(titleBgX1 + 1, titleBgY1 + 1, titleBgX2 - 1, titleBgY2 - 1, 0xFF1e1e1e);
        GuiDraw.drawCenteredString(fontRendererObj, title, cx, titleY, 0xFFCCCCCC);

        // divider
        int stackCy = titleY + lineHeight + 12;
        int cw = 8, ch = 11;
        for (int s = 2; s >= 0; s--) {
            int sx = cx - cw / 2 + s * 2;
            int sy = stackCy - ch / 2 - s;
            int cardBorder = s == 0 ? 0xFF666666 : 0xFF444444;
            int cardFill = s == 0 ? 0xFF2a2a2a : 0xFF1e1e1e;
            GuiDraw.fill(sx, sy, sx + cw, sy + ch, cardBorder);
            GuiDraw.fill(sx + 1, sy + 1, sx + cw - 1, sy + ch - 1, cardFill);
        }
        GuiDraw.fill(cx - 55, stackCy, cx - 10, stackCy + 1, 0xFF444444);
        GuiDraw.fill(cx + 10, stackCy, cx + 55, stackCy + 1, 0xFF444444);

        Direction[] dirs = getValidDirs(tableEntity);

        for (int i = 0; i < dirs.length; i++) {
            CardPlayer target = tableEntity.players.get(dirs[i]);
            if (target == null) continue;

            boolean selected = selectedSlot == i;
            boolean hovered = !selected && mouseX >= cardLeft(i, cx, dirs.length)
                && mouseX <= cardLeft(i, cx, dirs.length) + CARD_W
                && mouseY >= cardTop(cy, false)
                && mouseY <= cardTop(cy, false) + CARD_H + TAB_H;
            boolean lifted = selected || hovered;

            int x = cardLeft(i, cx, dirs.length);
            int y = cardTop(cy, lifted);

            // shadow
            GuiDraw.fill(x + 3, y + 3, x + CARD_W + 3, y + CARD_H + TAB_H + 3, 0x55000000);

            // card border
            int borderCol = selected ? 0xFFc9a84c : (hovered ? 0xFF666666 : 0xFF3a3a3a);
            GuiDraw.fill(x, y, x + CARD_W, y + CARD_H, borderCol);
            // card fill
            GuiDraw.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, 0xFF1e1e1e);

            // inner border
            GuiDraw.fill(x + 4, y + 4, x + CARD_W - 4, y + 5, 0xFF2a2a2a);
            GuiDraw.fill(x + 4, y + CARD_H - 5, x + CARD_W - 4, y + CARD_H - 4, 0xFF2a2a2a);
            GuiDraw.fill(x + 4, y + 4, x + 5, y + CARD_H - 4, 0xFF2a2a2a);
            GuiDraw.fill(x + CARD_W - 5, y + 4, x + CARD_W - 4, y + CARD_H - 4, 0xFF2a2a2a);

            // head
            int headX = x + CARD_W / 2 - HEAD_SIZE / 2;
            int headY = y + 8;
            ResourceLocation skin = getPlayerSkin(tableEntity, dirs[i]);
            if (skin != null) {
                GuiDraw.drawPlayerHead(skin, headX, headY, HEAD_SIZE);
            } else {
                GuiDraw.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xFF444444);
            }

            // name
            int nameY = headY + HEAD_SIZE + 3;
            String name = target.name.length() > 10 ? target.name.substring(0, 9) + "." : target.name;
            GL11.glPushMatrix();
            GL11.glTranslatef(x + CARD_W / 2f, nameY, 0);
            GL11.glScalef(0.75f, 0.75f, 1.0f);
            GuiDraw.drawCenteredString(fontRendererObj, name, 0, 0, 0xFFCCCCCC);
            GL11.glPopMatrix();

            // pips
            CardPlayer gamePlayer = tableEntity.game != null ? tableEntity.game.deAmputate(target.uuid) : null;
            int handSize = gamePlayer != null ? gamePlayer.hand.size() : 0;
            int pipCount = Math.min(handSize, PIP_CAP);
            int pipAreaTop = nameY + lineHeight + 4;
            int pipColor = selected ? 0xFFc9a84c : 0xFF555555;

            for (int p = 0; p < pipCount; p++) {
                int col = p % PIP_COLS;
                int row = p / PIP_COLS;
                int pipsInThisRow = Math.min(PIP_COLS, pipCount - row * PIP_COLS);
                int rowW = pipsInThisRow * PIP_W + (pipsInThisRow - 1) * PIP_GAP;
                int rowStartX = x + CARD_W / 2 - rowW / 2;
                int px = rowStartX + col * (PIP_W + PIP_GAP);
                int py = pipAreaTop + row * (PIP_H + PIP_GAP);
                GuiDraw.fill(px, py, px + PIP_W, py + PIP_H, pipColor);
            }

            if (handSize > PIP_CAP) {
                int rows = (pipCount + PIP_COLS - 1) / PIP_COLS;
                int overflowY = pipAreaTop + rows * (PIP_H + PIP_GAP) + 1;
                GuiDraw.drawCenteredString(
                    fontRendererObj,
                    "+" + (handSize - PIP_CAP),
                    x + CARD_W / 2,
                    overflowY,
                    0xFF555555);
            }

            // tab
            int tabY = y + CARD_H;
            int tabBg = selected ? 0xFFc9a84c : 0xFF2a2a2a;
            int tabBorder = selected ? 0xFFc9a84c : 0xFF3a3a3a;
            GuiDraw.fill(x, tabY, x + CARD_W, tabY + TAB_H, tabBorder);
            GuiDraw.fill(x + 1, tabY + 1, x + CARD_W - 1, tabY + TAB_H - 1, tabBg);
            String tabText = selected ? "Selected" : "Swap";
            int tabTextCol = selected ? 0xFF1a1a1a : 0xFF666666;
            GuiDraw.drawCenteredString(
                fontRendererObj,
                tabText,
                x + CARD_W / 2,
                tabY + TAB_H / 2 - lineHeight / 2,
                tabTextCol);
        }

        // bottom buttons
        int btnY = cy + (CARD_H + TAB_H) / 2 + 10;
        int btnW = 80;
        int btnH = 14;
        int confirmX = cx - btnW / 2;
        boolean confirmHovered = mouseX >= confirmX && mouseX <= confirmX + btnW
            && mouseY >= btnY
            && mouseY <= btnY + btnH;
        boolean confirmActive = selectedSlot != -1;
        int confirmBorder = confirmActive ? (confirmHovered ? 0xFFc9a84c : 0xFF9a7d2e) : 0xFF3a3a3a;
        int confirmFill = confirmActive ? (confirmHovered ? 0xFF9a7d2e : 0xFF2a2a2a) : 0xFF1e1e1e;
        int confirmText = confirmActive ? 0xFFc9a84c : 0xFF444444;
        GuiDraw.fill(confirmX, btnY, confirmX + btnW, btnY + btnH, confirmBorder);
        GuiDraw.fill(confirmX + 1, btnY + 1, confirmX + btnW - 1, btnY + btnH - 1, confirmFill);
        GuiDraw.drawCenteredString(fontRendererObj, "Confirm Swap", cx, btnY + btnH / 2 - lineHeight / 2, confirmText);

        int cancelY = btnY + btnH + 6;
        int cancelX = cx - btnW / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX <= cancelX + btnW
            && mouseY >= cancelY
            && mouseY <= cancelY + btnH;
        GuiDraw.fill(cancelX, cancelY, cancelX + btnW, cancelY + btnH, cancelHovered ? 0xFF555555 : 0xFF3a3a3a);
        GuiDraw.fill(cancelX + 1, cancelY + 1, cancelX + btnW - 1, cancelY + btnH - 1, 0xFF1e1e1e);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            "Cancel",
            cx,
            cancelY + btnH / 2 - lineHeight / 2,
            cancelHovered ? 0xFFAAAAAA : 0xFF666666);

        super.drawScreen(mouseX, mouseY, partialTick);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
