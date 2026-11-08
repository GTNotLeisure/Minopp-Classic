package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ColorPicker3D;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

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
            ColorPicker3D.open(gamePos, colors);
            int bw = 56, gap = 6, total = colors.size() * bw + (colors.size() - 1) * gap;
            for (int i = 0; i < colors.size(); i++) {
                CardColor c = colors.get(i);
                buttonList.add(
                    new UiButton(
                        c.ordinal(),
                        width / 2 - total / 2 + i * (bw + gap),
                        height - 50,
                        bw,
                        18,
                        net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(
                            Chat.translatable(c.langKey())
                                .getFormattedText()),
                        c.rgb));
            }
            buttonList.add(
                new UiButton(
                    -1,
                    width / 2 - 40,
                    height - 28,
                    80,
                    18,
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
            HandScreen.returnFromChoice();
            return;
        }
        if (button.id == -2) {
            if (picker.selected.size() < targetCount) return;
            choices.targets.clear();
            choices.targets.addAll(picker.selected);
            send();
            return;
        }
        chooseColor(CardColor.values()[button.id]);
    }

    private void chooseColor(CardColor color) {
        ColorPicker3D.close();
        choices.color = color;
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
            BlockEntityMinoTable.hideHandUntil
                .put(player.uuid, System.currentTimeMillis() + BlockEntityMinoTable.HandSwapAnimation.DURATION_MS);
        }
        C2SPlayCardPacket.Client.sendPlayCardC2S(gamePos, player, card.uid, choices);
        HandScreen.returnFromChoice();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (colorStep && keyCode >= org.lwjgl.input.Keyboard.KEY_1 && keyCode <= org.lwjgl.input.Keyboard.KEY_4) {
            List<CardColor> colors = CardColor.forSide(darkSide);
            int i = keyCode - org.lwjgl.input.Keyboard.KEY_1;
            if (i < colors.size()) chooseColor(colors.get(i));
            return;
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
            HandScreen.returnFromChoice();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (!colorStep && picker != null && picker.click(mouseX, mouseY)) return;
        if (colorStep && button == 0) {
            int hit = ColorPicker3D.hitTest(mouseX, mouseY);
            List<CardColor> colors = CardColor.forSide(darkSide);
            if (hit >= 0 && hit < colors.size()) {
                mc.getSoundHandler()
                    .playSound(
                        net.minecraft.client.audio.PositionedSoundRecord
                            .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                chooseColor(colors.get(hit));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onGuiClosed() {
        ColorPicker3D.close();
    }

    @Override
    public void updateScreen() {
        BlockEntityMinoTable table = table();
        String reason;
        if (table == null || table.game == null) {
            reason = "game.minopp.play.no_game";
        } else {
            CardPlayer me = table.game.deAmputate(player);
            reason = me == null ? "game.minopp.play.no_player" : table.game.whyCannotPlay(me, card, choices.useFlex);
        }
        if (reason != null) {
            BlockMinoTable.Client.displayActionBar(
                Chat.translatable(reason)
                    .getFormattedText());
            HandScreen.returnFromChoice();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        String theme = ThemeManager.INSTANCE.selected();
        if (colorStep) {
            UiDraw.gradient(0, 0, width, 40, 0xA0300606, 0x00300606);
            UiDraw.gradient(0, height - 60, width, height, 0x00300606, 0xA0300606);
            CardRenderer.drawFace2D(card.face(darkSide), theme, 10, 8, 26, 40);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.title")
                    .getFormattedText(),
                width / 2,
                10,
                0xFFFFFFFF);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.hint")
                    .getFormattedText(),
                width / 2,
                22,
                0xFFBBBBBB);
            List<CardColor> colors = CardColor.forSide(darkSide);
            int hit = ColorPicker3D.hitTest(mouseX, mouseY);
            if (hit < 0) {
                for (Object o : buttonList) {
                    GuiButton b = (GuiButton) o;
                    if (b.id >= 0 && mouseX >= b.xPosition
                        && mouseX < b.xPosition + b.width
                        && mouseY >= b.yPosition
                        && mouseY < b.yPosition + b.height) {
                        hit = colors.indexOf(CardColor.values()[b.id]);
                    }
                }
            }
            ColorPicker3D.hovered = hit;
            if (hit >= 0 && hit < colors.size()) {
                String name = Chat.translatable(
                    colors.get(hit)
                        .langKey())
                    .getFormattedText();
                int w = fontRendererObj.getStringWidth(name);
                UiDraw.roundRect(mouseX + 8, mouseY - 14, mouseX + 16 + w, mouseY - 2, 3, 0xC0000000);
                fontRendererObj.drawStringWithShadow(name, mouseX + 12, mouseY - 12, colors.get(hit).rgb | 0xFF000000);
            }
        } else {
            UiDraw.gradient(0, 0, width, height, 0xB0601010, 0xE0200404);
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
