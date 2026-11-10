package com.hfstudio.minopp.util;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

public class NbtUtil {

    private NbtUtil() {}

    public static void putUUID(NBTTagCompound tag, String key, UUID uuid) {
        tag.setLong(key + "M", uuid.getMostSignificantBits());
        tag.setLong(key + "L", uuid.getLeastSignificantBits());
    }

    public static boolean hasUUID(NBTTagCompound tag, String key) {
        return tag.hasKey(key + "M", 4) && tag.hasKey(key + "L", 4);
    }

    public static UUID getUUID(NBTTagCompound tag, String key) {
        return new UUID(tag.getLong(key + "M"), tag.getLong(key + "L"));
    }
}
