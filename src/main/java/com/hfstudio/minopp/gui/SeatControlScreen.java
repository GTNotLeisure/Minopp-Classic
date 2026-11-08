package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Table lobby: seats around a felt table (up to 20), start / stop / reset, and shortcuts into the settings tabs.
 */
@SideOnly(Side.CLIENT)
public class SeatControlScreen extends GuiScreen {

    private final BlockPos gamePos;

    private record Action(String id, String key, String label, int x, int y, int w, int h, boolean enabled,
        boolean big) {}

    private final List<Action> actions = new ArrayList<>();
    private long openTime = System.currentTimeMillis();

    public SeatControlScreen(BlockPos gamePos) {
        this.gamePos = gamePos;
    }

    private BlockEntityMinoTable getTable() {
        return ItemHandCards.getTable(mc.theWorld, gamePos);
    }

    private static String tr(String key, Object... args) {
        return Chat.translatable(key, args)
            .getFormattedText();
    }

    private void layoutActions(BlockEntityMinoTable te) {
        actions.clear();
        boolean idle = te.game == null;
        CardPlayer me = ItemHandCards.getCardPlayer(mc.thePlayer);
        boolean seated = te.getPlayersList()
            .contains(me);
        int sideW = Math.min(150, width / 3);
        int x = width - 10 - sideW;
        int y = 44;
        actions.add(new Action("settings", "Z", tr("gui.minopp.seats.settings"), x, y, sideW, 22, true, false));
        actions.add(new Action("style", "C", tr("gui.minopp.seats.style"), x, y + 28, sideW, 22, true, false));
        actions.add(new Action("designs", "V", tr("gui.minopp.seats.designs"), x, y + 56, sideW, 22, true, false));
        actions.add(
            new Action("reset", "R", tr("gui.minopp.seats.reset_plain"), x, y + 84, sideW, 22, idle && seated, false));
        actions.add(new Action("stop", "S", tr("gui.minopp.seats.stop_plain"), x, y + 112, sideW, 22, !idle, false));
        actions.add(
            new Action(
                "start",
                "X",
                tr("gui.minopp.seats.start_plain"),
                10,
                height - 40,
                Math.min(220, width / 2),
                30,
                idle && te.getPlayersList()
                    .size() >= 2,
                true));
    }

    @Override
    public void initGui() {
        openTime = System.currentTimeMillis();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        BlockEntityMinoTable te = getTable();
        if (te == null) {
            mc.displayGuiScreen(null);
            return;
        }
        layoutActions(te);
        UiDraw.background(width, height);
        UiDraw.outlinedText(
            fontRendererObj,
            tr("gui.minopp.seats.title"),
            10,
            8,
            2f,
            UiDraw.TITLE_YELLOW,
            UiDraw.OUTLINE_DARK);
        String sub = tr(
            "gui.minopp.play.seat_info",
            te.getPlayersList()
                .size(),
            te.seatCount(),
            te.config.countDeckSize());
        if (te.game != null) sub = EnumChatFormatting.YELLOW + tr("gui.minopp.play.game_active");
        fontRendererObj.drawStringWithShadow(sub, 12, 30, 0xFFFFFFFF);

        // table area
        int sideW = Math.min(150, width / 3);
        int areaX1 = 10, areaY1 = 44, areaX2 = width - 20 - sideW, areaY2 = height - 50;
        UiDraw.panel(areaX1, areaY1, areaX2, areaY2);
        int cx = (areaX1 + areaX2) / 2, cy = (areaY1 + areaY2) / 2;
        int rx = (areaX2 - areaX1) / 2 - 34, ry = (areaY2 - areaY1) / 2 - 20;
        drawFelt(cx, cy, (int) (rx * 0.62), (int) (ry * 0.55));

        int seatCount = te.seatCount();
        float appear = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        for (int i = 0; i < seatCount; i++) {
            double a = BlockEntityMinoTable.seatAngle(i, seatCount);
            int sx = cx + (int) (Math.sin(a) * rx * (0.7f + 0.3f * appear));
            int sy = cy - (int) (Math.cos(a) * ry * (0.7f + 0.3f * appear));
            drawSeat(te, i, sx, sy, mouseX, mouseY);
        }

        for (Action act : actions) drawAction(act, mouseX, mouseY);

        String esc = tr("gui.minopp.settings.back");
        int ew = UiDraw.pillWidth(fontRendererObj, "Esc", esc);
        UiDraw.pill(fontRendererObj, width - 8 - ew, height - 26, "Esc", esc, false);
    }

