package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DecisionScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final CardPlayer self;
    private final PendingDecision.Type type;
    private PlayerPicker picker;

    public DecisionScreen(BlockPos gamePos, CardPlayer self, PendingDecision decision) {
        this.gamePos = gamePos;
        this.self = self;
        this.type = decision.type;
    }

    private CardGame game() {
        BlockEntityMinoTable t = ItemHandCards.getTable(mc.theWorld, gamePos);
        return t == null ? null : t.game;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        CardGame game = game();
        int cy = height / 2;
        switch (type) {
            case CHALLENGE -> {
                buttonList.add(
                    new UiButton(
                        1,
                        width / 2 - 104,
                        cy,
                        100,
                        20,
                        Chat.translatable("gui.minopp.decision.do_challenge")
                            .getFormattedText()));
                buttonList.add(
                    new UiButton(
                        0,
                        width / 2 + 4,
                        cy,
                        100,
                        20,
                        Chat.translatable("gui.minopp.decision.accept")
                            .getFormattedText()));
            }
            case ROULETTE_COLOR -> {
                List<CardColor> colors = CardColor.forSide(game != null && game.darkSide);
                int bw = 60, gap = 8, total = colors.size() * bw + (colors.size() - 1) * gap;
                for (int i = 0; i < colors.size(); i++) {
                    CardColor c = colors.get(i);
                    buttonList.add(
                        new UiButton(
                            c.ordinal(),
                            width / 2 - total / 2 + i * (bw + gap),
                            cy,
                            bw,
                            20,
                            EnumChatFormatting.getTextWithoutFormattingCodes(
                                Chat.translatable(c.langKey())
                                    .getFormattedText()),
                            c.rgb));
                }
            }
            case POINT_TAKEN -> {
                List<CardPlayer> others = new ArrayList<>();
                List<Integer> sizes = new ArrayList<>();
                if (game != null) {
                    for (CardPlayer p : game.activePlayers()) {
                        if (p.equals(self)) continue;
                        others.add(p);
                        sizes.add(p.hand.size());
                    }
                }
                picker = new PlayerPicker(others, sizes, 1);
                picker.layout(width / 2, 50, width - 40);
                buttonList.add(
                    new UiButton(
                        -2,
                        width / 2 - 40,
                        Math.min(height - 28, 50 + picker.height() + 14),
                        80,
                        20,
                        Chat.translatable("gui.minopp.choice.confirm")
                            .getFormattedText()));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == -2) {
            if (picker == null || picker.selected.isEmpty()) return;
            C2SPlayCardPacket.Client.sendDecisionC2S(gamePos, 0, picker.selected.get(0));
        } else {
            C2SPlayCardPacket.Client.sendDecisionC2S(gamePos, button.id, null);
        }
        mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (picker != null && picker.click(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        UiDraw.gradient(0, 0, width, height, 0xB0601010, 0xE0200404);
        CardGame game = game();
        if (game == null || game.decisionFor(self.uuid) == null) {
            mc.displayGuiScreen(null);
            return;
        }
        String title = Chat.translatable(
            "gui.minopp.decision." + type.name()
                .toLowerCase(),
            game.pendingDraw)
            .getFormattedText();
        int y = type == PendingDecision.Type.POINT_TAKEN ? 24 : height / 2 - 30;
        GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, y, 0xFFFFFFFF);
        long left = Math.max(0, (game.decisionFor(self.uuid).deadline - System.currentTimeMillis()) / 1000);
        GuiDraw.drawCenteredString(fontRendererObj, left + "s", width / 2, y + 12, 0xFFAAAAAA);
        if (picker != null) picker.draw(fontRendererObj, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
