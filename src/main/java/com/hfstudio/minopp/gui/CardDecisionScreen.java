package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardDecisions;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

@SideOnly(Side.CLIENT)
public class CardDecisionScreen extends GuiScreen {

    private final BlockPos tablePos;
    private final UUID playerId, decisionId;
    private final CardGrid grid = new CardGrid();
    private final IntSet selected = new IntLinkedOpenHashSet();
    private CardGame shownGame;
    private PendingDecision decision;
    private List<CardPlayer> targets = List.of();
    private int targetIndex;
    private boolean offered, waiting;
    private int needed = 1;

    public CardDecisionScreen(BlockPos tablePos, CardPlayer player, PendingDecision decision) {
        this.tablePos = tablePos;
        playerId = player.uuid;
        decisionId = decision.id;
        this.decision = decision;
    }

    public static void register() {
        for (String id : List.of(CardDecisions.RECYCLE, CardDecisions.SWAP, CardDecisions.DISCARD))
            DecisionScreens.register(id, CardDecisionScreen::new);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(
            new UiButton(
                0,
                width / 2 - 40,
                height - 26,
                80,
                20,
                Chat.translatable("gui.minopp.choice.confirm")
                    .getFormattedText()));
        buttonList.add(new UiButton(1, 6, height - 26, 30, 20, "<"));
        buttonList.add(new UiButton(2, 42, height - 26, 30, 20, ">"));
        if (CardDecisions.RECYCLE.equals(decision.typeId))
            buttonList.add(new UiButton(3, width / 2 - 100, 20, 200, 18, ""));
        updateScreen();
    }

    @Override
    public void updateScreen() {
        BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, tablePos);
        CardGame game = table == null ? null : table.game;
        PendingDecision current = game == null ? null : game.decisionFor(playerId);
        if (current == null || !current.id.equals(decisionId)) {
            mc.displayGuiScreen(null);
            return;
        }
        if (game != shownGame) {
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
            selected.removeIf(
                (int uid) -> cards.stream()
                    .noneMatch(card -> card.uid == uid));
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
        grid.layout(width, height, 24, 46, 46);
        for (GuiButton button : buttonList) {
            if (button.id == 0) button.enabled = !waiting && needed > 0 && selected.size() == needed;
            if (button.id == 1) button.enabled = grid.page() > 0;
            if (button.id == 2) button.enabled = grid.page() + 1 < grid.pages();
            if (button.id == 3 && !targets.isEmpty())
                button.displayString = Chat.translatable("gui.minopp.choice.recipient", targets.get(targetIndex).name)
                    .getFormattedText();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shownGame == null) return;
        UiDraw.gradient(0, 0, width, height, 0xB0200404, 0xE0200404);
        String key = CardDecisions.SWAP.equals(decision.typeId)
            ? offered ? "gui.minopp.decision.swap_take" : "gui.minopp.decision.swap_give"
            : "gui.minopp.decision." + decision.typeId.substring(7);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable(key)
                .getFormattedText(),
            width / 2,
            7,
            0xFFFFE070);
        grid.draw(ThemeManager.INSTANCE.selected(), shownGame.darkSide, mouseX, mouseY, selected::contains);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.page", grid.page() + 1, grid.pages())
                .getFormattedText(),
            width / 2,
            height - 39,
            0xFFE8E8E8);
        fontRendererObj.drawStringWithShadow(
            selected.size() + "/"
                + needed
                + "  "
                + Math.max(0, (decision.deadline - System.currentTimeMillis()) / 1000)
                + "s",
            width - 76,
            height - 20,
            0xFFE8E8E8);
        super.drawScreen(mouseX, mouseY, partialTicks);
        Card hover = grid.hit(mouseX, mouseY, selected::contains);
        if (hover != null) drawHoveringText(
            CardTooltips.lines(fontRendererObj, shownGame, hover, width),
            mouseX,
            mouseY,
            fontRendererObj);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (!waiting && button == 0) {
            Card card = grid.hit(x, y, selected::contains);
            if (card != null) {
                if (!selected.remove(card.uid) && selected.size() < needed) selected.add(card.uid);
                updateScreen();
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0 && selected.size() == needed && !waiting) {
            waiting = true;
            UUID target = CardDecisions.RECYCLE.equals(decision.typeId) && !targets.isEmpty()
                ? targets.get(targetIndex).uuid
                : null;
            for (int uid : selected) C2SPlayCardPacket.Client.sendDecisionC2S(tablePos, decision, uid, target);
        } else if (button.id == 3 && !targets.isEmpty()) targetIndex = (targetIndex + 1) % targets.size();
        else if (button.id == 1 || button.id == 2) grid.scroll(button.id == 1 ? -1 : 1);
        updateScreen();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) grid.scroll(-Integer.signum(wheel));
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) grid.scroll(key == Keyboard.KEY_PRIOR ? -1 : 1);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