    private void drawFelt(int cx, int cy, int rx, int ry) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0);
        GL11.glScalef(rx / 40f, ry / 40f, 1);
        UiDraw.roundRect(-42, -42, 42, 42, 40, 0xFF6A3A1A, 0xFF4A2410);
        UiDraw.roundRect(-38, -38, 38, 38, 36, 0xFF3FA046, 0xFF1F6A28);
        GL11.glPopMatrix();
        // deck in the middle
        com.hfstudio.minopp.render.CardRenderer
            .drawBack2D(ThemeManager.INSTANCE.selected(), false, cx - 9, cy - 14, 18, 28);
    }

    private void drawSeat(BlockEntityMinoTable te, int seat, int x, int y, int mouseX, int mouseY) {
        CardPlayer p = te.seats[seat];
        boolean me = p != null && p.uuid.equals(
            mc.thePlayer.getGameProfile()
                .getId());
        int w = Math.max(34, Math.min(70, p == null ? 34 : fontRendererObj.getStringWidth(p.name) + 22));
        if (te.seatCount() > 10) w = Math.min(w, 52);
        int h = 16;
        int x1 = x - w / 2, y1 = y - h / 2;
        if (p == null) {
            UiDraw.roundRect(x1, y1, x1 + w, y1 + h, 6, 0x50000000);
            UiDraw.scaledCentered(fontRendererObj, String.valueOf(seat + 1), x, y1 + 4, 1f, 0xFF888888);
            return;
        }
        boolean current = te.game != null && te.game.currentPlayer().uuid.equals(p.uuid);
        UiDraw.roundFrame(
            x1,
            y1,
            x1 + w,
            y1 + h,
            6,
            1,
            current ? UiDraw.GLOW : (me ? 0xFFFFFFFF : 0xFF2A2A2A),
            0xFF3A6AD0,
            0xFF1A3A90);
        GuiDraw.drawPlayerHead(PlayerPicker.skinOf(p.uuid), x1 + 3, y1 + 3, 10);
        String name = fontRendererObj.trimStringToWidth(p.name, w - 18);
        fontRendererObj.drawStringWithShadow(name, x1 + 16, y1 + 4, 0xFFFFFFFF);
        if (te.game != null) {
            CardPlayer gp = te.game.deAmputate(p.uuid);
            if (gp != null) {
                String c = gp.eliminated ? "x" : String.valueOf(gp.hand.size());
                UiDraw.roundRect(x1 + w - 4, y1 + h - 8, x1 + w + 8, y1 + h + 3, 3, 0xFFFFFFFF);
                fontRendererObj
                    .drawString(c, x1 + w + 2 - fontRendererObj.getStringWidth(c) / 2, y1 + h - 6, 0xFF202020);
            }
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void drawAction(Action a, int mouseX, int mouseY) {
        boolean hover = a.enabled && mouseX >= a.x && mouseX < a.x + a.w && mouseY >= a.y && mouseY < a.y + a.h;
        int border = hover ? UiDraw.GLOW : (a.enabled ? 0xFF5A5A5A : 0xFF3A3A3A);
        UiDraw.roundFrame(
            a.x,
            a.y,
            a.x + a.w,
            a.y + a.h,
            a.h / 2,
            2,
            border,
            a.enabled ? (hover ? 0xFF4A4A4A : 0xFF2E2E2E) : 0xFF242424,
            0xFF141414);
        int kw = fontRendererObj.getStringWidth(a.key) + 8;
        UiDraw.roundFrame(
            a.x + 6,
            a.y + a.h / 2 - 7,
            a.x + 6 + kw,
            a.y + a.h / 2 + 7,
            2,
            1,
            0xFF8A8A8A,
            0xFF3A3A3A,
            0xFF2A2A2A);
        fontRendererObj.drawString(a.key, a.x + 10, a.y + a.h / 2 - 3, 0xFFE0E0E0);
        int color = a.enabled ? 0xFFFFFFFF : 0xFF777777;
        if (a.big) {
            UiDraw.outlinedText(fontRendererObj, a.label, a.x + 14 + kw, a.y + a.h / 2f - 6, 1.5f, color, 0xFF000000);
        } else {
            fontRendererObj.drawStringWithShadow(a.label, a.x + 12 + kw, a.y + a.h / 2 - 4, color);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void perform(String id) {
        switch (id) {
            case "settings" -> GameSettingsScreen.open(gamePos, this, GameSettingsScreen.Tab.PACKS);
            case "style" -> GameSettingsScreen.open(gamePos, this, GameSettingsScreen.Tab.STYLE);
            case "designs" -> GameSettingsScreen.open(gamePos, this, GameSettingsScreen.Tab.DESIGNS);
            case "reset" -> {
                C2SSeatControlPacket.Client.sendResetSeatsC2S(gamePos);
                mc.displayGuiScreen(null);
            }
            case "stop" -> {
                C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, false);
                mc.displayGuiScreen(null);
            }
            case "start" -> {
                C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, true);
                mc.displayGuiScreen(null);
            }
            default -> {}
        }
        mc.getSoundHandler()
            .playSound(
                net.minecraft.client.audio.PositionedSoundRecord
                    .func_147674_a(new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0f));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        for (Action a : actions) {
            if (a.enabled && mouseX >= a.x && mouseX < a.x + a.w && mouseY >= a.y && mouseY < a.y + a.h) {
                perform(a.id);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char c, int key) {
        String id = switch (key) {
            case Keyboard.KEY_Z -> "settings";
            case Keyboard.KEY_C -> "style";
            case Keyboard.KEY_V -> "designs";
            case Keyboard.KEY_R -> "reset";
            case Keyboard.KEY_S -> "stop";
            case Keyboard.KEY_X -> "start";
            default -> null;
        };
        if (id != null) {
            for (Action a : actions) if (a.id.equals(id) && a.enabled) perform(id);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
