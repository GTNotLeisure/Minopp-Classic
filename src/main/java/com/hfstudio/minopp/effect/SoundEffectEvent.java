package com.hfstudio.minopp.effect;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.gui.TurnDeadMan;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public record SoundEffectEvent(int timeOffset, Optional<UUID> target, ResourceLocation soundId) implements EffectEvent {

    @Override
    public Type<SoundEffectEvent> type() {
        return EffectEvents.SOUND;
    }

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        buffer.writeInt(event.timeOffset());
        Buf.writeResourceLocation(buffer, soundId);
    }

    public static SoundEffectEvent decode(ByteBuf buffer) {
        return new SoundEffectEvent(buffer.readInt(), Optional.empty(), Buf.readResourceLocation(buffer));
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {
        level.playSound(origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, soundId.toString(), 1, 1, false);
        if (selfPartOfSourceGame) {
            // Something's happening, so reset the idle timer
            // This is chosen as most actions have a sound effect associated with them
            TurnDeadMan.pedal();
        }
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}
}
