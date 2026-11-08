package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Collects the choices a card needs before it is played: a color (wilds) and/or one or two target players (7 swap,
 * targeted draws, Drawn Together...). Replaces the wild selection and hand swap screens of the original mod.
 */
@SideOnly(Side.CLIENT)
public class PlayChoiceScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final CardPlayer player;
    private final Card card;
    private final PlayContext.PlayChoices choices;
    private final boolean needsColor;
    private final int targetCount;
    private final boolean darkSide;

    private boolean colorStep;
    private PlayerPicker picker;

    public PlayChoiceScreen(BlockPos gamePos, CardPlayer player, Card card, PlayContext.PlayChoices choices,
        boolean needsColor, int targetCount, boolean darkSide) {
        this.gamePos = gamePos;
        this.player = player;
        this.card = card;
        this.choices = choices;
        this.needsColor = needsColor;
        this.targetCount = targetCount;
        this.darkSide = darkSide;
        this.colorStep = needsColor;
    }

    private BlockEntityMinoTable table() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int cy = height / 2;
        if (colorStep) {
            List<CardColor> colors = CardColor.forSide(darkSide);
            int bw = 60, gap = 8, total = colors.size() * bw + (colors.size() - 1) * gap;
            for (int i = 0; i < colors.size(); i++) {
                CardColor c = colors.get(i);
                buttonList.add(
                    new UiButton(
                        c.ordinal(),
                        width / 2 - total / 2 + i * (bw + gap),
                        cy + 10,
                        bw,
                        20,
                        net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(
                            Chat.translatable(c.langKey())
                                .getFormattedText()),
                        c.rgb));
            }
            buttonList.add(
                new UiButton(
                    -1,
                    width / 2 - 40,
                    cy + 40,
                    80,
                    20,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        } else {
            BlockEntityMinoTable table = table();
            List<CardPlayer> others = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            if (table != null && table.game != null) {
                for (CardPlayer p : table.game.activePlayers()) {
                    if (p.equals(player)) continue;
                    others.add(p);
                    sizes.add(p.hand.size());
                }
            }
            picker = new PlayerPicker(others, sizes, targetCount);
            picker.layout(width / 2, 50, width - 40);
            int by = Math.min(height - 28, 50 + picker.height() + 14);
            buttonList.add(
                new UiButton(
                    -2,
                    width / 2 - 84,
                    by,
                    80,
                    20,
                    Chat.translatable("gui.minopp.choice.confirm")
                        .getFormattedText()));
            buttonList.add(
                new UiButton(
                    -1,
                    width / 2 + 4,
                    by,
                    80,
                    20,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == -1) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id == -2) {
            if (picker.selected.size() < targetCount) return;
            choices.targets.clear();
            choices.targets.addAll(picker.selected);
            send();
            return;
        }
        choices.color = CardColor.values()[button.id];
        if (targetCount > 0) {
            colorStep = false;
            initGui();
        } else {
            send();
        }
    }

    private void send() {
        BlockEntityMinoTable table = table();
        if (table != null && targetCount > 0 && card.light.isNumber() && card.face(darkSide).number == 7) {
            // Hide our own hand during the swap animation (as the original swap screen did)
            BlockEntityMinoTable.hideHandUntil
                .put(player.uuid, System.currentTimeMillis() + BlockEntityMinoTable.HandSwapAnimation.DURATION_MS);
        }
        C2SPlayCardPacket.Client.sendPlayCardC2S(gamePos, player, card.uid, choices);
        mc.displayGuiScreen(null);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (!colorStep && picker != null && picker.click(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        UiDraw.gradient(0, 0, width, height, 0xB0601010, 0xE0200404);
        String theme = ThemeManager.INSTANCE.selected();
        if (colorStep) {
            int cy = height / 2;
            CardRenderer.drawFace2D(card.face(darkSide), theme, width / 2 - 20, cy - 80, 40, 62);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.title")
                    .getFormattedText(),
                width / 2,
                cy - 10,
                0xFFFFFFFF);
            List<CardColor> colors = CardColor.forSide(darkSide);
            int bw = 60, gap = 8, total = colors.size() * bw + (colors.size() - 1) * gap;
            for (int i = 0; i < colors.size(); i++) {
                int x = width / 2 - total / 2 + i * (bw + gap);
            }
        } else {
            String title = Chat
                .translatable(
                    targetCount > 1 ? "gui.minopp.choice.targets" : "gui.minopp.swap_card_selection.title",
                    targetCount)
                .getFormattedText();
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, 20, 0xFFCCCCCC);
            CardRenderer.drawFace2D(card.face(darkSide), theme, 10, 10, 26, 40);
            if (picker != null) picker.draw(fontRendererObj, mouseX, mouseY);
        }
        if (choices.shout) {
            GuiDraw.drawString(
                fontRendererObj,
                Chat.translatable("gui.minopp.play.cursor.shout")
                    .getFormattedText(),
                10,
                height - 20,
                0xFFFFFFFF);
        }
        if (choices.useFlex) {
            GuiDraw.drawString(
                fontRendererObj,
                Chat.translatable("gui.minopp.play.cursor.flex")
                    .getFormattedText(),
                10,
                height - 32,
                0xFFFFD040);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
