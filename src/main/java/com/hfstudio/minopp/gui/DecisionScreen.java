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
import com.hfstudio.minopp.game.DecisionTypes;
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
    private final PendingDecision decision;
    private PlayerPicker picker;
    private int customPage, customTop;

    public DecisionScreen(BlockPos gamePos, CardPlayer self, PendingDecision decision) {
        this.gamePos = gamePos;
        this.self = self;
        this.type = decision.type;
        this.decision = decision;
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
            case CUSTOM -> {
                if (game == null) return;
                List<DecisionTypes.Option> options = DecisionTypes.get(decision.typeId)
                    .handler()
                    .options(game, decision);
                int columns = Math.max(1, Math.min(3, (width - 20) / 130));
                int pageSize = columns * Math.max(1, (height - 100) / 24);
                int pages = Math.max(1, (options.size() + pageSize - 1) / pageSize);
                customPage = Math.floorMod(customPage, pages);
                int from = customPage * pageSize, to = Math.min(options.size(), from + pageSize);
                int rows = (to - from + columns - 1) / columns;
                customTop = height - 28 - (pages > 1 ? 28 : 0) - Math.max(0, rows - 1) * 24;
                for (int i = from; i < to; i++) {
                    DecisionTypes.Option option = options.get(i);
                    int index = i - from;
                    int rowCount = Math.min(columns, to - from - index / columns * columns);
                    int rowWidth = rowCount * 124 + (rowCount - 1) * 6;
                    buttonList.add(
                        new UiButton(
                            option.value(),
                            (width - rowWidth) / 2 + index % columns * 130,
                            customTop + index / columns * 24,
                            124,
                            20,
                            Chat.translatable(option.labelKey())
                                .getFormattedText()));
                }
                if (pages > 1) {
                    buttonList.add(new UiButton(-3, width / 2 - 70, height - 28, 60, 20, "<"));
                    buttonList.add(new UiButton(-4, width / 2 + 10, height - 28, 60, 20, ">"));
                }
            }
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
                List<CardColor> colors = game == null ? CardColor.LIGHT : game.colors();
                int bw = 60, gap = 8, columns = Math.max(1, Math.min(4, (width - 24) / 68));
                int total = columns * bw + (columns - 1) * gap;
                for (int i = 0; i < colors.size(); i++) {
                    CardColor c = colors.get(i);
                    buttonList.add(
                        new UiButton(
                            c.ordinal(),
                            width / 2 - total / 2 + i % columns * (bw + gap),
                            cy + i / columns * 24,
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
        if (type == PendingDecision.Type.CUSTOM && (button.id == -3 || button.id == -4)) {
            customPage += button.id == -3 ? -1 : 1;
            initGui();
            return;
        }
        if (button.id == -2) {
            if (picker == null || picker.selected.isEmpty()) return;
            C2SPlayCardPacket.Client.sendDecisionC2S(gamePos, decision, 0, picker.selected.get(0));
        } else {
            C2SPlayCardPacket.Client.sendDecisionC2S(gamePos, decision, button.id, null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (picker != null && picker.click(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (type == PendingDecision.Type.CUSTOM)
            UiDraw.gradient(0, customTop - 48, width, height, 0x00200404, 0xB0200404);
        else UiDraw.gradient(0, 0, width, height, 0xB0601010, 0xE0200404);
        CardGame game = game();
        PendingDecision current = game == null ? null : game.decisionFor(self.uuid);
        if (current == null || !current.id.equals(decision.id)) {
            mc.displayGuiScreen(null);
            return;
        }
        String title = Chat.translatable(
            type == PendingDecision.Type.CUSTOM ? DecisionTypes.get(decision.typeId)
                .titleKey()
                : "gui.minopp.decision." + type.name()
                    .toLowerCase(),
            game.pendingDraw)
            .getFormattedText();
        int y = type == PendingDecision.Type.CUSTOM ? customTop - 34
            : type == PendingDecision.Type.POINT_TAKEN ? 24 : height / 2 - 30;
        UiDraw.fittedCentered(fontRendererObj, title, width / 2f, y, width - 24, 0xFFFFFFFF);
        long left = Math.max(0, (current.deadline - System.currentTimeMillis()) / 1000);
        GuiDraw.drawCenteredString(fontRendererObj, left + "s", width / 2, y + 12, 0xFFAAAAAA);
        if (picker != null) picker.draw(fontRendererObj, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
