package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.SearchMatch;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class DebugCardScreen extends GuiScreen {

    private final GuiScreen parent;
    private final BlockPos tablePos;
    private final long gameId;
    private final UUID playerId;
    private final Consumer<Card> selection;
    private final CardGrid grid = new CardGrid();
    private GuiTextField search;
    private String query = "";
    private CardGame shownGame;
    private List<Card> hand = List.of();

    public DebugCardScreen(GuiScreen parent, BlockPos tablePos, long gameId, UUID playerId, Consumer<Card> selection) {
        this.parent = parent;
        this.tablePos = tablePos;
        this.gameId = gameId;
        this.playerId = playerId;
        this.selection = selection;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        search = new GuiTextField(fontRendererObj, Math.max(8, width / 2 - 100), 22, Math.min(200, width - 16), 14);
        search.setMaxStringLength(80);
        search.setText(query);
        buttonList.clear();
        buttonList.add(new UiButton(0, 6, height - 26, 30, 20, "<"));
        buttonList.add(new UiButton(1, 42, height - 26, 30, 20, ">"));
        buttonList.add(
            new UiButton(
                2,
                width - 86,
                height - 26,
                80,
                20,
                Chat.translatable("gui.cancel")
                    .getFormattedText()));
        updateScreen();
    }

    private void filter() {
        grid.setCards(
            hand.stream()
                .filter(
                    card -> SearchMatch.matches(
                        card.face(shownGame.darkSide)
                            .getDisplayName()
                            .getUnformattedText(),
                        query))
                .toList());
        grid.layout(width, height, 24);
    }

    @Override
    public void updateScreen() {
        BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, tablePos);
        CardPlayer player = table == null || table.game == null ? null : table.game.deAmputate(playerId);
        if (player == null || table.game.id != gameId || !table.game.isDebugger(player)) {
            mc.displayGuiScreen(null);
            return;
        }
        if (shownGame != table.game) {
            shownGame = table.game;
            hand = ItemHandCards.sortedHand(shownGame, player);
            filter();
        }
        grid.layout(width, height, 24);
        if (search != null) search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shownGame == null) return;
        UiDraw.gradient(0, 0, width, height, 0x90200404, 0xD0200404);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.debug.card_jump")
                .getFormattedText(),
            width / 2,
            7,
            0xFFFFE070);
        search.drawTextBox();
        grid.draw(ThemeManager.INSTANCE.selected(), shownGame.darkSide, mouseX, mouseY, uid -> false);
        GuiDraw.drawCenteredString(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.page", grid.page() + 1, grid.pages())
                .getFormattedText(),
            width / 2,
            height - 20,
            0xFFE8E8E8);
        for (GuiButton button : buttonList) {
            if (button.id == 0) button.enabled = grid.page() > 0;
            if (button.id == 1) button.enabled = grid.page() + 1 < grid.pages();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        Card hover = grid.hit(mouseX, mouseY, uid -> false);
        if (hover != null) drawHoveringText(
            CardTooltips.lines(fontRendererObj, shownGame, hover, width),
            mouseX,
            mouseY,
            fontRendererObj);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        search.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            Card card = grid.hit(mouseX, mouseY, uid -> false);
            if (card != null) {
                selection.accept(card);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) mc.displayGuiScreen(parent);
        else grid.scroll(button.id == 0 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (search.textboxKeyTyped(character, key)) {
            query = search.getText();
            filter();
        } else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
            grid.scroll(key == Keyboard.KEY_PRIOR ? -1 : 1);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) grid.scroll(-Integer.signum(wheel));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
