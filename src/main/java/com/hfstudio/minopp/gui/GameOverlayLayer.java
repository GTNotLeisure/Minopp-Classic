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
import com.hfstudio.minopp.game.Teams;
import com.hfstudio.minopp.gui.ui.UiDraw;
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
        if (currentPlayer.equals(cardPlayer) && !game.awaitingCutIn) {
            if (game.currentPlayerPhase == CardGame.PlayerActionPhase.DISCARD_HAND) {
                zoomAnimationTarget = 1;
            } else {
                if (zoomAnimationTarget < 1.01) {
                    zoomAnimationTarget = 1.5;
                } else if (zoomAnimationProgress >= 1.5) {
                    zoomAnimationTarget = 1.05;
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
                .getFormattedText(),
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
                UiDraw.lerpColor(0xFFFFFFFF, 0xFFFFE15A, slowPulse()));
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
                .append(
                    p.eliminated ? "x"
                        : game.hiddenCounts.contains(p.uuid) && !p.equals(cardPlayer) ? "?"
                            : String.valueOf(p.hand.size()));
            if (p.team >= 0) counts.append(Teams.format(p.team))
                .append('[')
                .append(p.team + 1)
                .append(']');
        }
        for (String line : font.listFormattedStringToWidth(counts.toString(), 260)) {
            drawStringWithBackdrop(font, line, x, y, 0xFFAAAAAA);
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
            && !(mc.currentScreen instanceof PlayChoiceScreen)
            && !HandScreen.isOpen()) {
            boolean classic = ClientConfig.display.classicInteraction;
            boolean isPass = classic && BlockMinoTable.Client.isCursorHittingPile();
            String cursorMessage = switch (game.currentPlayerPhase) {
                case DISCARD_HAND -> isPass ? Chat.translatable("gui.minopp.play.cursor.pass_draw")
                    .getFormattedText()
                    : Chat.translatable("gui.minopp.play.cursor.play")
                        .getFormattedText();
                case DISCARD_DRAWN, CHAIN -> isPass ? Chat.translatable("gui.minopp.play.cursor.pass")
                    .getFormattedText()
                    : Chat.translatable("gui.minopp.play.cursor.play")
                        .getFormattedText();
            };
            if (!classic) cursorMessage = Chat.translatable("gui.minopp.play.cursor.open_hand")
                .getFormattedText();
            String shoutMessage = Chat.translatable("gui.minopp.play.cursor.shout")
                .getFormattedText();
            String flexMessage = Chat.translatable("gui.minopp.play.cursor.flex")
                .getFormattedText();
            boolean isShouting = classic && !isPass && BlockMinoTable.Client.isShoutModifierHeld();
            boolean isFlexing = classic && !isPass
                && BlockMinoTable.Client.isFlexModifierHeld()
                && me != null
                && me.powerOn
                && tableEntity.config.getBool(GameRules.FLEX_POWER);
            int lines = 1 + (isShouting ? 1 : 0) + (isFlexing ? 1 : 0);
            int msgWidth = Math.max(
                font.getStringWidth(cursorMessage),
                Math.max(
                    isShouting ? font.getStringWidth(shoutMessage) : 0,
                    isFlexing ? font.getStringWidth(flexMessage) : 0));
            int msgHeight = lh * lines;
            UiDraw.roundFrame(
                width / 2 + 8,
                height / 2 - msgHeight / 2 - 3,
                width / 2 + msgWidth + 17,
                height / 2 + msgHeight / 2 + 3,
                3,
                1,
                isPass ? 0xC0FFC21E : 0x60FFFFFF,
                isPass ? 0xC0402A08 : 0xA0101010,
                isPass ? 0xC0281A04 : 0xA0000000);
            int ty = height / 2 - msgHeight / 2;
            GuiDraw.drawString(font, cursorMessage, width / 2 + 12, ty, isPass ? 0xFFFFE15A : 0xFFFFFFDD);
            if (isShouting) GuiDraw.drawString(font, shoutMessage, width / 2 + 12, ty += lh, 0xFFFFFFDD);
            if (isFlexing) GuiDraw.drawString(font, flexMessage, width / 2 + 12, ty + lh, 0xFFFFD040);
        }

        if (TurnDeadMan.isAlarmActive()) {
            if (mc.currentScreen instanceof PlayChoiceScreen || HandScreen.isOpen()) return;
            String deadManMessage = Chat.translatable("gui.minopp.play.cursor.dead_man")
                .getFormattedText();
            int msgWidth = font.getStringWidth(deadManMessage);
            float p = slowPulse();
            int glow = (int) (60 + 120 * p) << 24 | 0xFFC21E;
            GL11.glPushMatrix();
            GL11.glTranslatef((float) (width / 2), (float) (height / 2 + 14), 0);
            UiDraw.roundRect(-msgWidth / 2 - 9, -3, msgWidth / 2 + 9, lh + 5, 7, glow);
            UiDraw
                .roundFrame(-msgWidth / 2 - 7, -1, msgWidth / 2 + 7, lh + 3, 6, 1, 0xFFFFC21E, 0xE0402A08, 0xE0281A04);
            GuiDraw.drawString(font, deadManMessage, -msgWidth / 2, 1, 0xFFFFF2C0);
            GL11.glPopMatrix();
        }
    }

    private static void drawStringWithBackdrop(FontRenderer font, String text, int x, int y, int color) {
        int a = (int) (((color >>> 24) & 0xFF) * 0.4f);
        GuiDraw.fill(x - 2, y, x + font.getStringWidth(text) + 2, y + font.FONT_HEIGHT, a << 24);
        GuiDraw.drawString(font, text, x, y, color);
    }

    private boolean renderHandCards(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        cardRects.clear();
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
        cardRects.clear();
        if (!ClientConfig.display.classicInteraction) {
            renderHandRow(font, game, realPlayer, hand, clientHandIndex, isMyTurn, myPlayerIndex, theme, width, height);
            return true;
        }
        int handSize = hand.size();
        int selectedCardYRaw = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - clientHandIndex));
        int cardDrawOffset = selectedCardYRaw < 20 ? 20 - selectedCardYRaw : 0;
        Random cardRandom = new Random(handSize);
        GL11.glEnable(GL11.GL_BLEND);
        int[] xs = new int[handSize], ys = new int[handSize];
        for (int i = 0; i < handSize; i++) {
            Card card = hand.get(i);
            int targetXOff = (i == clientHandIndex ? -30 : 0) + cardRandom.nextInt(7) - 3;
            Float currentXOffBoxed = handCardCurrentXOff.get(card.uid);
            float currentXOff = currentXOffBoxed == null ? CARD_WIDTH + 10 : currentXOffBoxed;
            xs[i] = width - 10 - CARD_WIDTH + (int) currentXOff;
            handCardCurrentXOff.put(card.uid, (float) Mth.lerp(8 * 0.05 * partialTicks, currentXOff, targetXOff));
            ys[i] = height - ((CARD_HEIGHT / 2) + CARD_V_SPACING * (handSize - i)) + cardDrawOffset;
        }
        addRect(clientHandIndex, xs[clientHandIndex], ys[clientHandIndex], CARD_WIDTH, CARD_HEIGHT);
        for (int i = handSize - 1; i >= 0; i--) {
            if (i != clientHandIndex) addRect(i, xs[i], ys[i], CARD_WIDTH, CARD_HEIGHT);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < handSize; i++) {
                boolean selected = i == clientHandIndex;
                if (pass == 0 ? selected : !selected) continue;
                Card card = hand.get(i);
                CardFace face = game.face(card);
                int x = xs[i], y = ys[i];
                if (selected && isMyTurn) {
                    String cardName = face.getDisplayName()
                        .getFormattedText();
                    GuiDraw.drawString(font, cardName, x - font.getStringWidth(cardName) - 10, y + 10, 0xFFFFFFDD);
                }
                if (selected) {
                    UiDraw.roundRect(
                        x + 3,
                        y + 3,
                        x + CARD_WIDTH + 3,
                        y + CARD_HEIGHT + 3,
                        Math.max(1, CARD_WIDTH * 3 / 32),
                        0x60000000);
                }
                if (isMyTurn && game.canPlay(realPlayer, card)) {
                    int rim = 0xA0 + (int) (0x5F * slowPulse());
                    UiDraw.roundRect(
                        x - 2,
                        y - 2,
                        x + CARD_WIDTH + 2,
                        y + CARD_HEIGHT + 2,
                        Math.max(1, CARD_WIDTH * 3 / 32) + 2,
                        (rim << 24) | 0xFFD43A);
                }
                CardRenderer.drawFace2D(face, theme, x, y, CARD_WIDTH, CARD_HEIGHT);
                if (!selected) {
                    int mini = Math.max(8, CARD_V_SPACING - 4);
                    CardRenderer.drawCornerSymbol(face, theme, x + 3, y + 2, mini);
                }

                boolean playable = isMyTurn && game.canPlay(realPlayer, card);
                float shadowAlpha = !isMyTurn ? 0.5f : (playable ? 0f : 0.3f);
                if (shadowAlpha > 0) {
                    int corner = Math.max(1, CARD_WIDTH * 3 / 32);
                    UiDraw.roundRect(
                        x,
                        y,
                        x + CARD_WIDTH,
                        y + CARD_HEIGHT,
                        corner,
                        0x222222 | ((int) (0xFF * shadowAlpha) << 24));
                }
                if (game.awaitingCutIn && game.currentPlayerIndex != myPlayerIndex && game.isIdenticalToTop(card)) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0));
                    int highlightAlpha = (int) (0x90 * pulse);
                    UiDraw.roundRect(
                        x - 2,
                        y - 2,
                        x + CARD_WIDTH + 2,
                        y + CARD_HEIGHT + 2,
                        Math.max(1, CARD_WIDTH * 3 / 32) + 2,
                        (highlightAlpha << 24) | 0xFFD700);
                }
            }
        }
        GL11.glDisable(GL11.GL_BLEND);
        return true;
    }

    /** Card hit areas of the last frame, topmost first: {index, centerX, centerY, angle (rad), w, h}. */
    private static final List<float[]> cardRects = new ArrayList<>();
    private float rowRaise = 0;
    private final Map<Integer, Float> rowLift = new HashMap<>();
    private long lastRowNanos = System.nanoTime();

    private static void addRect(int index, float x, float y, float w, float h) {
        cardRects.add(new float[] { index, x + w / 2, y + h / 2, 0, w, h });
    }

    public static int hitTest(int mouseX, int mouseY) {
        for (float[] r : cardRects) {
            double dx = mouseX - r[1], dy = mouseY - r[2];
            double cos = Math.cos(-r[3]), sin = Math.sin(-r[3]);
            double lx = dx * cos - dy * sin, ly = dx * sin + dy * cos;
            if (Math.abs(lx) <= r[4] / 2 && Math.abs(ly) <= r[5] / 2) return (int) r[0];
        }
        return -1;
    }

    private void renderHandRow(FontRenderer font, CardGame game, CardPlayer me, List<Card> hand, int selectedIndex,
        boolean isMyTurn, int myPlayerIndex, String theme, int width, int height) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastRowNanos) / 1e9f);
        lastRowNanos = now;
        float ease = 1 - (float) Math.exp(-dt * 14);
        boolean interactive = HandScreen.isOpen();
        rowRaise += ((interactive ? 1 : 0) - rowRaise) * ease;
        rowLift.keySet()
            .removeIf(
                uid -> hand.stream()
                    .noneMatch(c -> c.uid == uid));

        int n = hand.size();
        int cw = Mth.clamp(width / 9, 40, 60);
        int ch = Math.round(cw * 8.9f / 5.6f);
        int corner = Math.max(1, cw * 3 / 32);
        int maxRow = Math.max(cw, Math.min(width - 150, (int) (width * 0.62)));
        float step = n <= 1 ? 0 : Math.min(cw * 0.62f, (maxRow - cw) / (float) (n - 1));
        float radius = ch * 4.2f;
        float anglePer = n <= 1 ? 0 : Math.min(step / radius, (float) Math.toRadians(40) / (n - 1));
        float yRest = height + ch * 0.12f;
        float yUp = height - ch * 0.5f - 8;
        float cy = yRest + (yUp - yRest) * rowRaise;
        float px = width / 2f, py = cy + radius;

        float[] cxs = new float[n], cys = new float[n], angs = new float[n];
        boolean[] playables = new boolean[n];
        for (int i = 0; i < n; i++) {
            Card card = hand.get(i);
            playables[i] = isMyTurn && game.canPlay(me, card);
            float target = i == selectedIndex ? (interactive ? 16 : 8) : (playables[i] ? 5 : 0);
            Float cur = rowLift.get(card.uid);
            float lift = cur == null ? 0 : cur;
            lift += (target - lift) * ease;
            rowLift.put(card.uid, lift);
            float ang = (i - (n - 1) / 2f) * anglePer;
            float r = radius + lift;
            angs[i] = ang;
            cxs[i] = px + (float) Math.sin(ang) * r;
            cys[i] = py - (float) Math.cos(ang) * r;
        }

        GL11.glEnable(GL11.GL_BLEND);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                boolean selected = i == selectedIndex;
                if (pass == 0 ? selected : !selected) continue;
                Card card = hand.get(i);
                CardFace face = game.face(card);
                boolean playable = playables[i];
                GL11.glPushMatrix();
                GL11.glTranslatef(cxs[i], cys[i], 0);
                GL11.glRotatef((float) Math.toDegrees(angs[i]), 0, 0, 1);
                GL11.glTranslatef(-cw / 2f, -ch / 2f, 0);
                if (selected) UiDraw.roundRect(2, 3, cw + 2, ch + 3, corner, 0x60000000);
                if (game.awaitingCutIn && game.currentPlayerIndex != myPlayerIndex && game.isIdenticalToTop(card)) {
                    int glow = 0x60 + (int) (0x9F * slowPulse());
                    UiDraw.roundRect(-3, -3, cw + 3, ch + 3, corner + 3, (glow << 24) | 0xFFD700);
                }
                if (playable) {
                    int rim = 0xA0 + (int) (0x5F * slowPulse());
                    UiDraw.roundRect(-2, -2, cw + 2, ch + 2, corner + 2, (rim << 24) | 0xFFD43A);
                }
                CardRenderer.drawFace2D(face, theme, 0, 0, cw, ch);
                float shade = !isMyTurn ? 0.35f : (playable ? 0f : 0.45f);
                if (selected && interactive) shade *= 0.5f;
                if (shade > 0) UiDraw.roundRect(0, 0, cw, ch, corner, 0x222222 | ((int) (0xFF * shade) << 24));
                GL11.glPopMatrix();
            }
        }

        if (selectedIndex >= 0 && selectedIndex < n && interactive) {
            String name = game.face(hand.get(selectedIndex))
                .getDisplayName()
                .getFormattedText();
            float a = angs[selectedIndex];
            int nw = font.getStringWidth(name);
            int nx = Math.round(cxs[selectedIndex] + (float) Math.sin(a) * (ch / 2f + 8));
            int ny = Math.round(cys[selectedIndex] - (float) Math.cos(a) * (ch / 2f + 8)) - 6;
            nx = Mth.clamp(nx, nw / 2 + 6, width - nw / 2 - 6);
            UiDraw.roundRect(nx - nw / 2 - 4, ny - 2, nx + nw / 2 + 4, ny + 10, 3, 0xA0000000);
            font.drawStringWithShadow(name, nx - nw / 2, ny, 0xFFFFFFFF);
        }
        GL11.glDisable(GL11.GL_BLEND);

        if (selectedIndex >= 0 && selectedIndex < n) {
            cardRects.add(
                new float[] { selectedIndex, cxs[selectedIndex], cys[selectedIndex], angs[selectedIndex], cw, ch });
        }
        for (int i = n - 1; i >= 0; i--) {
            if (i != selectedIndex) cardRects.add(new float[] { i, cxs[i], cys[i], angs[i], cw, ch });
        }
    }

    private void performZoomAnimation(float partialTicks) {
        if (Math.abs(zoomAnimationTarget - zoomAnimationProgress) < 0.01) {
            zoomAnimationProgress = zoomAnimationTarget;
        } else {
            zoomAnimationProgress += (zoomAnimationTarget - zoomAnimationProgress) * 8 * 0.05 * partialTicks;
        }
        MinoClient.globalFovModifier = Mth.lerp(Mth.clamp(zoomAnimationProgress, 0, 1), 1.0, 0.97);
    }

    private static float slowPulse() {
        return (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1600.0 * Math.PI * 2));
    }

    public static final GameOverlayLayer INSTANCE = new GameOverlayLayer();
}
