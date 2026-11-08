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

/**
 * A sound played at the table after a delay. Targeted sounds (e.g. "it's your turn") are only heard by that player;
 * the target is now sent over the network (the 1.20.1 version dropped it, so everyone heard them).
 */
public record SoundEffectEvent(int timeOffset, Optional<UUID> target, ResourceLocation soundId, float volume,
    float pitch) implements EffectEvent {

    public SoundEffectEvent(int timeOffset, Optional<UUID> target, ResourceLocation soundId) {
        this(timeOffset, target, soundId, 1f, 1f);
    }

    @Override
    public Type<SoundEffectEvent> type() {
        return EffectEvents.SOUND;
    }

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        buffer.writeInt(event.timeOffset());
        buffer.writeBoolean(target.isPresent());
        target.ifPresent(uuid -> Buf.writeUUID(buffer, uuid));
        Buf.writeResourceLocation(buffer, soundId);
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
    }

    public static SoundEffectEvent decode(ByteBuf buffer) {
        int time = buffer.readInt();
        Optional<UUID> target = buffer.readBoolean() ? Optional.of(Buf.readUUID(buffer)) : Optional.empty();
        ResourceLocation id = Buf.readResourceLocation(buffer);
        return new SoundEffectEvent(time, target, id, buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {
        level.playSound(origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, soundId.toString(), volume, pitch, false);
        if (selfPartOfSourceGame) {
            // Something's happening, so reset the idle timer
            // This is chosen as most actions have a sound effect associated with them
            TurnDeadMan.pedal();
        }
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}
}
