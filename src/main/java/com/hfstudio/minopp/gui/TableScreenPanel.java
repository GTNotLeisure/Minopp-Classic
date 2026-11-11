package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.ui.ChoiceButton;
import com.hfstudio.minopp.gui.ui.SideColumnLayout;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.TurnIndicator;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TableScreenPanel {

    public static final int HAND_BUTTON = -10;

    private final BlockPos tablePos;
    private final long gameId;
    private final UUID playerId;

    public TableScreenPanel(BlockPos tablePos, UUID playerId) {
        this(tablePos, currentGameId(tablePos), playerId);
    }

    public TableScreenPanel(BlockPos tablePos, long gameId, UUID playerId) {
        this.tablePos = tablePos;
        this.gameId = gameId;
        this.playerId = playerId;
    }

    private static long currentGameId(BlockPos tablePos) {
        Minecraft mc = Minecraft.getMinecraft();
        BlockEntityMinoTable table = mc.theWorld == null ? null : ItemHandCards.getTable(mc.theWorld, tablePos);
        return table == null || table.game == null ? -1 : table.game.id;
    }

    public CardGame game() {
        Minecraft mc = Minecraft.getMinecraft();
        BlockEntityMinoTable table = mc.theWorld == null ? null : ItemHandCards.getTable(mc.theWorld, tablePos);
        CardGame game = table == null ? null : table.game;
        CardPlayer player = game == null ? null : game.deAmputate(playerId);
        return game != null && game.id == gameId && player != null && !player.eliminated ? game : null;
    }

    public int contentLeft(int width) {
        return SideColumnLayout.boundary(width, 0.25f);
    }

    public SideColumnLayout sideLayout(int width, int height) {
        return SideColumnLayout.of(width, height, Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT, 0.25f);
    }

    public int contentWidth(int width) {
        return width - contentLeft(width);
    }

    public int centerX(int width) {
        return contentLeft(width) + contentWidth(width) / 2;
    }

    public int handPreviewHeight(int width) {
        return Math.min(64, Math.round(GameOverlayLayer.cardWidth(contentWidth(width)) * 8.9f / 5.6f / 2) + 28);
    }

    public void drawHandPreview(int width, int height, int mouseX, int mouseY) {
        CardGame game = game();
        if (game == null) return;
        int previewHeight = handPreviewHeight(width);
        UiDraw.scissor(contentLeft(width), height - previewHeight, contentWidth(width), previewHeight);
        GameOverlayLayer.INSTANCE.renderHandPreview(contentLeft(width), contentWidth(width), height);
        UiDraw.endScissor();
        int index = GameOverlayLayer.hitTest(mouseX - contentLeft(width), mouseY);
        List<Card> hand = HandScreen.visibleHand(game, game.deAmputate(playerId));
        Minecraft mc = Minecraft.getMinecraft();
        if (mouseX >= contentLeft(width) && mouseY >= height - previewHeight && index >= 0 && index < hand.size())
            UiDraw.tooltip(
                mc.fontRenderer,
                CardTooltips.lines(mc.fontRenderer, game, hand.get(index), width),
                mouseX,
                mouseY,
                width,
                height);
    }

    public boolean browseHandPreview(int width, int height, int mouseX, int mouseY, int direction) {
        if (mouseX < contentLeft(width) || mouseY < height - handPreviewHeight(width)) return false;
        CardGame game = game();
        if (game == null) return true;
        Minecraft mc = Minecraft.getMinecraft();
        List<Card> hand = HandScreen.visibleHand(game, game.deAmputate(playerId));
        if (hand.isEmpty() || mc.thePlayer.getHeldItem() == null) return true;
        int index = GameOverlayLayer.hitTest(mouseX - contentLeft(width), mouseY);
        if (direction != 0) {
            GameOverlayLayer.turnCarousel(direction);
            index = Math.floorMod(ItemHandCards.getClientHandIndex(mc.thePlayer) + direction, hand.size());
        }
        if (index >= 0 && index < hand.size()) ItemDataUtils.setHandIndex(mc.thePlayer.getHeldItem(), index);
        return true;
    }

    public void addHandButton(List<GuiButton> buttons, int width, int height) {
        SideColumnLayout side = sideLayout(width, height);
        buttons.add(
            new ChoiceButton(
                HAND_BUTTON,
                side.left(),
                side.buttonTop(0),
                side.width(),
                side.controlHeight(),
                Chat.translatable("gui.minopp.hand.overview")
                    .getFormattedText(),
                0xFF2A7FD4));
    }

    public void openHand(GuiScreen parent, BooleanSupplier choiceActive) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new HandOverviewScreen(parent, tablePos, gameId, playerId, null, choiceActive));
    }

    public void openEncyclopedia(GuiScreen parent) {
        if (game() != null) GameSettingsScreen.openEncyclopedia(tablePos, parent);
    }

    public void draw(int width, int height) {
        CardGame game = game();
        if (game == null) return;
        drawFrame(width, height);
        SideColumnLayout side = sideLayout(width, height);
        GameOverlayLayer.INSTANCE.renderStatus(
            game.tableEntity,
            playerId,
            side.left(),
            side.top(),
            side.width(),
            side.buttonTop(0) - side.gap() - side.top());
    }

    public void drawFrame(int width, int height) {
        CardGame game = game();
        if (game == null) return;
        int left = contentLeft(width);
        UiDraw.gradient(0, 0, left - 4, height, 0xD0181010, 0xE0200404);
        drawCountdown(game, playerId, centerX(width), 6, contentWidth(width) - 16);
        TurnIndicator.drawHud(
            game.tableEntity,
            centerX(width),
            Math.max(36, height - handPreviewHeight(width) - 18),
            contentWidth(width) - 24);
    }

    public static void drawCountdown(CardGame game, UUID playerId, int centerX, int top, int maxWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        PendingDecision decision = game.decisionFor(playerId);
        if (decision == null && game.isTurnTransitioning()) {
            UiDraw.fittedCentered(
                mc.fontRenderer,
                Chat.translatable("gui.minopp.play.turn_handoff", game.currentPlayer().name)
                    .getFormattedText(),
                centerX,
                top,
                maxWidth,
                0xFFFFD85A);
            return;
        }
        int seconds = decision == null ? game.turnSecondsRemaining()
            : (int) Math.max(0, (decision.deadline - System.currentTimeMillis() + 999) / 1000);
        if (seconds >= 0) UiDraw.fittedCentered(
            mc.fontRenderer,
            Chat.translatable(decision == null ? "gui.minopp.play.turn_time" : "gui.minopp.decision.time", seconds)
                .getFormattedText(),
            centerX,
            top,
            maxWidth,
            seconds <= 5 ? 0xFFFF7050 : 0xFFFFE070);
    }
}
