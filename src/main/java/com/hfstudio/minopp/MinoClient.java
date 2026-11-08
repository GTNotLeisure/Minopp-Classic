package com.hfstudio.minopp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.effect.EffectQueue;
import com.hfstudio.minopp.gui.SeatControlScreen;
import com.hfstudio.minopp.network.MinoNetwork;
import com.hfstudio.minopp.network.S2CActionEphemeralPacket;
import com.hfstudio.minopp.network.S2CAutoPlayerScreenPacket;
import com.hfstudio.minopp.network.S2CEffectListPacket;
import com.hfstudio.minopp.util.WorldUtil;

public class MinoClient {

    public static final EffectQueue SOUND_QUEUE = new EffectQueue();

    public static KeyBinding KEY_SHOUT_MODIFIER;

    // For interfacing with platform codes
    public static double globalFovModifier = 1;
    public static boolean handCardOverlayActive = false;

    /** Winner sparkle effect (replacement for the Glowing effect): entity id -> remaining ticks. */
    private static final Map<UUID, Integer> GLOWS = new HashMap<>();
    private static final Random RANDOM = new Random();

    public static void init() {
        KEY_SHOUT_MODIFIER = new KeyBinding(
            "key.minopp.shout_modifier",
            Keyboard.KEY_LCONTROL,
            "key.categories.minopp");
        cpw.mods.fml.client.registry.ClientRegistry.registerKeyBinding(KEY_SHOUT_MODIFIER);
        MinoNetwork.registerClientReceiver(S2CActionEphemeralPacket.ID, S2CActionEphemeralPacket.Client::handleS2C);
        MinoNetwork.registerClientReceiver(S2CEffectListPacket.ID, S2CEffectListPacket.Client::handleS2C);
        MinoNetwork.registerClientReceiver(S2CAutoPlayerScreenPacket.ID, S2CAutoPlayerScreenPacket.Client::handleS2C);
    }

    public static void tick() {
        World level = Minecraft.getMinecraft().theWorld;
        if (level == null) {
            GLOWS.clear();
            return;
        }
        MinoClient.SOUND_QUEUE.tick(level);
        tickGlows(level);
    }

    public static void addGlow(UUID target, int durationTicks) {
        GLOWS.put(target, durationTicks);
    }

    private static void tickGlows(World level) {
        for (Iterator<Map.Entry<UUID, Integer>> it = GLOWS.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
                continue;
            }
            entry.setValue(remaining);
            Entity entity = WorldUtil.getEntityByUUID(level, entry.getKey());
            if (entity == null) continue;
            for (int i = 0; i < 2; i++) {
                double px = entity.posX + (RANDOM.nextDouble() - 0.5) * entity.width * 1.6;
                double py = entity.boundingBox.minY + RANDOM.nextDouble() * entity.height * 1.1;
                double pz = entity.posZ + (RANDOM.nextDouble() - 0.5) * entity.width * 1.6;
                level.spawnParticle("happyVillager", px, py, pz, 0, 0, 0);
            }
        }
    }

    public static void closeSeatControlScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof SeatControlScreen) {
            mc.displayGuiScreen(null);
        }
    }
}
