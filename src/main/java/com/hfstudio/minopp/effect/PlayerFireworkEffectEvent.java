package com.hfstudio.minopp.effect;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.WorldUtil;

import io.netty.buffer.ByteBuf;

public record PlayerFireworkEffectEvent(int timeOffset, UUID targetPlayer) implements EffectEvent {

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public Type<PlayerFireworkEffectEvent> type() {
        return EffectEvents.PLAYER_FIREWORK;
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {
        Entity entity = WorldUtil.getEntityByUUID(level, targetPlayer);
        if (entity != null && entity.getDistanceSq(origin.x, origin.y, origin.z) < 16 * 16) {
            level.makeFireworks(entity.posX, entity.boundingBox.minY + 3, entity.posZ, 0, 0, 0, generateWinExplosion());
            return;
        }
        level.makeFireworks(origin.x + 0.5f, origin.y + 3, origin.z + 0.5f, 0, 0, 0, generateWinExplosion());
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        buffer.writeInt(timeOffset);
        Buf.writeUUID(buffer, targetPlayer);
    }

    public static PlayerFireworkEffectEvent decode(ByteBuf buffer) {
        return new PlayerFireworkEffectEvent(buffer.readInt(), Buf.readUUID(buffer));
    }

    public static NBTTagCompound generateWinExplosion() {
        NBTTagCompound explosion1 = new NBTTagCompound();
        explosion1.setByte("Type", (byte) 0); // small ball
        explosion1.setIntArray("Colors", new int[] {0xD32F2F, 0xF4511E});
        explosion1.setIntArray("FadeColors", new int[] {0xEF9A9A, 0xFFAB91});
        explosion1.setBoolean("Flicker", false);
        explosion1.setBoolean("Trail", false);

        NBTTagCompound explosion2 = new NBTTagCompound();
        explosion2.setByte("Type", (byte) 1); // large ball
        explosion2.setIntArray("Colors", new int[] {0xFDD835, 0xC0CA33});
        explosion2.setIntArray("FadeColors", new int[] {0xFFF59D, 0xE6EE9C});
        explosion2.setBoolean("Flicker", false);
        explosion2.setBoolean("Trail", false);

        NBTTagList explosions = new NBTTagList();
        explosions.appendTag(explosion1);
        explosions.appendTag(explosion2);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Explosions", explosions);
        return tag;
    }
}
