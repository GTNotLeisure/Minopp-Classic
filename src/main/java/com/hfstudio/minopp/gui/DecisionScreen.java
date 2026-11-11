package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.DecisionTypes;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.ui.ChoiceButton;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DecisionScreen extends GuiScreen {

    private final BlockPos gamePos;
    private final TableScreenPanel panel;
    private final CardPlayer self;
    private final PendingDecision.Type type;
    private final PendingDecision decision;
    private PlayerPicker picker;
    private int customPage;
    private RadialChoiceLayout choiceRing;
    private final ChoiceKeyNavigation navigation = new ChoiceKeyNavigation();

    public DecisionScreen(BlockPos gamePos, CardPlayer self, PendingDecision decision) {
        this.gamePos = gamePos;
        this.panel = new TableScreenPanel(gamePos, self.uuid);
        this.self = self;
        this.type = decision.type;
        this.decision = decision;
    }

    private CardGame game() {
        return panel.game();
    }

    private int choiceBottom() {
        return height - panel.handPreviewHeight(width);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        panel.addHandButton(buttonList, width, height);
        CardGame game = game();
        choiceRing = null;
        navigation.setOptions(List.of());
        switch (type) {
            case CUSTOM -> {
                if (game == null) return;
                List<DecisionTypes.Option> options = DecisionTypes.get(decision.typeId)
                    .handler()
                    .options(game, decision);
                int longest = options.stream()
                    .mapToInt(
                        option -> fontRendererObj.getStringWidth(
                            Chat.translatable(option.labelKey())
                                .getFormattedText())
                            + 16)
                    .max()
                    .orElse(22);
                int radius = Math
                    .min(120, Math.min(width / 2 - panel.contentLeft(width) - 8, choiceBottom() - height / 2 - 4));
                int pageSize = Math.max(
                    2,
                    Math.min(8, (int) (2 * Math.PI * Math.max(12, radius) / Math.hypot(Math.min(160, longest), 20))));
                int pages = Math.max(1, (options.size() + pageSize - 1) / pageSize);
                customPage = Math.floorMod(customPage, pages);
                int from = customPage * pageSize, to = Math.min(options.size(), from + pageSize);
                for (int i = from; i < to; i++) {
                    DecisionTypes.Option option = options.get(i);
                    buttonList.add(
                        new ChoiceButton(
                            option.value(),
                            0,
                            0,
                            80,
                            20,
                            Chat.translatable(option.labelKey())
                                .getFormattedText()));
                }
                if (pages > 1) {
                    buttonList.add(new UiButton(-3, panel.contentLeft(width) + 8, 24, 30, 18, "<"));
                    buttonList.add(new UiButton(-4, width - 38, 24, 30, 18, ">"));
                }
            }
            case CHALLENGE -> {
                buttonList.add(
                    new ChoiceButton(
                        1,
                        0,
                        0,
                        100,
                        20,
                        Chat.translatable("gui.minopp.decision.do_challenge")
                            .getFormattedText()));
                buttonList.add(
                    new ChoiceButton(
                        0,
                        0,
                        0,
                        100,
                        20,
                        Chat.translatable("gui.minopp.decision.accept")
                            .getFormattedText()));
            }
            case ROULETTE_COLOR -> {
                List<CardColor> colors = game == null ? CardColor.LIGHT : game.colors();
                for (int i = 0; i < colors.size(); i++) {
                    CardColor c = colors.get(i);
                    buttonList.add(
                        new ChoiceButton(
                            c.ordinal(),
                            0,
                            0,
                            60,
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
                int page = picker == null ? 0 : picker.page();
                List<UUID> selectedTargets = picker == null ? List.of() : List.copyOf(picker.selected);
                picker = new PlayerPicker(others, sizes, 1);
                for (CardPlayer candidate : others)
                    if (selectedTargets.contains(candidate.uuid)) picker.selected.add(candidate.uuid);
                picker.layoutRadial(
                    width / 2,
                    height / 2,
                    panel.contentLeft(width) + 8,
                    48,
                    width - 8,
                    choiceBottom() - 4);
                picker.scroll(page);
                buttonList.add(
                    new UiButton(
                        -2,
                        panel.contentLeft(width) + 8,
                        24,
                        80,
                        20,
                        Chat.translatable("gui.minopp.choice.confirm")
                            .getFormattedText()));
            }
        }
        if (type != PendingDecision.Type.POINT_TAKEN) {
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
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == TableScreenPanel.HAND_BUTTON) {
            panel.openHand(this, this::choiceActive);
            return;
        }
        if (!choiceActive()) return;
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
        if (picker != null && picker.click(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        navigation.updatePointer(mouseX, mouseY);
        if (type == PendingDecision.Type.CUSTOM)
            UiDraw.gradient(panel.contentLeft(width), 0, width, height, 0x00200404, 0xB0200404);
        else UiDraw.gradient(panel.contentLeft(width), 0, width, height, 0xB0601010, 0xE0200404);
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
        int y = choiceRing == null ? 48 : choiceRing.top();
        UiDraw.fittedCentered(
            fontRendererObj,
            title,
            choiceRing == null ? panel.centerX(width) : width / 2,
            y,
            panel.contentWidth(width) - 24,
            0xFFFFFFFF);
        panel.draw(width, height);
        if (choiceRing != null) choiceRing.draw();
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
        navigation.drawFocus();
        super.drawScreen(mouseX, mouseY, partialTicks);
        panel.drawHandPreview(width, height, mouseX, mouseY);
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
        if (picker != null && wheel != 0) picker.scroll(-Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (!choiceActive()) return;
        GameControls.Action action = GameControls.actionFor(key);
        if (key > 0 && Keyboard.isRepeatEvent()
            && action != GameControls.Action.PREVIOUS
            && action != GameControls.Action.NEXT) return;
        if (action == GameControls.Action.DOUBT && type == PendingDecision.Type.CHALLENGE) {
            C2SPlayCardPacket.Client.sendDecisionC2S(gamePos, decision, 1, null);
            return;
        }
        if (GameControls.handleUtility(key)) return;
        if (action == GameControls.Action.PREVIOUS || action == GameControls.Action.NEXT) {
            int direction = action == GameControls.Action.PREVIOUS ? -1 : 1;
            if (picker == null) navigation.move(direction);
            else picker.scroll(direction);
            return;
        }
        if (action == GameControls.Action.CONFIRM || action == GameControls.Action.PLAY) {
            GuiButton selected = navigation.selected();
            if (selected != null) actionPerformed(selected);
            else {
                int confirm = picker != null ? -2 : type == PendingDecision.Type.CHALLENGE ? 0 : -1;
                for (GuiButton button : buttonList) if (button.id == confirm && button.enabled) {
                    actionPerformed(button);
                    break;
                }
            }
            return;
        }
        if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
            int direction = key == Keyboard.KEY_PRIOR ? -1 : 1;
            if (picker != null) picker.scroll(direction);
            else if (type == PendingDecision.Type.CUSTOM) {
                customPage += direction;
                initGui();
            }
            return;
        }
        if (action == null) super.keyTyped(character, key);
    }

    private boolean choiceActive() {
        CardGame game = game();
        PendingDecision current = game == null ? null : game.decisionFor(self.uuid);
        return current != null && !current.isAnswered()
            && current.id.equals(decision.id)
            && System.currentTimeMillis() < current.deadline;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
