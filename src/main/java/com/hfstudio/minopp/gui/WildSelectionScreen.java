package com.hfstudio.minopp.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WildSelectionScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final CardPlayer player;
    private final Card handCard;
    private final boolean shout;
    private final String title;

    public WildSelectionScreen(BlockPos gamePos, CardPlayer player, Card handCard, boolean shout) {
        this.title = Chat.translatable("gui.minopp.wild_selection.title").getFormattedText();
        this.gamePos = gamePos;
        this.player = player;
        this.handCard = handCard;
        this.shout = shout;
    }

    int BTN_WIDTH = 60;
    int BTN_HEIGHT = 20;
    int BTN_SPACING = 10;
    int MARGIN = 8;

    int PANEL_HEIGHT = MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN + BTN_HEIGHT + MARGIN;
    int PANEL_WIDTH = MARGIN + BTN_WIDTH * 4 + BTN_SPACING * 3 + MARGIN;

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        buttonList.clear();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;

        for (Card.Suit suit : Card.Suit.values()) {
            if (suit == Card.Suit.WILD) continue;
            buttonList.add(new GuiButton(suit.ordinal(), xOff + MARGIN + (BTN_WIDTH + BTN_SPACING) * suit.ordinal(),
                yOff + MARGIN + 9 + MARGIN, BTN_WIDTH, BTN_HEIGHT,
                Chat.translatable("game.minopp.card.suit." + suit.name().toLowerCase()).getFormattedText()));
        }
        buttonList.add(new GuiButton(-1, xOff + MARGIN + (BTN_WIDTH + BTN_SPACING) * 2 + BTN_WIDTH / 2,
            yOff + MARGIN + 9 + MARGIN + BTN_HEIGHT + MARGIN, BTN_WIDTH, BTN_HEIGHT, Chat.translatable("gui.cancel").getFormattedText()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0) {
            C2SPlayCardPacket.Client.sendPlayCardC2S(gamePos, player, handCard, Card.Suit.values()[button.id], shout);
        }
        mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int xOff = (width - PANEL_WIDTH) / 2;
        int yOff = (height - PANEL_HEIGHT) / 2;
        GuiDraw.fill(xOff + MARGIN, yOff + MARGIN, xOff + PANEL_WIDTH + MARGIN, yOff + PANEL_HEIGHT + MARGIN, 0x66000000);
        GuiDraw.fill(xOff, yOff, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0xFF313031);
        GuiDraw.fill(xOff, yOff + PANEL_HEIGHT - BTN_HEIGHT, xOff + PANEL_WIDTH, yOff + PANEL_HEIGHT, 0x66546E7A);
        GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, yOff + MARGIN, 0xFFFFFFFF);
        if (shout) {
            GuiDraw.drawString(fontRendererObj, Chat.translatable("gui.minopp.play.cursor.shout").getFormattedText(),
                xOff + MARGIN, yOff + PANEL_HEIGHT - MARGIN - 9, 0xFFFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
