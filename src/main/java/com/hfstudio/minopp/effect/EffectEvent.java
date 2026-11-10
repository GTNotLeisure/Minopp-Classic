package com.hfstudio.minopp.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;

import io.netty.buffer.ByteBuf;

public interface EffectEvent {

    Type<? extends EffectEvent> type();

    int timeOffset();

    Optional<UUID> target();

    void summonClient(World level, BlockPos origin, boolean selfIsPartOfSourceGame);

    // Summons the effect on the server side. TimeOffset not supported.
    void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity);

    void encode(EffectEvent event, ByteBuf buffer);

    public class Type<T extends EffectEvent> {

        private final String id;
        private final Function<ByteBuf, T> function;

        public Type(String id, Function<ByteBuf, T> function) {
            this.id = Objects.requireNonNull(id, "id");
            this.function = Objects.requireNonNull(function, "function");
            if (id.isBlank()) throw new IllegalArgumentException("Effect id cannot be blank");
        }

        public String id() {
            return id;
        }

        public Function<ByteBuf, T> function() {
            return function;
        }
    }
}
