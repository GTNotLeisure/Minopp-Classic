package com.hfstudio.minopp.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardColor;
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
        POP,
        REVERSE,
        WILD,
        SKIP_SIGN,
        DRAW,
        SHOUT,
        PLUS
    }

    private record Fx(Kind kind, long start, int duration, double x, double y, double z, int color, Object data) {

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
        final Set<UUID> shouted = new HashSet<>();
        int shoutSeq = -1;
    }

    private static final ResourceLocation TEX_DIRECTION = Mino.id("textures/fx/direction.png");
    private static final ResourceLocation TEX_SKIP = Mino.id("textures/fx/skip.png");
    private static final ResourceLocation TEX_COIN = Mino.id("textures/fx/coin.png");
    private static final double PILE_Y = 0.95;

    private static final Map<BlockEntityMinoTable, State> STATES = new WeakHashMap<>();
    private static final Random RANDOM = new Random();
    private static final int[][] BALLOON = balloonVoxels();

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
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        s.ringSpeed += (1f - s.ringSpeed) * Math.min(1f, dt * 1.5f);
        s.ringAngle += dt * 22f * s.ringSpeed * (game.isAntiClockwise ? 1 : -1);
        directionRing(s.ringAngle, s.ringSpeed, game.isAntiClockwise);

        if (game.balloonHolder != null) {
            int seat = seatOf(game, game.balloonHolder);
            if (seat >= 0) {
                s.balloonSeat = seat;
                boolean busy = false;
                for (Fx f : s.fx) busy |= now >= f.start && f.kind != Kind.SHOCK && f.kind != Kind.SKIP;
                balloon(table, table.getSeatLocalPos(seat), game.balloonFuse, now, busy);
            }
        }

        s.fx.removeIf(f -> f.t(now) >= 1f);
        for (Fx f : s.fx) {
            float t = f.t(now);
            if (now < f.start) continue;
            switch (f.kind) {
                case SKIP -> skipMark(f, t);
                case COIN -> coin(f, t);
                case FLIP -> flipCard(f, t);
                case SHOCK -> shockwave(f, t);
                case POP -> burst(f, t, 14, 0.06f);
                case REVERSE -> reverseSymbol(f, t);
                case WILD -> wildPillar(f, t);
                case SKIP_SIGN -> skipSymbol(f, t);
                case DRAW -> drawSlabs(f, t);
                case SHOUT -> shoutText(f, t);
                case PLUS -> plusText(f, t);
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
        String theme = ThemeManager.INSTANCE.selected();
        boolean turned = !first && game.isAntiClockwise != s.anti;
        if (turned) s.ringSpeed = 9f;
        for (CardPlayer p : game.players) {
            if (p.hasShoutedMino && !s.shouted.contains(p.uuid) && !first) {
                Vec3d at = head(table, p.seat, 1.95);
                s.fx.add(new Fx(Kind.SHOUT, now, 1800, at.x, at.y, at.z, 0, null));
            }
        }
        s.shouted.clear();
        for (CardPlayer p : game.players) if (p.hasShoutedMino) s.shouted.add(p.uuid);
        if (!first && game.debugShoutSeq != s.shoutSeq && game.debugShoutBy != null) {
            int seat = seatOf(game, game.debugShoutBy);
            if (seat >= 0) {
                Vec3d at = head(table, seat, 1.95);
                s.fx.add(new Fx(Kind.SHOUT, now, 1800, at.x, at.y, at.z, 0, null));
            }
        }
        s.shoutSeq = game.debugShoutSeq;
        if (!first && game.darkSide != s.dark && game.topCard != null) {
            CardFace[] faces = { game.topCard.face(s.dark), game.topCard.face(game.darkSide) };
            s.fx.add(new Fx(Kind.FLIP, now, 1500, 1.5, PILE_Y, 1.5, 0, new Object[] { faces, theme }));
        }
        if (!first && s.balloon != null && game.balloonHolder == null && s.balloonSeat >= 0) {
            Vec3d p = table.getSeatLocalPos(s.balloonSeat);
            Vec3d at = new Vec3d(p.x + (p.x - 1.5) * 0.15, p.y + 2.15, p.z + (p.z - 1.5) * 0.15);
            s.fx.add(new Fx(Kind.POP, now, 900, at.x, at.y, at.z, 0xE83030, now));
            particles(table, "largeexplode", at.x, at.y, at.z, 1, 0);
        }
        if (!first && top != s.topUid && game.topCard != null) {
            CardFace face = game.topFace();
            s.fx.add(new Fx(Kind.SHOCK, now, 500, 1.5, PILE_Y, 1.5, game.currentColor.rgb, null));
            String id = face.type().id;
            int rgb = game.currentColor.isWild() ? 0xFFFFFF : game.currentColor.rgb;
            boolean reverseCard = face.type().matchKey.equals("reverse") || id.contains("reverse");
            if (reverseCard || turned) {
                s.fx.add(new Fx(Kind.REVERSE, now, 1300, 1.5, PILE_Y, 1.5, rgb, game.isAntiClockwise));
                turned = false;
            } else if (isSkip(id) || face.type().matchKey.equals("skip")) {
                s.fx.add(new Fx(Kind.SKIP_SIGN, now, 1300, 1.5, PILE_Y, 1.5, rgb, null));
            }
            int victimIdx = game.debugHold && game.debugNext >= 0 ? game.debugNext : game.currentPlayerIndex;
            if (face.type()
                .isDraw()) {
                int amount = game.pendingDraw > 0 ? game.pendingDraw : face.type().drawValue;
                Vec3d at = victimIdx >= 0 && victimIdx < game.players.size()
                    ? head(table, game.players.get(victimIdx).seat, 1.75)
                    : new Vec3d(1.5, PILE_Y + 1.75, 1.5);
                s.fx.add(
                    new Fx(Kind.PLUS, now + (face.isWild() ? 1000 : 250), 1400, at.x, at.y, at.z, rgb, "+" + amount));
            }
            if (face.type()
                .isDraw() && !face.isWild()) {
                Vec3d victim = null;
                if (victimIdx >= 0 && victimIdx < game.players.size()) {
                    Vec3d seat = table.getSeatLocalPos(game.players.get(victimIdx).seat);
                    victim = new Vec3d(seat.x, seat.y + 0.4, seat.z);
                }
                s.fx.add(
                    new Fx(
                        Kind.DRAW,
                        now,
                        1500,
                        1.5,
                        PILE_Y,
                        1.5,
                        rgb,
                        new Object[] { Math.max(1, Math.min(5, face.type().drawValue)), victim }));
            }
            if (isSkip(id) || id.equals("reverse") && game.activePlayers()
                .size() == 2) {
                for (int seat : skippedSeats(game)) {
                    Vec3d at = head(table, seat, 1.5);
                    s.fx.add(new Fx(Kind.SKIP, now, 1600, at.x, at.y, at.z, 0, null));
                }
            }
            if (id.equals("wild_fifty_fifty")) s.fx.add(new Fx(Kind.COIN, now, 1600, 1.5, 1.2, 1.5, 0, null));
            if (face.isWild() && !face.type().keepsColor && game.currentColor != CardColor.WILD) {
                boolean blocks = face.type()
                    .isDraw();
                s.fx.add(
                    new Fx(
                        Kind.WILD,
                        now,
                        1800,
                        1.5,
                        PILE_Y,
                        1.5,
                        game.currentColor.rgb,
                        new Object[] { game.currentColor, blocks }));
            }
            cardParticles(table, id);
        }
        if (turned) s.fx.add(new Fx(Kind.REVERSE, now, 1300, 1.5, PILE_Y, 1.5, 0xFFD43A, game.isAntiClockwise));
        s.topUid = top;
        s.dark = game.darkSide;
        s.anti = game.isAntiClockwise;
        s.balloon = game.balloonHolder;
    }

    // just over the head of whoever sits in that seat
    private static Vec3d head(BlockEntityMinoTable table, int seat, double lift) {
        Vec3d p = table.getSeatLocalPos(seat);
        double ox = p.x - 1.5, oz = p.z - 1.5, len = Math.max(1e-3, Math.sqrt(ox * ox + oz * oz));
        return new Vec3d(p.x + ox / len * 0.45, p.y + lift, p.z + oz / len * 0.45);
    }

    public static boolean isSkip(String id) {
        return switch (id) {
            case "skip", "skip_everyone", "aw_skip", "aw_skip_two", "flex_skip", "reverse_skip" -> true;
            default -> false;
        };
    }

    public static List<Integer> skippedSeats(CardGame game) {
        List<Integer> out = new ArrayList<>();
        int n = game.players.size();
        int from = -1;
        UUID owner = game.topCard == null ? null : game.topCard.owner;
        for (int i = 0; i < n; i++) if (game.players.get(i).uuid.equals(owner)) from = i;
        if (from < 0) return out;
        int until = game.debugHold && game.debugNext >= 0 ? game.debugNext : game.currentPlayerIndex;
        int step = game.isAntiClockwise ? -1 : 1;
        if (n >= 2) {
            for (int i = Math.floorMod(from + step, n), guard = 0; i != until
                && guard < n; i = Math.floorMod(i + step, n), guard++) {
                CardPlayer p = game.players.get(i);
                if (!p.eliminated && i != from) out.add(p.seat);
            }
        }
        if (out.isEmpty() && game.debug) out.add(game.players.get(from).seat);
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

    private static int shade(int rgb, float k) {
        int r = (int) ((rgb >> 16 & 255) * k), g = (int) ((rgb >> 8 & 255) * k), b = (int) ((rgb & 255) * k);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static void bind(ResourceLocation tex) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    // a unit textured square in the local XY plane, image top toward +Y
    private static void sprite(ResourceLocation tex, float alpha, boolean mirror) {
        bind(tex);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(1, 1, 1, alpha);
        double u0 = mirror ? 1 : 0, u1 = mirror ? 0 : 1;
        t.addVertexWithUV(-0.5, -0.5, 0, u0, 1);
        t.addVertexWithUV(0.5, -0.5, 0, u1, 1);
        t.addVertexWithUV(0.5, 0.5, 0, u1, 0);
        t.addVertexWithUV(-0.5, 0.5, 0, u0, 0);
        t.draw();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    // faces the camera like a name tag, +X to the viewer's right and +Y up
    private static void billboard() {
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
        GL11.glScalef(-1, 1, 1);
    }

    private static float overshoot(float t) {
        float c = 1.70158f * 1.5f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static float easeOutCubic(float t) {
        float u = 1 - Math.max(0f, Math.min(1f, t));
        return 1 - u * u * u;
    }

    private static float fadeOut(float t, float from) {
        return t < from ? 1f : Math.max(0f, 1f - (t - from) / (1f - from));
    }

    private static void cube(Tessellator t, double x, double y, double z, double s, int rgb, float a, int mask) {
        double h = s / 2, x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        if ((mask & 1) != 0) {
            color(t, rgb, a);
            t.addVertex(x0, y1, z0);
            t.addVertex(x0, y1, z1);
            t.addVertex(x1, y1, z1);
            t.addVertex(x1, y1, z0);
        }
        if ((mask & 2) != 0) {
            color(t, shade(rgb, 0.5f), a);
            t.addVertex(x0, y0, z0);
            t.addVertex(x1, y0, z0);
            t.addVertex(x1, y0, z1);
            t.addVertex(x0, y0, z1);
        }
        if ((mask & 12) != 0) color(t, shade(rgb, 0.8f), a);
        if ((mask & 4) != 0) {
            t.addVertex(x0, y0, z0);
            t.addVertex(x0, y1, z0);
            t.addVertex(x1, y1, z0);
            t.addVertex(x1, y0, z0);
        }
        if ((mask & 8) != 0) {
            t.addVertex(x0, y0, z1);
            t.addVertex(x1, y0, z1);
            t.addVertex(x1, y1, z1);
            t.addVertex(x0, y1, z1);
        }
        if ((mask & 48) != 0) color(t, shade(rgb, 0.6f), a);
        if ((mask & 16) != 0) {
            t.addVertex(x0, y0, z0);
            t.addVertex(x0, y0, z1);
            t.addVertex(x0, y1, z1);
            t.addVertex(x0, y1, z0);
        }
        if ((mask & 32) != 0) {
            t.addVertex(x1, y0, z0);
            t.addVertex(x1, y1, z0);
            t.addVertex(x1, y1, z1);
            t.addVertex(x1, y0, z1);
        }
    }

    // block-like bits flying out of a point and falling back
    private static void cubes(double x, double y, double z, float sec, long seed, int[] colors, int count, float size,
        float speed, float alpha) {
        Random r = new Random(seed);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2, out = (0.4 + r.nextDouble() * 0.6) * speed;
            double vx = Math.cos(a) * out, vz = Math.sin(a) * out, vy = (0.5 + r.nextDouble()) * speed;
            float s = size * (0.6f + r.nextFloat() * 0.7f) * Math.max(0f, 1f - sec / 1.4f);
            if (s <= 0.002f) continue;
            double px = x + vx * sec, py = y + vy * sec - 2.4 * sec * sec, pz = z + vz * sec;
            cube(t, px, py, pz, s, colors[i % colors.length], alpha, 63);
        }
        t.draw();
    }

    // the play direction: two pixel arrows lying on the table around the discard pile
    private static void directionRing(float angle, float speed, boolean anti) {
        GL11.glPushMatrix();
        GL11.glTranslated(1.5, PILE_Y - 0.005, 1.5);
        GL11.glRotatef(angle, 0, 1, 0);
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glScalef(1.75f, 1.75f, 1.75f);
        GL11.glDepthMask(false);
        sprite(TEX_DIRECTION, Math.min(1f, 0.85f + (speed - 1f) * 0.05f), anti);
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    // the "no entry" sign bouncing over a skipped seat
    private static void skipMark(Fx f, float t) {
        float scale = 0.55f * overshoot(Math.min(1f, t / 0.25f));
        double bounce = Math.abs(Math.sin(t * Math.PI * 4)) * (1 - t) * 0.25;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + bounce, f.z);
        billboard();
        GL11.glRotatef((float) Math.sin(t * Math.PI * 6) * 14 * (1 - t), 0, 0, 1);
        GL11.glScalef(scale, scale, scale);
        sprite(TEX_SKIP, fadeOut(t, 0.8f), false);
        GL11.glPopMatrix();
    }

    // a gold coin tossed above the pile: it climbs, flips end over end and lands
    private static void coin(Fx f, float t) {
        double height = Math.sin(Math.min(1f, t / 0.85f) * Math.PI) * 0.85;
        float alpha = fadeOut(t, 0.85f);
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + height, f.z);
        billboard();
        GL11.glRotatef(t * 1800f, 1, 0, 0);
        GL11.glScalef(0.34f, 0.34f, 0.34f);
        for (int i = 0; i < 3; i++) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, (i - 1) * 0.05f);
            sprite(TEX_COIN, alpha, i == 2);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    // the top card rises, turns over to its other side and drops back onto the pile
    private static void flipCard(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        CardFace[] faces = (CardFace[]) d[0];
        String theme = (String) d[1];
        float rise = t < 0.22f ? easeOutCubic(t / 0.22f) : t > 0.8f ? 1 - easeOutCubic((t - 0.8f) / 0.2f) : 1;
        float turn = Math.max(0f, Math.min(1f, (t - 0.25f) / 0.45f));
        float deg = (float) (0.5 - 0.5 * Math.cos(turn * Math.PI)) * 180f;
        float s = 0.11f * (0.55f + 0.45f * rise);
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + 0.05 + rise * 0.8, f.z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glScalef(-1, 1, 1);
        GL11.glRotatef((float) Math.sin(t * Math.PI * 2) * 6, 0, 0, 1);
        GL11.glRotatef(deg, 0, 1, 0);
        if (deg > 90) GL11.glRotatef(180, 0, 1, 0);
        GL11.glScalef(s, -s, s);
        float bend = (float) Math.sin(turn * Math.PI) * 0.22f + (float) Math.sin(rise * Math.PI) * 0.05f;
        bentFace(deg > 90 ? faces[1] : faces[0], theme, 5.6f, 8.9f, deg > 90 ? -bend : bend);
        GL11.glPopMatrix();
        if (t > 0.45f) {
            float sec = (t - 0.45f) * f.duration / 1000f;
            int c = faces[1].color.isWild() ? 0x8A3CFF : faces[1].color.rgb & 0xFFFFFF;
            cubes(f.x, f.y + 0.85, f.z, sec, f.start, new int[] { c, 0xFFFFFF, shade(c, 0.7f) }, 20, 0.06f, 1.4f, 1f);
        }
    }

    // the wild's color pillar rises from the pile and spins: a ring cut in four for a plain wild, four blocks for a
    // draw wild; it stops with the chosen color facing the viewer, the other three sink and the chosen one bursts
    private static void wildPillar(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        CardColor chosen = (CardColor) d[0];
        boolean square = (Boolean) d[1];
        List<CardColor> colors = CardColor.forSide(chosen.side == CardColor.Side.DARK);
        int pick = Math.max(0, colors.indexOf(chosen));
        float stop = 0.55f;
        float rise = easeOutCubic(Math.min(1f, t / 0.15f));
        float k = Math.min(1f, t / stop);
        float spin = pick * 90 + (square ? 0 : 45) - 270 + (1 - easeOutCubic(k)) * 1080f;
        float settle = t < stop ? 0f : Math.min(1f, (t - stop) / 0.2f);
        double height = 0.6 * rise;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, PILE_Y, f.z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(spin, 0, 1, 0);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        for (int q = 0; q < 4; q++) {
            boolean picked = q == pick;
            if (!picked && settle >= 1f) continue;
            int rgb = colors.get(q).rgb & 0xFFFFFF;
            double y0 = picked ? settle * 0.25 : -settle * 0.4;
            double y1 = y0 + (picked ? height * (1 + settle * 0.3) : height * (1 - settle));
            float alpha = picked ? fadeOut(t, 0.85f) : 1f - settle;
            if (y1 - y0 < 0.01 || alpha <= 0.02f) continue;
            if (square) {
                double c = Math.toRadians(q * 90), cx = Math.cos(c) * 0.32, cz = Math.sin(c) * 0.32;
                double s = 0.28;
                cuboid(tes, cx - s / 2, y0, cz - s / 2, cx + s / 2, y1, cz + s / 2, rgb, alpha);
                if (settle <= 0f) {
                    double top = y1 + 0.004, tip = 0.14, base = 0.045;
                    double nx = Math.cos(c), nz = Math.sin(c), px = -nz, pz = nx;
                    color(tes, 0xFF8A1E, alpha);
                    tes.addVertex(nx * (tip + 0.05), top, nz * (tip + 0.05));
                    tes.addVertex(nx * 0.04 + px * base * 1.6, top, nz * 0.04 + pz * base * 1.6);
                    tes.addVertex(nx * 0.04 - px * base * 1.6, top, nz * 0.04 - pz * base * 1.6);
                    tes.addVertex(nx * 0.04 - px * base * 1.6, top, nz * 0.04 - pz * base * 1.6);
                }
            } else {
                ringQuarter(tes, q * 90 + 3, q * 90 + 87, 0.03, 0.56, y0, y1, rgb, alpha);
            }
        }
        tes.draw();
        GL11.glPopMatrix();
        if (t >= stop + 0.12f) {
            float sec = (t - stop - 0.12f) * f.duration / 1000f;
            int rgb = chosen.rgb & 0xFFFFFF;
            float alpha = fadeOut(t, 0.85f);
            cubes(
                f.x,
                PILE_Y + 0.6,
                f.z,
                sec,
                f.start,
                new int[] { rgb, shade(rgb, 1.3f), shade(rgb, 0.75f) },
                28,
                0.08f,
                1.8f,
                alpha);
            float grow = Math.min(1f, (t - stop - 0.12f) / 0.12f);
            GL11.glPushMatrix();
            GL11.glTranslated(f.x, PILE_Y, f.z);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
            GL11.glDepthMask(false);
            tes.startDrawingQuads();
            double w = 0.3, h = 2.2 * grow;
            color(tes, rgb, 0.5f * alpha);
            tes.addVertex(-w, 0, 0);
            tes.addVertex(w, 0, 0);
            color(tes, rgb, 0f);
            tes.addVertex(w, h, 0);
            tes.addVertex(-w, h, 0);
            tes.draw();
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
        }
    }

    private static void cuboid(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1, int rgb,
        float a) {
        color(t, rgb, a);
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        color(t, shade(rgb, 0.8f), a);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        color(t, shade(rgb, 0.6f), a);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
    }

    // a quarter of a thick ring, faceted like a block model
    private static void ringQuarter(Tessellator t, double fromDeg, double toDeg, double r0, double r1, double y0,
        double y1, int rgb, float a) {
        int steps = 4;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            double a1 = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 1) / steps);
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            color(t, rgb, a);
            t.addVertex(c0 * r0, y1, s0 * r0);
            t.addVertex(c0 * r1, y1, s0 * r1);
            t.addVertex(c1 * r1, y1, s1 * r1);
            t.addVertex(c1 * r0, y1, s1 * r0);
            color(t, shade(rgb, 0.8f), a);
            t.addVertex(c0 * r1, y0, s0 * r1);
            t.addVertex(c0 * r1, y1, s0 * r1);
            t.addVertex(c1 * r1, y1, s1 * r1);
            t.addVertex(c1 * r1, y0, s1 * r1);
            color(t, shade(rgb, 0.55f), a);
            t.addVertex(c0 * r0, y0, s0 * r0);
            t.addVertex(c0 * r0, y1, s0 * r0);
            t.addVertex(c1 * r0, y1, s1 * r0);
            t.addVertex(c1 * r0, y0, s1 * r0);
        }
        color(t, shade(rgb, 0.7f), a);
        for (double deg : new double[] { fromDeg, toDeg }) {
            double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
            t.addVertex(c * r0, y0, s * r0);
            t.addVertex(c * r1, y0, s * r1);
            t.addVertex(c * r1, y1, s * r1);
            t.addVertex(c * r0, y1, s * r0);
        }
    }

    // a ring of pixel squares in the new color spreading out from the pile
    private static void shockwave(Fx f, float t) {
        float e = 1 - (1 - t) * (1 - t);
        double r = 0.2 + e * 0.8;
        int n = (int) (r * 60);
        int rgb = f.color & 0xFFFFFF;
        double px = 1 / 16.0;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + 0.004, f.z);
        GL11.glDepthMask(false);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        color(tes, rgb, (1 - t) * 0.9f);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            long gx = Math.round(Math.cos(a) * r / px), gz = Math.round(Math.sin(a) * r / px);
            if (!seen.add(gx * 100003 + gz)) continue;
            double x0 = gx * px - px / 2, z0 = gz * px - px / 2;
            tes.addVertex(x0, 0, z0);
            tes.addVertex(x0, 0, z0 + px);
            tes.addVertex(x0 + px, 0, z0 + px);
            tes.addVertex(x0 + px, 0, z0);
        }
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static void burst(Fx f, float t, int count, float size) {
        cubes(
            f.x,
            f.y,
            f.z,
            t * f.duration / 1000f,
            f.start,
            new int[] { f.color & 0xFFFFFF, 0xFFFFFF },
            count,
            size,
            1.6f,
            fadeOut(t, 0.7f));
    }

    private static int[][] balloonVoxels() {
        List<int[]> out = new ArrayList<>();
        Set<Long> in = new HashSet<>();
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 4; y++) for (int z = -3; z <= 3; z++) {
            double ny = (y - 0.5) / 3.9;
            if (x * x / 9.6 + ny * ny + z * z / 9.6 <= 1) in.add(key(x, y, z));
        }
        for (long k : in) {
            int x = (int) (k >> 16 & 255) - 64, y = (int) (k >> 8 & 255) - 64, z = (int) (k & 255) - 64;
            int mask = 0;
            if (!in.contains(key(x, y + 1, z))) mask |= 1;
            if (!in.contains(key(x, y - 1, z))) mask |= 2;
            if (!in.contains(key(x, y, z - 1))) mask |= 4;
            if (!in.contains(key(x, y, z + 1))) mask |= 8;
            if (!in.contains(key(x - 1, y, z))) mask |= 16;
            if (!in.contains(key(x + 1, y, z))) mask |= 32;
            if (mask != 0) out.add(new int[] { x, y, z, mask });
        }
        return out.toArray(new int[0][]);
    }

    private static long key(int x, int y, int z) {
        return (long) (x + 64) << 16 | (long) (y + 64) << 8 | (z + 64);
    }

    // the time balloon, built from blocks, floats over its holder and swells as the fuse runs down
    // kept above and behind the holder; it turns see-through while another effect plays or the camera is right at it
    private static void balloon(BlockEntityMinoTable table, Vec3d seat, int fuse, long now, boolean busy) {
        double x = seat.x + (seat.x - 1.5) * 0.15, z = seat.z + (seat.z - 1.5) * 0.15;
        double bob = Math.sin((now % 100000L) / 400.0) * 0.04;
        double y = seat.y + 2.15 + bob;
        float size = 0.11f + Math.max(0, 8 - fuse) * 0.014f;
        float wobble = fuse <= 1 ? (float) Math.sin((now % 100000L) / 45.0) * 0.01f : 0;
        float v = (size + wobble) / 3.2f;
        double cx = table.xCoord + x - RenderManager.renderPosX, cy = table.yCoord + y - RenderManager.renderPosY,
            cz = table.zCoord + z - RenderManager.renderPosZ;
        boolean close = cx * cx + cy * cy + cz * cz < 2.2 * 2.2;
        float alpha = busy || close ? 0.3f : 1f;
        Tessellator tes = Tessellator.instance;
        GL11.glPushMatrix();
        if (alpha < 1f) GL11.glDepthMask(false);
        GL11.glTranslated(x, y, z);
        GL11.glRotatef((now % 36000L) / 50f, 0, 1, 0);
        tes.startDrawingQuads();
        for (int[] b : BALLOON) {
            int rgb = b[1] >= 2 && b[0] <= -1 && b[2] <= -1 ? 0xFF7070 : 0xD82828;
            cube(tes, b[0] * v, b[1] * v, b[2] * v, v, rgb, alpha, b[3]);
        }
        cube(tes, 0, -4 * v, 0, v * 0.8, 0xB01E1E, alpha, 63);
        for (int i = 0; i < 7; i++) {
            cube(tes, Math.sin(i * 0.9) * 0.012, -4.6 * v - i * 0.065, 0, 0.022, 0xEEEEEE, alpha, 63);
        }
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    // a convex polygon in the local XY plane, extruded along Z; the front (toward the viewer) is lit, sides shade by
    // which way they face
    private static void prism(Tessellator t, double[] xs, double[] ys, double depth, int rgb, float a) {
        int n = xs.length;
        double zf = -depth / 2, zb = depth / 2;
        color(t, rgb, a);
        for (int i = 1; i < n - 1; i++) {
            t.addVertex(xs[0], ys[0], zf);
            t.addVertex(xs[i], ys[i], zf);
            t.addVertex(xs[i + 1], ys[i + 1], zf);
            t.addVertex(xs[i + 1], ys[i + 1], zf);
        }
        color(t, shade(rgb, 0.45f), a);
        for (int i = 1; i < n - 1; i++) {
            t.addVertex(xs[0], ys[0], zb);
            t.addVertex(xs[i + 1], ys[i + 1], zb);
            t.addVertex(xs[i], ys[i], zb);
            t.addVertex(xs[i], ys[i], zb);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double dx = xs[j] - xs[i], dy = ys[j] - ys[i], len = Math.max(1e-6, Math.hypot(dx, dy));
            double ny = -dx / len;
            color(t, shade(rgb, (float) (0.62 + 0.22 * Math.abs(ny) * Math.signum(ny) + 0.1)), a);
            t.addVertex(xs[i], ys[i], zf);
            t.addVertex(xs[i], ys[i], zb);
            t.addVertex(xs[j], ys[j], zb);
            t.addVertex(xs[j], ys[j], zf);
        }
    }

    private static void box2(Tessellator t, double cx, double cy, double angle, double len, double w, double depth,
        int rgb, float a) {
        double c = Math.cos(angle), s = Math.sin(angle), hl = len / 2, hw = w / 2;
        double[] xs = { cx - c * hl + s * hw, cx + c * hl + s * hw, cx + c * hl - s * hw, cx - c * hl - s * hw };
        double[] ys = { cy - s * hl - c * hw, cy + s * hl - c * hw, cy + s * hl + c * hw, cy - s * hl + c * hw };
        prism(t, xs, ys, depth, rgb, a);
    }

    // stands over the pile facing the viewer, tipped back a little so its thickness shows
    private static void symbolFrame(Fx f, float t, float spin) {
        float up = Math.min(1f, t / 0.22f);
        float s = 1.05f * overshoot(up) * (t > 0.8f ? 1 - easeOutCubic((t - 0.8f) / 0.2f) : 1);
        GL11.glTranslated(f.x, f.y + 0.3 + 0.55 * easeOutCubic(up) + Math.sin(t * Math.PI * 2) * 0.03, f.z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glScalef(-1, 1, 1);
        GL11.glRotatef(RenderManager.instance.playerViewX * 0.55f, 1, 0, 0);
        GL11.glRotatef(spin, 0, 1, 0);
        GL11.glScalef(s, s, s);
    }

    // the skip sign as a thick block shape in the current color
    private static void skipSymbol(Fx f, float t) {
        int rgb = f.color & 0xFFFFFF;
        GL11.glPushMatrix();
        symbolFrame(f, t, (1 - easeOutCubic(Math.min(1f, t / 0.5f))) * 360f);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        int seg = 20;
        double r0 = 0.3, r1 = 0.42, depth = 0.14;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            prism(
                tes,
                new double[] { Math.cos(a0) * r0, Math.cos(a0) * r1, Math.cos(a1) * r1, Math.cos(a1) * r0 },
                new double[] { Math.sin(a0) * r0, Math.sin(a0) * r1, Math.sin(a1) * r1, Math.sin(a1) * r0 },
                depth,
                rgb,
                1f);
        }
        box2(tes, 0, 0, Math.PI / 4, 0.64, 0.12, depth, rgb, 1f);
        tes.draw();
        GL11.glPopMatrix();
    }

    private static void arrow(Tessellator t, double ox, double oy, int dir, double depth, int rgb, float a) {
        prism(
            t,
            new double[] { ox - 0.3 * dir, ox + 0.06 * dir, ox + 0.06 * dir, ox - 0.3 * dir },
            new double[] { oy - 0.065, oy - 0.065, oy + 0.065, oy + 0.065 },
            depth,
            rgb,
            a);
        prism(
            t,
            new double[] { ox + 0.04 * dir, ox + 0.32 * dir, ox + 0.04 * dir },
            new double[] { oy - 0.18, oy, oy + 0.18 },
            depth,
            rgb,
            a);
    }

    // two thick arrows chasing each other, turning half a circle in the new direction
    private static void reverseSymbol(Fx f, float t) {
        boolean anti = (Boolean) f.data;
        int rgb = f.color & 0xFFFFFF;
        GL11.glPushMatrix();
        symbolFrame(f, t, 0);
        GL11.glRotatef(45 + (anti ? 1 : -1) * easeOutCubic(Math.min(1f, t / 0.7f)) * 180f, 0, 0, 1);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        arrow(tes, 0.02, 0.14, 1, 0.14, rgb, 1f);
        arrow(tes, -0.02, -0.14, -1, 0.14, shade(rgb, 0.9f), 1f);
        tes.draw();
        GL11.glPopMatrix();
    }

    private static double[][] roundedRect(double w, double h, double r) {
        int seg = 4, n = seg * 4;
        double[] xs = new double[n], ys = new double[n];
        for (int q = 0; q < 4; q++) {
            double ox = q == 0 || q == 3 ? w / 2 - r : -w / 2 + r, oy = q < 2 ? h / 2 - r : -h / 2 + r;
            for (int j = 0; j < seg; j++) {
                double ang = Math.PI / 2 * (q + j / (double) (seg - 1));
                xs[q * seg + j] = ox + Math.cos(ang) * r;
                ys[q * seg + j] = oy + Math.sin(ang) * r;
            }
        }
        return new double[][] { xs, ys };
    }

    private static void cardSlab(Tessellator t, double w, double h, double depth, int rgb, float a) {
        double[][] outer = roundedRect(w, h, Math.min(w, h) * 0.07);
        prism(t, outer[0], outer[1], depth, 0xF4F4F4, a);
        double[][] inner = roundedRect(w * 0.84, h * 0.88, Math.min(w, h) * 0.05);
        prism(t, inner[0], inner[1], depth + 0.012, rgb, a);
    }

    // draw cards: thick cards of the current color fan out over the pile, then fly to whoever has to take them
    private static void drawSlabs(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        int count = (Integer) d[0];
        Vec3d victim = (Vec3d) d[1];
        int rgb = f.color & 0xFFFFFF;
        Tessellator tes = Tessellator.instance;
        for (int i = 0; i < count; i++) {
            float ti = t - i * 0.07f;
            if (ti <= 0) continue;
            float pop = overshoot(Math.min(1f, ti / 0.22f));
            float fly = victim == null ? 0 : easeOutCubic(Math.max(0f, (ti - 0.55f) / 0.35f));
            float off = (i - (count - 1) / 2f);
            double x = f.x + (victim == null ? 0 : (victim.x - f.x) * fly);
            double z = f.z + (victim == null ? 0 : (victim.z - f.z) * fly);
            double y = f.y + 0.6 + Math.sin(fly * Math.PI) * 0.5 - fly * 0.1;
            float s = pop * (1 - fly * 0.6f) * fadeOut(t, 0.92f);
            if (s <= 0.01f) continue;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
            GL11.glScalef(-1, 1, 1);
            GL11.glRotatef(RenderManager.instance.playerViewX * 0.5f, 1, 0, 0);
            GL11.glTranslated(off * 0.36 * (1 - fly), 0, off * 0.03);
            GL11.glRotatef(-off * 12 * (1 - fly) + fly * 200, 0, 0, 1);
            GL11.glScalef(s, s, s);
            tes.startDrawingQuads();
            cardSlab(tes, 0.42, 0.62, 0.07, rgb, 1f);
            tes.draw();
            GL11.glPopMatrix();
        }
    }

    // "MINO!" in thick letters over the shouting player, with a few stars
    private static void shoutText(Fx f, float t) {
        float out = fadeOut(t, 0.8f);
        bigText(
            "MINO!",
            f.x,
            f.y + t * 0.25,
            f.z,
            t,
            0.04f,
            0xFFE040,
            (float) Math.sin(t * Math.PI * 3) * 9 * (1 - t) - 8);
        float sec = t * f.duration / 1000f;
        cubes(f.x, f.y, f.z, sec, f.start, new int[] { 0xFFE040, 0xFFFFFF, 0xFFB020 }, 14, 0.05f, 1.3f, out);
    }

    // the penalty total in big letters over the head of whoever takes it, inside a beam of the current color
    private static void plusText(Fx f, float t) {
        float out = fadeOut(t, 0.75f);
        int rgb = f.color & 0xFFFFFF;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y - 1.75, f.z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glDepthMask(false);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        double w = 0.3 + 0.05 * Math.sin(t * Math.PI * 4), h = 2.3 * easeOutCubic(Math.min(1f, t / 0.2f));
        color(tes, rgb, 0.55f * out);
        tes.addVertex(-w * 0.7, 0, 0);
        tes.addVertex(w * 0.7, 0, 0);
        color(tes, rgb, 0f);
        tes.addVertex(w * 1.3, h, 0);
        tes.addVertex(-w * 1.3, h, 0);
        tes.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
        bigText((String) f.data, f.x, f.y + t * 0.15, f.z, t, 0.05f, 0xFFFFFF, 0);
        cubes(
            f.x,
            f.y - 0.3,
            f.z,
            t * f.duration / 1000f,
            f.start,
            new int[] { rgb, shade(rgb, 1.3f), 0xFFFFFF },
            12,
            0.05f,
            1.1f,
            out);
    }

    // thick letters facing the viewer: the face color over a dark extrusion
    private static void bigText(String text, double x, double y, double z, float t, float size, int face, float tilt) {
        net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        float out = fadeOut(t, 0.8f);
        float s = size * overshoot(Math.min(1f, t / 0.25f)) * (0.7f + 0.3f * out);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        billboard();
        GL11.glRotatef(tilt, 0, 0, 1);
        GL11.glScalef(s, -s, s);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int w = font.getStringWidth(text);
        int alpha = Math.max(8, (int) (out * 255)) << 24;
        for (int layer = 6; layer >= 0; layer--) {
            GL11.glPushMatrix();
            GL11.glTranslatef(layer * 0.25f, layer * 0.25f, layer * 0.5f);
            int rgb = layer == 0 ? face : layer == 1 ? 0xFFFFFF : 0x3A1A00;
            if (layer == 0) {
                for (int[] o : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(o[0] * 0.6f, o[1] * 0.6f, 0.2f);
                    font.drawString(text, -w / 2, -4, alpha | 0x1A0A00);
                    GL11.glPopMatrix();
                }
            }
            font.drawString(text, -w / 2, -4, alpha | rgb);
            GL11.glPopMatrix();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    // a card face built as a strip of columns so it can curl like real card stock
    private static void bentFace(CardFace face, String theme, float w, float h, float bend) {
        int cols = 12;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (ResourceLocation tex : CardBatch.textures(face, theme)) {
            if (tex == null) continue;
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(tex);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.setColorRGBA_F(1, 1, 1, 1);
            for (int i = 0; i < cols; i++) {
                double u0 = i / (double) cols, u1 = (i + 1) / (double) cols;
                double x0 = -w / 2 + w * u0, x1 = -w / 2 + w * u1;
                double k0 = 2 * u0 - 1, k1 = 2 * u1 - 1;
                double z0 = bend * w * (k0 * k0 - 1), z1 = bend * w * (k1 * k1 - 1);
                t.addVertexWithUV(x0, -h / 2, z0, u0, 0);
                t.addVertexWithUV(x1, -h / 2, z1, u1, 0);
                t.addVertexWithUV(x1, h / 2, z1, u1, 1);
                t.addVertexWithUV(x0, h / 2, z0, u0, 1);
            }
            t.draw();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
