package com.hfstudio.minopp.effect;

import java.util.HashMap;
import java.util.Map;

public class EffectEvents {

    public static final int EFFECT_RADIUS = 16;

    public static final EffectEvent.Type<SoundEffectEvent> SOUND = new EffectEvent.Type<>(
        "minopp:sound",
        SoundEffectEvent::decode);
    public static final EffectEvent.Type<PlayerFireworkEffectEvent> PLAYER_FIREWORK = new EffectEvent.Type<>(
        "minopp:player_firework",
        PlayerFireworkEffectEvent::decode);
    public static final EffectEvent.Type<PlayerGlowEffectEvent> PLAYER_GLOW = new EffectEvent.Type<>(
        "minopp:player_glow",
        PlayerGlowEffectEvent::decode);
    public static final EffectEvent.Type<GrantRewardEffectEvent> GRANT_REWARD = new EffectEvent.Type<>(
        "minopp:grant_reward",
        GrantRewardEffectEvent::decode);
    public static final EffectEvent.Type<SeatActionTakenEffectEvent> SEAT_ACTION_TAKEN = new EffectEvent.Type<>(
        "minopp:seat_action_taken",
        SeatActionTakenEffectEvent::decode);

    public static final Map<String, EffectEvent.Type<?>> REGISTRY = new HashMap<>();

    static {
        for (EffectEvent.Type<?> type : new EffectEvent.Type<?>[] { SOUND, PLAYER_FIREWORK, PLAYER_GLOW, GRANT_REWARD,
            SEAT_ACTION_TAKEN }) {
            REGISTRY.put(type.id(), type);
        }
    }
}
