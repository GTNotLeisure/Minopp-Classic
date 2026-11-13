package com.hfstudio.minopp.util;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class WorldUtil {

    private WorldUtil() {}

    public static EntityPlayer getPlayerByUUID(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        for (EntityPlayer o : world.playerEntities) {
            EntityPlayer p = o;
            if (uuid.equals(PlayerIdentity.of(p))) return p;
        }
        return null;
    }

    public static Entity getEntityByUUID(World world, UUID uuid) {
        if (world == null || uuid == null) return null;
        EntityPlayer player = getPlayerByUUID(world, uuid);
        if (player != null) return player;
        for (Entity o : world.loadedEntityList) {
            Entity e = o;
            if (uuid.equals(PlayerIdentity.of(e))) return e;
        }
        return null;
    }
}
