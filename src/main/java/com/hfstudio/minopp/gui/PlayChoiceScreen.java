package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.CardType;
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
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

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
    /**
     * The steps after the color: a number, the targets, the cards to discard.
     */
    private final boolean needsNumber;
    private final int pickCount;
    private boolean numberStep, targetsDone, pickStep;
    private final List<Card> pickable = new ArrayList<>();
    private final IntSet picked = new IntLinkedOpenHashSet();
    private final CardGrid pickGrid = new CardGrid();

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
        CardType type = card.face(darkSide)
            .type();
        this.needsNumber = type.choiceFor(choices.useFlex)
            .needsNumber();
        this.pickCount = type.pickCount(Math.max(0, player.hand.size() - 1));
        if (!needsColor) advance(false);
    }

    /**
     * Moves to the first step still open; sends the play once every step is done.
     */
    private void advance(boolean init) {
        numberStep = pickStep = false;
        if (needsNumber && choices.number < 1) numberStep = true;
        else if (targetCount > 0 && !targetsDone) {} else
            if (pickCount > 0 && picked.size() < pickCount && choices.picks.isEmpty()) pickStep = true;
            else if (init) {
                send();
                return;
            }
        if (pickStep && pickable.isEmpty()) {
            BlockEntityMinoTable table = table();
            List<Card> hand = table != null && table.game != null ? ItemHandCards.sortedHand(table.game, player)
                : player.hand;
            for (Card c : hand) if (c.uid != card.uid) pickable.add(c);
            pickGrid.setCards(pickable);
        }
        if (init) initGui();
    }

    private int bandTop() {
        return height - 74;
    }

    // only debug games may back out of a play once its color or targets are being chosen
    private boolean canCancel() {
        BlockEntityMinoTable table = table();
        return table != null && table.game != null && table.game.debug;
    }

    private BlockEntityMinoTable table() {
        return ItemHandCards.getTable(Minecraft.getMinecraft().theWorld, gamePos);
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
                        height - 30,
                        bw,
                        18,
                        EnumChatFormatting.getTextWithoutFormattingCodes(
                            Chat.translatable(c.langKey())
                                .getFormattedText()),
                        c.rgb));
            }
            if (canCancel()) buttonList.add(
                new UiButton(
                    -1,
                    width / 2 + total / 2 + 10,
                    height - 30,
                    50,
                    18,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        } else if (numberStep) {
            int bw = 22, gap = 4, total = 9 * bw + 8 * gap;
            for (int n = 1; n <= 9; n++) buttonList.add(
                new UiButton(
                    300 + n,
                    width / 2 - total / 2 + (n - 1) * (bw + gap),
                    height - 30,
                    bw,
                    18,
                    String.valueOf(n),
                    0xFFE0A21A));
            if (canCancel()) buttonList.add(
                new UiButton(
                    -1,
                    width / 2 + total / 2 + 10,
                    height - 30,
                    50,
                    18,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        } else if (pickStep) {
            pickGrid.layout(width, height, 8);
            int by = height - 26;
            UiButton ok = new UiButton(
                -3,
                width / 2 - 84,
                by,
                80,
                20,
                Chat.translatable("gui.minopp.choice.confirm")
                    .getFormattedText());
            ok.enabled = picked.size() == pickCount;
            buttonList.add(ok);
            if (canCancel()) buttonList.add(
                new UiButton(
                    -1,
                    width / 2 + 4,
                    by,
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
            picker.layout(width / 2, 0, width - 40);
            int top = Math.max(40, height - 32 - picker.height() - 6);
            picker.layout(width / 2, top, width - 40);
            int by = height - 28;
            buttonList.add(
                new UiButton(
                    -2,
                    width / 2 - 84,
                    by,
                    80,
                    20,
                    Chat.translatable("gui.minopp.choice.confirm")
                        .getFormattedText()));
            if (canCancel()) buttonList.add(
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
            if (canCancel()) HandScreen.returnFromChoice();
            return;
        }
        if (button.id == -2) {
            if (picker.selected.size() < targetCount) return;
            choices.targets.clear();
            choices.targets.addAll(picker.selected);
            targetsDone = true;
            advance(true);
            return;
        }
        if (button.id == -3) {
            if (picked.size() != pickCount) return;
            choices.picks.clear();
            choices.picks.addAll(picked);
            advance(true);
            return;
        }
        if (button.id > 300 && button.id <= 309) {
            choices.number = button.id - 300;
            advance(true);
            return;
        }
        chooseColor(CardColor.values()[button.id]);
    }

    private void chooseColor(CardColor color) {
        ColorPicker3D.close();
        choices.color = color;
        colorStep = false;
        advance(true);
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
        if (numberStep && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            choices.number = keyCode - Keyboard.KEY_1 + 1;
            advance(true);
            return;
        }
        if (colorStep && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_4) {
            List<CardColor> colors = CardColor.forSide(darkSide);
            int i = keyCode - Keyboard.KEY_1;
            if (i < colors.size()) chooseColor(colors.get(i));
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (canCancel()) HandScreen.returnFromChoice();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (pickStep && button == 0) {
            Card hit = pickGrid.hit(mouseX, mouseY, picked::contains);
            if (hit != null) {
                int uid = hit.uid;
                if (picked.contains(uid)) picked.remove(uid);
                else if (picked.size() < pickCount) picked.add(uid);
                mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.click"), 1.6F));
                for (GuiButton b : buttonList) if (b.id == -3) b.enabled = picked.size() == pickCount;
                return;
            }
        }
        if (!colorStep && !pickStep && !numberStep && picker != null && picker.click(mouseX, mouseY)) return;
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
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (pickStep && wheel != 0) pickGrid.scroll(-Integer.signum(wheel));
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
            UiDraw.gradient(0, height - 84, width, height, 0x00200404, 0xB0200404);
            String title = Chat.translatable("gui.minopp.wild_selection.title")
                .getFormattedText();
            int tw = fontRendererObj.getStringWidth(title);
            UiDraw.roundRect(width / 2 - tw / 2 - 8, band - 3, width / 2 + tw / 2 + 8, band + 10, 5, 0xC0101010);
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, band, 0xFFFFE070);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable("gui.minopp.wild_selection.hint")
                    .getFormattedText(),
                width / 2,
                band + 15,
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
        } else if (numberStep) {
            UiDraw.gradient(0, height - 84, width, height, 0x00200404, 0xB0200404);
            String title = Chat.translatable("gui.minopp.choice.number")
                .getFormattedText();
            int tw = fontRendererObj.getStringWidth(title);
            UiDraw.roundRect(width / 2 - tw / 2 - 8, band - 3, width / 2 + tw / 2 + 8, band + 10, 5, 0xC0101010);
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, band, 0xFFFFE070);
        } else if (pickStep) {
            drawPickGrid(theme, mouseX, mouseY);
        } else {
            int top = picker == null ? band : picker.top();
            UiDraw.gradient(0, top - 22, width, height, 0x80200404, 0xC0200404);
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

    private void drawPickGrid(String theme, int mouseX, int mouseY) {
        pickGrid.layout(width, height, 8);
        UiDraw.gradient(0, pickGrid.top() - 26, width, height, 0x80200404, 0xD0200404);
        String title = Chat.translatable("gui.minopp.choice.pick", pickCount, picked.size(), pickCount)
            .getFormattedText();
        if (pickGrid.pages() > 1)
            title += "  " + Chat.translatable("gui.minopp.hand.page", pickGrid.page() + 1, pickGrid.pages())
                .getFormattedText();
        GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, pickGrid.top() - 18, 0xFFFFE070);
        pickGrid.draw(theme, darkSide, mouseX, mouseY, picked::contains);
        Card hover = pickGrid.hit(mouseX, mouseY, picked::contains);
        BlockEntityMinoTable table = table();
        if (hover != null && table != null && table.game != null) drawHoveringText(
            CardTooltips.lines(fontRendererObj, table.game, hover, width),
            mouseX,
            mouseY,
            fontRendererObj);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
