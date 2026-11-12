package com.hfstudio.minopp.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.NbtUtil;
import com.hfstudio.minopp.util.Vec3d;

public class TableBotLifecycle {

    private final BlockEntityMinoTable table;
    private final Map<UUID, BotState> bots = new HashMap<>();
    private boolean disposed;

    private static class BotState {

        private EntityAutoPlayer entity;
        private NBTTagCompound config = new NBTTagCompound();
        private int chunkX;
        private int chunkZ;
        private long retryAt;
    }

    public TableBotLifecycle(BlockEntityMinoTable table) {
        this.table = table;
    }

    public void track(EntityAutoPlayer bot) {
        World world = table.getWorldObj();
        if (disposed || bot.cardPlayer == null || world == null || world.isRemote) return;
        BotState state = bots.computeIfAbsent(bot.getUniqueID(), id -> new BotState());
        state.entity = bot;
        capture(state);
    }

    private void capture(BotState state) {
        EntityAutoPlayer bot = state.entity;
        if (bot == null) return;
        NBTTagCompound config = bot.writeConfigToTag();
        int chunkX = MathHelper.floor_double(bot.posX) >> 4;
        int chunkZ = MathHelper.floor_double(bot.posZ) >> 4;
        if (!config.equals(state.config) || chunkX != state.chunkX || chunkZ != state.chunkZ) {
            state.config = config;
            state.chunkX = chunkX;
            state.chunkZ = chunkZ;
            table.markDirty();
        }
    }

    private CardPlayer member(UUID id) {
        int seat = table.seatOf(id);
        if (seat < 0 || !table.seats[seat].bot) return null;
        return table.game == null ? table.seats[seat] : table.game.deAmputate(id);
    }

