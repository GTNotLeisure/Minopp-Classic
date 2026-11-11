package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.ChoiceButton;
import com.hfstudio.minopp.gui.ui.SideColumnLayout;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HandOverviewScreen extends GuiScreen {

    private static final int BTN_ENCYCLOPEDIA = 3;

    private final GuiScreen parent;
    private final TableScreenPanel panel;
    private final UUID playerId;
    private final BooleanSupplier choiceActive;
    private final Consumer<Card> selection;
    private final CardGrid grid = new CardGrid();
    private final TableMessagePanel messages = new TableMessagePanel();
    private GuiTextField search;
    private SideColumnLayout controlColumn;
    private String query = "";
    private CardGame shownGame;
    private List<Card> hand = List.of();

    public HandOverviewScreen(GuiScreen parent, BlockPos tablePos, long gameId, UUID playerId,
        Consumer<Card> selection) {
        this(parent, tablePos, gameId, playerId, selection, () -> true);
    }

    public HandOverviewScreen(GuiScreen parent, BlockPos tablePos, long gameId, UUID playerId, Consumer<Card> selection,
        BooleanSupplier choiceActive) {
        this.parent = parent;
        this.panel = new TableScreenPanel(tablePos, gameId, playerId);
        this.playerId = playerId;
        this.selection = selection;
        this.choiceActive = choiceActive;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        messages.invalidateLayout();
        controlColumn = panel.sideLayout(width, height);
        int searchWidth = Math.round(panel.contentWidth(width) * 0.42f);
        search = new GuiTextField(
            fontRendererObj,
            panel.centerX(width) - searchWidth / 2,
            Math.round(height * 0.105f),
            searchWidth,
            Math.max(fontRendererObj.FONT_HEIGHT + 5, controlColumn.controlHeight() * 3 / 4));
        search.setMaxStringLength(80);
        search.setText(query);
        buttonList.clear();
        int footerTop = controlColumn.buttonTop(0);
        int pageWidth = Math.max(fontRendererObj.FONT_HEIGHT * 2, Math.round(panel.contentWidth(width) * 0.06f));
        int pageLeft = panel.contentLeft(width) + controlColumn.left();
        buttonList.add(new UiButton(0, pageLeft, footerTop, pageWidth, controlColumn.controlHeight(), "<"));
        buttonList.add(
            new UiButton(
                1,
                pageLeft + pageWidth + controlColumn.gap(),
                footerTop,
                pageWidth,
                controlColumn.controlHeight(),
                ">"));
        buttonList.add(
            new ChoiceButton(
                BTN_ENCYCLOPEDIA,
                controlColumn.left(),
                footerTop,
                controlColumn.width(),
                controlColumn.controlHeight(),
                Chat.translatable("gui.minopp.hand.encyclopedia")
                    .getFormattedText(),
                0xFF2A9F4A));
        buttonList.add(
            new ChoiceButton(
                2,
                width - controlColumn.left() - Math.round(panel.contentWidth(width) * 0.18f),
                footerTop,
                Math.round(panel.contentWidth(width) * 0.18f),
                controlColumn.controlHeight(),
                Chat.translatable("gui.minopp.hand.back")
                    .getFormattedText()));
        updateScreen();
    }

    private void filter() {
        grid.setCards(
            hand.stream()
                .filter(card -> CardTooltips.matches(shownGame.face(card), query))
                .toList());
        layoutGrid();
    }

    private void layoutGrid() {
        grid.layoutFromTopIn(
            panel.contentLeft(width),
            panel.contentWidth(width),
            height,
            24,
            search.yPosition + search.height + controlColumn.gap(),
            height - controlColumn.buttonTop(0) + fontRendererObj.FONT_HEIGHT + controlColumn.gap() * 2);
    }

    @Override
    public void updateScreen() {
        CardGame game = panel.game();
        if (game == null || !choiceActive.getAsBoolean()) {
            HandScreen.returnFromChoice();
            return;
        }
        CardPlayer player = game.deAmputate(playerId);
        if (shownGame != game) {
            int page = grid.page();
            shownGame = game;
            hand = ItemHandCards.sortedHand(shownGame, player);
            filter();
            grid.scroll(page);
        }
        layoutGrid();
        if (search != null) search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shownGame == null) return;
        panel.drawFrame(width, height);
        messages.draw(
            shownGame.tableEntity,
            playerId,
            controlColumn.left(),
            controlColumn.top(),
            controlColumn.width(),
            controlColumn.buttonTop(0) - controlColumn.gap() - controlColumn.top(),
            mouseX,
            mouseY);
        UiDraw.gradient(panel.contentLeft(width), 20, width, height, 0x90200404, 0xD0200404);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.overview")
                .getFormattedText(),
            panel.centerX(width),
            Math.round(height * 0.06f),
            0xFFFFE070);
        search.drawTextBox();
        grid.draw(ThemeManager.INSTANCE.selected(), shownGame.darkSide, mouseX, mouseY, uid -> false);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.page", grid.page() + 1, grid.pages())
                .getFormattedText(),
            panel.centerX(width),
            controlColumn.buttonTop(0) - fontRendererObj.FONT_HEIGHT - controlColumn.gap(),
            0xFFE8E8E8);
        for (GuiButton button : buttonList) {
            if (button.id == 0) button.enabled = grid.page() > 0;
            if (button.id == 1) button.enabled = grid.page() + 1 < grid.pages();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        Card hover = grid.hit(mouseX, mouseY, uid -> false);
        if (hover != null) UiDraw.tooltip(
            fontRendererObj,
            CardTooltips.lines(fontRendererObj, shownGame, hover, width),
            mouseX,
            mouseY,
            width,
            height);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        search.mouseClicked(mouseX, mouseY, button);
        if (search.isFocused()) return;
        if (messages.mousePressed(mouseX, mouseY, button)) return;
        if (button == 0) {
            Card card = grid.hit(mouseX, mouseY, uid -> false);
            if (card != null) {
                if (selection != null) selection.accept(card);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (mc.currentScreen != this) return;
        for (GuiButton control : buttonList) {
            if (control.visible && mouseX >= control.xPosition
                && mouseX < control.xPosition + control.width
                && mouseY >= control.yPosition
                && mouseY < control.yPosition + control.height) return;
        }
        GameControls.handleUtility(button - 100);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_ENCYCLOPEDIA) {
            if (choiceActive.getAsBoolean()) panel.openEncyclopedia(this);
        } else if (button.id == 2) returnToParent();
        else grid.scroll(button.id == 0 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (search.isFocused()) search.setFocused(false);
            else returnToParent();
            return;
        }
        if (search.isFocused()) {
            if (key == Keyboard.KEY_RETURN) search.setFocused(false);
            else if (search.textboxKeyTyped(character, key)) {
                query = search.getText();
                filter();
            }
            return;
        }
        if (GameControls.handleUtility(key)) return;
        GameControls.Action action = GameControls.actionFor(key);
        if (action == GameControls.Action.PREVIOUS || action == GameControls.Action.NEXT) {
            grid.scroll(action == GameControls.Action.PREVIOUS ? -1 : 1);
        } else if (key == Keyboard.KEY_F && action == null) search.setFocused(true);
        else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
            grid.scroll(key == Keyboard.KEY_PRIOR ? -1 : 1);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (mouseX < panel.contentLeft(width)) messages.scroll(mouseX, mouseY, -Integer.signum(wheel));
        else grid.scroll(-Integer.signum(wheel));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (button == 0) messages.mouseDragged(mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state >= 0) messages.mouseReleased();
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    private void returnToParent() {
        if (panel.game() != null && choiceActive.getAsBoolean()) mc.displayGuiScreen(parent);
        else HandScreen.returnFromChoice();
    }

    @Override
    public void onGuiClosed() {
        messages.mouseReleased();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
