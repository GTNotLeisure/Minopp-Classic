package com.hfstudio.minopp;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.AutoPlayer;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
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
    private static final String[] NAMES = { "Pudding", "Luna", "Dusty", "Pepper", "Mochi", "Biscuit", "Nova", "Ziggy",
        "Clover", "Maple", "Pixel", "Sprout", "Tofu", "Juniper", "Waffle", "Comet", "Hazel", "Noodle", "Bean" };

    private int ticks;
    private int stage;
    private int mode;
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
                stage = 2;
                ticks = 0;
            }
            case 2 -> {
                // wait for every bot to sit down, then deal
                if (table == null || ticks < 30) return;
                if (table.getPlayersList()
                    .size() >= playerCount() || ticks > 200) {
                    configureMode(table.config);
                    table.startGame(new CardPlayer(player));
                    player.inventory.currentItem = 0;
                    stage = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (table == null) return;
                if (table.game == null) {
                    // game over: next mode after a short pause
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
                if (me != null && table.game.currentPlayer()
                    .equals(me)) {
                    ActionReport r = ai.playAtGame(table.game, me, server);
                    table.handleActionResult(r, me, player);
                }
            }
            default -> {}
        }
    }

    private void configureMode(TableConfig config) {
        int seats = playerCount();
        config.resetToPreset(MODES[mode]);
        config.set(GameRules.MAX_PLAYERS, seats);
        config.set(GameRules.SEVEN_ZERO, 1);
        config.set(GameRules.JUMP_IN, 1);
        if (MODES[mode].equals(CardPack.CLASSIC)) {
            // a few cards of other packs mixed in
            config.setCount("no_mercy:discard_all", 2);
            config.setCount("weekend:wild_time_balloon", 2);
            config.setCount("hero:wild_rebound", 2);
        }
    }

    private void setup(World world, EntityPlayerMP player) {
        int n = playerCount();
        world.setWorldTime(6000);
        int px = MathHelper.floor_double(player.posX), py = MathHelper.floor_double(player.boundingBox.minY);
        int pz = MathHelper.floor_double(player.posZ);
        core = new BlockPos(px - 1, py, pz - 6);
        for (int dx = -4; dx <= 6; dx++) for (int dz = -4; dz <= 6; dz++) {
            for (int dy = 0; dy < 4; dy++) world.setBlockToAir(core.x + dx, core.y + dy, core.z + dz);
        }
        world.setBlock(core.x, core.y, core.z, Mino.BLOCK_MINO_TABLE, 0, 3);
        Mino.BLOCK_MINO_TABLE.onBlockPlacedBy(world, core.x, core.y, core.z, player, null);
        BlockEntityMinoTable table = ItemHandCards.getTable(world, core);
        table.config.set(GameRules.MAX_PLAYERS, n);
        // the player takes the south seat (index n/2), bots fill the others
        int playerSeat = n / 2;
        int bot = 0;
        for (int i = 0; i < n; i++) {
            if (i == playerSeat) continue;
            Vec3d seat = BlockEntityMinoTable.getSeatLocalPos(i, n);
            double ox = seat.x - 1.5, oz = seat.z - 1.5;
            double len = Math.sqrt(ox * ox + oz * oz);
            EntityAutoPlayer e = new EntityAutoPlayer(world);
            e.setPosition(core.x + seat.x + ox / len * 0.45, core.y, core.z + seat.z + oz / len * 0.45);
            e.setCustomNameTag(NAMES[bot++ % NAMES.length]);
            e.autoPlayer.aiNoDelay = 1;
            e.setActive(true);
            world.spawnEntityInWorld(e);
        }
        Vec3d mySeat = BlockEntityMinoTable.getSeatLocalPos(playerSeat, n);
        player.playerNetServerHandler.setPlayerLocation(
            core.x + mySeat.x,
            core.y,
            core.z + mySeat.z + 0.9,
            180,
            Float.parseFloat(System.getProperty("minopp.previewPitch", "42")));
    }
}
