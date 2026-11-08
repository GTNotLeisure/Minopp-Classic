package com.hfstudio.minopp.effect;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

/**
 * 1.7.10 has no Glowing potion effect; the winner is instead surrounded by sparkle particles on every client nearby.
 */
public record PlayerGlowEffectEvent(UUID targetPlayer, int duration) implements EffectEvent {

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public Type<PlayerGlowEffectEvent> type() {
        return EffectEvents.PLAYER_GLOW;
    }

    @Override
    public int timeOffset() {
        return 0;
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {
        MinoClient.addGlow(targetPlayer, duration);
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        Buf.writeUUID(buffer, targetPlayer);
        buffer.writeInt(duration);
    }

    public static PlayerGlowEffectEvent decode(ByteBuf buffer) {
        return new PlayerGlowEffectEvent(Buf.readUUID(buffer), buffer.readInt());
    }
}
