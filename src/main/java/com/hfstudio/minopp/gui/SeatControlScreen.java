package com.hfstudio.minopp.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Seats around the table (up to 20, drawn on a ring like the 4 seats of the original screen), start / stop / reset,
 * and entry points to table settings and the card theme.
 */
@SideOnly(Side.CLIENT)
public class SeatControlScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final String title;

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation(
        "minecraft",
        "textures/blocks/planks_spruce.png");
    private static final int LARGE_BTN_WIDTH = 70;
    private static final int LARGE_BTN_HEIGHT = 20;
    private static final int MARGIN = 8;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;

    private GuiButton stopButton, startButton, leaveButton, settingsButton, themeButton;

    public SeatControlScreen(BlockPos gamePos) {
        this.title = Chat.translatable("gui.minopp.seats.title")
            .getFormattedText();
        this.gamePos = gamePos;
    }

    private BlockEntityMinoTable getTable() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;
        int by = yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT;

        stopButton = new GuiButton(
            0,
            xOff + MARGIN,
            by,
            LARGE_BTN_WIDTH,
            LARGE_BTN_HEIGHT,
            Chat.translatable("gui.minopp.seats.stop")
                .getFormattedText());
        startButton = new GuiButton(
            1,
            xOff + MARGIN + LARGE_BTN_WIDTH + 4,
            by,
            LARGE_BTN_WIDTH,
            LARGE_BTN_HEIGHT,
            Chat.translatable("gui.minopp.seats.start")
                .getFormattedText());
        leaveButton = new GuiButton(
            2,
            xOff + PANEL_WIDTH - MARGIN - LARGE_BTN_WIDTH,
            by,
            LARGE_BTN_WIDTH,
            LARGE_BTN_HEIGHT,
            Chat.translatable("gui.minopp.seats.reset")
                .getFormattedText());
        settingsButton = new GuiButton(
            3,
            xOff + PANEL_WIDTH - MARGIN - 90,
            yOff + 4,
            90,
            16,
            Chat.translatable("gui.minopp.seats.settings")
                .getFormattedText());
        themeButton = new GuiButton(4, xOff + MARGIN, yOff + 4, 110, 16, "");
        buttonList.add(stopButton);
        buttonList.add(startButton);
        buttonList.add(leaveButton);
        buttonList.add(settingsButton);
        buttonList.add(themeButton);
        stopButton.enabled = startButton.enabled = leaveButton.enabled = false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> {
                C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, false);
                mc.displayGuiScreen(null);
            }
            case 1 -> {
                C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, true);
                mc.displayGuiScreen(null);
            }
            case 2 -> {
                C2SSeatControlPacket.Client.sendResetSeatsC2S(gamePos);
                mc.displayGuiScreen(null);
            }
            case 3 -> mc.displayGuiScreen(new TableSettingsScreen(gamePos, this));
            case 4 -> ThemeManager.INSTANCE.cycle();
            default -> {}
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        BlockEntityMinoTable tableEntity = getTable();
        themeButton.displayString = Chat.translatable(
            "gui.minopp.theme",
            ThemeManager.INSTANCE.selectedInfo()
                .name())
            .getFormattedText();
        if (tableEntity != null) {
            startButton.enabled = tableEntity.game == null && tableEntity.getPlayersList()
                .size() >= 2;
            stopButton.enabled = tableEntity.game != null;
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(mc.thePlayer);
            leaveButton.enabled = tableEntity.game == null && tableEntity.getPlayersList()
                .contains(cardPlayer);

            int xOff = (width - PANEL_WIDTH) / 2;
            int yOff = (height - PANEL_HEIGHT) / 2;
            GuiDraw.fill(
                xOff + MARGIN,
                yOff + MARGIN,
                xOff + PANEL_WIDTH + MARGIN,
                yOff + PANEL_HEIGHT + MARGIN,
                0x99000000);
            GuiDraw.fill(xOff - 1, yOff - 1, xOff + PANEL_WIDTH + 1, yOff + PANEL_HEIGHT + 1, 0xCC000000);
            GuiDraw.blit(PANEL_TEXTURE, xOff, yOff, PANEL_WIDTH, PANEL_HEIGHT, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 16, 16);
            GuiDraw.fill(
                xOff,
                yOff + PANEL_HEIGHT - LARGE_BTN_HEIGHT - 2 * MARGIN + 4,
                xOff + PANEL_WIDTH,
                yOff + PANEL_HEIGHT,
                0xCC000000);
            GuiDraw.fill(xOff, yOff + 24, xOff + PANEL_WIDTH, yOff + 38, 0x99000000);
            int seatCount = tableEntity.seatCount();
            GuiDraw.drawCenteredString(
                fontRendererObj,
                title + " ("
                    + tableEntity.getPlayersList()
                        .size()
                    + "/"
                    + seatCount
                    + ")",
                width / 2,
                yOff + 27,
                0xFFAAAAAA);

            // table in the middle
            int cx = width / 2, cy = yOff + 38 + (PANEL_HEIGHT - 38 - LARGE_BTN_HEIGHT - 2 * MARGIN) / 2;
            GuiDraw.fill(cx - 22, cy - 22, cx + 22, cy + 22, 0xFF3E2723);
            GuiDraw.fill(cx - 19, cy - 19, cx + 19, cy + 19, 0xFF2E7D32);
            int radiusX = 110, radiusY = 58;
            for (int i = 0; i < seatCount; i++) {
                double a = BlockEntityMinoTable.seatAngle(i, seatCount);
                int sx = cx + (int) (Math.sin(a) * radiusX);
                int sy = cy - (int) (Math.cos(a) * radiusY);
                CardPlayer p = tableEntity.seats[i];
                String name = p == null ? "-" : p.name;
                float scale = seatCount > 8 ? 0.75f : 1f;
                int headSize = 9;
                GL11.glPushMatrix();
                GL11.glTranslatef(sx, sy, 0);
                GL11.glScalef(scale, scale, 1);
                int textW = fontRendererObj.getStringWidth(name) + (p != null ? headSize + 3 : 0);
                int left = -textW / 2;
                if (p != null) {
                    GuiDraw.drawPlayerHead(PlayerPicker.skinOf(p.uuid), left, -5, headSize);
                    left += headSize + 3;
                }
                GuiDraw.drawString(fontRendererObj, name, left, -4, p != null ? 0xFFFFFFFF : 0xFF777777);
                GL11.glPopMatrix();
            }
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
