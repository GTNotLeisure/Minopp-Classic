package com.hfstudio.minopp.render;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardTypes;
import com.hfstudio.minopp.util.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Table animations for played cards. Register effects for a card type id (or for every face matching a test) from
 * the client side of your mod; when a card is played they run with a {@link Context} that can start any of the
 * built-in animations or a custom one. Unless an effect calls {@link Context#cancelDefaults()}, the generic
 * animations for the card's kind (skip, reverse, draw, wild color) play as well.
 *
 * <pre>
 * CardFxRegistry.register("my_wild_bomb", ctx -> {
 *     ctx.bigText(
 *         "BOOM",
 *         0xFF4020,
 *         ctx.pile()
 *             .add(0, 1.6, 0),
 *         300,
 *         1400);
 *     ctx.burst(ctx.pile(), 0xFF4020, 200);
 * });
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class CardFxRegistry {

    private CardFxRegistry() {}

    @FunctionalInterface
    public interface CardFx {

        void play(Context ctx);
    }

    /**
     * Draws one frame of a custom effect in table space, already translated to its anchor; t runs 0..1.
     */
    @FunctionalInterface
    public interface Renderer {

        void render(float t, Context ctx);
    }

    private static final Map<String, CardFx[]> BY_TYPE = new HashMap<>();
    private static Match[] matches = new Match[0];

    private record Match(Predicate<CardFace> when, CardFx effect) {}

    public static void register(String typeId, CardFx fx) {
        if (!CardTypes.exists(typeId)) throw new IllegalArgumentException("Unknown card type: " + typeId);
        Objects.requireNonNull(fx, "fx");
        CardFx[] previous = BY_TYPE.get(typeId);
        int size = previous == null ? 0 : previous.length;
        CardFx[] next = previous == null ? new CardFx[1] : Arrays.copyOf(previous, size + 1);
        next[size] = fx;
        BY_TYPE.put(typeId, next);
    }

    public static void register(Predicate<CardFace> when, CardFx fx) {
        Match match = new Match(Objects.requireNonNull(when, "when"), Objects.requireNonNull(fx, "fx"));
        Match[] next = Arrays.copyOf(matches, matches.length + 1);
        next[matches.length] = match;
        matches = next;
    }

    static void dispatch(Context ctx) {
        CardFx[] effects = BY_TYPE.get(ctx.face.type().id);
        for (Match match : matches) {
            if (match.when.test(ctx.face)) match.effect.play(ctx);
        }
        if (effects != null) for (CardFx fx : effects) fx.play(ctx);
    }

    /**
     * What an effect can see and start. Positions are in table space: the table's corner block is the origin.
     */
    public static class Context {

        final BlockEntityMinoTable table;
        final CardGame game;
        final CardFace face;
        final long now;
        final TableFx.Sink sink;
        boolean defaultsCancelled;

        Context(BlockEntityMinoTable table, CardGame game, CardFace face, long now, TableFx.Sink sink) {
            this.table = table;
            this.game = game;
            this.face = face;
            this.now = now;
            this.sink = sink;
        }

        public BlockEntityMinoTable table() {
            return table;
        }

        public CardGame game() {
            return game;
        }

        public CardFace face() {
            return face;
        }

        public long now() {
            return now;
        }

        /**
         * Skip the generic animations for this card's kind.
         */
        public void cancelDefaults() {
            defaultsCancelled = true;
        }

        /**
         * The color now in play as RGB (white while it is still wild).
         */
        public int color() {
            return game.currentColor.isWild() ? 0xFFFFFF : game.currentColor.rgb & 0xFFFFFF;
        }

        /**
         * The center of the discard pile, on the table top.
         */
        public Vec3d pile() {
            return new Vec3d(1.5, TableFx.PILE_Y, 1.5);
        }

        /**
         * Over the head of whoever sits in a seat.
         */
        public Vec3d head(int seat, double lift) {
            return TableFx.head(table, seat, lift);
        }

        /**
         * The seat of whoever has to answer this card (the next player), or -1.
         */
        public int victimSeat() {
            int i = game.debugHold && game.debugNext >= 0 ? game.debugNext : game.currentPlayerIndex;
            return i >= 0 && i < game.players.size() ? game.players.get(i).seat : -1;
        }

        public void skipSymbol() {
            sink.skipSymbol(color());
        }

        public void skipSignsOverSkipped() {
            sink.skipSigns();
        }

        public void reverseArrows() {
            sink.reverse(color());
        }

        /**
         * The four-color pillar that settles on the chosen color: blocks for draw wilds, a ring otherwise.
         */
        public void colorPillar(boolean blocks) {
            sink.pillar(blocks);
        }

        /**
         * Thick cards of the current color that fly to whoever has to draw them.
         */
        public void drawCards(int count) {
            sink.slabs(count, color());
        }

        public void bigText(String text, int rgb, Vec3d at, long delayMs, int durationMs) {
            sink.text(text, rgb, at, delayMs, durationMs);
        }

        /**
         * The card's own symbol popping up on a disc of its color.
         */
        public void symbolPop(Vec3d at, long delayMs) {
            sink.symbol(face, at, delayMs);
        }

        public void coinToss(Vec3d at) {
            sink.coin(at);
        }

        public void burst(Vec3d at, int rgb, long delayMs) {
            sink.burst(at, rgb, delayMs);
        }

        public void particles(String name, int count, double spread) {
            sink.particles(name, count, spread);
        }

        public void custom(Vec3d at, long delayMs, int durationMs, Renderer renderer) {
            sink.custom(at, delayMs, durationMs, renderer);
        }
    }
}
