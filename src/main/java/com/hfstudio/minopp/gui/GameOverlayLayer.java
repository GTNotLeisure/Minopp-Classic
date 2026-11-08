package com.hfstudio.minopp.gui;

import java.util.ArrayList;
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
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.BlockEntityMinoTableRenderer;
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
    private final Map<Long, Float> handCardCurrentXOff = new HashMap<>();

    public void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        World level = mc.theWorld;
        if (player == null || level == null) return;
        BlockPos handCardGamePos = ItemHandCards.getHandCardGamePos(player);
        BlockPos hitResultGamePos = BlockMinoTable.Client.getCursorPickedGame();
        BlockPos gamePos = (handCardGamePos != null) ? handCardGamePos : hitResultGamePos;
        if (gamePos == null) {
            TurnDeadMan.setOutsideGame();
            MinoClient.handCardOverlayActive = false;
            handCardCurrentXOff.clear();
            return;
        }
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(level, gamePos);
        if (tableEntity == null) {
            TurnDeadMan.setOutsideGame();
            MinoClient.handCardOverlayActive = false;
            handCardCurrentXOff.clear();
            return;
        }

        if (tableEntity.game == null) {
            renderGameInactive(partialTicks, tableEntity);
            TurnDeadMan.setOutsideGame();
            zoomAnimationProgress = zoomAnimationTarget = 0;
        } else {
            TurnDeadMan.tick(tableEntity.game, partialTicks);
            if (handCardGamePos == null || hitResultGamePos == null
                || Objects.equals(handCardGamePos, hitResultGamePos)) {
                // Only render active view when the player is looking at the same table as the hand cards
                renderGameActive(partialTicks, tableEntity);
            } else {
                zoomAnimationTarget = 0;
            }
        }
        performZoomAnimation(partialTicks, tableEntity);
        MinoClient.handCardOverlayActive = renderHandCards(partialTicks);
    }

    private void renderGameInactive(float partialTicks, BlockEntityMinoTable tableEntity) {
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
    }

    private void renderGameActive(float partialTicks, BlockEntityMinoTable tableEntity) {
        Minecraft mc = Minecraft.getMinecraft();
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(mc.thePlayer);
        CardGame game = tableEntity.game;
        CardPlayer currentPlayer = game.players.get(game.currentPlayerIndex);
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

        // defines game GUI position, listing game actions (by default top left), controlled through config
        int x = ClientConfig.display.guiXPosition, y = ClientConfig.display.guiYPosition;

        FontRenderer font = mc.fontRenderer;
        drawStringWithBackdrop(
            font,
            Chat.translatable("gui.minopp.play.game_active")
                .getFormattedText() + " © Zbx1425",
            x,
            y,
            0xFF7090FF);
        y += font.FONT_HEIGHT;
        if (currentPlayer.equals(cardPlayer)) {
            drawStringWithBackdrop(
                font,
                Chat.translatable(
                    "gui.minopp.play." + game.currentPlayerPhase.name()
                        .toLowerCase())
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
        y += font.FONT_HEIGHT;

        IChatComponent auxInfo = Chat
            .translatable("gui.minopp.play.direction." + (game.isAntiClockwise ? "ccw" : "cw"));
        if (game.drawCount > 0) {
            auxInfo = auxInfo.appendText(", ")
                .appendSibling(Chat.translatable("gui.minopp.play.draw_accumulate", game.drawCount));
        }
        drawStringWithBackdrop(font, auxInfo.getFormattedText(), x, y, 0xFFAAAAAA);
        y += font.FONT_HEIGHT * 2;

        IChatComponent topCardInfo = Chat.translatable(
            "gui.minopp.play.top_card",
            game.topCard.getDisplayName()
                .getFormattedText());
        if (game.topCard.suit == Card.Suit.WILD) {
            topCardInfo.appendText(", ")
                .appendSibling(
                    Chat.translatable(
                        "gui.minopp.play.top_card_wild_color",
                        Chat.translatable(
                            "game.minopp.card.suit." + game.topCard.getEquivSuit()
                                .name()
                                .toLowerCase())));
        }
        drawStringWithBackdrop(font, topCardInfo.getFormattedText(), x, y, 0xFFFFFFDD);
        y += font.FONT_HEIGHT * 2;

        for (String part : tableEntity.state.message()
            .getFormattedText()
            .split("\n")) {
            drawStringWithBackdrop(font, part, x, y, 0xFFFFFFFF);
            y += font.FONT_HEIGHT;
        }
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        for (ListIterator<Pair<ActionMessage, Long>> it = tableEntity.clientMessageList
            .listIterator(tableEntity.clientMessageList.size()); it.hasPrevious();) {
            if (y > res.getScaledHeight() - font.FONT_HEIGHT - 40) {
                break;
            }
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
                y += font.FONT_HEIGHT;
            }
        }

        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            if (mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ) == Mino.BLOCK_MINO_TABLE) {
                boolean isPass = BlockMinoTable.Client.isCursorHittingPile();
                if (currentPlayer.equals(cardPlayer) && !(mc.currentScreen instanceof SwapHandSelectionScreen)) {
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
                    boolean isShouting = !isPass && BlockMinoTable.Client.isShoutModifierHeld();
                    boolean highlight = mc.theWorld.getTotalWorldTime() % 3L < 2L && isPass;
                    int msgWidth = Math
                        .max(font.getStringWidth(cursorMessage), isShouting ? font.getStringWidth(shoutMessage) : 0);
                    int msgHeight = isShouting ? font.FONT_HEIGHT * 2 : font.FONT_HEIGHT;
                    GuiDraw.fill(
                        width / 2 + 8,
                        height / 2 - msgHeight / 2 - 2,
                        width / 2 + msgWidth + 16,
                        height / 2 + msgHeight / 2 + 3,
                        highlight ? 0x80AAAA66 : 0x80000000);
                    GuiDraw.drawString(
                        font,
                        cursorMessage,
                        width / 2 + 12,
                        height / 2 - msgHeight / 2,
                        highlight ? 0xFF222222 : 0xFFFFFFDD);
                    if (isShouting) {
                        GuiDraw.drawString(
                            font,
                            shoutMessage,
                            width / 2 + 12,
                            height / 2 - msgHeight / 2 + font.FONT_HEIGHT,
                            highlight ? 0xFF222222 : 0xFFFFFFDD);
                    }
                }
            }
        }

        if (TurnDeadMan.isAlarmActive()) {
            if (mc.currentScreen instanceof SwapHandSelectionScreen) return;
            String deadManMessage = Chat.translatable("gui.minopp.play.cursor.dead_man")
                .getFormattedText();
            boolean highlight = mc.theWorld.getTotalWorldTime() % 3L < 2L;
            int msgWidth = font.getStringWidth(deadManMessage);
            int msgHeight = font.FONT_HEIGHT;
            GL11.glPushMatrix();
            GL11.glTranslatef((float) (width / 2), (float) (height / 2 + 12), 0);
            GL11.glScalef(1.5f, 1.5f, 1);
            GuiDraw.fill(-msgWidth / 2 - 4, 0, msgWidth / 2 + 4, msgHeight + 4, highlight ? 0x80AAAA66 : 0x80000000);
            GuiDraw.drawString(font, deadManMessage, -msgWidth / 2, 2, highlight ? 0xFF222222 : 0xFFFFFFDD);
            GL11.glPopMatrix();
        }
    }

    private static void drawStringWithBackdrop(FontRenderer font, String text, int x, int y, int color) {
        int a = (int) (((color >>> 24) & 0xFF) * 0.4f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int backdrop = a << 24 | ((r * 0) << 16) | ((g * 0) << 8) | (b * 0);
        GuiDraw.fill(x - 2, y, x + font.getStringWidth(text) + 2, y + font.FONT_HEIGHT, backdrop);
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
        if (tableEntity == null) return false;
        if (tableEntity.game == null) return false;

        CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
        CardPlayer realPlayer = tableEntity.game.players.stream()
            .filter(p -> p.equals(playerWithoutHand))
            .findFirst()
            .orElse(null);
        if (realPlayer == null) return false;

        int myPlayerIndex = tableEntity.game.players.indexOf(realPlayer);
        boolean isMyTurn = myPlayerIndex == tableEntity.game.currentPlayerIndex && !tableEntity.game.awaitingCutIn;
        int currentPlayer = tableEntity.game.currentPlayerIndex;
        final int CARD_V_SPACING = 20;
        final int CARD_WIDTH = (int) (100.0 * Mth.lerp(zoomAnimationProgress, 0.93, 1.0));
        final int CARD_HEIGHT = (int) (CARD_WIDTH * 8.9 / 5.6);

        int clientHandIndex = Mth.clamp(ItemHandCards.getClientHandIndex(player), 0, realPlayer.hand.size() - 1);

        realPlayer.hand.sort(Card::compareTo);
        List<Long> handCardHashes = new ArrayList<>();
        for (Card card : realPlayer.hand) {
            if (!handCardHashes.isEmpty()
                && card.hashCode() == (handCardHashes.get(handCardHashes.size() - 1) & 0xFFFFFFFFL)) {
                handCardHashes.add(handCardHashes.get(handCardHashes.size() - 1) + 0x100000000L);
            } else {
                handCardHashes.add((long) card.hashCode());
            }
        }
        handCardCurrentXOff.keySet()
            .removeIf(hash -> !handCardHashes.contains(hash));

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        int handSize = realPlayer.hand.size();
        int selectedCardYRaw = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - clientHandIndex));
        int cardDrawOffset = selectedCardYRaw < 20 ? 20 - selectedCardYRaw : 0;
        Random cardRandom = new Random(handSize);
        GL11.glEnable(GL11.GL_BLEND);
        for (int i = 0; i < handSize; i++) {
            int targetXOff = (i == clientHandIndex ? -30 : 0) + cardRandom.nextInt(7) - 3;
            Float currentXOffBoxed = handCardCurrentXOff.get(handCardHashes.get(i));
            float currentXOff = currentXOffBoxed == null ? CARD_WIDTH + 10 : currentXOffBoxed;
            int x = width - 10 - CARD_WIDTH + (int) currentXOff;
            handCardCurrentXOff
                .put(handCardHashes.get(i), (float) Mth.lerp(8 * 0.05 * partialTicks, currentXOff, targetXOff));
            int y = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - i)) + cardDrawOffset;
            Card card = realPlayer.hand.get(i);
            if (i == clientHandIndex && isMyTurn) {
                String cardName = card.getDisplayName()
                    .getFormattedText();
                GuiDraw.drawString(font, cardName, x - font.getStringWidth(cardName) - 10, y + 10, 0xFFFFFFDD);
            }
            GuiDraw.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xFF222222);
            GuiDraw.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, 0xFFDDDDDD);

            float cardU = BlockEntityMinoTableRenderer.getAtlasU(card);
            float cardV = card.suit.ordinal() * 25;
            int cardUW = 16;
            int cardVH = 25;

            float shadowAlpha = (!isMyTurn || tableEntity.game.awaitingCutIn) ? 0.5f : 0f;

            GL11.glColor4f(1, 1, 1, 1);
            GuiDraw.blit(
                BlockEntityMinoTableRenderer.DECK_ATLAS,
                x + 5,
                y + 5,
                CARD_WIDTH - 10,
                CARD_HEIGHT - 10,
                cardU + 1,
                cardV + 1,
                cardUW - 2,
                cardVH - 2,
                256,
                128);
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 7, y + 7, 0);
            GL11.glScalef(1.5f, 1.5f, 1);
            if (card.family == Card.Family.REVERSE) {
                GuiDraw.blit(BlockEntityMinoTableRenderer.DECK_ATLAS, 0, 0, 10, 10, 208, 0, 10, 10, 256, 128);
            } else if (card.family == Card.Family.SKIP) {
                GuiDraw.blit(BlockEntityMinoTableRenderer.DECK_ATLAS, 0, 0, 10, 10, 218, 0, 10, 10, 256, 128);
            } else if (card.suit == Card.Suit.WILD && card.family == Card.Family.NUMBER) {
                GuiDraw.blit(BlockEntityMinoTableRenderer.DECK_ATLAS, 0, 0, 10, 10, 228, 0, 10, 10, 256, 128);
            } else {
                String cardName = card.getCardFaceName()
                    .getFormattedText();
                // shadowAlpha decides greyed or non-greyed cards
                int colorA = (int) (0x22 * shadowAlpha + 0xFF * (1 - shadowAlpha));
                GuiDraw.drawString(font, cardName, 0, 0, 0xFF000000 + colorA * 0x10101);
            }
            GL11.glPopMatrix();
            GuiDraw.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0x222222 | ((int) (0xFF * shadowAlpha) << 24));

            if (tableEntity.game.awaitingCutIn && currentPlayer != myPlayerIndex) {
                Card topCard = tableEntity.game.topCard;
                boolean canCutInWithThisCard = card.getEquivFamily() == topCard.getEquivFamily()
                    && card.getEquivSuit() == topCard.getEquivSuit()
                    && card.number == topCard.number;
                if (canCutInWithThisCard) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 150.0));
                    int highlightAlpha = (int) (0x90 * pulse);
                    GuiDraw
                        .fill(x - 2, y - 2, x + CARD_WIDTH + 2, y + CARD_HEIGHT + 2, (highlightAlpha << 24) | 0xFFD700);
                }
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
        return true;
    }

    private void performZoomAnimation(float partialTicks, BlockEntityMinoTable tableEntity) {
        if (Math.abs(zoomAnimationTarget - zoomAnimationProgress) < 0.01) {
            zoomAnimationProgress = zoomAnimationTarget;
        } else {
            zoomAnimationProgress += (zoomAnimationTarget - zoomAnimationProgress) * 8 * 0.05 * partialTicks;
        }
        MinoClient.globalFovModifier = Mth.lerp(Mth.clamp(zoomAnimationProgress, 0, 1), 1.0, 0.97);
    }

    public static final GameOverlayLayer INSTANCE = new GameOverlayLayer();
}