    public void tick() {
        World world = table.getWorldObj();
        if (disposed || world == null || world.isRemote) return;
        long time = world.getTotalWorldTime();
        boolean scan = time % 20 == 0;
        Iterator<Map.Entry<UUID, BotState>> entries = bots.entrySet()
            .iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, BotState> entry = entries.next();
            if (member(entry.getKey()) == null) {
                if (entry.getValue().entity != null) world.removeEntity(entry.getValue().entity);
                entries.remove();
                scan = true;
                table.markDirty();
            }
        }
        for (CardPlayer seat : table.seats) {
            if (seat == null || !seat.bot) continue;
            BotState state = bots.get(seat.uuid);
            if (state == null) {
                state = new BotState();
                Vec3d spot = table.botSpot(seat.seat);
                state.chunkX = MathHelper.floor_double(spot.x) >> 4;
                state.chunkZ = MathHelper.floor_double(spot.z) >> 4;
                bots.put(seat.uuid, state);
                scan = true;
            }
            EntityAutoPlayer bot = state.entity;
            if (bot != null) {
                // Save current configuration even when an external removal has already marked the bot dead.
                if (scan || !bot.isEntityAlive()) capture(state);
                int chunkX = MathHelper.floor_double(bot.posX) >> 4;
                int chunkZ = MathHelper.floor_double(bot.posZ) >> 4;
                if (state.chunkX != chunkX || state.chunkZ != chunkZ) capture(state);
                if (!bot.isEntityAlive() || world.getEntityByID(bot.getEntityId()) != bot) {
                    state.entity = null;
                    scan |= time >= state.retryAt;
                }
            } else scan |= time >= state.retryAt;
        }
        if (bots.isEmpty()) return;
        if (scan) discover();
        boolean changed = false;
        for (Map.Entry<UUID, BotState> entry : bots.entrySet()) {
            BotState state = entry.getValue();
            CardPlayer player = member(entry.getKey());
            if (player == null) continue;
            if (state.entity != null) {
                changed |= state.entity.synchronizeSeat(table, player);
                continue;
            }
            if (time < state.retryAt) continue;
            state.retryAt = time + 20;
            Vec3d spot = table.botSpot(player.seat);
            // Loading either chunk here could revive a bot that is still saved in an unloaded chunk.
            if (!loaded(world, state.chunkX, state.chunkZ)
                || !loaded(world, MathHelper.floor_double(spot.x) >> 4, MathHelper.floor_double(spot.z) >> 4)) continue;
            EntityAutoPlayer bot = new EntityAutoPlayer(world);
            bot.restoreSeat(table, player, state.config);
            if (!world.spawnEntityInWorld(bot) || !bot.isEntityAlive()) {
                world.removeEntity(bot);
                continue;
            }
            state.entity = bot;
            capture(state);
            changed = true;
        }
        if (changed) table.sync();
    }

    private static boolean loaded(World world, int chunkX, int chunkZ) {
        return world.getChunkProvider()
            .chunkExists(chunkX, chunkZ) && world.getChunkFromChunkCoords(chunkX, chunkZ).isChunkLoaded;
    }

    private void discover() {
        World world = table.getWorldObj();
        Set<EntityAutoPlayer> observed = new HashSet<>();
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityAutoPlayer bot)) continue;
            BotState state = bots.get(bot.getUniqueID());
            boolean attached = table.getBlockPos()
                .equals(bot.tablePos);
            if (state == null) {
                if (attached) world.removeEntity(bot);
                continue;
            }
            if (!bot.isEntityAlive()) {
                world.removeEntity(bot);
                state.retryAt = Math.max(state.retryAt, world.getTotalWorldTime() + 1);
            } else if (state.entity == null) {
                state.entity = bot;
                capture(state);
            } else if (state.entity != bot) world.removeEntity(bot);
            if (state.entity == bot) observed.add(bot);
        }
        for (BotState state : bots.values()) {
            EntityAutoPlayer bot = state.entity;
            if (bot == null || observed.contains(bot)) continue;
            capture(state);
            // Repair stale tracking and chunk membership left by removals that bypass World.removeEntity.
            if (world.getEntityByID(bot.getEntityId()) == bot) world.removePlayerEntityDangerously(bot);
            state.entity = null;
        }
    }

    public void remove(UUID id) {
        World world = table.getWorldObj();
        if (world == null || world.isRemote) return;
        BotState state = bots.remove(id);
        if (state != null && state.entity != null) world.removeEntity(state.entity);
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityAutoPlayer && id.equals(entity.getUniqueID())) world.removeEntity(entity);
        }
        table.markDirty();
    }

    public void dispose() {
        World world = table.getWorldObj();
        if (disposed || world == null || world.isRemote) return;
        disposed = true;
        for (BotState state : bots.values()) {
            if (state.entity != null) world.removeEntity(state.entity);
        }
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityAutoPlayer bot && (table.getBlockPos()
                .equals(bot.tablePos) || member(bot.getUniqueID()) != null)) world.removeEntity(bot);
        }
        bots.clear();
    }

    public NBTTagList toTag() {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, BotState> entry : bots.entrySet()) {
            BotState state = entry.getValue();
            capture(state);
            NBTTagCompound tag = new NBTTagCompound();
            NbtUtil.putUUID(tag, "player", entry.getKey());
            tag.setTag("config", state.config.copy());
            tag.setInteger("chunkX", state.chunkX);
            tag.setInteger("chunkZ", state.chunkZ);
            list.appendTag(tag);
        }
        return list;
    }

    public void fromTag(NBTTagList list) {
        bots.clear();
        disposed = false;
        for (int i = 0; i < list.tagCount() && i < BlockEntityMinoTable.MAX_SEATS; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (!NbtUtil.hasUUID(tag, "player")) continue;
            BotState state = new BotState();
            state.config = (NBTTagCompound) tag.getCompoundTag("config")
                .copy();
            state.chunkX = tag.getInteger("chunkX");
            state.chunkZ = tag.getInteger("chunkZ");
            bots.put(NbtUtil.getUUID(tag, "player"), state);
        }
    }
}
