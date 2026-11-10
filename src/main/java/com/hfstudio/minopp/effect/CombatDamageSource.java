package com.hfstudio.minopp.effect;

import java.util.Objects;

import net.minecraft.entity.Entity;
import net.minecraft.util.EntityDamageSource;

public class CombatDamageSource extends EntityDamageSource {

    public static final String TYPE = "minopp.combat";

    public CombatDamageSource(Entity attacker) {
        super(TYPE, Objects.requireNonNull(attacker, "attacker"));
        setMagicDamage();
    }

    @Override
    public boolean isDifficultyScaled() {
        return false;
    }
}
