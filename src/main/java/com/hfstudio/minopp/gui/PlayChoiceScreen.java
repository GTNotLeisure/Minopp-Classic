package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import com.hfstudio.minopp.gui.ui.ChoiceButton;
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
    private final TableScreenPanel panel;
    private final CardPlayer player;
    private final Card card;
    private final PlayContext.PlayChoices choices;
    private final boolean needsColor;
    private final int targetCount;
    private final boolean darkSide;

    private boolean colorStep;
    private PlayerPicker picker;
    private RadialChoiceLayout choiceRing;
    private final ChoiceKeyNavigation navigation = new ChoiceKeyNavigation();
    /**
     * The steps after the color: a number, the targets, the cards to discard.
     */
    private final boolean needsNumber;
    private final int pickCount;
    private boolean numberStep, targetsDone, pickStep;
    private final List<Card> pickable = new ArrayList<>();
    private final IntSet picked = new IntLinkedOpenHashSet();
    private final CardGrid pickGrid = new CardGrid();
    private final CardGridSelection pickSelection = new CardGridSelection(pickGrid, picked);

    public PlayChoiceScreen(BlockPos gamePos, CardPlayer player, Card card, PlayContext.PlayChoices choices,
        boolean needsColor, int targetCount, boolean darkSide) {
        this.gamePos = gamePos;
        this.panel = new TableScreenPanel(gamePos, player.uuid);
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

    private List<CardColor> colors() {
        BlockEntityMinoTable table = table();
        return table != null && table.game != null ? table.game.colors() : CardColor.forSide(darkSide);
    }

    private int bandTop() {
        return choiceRing == null ? 40 : choiceRing.top();
    }

    // only debug games may back out of a play once its color or targets are being chosen
    private boolean canCancel() {
        BlockEntityMinoTable table = table();
        return table != null && table.game != null && table.game.debug;
    }

    private BlockEntityMinoTable table() {
        return ItemHandCards.getTable(Minecraft.getMinecraft().theWorld, gamePos);
    }

    private int choiceBottom() {
        return height - panel.handPreviewHeight(width);
    }

    @Override
    public void initGui() {
        pickSelection.release();
        buttonList.clear();
        panel.addHandButton(buttonList, width, height);
        choiceRing = null;
        navigation.setOptions(List.of());
        if (colorStep) {
            List<CardColor> colors = colors();
            ringPicker = !card.face(darkSide)
                .type()
                .isDraw();
            ColorPicker3D.open(gamePos, colors, ringPicker);
            shareChoice(true, -1);
            for (CardColor color : colors) buttonList.add(
                new ChoiceButton(
                    color.ordinal(),
                    0,
                    0,
                    56,
                    20,
                    EnumChatFormatting.getTextWithoutFormattingCodes(
                        Chat.translatable(color.langKey())
                            .getFormattedText()),
                    color.rgb));
        } else if (numberStep) {
            for (int number = 1; number <= 9; number++)
                buttonList.add(new ChoiceButton(300 + number, 0, 0, 22, 20, String.valueOf(number), 0xFFE0A21A));
        } else if (pickStep) {
            pickGrid.layoutIn(
                panel.contentLeft(width),
                panel.contentWidth(width),
                height,
                8,
                50,
                58 + panel.handPreviewHeight(width));
            String randomLabel = Chat.translatable("gui.minopp.choice.random")
                .getFormattedText();
            int randomWidth = Math.max(80, fontRendererObj.getStringWidth(randomLabel) + 12);
            UiButton random = new UiButton(
                -4,
                panel.centerX(width) - randomWidth / 2,
                choiceBottom() - 50,
                randomWidth,
                20,
                randomLabel);
            random.enabled = pickCount > 0 && pickCount <= pickGrid.cards()
                .size();
            buttonList.add(random);
            int by = choiceBottom() - 26;
            boolean cancel = canCancel();
            int confirmWidth = Math.max(80, fontRendererObj.getStringWidth(pickConfirmLabel(pickCount)) + 12);
            int left = panel.centerX(width) - (confirmWidth + (cancel ? 84 : 0)) / 2;
            UiButton ok = new UiButton(-3, left, by, confirmWidth, 20, pickConfirmLabel(picked.size()));
            ok.enabled = picked.size() == pickCount;
            buttonList.add(ok);
            if (cancel) buttonList.add(
                new UiButton(
                    -1,
                    left + confirmWidth + 4,
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
            int page = picker == null ? 0 : picker.page();
            List<UUID> selectedTargets = picker == null ? List.of() : List.copyOf(picker.selected);
            picker = new PlayerPicker(others, sizes, targetCount);
            for (CardPlayer candidate : others)
                if (selectedTargets.contains(candidate.uuid)) picker.selected.add(candidate.uuid);
            picker.layoutRadial(width / 2, height / 2, panel.contentLeft(width) + 8, 48, width - 8, choiceBottom() - 4);
            picker.scroll(page);
            int by = 24;
            buttonList.add(
                new UiButton(
                    -2,
                    panel.contentLeft(width) + 8,
                    by,
                    80,
                    20,
                    Chat.translatable("gui.minopp.choice.confirm")
                        .getFormattedText()));
            if (canCancel()) buttonList.add(
                new UiButton(
                    -1,
                    width - 88,
                    by,
                    80,
                    20,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        }
        if (colorStep || numberStep) {
            List<GuiButton> options = buttonList.stream()
                .filter(button -> button.id >= 0)
                .toList();
            navigation.setOptions(options);
            choiceRing = RadialChoiceLayout.arrange(
                options,
                fontRendererObj,
                width / 2,
                height / 2,
                panel.contentLeft(width) + 8,
                36,
                width - 8,
                choiceBottom() - 4);
            if (canCancel()) buttonList.add(
                new UiButton(
                    -1,
                    width - 68,
                    24,
                    60,
                    18,
                    Chat.translatable("gui.cancel")
                        .getFormattedText()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == TableScreenPanel.HAND_BUTTON) {
            panel.openHand(this, this::choiceActive);
            return;
        }
        if (!choiceActive()) return;
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
        if (button.id == -4) {
            if (pickStep) {
                pickSelection.selectRandom(pickCount);
                updatePickConfirm();
            }
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

    private int sentHover = -2;
    private boolean ringPicker;

    private void shareChoice(boolean active, int hover) {
        if (active && hover == sentHover) return;
        sentHover = active ? hover : -2;
        C2SPlayCardPacket.Client.sendChoosingC2S(gamePos, active, ringPicker, hover);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (canCancel()) HandScreen.returnFromChoice();
            return;
        }
        if (!choiceActive()) return;
        GameControls.Action action = GameControls.actionFor(keyCode);
        if (keyCode > 0 && Keyboard.isRepeatEvent()
            && action != GameControls.Action.PREVIOUS
            && action != GameControls.Action.NEXT) return;
        if (action == GameControls.Action.SHOUT) {
            choices.shout = true;
            GameControls.handleUtility(keyCode);
            return;
        }
        if (GameControls.handleUtility(keyCode)) return;
        if (action == GameControls.Action.PREVIOUS || action == GameControls.Action.NEXT) {
            int direction = action == GameControls.Action.PREVIOUS ? -1 : 1;
            if (colorStep || numberStep) navigation.move(direction);
            else if (pickStep) pickGrid.scroll(direction);
            else if (picker != null) picker.scroll(direction);
            return;
        }
        if (action == GameControls.Action.CONFIRM || action == GameControls.Action.PLAY) {
            if (colorStep || numberStep) {
                GuiButton selected = navigation.selected();
                if (selected != null) actionPerformed(selected);
                else if (colorStep) {
                    List<CardColor> colors = colors();
                    int hovered = ColorPicker3D.hovered;
                    if (hovered >= 0 && hovered < colors.size()) chooseColor(colors.get(hovered));
                }
            } else {
                int confirm = pickStep ? -3 : -2;
                for (GuiButton button : buttonList) if (button.id == confirm && button.enabled) {
                    actionPerformed(button);
                    break;
                }
            }
            return;
        }
        if (colorStep || numberStep) {
            switch (keyCode) {
                case Keyboard.KEY_W, Keyboard.KEY_UP -> navigation.move(0, -1);
                case Keyboard.KEY_S, Keyboard.KEY_DOWN -> navigation.move(0, 1);
                case Keyboard.KEY_A -> navigation.move(-1, 0);
                case Keyboard.KEY_D -> navigation.move(1, 0);
                default -> {}
            }
        }
        if (action != null) return;
        if (numberStep && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            choices.number = keyCode - Keyboard.KEY_1 + 1;
            advance(true);
            return;
        }
        if (colorStep && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_8) {
            List<CardColor> colors = colors();
            int index = keyCode - Keyboard.KEY_1;
            if (index < colors.size()) chooseColor(colors.get(index));
            return;
        }
        if (keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT) {
            int direction = keyCode == Keyboard.KEY_PRIOR ? -1 : 1;
            if (pickStep) pickGrid.scroll(direction);
            else if (!colorStep && !numberStep && picker != null) picker.scroll(direction);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && panel.browseHandPreview(width, height, mouseX, mouseY, 0)) return;
        if (button == 0) for (GuiButton control : buttonList) {
            if (control.visible && mouseX >= control.xPosition
                && mouseX < control.xPosition + control.width
                && mouseY >= control.yPosition
                && mouseY < control.yPosition + control.height) {
                super.mouseClicked(mouseX, mouseY, button);
                return;
            }
        }
        if (GameControls.actionFor(button - 100) != null) {
            navigation.updatePointer(mouseX, mouseY);
            keyTyped('\0', button - 100);
            return;
        }
        if (pickStep && button == 0) {
            if (pickSelection.press(mouseX, mouseY, pickCount, Minecraft.getSystemTime())) {
                mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.click"), 1.6F));
                updatePickConfirm();
                return;
            }
        }
        if (!colorStep && !pickStep && !numberStep && picker != null && picker.click(mouseX, mouseY)) return;
        if (colorStep && button == 0 && mouseX >= panel.contentLeft(width)) {
            int hit = ColorPicker3D.hitTest(mouseX, mouseY);
            List<CardColor> colors = colors();
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
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == 0) {
            if (pickStep && pickSelection.drag(mouseX, mouseY, Minecraft.getSystemTime())) updatePickConfirm();
            pickSelection.release();
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void onGuiClosed() {
        pickSelection.release();
        ColorPicker3D.close();
        if (sentHover != -2) shareChoice(false, -1);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && panel.browseHandPreview(
            width,
            height,
            Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1,
            -Integer.signum(wheel))) return;
        if (wheel != 0) {
            if (pickStep) pickGrid.scroll(-Integer.signum(wheel));
            else if (!colorStep && !numberStep && picker != null) picker.scroll(-Integer.signum(wheel));
        }
    }

    private boolean choiceActive() {
        BlockEntityMinoTable table = table();
        return panel.game() != null && table.game.darkSide == darkSide
            && table.game.whyCannotPlay(player, card, choices.useFlex) == null;
    }

    @Override
    public void updateScreen() {
        BlockEntityMinoTable table = table();
        String reason;
        if (panel.game() == null || table == null || table.game == null) {
            reason = "game.minopp.play.no_game";
        } else {
            CardPlayer me = table.game.deAmputate(player);
            reason = me == null ? "game.minopp.play.no_player" : table.game.whyCannotPlay(me, card, choices.useFlex);
        }
        if (reason != null || table.game.darkSide != darkSide) {
            BlockMinoTable.Client.displayActionBar(
                Chat.translatable(reason == null ? "game.minopp.play.no_game" : reason)
                    .getFormattedText());
            HandScreen.returnFromChoice();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        navigation.updatePointer(mouseX, mouseY);
        String theme = ThemeManager.INSTANCE.selected();
        int band = bandTop();
        if (colorStep) {
            UiDraw.gradient(panel.contentLeft(width), band - 10, width, height, 0x00200404, 0xB0200404);
            String title = Chat.translatable("gui.minopp.wild_selection.title")
                .getFormattedText();
            int tw = fontRendererObj.getStringWidth(title);
            UiDraw.roundRect(width / 2 - tw / 2 - 8, band - 3, width / 2 + tw / 2 + 8, band + 10, 5, 0xC0101010);
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, band, 0xFFFFE070);
            GuiDraw.drawCenteredString(
                fontRendererObj,
                Chat.translatable(
                    "gui.minopp.wild_selection.hint",
                    colors().size(),
                    GameControls.display(GameControls.Action.PREVIOUS),
                    GameControls.display(GameControls.Action.NEXT),
                    GameControls.display(GameControls.Action.CONFIRM))
                    .getFormattedText(),
                width / 2,
                band + 15,
                0xFFDDDDDD);
            List<CardColor> colors = colors();
            GuiButton selected = navigation.selected();
            int hit = selected == null ? ColorPicker3D.hitTest(mouseX, mouseY)
                : colors.indexOf(CardColor.values()[selected.id]);
            ColorPicker3D.hovered = hit;
            shareChoice(true, hit);
            if (hit >= 0 && hit < colors.size()) {
                String name = Chat.translatable(
                    colors.get(hit)
                        .langKey())
                    .getFormattedText();
                int w = fontRendererObj.getStringWidth(name);
                int tx = mouseX, ty = mouseY;
                if (selected != null) {
                    tx = selected.xPosition + selected.width / 2 - w / 2 - 12;
                    ty = selected.yPosition - 8;
                }
                UiDraw.roundRect(tx + 8, ty - 14, tx + 16 + w, ty - 2, 3, 0xC0000000);
                fontRendererObj.drawStringWithShadow(name, tx + 12, ty - 12, colors.get(hit).rgb | 0xFF000000);
            }
        } else if (numberStep) {
            UiDraw.gradient(panel.contentLeft(width), band - 10, width, height, 0x00200404, 0xB0200404);
            String title = Chat.translatable("gui.minopp.choice.number")
                .getFormattedText();
            int tw = fontRendererObj.getStringWidth(title);
            UiDraw.roundRect(width / 2 - tw / 2 - 8, band - 3, width / 2 + tw / 2 + 8, band + 10, 5, 0xC0101010);
            GuiDraw.drawCenteredString(fontRendererObj, title, width / 2, band, 0xFFFFE070);
        } else if (pickStep) {
            drawPickGrid(theme, mouseX, mouseY);
        } else {
            int top = picker == null ? band : Math.max(58, picker.top());
            UiDraw.gradient(panel.contentLeft(width), top - 22, width, height, 0x80200404, 0xC0200404);
            String title = Chat
                .translatable(
                    targetCount > 1 ? "gui.minopp.choice.targets" : "gui.minopp.swap_card_selection.title",
                    targetCount)
                .getFormattedText();
            GuiDraw.drawCenteredString(fontRendererObj, title, panel.centerX(width), top - 16, 0xFFFFE070);
            if (picker != null) {
                picker.draw(fontRendererObj, mouseX, mouseY);
                if (picker.pages() > 1) GuiDraw.drawCenteredString(
                    fontRendererObj,
                    Chat.translatable("gui.minopp.hand.page", picker.page() + 1, picker.pages())
                        .getFormattedText(),
                    panel.centerX(width),
                    choiceBottom() - 46,
                    0xFFE8E8E8);
            }
        }
        if (choices.shout) {
            GuiDraw.drawString(
                fontRendererObj,
                Chat.translatable("gui.minopp.play.cursor.shout")
                    .getFormattedText(),
                panel.contentLeft(width) + 8,
                choiceBottom() - 20,
                0xFFFFFFFF);
        }
        if (choices.useFlex) {
            GuiDraw.drawString(
                fontRendererObj,
                Chat.translatable("gui.minopp.play.cursor.flex")
                    .getFormattedText(),
                panel.contentLeft(width) + 8,
                choiceBottom() - 32,
                0xFFFFD040);
        }
        panel.draw(width, height);
        if (choiceRing != null) choiceRing.draw();
        navigation.drawFocus();
        super.drawScreen(mouseX, mouseY, partialTicks);
        panel.drawHandPreview(width, height, mouseX, mouseY);
        if (pickStep) drawPickTooltip(mouseX, mouseY);
    }

    private void drawPickGrid(String theme, int mouseX, int mouseY) {
        pickGrid.layoutIn(
            panel.contentLeft(width),
            panel.contentWidth(width),
            height,
            8,
            50,
            58 + panel.handPreviewHeight(width));
        if (!Mouse.isButtonDown(0)) pickSelection.release();
        else if (pickSelection.drag(mouseX, mouseY, Minecraft.getSystemTime())) updatePickConfirm();
        UiDraw.gradient(panel.contentLeft(width), pickGrid.top() - 26, width, height, 0x80200404, 0xD0200404);
        String title = Chat.translatable("gui.minopp.choice.pick", pickCount, picked.size(), pickCount)
            .getFormattedText();
        if (pickGrid.pages() > 1)
            title += "  " + Chat.translatable("gui.minopp.hand.page", pickGrid.page() + 1, pickGrid.pages())
                .getFormattedText();
        GuiDraw.drawCenteredString(fontRendererObj, title, panel.centerX(width), pickGrid.top() - 18, 0xFFFFE070);
        pickGrid.draw(theme, darkSide, mouseX, mouseY, picked::contains);
    }

    private void drawPickTooltip(int mouseX, int mouseY) {
        if (pickSelection.isDragging()) return;
        Card hover = pickGrid.hit(mouseX, mouseY, picked::contains);
        BlockEntityMinoTable table = table();
        if (hover != null && table != null && table.game != null) UiDraw.tooltip(
            fontRendererObj,
            CardTooltips.lines(fontRendererObj, table.game, hover, width),
            mouseX,
            mouseY,
            width,
            height);
    }

    private void updatePickConfirm() {
        for (GuiButton button : buttonList) if (button.id == -3) {
            button.enabled = picked.size() == pickCount;
            button.displayString = pickConfirmLabel(picked.size());
        }
    }

    private String pickConfirmLabel(int count) {
        return Chat.translatable("gui.minopp.choice.confirm_count", count, pickCount)
            .getFormattedText();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
