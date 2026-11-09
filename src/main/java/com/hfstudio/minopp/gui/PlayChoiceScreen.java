package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

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

    // choices sit on a band just above the raised hand instead of covering the whole screen
    private int bandTop() {
        int cw = Math.max(40, Math.min(60, width / 9)), ch = Math.round(cw * 8.9f / 5.6f);
        return Math.max(60, height - ch - 86);
    }

    private BlockEntityMinoTable table() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cy = height / 2;
        int band = bandTop();
        if (colorStep) {
            List<CardColor> colors = CardColor.forSide(darkSide);
            ringPicker = !card.face(darkSide)
                .type()
                .isDraw();
            ColorPicker3D.open(gamePos, colors, ringPicker);
            shareChoice(true, -1);
            int bw = 56, gap = 6, total = colors.size() * bw + (colors.size() - 1) * gap;
            for (int i = 0; i < colors.size(); i++) {
                CardColor c = colors.get(i);
                buttonList.add(
                    new UiButton(
                        c.ordinal(),
                        width / 2 - total / 2 + i * (bw + gap),
                        band + 26,
                        bw,
                        18,
                        EnumChatFormatting.getTextWithoutFormattingCodes(
                            Chat.translatable(c.langKey())
                                .getFormattedText()),
                        c.rgb));
            }
            buttonList.add(
                new UiButton(
                    -1,
                    width / 2 + total / 2 + 10,
                    band + 26,
                    50,
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
            picker.layout(width / 2, 0, width - 40);
            int top = Math.max(40, band - picker.height() - 10);
            picker.layout(width / 2, top, width - 40);
            int by = Math.min(height - 28, top + picker.height() + 6);
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

    private int keyPick = -1;
    private int sentHover = -2;
    private boolean ringPicker;

    private void shareChoice(boolean active, int hover) {
        if (active && hover == sentHover) return;
        sentHover = active ? hover : -2;
        C2SPlayCardPacket.Client.sendChoosingC2S(gamePos, active, ringPicker, hover);
    }

    private int lastMouseX = Integer.MIN_VALUE, lastMouseY;

    // WASD / arrows move between the pillars by where they appear on screen, so it works from any side of the table
    private void movePick(int dx, int dy) {
        List<CardColor> colors = CardColor.forSide(darkSide);
        int n = Math.min(4, colors.size());
        float ox = 0, oy = 0;
        int found = 0;
        for (int i = 0; i < n; i++) {
            float[] c = ColorPicker3D.center(i);
            if (c == null) continue;
            ox += c[0];
            oy += c[1];
            found++;
        }
        if (found == 0) return;
        ox /= found;
        oy /= found;
        float[] from = ColorPicker3D.center(keyPick);
        if (from != null) {
            ox = from[0];
            oy = from[1];
        }
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float[] c = ColorPicker3D.center(i);
            if (c == null || i == keyPick) continue;
            float along = (c[0] - ox) * dx + (c[1] - oy) * dy;
            float across = Math.abs((c[0] - ox) * dy - (c[1] - oy) * dx);
            if (along <= 1) continue;
            float score = along + 2 * across;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            keyPick = best;
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.6F));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (colorStep) {
            switch (keyCode) {
                case Keyboard.KEY_W, Keyboard.KEY_UP -> {
                    movePick(0, -1);
                    return;
                }
                case Keyboard.KEY_S, Keyboard.KEY_DOWN -> {
                    movePick(0, 1);
                    return;
                }
                case Keyboard.KEY_A, Keyboard.KEY_LEFT -> {
                    movePick(-1, 0);
                    return;
                }
                case Keyboard.KEY_D, Keyboard.KEY_RIGHT -> {
                    movePick(1, 0);
                    return;
                }
                case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER, Keyboard.KEY_SPACE -> {
                    List<CardColor> colors = CardColor.forSide(darkSide);
                    if (keyPick >= 0 && keyPick < colors.size()) chooseColor(colors.get(keyPick));
                    return;
                }
                default -> {}
            }
        }
        if (colorStep && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_4) {
            List<CardColor> colors = CardColor.forSide(darkSide);
            int i = keyCode - Keyboard.KEY_1;
            if (i < colors.size()) chooseColor(colors.get(i));
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
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
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                chooseColor(colors.get(hit));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onGuiClosed() {
        ColorPicker3D.close();
        if (sentHover != -2) shareChoice(false, -1);
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
        int band = bandTop();
        if (colorStep) {
            UiDraw.gradient(0, band - 6, width, band + 50, 0x00200404, 0xB0200404);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.title")
                    .getFormattedText(),
                width / 2,
                band,
                0xFFFFE070);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.hint")
                    .getFormattedText(),
                width / 2,
                band + 12,
                0xFFDDDDDD);
            List<CardColor> colors = CardColor.forSide(darkSide);
            if (lastMouseX != Integer.MIN_VALUE && (mouseX != lastMouseX || mouseY != lastMouseY)) keyPick = -1;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            int hit = keyPick >= 0 ? keyPick : ColorPicker3D.hitTest(mouseX, mouseY);
            if (hit < 0) {
                for (GuiButton o : buttonList) {
                    GuiButton b = o;
                    if (b.id >= 0 && mouseX >= b.xPosition
                        && mouseX < b.xPosition + b.width
                        && mouseY >= b.yPosition
                        && mouseY < b.yPosition + b.height) {
                        hit = colors.indexOf(CardColor.values()[b.id]);
                    }
                }
            }
            ColorPicker3D.hovered = hit;
            shareChoice(true, hit);
            if (hit >= 0 && hit < colors.size()) {
                String name = Chat.translatable(
                    colors.get(hit)
                        .langKey())
                    .getFormattedText();
                int w = fontRendererObj.getStringWidth(name);
                int tx = mouseX, ty = mouseY;
                float[] c = keyPick >= 0 ? ColorPicker3D.center(keyPick) : null;
                if (c != null) {
                    tx = (int) c[0] - w / 2 - 12;
                    ty = (int) c[1] - 18;
                }
                UiDraw.roundRect(tx + 8, ty - 14, tx + 16 + w, ty - 2, 3, 0xC0000000);
                fontRendererObj.drawStringWithShadow(name, tx + 12, ty - 12, colors.get(hit).rgb | 0xFF000000);
            }
        } else {
            int top = picker == null ? band : picker.top();
            UiDraw.gradient(0, top - 22, width, band + 50, 0x80200404, 0xC0200404);
            String title = Chat
                .translatable(
                    targetCount > 1 ? "gui.minopp.choice.targets" : "gui.minopp.swap_card_selection.title",
                    targetCount)
                .getFormattedText();
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, top - 16, 0xFFFFE070);
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
