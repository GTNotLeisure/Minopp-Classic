package com.hfstudio.minopp;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.AutoPlayer;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Vec3d;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Development helper, active only with {@code -Dminopp.worldPreview=<players>}: builds a table next to the first
 * player, seats bots around it, starts a game and auto-plays the player's turns, then loops through the game modes.
 * Used to check dealing, playing and rendering with many players. Never active in normal play.
 */
public final class DevPreview {

    public static boolean enabled() {
        return System.getProperty("minopp.worldPreview") != null;
    }

    private static int playerCount() {
        try {
            return Math.max(2, Math.min(20, Integer.parseInt(System.getProperty("minopp.worldPreview"))));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private static final String[] MODES = { CardPack.CLASSIC, CardPack.NO_MERCY, CardPack.FLIP, CardPack.FLEX,
        CardPack.ALL_WILD, CardPack.PARTY };
    private int ticks;
    private boolean teamsConfigured;
    private int stage;
    private int mode = Math.max(
        0,
        Arrays.asList(MODES)
            .indexOf(System.getProperty("minopp.previewMode", CardPack.CLASSIC)));
    private BlockPos core;
    private final AutoPlayer ai = new AutoPlayer();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer();
        List<?> players = server.getConfigurationManager().playerEntityList;
        if (players.isEmpty()) return;
        EntityPlayerMP player = (EntityPlayerMP) players.get(0);
        World world = player.worldObj;
        ticks++;
        BlockEntityMinoTable table = core == null ? null : ItemHandCards.getTable(world, core);
        switch (stage) {
            case 0 -> {
                if (ticks < 40) return;
                setup(world, player);
                stage = 1;
                ticks = 0;
            }
            case 1 -> {
                if (ticks < 30 || table == null) return;
                table.joinPlayerToTable(new CardPlayer(player), new Vec3d(player.posX, player.posY, player.posZ));
                int seat = table.seatOf(
                    player.getGameProfile()
                        .getId());
                String cam = System.getProperty("minopp.previewCam");
                if (cam != null) {
                    String[] c = cam.split(",");
                    double dx = Double.parseDouble(c[0]), dy = Double.parseDouble(c[1]), dz = Double.parseDouble(c[2]);
                    player.capabilities.isFlying = true;
                    player.sendPlayerAbilities();
                    player.playerNetServerHandler.setPlayerLocation(
                        core.x + 1.5 + dx,
                        core.y + dy,
                        core.z + 1.5 + dz,
                        (float) Math.toDegrees(Math.atan2(dx, -dz)),
                        (float) Math.toDegrees(Math.atan2(dy + 0.62, Math.sqrt(dx * dx + dz * dz))));
                } else if (seat >= 0) {
                    Vec3d spot = table.botSpot(seat);
                    double ox = spot.x - (core.x + 1.5), oz = spot.z - (core.z + 1.5);
                    double len = Math.max(1e-3, Math.sqrt(ox * ox + oz * oz));
                    player.playerNetServerHandler.setPlayerLocation(
                        spot.x + ox / len * 0.5,
                        core.y,
                        spot.z + oz / len * 0.5,
                        (float) Math.toDegrees(Math.atan2(ox, -oz)),
                        player.rotationPitch);
                }
                table.addBots(BlockEntityMinoTable.MAX_SEATS);
                stage = 2;
                ticks = 0;
            }
            case 2 -> {
                if (table == null || ticks < 30) return;
                if (System.getProperty("minopp.previewTeams") != null && !teamsConfigured) {
                    teamsConfigured = true;
                    table.config.set(GameRules.TEAM_MODE, 1);
                    table.config.set(GameRules.TEAM_COUNT, Integer.parseInt(System.getProperty("minopp.previewTeams")));
                    table.onSeatCountChanged();
                    table.sync();
                }
                if (System.getProperty("minopp.previewTeams") != null) return;
                if (table.getPlayersList()
                    .size() >= playerCount() || ticks > 200) {
                    configureMode(table.config);
                    if (System.getProperty("minopp.previewDebug") != null) {
                        table.config.set(GameRules.FLIP_MODE, 1);
                        table.startGame(
                            new CardPlayer(player),
                            player.getGameProfile()
                                .getId());
                    } else {
                        table.startGame(new CardPlayer(player));
                    }
                    player.inventory.currentItem = 0;
                    stage = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (table == null) return;
                if (table.game == null) {
                    if (ticks > 100) {
                        mode = (mode + 1) % MODES.length;
                        stage = 2;
                        ticks = 25;
                    }
                    return;
                }
                if (ticks % 30 != 0 || table.game.awaitingCutIn || !table.game.decisions.isEmpty()) {
                    if (!table.game.decisions.isEmpty() && ticks % 30 == 0) {
                        ActionReport r = table.game.tickDecisions(Long.MAX_VALUE, u -> false);
                        if (r != null)
                            table.handleActionResult(r, table.game == null ? null : table.game.currentPlayer(), null);
                    }
                    return;
                }
                CardPlayer me = table.game.deAmputate(
                    player.getGameProfile()
                        .getId());
                if (table.game.debug) {
                    if (ticks % 60 == 0 && me != null) playScripted(table, me, player);
                    return;
                }
                if (me != null && table.game.currentPlayer()
                    .equals(me)) {
                    ActionReport r = ai.playAtGame(table.game, me, server);
                    table.handleActionResult(r, me, player);
                }
            }
            default -> {}
        }
    }

    private static final String[] SCRIPT = { "skip", "reverse", "draw2", "wild", "wild_draw4", "wild_fifty_fifty",
        "flip", "skip_everyone", "wild_draw2", "flip", "wild_time_balloon", "number", "shout" };
    private int scriptStep;

    // debug preview: plays one card of each effect in turn, so every table animation can be watched
    private void playScripted(BlockEntityMinoTable table, CardPlayer me, EntityPlayerMP player) {
        CardGame game = table.game;
        String id = SCRIPT[scriptStep++ % SCRIPT.length];
        if (id.equals("shout")) {
            for (CardPlayer p : game.players) p.hasShoutedMino = false;
            CardPlayer bot = game.players.get(scriptStep % game.players.size());
            bot.hasShoutedMino = true;
            table.sync();
            return;
        }
        List<CardColor> colors = CardColor.forSide(game.darkSide);
        Card pick = null;
        for (Card c : me.hand) {
            CardFace f = game.face(c);
            if (f.type().id.equals(id) && (f.isWild() || f.color == colors.get(scriptStep % 4))) {
                pick = c;
                break;
            }
        }
        if (pick == null) {
            for (Card c : me.hand) if (game.face(c)
                .type().id.equals(id)) {
                    pick = c;
                    break;
                }
        }
        if (pick == null) pick = me.hand.get(scriptStep % me.hand.size());
        PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
        choices.color = colors.get((scriptStep / 2) % 4);
        ActionReport r = game.playCard(me, pick.uid, choices);
        table.handleActionResult(r, me, player);
    }

    private void configureMode(TableConfig config) {
        int seats = playerCount();
        config.resetToPreset(MODES[mode]);
        config.set(GameRules.MAX_PLAYERS, seats);
        config.set(GameRules.SEVEN_ZERO, 1);
        config.set(GameRules.JUMP_IN, 1);
        if (MODES[mode].equals(CardPack.CLASSIC)) {
            config.setCount("no_mercy:discard_all", 2);
            config.setCount("weekend:wild_time_balloon", 2);
            config.setCount("jungle:wild_rebound", 2);
        }
        for (String spec : System.getProperty("minopp.previewDeck", "")
            .split(",")) {
            int eq = spec.indexOf('=');
            if (eq > 0) config.setCount(spec.substring(0, eq), Integer.parseInt(spec.substring(eq + 1)));
        }
    }

    private void setup(World world, EntityPlayerMP player) {
        int n = playerCount();
        world.setWorldTime(6000);
        int px = MathHelper.floor_double(player.posX), py = MathHelper.floor_double(player.boundingBox.minY);
        int pz = MathHelper.floor_double(player.posZ);
        core = new BlockPos(px - 1, py, pz - 6);
        for (int dx = -14; dx <= 16; dx++) for (int dz = -14; dz <= 16; dz++) {
            for (int dy = 0; dy < 4; dy++) world.setBlockToAir(core.x + dx, core.y + dy, core.z + dz);
        }
        world.setBlock(core.x, core.y, core.z, Mino.BLOCK_MINO_TABLE, 0, 3);
        Mino.BLOCK_MINO_TABLE.onBlockPlacedBy(world, core.x, core.y, core.z, player, null);
        BlockEntityMinoTable table = ItemHandCards.getTable(world, core);
        table.config.set(GameRules.MAX_PLAYERS, n);
        int playerSeat = n / 2;
        Vec3d mySeat = BlockEntityMinoTable.getSeatLocalPos(playerSeat, n);
        player.playerNetServerHandler.setPlayerLocation(
            core.x + mySeat.x,
            core.y,
            core.z + mySeat.z + 0.9,
            180,
            Float.parseFloat(System.getProperty("minopp.previewPitch", "42")));
    }
}
