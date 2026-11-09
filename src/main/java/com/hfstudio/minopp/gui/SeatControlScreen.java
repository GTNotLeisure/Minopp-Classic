package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.Teams;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SSeatControlPacket;
import com.hfstudio.minopp.render.CardRenderer;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SeatControlScreen extends GuiScreen {

    private final BlockPos gamePos;

    private record Action(String id, String key, String label, int x, int y, int w, int h, boolean enabled,
        boolean big) {}

    private final List<Action> actions = new ArrayList<>();
    /** Seat hit areas of the last frame for team picking: {seat, x1, y1, x2, y2}. */
    private final List<int[]> seatRects = new ArrayList<>();
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
        boolean teams = Teams.enabled(te.config);
        int sideW = Math.min(150, width / 3);
        int x = width - 10 - sideW;
        int y = 44, h = 22, step = 28;
        int half = (sideW - 4) / 2, third = (sideW - 8) / 3;
        actions.add(new Action("settings", "Z", tr("gui.minopp.seats.settings"), x, y, sideW, h, true, false));
        actions.add(new Action("style", "C", tr("gui.minopp.seats.style"), x, y + step, sideW, h, true, false));
        actions.add(new Action("designs", "V", tr("gui.minopp.seats.designs"), x, y + step * 2, sideW, h, true, false));
        int r = y + step * 3;
        actions.add(new Action("reset", "R", tr("gui.minopp.seats.reset_plain"), x, r, half, h, idle && seated, false));
        actions.add(
            new Action("leave", "L", tr("gui.minopp.seats.leave"), x + half + 4, r, half, h, idle && seated, false));
        r += step;
        if (teams) {
            actions.add(new Action("stop", "S", tr("gui.minopp.seats.stop_plain"), x, r, half, h, !idle, false));
            actions.add(
                new Action(
                    "teams",
                    "T",
                    tr("gui.minopp.seats.auto_teams"),
                    x + half + 4,
                    r,
                    half,
                    h,
                    idle && seated,
                    false));
        } else {
            actions.add(new Action("stop", "S", tr("gui.minopp.seats.stop_plain"), x, r, sideW, h, !idle, false));
        }
        r += step;
        int seatedCount = te.getPlayersList()
            .size();
        boolean freeSeat = seatedCount < te.seatCount();
        actions.add(
            new Action(
                "bot_add",
                null,
                tr("gui.minopp.seats.bot_add"),
                x,
                r,
                third,
                h,
                idle && (freeSeat || seatedCount > 0),
                false));
        actions.add(
            new Action(
                "bot_fill",
                null,
                tr("gui.minopp.seats.bot_fill"),
                x + third + 4,
                r,
                third,
                h,
                idle && freeSeat,
                false));
        actions.add(
            new Action(
                "bot_clear",
                null,
                tr("gui.minopp.seats.bot_clear"),
                x + (third + 4) * 2,
                r,
                third,
                h,
                idle,
                false));
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
                    .size() >= 2 && teamProblem(te) == null,
                true));
    }

    private static IChatComponent teamProblem(BlockEntityMinoTable te) {
        if (!Teams.enabled(te.config)) return null;
        return Teams.problem(te.getPlayersList(), Teams.count(te.config));
    }

    private boolean canChangeTeam(CardPlayer p) {
        UUID me = mc.thePlayer.getGameProfile()
            .getId();
        if (p.uuid.equals(me)) return true;
        for (EntityPlayer o : mc.theWorld.playerEntities) {
            if (o.getGameProfile()
                .getId()
                .equals(p.uuid)) return false;
        }
        return true;
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
        if (!te.hostName.isEmpty()) sub += "   " + EnumChatFormatting.GOLD + tr("gui.minopp.seats.host", te.hostName);
        if (te.game == null && Teams.enabled(te.config)) {
            IChatComponent problem = teamProblem(te);
            sub += "   " + Teams.summary(te.getPlayersList(), Teams.count(te.config))
                .getFormattedText() + (problem == null ? EnumChatFormatting.GREEN + "  \u2714" : "");
        }
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(sub, width - 24), 12, 30, 0xFFFFFFFF);

        int sideW = Math.min(150, width / 3);
        int areaX1 = 10, areaY1 = 44, areaX2 = width - 20 - sideW, areaY2 = height - 50;
        UiDraw.panel(areaX1, areaY1, areaX2, areaY2);
        int cx = (areaX1 + areaX2) / 2, cy = (areaY1 + areaY2) / 2;
        int rx = (areaX2 - areaX1) / 2 - 34, ry = (areaY2 - areaY1) / 2 - 20;
        drawFelt(cx, cy, (int) (rx * 0.62), (int) (ry * 0.55));
        int target = te.config.getInt(GameRules.SCORE_TARGET);
        if (target > 0 && !te.scores.isEmpty()) {
            StringBuilder line = new StringBuilder(tr("gui.minopp.seats.points", target));
            for (Map.Entry<UUID, Integer> e : te.scores.entrySet()) {
                line.append("  ")
                    .append(EnumChatFormatting.WHITE)
                    .append(te.scoreNames.getOrDefault(e.getKey(), "?"))
                    .append(' ')
                    .append(EnumChatFormatting.GOLD)
                    .append(e.getValue());
            }
            fontRendererObj.drawStringWithShadow(
                fontRendererObj.trimStringToWidth(line.toString(), areaX2 - areaX1 - 12),
                areaX1 + 6,
                areaY1 + 5,
                0xFFFFE0A0);
        }

        seatRects.clear();
        int seatCount = te.seatCount();
        float appear = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        for (int i = 0; i < seatCount; i++) {
            double a = BlockEntityMinoTable.seatAngle(i, seatCount);
            int sx = cx + (int) (Math.sin(a) * rx * (0.7f + 0.3f * appear));
            int sy = cy - (int) (Math.cos(a) * ry * (0.7f + 0.3f * appear));
            drawSeat(te, i, sx, sy, mouseX, mouseY);
        }

        for (Action act : actions) drawAction(act, mouseX, mouseY);

        IChatComponent problem = te.game == null ? teamProblem(te) : null;
        if (problem != null) {
            String why = fontRendererObj.trimStringToWidth(tr("gui.minopp.seats.teams_unequal"), width - 40);
            fontRendererObj.drawStringWithShadow(why, 16, height - 49, 0xFFFF8080);
        }
        for (Action act : actions) {
            if (act.big || mouseX < act.x || mouseX >= act.x + act.w || mouseY < act.y || mouseY >= act.y + act.h)
                continue;
            List<String> tip = new ArrayList<>();
            tip.add(
                act.label
                    + (actionKey(act.id) != null ? EnumChatFormatting.GRAY + "  [" + actionKey(act.id) + "]" : ""));
            String desc = "gui.minopp.seats.tip." + act.id;
            if (net.minecraft.util.StatCollector.canTranslate(desc)) tip.add(EnumChatFormatting.GRAY + tr(desc));
            UiDraw.tooltip(fontRendererObj, tip, mouseX, mouseY, width, height);
        }
        if (te.game == null && Teams.enabled(te.config)) {
            for (int[] r : seatRects) {
                if (mouseX >= r[1] && mouseX < r[3] && mouseY >= r[2] && mouseY < r[4]) {
                    CardPlayer p = te.seats[r[0]];
                    if (p == null) break;
                    List<String> tip = new ArrayList<>();
                    tip.add(
                        p.name + "  "
                            + Teams.name(Math.max(0, p.team))
                                .getFormattedText());
                    tip.add(
                        EnumChatFormatting.GRAY
                            + tr(canChangeTeam(p) ? "gui.minopp.seats.team_hint" : "gui.minopp.seats.team_locked"));
                    UiDraw.tooltip(fontRendererObj, tip, mouseX, mouseY, width, height);
                    break;
                }
            }
        }

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
        CardRenderer.drawBack2D(ThemeManager.INSTANCE.selected(), false, cx - 9, cy - 14, 18, 28);
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
        seatRects.add(new int[] { seat, x1, y1, x1 + w, y1 + h });
        int top = 0xFF3A6AD0, bottom = 0xFF1A3A90;
        int team = te.game != null ? teamInGame(te, p) : p.team;
        if (Teams.enabled(te.config) && team >= 0) {
            int rgb = Teams.rgb(team);
            top = 0xFF000000 | rgb;
            bottom = 0xFF000000 | UiDraw.lerpColor(0xFF000000 | rgb, 0xFF000000, 0.45f);
        }
        boolean hover = te.game == null && Teams.enabled(te.config)
            && mouseX >= x1
            && mouseX < x1 + w
            && mouseY >= y1
            && mouseY < y1 + h
            && canChangeTeam(p);
        UiDraw.roundFrame(
            x1,
            y1,
            x1 + w,
            y1 + h,
            6,
            1,
            current || hover ? UiDraw.GLOW : (me ? 0xFFFFFFFF : 0xFF2A2A2A),
            top,
            bottom);
        GuiDraw.drawPlayerHead(PlayerPicker.skinOf(p.uuid), x1 + 3, y1 + 3, 10);
        UiDraw.marquee(fontRendererObj, p.name, x1 + 16, y1 + 4, w - 18, 1f, 0xFFFFFFFF);
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

    private static int teamInGame(BlockEntityMinoTable te, CardPlayer seat) {
        CardPlayer gp = te.game.deAmputate(seat.uuid);
        return gp == null ? -1 : gp.team;
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
        int kw = 0;
        if (a.key != null) {
            kw = fontRendererObj.getStringWidth(a.key) + 8;
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
        }
        int color = a.enabled ? 0xFFFFFFFF : 0xFF777777;
        if (a.big) {
            UiDraw.outlinedText(fontRendererObj, a.label, a.x + 14 + kw, a.y + a.h / 2f - 6, 1.5f, color, 0xFF000000);
        } else {
            int room = a.w - kw - (a.key != null ? 16 : 8);
            int lw = fontRendererObj.getStringWidth(a.label);
            int lx = a.key != null ? a.x + 12 + kw : a.x + (lw <= room ? (a.w - lw) / 2 : 4);
            UiDraw.marquee(fontRendererObj, a.label, lx, a.y + a.h / 2 - 4, room, 1f, color);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static String actionKey(String id) {
        return switch (id) {
            case "settings" -> "Z";
            case "style" -> "C";
            case "designs" -> "V";
            case "reset" -> "R";
            case "leave" -> "L";
            case "stop" -> "S";
            case "teams" -> "T";
            case "bot_add" -> "B";
            case "bot_fill" -> "F";
            case "bot_clear" -> "K";
            case "start" -> "X";
            default -> null;
        };
    }

    private void perform(String id) {
        switch (id) {
            case "leave" -> C2SSeatControlPacket.Client.sendLeaveC2S(gamePos);
            case "bot_add" -> C2SSeatControlPacket.Client.sendBotsC2S(gamePos, 0);
            case "bot_fill" -> C2SSeatControlPacket.Client.sendBotsC2S(gamePos, 1);
            case "bot_clear" -> C2SSeatControlPacket.Client.sendBotsC2S(gamePos, 2);
            case "bot_remove" -> C2SSeatControlPacket.Client.sendBotsC2S(gamePos, 3);
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
            case "teams" -> C2SSeatControlPacket.Client.sendAutoTeamsC2S(gamePos);
            case "start" -> {
                C2SSeatControlPacket.Client.sendGameEnableC2S(gamePos, true);
                mc.displayGuiScreen(null);
            }
            default -> {}
        }
        mc.getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        for (Action a : actions) {
            if (!a.enabled || mouseX < a.x || mouseX >= a.x + a.w || mouseY < a.y || mouseY >= a.y + a.h) continue;
            if (button == 0) {
                perform(a.id);
                return;
            }
            if (button == 1 && a.id.equals("bot_add")) {
                perform("bot_remove");
                return;
            }
        }
        BlockEntityMinoTable te = getTable();
        if (te != null && te.game == null && Teams.enabled(te.config) && (button == 0 || button == 1)) {
            for (int[] r : seatRects) {
                if (mouseX < r[1] || mouseX >= r[3] || mouseY < r[2] || mouseY >= r[4]) continue;
                CardPlayer p = te.seats[r[0]];
                if (p == null || !canChangeTeam(p)) break;
                int count = Teams.count(te.config);
                int next = Math.floorMod(Math.max(0, p.team) + (button == 0 ? 1 : -1), count);
                C2SSeatControlPacket.Client.sendTeamC2S(gamePos, p.uuid, next);
                mc.getSoundHandler()
                    .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0f));
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
            case Keyboard.KEY_T -> "teams";
            case Keyboard.KEY_L -> "leave";
            case Keyboard.KEY_B -> isShiftKeyDown() ? "bot_remove" : "bot_add";
            case Keyboard.KEY_F -> "bot_fill";
            case Keyboard.KEY_K -> "bot_clear";
            default -> null;
        };
        if (id != null) {
            String actionId = id.equals("bot_remove") ? "bot_add" : id;
            for (Action a : actions) if (a.id.equals(actionId) && a.enabled) perform(id);
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
