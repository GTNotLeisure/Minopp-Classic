package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Direction;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SeatControlScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final String title;

    public SeatControlScreen(BlockPos gamePos) {
        this.title = Chat.translatable("gui.minopp.seats.title").getFormattedText();
        this.gamePos = gamePos;
    }

    // all gamerules which can be toggled at before a game
    private static boolean settingsOpen = false;
    private GuiButton settingsButton;

    private final List<GuiButton> ruleButtons = new ArrayList<>();
    private final List<String> ruleButtonKeys = new ArrayList<>();

    private static final String[][] RULES_LIST = {
        {BlockEntityMinoTable.RULE_JUMP_IN, "Allow Jump-In"},
        {BlockEntityMinoTable.RULE_STACKING, "Allow Stacking"},
        {BlockEntityMinoTable.RULE_SEVEN0, "Allow 7-0 Rules"}
    };

    private BlockEntityMinoTable getTable() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    private static ResourceLocation getPlayerSkin(BlockEntityMinoTable tableEntity, Direction direction) {
        CardPlayer player = tableEntity.players.get(direction);
        if (player == null) return null;
        EntityPlayer levelPlayer = WorldUtil.getPlayerByUUID(Minecraft.getMinecraft().theWorld, player.uuid);
        if (levelPlayer instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getLocationSkin();
        }
        return AbstractClientPlayer.locationStevePng;
    }

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("minecraft", "textures/blocks/planks_spruce.png");
    int LARGE_BTN_WIDTH = 70;
    int LARGE_BTN_HEIGHT = 20;
    int MARGIN = 8;

    int PANEL_HEIGHT = MARGIN + 6 + MARGIN
        + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT + MARGIN + 9 + MARGIN
        + MARGIN + LARGE_BTN_HEIGHT + MARGIN;
    int PANEL_WIDTH = 260;

    private GuiButton stopButton, startButton, leaveButton;

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;

        stopButton = new GuiButton(0, xOff + MARGIN, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT, LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT,
            Chat.translatable("gui.minopp.seats.stop").getFormattedText());
        stopButton.enabled = false;
        buttonList.add(stopButton);

        startButton = new GuiButton(1, xOff + MARGIN + LARGE_BTN_WIDTH + MARGIN, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT,
            LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT, Chat.translatable("gui.minopp.seats.start").getFormattedText());
        startButton.enabled = false;
        buttonList.add(startButton);

        leaveButton = new GuiButton(2, xOff + PANEL_WIDTH - MARGIN - LARGE_BTN_WIDTH, yOff + PANEL_HEIGHT - MARGIN - LARGE_BTN_HEIGHT,
            LARGE_BTN_WIDTH, LARGE_BTN_HEIGHT, Chat.translatable("gui.minopp.seats.reset").getFormattedText());
        leaveButton.enabled = false;
        buttonList.add(leaveButton);

        settingsButton = new GuiButton(3, xOff + PANEL_WIDTH - 20, yOff + 4, 16, 16, settingsOpen ? "<" : ">");
        buttonList.add(settingsButton);

        ruleButtons.clear();
        ruleButtonKeys.clear();
        if (settingsOpen && getTable() != null) {
            int sideX = xOff + PANEL_WIDTH + 8;
            int sideY = yOff;
            int rowHeight = 14;
            int headerHeight = 9 + 12;
            int checkSize = 10;
            for (int i = 0; i < RULES_LIST.length; i++) {
                int rowY = sideY + headerHeight + 8 + i * rowHeight;
                GuiButton btn = new GuiButton(100 + i, sideX + 6 - 1, rowY - 1, checkSize + 2, checkSize + 2, "");
                buttonList.add(btn);
                ruleButtons.add(btn);
                ruleButtonKeys.add(RULES_LIST[i][0]);
            }
        }
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
            case 3 -> {
                settingsOpen = !settingsOpen;
                initGui();
            }
            default -> {
                int idx = button.id - 100;
                BlockEntityMinoTable te = getTable();
                if (te != null && idx >= 0 && idx < ruleButtonKeys.size()) {
                    String ruleKey = ruleButtonKeys.get(idx);
                    boolean current = te.getRule(ruleKey, true);
                    C2SSeatControlPacket.Client.sendRuleC2S(gamePos, ruleKey, !current);
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        BlockEntityMinoTable tableEntity = getTable();
        if (tableEntity != null) {
            startButton.enabled = tableEntity.game == null && tableEntity.getPlayersList().size() >= 2;
            stopButton.enabled = tableEntity.game != null;
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(mc.thePlayer);
            leaveButton.enabled = tableEntity.game == null && tableEntity.getPlayersList().contains(cardPlayer);

            int xOff = (width - PANEL_WIDTH) / 2;
            int yOff = (height - PANEL_HEIGHT) / 2;
            GuiDraw.fill(xOff + MARGIN, yOff + MARGIN, xOff + PANEL_WIDTH + MARGIN, yOff + PANEL_HEIGHT + MARGIN, 0x99000000);
            // Tiled block texture background
            GuiDraw.fill(xOff - 1, yOff - 1, xOff + PANEL_WIDTH + 1, yOff + PANEL_HEIGHT + 1, 0xCC000000);
            GuiDraw.blit(PANEL_TEXTURE, xOff, yOff, PANEL_WIDTH, PANEL_HEIGHT, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 16, 16);
            // Dark accent strip for button row
            GuiDraw.fill(xOff, yOff + PANEL_HEIGHT - LARGE_BTN_HEIGHT, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0xCC000000);
            // Title backdrop
            GuiDraw.fill(xOff, yOff + MARGIN - 6, xOff + PANEL_WIDTH, yOff + MARGIN + 9 + 6, 0x99000000);
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, yOff + MARGIN, 0xFFAAAAAA);
            // middle square
            GuiDraw.fill(width / 2 - LARGE_BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN,
                width / 2 + LARGE_BTN_HEIGHT / 2, yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT, 0xFF3E2723);

            if (settingsOpen) {
                GuiDraw.fill(settingsButton.xPosition - 1, settingsButton.yPosition - 1,
                    settingsButton.xPosition + settingsButton.width + 1, settingsButton.yPosition + settingsButton.height + 1, 0xFF4CAF50);
            }

            // settings tab
            if (settingsOpen) {
                int sideX = xOff + PANEL_WIDTH + 8;
                int sideY = yOff;
                int checkSize = 10;
                int rowHeight = 14;
                int headerHeight = 9 + 12;
                int sideWidth = 140;
                int sideHeight = PANEL_HEIGHT;

                GuiDraw.fill(sideX - 1, sideY - 1, sideX + sideWidth + 1, sideY + sideHeight + 1, 0xCC000000);
                GuiDraw.fill(sideX, sideY, sideX + sideWidth, sideY + sideHeight, 0xFF313031);
                GuiDraw.fill(sideX, sideY + MARGIN - 6, sideX + sideWidth, sideY + MARGIN + 9 + 6, 0x99000000);
                GuiDraw.drawString(fontRendererObj, "Settings", sideX + 6, sideY + MARGIN, 0xFFFFFFFF);

                for (int i = 0; i < RULES_LIST.length; i++) {
                    String ruleKey = RULES_LIST[i][0];
                    String label = RULES_LIST[i][1];
                    int rowY = sideY + headerHeight + 8 + i * rowHeight;
                    boolean enabled = tableEntity.getRule(ruleKey, true);
                    if (enabled) {
                        GuiDraw.fill(sideX + 6 - 2, rowY - 2, sideX + 6 + checkSize + 2, rowY + checkSize + 2, 0xFF4CAF50);
                    }
                    GuiDraw.drawString(fontRendererObj, label, sideX + 22, rowY + 1, 0xFFFFFFFF);
                }

                for (GuiButton b : ruleButtons) {
                    b.enabled = tableEntity.game == null;
                }
            }

            int headSize = 9;
            // North Player
            {
                String name = getPlayerName(tableEntity, Direction.NORTH);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.NORTH);
                if (skin != null) {
                    int totalWidth = fontRendererObj.getStringWidth(name) + headSize + 4;
                    int startX = width / 2 - totalWidth / 2;
                    GuiDraw.drawPlayerHead(skin, startX, y - 1, headSize);
                    GuiDraw.drawString(fontRendererObj, name, startX + headSize + 4, y, 0xFFAAAAAA);
                } else {
                    GuiDraw.drawCenteredString(fontRendererObj, name, width / 2, y, 0xFFAAAAAA);
                }
            }

            // West Player
            {
                String name = getPlayerName(tableEntity, Direction.WEST);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT / 2 - 9 / 2;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.WEST);
                int nameX = width / 2 - MARGIN - LARGE_BTN_HEIGHT / 2 - fontRendererObj.getStringWidth(name) - (skin != null ? headSize + 4 : 0);
                if (skin != null) {
                    GuiDraw.drawPlayerHead(skin, nameX - headSize - 4, y - 1, headSize);
                }
                GuiDraw.drawString(fontRendererObj, name, nameX, y, 0xFFAAAAAA);
            }

            // East Player
            {
                String name = getPlayerName(tableEntity, Direction.EAST);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT / 2 - 9 / 2;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.EAST);
                int x = width / 2 + MARGIN + LARGE_BTN_HEIGHT / 2;
                if (skin != null) {
                    GuiDraw.drawPlayerHead(skin, x, y - 1, headSize);
                }
                int nameX = x + (skin != null ? headSize + 4 : 0);
                GuiDraw.drawString(fontRendererObj, name, nameX, y, 0xFFAAAAAA);
            }

            // South Player
            {
                String name = getPlayerName(tableEntity, Direction.SOUTH);
                int y = yOff + MARGIN + 9 + MARGIN + MARGIN + 9 + MARGIN + LARGE_BTN_HEIGHT + MARGIN;
                ResourceLocation skin = getPlayerSkin(tableEntity, Direction.SOUTH);
                if (skin != null) {
                    int totalWidth = fontRendererObj.getStringWidth(name) + headSize + 4;
                    int startX = width / 2 - totalWidth / 2;
                    GuiDraw.drawPlayerHead(skin, startX, y - 1, headSize);
                    GuiDraw.drawString(fontRendererObj, name, startX + headSize + 4, y, 0xFFAAAAAA);
                } else {
                    GuiDraw.drawCenteredString(fontRendererObj, name, width / 2, y, 0xFFAAAAAA);
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    private static String getPlayerName(BlockEntityMinoTable tableEntity, Direction direction) {
        return tableEntity.players.get(direction) == null ? "-" : tableEntity.players.get(direction).name;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
