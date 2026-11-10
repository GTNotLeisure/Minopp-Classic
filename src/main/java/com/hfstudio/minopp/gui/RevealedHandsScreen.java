package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

@SideOnly(Side.CLIENT)
public class RevealedHandsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final BlockPos tablePos;
    private final long gameId;
    private final CardGrid grid = new CardGrid();
    private final List<CardPlayer> players = new ArrayList<>();
    private final Object2IntMap<UUID> handPages = new Object2IntOpenHashMap<>();
    private CardGame shownGame;
    private UUID holderId;
    private int holderIndex, rosterPage, rosterSize, gridInset;
    private long nextExpiry;

    public RevealedHandsScreen(GuiScreen parent, BlockPos tablePos, long gameId) {
        this(parent, tablePos, gameId, null);
    }

    public RevealedHandsScreen(GuiScreen parent, BlockPos tablePos, long gameId, UUID holderId) {
        this.parent = parent;
        this.tablePos = tablePos;
        this.gameId = gameId;
        this.holderId = holderId;
    }

    public static boolean available(CardGame game, UUID viewer) {
        CardPlayer member = game.deAmputate(viewer);
        if (member == null || member.eliminated) return false;
        for (CardPlayer player : game.players) if (visible(game, player, viewer)) return true;
        return false;
    }

    public static boolean canView(CardGame game, UUID viewer, UUID holder) {
        CardPlayer member = game.deAmputate(viewer), player = game.deAmputate(holder);
        return member != null && !member.eliminated && player != null && visible(game, player, viewer);
    }

    private static boolean visible(CardGame game, CardPlayer player, UUID viewer) {
        if (player.uuid.equals(viewer) || player.eliminated
            || player.hand.isEmpty()
            || player.handVisibleUntil <= System.currentTimeMillis()) return false;
        for (Card card : player.hand) if (game.face(card)
            .isHidden()) return false;
        return true;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new UiButton(0, 6, 24, 24, 20, "<"));
        buttonList.add(new UiButton(1, width - 30, 24, 24, 20, ">"));
        buttonList.add(new UiButton(2, 6, height - 26, 30, 20, "<"));
        buttonList.add(new UiButton(3, 42, height - 26, 30, 20, ">"));
        buttonList.add(
            new UiButton(
                4,
                width - 86,
                height - 26,
                80,
                20,
                Chat.translatable("gui.done")
                    .getFormattedText()));
        if (refresh()) {
            layoutRoster();
            grid.layoutFromTop(width, height, 24, gridInset, 34);
        }
    }

    private boolean refresh() {
        BlockEntityMinoTable table = mc.theWorld == null ? null : ItemHandCards.getTable(mc.theWorld, tablePos);
        if (mc.thePlayer == null || table == null || table.game == null || table.game.id != gameId) {
            clear();
            mc.displayGuiScreen(null);
            return false;
        }
        if (shownGame == table.game && System.currentTimeMillis() < nextExpiry) return true;
        boolean first = shownGame == null;
        shownGame = table.game;
        UUID viewer = mc.thePlayer.getGameProfile()
            .getId();
        CardPlayer member = shownGame.deAmputate(viewer);
        if (member == null || member.eliminated) {
            clear();
            mc.displayGuiScreen(null);
            return false;
        }
        players.clear();
        nextExpiry = Long.MAX_VALUE;
        for (CardPlayer player : shownGame.players) if (visible(shownGame, player, viewer)) {
            players.add(player);
            nextExpiry = Math.min(nextExpiry, player.handVisibleUntil);
        }
        if (players.isEmpty()) {
            clear();
            mc.displayGuiScreen(parent);
            return false;
        }
        holderIndex = 0;
        for (int i = 0; i < players.size(); i++) if (players.get(i).uuid.equals(holderId)) holderIndex = i;
        if (first) rosterPage = holderIndex / (2 * Math.max(1, Math.min(5, (width - 80) / 70)));
        handPages.keySet()
            .removeIf(
                id -> players.stream()
                    .noneMatch(player -> player.uuid.equals(id)));
        layoutRoster();
        showPlayer();
        return true;
    }

    private void layoutRoster() {
        buttonList.removeIf(button -> button.id >= 100);
        int columns = Math.max(1, Math.min(5, (width - 80) / 70));
        rosterSize = columns * 2;
        int pages = Math.max(1, (players.size() + rosterSize - 1) / rosterSize);
        rosterPage = Math.min(rosterPage, pages - 1);
        int from = rosterPage * rosterSize, to = Math.min(players.size(), from + rosterSize);
        int rows = Math.max(1, (to - from + columns - 1) / columns);
        gridInset = 24 + rows * 22 + 20;
        int buttonWidth = Math.min(100, (width - 80) / columns - 4);
        for (int i = from; i < to; i++) {
            int index = i - from;
            int inRow = Math.min(columns, to - from - index / columns * columns);
            int x = (width - inRow * (buttonWidth + 4) + 4) / 2 + index % columns * (buttonWidth + 4);
            CardPlayer player = players.get(i);
            buttonList.add(
                new UiButton(
                    100 + i,
                    x,
                    24 + index / columns * 22,
                    buttonWidth,
                    18,
                    Chat.translatable("gui.minopp.reveal.player", player.name, player.hand.size())
                        .getFormattedText(),
                    i == holderIndex ? 0xFF2A7FD4 : 0));
        }
        for (GuiButton button : buttonList) {
            if (button.id == 0) button.enabled = rosterPage > 0;
            if (button.id == 1) button.enabled = rosterPage + 1 < pages;
            if (button.id < 2) button.visible = pages > 1;
        }
    }

    private void showPlayer() {
        CardPlayer player = players.get(holderIndex);
        if (holderId != null) handPages.put(holderId, grid.page());
        holderId = player.uuid;
        grid.setCards(ItemHandCards.sortedHand(shownGame, player));
        grid.layoutFromTop(width, height, 24, gridInset, 34);
        grid.scroll(handPages.getInt(holderId));
    }

    private void clear() {
        grid.setCards(List.of());
        players.clear();
        handPages.clear();
        shownGame = null;
        holderId = null;
    }

    @Override
    public void updateScreen() {
        refresh();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!refresh()) return;
        UiDraw.gradient(0, 0, width, height, 0x90200404, 0xD0200404);
        CardPlayer player = players.get(holderIndex);
        String title = Chat.translatable("gui.minopp.reveal.title")
            .getFormattedText();
        if (player.handVisibleUntil < Long.MAX_VALUE)
            title += " · " + Math.max(0, (player.handVisibleUntil - System.currentTimeMillis() + 999) / 1000) + "s";
        if (players.size() > rosterSize) title += " · " + Chat
            .translatable(
                "gui.minopp.reveal.players_page",
                rosterPage + 1,
                (players.size() + rosterSize - 1) / rosterSize)
            .getFormattedText();
        UiDraw.fittedCentered(fontRendererObj, title, width / 2f, 7, width - 20, 0xFFFFE070);
        UiDraw.fittedCentered(
            fontRendererObj,
            Chat.translatable("gui.minopp.reveal.player", player.name, player.hand.size())
                .getFormattedText(),
            width / 2f,
            grid.top() - 14,
            width - 20,
            0xFFFFFFFF);
        grid.draw(ThemeManager.INSTANCE.themeFor(holderId), shownGame.darkSide, mouseX, mouseY, uid -> false);
        UiDraw.fittedCentered(
            fontRendererObj,
            Chat.translatable("gui.minopp.hand.page", grid.page() + 1, grid.pages())
                .getFormattedText(),
            width / 2f,
            height - 20,
            width - 180,
            0xFFE8E8E8);
        for (GuiButton button : buttonList) {
            if (button.id == 2) button.enabled = grid.page() > 0;
            if (button.id == 3) button.enabled = grid.page() + 1 < grid.pages();
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
        else for (GuiButton button : buttonList) {
            if (button.id >= 100 && mouseX >= button.xPosition
                && mouseX < button.xPosition + button.width
                && mouseY >= button.yPosition
                && mouseY < button.yPosition + button.height) {
                UiDraw.tooltip(fontRendererObj, List.of(button.displayString), mouseX, mouseY, width, height);
                break;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!refresh() || !buttonList.contains(button)) return;
        if (button.id == 4) mc.displayGuiScreen(parent);
        else if (button.id < 2) {
            rosterPage += button.id == 0 ? -1 : 1;
            layoutRoster();
            grid.layoutFromTop(width, height, 24, gridInset, 34);
        } else if (button.id >= 100) {
            holderIndex = button.id - 100;
            layoutRoster();
            showPlayer();
        } else grid.scroll(button.id == 2 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) grid.scroll(key == Keyboard.KEY_PRIOR ? -1 : 1);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && refresh()) {
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (mouseY < gridInset - 18) {
                int pages = Math.max(1, (players.size() + rosterSize - 1) / rosterSize);
                rosterPage = Math.max(0, Math.min(pages - 1, rosterPage - Integer.signum(wheel)));
                layoutRoster();
                grid.layoutFromTop(width, height, 24, gridInset, 34);
            } else grid.scroll(-Integer.signum(wheel));
        }
    }

    @Override
    public void onGuiClosed() {
        clear();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
