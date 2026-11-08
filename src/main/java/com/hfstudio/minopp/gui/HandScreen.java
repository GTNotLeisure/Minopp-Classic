package com.hfstudio.minopp.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.UiButton;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HandScreen extends GuiScreen {

    private static boolean resumeAfterChoice;

    private static final int BTN_DRAW = 1, BTN_SHOUT = 2;

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
        drawButton = new UiButton(BTN_DRAW, Math.max(4, bx), by, bw, bh, "", 0xFF2A7FD4);
        shoutButton = new UiButton(BTN_SHOUT, Math.max(4, bx), by + bh + 4, bw, bh, "", 0xFFE0A21A);
        buttonList.add(drawButton);
        buttonList.add(shoutButton);
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
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
        if (Context.resolve(mc) == null) mc.displayGuiScreen(null);
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
        updateButtons();
        super.drawScreen(mouseX, mouseY, partialTicks);

        boolean classic = ClientConfig.display.classicInteraction;
        String keys = Chat.translatable(classic ? "gui.minopp.hand.hint_classic" : "gui.minopp.hand.hint")
            .getFormattedText();
        int tw = fontRendererObj.getStringWidth(keys);
        int ty = 6;
        int tx = classic ? width - tw - 8 : width / 2 - tw / 2;
        UiDraw.roundRect(tx - 4, ty - 2, tx + tw + 3, ty + 9, 3, 0x90000000);
        fontRendererObj.drawStringWithShadow(keys, tx, ty, 0xFFE8E8E8);
        if (noticeText != null && System.currentTimeMillis() < noticeUntil) {
            int nw = fontRendererObj.getStringWidth(noticeText);
            int nx = width / 2 - nw / 2, ny = ty + 16;
            UiDraw.roundRect(nx - 6, ny - 3, nx + nw + 5, ny + 10, 4, 0xC0401010);
            fontRendererObj.drawStringWithShadow(noticeText, nx, ny, 0xFFFFE0A0);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        Context ctx = Context.resolve(mc);
        boolean classic = ClientConfig.display.classicInteraction;
        int prev = classic ? Keyboard.KEY_W : Keyboard.KEY_A;
        int next = classic ? Keyboard.KEY_S : Keyboard.KEY_D;
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == mc.gameSettings.keyBindInventory.getKeyCode()
            || keyCode == com.hfstudio.minopp.MinoClient.KEY_HAND.getKeyCode()) {
            mc.displayGuiScreen(null);
        } else if (ctx == null) {
            return;
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
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        Context ctx = Context.resolve(mc);
        if (wheel != 0 && ctx != null)
            select(ctx, ItemHandCards.getClientHandIndex(mc.thePlayer) - Integer.signum(wheel));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        Context ctx = Context.resolve(mc);
        if (ctx == null) return;
        int hit = GameOverlayLayer.hitTest(mouseX, mouseY);
        if (hit >= 0) play(ctx, hit);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Context ctx = Context.resolve(mc);
        if (ctx == null) return;
        if (button.id == BTN_DRAW) draw(ctx);
        else if (button.id == BTN_SHOUT) shout(ctx);
    }

    private void select(Context ctx, int index) {
        int clamped = Mth.clamp(index, 0, ctx.hand.size() - 1);
        if (clamped != ItemHandCards.getClientHandIndex(mc.thePlayer)) {
            ItemDataUtils.setHandIndex(mc.thePlayer.getHeldItem(), clamped);
            mc.getSoundHandler()
                .playSound(
                    net.minecraft.client.audio.PositionedSoundRecord
                        .func_147674_a(new net.minecraft.util.ResourceLocation("random.click"), 1.9f));
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
            this.hand = ItemHandCards.sortedHand(game, me);
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
