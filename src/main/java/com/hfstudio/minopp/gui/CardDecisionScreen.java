package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardDecisions;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

@SideOnly(Side.CLIENT)
public class CardDecisionScreen extends GuiScreen {

    private final BlockPos tablePos;
    private final TableScreenPanel panel;
    private final UUID playerId, decisionId;
    private final CardGrid grid = new CardGrid();
    private final IntSet selected = new IntLinkedOpenHashSet();
    private final CardGridSelection selection = new CardGridSelection(grid, selected);
    private CardGame shownGame;
    private PendingDecision decision;
    private List<CardPlayer> targets = List.of();
    private int targetIndex;
    private boolean offered, waiting;
    private int needed = 1;

    public CardDecisionScreen(BlockPos tablePos, CardPlayer player, PendingDecision decision) {
        this.tablePos = tablePos;
        panel = new TableScreenPanel(tablePos, player.uuid);
        playerId = player.uuid;
        decisionId = decision.id;
        this.decision = decision;
    }

    public static void register() {
        for (String id : List.of(CardDecisions.RECYCLE, CardDecisions.SWAP, CardDecisions.DISCARD))
            DecisionScreens.register(id, CardDecisionScreen::new);
    }

    private int choiceBottom() {
        return height - panel.handPreviewHeight(width);
    }

    @Override
    public void initGui() {
        selection.release();
        buttonList.clear();
        panel.addHandButton(buttonList, width, height);
        buttonList.add(
            new UiButton(
                0,
                panel.centerX(width) - 40,
                choiceBottom() - 26,
                80,
                20,
                Chat.translatable("gui.minopp.choice.confirm")
                    .getFormattedText()));
        buttonList
            .add(new UiButton(1, panel.contentLeft(width) + 6, choiceBottom() - gridBottomInset() + 8, 30, 20, "<"));
        buttonList.add(new UiButton(2, width - 36, choiceBottom() - gridBottomInset() + 8, 30, 20, ">"));
        if (CardDecisions.RECYCLE.equals(decision.typeId)) buttonList.add(
            new UiButton(
                3,
                panel.centerX(width) - Math.min(200, panel.contentWidth(width) - 24) / 2,
                38,
                Math.min(200, panel.contentWidth(width) - 24),
                18,
                ""));
        if (CardDecisions.DISCARD.equals(decision.typeId)) {
            String randomLabel = Chat.translatable("gui.minopp.choice.random")
                .getFormattedText();
            int randomWidth = Math.max(80, fontRendererObj.getStringWidth(randomLabel) + 12);
            buttonList.add(
                new UiButton(
                    4,
                    panel.centerX(width) - randomWidth / 2,
                    choiceBottom() - 50,
                    randomWidth,
                    20,
                    randomLabel));
        }
        updateScreen();
    }

    @Override
    public void updateScreen() {
        CardGame game = panel.game();
        PendingDecision current = game == null ? null : game.decisionFor(playerId);
        if (current == null || !current.id.equals(decisionId)) {
            mc.displayGuiScreen(null);
            return;
        }
        if (game != shownGame) {
            selection.release();
            boolean first = shownGame == null;
            waiting = false;
            boolean nextStep = current.data.hasKey("offered");
            if (offered != nextStep) {
                selected.clear();
                waiting = false;
                offered = nextStep;
            }
            decision = current;
            shownGame = game;
            List<Card> cards = CardDecisions.cards(game, current);
            if (!selected.isEmpty()) {
                IntSet available = new IntOpenHashSet(cards.size());
                for (Card card : cards) available.add(card.uid);
                selected.retainAll(available);
            }
            int page = grid.page();
            grid.setCards(cards);
            grid.scroll(page);
            needed = Math.min(cards.size(), CardDecisions.count(current) - current.data.getIntArray("picked").length);
            targets = game.activePlayers();
            if (first) for (int i = 0; i < targets.size(); i++) {
                if (targets.get(i).uuid.equals(playerId)) targetIndex = i;
            }
            targetIndex = Math.min(targetIndex, Math.max(0, targets.size() - 1));
        }
        grid.layoutIn(
            panel.contentLeft(width),
            panel.contentWidth(width),
            height,
            24,
            64,
            gridBottomInset() + panel.handPreviewHeight(width));
        for (GuiButton button : buttonList) {
            if (button.id == 0) {
                button.enabled = !waiting && needed > 0 && selected.size() == needed;
                button.displayString = Chat.translatable("gui.minopp.choice.confirm_count", selected.size(), needed)
                    .getFormattedText();
                button.width = Math.max(
                    80,
                    fontRendererObj.getStringWidth(
                        Chat.translatable("gui.minopp.choice.confirm_count", needed, needed)
                            .getFormattedText())
                        + 12);
                button.xPosition = panel.centerX(width) - button.width / 2;
            }
            if (button.id == 1) button.enabled = grid.page() > 0;
            if (button.id == 2) button.enabled = grid.page() + 1 < grid.pages();
            if (button.id == 4) button.enabled = !waiting && needed > 0
                && needed <= grid.cards()
                    .size();
            if (button.id == 3 && !targets.isEmpty())
                button.displayString = Chat.translatable("gui.minopp.choice.recipient", targets.get(targetIndex).name)
                    .getFormattedText();
        }
    }

