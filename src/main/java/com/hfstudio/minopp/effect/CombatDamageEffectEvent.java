package com.hfstudio.minopp.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.WorldUtil;

import io.netty.buffer.ByteBuf;

public record CombatDamageEffectEvent(UUID sourcePlayer, UUID targetPlayer, int amount) implements EffectEvent {

    public CombatDamageEffectEvent {
        Objects.requireNonNull(sourcePlayer, "sourcePlayer");
        Objects.requireNonNull(targetPlayer, "targetPlayer");
        if (amount < 0) throw new IllegalArgumentException("Damage cannot be negative");
    }

    @Override
    public Type<CombatDamageEffectEvent> type() {
        return EffectEvents.COMBAT_DAMAGE;
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
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {}

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {
        if (amount == 0 || sourcePlayer.equals(targetPlayer)) return;
        Entity attacker = WorldUtil.getEntityByUUID(level, sourcePlayer);
        Entity victim = WorldUtil.getEntityByUUID(level, targetPlayer);
        if (attacker == null || victim == null || !attacker.isEntityAlive() || !victim.isEntityAlive()) return;
        if (victim instanceof EntityPlayer player && player.capabilities.isCreativeMode) return;
        victim.attackEntityFrom(new CombatDamageSource(attacker), amount);
    }

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        Buf.writeUUID(buffer, sourcePlayer);
        Buf.writeUUID(buffer, targetPlayer);
        buffer.writeInt(amount);
    }

    public static CombatDamageEffectEvent decode(ByteBuf buffer) {
        return new CombatDamageEffectEvent(Buf.readUUID(buffer), Buf.readUUID(buffer), buffer.readInt());
    }
}
