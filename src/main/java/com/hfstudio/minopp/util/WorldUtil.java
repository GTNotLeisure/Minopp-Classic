package com.hfstudio.minopp.util;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class WorldUtil {

    private WorldUtil() {}

    public static EntityPlayer getPlayerByUUID(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        for (Object o : world.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (uuid.equals(
                p.getGameProfile()
                    .getId()))
                return p;
        }
        return null;
    }

    /** Finds an entity by its UUID. Players are matched by their profile id. */
    public static Entity getEntityByUUID(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        EntityPlayer player = getPlayerByUUID(world, uuid);
        if (player != null) return player;
        for (Object o : world.loadedEntityList) {
            Entity e = (Entity) o;
            if (uuid.equals(e.getUniqueID())) return e;
        }
        return null;
    }
}