    private int gridBottomInset() {
        return CardDecisions.DISCARD.equals(decision.typeId) ? 94 : 70;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shownGame == null) return;
        if (waiting || !Mouse.isButtonDown(0)) selection.release();
        else if (selection.drag(mouseX, mouseY, Minecraft.getSystemTime())) updateScreen();
        UiDraw.gradient(panel.contentLeft(width), 20, width, height, 0xB0200404, 0xE0200404);
        String key = CardDecisions.SWAP.equals(decision.typeId)
            ? offered ? "gui.minopp.decision.swap_take" : "gui.minopp.decision.swap_give"
            : "gui.minopp.decision." + decision.typeId.substring(7);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable(key)
                .getFormattedText(),
            panel.centerX(width),
            22,
            0xFFFFE070);
        grid.draw(ThemeManager.INSTANCE.selected(), shownGame.darkSide, mouseX, mouseY, selected::contains);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.page", grid.page() + 1, grid.pages())
                .getFormattedText(),
            panel.centerX(width),
            choiceBottom() - gridBottomInset() + 14,
            0xFFE8E8E8);
        panel.draw(width, height);
        super.drawScreen(mouseX, mouseY, partialTicks);
        panel.drawHandPreview(width, height, mouseX, mouseY);
        Card hover = grid.hit(mouseX, mouseY, selected::contains);
        if (hover != null && !selection.isDragging()) UiDraw.tooltip(
            fontRendererObj,
            CardTooltips.lines(fontRendererObj, shownGame, hover, width),
            mouseX,
            mouseY,
            width,
            height);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && panel.browseHandPreview(width, height, x, y, 0)) return;
        if (button == 0) for (GuiButton control : buttonList) {
            if (control.visible && x >= control.xPosition
                && x < control.xPosition + control.width
                && y >= control.yPosition
                && y < control.yPosition + control.height) {
                super.mouseClicked(x, y, button);
                return;
            }
        }
        if (GameControls.actionFor(button - 100) != null) {
            keyTyped('\0', button - 100);
            return;
        }
        if (!waiting && button == 0 && selection.press(x, y, needed, Minecraft.getSystemTime())) {
            updateScreen();
            return;
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state == 0) {
            if (!waiting && selection.drag(mouseX, mouseY, Minecraft.getSystemTime())) updateScreen();
            selection.release();
        }
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    public void onGuiClosed() {
        selection.release();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == TableScreenPanel.HAND_BUTTON) {
            panel.openHand(this, this::choiceActive);
            return;
        }
        if (!choiceActive()) return;
        if (button.id == 0 && selected.size() == needed && !waiting) {
            selection.release();
            waiting = true;
            UUID target = CardDecisions.RECYCLE.equals(decision.typeId) && !targets.isEmpty()
                ? targets.get(targetIndex).uuid
                : null;
            C2SPlayCardPacket.Client.sendDecisionC2S(tablePos, decision, selected.toIntArray(), target);
        } else if (button.id == 4 && !waiting && CardDecisions.DISCARD.equals(decision.typeId)) {
            selection.selectRandom(needed);
        } else if (button.id == 3 && !targets.isEmpty()) targetIndex = (targetIndex + 1) % targets.size();
        else if (button.id == 1 || button.id == 2) grid.scroll(button.id == 1 ? -1 : 1);
        updateScreen();
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
        if (wheel != 0) grid.scroll(-Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (!choiceActive()) return;
        GameControls.Action action = GameControls.actionFor(key);
        if (key > 0 && Keyboard.isRepeatEvent()
            && action != GameControls.Action.PREVIOUS
            && action != GameControls.Action.NEXT) return;
        if (GameControls.handleUtility(key)) return;
        if (action == GameControls.Action.CONFIRM || action == GameControls.Action.PLAY) {
            for (GuiButton button : buttonList) if (button.id == 0 && button.enabled) {
                actionPerformed(button);
                break;
            }
        } else if (action == GameControls.Action.PREVIOUS || action == GameControls.Action.NEXT)
            grid.scroll(action == GameControls.Action.PREVIOUS ? -1 : 1);
        else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) grid.scroll(key == Keyboard.KEY_PRIOR ? -1 : 1);
    }

    private boolean choiceActive() {
        CardGame game = panel.game();
        PendingDecision current = game == null ? null : game.decisionFor(playerId);
        return current != null && current.id.equals(decisionId) && System.currentTimeMillis() < current.deadline;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
