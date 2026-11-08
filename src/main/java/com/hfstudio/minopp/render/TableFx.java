package com.hfstudio.minopp.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class TableFx {

    private enum Kind {
        SKIP,
        COIN,
        FLIP,
        SHOCK,
        POP
    }

    private record Fx(Kind kind, long start, int duration, double x, double y, double z, int color) {

        float t(long now) {
            return Math.min(1f, (now - start) / (float) duration);
        }
    }

    private static final class State {

        int topUid = Integer.MIN_VALUE;
        boolean dark, anti;
        UUID balloon;
        int balloonSeat = -1;
        float ringAngle, ringSpeed = 1f;
        long lastFrame = System.currentTimeMillis();
        final List<Fx> fx = new ArrayList<>();
    }

    private static final Map<BlockEntityMinoTable, State> STATES = new WeakHashMap<>();
    private static final Random RANDOM = new Random();

    private TableFx() {}

    public static void render(BlockEntityMinoTable table, CardGame game) {
        State s = STATES.computeIfAbsent(table, k -> new State());
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - s.lastFrame) / 1000f);
        s.lastFrame = now;
        observe(table, game, s, now);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        s.ringSpeed += (1f - s.ringSpeed) * Math.min(1f, dt * 1.5f);
        s.ringAngle += dt * 22f * s.ringSpeed * (game.isAntiClockwise ? 1 : -1);
        directionRing(s.ringAngle, s.ringSpeed, game.isAntiClockwise ? -1 : 1);

        if (game.balloonHolder != null) {
            int seat = seatOf(game, game.balloonHolder);
            if (seat >= 0) {
                s.balloonSeat = seat;
                balloon(table.getSeatLocalPos(seat), game.balloonFuse, now);
            }
        }

        s.fx.removeIf(f -> f.t(now) >= 1f);
        for (Fx f : s.fx) {
            float t = f.t(now);
            switch (f.kind) {
                case SKIP -> skipMark(f.x, f.y, f.z, t);
                case COIN -> coin(f.x, f.y, f.z, t);
                case FLIP -> flipFlash(f.x, f.y, f.z, t, f.color);
                case SHOCK -> shockwave(f.x, f.y, f.z, t, f.color);
                case POP -> pop(f.x, f.y, f.z, t);
            }
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static int seatOf(CardGame game, UUID uuid) {
        for (CardPlayer p : game.players) if (p.uuid.equals(uuid)) return p.seat;
        return -1;
    }

    private static void observe(BlockEntityMinoTable table, CardGame game, State s, long now) {
        boolean first = s.topUid == Integer.MIN_VALUE;
        int top = game.topCard == null ? -1 : game.topCard.uid;
        if (!first && game.isAntiClockwise != s.anti) s.ringSpeed = 9f;
        if (!first && game.darkSide != s.dark) {
            s.fx.add(new Fx(Kind.FLIP, now, 900, 1.5, 0.95, 1.5, game.darkSide ? 0x8A3CFF : 0xFFE060));
        }
        if (!first && s.balloon != null && game.balloonHolder == null && s.balloonSeat >= 0) {
            Vec3d p = table.getSeatLocalPos(s.balloonSeat);
            Vec3d at = new Vec3d(p.x + (1.5 - p.x) * 0.25, p.y + 1.25, p.z + (1.5 - p.z) * 0.25);
            s.fx.add(new Fx(Kind.POP, now, 500, at.x, at.y, at.z, 0xE83030));
            particles(table, "largeexplode", at.x, at.y, at.z, 1, 0);
        }
        if (!first && top != s.topUid && game.topCard != null) {
            CardFace face = game.topFace();
            s.fx.add(new Fx(Kind.SHOCK, now, 450, 1.5, 0.95, 1.5, game.currentColor.rgb));
            String id = face.type().id;
            if (isSkip(id) || id.equals("reverse") && game.activePlayers()
                .size() == 2) {
                for (int seat : skippedSeats(table, game)) {
                    Vec3d p = table.getSeatLocalPos(seat);
                    s.fx.add(
                        new Fx(
                            Kind.SKIP,
                            now,
                            1300,
                            p.x + (1.5 - p.x) * 0.2,
                            p.y + 1.15,
                            p.z + (1.5 - p.z) * 0.2,
                            0xE02020));
                }
            }
            if (id.equals("wild_fifty_fifty")) s.fx.add(new Fx(Kind.COIN, now, 1600, 1.5, 1.2, 1.5, 0xF0C030));
            cardParticles(table, id);
        }
        s.topUid = top;
        s.dark = game.darkSide;
        s.anti = game.isAntiClockwise;
        s.balloon = game.balloonHolder;
    }

    private static boolean isSkip(String id) {
        return switch (id) {
            case "skip", "skip_everyone", "aw_skip", "aw_skip_two", "flex_skip" -> true;
            default -> false;
        };
    }

    private static List<Integer> skippedSeats(BlockEntityMinoTable table, CardGame game) {
        List<Integer> out = new ArrayList<>();
        int n = game.players.size();
        int from = -1;
        for (int i = 0; i < n; i++) if (game.players.get(i).uuid.equals(table.lastPlayedByUuid)) from = i;
        if (from < 0 || n < 2) return out;
        int step = game.isAntiClockwise ? -1 : 1;
        for (int i = Math.floorMod(from + step, n), guard = 0; i != game.currentPlayerIndex
            && guard < n; i = Math.floorMod(i + step, n), guard++) {
            CardPlayer p = game.players.get(i);
            if (!p.eliminated && i != from) out.add(p.seat);
        }
        return out;
    }

    private static void cardParticles(BlockEntityMinoTable table, String id) {
        String particle;
        int count;
        double spread = 0.6;
        switch (id) {
            case "wild_smoke" -> {
                particle = "largesmoke";
                count = 24;
            }
            case "wild_cyclone", "wild_hand_shuffle", "wild_crossfade", "wild_swap_hands", "trade_hands" -> {
                particle = "portal";
                count = 40;
                spread = 1.2;
            }
            case "wild_card_storm", "wild_surprise", "wild_trickster" -> {
                particle = "fireworksSpark";
                count = 20;
            }
            case "wild_booby_trap", "blaster", "wild_blast_all" -> {
                particle = "lava";
                count = 12;
            }
            case "wild_shield" -> {
                particle = "happyVillager";
                count = 20;
                spread = 1.2;
            }
            case "wild_surge", "wild_echo", "wild_thief" -> {
                particle = "witchMagic";
                count = 24;
            }
            case "wild_dance_floor" -> {
                particle = "note";
                count = 10;
            }
            case "gift", "bonus_turn" -> {
                particle = "heart";
                count = 8;
            }
            case "wild_draw6", "wild_draw10", "wild_plus6", "wild_reverse_plus6", "wild_color_roulette" -> {
                particle = "angryVillager";
                count = 8;
            }
            case "discard_all", "double_discard_all", "wild_even_out", "wild_pile_up" -> {
                particle = "crit";
                count = 24;
            }
            case "wild_time_balloon" -> {
                particle = "cloud";
                count = 10;
            }
            case "wild_hurry_up" -> {
                particle = "flame";
                count = 12;
            }
            default -> {
                return;
            }
        }
        particles(table, particle, 1.5, 1.05, 1.5, count, spread);
    }

    private static void particles(BlockEntityMinoTable table, String name, double lx, double ly, double lz, int count,
        double spread) {
        World w = table.getWorldObj();
        if (w == null || Minecraft.getMinecraft().gameSettings.particleSetting == 2) return;
        for (int i = 0; i < count; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * spread, oz = (RANDOM.nextDouble() - 0.5) * spread;
            double vy = name.equals("note") ? RANDOM.nextDouble() : 0.02 + RANDOM.nextDouble() * 0.06;
            w.spawnParticle(
                name,
                table.xCoord + lx + ox,
                table.yCoord + ly + RANDOM.nextDouble() * 0.2,
                table.zCoord + lz + oz,
                ox * 0.05,
                vy,
                oz * 0.05);
        }
    }

    private static void color(Tessellator t, int rgb, float a) {
        t.setColorRGBA_F((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, a);
    }

    // chevrons circling the discard pile; they spin fast for a moment whenever the direction changes
    private static void directionRing(float angle, float speed, int sign) {
        GL11.glPushMatrix();
        GL11.glTranslated(1.5, 0.945, 1.5);
        GL11.glRotatef(angle, 0, 1, 0);
        GL11.glDepthMask(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        float a = Math.min(0.95f, 0.7f + (speed - 1f) * 0.05f);
        int n = 6;
        double r = 0.66, len = 0.24;
        for (int pass = 0; pass < 2; pass++) {
            double w = pass == 0 ? 0.11 : 0.08;
            double grow = pass == 0 ? 0.03 : 0;
            int rgb = pass == 0 ? 0x3A1A00 : 0xFFD43A;
            float alpha = pass == 0 ? a * 0.45f : a;
            double lift = pass * 0.002;
            for (int i = 0; i < n; i++) {
                double c = i * Math.PI * 2 / n;
                for (int k = 0; k < 2; k++) {
                    double off = k * 0.1 * sign;
                    double tip = c + sign * (len + grow) / r * 0.5 + off / r;
                    double tail = c - sign * (len + grow) / r * 0.5 + off / r;
                    double mx = Math.sin(tail), mz = -Math.cos(tail);
                    double px = Math.sin(tip), pz = -Math.cos(tip);
                    color(t, rgb, alpha * (k == 0 ? 0.6f : 1f));
                    t.addVertex(mx * (r - w), lift, mz * (r - w));
                    t.addVertex(px * r, lift, pz * r);
                    t.addVertex(px * r, lift, pz * r);
                    t.addVertex(mx * (r + w), lift, mz * (r + w));
                }
            }
        }
        t.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static void ring(Tessellator t, double r1, double r2, int segments, int rgb, float a) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            color(t, rgb, a);
            t.addVertex(Math.cos(a0) * r1, Math.sin(a0) * r1, 0);
            t.addVertex(Math.cos(a0) * r2, Math.sin(a0) * r2, 0);
            t.addVertex(Math.cos(a1) * r2, Math.sin(a1) * r2, 0);
            t.addVertex(Math.cos(a1) * r1, Math.sin(a1) * r1, 0);
        }
    }

    private static void billboard() {
        GL11.glRotatef(-net.minecraft.client.renderer.entity.RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(net.minecraft.client.renderer.entity.RenderManager.instance.playerViewX, 1, 0, 0);
    }

    private static float overshoot(float t) {
        float c = 1.70158f * 1.5f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    // the "no entry" sign bouncing over a skipped seat
    private static void skipMark(double x, double y, double z, float t) {
        float in = Math.min(1f, t / 0.3f);
        float scale = 0.28f * overshoot(in);
        float alpha = t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f;
        double bounce = Math.abs(Math.sin(t * Math.PI * 3)) * (1 - t) * 0.18;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + bounce, z);
        billboard();
        GL11.glScalef(scale, scale, scale);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        ring(tes, 0.78, 1.0, 28, 0xE02424, alpha);
        ring(tes, 0, 0.78, 28, 0xFFFFFF, alpha * 0.55f);
        double c = Math.cos(Math.PI / 4), s = Math.sin(Math.PI / 4), h = 0.11, l = 0.8;
        color(tes, 0xE02424, alpha);
        tes.addVertex(-l * c - h * s, -l * s + h * c, 0.001);
        tes.addVertex(l * c - h * s, l * s + h * c, 0.001);
        tes.addVertex(l * c + h * s, l * s - h * c, 0.001);
        tes.addVertex(-l * c + h * s, -l * s - h * c, 0.001);
        tes.draw();
        GL11.glPopMatrix();
    }

    // a gold coin tossed above the pile: it climbs, spins and lands
    private static void coin(double x, double y, double z, float t) {
        double height = Math.sin(Math.min(1f, t / 0.85f) * Math.PI) * 0.7;
        float alpha = t < 0.85f ? 1f : 1f - (t - 0.85f) / 0.15f;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + height, z);
        billboard();
        GL11.glRotatef(t * 1440f, 1, 0, 0);
        GL11.glScalef(0.16f, 0.16f, 0.16f);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        ring(tes, 0, 0.8, 24, 0xF4C430, alpha);
        ring(tes, 0.8, 1.0, 24, 0xB8860B, alpha);
        tes.draw();
        GL11.glPopMatrix();
    }

    private static void flipFlash(double x, double y, double z, float t, int rgb) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.01, z);
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glDepthMask(false);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        float r = 0.2f + t * 1.8f;
        ring(tes, r * 0.82, r, 40, rgb, (1 - t) * 0.8f);
        ring(tes, 0, r * 0.82, 40, rgb, (1 - t) * 0.18f);
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static void shockwave(double x, double y, double z, float t, int rgb) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + 0.005, z);
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glDepthMask(false);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        float e = 1 - (1 - t) * (1 - t);
        float r = 0.18f + e * 0.55f;
        ring(tes, r - 0.035, r, 32, rgb, (1 - t) * 0.75f);
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static void pop(double x, double y, double z, float t) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        billboard();
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            double r1 = 0.1 + t * 0.4, r2 = r1 + 0.12 * (1 - t);
            double w = 0.02;
            double cx = Math.cos(a), sy = Math.sin(a), nx = -sy * w, ny = cx * w;
            color(tes, i % 2 == 0 ? 0xE83030 : 0xFFFFFF, 1 - t);
            tes.addVertex(cx * r1 + nx, sy * r1 + ny, 0);
            tes.addVertex(cx * r2 + nx, sy * r2 + ny, 0);
            tes.addVertex(cx * r2 - nx, sy * r2 - ny, 0);
            tes.addVertex(cx * r1 - nx, sy * r1 - ny, 0);
        }
        tes.draw();
        GL11.glPopMatrix();
    }

    // the time balloon floats over its holder and swells as the fuse runs down
    private static void balloon(Vec3d seat, int fuse, long now) {
        double x = seat.x + (1.5 - seat.x) * 0.25, z = seat.z + (1.5 - seat.z) * 0.25;
        double bob = Math.sin((now % 100000L) / 400.0) * 0.04;
        float size = 0.14f + Math.max(0, 8 - fuse) * 0.018f;
        float wobble = fuse <= 1 ? (float) Math.sin((now % 100000L) / 45.0) * 0.015f : 0;
        double cy = seat.y + 1.25 + bob;
        Tessellator tes = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, cy, z);
        tes.startDrawing(GL11.GL_LINES);
        color(tes, 0xEEEEEE, 0.9f);
        tes.addVertex(0, -size, 0);
        tes.addVertex(0.02, -size - 0.45, 0.01);
        tes.draw();
        GL11.glScalef(size + wobble, (size + wobble) * 1.18f, size + wobble);
        tes.startDrawingQuads();
        int lat = 8, lon = 12;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                float shade = 0.62f + 0.38f * (float) ((Math.sin(t0) + Math.sin(t1)) * 0.25 + 0.5);
                tes.setColorRGBA_F(0.9f * shade, 0.16f * shade, 0.16f * shade, 0.95f);
                tes.addVertex(Math.cos(t0) * Math.cos(p0), Math.sin(t0), Math.cos(t0) * Math.sin(p0));
                tes.addVertex(Math.cos(t1) * Math.cos(p0), Math.sin(t1), Math.cos(t1) * Math.sin(p0));
                tes.addVertex(Math.cos(t1) * Math.cos(p1), Math.sin(t1), Math.cos(t1) * Math.sin(p1));
                tes.addVertex(Math.cos(t0) * Math.cos(p1), Math.sin(t0), Math.cos(t0) * Math.sin(p1));
            }
        }
        tes.draw();
        GL11.glPopMatrix();
    }
}
