package com.hfstudio.minopp.render;

import java.lang.ref.WeakReference;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.Teams;
import com.hfstudio.minopp.game.TurnTransition;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.PlayerIdentity;
import com.hfstudio.minopp.util.Vec3d;
import com.hfstudio.minopp.util.WorldUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TurnIndicator {

    private static final long NOTICE_MS = 2200;
    private static final long BOUNCE_PERIOD_MS = 1800;
    private static final long SPIN_PERIOD_MS = 6000;
    private static final Map<BlockEntityMinoTable, State> STATES = new WeakHashMap<>();
    private static final Map<UUID, WeakReference<Entity>> ENTITIES = new HashMap<>();
    private static final Map<UUID, Long> RETRY_AFTER = new HashMap<>();
    private static final CameraFrame CAMERA = new CameraFrame();

    private TurnIndicator() {}

    private static class CameraFrame {

        private final FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer facingBuffer = BufferUtils.createFloatBuffer(16);
        private final Matrix4f view = new Matrix4f();
        private final Matrix4f inverse = new Matrix4f();

        private boolean capture() {
            viewBuffer.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, viewBuffer);
            view.load(viewBuffer);
            if (Matrix4f.invert(view, inverse) == null) return false;
            // Preserve the actual camera orientation, including front view, roll and freecam.
            inverse.m30 = inverse.m31 = inverse.m32 = 0;
            facingBuffer.clear();
            inverse.store(facingBuffer);
            facingBuffer.flip();
            return true;
        }
    }

    private static class State {

        private final long gameId;
        private int serial;
        private UUID playerId;
        private int seat;
        private long observedAt;
        private long arrivedAt = -1;
        private boolean ready;
        private TurnTransition fallback;

        private State(CardGame game, long now) {
            gameId = game.id;
            serial = game.turnSerial;
            playerId = game.currentPlayer().uuid;
            seat = game.currentPlayer().seat;
            observedAt = now;
        }
    }

    public static void clear(World world) {
        ENTITIES.clear();
        RETRY_AFTER.clear();
        STATES.keySet()
            .removeIf(table -> table.getWorldObj() == world);
    }

    public static void tick() {
        World world = Minecraft.getMinecraft().theWorld;
        ENTITIES.values()
            .removeIf(reference -> {
                Entity entity = reference.get();
                return entity == null || entity.isDead || entity.worldObj != world;
            });
        RETRY_AFTER.values()
            .removeIf(until -> until <= System.currentTimeMillis());
        STATES.entrySet()
            .removeIf(entry -> {
                BlockEntityMinoTable table = entry.getKey();
                return world == null || table.getWorldObj() != world
                    || table.isInvalid()
                    || table.game == null
                    || table.game.id != entry.getValue().gameId;
            });
    }

    private static State observe(BlockEntityMinoTable table, CardGame game, long now) {
        State state = STATES.get(table);
        if (state == null || state.gameId != game.id) {
            state = new State(game, now);
            STATES.put(table, state);
        } else if (state.serial != game.turnSerial || !state.playerId.equals(game.currentPlayer().uuid)) {
            int previousSeat = state.seat;
            state.serial = game.turnSerial;
            state.playerId = game.currentPlayer().uuid;
            state.seat = game.currentPlayer().seat;
            state.observedAt = now;
            state.arrivedAt = -1;
            state.ready = false;
            state.fallback = new TurnTransition(
                previousSeat,
                state.seat,
                game.isAntiClockwise,
                TurnTransition.DURATION_TICKS);
        }
        boolean ready = !game.isTurnTransitioning() && !game.awaitingCutIn && game.decisions.isEmpty();
        if (ready && !state.ready) state.arrivedAt = now;
        if (!ready) state.arrivedAt = -1;
        state.ready = ready;
        return state;
    }

    private static TurnTransition motion(CardGame game, State state) {
        TurnTransition transition = game.turnTransition();
        return transition != null && transition.toSeat() == state.seat ? transition : state.fallback;
    }

    private static float progress(CardGame game, State state, long now) {
        TurnTransition transition = game.turnTransition();
        if (transition != null && transition.toSeat() == state.seat) return game.turnTransitionProgress();
        return state.fallback == null ? 1
            : Math.min(1, (now - state.observedAt) / (TurnTransition.DURATION_TICKS * 50f));
    }

    private static boolean ownTurn(CardPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && player.uuid.equals(PlayerIdentity.of(mc.thePlayer));
    }

    private static int turnColor(CardGame game) {
        CardPlayer player = game.currentPlayer();
        return Teams.enabled(game.config()) && player.team >= 0 ? Teams.rgb(player.team) : 0xFFD85A;
    }

    private static String label(CardGame game) {
        CardPlayer player = game.currentPlayer();
        String key = game.isTurnTransitioning() ? "gui.minopp.play.turn_handoff"
            : ownTurn(player) ? "gui.minopp.play.turn_yours" : "gui.minopp.play.turn_named";
        return Chat.translatable(key, player.name)
            .getFormattedText();
    }

    public static void drawHud(BlockEntityMinoTable table, int centerX, int top, int maxWidth) {
        Minecraft mc = Minecraft.getMinecraft();
        if (table == null || table.game == null || mc.gameSettings.hideGUI || maxWidth < 40) return;
        CardGame game = table.game;
        long now = System.currentTimeMillis();
        State state = observe(table, game, now);
        if (!game.decisions.isEmpty() || game.awaitingCutIn || game.currentPlayer().eliminated) return;
        long elapsed = state.arrivedAt < 0 ? 0 : now - state.arrivedAt;
        if (!game.isTurnTransitioning() && elapsed >= NOTICE_MS) return;
        float fade = Math.max(0, Math.min(1, (NOTICE_MS - elapsed) / 350f));
        int alpha = (int) (255 * fade);
        int rgb = turnColor(game);
        String text = label(game);
        UiDraw.centeredHint(mc.fontRenderer, text, centerX, top, maxWidth, alpha << 24 | rgb);
    }

    private static Entity entity(World world, UUID playerId) {
        WeakReference<Entity> reference = ENTITIES.get(playerId);
        Entity entity = reference == null ? null : reference.get();
        if (entity != null && !entity.isDead && entity.worldObj == world) return entity;
        long now = System.currentTimeMillis();
        if (RETRY_AFTER.getOrDefault(playerId, 0L) > now) return null;
        entity = findEntity(world, playerId);
        if (entity == null || entity.isDead) {
            RETRY_AFTER.put(playerId, now + 500);
            return null;
        }
        RETRY_AFTER.remove(playerId);
        ENTITIES.put(playerId, new WeakReference<>(entity));
        return entity;
    }

    private static Entity findEntity(World world, UUID playerId) {
        EntityPlayer player = WorldUtil.getPlayerByUUID(world, playerId);
        if (player != null) return player;
        for (Entity candidate : world.loadedEntityList) {
            if (candidate.isDead || candidate instanceof EntityPlayer) continue;
            if (playerId.equals(candidate.getUniqueID())) return candidate;
            // Mob spawn packets do not synchronize UUIDs; hand bindings carry the server identity.
            if (candidate instanceof EntityLivingBase living) {
                ItemStack held = living.getHeldItem();
                if (held != null && held.getItem() == Mino.ITEM_HAND_CARDS
                    && playerId.equals(ItemDataUtils.getBearerId(held))) return candidate;
            }
        }
        return null;
    }

    private static Vec3d position(BlockEntityMinoTable table, Entity entity, float partialTicks) {
        return new Vec3d(
            entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - table.xCoord,
            entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - entity.yOffset - table.yCoord,
            entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - table.zCoord);
    }

    private static Vec3d playerPosition(BlockEntityMinoTable table, CardGame game, int seat, float partialTicks) {
        for (CardPlayer player : game.players) {
            if (player.seat != seat) continue;
            Entity entity = entity(table.getWorldObj(), player.uuid);
            if (entity != null) return position(table, entity, partialTicks);
            break;
        }
        return table.getSeatLocalPos(seat);
    }

    public static void renderWorld(BlockEntityMinoTable table, CardGame game, float partialTicks) {
        CardPlayer current = game.currentPlayer();
        if (current.eliminated || current.seat < 0) return;
        long now = System.currentTimeMillis();
        State state = observe(table, game, now);
        TurnTransition transition = motion(game, state);
        float raw = progress(game, state, now);
        float eased = raw * raw * (3 - 2 * raw);
        Vec3d destination = playerPosition(table, game, current.seat, partialTicks);
        double toAngle = Math.atan2(destination.x - 1.5, 1.5 - destination.z);
        double angle = toAngle;
        double delta = 0;
        if (transition != null && transition.fromSeat() >= 0 && raw < 1) {
            Vec3d source = playerPosition(table, game, transition.fromSeat(), partialTicks);
            double fromAngle = Math.atan2(source.x - 1.5, 1.5 - source.z);
            int direction = transition.antiClockwise() ? -1 : 1;
            double distance = (toAngle - fromAngle) * direction;
            distance -= Math.floor(distance / (Math.PI * 2)) * Math.PI * 2;
            if (distance < 0.001) distance = Math.PI * 2;
            delta = distance * direction;
            angle = fromAngle + delta * eased;
        }
        int color = turnColor(game);
        float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        try {
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            if (raw < 1 && ClientConfig.display.tableEffects) trail(tess, angle, delta, color);
            tess.draw();
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(1.5 + Math.sin(angle) * 1.46, 1.08, 1.5 - Math.cos(angle) * 1.46);
                GL11.glRotated(-Math.toDegrees(angle), 0, 1, 0);
                GL11.glRotatef(90, 1, 0, 0);
                GL11.glScaled(1.7, 1.7, 0.65);
                TurnArrowRenderer.draw(color);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
            GL11.glPopAttrib();
        }
    }

    private static void color(Tessellator tess, int rgb, float alpha) {
        tess.setColorRGBA_F((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
    }

    private static void vertex(Tessellator tess, double angle, double radius, double side) {
        tess.addVertex(
            1.5 + Math.sin(angle) * radius + Math.cos(angle) * side,
            1.035,
            1.5 - Math.cos(angle) * radius + Math.sin(angle) * side);
    }

    private static void trail(Tessellator tess, double angle, double delta, int rgb) {
        double length = Math.copySign(Math.min(Math.abs(delta), 1.2), delta);
        for (int i = 0; i < 20; i++) {
            double a = angle - length + length * i / 20, b = a + length / 20;
            color(tess, rgb, i / 20f * 0.5f);
            vertex(tess, a, 1.15, 0);
            vertex(tess, b, 1.15, 0);
            vertex(tess, b, 1.23, 0);
            vertex(tess, a, 1.23, 0);
        }
    }

    public static void renderPlayerMarkers(float partialTicks) {
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null || STATES.isEmpty() || !CAMERA.capture()) return;
        long now = System.currentTimeMillis();
        float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        try {
            for (BlockEntityMinoTable table : STATES.keySet()) {
                CardGame game = table.game;
                if (table.getWorldObj() != world || table.isInvalid()
                    || game == null
                    || game.currentPlayer().eliminated) continue;
                Entity player = entity(world, game.currentPlayer().uuid);
                if (player == null || !player.isEntityAlive()) continue;
                State state = observe(table, game, now);
                Vec3d at = position(table, player, partialTicks);
                long age = now - state.observedAt;
                double bounce = 0.12 * (1 + Math.cos(age * Math.PI * 2 / BOUNCE_PERIOD_MS));
                at = at.add(0, player.height + 0.3 + bounce, 0);
                int rgb = turnColor(game);
                GL11.glPushMatrix();
                GL11.glTranslated(
                    table.xCoord - RenderManager.renderPosX,
                    table.yCoord - RenderManager.renderPosY,
                    table.zCoord - RenderManager.renderPosZ);
                headArrow(at, 25 + (age % SPIN_PERIOD_MS) * 360d / SPIN_PERIOD_MS, rgb);
                float flash = state.arrivedAt < 0 ? 0 : Math.max(0, 1 - (now - state.arrivedAt) / 1000f);
                if (flash > 0) worldLabel(label(game), at.add(0, 0.8, 0), rgb, flash);
                GL11.glPopMatrix();
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
            GL11.glPopAttrib();
        }
    }

    private static void headArrow(Vec3d at, double yaw, int rgb) {
        GL11.glPushMatrix();
        GL11.glTranslated(at.x, at.y, at.z);
        GL11.glRotated(yaw, 0, 1, 0);
        TurnArrowRenderer.draw(rgb);
        GL11.glPopMatrix();
    }

    private static void worldLabel(String text, Vec3d at, int rgb, float alpha) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        float scale = Math.min(0.018f, 1.8f / Math.max(1, font.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslated(at.x, at.y, at.z);
        GL11.glMultMatrix(CAMERA.facingBuffer);
        GL11.glScalef(scale, -scale, scale);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        UiDraw.drawStringWithBackdrop(font, text, -font.getStringWidth(text) / 2, 0, (int) (255 * alpha) << 24 | rgb);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}
