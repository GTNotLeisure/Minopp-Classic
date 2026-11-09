package com.hfstudio.minopp.render;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
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
        PLUS,
        CUSTOM,
        TOSS,
        ROULETTE,
        SYMBOL,
        DICE
    }

    private record Fx(Kind kind, long start, int duration, double x, double y, double z, int color, Object data) {

        float t(long now) {
            return Math.min(1f, (now - start) / (float) duration);
        }
    }

    private static final class State {

        int topUid = Integer.MIN_VALUE;
        CardColor color;
        boolean dark, anti;
        UUID balloon;
        int balloonSeat = -1;
        float ringAngle, ringSpeed = 1f;
        long lastFrame = System.currentTimeMillis();
        final List<Fx> fx = new ArrayList<>();
        final Set<UUID> shouted = new HashSet<>();
        int shoutSeq = -1;
        final Set<UUID> coins = new HashSet<>();
        int tossSeq = -1;
        int diceSeq = -1, luckySeq = -1;
    }

    private static final ResourceLocation TEX_DIRECTION = Mino.id("textures/fx/direction.png");
    private static final ResourceLocation TEX_SKIP = Mino.id("textures/fx/skip.png");
    private static final ResourceLocation TEX_COIN = Mino.id("textures/fx/coin.png");
    private static final ResourceLocation TEX_HEADS = Mino.id("textures/fx/coin_heads.png");
    private static final ResourceLocation TEX_TAILS = Mino.id("textures/fx/coin_tails.png");
    static final double PILE_Y = 0.95;

    private static final Map<BlockEntityMinoTable, State> STATES = new WeakHashMap<>();
    private static final Random RANDOM = new Random();
    private static final int[][] BALLOON = balloonVoxels();

    private TableFx() {}

    private static final Map<UUID, WeakReference<Entity>> HOLDERS = new HashMap<>();
    private static long holderScan;

    // the player or bot behind a card player, looked up from the client world at most every couple of seconds
    private static Entity holderEntity(UUID uuid) {
        WeakReference<Entity> ref = HOLDERS.get(uuid);
        Entity e = ref == null ? null : ref.get();
        if (e != null && !e.isDead) return e;
        long now = System.currentTimeMillis();
        if (now - holderScan < 2000) return null;
        holderScan = now;
        World w = Minecraft.getMinecraft().theWorld;
        if (w == null) return null;
        for (EntityPlayer o : w.playerEntities) {
            EntityPlayer p = o;
            if (uuid.equals(
                p.getGameProfile()
                    .getId()))
                e = p;
        }
        if (e == null) for (Object o : w.loadedEntityList) {
            if (o instanceof EntityAutoPlayer bot && uuid.equals(bot.getUniqueID())) e = bot;
        }
        if (e != null) HOLDERS.put(uuid, new WeakReference<>(e));
        return e;
    }

    public static void render(BlockEntityMinoTable table, CardGame game, float partialTick) {
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
                Vec3d seatPos = table.getSeatLocalPos(seat);
                double bx = seatPos.x + (seatPos.x - 1.5) * 0.15, bz = seatPos.z + (seatPos.z - 1.5) * 0.15,
                    by = seatPos.y + 2.15;
                Entity holder = holderEntity(game.balloonHolder);
                if (holder != null && holder.getDistanceSq(table.xCoord + 1.5, table.yCoord, table.zCoord + 1.5) < 64) {
                    bx = holder.prevPosX + (holder.posX - holder.prevPosX) * partialTick - table.xCoord;
                    bz = holder.prevPosZ + (holder.posZ - holder.prevPosZ) * partialTick - table.zCoord;
                    by = holder.prevPosY + (holder.posY - holder.prevPosY) * partialTick
                        - holder.yOffset
                        + holder.height
                        + 0.55
                        - table.yCoord;
                }
                balloon(table, new Vec3d(bx, by, bz), game.balloonFuse, now, busy);
            }
        }

        s.fx.removeIf(f -> f.t(now) >= 1f);
        if (!ClientConfig.display.tableEffects) s.fx.clear();
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
                case TOSS -> toss(f, t);
                case ROULETTE -> roulette(f, now);
                case SYMBOL -> symbolPop(f, t);
                case DICE -> dice(f, now);
                case CUSTOM -> {
                    Object[] d = (Object[]) f.data;
                    GL11.glPushMatrix();
                    GL11.glTranslated(f.x, f.y, f.z);
                    ((CardFxRegistry.Renderer) d[0]).render(t, (CardFxRegistry.Context) d[1]);
                    GL11.glPopMatrix();
                }
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
        if (!first && game.tossSeq != s.tossSeq && game.tossHeads != null && game.tossTails != null) {
            int hs = seatOf(game, game.tossHeads), ts = seatOf(game, game.tossTails);
            if (hs >= 0 && ts >= 0) {
                Vec3d h = head(table, hs, 1.7), tl = head(table, ts, 1.7);
                s.fx.add(new Fx(Kind.TOSS, now, 3200, 1.5, PILE_Y, 1.5, 0, new Object[] { h, tl, game.tossHeadsUp }));
                Vec3d loser = game.tossHeadsUp ? tl : h;
                s.fx.add(new Fx(Kind.PLUS, now + 2300, 1300, loser.x, loser.y + 0.45, loser.z, 0xFFD040, "+4"));
            }
        }
        s.tossSeq = game.tossSeq;
        if (!first && game.diceSeq != s.diceSeq && game.diceSides > 0 && game.diceRoller != null) {
            int seat = seatOf(game, game.diceRoller);
            if (seat >= 0) s.fx.add(throwDie(table, seat, game.diceSides, game.diceResult, now));
        }
        s.diceSeq = game.diceSeq;
        if (!first && game.luckySeq != s.luckySeq && game.luckyPlayer != null) {
            int seat = seatOf(game, game.luckyPlayer);
            if (seat >= 0) {
                Vec3d at = head(table, seat, 1.6);
                boolean win = game.luckyResult != CardGame.LUCKY_TAILS;
                if (game.luckyResult != CardGame.LUCKY_SEVENS)
                    s.fx.add(new Fx(Kind.COIN, now, 1600, at.x, at.y, at.z, 0, null));
                s.fx.add(
                    new Fx(
                        Kind.PLUS,
                        now + (game.luckyResult == CardGame.LUCKY_SEVENS ? 200 : 1300),
                        1500,
                        at.x,
                        at.y + 0.45,
                        at.z,
                        win ? 0x60F070 : 0xFF4840,
                        win ? "-7" : "+7"));
            }
        }
        s.luckySeq = game.luckySeq;
        for (CardPlayer p : game.players) {
            if (p.coinUsed && !s.coins.contains(p.uuid) && !first) {
                Vec3d at = head(table, p.seat, 1.6);
                s.fx.add(new Fx(Kind.COIN, now, 1600, at.x, at.y, at.z, 0, null));
                s.fx.add(
                    new Fx(Kind.POP, now + 700, 900, at.x, at.y + 0.6, at.z, p.coinNoMercy ? 0xE02020 : 0xFFD040, now));
            }
        }
        s.coins.clear();
        for (CardPlayer p : game.players) if (p.coinUsed) s.coins.add(p.uuid);
        if (!first && game.darkSide != s.dark && game.topCard != null) {
            CardFace[] faces = { s.dark ? game.topCard.dark : game.topCard.light,
                game.darkSide ? game.topCard.dark : game.topCard.light };
            s.fx.add(new Fx(Kind.FLIP, now, 1400, 1.5, PILE_Y, 1.5, 0, new Object[] { faces, theme, game.darkSide }));
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
            Sink sink = new Sink(table, game, s, now);
            CardFxRegistry.Context ctx = new CardFxRegistry.Context(table, game, face, now, sink);
            sink.ctx = ctx;
            CardFxRegistry.dispatch(ctx);
            if (!ctx.defaultsCancelled) {
                String id = face.type().id;
                int rgb = ctx.color();
                boolean reverseCard = face.type().matchKey.equals("reverse") || id.contains("reverse");
                if (reverseCard || turned) {
                    sink.reverse(rgb);
                    turned = false;
                } else if (isSkip(id) || face.type().matchKey.equals("skip")) {
                    sink.skipSymbol(rgb);
                }
                if (face.type()
                    .isDraw()) {
                    int amount = game.pendingDraw > 0 ? game.pendingDraw : face.type().drawValue;
                    int seat = ctx.victimSeat();
                    Vec3d at = seat >= 0 ? head(table, seat, 1.75) : new Vec3d(1.5, PILE_Y + 1.75, 1.5);
                    sink.text("+" + amount, rgb, at, face.isWild() ? 1000 : 250, 1400);
                    if (!face.isWild()) sink.slabs(face.type().drawValue, rgb);
                }
                if (isSkip(id) || id.equals("reverse") && game.activePlayers()
                    .size() == 2) sink.skipSigns();
                if (face.isWild() && !face.type().keepsColor && game.currentColor != CardColor.WILD) {
                    sink.pillar(
                        face.type()
                            .isDraw());
                }
            }
        }
        // a wild whose color is settled later (a roulette, a decision) gets its color pillar when that happens
        if (!first && top == s.topUid
            && game.topCard != null
            && s.color == CardColor.WILD
            && game.currentColor != CardColor.WILD
            && game.topFace()
                .isWild()
            && !game.topFace()
                .type().id.equals("wild_color_roulette")) {
            Sink sink = new Sink(table, game, s, now);
            sink.ctx = new CardFxRegistry.Context(table, game, game.topFace(), now, sink);
            sink.pillar(
                game.topFace()
                    .type()
                    .isDraw());
        }
        s.color = game.currentColor;
        if (turned) s.fx.add(new Fx(Kind.REVERSE, now, 1300, 1.5, PILE_Y, 1.5, 0xFFD43A, game.isAntiClockwise));
        s.topUid = top;
        s.dark = game.darkSide;
        s.anti = game.isAntiClockwise;
        s.balloon = game.balloonHolder;
    }

    static Vec3d head(BlockEntityMinoTable table, int seat, double lift) {
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

    static {
        registerBuiltins();
    }

    private static void particlesFor(String particle, int count, double spread, String... ids) {
        for (String id : ids) CardFxRegistry.register(id, ctx -> ctx.particles(particle, count, spread));
    }

    private static void registerBuiltins() {
        particlesFor("largesmoke", 24, 0.6, "wild_smoke");
        particlesFor(
            "portal",
            40,
            1.2,
            "wild_cyclone",
            "wild_hand_shuffle",
            "wild_crossfade",
            "wild_swap_hands",
            "trade_hands");
        particlesFor("fireworksSpark", 20, 0.6, "wild_card_storm", "wild_surprise", "wild_trickster");
        particlesFor("lava", 12, 0.6, "wild_booby_trap", "blaster", "wild_blast_all");
        particlesFor("happyVillager", 20, 1.2, "wild_shield");
        particlesFor("witchMagic", 24, 0.6, "wild_surge", "wild_echo", "wild_thief");
        particlesFor("note", 10, 0.6, "wild_dance_floor");
        particlesFor("heart", 8, 0.6, "gift", "bonus_turn");
        particlesFor(
            "angryVillager",
            8,
            0.6,
            "wild_draw6",
            "wild_draw10",
            "wild_plus6",
            "wild_reverse_plus6",
            "wild_color_roulette");
        particlesFor("crit", 24, 0.6, "discard_all", "double_discard_all", "wild_even_out", "wild_pile_up");
        particlesFor("cloud", 10, 0.6, "wild_time_balloon");
        particlesFor("crit", 30, 0.8, "wild_discard_all");
        particlesFor("lava", 16, 0.8, "wild_final_attack");
        particlesFor("largesmoke", 30, 1.2, "wild_sudden_death");
        particlesFor("happyVillager", 14, 0.6, "play_again_10");
        particlesFor("flame", 12, 0.6, "wild_hurry_up");
        CardFxRegistry.register("wild_color_roulette", ctx -> ctx.sink.roulette());
        for (String sign : new String[] { "number_sign", "wild_number_sign" }) {
            CardFxRegistry.register(
                sign,
                ctx -> ctx.bigText(
                    "#" + Math.max(1, ctx.game().signNumber),
                    ctx.face.color.isWild() || ctx.face.color == CardColor.COLORLESS ? 0xD0D0D8
                        : ctx.face.color.rgb & 0xFFFFFF,
                    ctx.pile()
                        .add(0, 1.6, 0),
                    250,
                    1400));
        }
        CardFxRegistry.register("joker", ctx -> ctx.particles("fireworksSpark", 36, 1.1));
        CardFxRegistry.register(
            "wild_times_two",
            ctx -> ctx.bigText(
                "x2",
                0xFF5040,
                ctx.pile()
                    .add(0, 1.7, 0),
                300,
                1300));
        CardFxRegistry.register(
            face -> !face.type().numbered && !OWN_FX.contains(face.type().id)
                && !isSkip(face.type().id)
                && !face.type().matchKey.equals("skip")
                && !face.type().matchKey.equals("reverse")
                && !face.type()
                    .isDraw(),
            ctx -> ctx.symbolPop(
                ctx.pile()
                    .add(0, 1.3, 0),
                0));
        CardFxRegistry.register("reveal_hands", ctx -> {
            for (CardPlayer p : ctx.game().players) {
                if (ctx.game().topCard != null && p.uuid.equals(ctx.game().topCard.owner)) continue;
                ctx.symbolPop(ctx.head(p.seat, 1.85), 250);
            }
        });
        for (String speed : new String[] { "speed_up", "wild_double_speed" }) {
            CardFxRegistry.register(speed, ctx -> {
                ctx.bigText(
                    "\u00bb\u00bb",
                    0x60E0FF,
                    ctx.pile()
                        .add(0, 1.8, 0),
                    300,
                    1200);
                ctx.particles("fireworksSpark", 24, 1.0);
            });
        }
        CardFxRegistry.register(
            "wild_sudden_death",
            ctx -> ctx.bigText(
                "24",
                0xD02020,
                ctx.pile()
                    .add(0, 1.75, 0),
                900,
                1500));
        CardFxRegistry.register(
            "wild_final_attack",
            ctx -> ctx.bigText(
                "!!!",
                0xFF8A1E,
                ctx.pile()
                    .add(0, 1.75, 0),
                900,
                1400));
    }

    /** Starts the built-in animations on behalf of {@link CardFxRegistry.Context}. */
    static final class Sink {

        private final BlockEntityMinoTable table;
        private final CardGame game;
        private final State s;
        private final long now;
        CardFxRegistry.Context ctx;

        Sink(BlockEntityMinoTable table, CardGame game, State s, long now) {
            this.table = table;
            this.game = game;
            this.s = s;
            this.now = now;
        }

        void skipSymbol(int rgb) {
            s.fx.add(new Fx(Kind.SKIP_SIGN, now, 1300, 1.5, PILE_Y, 1.5, rgb, null));
        }

        void skipSigns() {
            for (int seat : skippedSeats(game)) {
                Vec3d at = head(table, seat, 1.5);
                s.fx.add(new Fx(Kind.SKIP, now, 1600, at.x, at.y, at.z, 0, null));
            }
        }

        void reverse(int rgb) {
            s.fx.add(new Fx(Kind.REVERSE, now, 1300, 1.5, PILE_Y, 1.5, rgb, game.isAntiClockwise));
        }

        void pillar(boolean blocks) {
            double[] dir = BlockEntityMinoTableRenderer
                .seatDir(table, game, game.topCard == null ? null : game.topCard.owner);
            if (dir == null) dir = new double[] { 0, 1 };
            s.fx.add(
                new Fx(
                    Kind.WILD,
                    now,
                    1300,
                    1.5,
                    PILE_Y,
                    1.5,
                    game.currentColor.rgb,
                    new Object[] { game.currentColor, blocks, dir[0], dir[1] }));
        }

        void slabs(int count, int rgb) {
            int seat = ctx.victimSeat();
            Vec3d victim = null;
            if (seat >= 0) {
                Vec3d p = table.getSeatLocalPos(seat);
                victim = new Vec3d(p.x, p.y + 0.4, p.z);
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
                    new Object[] { Math.max(1, Math.min(5, count)), victim }));
        }

        void text(String text, int rgb, Vec3d at, long delay, int duration) {
            s.fx.add(new Fx(Kind.PLUS, now + delay, duration, at.x, at.y, at.z, rgb, text));
        }

        void symbol(CardFace face, Vec3d at, long delay) {
            s.fx.add(
                new Fx(
                    Kind.SYMBOL,
                    now + delay,
                    1300,
                    at.x,
                    at.y,
                    at.z,
                    0,
                    new Object[] { face, ThemeManager.INSTANCE.selected() }));
        }

        void roulette() {
            s.fx.add(
                new Fx(Kind.ROULETTE, now, 9000, 1.5, PILE_Y + 0.95, 1.5, 0, new Object[] { table, new long[] { 0 } }));
        }

        void coin(Vec3d at) {
            s.fx.add(new Fx(Kind.COIN, now, 1600, at.x, at.y, at.z, 0, null));
        }

        void burst(Vec3d at, int rgb, long delay) {
            s.fx.add(new Fx(Kind.POP, now + delay, 900, at.x, at.y, at.z, rgb, now));
        }

        void particles(String name, int count, double spread) {
            if (!ClientConfig.display.tableEffects) return;
            TableFx.particles(table, name, 1.5, 1.05, 1.5, count, spread);
        }

        void custom(Vec3d at, long delay, int duration, CardFxRegistry.Renderer renderer) {
            s.fx.add(new Fx(Kind.CUSTOM, now + delay, duration, at.x, at.y, at.z, 0, new Object[] { renderer, ctx }));
        }
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

    private static final Set<String> OWN_FX = Set.of(
        "wild",
        "wild_draw4",
        "flip",
        "wild_fifty_fifty",
        "wild_color_roulette",
        "wild_time_balloon",
        "wild_sudden_death",
        "wild_final_attack",
        "wild_d4",
        "wild_d6",
        "wild_d8",
        "wild_d12",
        "wild_d20",
        "wild_lucky_seven");

    private static final int[] DIE_COLORS = { 0x3CAA50, 0xD23228, 0xE6BE1E, 0x286ED2, 0x8C46C8 };
    private static final double DIE_SIZE = 0.17;

    private static int dieColor(int sides) {
        return DIE_COLORS[switch (sides) {
            case 4 -> 0;
            case 6 -> 1;
            case 8 -> 2;
            case 12 -> 3;
            default -> 4;
        }];
    }

    // thrown from in front of the roller onto the table beside the discard pile
    private static Fx throwDie(BlockEntityMinoTable table, int seat, int sides, int result, long now) {
        Vec3d h = head(table, seat, 1.35);
        double dx = h.x - 1.5, dz = h.z - 1.5, len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        dx /= len;
        dz /= len;
        double side = (RANDOM.nextDouble() - 0.5) * 0.5;
        double[] start = { h.x - dx * 0.35, h.y, h.z - dz * 0.35 };
        double[] target = { 1.5 + dx * 0.45 - dz * side, PILE_Y, 1.5 + dz * 0.45 + dx * side };
        Dice.Roll roll = Dice.simulate(sides, result, RANDOM.nextLong(), start, target, PILE_Y + 0.01, DIE_SIZE);
        int duration = (int) (roll.frames() * 1000L / Dice.FPS) + 2200;
        double[] world = { table.xCoord, table.yCoord, table.zCoord };
        return new Fx(
            Kind.DICE,
            now,
            duration,
            0,
            0,
            0,
            dieColor(sides),
            new Object[] { roll, world, new int[] { 0 } });
    }

    private static void dice(Fx f, long now) {
        Object[] d = (Object[]) f.data;
        Dice.Roll roll = (Dice.Roll) d[0];
        double[] world = (double[]) d[1];
        int[] heard = (int[]) d[2];
        double sec = (now - f.start) / 1000.0;
        float t = f.t(now);
        double frame = sec * Dice.FPS;
        while (heard[0] < roll.bounces.size() && roll.bounces.get(heard[0]) <= frame) {
            double[] p = roll.pos.get(roll.bounces.get(heard[0]));
            float loud = 0.55f / (1 + heard[0]);
            Minecraft.getMinecraft().theWorld.playSound(
                world[0] + p[0],
                world[1] + p[1],
                world[2] + p[2],
                "step.wood",
                loud,
                1.5f + 0.1f * heard[0],
                false);
            heard[0]++;
        }
        float alpha = fadeOut(t, 0.88f);
        double[] at = Dice.draw(roll, sec, DIE_SIZE, f.color, alpha);
        double settled = roll.settleEnd / (double) Dice.FPS;
        if (sec > settled) {
            float lt = (float) Math.min(1, (sec - settled) / (f.duration / 1000.0 - settled));
            bigText(String.valueOf(roll.result), at[0], at[1] + 0.32, at[2], lt, 0.035f, f.color, 0);
        }
    }

    private static void symbolPop(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        CardFace face = (CardFace) d[0];
        String theme = (String) d[1];
        ResourceLocation tex = ThemeManager.INSTANCE.texture(theme, "symbol_" + face.type().symbol);
        float scale = 0.5f * overshoot(Math.min(1f, t / 0.25f));
        float alpha = fadeOut(t, 0.72f);
        int rgb = face.color.isWild() ? 0x2A2630 : face.color.rgb & 0xFFFFFF;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + t * 0.25, f.z);
        billboard();
        GL11.glRotatef((float) Math.sin(t * Math.PI * 4) * 10 * (1 - t), 0, 0, 1);
        GL11.glScalef(scale, scale, scale);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            prism(
                tes,
                new double[] { 0, Math.cos(a0) * 0.62, Math.cos(a1) * 0.62 },
                new double[] { 0, Math.sin(a0) * 0.62, Math.sin(a1) * 0.62 },
                0.08,
                rgb,
                alpha);
        }
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            prism(
                tes,
                new double[] { Math.cos(a0) * 0.62, Math.cos(a0) * 0.7, Math.cos(a1) * 0.7, Math.cos(a1) * 0.62 },
                new double[] { Math.sin(a0) * 0.62, Math.sin(a0) * 0.7, Math.sin(a1) * 0.7, Math.sin(a1) * 0.62 },
                0.09,
                0xFFFFFF,
                alpha);
        }
        tes.draw();
        if (tex != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, -0.06f);
            GL11.glScalef(1.15f, 1.8f, 1);
            sprite(tex, alpha, false);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    private static void toss(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        Vec3d headsAt = (Vec3d) d[0], tailsAt = (Vec3d) d[1];
        boolean headsUp = (Boolean) d[2];
        float pop = Math.min(1f, t / 0.12f);
        float out = fadeOut(t, 0.9f);
        smallCoin(headsAt, TEX_HEADS, overshoot(pop) * 0.32f, out, t > 0.72f ? (headsUp ? 1 : -1) : 0, t);
        smallCoin(tailsAt, TEX_TAILS, overshoot(pop) * 0.32f, out, t > 0.72f ? (headsUp ? -1 : 1) : 0, t);
        float fly = Math.max(0f, Math.min(1f, (t - 0.15f) / 0.5f));
        double height = 0.55 + Math.sin(fly * Math.PI) * 0.9;
        float spins = 5;
        float angle = easeOutCubic(fly) * spins * 360 + (headsUp ? 0 : 180);
        float scale = 0.55f * Math.min(1f, t / 0.15f) * out;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + height, f.z);
        billboard();
        GL11.glRotatef(angle, 1, 0, 0);
        GL11.glScalef(scale, scale, scale);
        boolean front = Math.cos(Math.toRadians(angle)) >= 0;
        for (int i = 0; i < 3; i++) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, (i - 1) * 0.04f);
            if (!front) GL11.glScalef(1, -1, 1);
            sprite(front ? TEX_HEADS : TEX_TAILS, 1f, false);
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
    }

    // verdict: 1 ticked, -1 crossed, 0 none yet
    private static void smallCoin(Vec3d at, ResourceLocation tex, float scale, float alpha, int verdict, float t) {
        if (scale <= 0.01f) return;
        GL11.glPushMatrix();
        GL11.glTranslated(at.x, at.y + Math.sin(t * Math.PI * 4) * 0.03, at.z);
        billboard();
        GL11.glScalef(scale, scale, scale);
        sprite(tex, alpha, false);
        if (verdict != 0) {
            float k = Math.min(1f, (t - 0.72f) / 0.08f);
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            if (verdict < 0) {
                box2(tes, 0, 0, Math.PI / 4, 0.95 * k, 0.16, 0.02, 0xE02020, alpha);
                box2(tes, 0, 0, -Math.PI / 4, 0.95 * k, 0.16, 0.02, 0xE02020, alpha);
            } else {
                box2(tes, -0.18, -0.12, -Math.PI / 4, 0.36 * k, 0.16, 0.02, 0x30D040, alpha);
                box2(tes, 0.12, 0.06, Math.PI / 3.2, 0.72 * k, 0.16, 0.02, 0x30D040, alpha);
            }
            tes.draw();
        }
        GL11.glPopMatrix();
    }

    private static void roulette(Fx f, long now) {
        Object[] d = (Object[]) f.data;
        BlockEntityMinoTable table = (BlockEntityMinoTable) d[0];
        long[] stopAt = (long[]) d[1];
        CardGame live = table.game;
        if (live == null) return;
        boolean dark = live.darkSide;
        List<CardColor> colors = CardColor.forSide(dark);
        CardColor result = live.currentColor;
        if (stopAt[0] == 0 && result != CardColor.WILD && colors.contains(result)) stopAt[0] = now;
        double spinRate = 0.9;
        double angle;
        float after = 0;
        if (stopAt[0] == 0) {
            angle = (now - f.start) * spinRate;
        } else {
            double atStop = (stopAt[0] - f.start) * spinRate;
            double center = colors.indexOf(result) * 90 + 45;
            double target = atStop + 540 + ((90 - center - atStop) % 360 + 720) % 360;
            float k = Math.min(1f, (now - stopAt[0]) / 1300f);
            angle = atStop + (target - atStop) * easeOutCubic(k);
            after = Math.max(0f, (now - stopAt[0] - 1300) / 900f);
            if (after >= 1f) return;
        }
        float appear = overshoot(Math.min(1f, (now - f.start) / 300f));
        float scale = 0.5f * appear * (1 - after);
        if (scale > 0.01f) {
            GL11.glPushMatrix();
            GL11.glTranslated(f.x, f.y + Math.sin((now - f.start) / 300.0) * 0.03, f.z);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
            GL11.glScalef(-1, 1, 1);
            GL11.glRotatef(RenderManager.instance.playerViewX * 0.4f, 1, 0, 0);
            GL11.glScalef(scale, scale, scale);
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            prism(tes, new double[] { -0.12, 0.12, 0 }, new double[] { 1.2, 1.2, 0.98 }, 0.12, 0xFFFFFF, 1f);
            tes.draw();
            GL11.glRotatef((float) angle, 0, 0, 1);
            tes.startDrawingQuads();
            for (int q = 0; q < Math.min(4, colors.size()); q++) {
                int rgb = colors.get(q).rgb & 0xFFFFFF;
                int seg = 6;
                for (int i = 0; i < seg; i++) {
                    double a0 = Math.toRadians(q * 90 + 2 + 86.0 * i / seg),
                        a1 = Math.toRadians(q * 90 + 2 + 86.0 * (i + 1) / seg);
                    prism(
                        tes,
                        new double[] { 0, Math.cos(a0), Math.cos(a1) },
                        new double[] { 0, Math.sin(a0), Math.sin(a1) },
                        0.1,
                        rgb,
                        1f);
                }
            }
            tes.draw();
            GL11.glPopMatrix();
        }
        if (stopAt[0] != 0 && now - stopAt[0] > 1300) {
            int rgb = result.rgb & 0xFFFFFF;
            cubes(
                f.x,
                f.y,
                f.z,
                (now - stopAt[0] - 1300) / 1000f,
                f.start,
                new int[] { rgb, shade(rgb, 1.3f), 0xFFFFFF },
                24,
                0.08f,
                1.6f,
                1 - after);
        }
    }

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

    private static void flipCard(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        CardFace[] faces = (CardFace[]) d[0];
        String theme = (String) d[1];
        boolean toDark = (Boolean) d[2];
        float rise = t < 0.2f ? easeOutCubic(t / 0.2f) : t > 0.82f ? 1 - easeOutCubic((t - 0.82f) / 0.18f) : 1;
        float turn = Math.max(0f, Math.min(1f, (t - 0.22f) / 0.5f));
        float deg = (float) (0.5 - 0.5 * Math.cos(turn * Math.PI)) * 180f;
        float s = 0.1f * (0.6f + 0.4f * rise);
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, f.y + 0.05 + rise * 0.75, f.z);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glScalef(-1, 1, 1);
        GL11.glRotatef(RenderManager.instance.playerViewX * 0.3f * rise, 1, 0, 0);
        GL11.glRotatef(deg, 0, 1, 0);
        GL11.glScalef(s, -s, s);
        float bend = (float) Math.sin(turn * Math.PI) * 0.12f;
        boolean back = deg > 90;
        CardFace shown = back ? faces[1] : faces[0];
        if (shown != null) bentFace(shown, theme, 5.6f, 8.9f, bend, back);
        else bentLayers(
            new ResourceLocation[] { ThemeManager.INSTANCE.texture(theme, back == toDark ? "back_dark" : "back") },
            null,
            5.6f,
            8.9f,
            bend,
            back);
        GL11.glPopMatrix();
        if (t > 0.5f) {
            float sec = (t - 0.5f) * f.duration / 1000f;
            int c = faces[1] == null || faces[1].color.isWild() ? (toDark ? 0x8A3CFF : 0xFFD040)
                : faces[1].color.rgb & 0xFFFFFF;
            cubes(
                f.x,
                f.y + 0.8,
                f.z,
                sec,
                f.start,
                new int[] { c, 0xFFFFFF, shade(c, 0.7f) },
                20,
                0.06f,
                1.4f,
                fadeOut(t, 0.8f));
        }
    }

    private static void wildPillar(Fx f, float t) {
        Object[] d = (Object[]) f.data;
        CardColor chosen = (CardColor) d[0];
        boolean square = (Boolean) d[1];
        double fx = (Double) d[2], fz = (Double) d[3], len = Math.max(1e-6, Math.hypot(fx, fz));
        fx /= len;
        fz /= len;
        double[][] off = { { fx, fz }, { fz, -fx }, { -fx, -fz }, { -fz, fx } };
        List<CardColor> colors = CardColor.forSide(chosen.side == CardColor.Side.DARK);
        int pick = Math.max(0, colors.indexOf(chosen));
        float lift = easeOutCubic(Math.min(1f, t / 0.22f));
        float pull = Math.max(0f, Math.min(1f, (t - 0.3f) / 0.3f));
        pull *= pull;
        GL11.glPushMatrix();
        GL11.glTranslated(f.x, PILE_Y - 0.005 - 0.3 * pull, f.z);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        for (int q = 0; q < Math.min(4, colors.size()); q++) {
            boolean picked = q == pick;
            double h = (picked ? 0.55 + 0.25 * lift : 0.24) * (1 - 0.85 * pull);
            double y0 = picked ? 0.12 * lift : 0;
            if (h <= 0.005) continue;
            int rgb = colors.get(q).rgb & 0xFFFFFF;
            double mid = Math.atan2(off[q][1], off[q][0]);
            double grow = picked ? 1 + 0.22 * lift : 1;
            if (square) {
                double cx = off[q][0] * 0.44 * (1 - pull), cz = off[q][1] * 0.44 * (1 - pull), k = 1 - 0.6 * pull;
                frustum(tes, cx, cz, 0.15 * k * grow, 0.18 * k * grow * (picked ? 1.15 : 1), y0, y0 + h, rgb);
                if (pull <= 0f) topArrow(tes, cx, cz, mid, -0.07, 0.09, 0.07, y0 + h + 0.004);
            } else {
                double k = 1 - 0.9 * pull, ox = Math.cos(mid) * 0.05 * k, oz = Math.sin(mid) * 0.05 * k;
                ringPiece(
                    tes,
                    ox,
                    oz,
                    mid - Math.toRadians(45),
                    mid + Math.toRadians(45),
                    0,
                    0.56 * k * grow * (picked ? 1.12 : 1.04),
                    0.5 * k * grow,
                    y0,
                    y0 + h,
                    rgb);
                if (pull <= 0f) topArrow(tes, ox, oz, mid, 0.18, 0.36, 0.085, y0 + h + 0.004);
            }
        }
        tes.draw();
        GL11.glPopMatrix();
        if (t >= 0.55f) {
            float sec = (t - 0.55f) * f.duration / 1000f;
            int rgb = chosen.rgb & 0xFFFFFF;
            float alpha = fadeOut(t, 0.85f);
            cubes(
                f.x,
                PILE_Y + 0.2,
                f.z,
                sec,
                f.start,
                new int[] { rgb, shade(rgb, 1.3f), shade(rgb, 0.75f) },
                30,
                0.09f,
                1.9f,
                alpha);
            float grow = Math.min(1f, (t - 0.55f) / 0.1f);
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

    private static void frustum(Tessellator t, double cx, double cz, double hwBottom, double hwTop, double y0,
        double y1, int rgb) {
        color(t, shade(rgb, 1.1f), 1f);
        t.addVertex(cx - hwTop, y1, cz - hwTop);
        t.addVertex(cx - hwTop, y1, cz + hwTop);
        t.addVertex(cx + hwTop, y1, cz + hwTop);
        t.addVertex(cx + hwTop, y1, cz - hwTop);
        double[][] c = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        float[] k = { 0.85f, 0.7f, 0.85f, 0.7f };
        for (int i = 0; i < 4; i++) {
            double[] p = c[i], q = c[(i + 1) % 4];
            color(t, shade(rgb, k[i]), 1f);
            t.addVertex(cx + p[0] * hwTop, y1, cz + p[1] * hwTop);
            t.addVertex(cx + q[0] * hwTop, y1, cz + q[1] * hwTop);
            color(t, shade(rgb, k[i] * 0.75f), 1f);
            t.addVertex(cx + q[0] * hwBottom, y0, cz + q[1] * hwBottom);
            t.addVertex(cx + p[0] * hwBottom, y0, cz + p[1] * hwBottom);
        }
    }

    private static void ringPiece(Tessellator t, double ox, double oz, double a0, double a1, double r0, double r1Top,
        double r1Bottom, double y0, double y1, int rgb) {
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            double u0 = a0 + (a1 - a0) * i / steps, u1 = a0 + (a1 - a0) * (i + 1) / steps;
            double c0 = Math.cos(u0), s0 = Math.sin(u0), c1 = Math.cos(u1), s1 = Math.sin(u1);
            color(t, shade(rgb, 1.1f), 1f);
            t.addVertex(ox + c0 * r0, y1, oz + s0 * r0);
            t.addVertex(ox + c0 * r1Top, y1, oz + s0 * r1Top);
            t.addVertex(ox + c1 * r1Top, y1, oz + s1 * r1Top);
            t.addVertex(ox + c1 * r0, y1, oz + s1 * r0);
            color(t, shade(rgb, 0.85f), 1f);
            t.addVertex(ox + c0 * r1Top, y1, oz + s0 * r1Top);
            t.addVertex(ox + c1 * r1Top, y1, oz + s1 * r1Top);
            color(t, shade(rgb, 0.6f), 1f);
            t.addVertex(ox + c1 * r1Bottom, y0, oz + s1 * r1Bottom);
            t.addVertex(ox + c0 * r1Bottom, y0, oz + s0 * r1Bottom);
        }
        color(t, shade(rgb, 0.7f), 1f);
        for (double a : new double[] { a0, a1 }) {
            double c = Math.cos(a), s = Math.sin(a);
            t.addVertex(ox + c * r0, y1, oz + s * r0);
            t.addVertex(ox + c * r1Top, y1, oz + s * r1Top);
            t.addVertex(ox + c * r1Bottom, y0, oz + s * r1Bottom);
            t.addVertex(ox + c * r0, y0, oz + s * r0);
        }
    }

    private static void topArrow(Tessellator t, double cx, double cz, double angle, double from, double to,
        double halfBase, double y) {
        double nx = Math.cos(angle), nz = Math.sin(angle), px = -nz, pz = nx;
        color(t, 0xFF8A1E, 1f);
        t.addVertex(cx + nx * to, y, cz + nz * to);
        t.addVertex(cx + nx * from + px * halfBase, y, cz + nz * from + pz * halfBase);
        t.addVertex(cx + nx * from - px * halfBase, y, cz + nz * from - pz * halfBase);
        t.addVertex(cx + nx * from - px * halfBase, y, cz + nz * from - pz * halfBase);
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

    private static void ringQuarter(Tessellator t, double fromDeg, double toDeg, double r0, double r1, double y0,
        double y1, int rgb, float a) {
        int steps = 12;
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

    // kept above and behind the holder; it turns see-through while another effect plays or the camera is right at it
    private static void balloon(BlockEntityMinoTable table, Vec3d at, int fuse, long now, boolean busy) {
        double x = at.x, z = at.z;
        double bob = Math.sin((now % 100000L) / 400.0) * 0.04;
        double y = at.y + bob;
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

    private static void bigText(String text, double x, double y, double z, float t, float size, int face, float tilt) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
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
        GL11.glPushMatrix();
        GL11.glTranslatef(0.9f, 0.9f, 0.3f);
        font.drawString(text, -w / 2, -4, alpha | 0x2A1000);
        GL11.glPopMatrix();
        font.drawString(text, -w / 2, -4, alpha | face);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private static void bentFace(CardFace face, String theme, float w, float h, float bend) {
        bentFace(face, theme, w, h, bend, false);
    }

    private static void bentFace(CardFace face, String theme, float w, float h, float bend, boolean mirror) {
        ResourceLocation[] layers = CardBatch.textures(face, theme);
        int[] tints = new int[layers.length];
        for (int i = 0; i < layers.length; i++) tints[i] = ThemeManager.INSTANCE.layerTint(theme, face, i);
        bentLayers(layers, tints, w, h, bend, mirror);
    }

    private static void bentLayers(ResourceLocation[] layers, int[] tints, float w, float h, float bend,
        boolean mirror) {
        int cols = 12;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int layer = 0; layer < layers.length; layer++) {
            ResourceLocation tex = layers[layer];
            if (tex == null) continue;
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(tex);
            int tint = tints == null ? 0xFFFFFF : tints[layer];
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.setColorRGBA_F((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1);
            for (int i = 0; i < cols; i++) {
                double u0 = i / (double) cols, u1 = (i + 1) / (double) cols;
                double x0 = -w / 2 + w * u0, x1 = -w / 2 + w * u1;
                double k0 = 2 * u0 - 1, k1 = 2 * u1 - 1;
                double z0 = bend * w * (k0 * k0 - 1), z1 = bend * w * (k1 * k1 - 1);
                double tu0 = mirror ? 1 - u0 : u0, tu1 = mirror ? 1 - u1 : u1;
                t.addVertexWithUV(x0, -h / 2, z0, tu0, 0);
                t.addVertexWithUV(x1, -h / 2, z1, tu1, 0);
                t.addVertexWithUV(x1, h / 2, z1, tu1, 1);
                t.addVertexWithUV(x0, h / 2, z0, tu0, 1);
            }
            t.draw();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
