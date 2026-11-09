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
        String sound = soundId.toString();
        float p = pitch;
        if (sound.equals("minopp:game.mino_shout")) {
            // each player picks how a MINO! shout sounds to them
            switch (com.hfstudio.minopp.config.ClientConfig.display.shoutSound) {
                case "off" -> sound = null;
                case "high" -> p *= 1.35f;
                case "low" -> p *= 0.75f;
                case "chime" -> {
                    sound = "random.orb";
                    p = 0.8f;
                }
                default -> {}
            }
        }
        if (sound != null) level.playSound(origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, sound, volume, p, false);
        if (selfPartOfSourceGame) {
            TurnDeadMan.pedal();
        }
    }

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {}
}
