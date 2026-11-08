package com.hfstudio.minopp.gui;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;
import com.hfstudio.minopp.util.Pair;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GameOverlayLayer {

    // Some animation related stuff
    private double zoomAnimationProgress = 0;
    private double zoomAnimationTarget = 0;
    private final Map<Integer, Float> handCardCurrentXOff = new HashMap<>();

    public void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        World level = mc.theWorld;
        if (player == null || level == null) return;
        BlockPos handCardGamePos = ItemHandCards.getHandCardGamePos(player);
        BlockPos hitResultGamePos = BlockMinoTable.Client.getCursorPickedGame();
        BlockPos gamePos = (handCardGamePos != null) ? handCardGamePos : hitResultGamePos;
        BlockEntityMinoTable tableEntity = gamePos == null ? null : ItemHandCards.getTable(level, gamePos);
        if (tableEntity == null) {
            TurnDeadMan.setOutsideGame();
            MinoClient.handCardOverlayActive = false;
            handCardCurrentXOff.clear();
            return;
        }

        if (tableEntity.game == null) {
            renderGameInactive(tableEntity);
            TurnDeadMan.setOutsideGame();
            zoomAnimationProgress = zoomAnimationTarget = 0;
        } else {
            TurnDeadMan.tick(tableEntity.game, partialTicks);
            if (handCardGamePos == null || hitResultGamePos == null
                || Objects.equals(handCardGamePos, hitResultGamePos)) {
                // Only render active view when the player is looking at the same table as the hand cards
                renderGameActive(tableEntity);
            } else {
                zoomAnimationTarget = 0;
            }
        }
        performZoomAnimation(partialTicks);
        MinoClient.handCardOverlayActive = renderHandCards(partialTicks);
    }

    private void renderGameInactive(BlockEntityMinoTable tableEntity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI) return;
        int x = ClientConfig.display.guiXPosition, y = ClientConfig.display.guiYPosition;
        FontRenderer font = mc.fontRenderer;
        for (String part : tableEntity.state.message()
            .getFormattedText()
            .split("\n")) {
            drawStringWithBackdrop(font, part, x, y, 0xFFFFFFFF);
            y += font.FONT_HEIGHT;
        }
        y += font.FONT_HEIGHT;
        drawStringWithBackdrop(
            font,
            Chat.translatable("gui.minopp.play.start_hint")
                .getFormattedText(),
            x,
            y,
            0xFF00DD55);
        y += font.FONT_HEIGHT;
        drawStringWithBackdrop(
            font,
            Chat.translatable(
                "gui.minopp.play.seat_info",
                tableEntity.getPlayersList()
                    .size(),
                tableEntity.seatCount(),
                tableEntity.config.countDeckSize())
                .getFormattedText(),
            x,
            y,
            0xFFAAAAAA);
    }

    private void renderGameActive(BlockEntityMinoTable tableEntity) {
        Minecraft mc = Minecraft.getMinecraft();
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(mc.thePlayer);
        CardGame game = tableEntity.game;
        CardPlayer currentPlayer = game.currentPlayer();
        CardPlayer me = game.deAmputate(cardPlayer);
        // Zoom effect target
        if (currentPlayer.equals(cardPlayer) && !game.awaitingCutIn) {
            if (game.currentPlayerPhase == CardGame.PlayerActionPhase.DISCARD_HAND) {
                zoomAnimationTarget = 1;
            } else {
                if (zoomAnimationTarget < 1.01) {
                    zoomAnimationTarget = 1.5; // Zoom in first
                } else if (zoomAnimationProgress >= 1.5) { // Already zoomed in
                    zoomAnimationTarget = 1.05; // Zoom out, but not trigger zoom in again
                }
            }
        } else {
            zoomAnimationTarget = 0;
        }

        if (mc.gameSettings.hideGUI || !ClientConfig.display.enableGameHistoryGUI) return;

        int x = ClientConfig.display.guiXPosition, y = ClientConfig.display.guiYPosition;
        FontRenderer font = mc.fontRenderer;
        int lh = font.FONT_HEIGHT;
        drawStringWithBackdrop(
            font,
            Chat.translatable("gui.minopp.play.game_active")
                .getFormattedText() + " © Zbx1425",
            x,
            y,
            0xFF7090FF);
        y += lh;
        if (me != null && me.eliminated) {
            drawStringWithBackdrop(
                font,
                Chat.translatable("gui.minopp.play.eliminated")
                    .getFormattedText(),
                x,
                y,
                0xFFFF5555);
        } else if (currentPlayer.equals(cardPlayer)) {
            String key = game.pendingDraw > 0 ? "gui.minopp.play.must_answer"
                : "gui.minopp.play." + game.currentPlayerPhase.name()
                    .toLowerCase();
            drawStringWithBackdrop(
                font,
                Chat.translatable(key, game.pendingDraw)
                    .getFormattedText(),
                x,
                y,
                (System.currentTimeMillis() % 400 < 200) ? 0xFFFFFFFF : 0xFFFFFF00);
        } else {
            drawStringWithBackdrop(
                font,
                Chat.translatable("gui.minopp.play.turn_other", currentPlayer.name)
                    .getFormattedText(),
                x,
                y,
                0xFFAAAAAA);
        }
        y += lh;

        IChatComponent auxInfo = Chat
            .translatable("gui.minopp.play.direction." + (game.isAntiClockwise ? "ccw" : "cw"));
        if (game.pendingDraw > 0) {
            auxInfo = auxInfo.appendText(", ")
                .appendSibling(Chat.translatable("gui.minopp.play.draw_accumulate", game.pendingDraw));
        }
        if (game.darkSide) auxInfo.appendText(", ")
            .appendSibling(Chat.translatable("gui.minopp.play.dark_side"));
        drawStringWithBackdrop(font, auxInfo.getFormattedText(), x, y, 0xFFAAAAAA);
        y += lh;
        if (me != null && tableEntity.config.getBool(GameRules.FLEX_POWER)) {
            drawStringWithBackdrop(
                font,
                Chat.translatable(me.powerOn ? "gui.minopp.play.power_on" : "gui.minopp.play.power_off")
                    .getFormattedText(),
                x,
                y,
                me.powerOn ? 0xFF55FF55 : 0xFFFF5555);
            y += lh;
        }
        if (game.danceOwner != null) {
            CardPlayer owner = game.deAmputate(game.danceOwner);
            drawStringWithBackdrop(
                font,
                Chat.translatable("gui.minopp.play.dance_active", owner == null ? "?" : owner.name)
                    .getFormattedText(),
                x,
                y,
                0xFFFF88FF);
            y += lh;
        }
        if (game.balloonHolder != null) {
            drawStringWithBackdrop(
                font,
                Chat.translatable("gui.minopp.play.balloon_active")
                    .getFormattedText(),
                x,
                y,
                0xFFFF6666);
            y += lh;
        }
        if (me != null && game.links.containsKey(me.uuid)) {
            CardPlayer partner = game.deAmputate(game.links.get(me.uuid));
            drawStringWithBackdrop(
                font,
                Chat.translatable("gui.minopp.play.linked_with", partner == null ? "?" : partner.name)
                    .getFormattedText(),
                x,
                y,
                0xFF88CCFF);
            y += lh;
        }
        if (!game.decisions.isEmpty()) {
            drawStringWithBackdrop(
                font,
                Chat.translatable("game.minopp.play.waiting_decision")
                    .getFormattedText(),
                x,
                y,
                0xFFFFAA00);
            y += lh;
        }
        y += lh;

        CardFace top = game.topFace();
        IChatComponent topCardInfo = Chat.translatable(
            "gui.minopp.play.top_card",
            top.getDisplayName()
                .getFormattedText());
        if (top.isWild() || game.currentColor != top.color) {
            topCardInfo.appendText(", ")
                .appendSibling(
                    Chat.translatable(
                        "gui.minopp.play.top_card_wild_color",
                        Chat.translatable(game.currentColor.langKey())));
        }
        drawStringWithBackdrop(font, topCardInfo.getFormattedText(), x, y, 0xFFFFFFDD);
        y += lh;

        // Other players' hand sizes (useful with many players)
        StringBuilder counts = new StringBuilder();
        for (CardPlayer p : game.players) {
            if (counts.length() > 0) counts.append("  ");
            boolean cur = p.equals(currentPlayer);
            counts
                .append(
                    cur ? EnumChatFormatting.YELLOW
                        : (p.eliminated ? EnumChatFormatting.DARK_GRAY : EnumChatFormatting.GRAY))
                .append(p.name.length() > 8 ? p.name.substring(0, 8) : p.name)
                .append(':')
                .append(p.eliminated ? "x" : p.hand.size());
            if (p.team >= 0) counts.append(EnumChatFormatting.AQUA)
                .append('[')
                .append(p.team + 1)
                .append(']');
        }
        for (Object line : font.listFormattedStringToWidth(counts.toString(), 260)) {
            drawStringWithBackdrop(font, (String) line, x, y, 0xFFAAAAAA);
            y += lh;
        }
        y += lh;

        for (String part : tableEntity.state.message()
            .getFormattedText()
            .split("\n")) {
            drawStringWithBackdrop(font, part, x, y, 0xFFFFFFFF);
            y += lh;
        }
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        for (ListIterator<Pair<ActionMessage, Long>> it = tableEntity.clientMessageList
            .listIterator(tableEntity.clientMessageList.size()); it.hasPrevious();) {
            if (y > res.getScaledHeight() - lh - 40) break;
            Pair<ActionMessage, Long> entry = it.previous();
            long currentTime = System.currentTimeMillis();
            if (entry.getSecond() - 200 < currentTime) {
                it.remove();
            } else {
                int color = entry.getFirst()
                    .type()
                    .isEphemeral() ? 0x00FF0000 : 0x00AAAAAA;
                int alpha = Mth.clamp((int) (0xFF * (entry.getSecond() - currentTime) / 1000), 0x05, 0xFF);
                drawStringWithBackdrop(
                    font,
                    entry.getFirst()
                        .message()
                        .getFormattedText(),
                    x,
                    y,
                    alpha << 24 | color);
                y += lh;
            }
        }

        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ) == Mino.BLOCK_MINO_TABLE
            && currentPlayer.equals(cardPlayer)
            && !(mc.currentScreen instanceof PlayChoiceScreen)) {
            boolean isPass = BlockMinoTable.Client.isCursorHittingPile();
            String cursorMessage = switch (game.currentPlayerPhase) {
                case DISCARD_HAND -> isPass ? Chat.translatable("gui.minopp.play.cursor.pass_draw")
                    .getFormattedText()
                    : Chat.translatable("gui.minopp.play.cursor.play")
                        .getFormattedText();
                case DISCARD_DRAWN -> isPass ? Chat.translatable("gui.minopp.play.cursor.pass")
                    .getFormattedText()
                    : Chat.translatable("gui.minopp.play.cursor.play")
                        .getFormattedText();
            };
            String shoutMessage = Chat.translatable("gui.minopp.play.cursor.shout")
                .getFormattedText();
            String flexMessage = Chat.translatable("gui.minopp.play.cursor.flex")
                .getFormattedText();
            boolean isShouting = !isPass && BlockMinoTable.Client.isShoutModifierHeld();
            boolean isFlexing = !isPass && BlockMinoTable.Client.isFlexModifierHeld()
                && me != null
                && me.powerOn
                && tableEntity.config.getBool(GameRules.FLEX_POWER);
            boolean highlight = mc.theWorld.getTotalWorldTime() % 3L < 2L && isPass;
            int lines = 1 + (isShouting ? 1 : 0) + (isFlexing ? 1 : 0);
            int msgWidth = Math.max(
                font.getStringWidth(cursorMessage),
                Math.max(
                    isShouting ? font.getStringWidth(shoutMessage) : 0,
                    isFlexing ? font.getStringWidth(flexMessage) : 0));
            int msgHeight = lh * lines;
            GuiDraw.fill(
                width / 2 + 8,
                height / 2 - msgHeight / 2 - 2,
                width / 2 + msgWidth + 16,
                height / 2 + msgHeight / 2 + 3,
                highlight ? 0x80AAAA66 : 0x80000000);
            int ty = height / 2 - msgHeight / 2;
            GuiDraw.drawString(font, cursorMessage, width / 2 + 12, ty, highlight ? 0xFF222222 : 0xFFFFFFDD);
            if (isShouting) GuiDraw.drawString(font, shoutMessage, width / 2 + 12, ty += lh, 0xFFFFFFDD);
            if (isFlexing) GuiDraw.drawString(font, flexMessage, width / 2 + 12, ty + lh, 0xFFFFD040);
        }

        if (TurnDeadMan.isAlarmActive()) {
            if (mc.currentScreen instanceof PlayChoiceScreen) return;
            String deadManMessage = Chat.translatable("gui.minopp.play.cursor.dead_man")
                .getFormattedText();
            boolean highlight = mc.theWorld.getTotalWorldTime() % 3L < 2L;
            int msgWidth = font.getStringWidth(deadManMessage);
            GL11.glPushMatrix();
            GL11.glTranslatef((float) (width / 2), (float) (height / 2 + 12), 0);
            GL11.glScalef(1.5f, 1.5f, 1);
            GuiDraw.fill(-msgWidth / 2 - 4, 0, msgWidth / 2 + 4, lh + 4, highlight ? 0x80AAAA66 : 0x80000000);
            GuiDraw.drawString(font, deadManMessage, -msgWidth / 2, 2, highlight ? 0xFF222222 : 0xFFFFFFDD);
            GL11.glPopMatrix();
        }
    }

    private static void drawStringWithBackdrop(FontRenderer font, String text, int x, int y, int color) {
        int a = (int) (((color >>> 24) & 0xFF) * 0.4f);
        GuiDraw.fill(x - 2, y, x + font.getStringWidth(text) + 2, y + font.FONT_HEIGHT, a << 24);
        GuiDraw.drawString(font, text, x, y, color);
    }

    /**
     * Render hand cards on the screen
     *
     * @return whether the hand cards are rendered
     */
    private boolean renderHandCards(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI) return false;

        FontRenderer font = mc.fontRenderer;
        EntityClientPlayerMP player = mc.thePlayer;
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(player);
        if (gamePos == null) return false;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(mc.theWorld, gamePos);
        if (tableEntity == null || tableEntity.game == null) return false;
        CardGame game = tableEntity.game;

        CardPlayer realPlayer = game.deAmputate(ItemHandCards.getCardPlayer(player));
        if (realPlayer == null || realPlayer.hand.isEmpty()) return false;

        int myPlayerIndex = game.players.indexOf(realPlayer);
        boolean isMyTurn = myPlayerIndex == game.currentPlayerIndex && !game.awaitingCutIn;
        final int CARD_V_SPACING = realPlayer.hand.size() > 20 ? 12 : 20;
        final int CARD_WIDTH = (int) (100.0 * Mth.lerp(zoomAnimationProgress, 0.93, 1.0));
        final int CARD_HEIGHT = (int) (CARD_WIDTH * 8.9 / 5.6);
        String theme = ThemeManager.INSTANCE.selected();

        List<Card> hand = ItemHandCards.sortedHand(game, realPlayer);
        int clientHandIndex = Mth.clamp(ItemHandCards.getClientHandIndex(player), 0, hand.size() - 1);
        handCardCurrentXOff.keySet()
            .removeIf(
                uid -> hand.stream()
                    .noneMatch(c -> c.uid == uid));

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        int handSize = hand.size();
        int selectedCardYRaw = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - clientHandIndex));
        int cardDrawOffset = selectedCardYRaw < 20 ? 20 - selectedCardYRaw : 0;
        Random cardRandom = new Random(handSize);
        GL11.glEnable(GL11.GL_BLEND);
        for (int i = 0; i < handSize; i++) {
            Card card = hand.get(i);
            CardFace face = game.face(card);
            int targetXOff = (i == clientHandIndex ? -30 : 0) + cardRandom.nextInt(7) - 3;
            Float currentXOffBoxed = handCardCurrentXOff.get(card.uid);
            float currentXOff = currentXOffBoxed == null ? CARD_WIDTH + 10 : currentXOffBoxed;
            int x = width - 10 - CARD_WIDTH + (int) currentXOff;
            handCardCurrentXOff.put(card.uid, (float) Mth.lerp(8 * 0.05 * partialTicks, currentXOff, targetXOff));
            int y = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - i)) + cardDrawOffset;
            if (i == clientHandIndex && isMyTurn) {
                String cardName = face.getDisplayName()
                    .getFormattedText();
                GuiDraw.drawString(font, cardName, x - font.getStringWidth(cardName) - 10, y + 10, 0xFFFFFFDD);
            }
            GuiDraw.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xFF222222);
            CardRenderer.drawFace2D(face, theme, x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2);

            boolean playable = isMyTurn && game.canPlay(realPlayer, card);
            float shadowAlpha = !isMyTurn ? 0.5f : (playable ? 0f : 0.3f);
            GuiDraw.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0x222222 | ((int) (0xFF * shadowAlpha) << 24));

            if (game.awaitingCutIn && game.currentPlayerIndex != myPlayerIndex && game.isIdenticalToTop(card)) {
                float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 150.0));
                int highlightAlpha = (int) (0x90 * pulse);
                GuiDraw.fill(x - 2, y - 2, x + CARD_WIDTH + 2, y + CARD_HEIGHT + 2, (highlightAlpha << 24) | 0xFFD700);
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
        return true;
    }

    private void performZoomAnimation(float partialTicks) {
        if (Math.abs(zoomAnimationTarget - zoomAnimationProgress) < 0.01) {
            zoomAnimationProgress = zoomAnimationTarget;
        } else {
            zoomAnimationProgress += (zoomAnimationTarget - zoomAnimationProgress) * 8 * 0.05 * partialTicks;
        }
        MinoClient.globalFovModifier = Mth.lerp(Mth.clamp(zoomAnimationProgress, 0, 1), 1.0, 0.97);
    }

    public static final GameOverlayLayer INSTANCE = new GameOverlayLayer();
}
