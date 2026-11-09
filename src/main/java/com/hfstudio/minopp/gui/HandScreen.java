package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.config.Prefs;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.gui.ui.SearchMatch;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.render.BlockEntityMinoTableRenderer;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HandScreen extends GuiScreen {

    private static boolean resumeAfterChoice;

    private static final int BTN_DRAW = 1, BTN_SHOUT = 2, BTN_DEBUG_TOGGLE = 99, BTN_DEBUG = 100, BTN_RULE = 200;

    private static String query = "";
    private static final Set<String> PLAIN = Set.of("number", "draw2", "reverse", "skip", "wild", "wild_draw4");
    private static boolean debugOpen = true;
    private GuiTextField search;
    private int hintBottom;
    private int edgeDir;
    private long edgeSince, edgeLast;

    /** The hand as the screen shows it: narrowed to the search while the screen is open. */
    public static List<Card> visibleHand(CardGame game, CardPlayer me) {
        List<Card> hand = ItemHandCards.sortedHand(game, me);
        if (query.isEmpty() || !isOpen()) return hand;
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (SearchMatch.matches(
                game.face(c)
                    .getDisplayName()
                    .getUnformattedText(),
                query)) out.add(c);
        }
        return out.isEmpty() ? hand : out;
    }

    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;
    private UiButton drawButton, shoutButton;

    private static String noticeText;
    private static long noticeUntil;

    public static void notice(String text) {
        noticeText = text;
        noticeUntil = System.currentTimeMillis() + 2500;
    }

    public static boolean isOpen() {
        return Minecraft.getMinecraft().currentScreen instanceof HandScreen;
    }

    public static void returnFromChoice() {
        Minecraft mc = Minecraft.getMinecraft();
        if (resumeAfterChoice && Context.resolve(mc) != null) {
            resumeAfterChoice = false;
            mc.displayGuiScreen(new HandScreen());
        } else {
            resumeAfterChoice = false;
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        boolean classic = ClientConfig.display.classicInteraction;
        int bw = 64, bh = 18;
        int bx = classic ? width - 10 - 100 - 12 - bw : width / 2 - 150 - bw;
        int by = classic ? height - 52 : height - 50;
        bx = Math.max(4, bx);
        drawButton = new UiButton(BTN_DRAW, bx, by, bw, bh, "", 0xFF2A7FD4);
        shoutButton = new UiButton(BTN_SHOUT, bx, by + bh + 4, bw, bh, "", 0xFFE0A21A);
        buttonList.add(drawButton);
        buttonList.add(shoutButton);
        int sw = classic ? 110 : Mth.clamp(width / 2 - (int) (width * 0.31f) - 10 - bx, bw, 140);
        search = new GuiTextField(fontRendererObj, bx, by - 32, sw, 13);
        search.setMaxStringLength(40);
        search.setText(query);
        hintBottom = 6 + hintLines(hintText(), width - 24).size() * 12;
        addDebugButtons();
        updateButtons();
    }

    private static final int DEBUG_ROWS = 8;
    private static int debugScroll;
    private boolean draggingDebugBar;
    /** {x, y, w, h, rows shown, rows in all} of the debug list, or null when there is none. */
    private int[] debugPanel;

    // the toggle sits at the right edge; under it one column of actions and mechanics that the wheel scrolls
    private void addDebugButtons() {
        debugPanel = null;
        Context ctx = Context.resolve(mc);
        if (ctx == null || !ctx.game.isDebugger(ctx.me)) return;
        List<String> actions = CardGame.DEBUG_ACTIONS;
        List<String> rules = CardGame.DEBUG_RULES;
        int labelW = 0;
        for (String a : actions) labelW = Math.max(
            labelW,
            fontRendererObj.getStringWidth(
                Chat.translatable("gui.minopp.debug.btn." + a)
                    .getFormattedText()));
        for (String r : rules) labelW = Math.max(labelW, fontRendererObj.getStringWidth(ruleLabel(ctx, r)));
        String title = Chat.translatable("gui.minopp.debug.title")
            .getFormattedText() + (debugOpen ? " [-]" : " [+]");
        int bh = 12, gap = 2;
        int bw = Math.max(labelW + 10, fontRendererObj.getStringWidth(title) + 10);
        int x0 = width - 6 - bw;
        int top = hintBottom + 16;
        buttonList.add(new UiButton(BTN_DEBUG_TOGGLE, x0, top - 14, bw, bh, title, 0xFF8A2FB0));
        if (!debugOpen) return;
        int total = actions.size() + rules.size();
        int rows = Math.min(DEBUG_ROWS, total);
        debugScroll = Math.max(0, Math.min(debugScroll, total - rows));
        debugPanel = new int[] { x0, top, bw, rows * (bh + gap) - gap, rows, total };
        for (int row = 0; row < rows; row++) {
            int i = debugScroll + row;
            int y = top + row * (bh + gap);
            if (i < actions.size()) {
                String a = actions.get(i);
                int accent = a.equals("win") || a.equals("stop") ? 0xFFB03030
                    : a.equals("turn") ? 0xFF2A9F4A : 0xFF7A3FB0;
                buttonList.add(
                    new UiButton(
                        BTN_DEBUG + i,
                        x0,
                        y,
                        bw,
                        bh,
                        Chat.translatable("gui.minopp.debug.btn." + a)
                            .getFormattedText(),
                        accent));
            } else {
                int k = i - actions.size();
                String r = rules.get(k);
                buttonList.add(
                    new UiButton(
                        BTN_RULE + k,
                        x0,
                        y,
                        bw,
                        bh,
                        ruleLabel(ctx, r),
                        ctx.table.config.getBool(r) ? 0xFF2A7FD4 : 0xFF555560));
            }
        }
    }

    private void rebuildDebugButtons() {
        buttonList.removeIf(o -> o instanceof UiButton b && b.id >= BTN_DEBUG_TOGGLE);
        addDebugButtons();
    }

    private boolean overDebugList(int mx, int my) {
        int[] p = debugPanel;
        return p != null && mx >= p[0] - 8 && mx < p[0] + p[2] && my >= p[1] && my < p[1] + p[3];
    }

    private boolean overDebugBar(int mx, int my) {
        int[] p = debugPanel;
        return p != null && p[5] > p[4] && mx >= p[0] - 8 && mx < p[0] && my >= p[1] && my < p[1] + p[3];
    }

    private int debugThumb() {
        int[] p = debugPanel;
        return Math.max(10, p[3] * p[4] / p[5]);
    }

    // the thumb follows the pointer: its middle sits where the mouse is, so a click on the track jumps there too
    private void dragDebugBar(int my) {
        int[] p = debugPanel;
        int thumb = debugThumb(), range = p[3] - thumb, maxScroll = p[5] - p[4];
        if (range <= 0) return;
        int s = Math.round((my - p[1] - thumb / 2f) * maxScroll / range);
        s = Math.max(0, Math.min(s, maxScroll));
        if (s != debugScroll) {
            debugScroll = s;
            rebuildDebugButtons();
        }
    }

    private void drawDebugScrollbar(int mouseX, int mouseY) {
        int[] p = debugPanel;
        if (p == null || p[5] <= p[4]) return;
        int x = p[0] - 6, y = p[1], h = p[3];
        UiDraw.roundRect(x, y, x + 4, y + h, 2, 0x70000000);
        int thumb = debugThumb();
        int ty = y + (h - thumb) * debugScroll / Math.max(1, p[5] - p[4]);
        boolean hot = draggingDebugBar || overDebugBar(mouseX, mouseY);
        UiDraw.roundRect(x, ty, x + 4, ty + thumb, 2, hot ? 0xFFE0B8FF : 0xFFB07AE0);
    }

    private static String ruleLabel(Context ctx, String rule) {
        GameRules.Rule r = GameRules.get(rule);
        return (ctx.table.config.getBool(rule) ? "\u2714 " : "\u2716 ") + Chat.translatable(r.langKey())
            .getFormattedText();
    }

    private String hintText() {
        boolean classic = ClientConfig.display.classicInteraction;
        return Chat.translatable(classic ? "gui.minopp.hand.hint_classic" : "gui.minopp.hand.hint")
            .getFormattedText();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (!resumeAfterChoice) query = "";
        BlockEntityMinoTableRenderer.deckHot = false;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateButtons() {
        Context ctx = Context.resolve(mc);
        boolean myTurn = ctx != null && ctx.isMyTurn();
        boolean drawn = ctx != null && ctx.game.currentPlayerPhase != CardGame.PlayerActionPhase.DISCARD_HAND;
        drawButton.displayString = Chat.translatable(drawn ? "gui.minopp.hand.pass" : "gui.minopp.hand.draw")
            .getFormattedText();
        drawButton.enabled = myTurn;
        boolean canShout = ctx != null && myTurn && !ctx.me.hasShoutedMino && ctx.hand.size() <= 2;
        shoutButton.enabled = canShout;
        shoutButton.displayString = Chat.translatable("gui.minopp.hand.shout")
            .getFormattedText();
    }

    @Override
    public void updateScreen() {
        Context live = Context.resolve(mc);
        if (live == null) mc.displayGuiScreen(null);
        else for (Object o : buttonList) {
            if (o instanceof UiButton b && b.id >= BTN_RULE && b.id - BTN_RULE < CardGame.DEBUG_RULES.size()) {
                String r = CardGame.DEBUG_RULES.get(b.id - BTN_RULE);
                b.displayString = ruleLabel(live, r);
                b.setAccent(live.table.config.getBool(r) ? 0xFF2A7FD4 : 0xFF555560);
            }
        }
        if (search != null) search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Context ctx = Context.resolve(mc);
        if (ctx == null) return;
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            if (lastMouseX != Integer.MIN_VALUE) {
                int hit = GameOverlayLayer.hitTest(mouseX, mouseY);
                if (hit >= 0) select(ctx, hit);
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        edgeTurn(ctx, mouseX, mouseY);
        BlockEntityMinoTableRenderer.deckHot = ctx.isMyTurn()
            && BlockEntityMinoTableRenderer.isOverDeck(mouseX, mouseY);
        updateButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawDebugScrollbar(mouseX, mouseY);

        int ty = 6;
        for (String line : hintLines(hintText(), width - 24)) {
            int tw = fontRendererObj.getStringWidth(line);
            int tx = width / 2 - tw / 2;
            UiDraw.roundRect(tx - 4, ty - 2, tx + tw + 3, ty + 9, 3, 0x90000000);
            fontRendererObj.drawStringWithShadow(line, tx, ty, 0xFFE8E8E8);
            ty += 12;
        }
        boolean found = query.isEmpty() || ctx.hand.size() < ctx.me.hand.size();
        search.setTextColor(found ? 0xFFFFFFFF : 0xFFFF6060);
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused()) {
            fontRendererObj.drawStringWithShadow(
                Chat.translatable("gui.minopp.hand.search")
                    .getFormattedText(),
                search.xPosition + 4,
                search.yPosition + 3,
                0xFF808080);
        }
        int total = ctx.hand.size();
        if (total > GameOverlayLayer.pageSize() || !query.isEmpty()) {
            String page = Chat
                .translatable(
                    "gui.minopp.hand.page",
                    Mth.clamp(ItemHandCards.getClientHandIndex(mc.thePlayer), 0, total - 1) + 1,
                    total)
                .getFormattedText();
            fontRendererObj.drawStringWithShadow(page, search.xPosition + 1, search.yPosition + 17, 0xFFFFE070);
        }
        int[] coin = GameOverlayLayer.coinRect();
        if (coin != null && mouseX >= coin[0]
            && mouseX < coin[0] + coin[2]
            && mouseY >= coin[1]
            && mouseY < coin[1] + coin[2]) {
            List<String> tip = new ArrayList<>();
            String side = ctx.me.coinNoMercy ? "gui.minopp.coin.no_mercy" : "gui.minopp.coin.mercy";
            tip.add(
                Chat.translatable(side)
                    .getFormattedText());
            tip.add(
                Chat.translatable(side + ".desc")
                    .getFormattedText());
            tip.add(
                Chat.translatable(
                    ctx.me.coinUsed ? "gui.minopp.coin.spent_label"
                        : ctx.me.coinLocked ? "gui.minopp.coin.tip_use" : "gui.minopp.coin.tip_flip")
                    .getFormattedText());
            UiDraw.tooltip(fontRendererObj, tip, mouseX, mouseY, width, height);
        }
        int sel = ItemHandCards.getClientHandIndex(mc.thePlayer);
        if (ClientConfig.display.cardInfo && sel >= 0 && sel < ctx.hand.size()) {
            Card card = ctx.hand.get(sel);
            if (!PLAIN.contains(
                ctx.game.face(card)
                    .type().id))
                drawCardInfo(ctx, card);
        }
        if (noticeText != null && System.currentTimeMillis() < noticeUntil) {
            int nw = fontRendererObj.getStringWidth(noticeText);
            int nx = width / 2 - nw / 2, ny = ty + 4;
            UiDraw.roundRect(nx - 6, ny - 3, nx + nw + 5, ny + 10, 4, 0xC0401010);
            fontRendererObj.drawStringWithShadow(noticeText, nx, ny, 0xFFFFE0A0);
        }
    }

    private void edgeTurn(Context ctx, int mouseX, int mouseY) {
        float[] ext = GameOverlayLayer.rowExtent();
        int dir = 0;
        if (ext != null && ctx.hand.size() > GameOverlayLayer.pageSize()
            && mouseY > ext[2] + 6
            && !search.isFocused()) {
            if (mouseX < ext[0] + 12 && mouseX > ext[0] - 20) dir = -1;
            else if (mouseX > ext[1] - 12 && mouseX < ext[1] + 20) dir = 1;
        }
        long now = System.currentTimeMillis();
        if (dir == 0 || dir != edgeDir) {
            edgeDir = dir;
            edgeSince = now;
            edgeLast = now;
        } else if (now - edgeSince > 400 && now - edgeLast > 170) {
            edgeLast = now;
            select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) + dir);
        }
    }

    private List<String> hintLines(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String sep = "  ·  ";
        StringBuilder line = new StringBuilder();
        for (String part : text.split(Pattern.quote(sep))) {
            String next = line.length() == 0 ? part : line + sep + part;
            if (line.length() > 0 && fontRendererObj.getStringWidth(next) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(part);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            out.addAll(fontRendererObj.listFormattedStringToWidth(l, maxWidth));
        }
        return out;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        Context ctx = Context.resolve(mc);
        if (search.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                search.setFocused(false);
            } else if (search.textboxKeyTyped(typedChar, keyCode) && !search.getText()
                .equals(query)) {
                    query = search.getText();
                    if (mc.thePlayer.getHeldItem() != null) ItemDataUtils.setHandIndex(mc.thePlayer.getHeldItem(), 0);
                }
            return;
        }
        if (keyCode == Keyboard.KEY_F) {
            search.setFocused(true);
            return;
        }
        boolean classic = ClientConfig.display.classicInteraction;
        int prev = classic ? Keyboard.KEY_W : Keyboard.KEY_A;
        int next = classic ? Keyboard.KEY_S : Keyboard.KEY_D;
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()
            || keyCode == MinoClient.KEY_HAND.getKeyCode()) {
            mc.displayGuiScreen(null);
        } else if (ctx == null) {
            return;
        } else if (keyCode == Keyboard.KEY_PRIOR
            || (keyCode == prev || keyCode == Keyboard.KEY_LEFT) && isShiftKeyDown()) {
                select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) - GameOverlayLayer.pageSize());
            } else if (keyCode == Keyboard.KEY_NEXT
                || (keyCode == next || keyCode == Keyboard.KEY_RIGHT) && isShiftKeyDown()) {
                    select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) + GameOverlayLayer.pageSize());
                } else if (keyCode == prev || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_UP) {
                    select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) - 1);
                } else if (keyCode == next || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_DOWN) {
                    select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) + 1);
                } else if (keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_RETURN) {
                    play(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer));
                } else if (keyCode == Keyboard.KEY_Q) {
                    draw(ctx);
                }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        Context ctx = Context.resolve(mc);
        if (wheel != 0 && debugPanel != null) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (overDebugList(mx, my)) {
                int before = debugScroll;
                debugScroll = Math.max(0, Math.min(debugScroll - Integer.signum(wheel), debugPanel[5] - debugPanel[4]));
                if (debugScroll != before) rebuildDebugButtons();
                return;
            }
        }
        if (wheel != 0 && ctx != null) {
            int dir = -Integer.signum(wheel);
            if (ctx.hand.size() > GameOverlayLayer.pageSize()) GameOverlayLayer.turnCarousel(dir);
            select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) + dir);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (draggingDebugBar && debugPanel != null) dragDebugBar(mouseY);
        else super.mouseClickMove(mouseX, mouseY, button, time);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        if (state >= 0) draggingDebugBar = false;
        super.mouseMovedOrUp(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int[] coin = GameOverlayLayer.coinRect();
        if (button == 0 && coin != null
            && mouseX >= coin[0]
            && mouseX < coin[0] + coin[2]
            && mouseY >= coin[1]
            && mouseY < coin[1] + coin[2]) {
            Context ctx = Context.resolve(mc);
            if (ctx != null && !ctx.me.coinUsed) C2SPlayCardPacket.Client.sendCoinC2S(ctx.pos, !ctx.me.coinLocked);
            return;
        }
        if (button == 0 && overDebugBar(mouseX, mouseY)) {
            draggingDebugBar = true;
            dragDebugBar(mouseY);
            return;
        }
        if (button == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        search.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= search.xPosition && mouseX < search.xPosition + search.width
            && mouseY >= search.yPosition
            && mouseY < search.yPosition + search.height) return;
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        Context ctx = Context.resolve(mc);
        if (ctx == null) return;
        int hit = GameOverlayLayer.hitTest(mouseX, mouseY);
        if (hit < 0 && BlockEntityMinoTableRenderer.isOverDeck(mouseX, mouseY)) {
            draw(ctx);
            return;
        }
        if (hit >= 0 && isShiftKeyDown()) {
            ClientConfig.display.cardInfo = !ClientConfig.display.cardInfo;
            Prefs.save();
            select(ctx, hit);
            notice(
                Chat.translatable(
                    ClientConfig.display.cardInfo ? "gui.minopp.hand.info_on" : "gui.minopp.hand.info_off")
                    .getFormattedText());
            return;
        }
        if (hit >= 0) play(ctx, hit);
    }

    private void drawCardInfo(Context ctx, Card card) {
        CardFace face = ctx.game.face(card);
        String title = Chat.translatable(
            "gui.minopp.hand.card_points",
            face.getDisplayName()
                .getFormattedText(),
            ctx.game.cardPoints(card))
            .getFormattedText();
        String key = "game.minopp.card.type." + face.type().id + ".desc";
        String desc = StatCollector.canTranslate(key) ? Chat.translatable(key)
            .getFormattedText()
            : Chat.translatable("gui.minopp.hand.card_number")
                .getFormattedText();
        int maxW = Math.min(380, width - 40);
        List<?> lines = fontRendererObj.listFormattedStringToWidth(desc, maxW);
        int bandH = 24 + lines.size() * 11;
        int cw = GameOverlayLayer.cardWidth(width), ch = Math.round(cw * 8.9f / 5.6f);
        int y = Math.max(40, height - ch / 2 - bandH / 2 - 6);
        UiDraw.gradient(0, y, width, y + bandH, 0xD0181010, 0xD0181010);
        UiDraw.gradient(0, y - 1, width, y, 0xFFE8B828, 0xFFE8B828);
        UiDraw.gradient(0, y + bandH, width, y + bandH + 1, 0xFFE8B828, 0xFFE8B828);
        int tw = fontRendererObj.getStringWidth(title);
        fontRendererObj.drawStringWithShadow(title, width / 2 - tw / 2, y + 6, 0xFFFFD040);
        for (int i = 0; i < lines.size(); i++) {
            String l = (String) lines.get(i);
            fontRendererObj.drawStringWithShadow(
                l,
                width / 2 - fontRendererObj.getStringWidth(l) / 2,
                y + 19 + i * 11,
                0xFFF0F0F0);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Context ctx = Context.resolve(mc);
        if (ctx == null) return;
        if (button.id == BTN_DRAW) draw(ctx);
        else if (button.id == BTN_SHOUT) shout(ctx);
        else if (button.id >= BTN_RULE && button.id - BTN_RULE < CardGame.DEBUG_RULES.size()) {
            mc.thePlayer.sendChatMessage("/minopp debug rule:" + CardGame.DEBUG_RULES.get(button.id - BTN_RULE));
        } else if (button.id == BTN_DEBUG_TOGGLE) {
            debugOpen = !debugOpen;
            initGui();
        } else if (button.id >= BTN_DEBUG && button.id - BTN_DEBUG < CardGame.DEBUG_ACTIONS.size()) {
            mc.thePlayer.sendChatMessage("/minopp debug " + CardGame.DEBUG_ACTIONS.get(button.id - BTN_DEBUG));
        }
    }

    private void select(Context ctx, int index) {
        int clamped = Math.floorMod(index, ctx.hand.size());
        if (clamped != ItemHandCards.getClientHandIndex(mc.thePlayer)) {
            ItemDataUtils.setHandIndex(mc.thePlayer.getHeldItem(), clamped);
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.click"), 1.9f));
            TurnDeadMan.pedal();
        }
    }

    private void play(Context ctx, int index) {
        if (index < 0 || index >= ctx.hand.size()) return;
        select(ctx, index);
        TurnDeadMan.pedal();
        Card card = ctx.hand.get(index);
        resumeAfterChoice = true;
        BlockMinoTable.Client.playCard(ctx.pos, ctx.table, ctx.me, card);
        if (mc.currentScreen == this) resumeAfterChoice = false;
    }

    private void shout(Context ctx) {
        if (!ctx.isMyTurn() || ctx.me.hasShoutedMino || ctx.hand.size() > 2) return;
        mc.thePlayer.sendChatMessage("/minopp shout");
    }

    private void draw(Context ctx) {
        if (!ctx.isMyTurn()) return;
        TurnDeadMan.pedal();
        C2SPlayCardPacket.Client.sendPlayNoCardC2S(ctx.pos, ItemHandCards.getCardPlayer(mc.thePlayer));
    }

    public static final class Context {

        public final BlockPos pos;
        public final BlockEntityMinoTable table;
        public final CardGame game;
        public final CardPlayer me;
        public final List<Card> hand;

        private Context(BlockPos pos, BlockEntityMinoTable table, CardPlayer me) {
            this.pos = pos;
            this.table = table;
            this.game = table.game;
            this.me = me;
            this.hand = visibleHand(game, me);
        }

        public boolean isMyTurn() {
            return game.players.indexOf(me) == game.currentPlayerIndex && !game.awaitingCutIn;
        }

        public static Context resolve(Minecraft mc) {
            if (mc.thePlayer == null || mc.theWorld == null) return null;
            BlockPos pos = ItemHandCards.getHandCardGamePos(mc.thePlayer);
            BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, pos);
            if (table == null || table.game == null) return null;
            CardPlayer me = table.game.deAmputate(ItemHandCards.getCardPlayer(mc.thePlayer));
            if (me == null || me.hand.isEmpty()) return null;
            return new Context(pos, table, me);
        }
    }
}
