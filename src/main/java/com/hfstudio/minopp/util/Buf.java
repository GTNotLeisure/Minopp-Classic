package com.hfstudio.minopp.util;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public final class Buf {

    private Buf() {}

    public static void writeUUID(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUUID(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeString(ByteBuf buf, String s) {
        ByteBufUtils.writeUTF8String(buf, s);
    }

    public static String readString(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    public static void writeNbt(ByteBuf buf, NBTTagCompound tag) {
        ByteBufUtils.writeTag(buf, tag);
    }

    public static NBTTagCompound readNbt(ByteBuf buf) {
        return ByteBufUtils.readTag(buf);
    }

    public static void writeResourceLocation(ByteBuf buf, ResourceLocation loc) {
        writeString(buf, loc.toString());
    }

    public static ResourceLocation readResourceLocation(ByteBuf buf) {
        return new ResourceLocation(readString(buf));
    }

    public static void writeBlockPos(ByteBuf buf, BlockPos pos) {
        buf.writeInt(pos.x);
        buf.writeInt(pos.y);
        buf.writeInt(pos.z);
    }

    public static BlockPos readBlockPos(ByteBuf buf) {
        return new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }
}
