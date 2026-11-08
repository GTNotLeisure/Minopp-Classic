package com.hfstudio.minopp.effect;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;

import io.netty.buffer.ByteBuf;

public record SeatActionTakenEffectEvent() implements EffectEvent {

    @Override
    public Type<? extends EffectEvent> type() {
        return EffectEvents.SEAT_ACTION_TAKEN;
    }

    @Override
    public int timeOffset() {
        return 0;
    }

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfIsPartOfSourceGame) {
        if (selfIsPartOfSourceGame) {
            // Another player is starting / stopping the game
            // Close the screen for better player awareness
            MinoClient.closeSeatControlScreen();
        }
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {}

    public static SeatActionTakenEffectEvent decode(ByteBuf buffer) {
        return new SeatActionTakenEffectEvent();
    }
}
