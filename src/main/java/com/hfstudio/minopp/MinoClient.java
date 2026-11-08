package com.hfstudio.minopp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.effect.EffectQueue;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.gui.DecisionScreen;
import com.hfstudio.minopp.gui.GameSettingsScreen;
import com.hfstudio.minopp.gui.SeatControlScreen;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.MinoNetwork;
import com.hfstudio.minopp.network.S2CActionEphemeralPacket;
import com.hfstudio.minopp.network.S2CAutoPlayerScreenPacket;
import com.hfstudio.minopp.network.S2CEffectListPacket;
import com.hfstudio.minopp.network.ThemeSyncPackets;
import com.hfstudio.minopp.render.ThemeManager;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.client.registry.ClientRegistry;

public class MinoClient {

    public static final EffectQueue SOUND_QUEUE = new EffectQueue();

    public static KeyBinding KEY_SHOUT_MODIFIER;
    public static KeyBinding KEY_FLEX_MODIFIER;

    // For interfacing with platform codes
    public static double globalFovModifier = 1;
    public static boolean handCardOverlayActive = false;

    /** Winner sparkle effect (replacement for the Glowing effect): entity id -> remaining ticks. */
    private static final Map<UUID, Integer> GLOWS = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static boolean wasInWorld = false;
    private static PendingDecision lastOpenedDecision = null;
    private static boolean themeListenerRegistered = false;
    private static boolean worldPreviewLaunched = false;

    public static void init() {
        KEY_SHOUT_MODIFIER = new KeyBinding(
            "key.minopp.shout_modifier",
            Keyboard.KEY_LCONTROL,
            "key.categories.minopp");
        KEY_FLEX_MODIFIER = new KeyBinding("key.minopp.flex_modifier", Keyboard.KEY_LMENU, "key.categories.minopp");
        ClientRegistry.registerKeyBinding(KEY_SHOUT_MODIFIER);
        ClientRegistry.registerKeyBinding(KEY_FLEX_MODIFIER);
        MinoNetwork.registerClientReceiver(S2CActionEphemeralPacket.ID, S2CActionEphemeralPacket.Client::handleS2C);
        MinoNetwork.registerClientReceiver(S2CEffectListPacket.ID, S2CEffectListPacket.Client::handleS2C);
        MinoNetwork.registerClientReceiver(S2CAutoPlayerScreenPacket.ID, S2CAutoPlayerScreenPacket.Client::handleS2C);
        MinoNetwork.registerClientReceiver(
            ThemeSyncPackets.S2C_ID,
            buf -> ThemeManager.INSTANCE.playerThemes.putAll(ThemeSyncPackets.decode(buf)));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!themeListenerRegistered) {
            // Registered after startup (not during mod init) so it never runs inside the startup resource reloads
            themeListenerRegistered = true;
            if (mc.getResourceManager() instanceof IReloadableResourceManager rm) {
                rm.registerReloadListener(ThemeManager.INSTANCE);
            }
        }
        World level = mc.theWorld;
        if (level == null || mc.thePlayer == null) {
            if (DevPreview.enabled() && !worldPreviewLaunched
                && mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu) {
                worldPreviewLaunched = true;
                mc.launchIntegratedServer(
                    "minopp_preview",
                    "Mino Preview",
                    new net.minecraft.world.WorldSettings(
                        1425L,
                        net.minecraft.world.WorldSettings.GameType.CREATIVE,
                        false,
                        false,
                        net.minecraft.world.WorldType.FLAT));
            }
            if (System.getProperty("minopp.uiPreview") != null
                && mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu) {
                mc.displayGuiScreen(com.hfstudio.minopp.gui.GameSettingsScreen.preview());
            }
            GLOWS.clear();
            if (wasInWorld) ThemeManager.INSTANCE.onDisconnect();
            wasInWorld = false;
            return;
        }
        if (System.getProperty("minopp.previewHandScreen") != null && mc.currentScreen == null
            && com.hfstudio.minopp.gui.HandScreen.Context.resolve(mc) != null) {
            // dev only: keep interaction mode open so screenshots show it
            mc.displayGuiScreen(new com.hfstudio.minopp.gui.HandScreen());
        }
        if (System.getProperty("minopp.previewTeams") != null && mc.currentScreen == null) {
            // dev only: show the lobby of the preview table
            for (Object te : level.loadedTileEntityList) {
                if (te instanceof com.hfstudio.minopp.block.BlockEntityMinoTable table && table.getPlayersList()
                    .size() >= 3) {
                    mc.displayGuiScreen(new com.hfstudio.minopp.gui.SeatControlScreen(table.getBlockPos()));
                    break;
                }
            }
        }
        if (System.getProperty("minopp.previewColorPicker") != null && mc.currentScreen == null) {
            // dev only: keep a wild color choice open (nothing is sent unless a color is clicked)
            com.hfstudio.minopp.gui.HandScreen.Context ctx = com.hfstudio.minopp.gui.HandScreen.Context.resolve(mc);
            if (ctx != null) mc.displayGuiScreen(
                new com.hfstudio.minopp.gui.PlayChoiceScreen(
                    ctx.pos,
                    ctx.me,
                    ctx.hand.get(0),
                    new com.hfstudio.minopp.game.PlayContext.PlayChoices(),
                    true,
                    0,
                    ctx.game.darkSide));
        }
        if (!wasInWorld) {
            wasInWorld = true;
            ThemeManager.INSTANCE.sendSelection(true);
        }
        MinoClient.SOUND_QUEUE.tick(level);
        tickGlows(level);
        tickDecisionPrompt(mc);
    }

    /** Opens the decision prompt when the game asks us something (challenge, roulette color, point taken). */
    private static void tickDecisionPrompt(Minecraft mc) {
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(mc.thePlayer);
        BlockEntityMinoTable table = gamePos == null ? null : ItemHandCards.getTable(mc.theWorld, gamePos);
        if (table == null || table.game == null) {
            lastOpenedDecision = null;
            return;
        }
        CardPlayer self = ItemHandCards.getCardPlayer(mc.thePlayer);
        PendingDecision decision = table.game.decisionFor(self.uuid);
        if (decision == null) {
            lastOpenedDecision = null;
            return;
        }
        boolean same = lastOpenedDecision != null && lastOpenedDecision.type == decision.type
            && lastOpenedDecision.deadline == decision.deadline;
        if (!same && mc.currentScreen == null) {
            lastOpenedDecision = decision;
            mc.displayGuiScreen(new DecisionScreen(gamePos, self, decision));
        }
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
            // our own glow in first person would just fill the screen with particles
            Minecraft mc = Minecraft.getMinecraft();
            if (entity == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) continue;
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
        if (mc.currentScreen instanceof SeatControlScreen || mc.currentScreen instanceof GameSettingsScreen) {
            mc.displayGuiScreen(null);
        }
    }
}
