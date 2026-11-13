package com.hfstudio.minopp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.GameControls;
import com.hfstudio.minopp.gui.GameOverlayLayer;
import com.hfstudio.minopp.gui.HandScreen;
import com.hfstudio.minopp.gui.PlayerPicker;
import com.hfstudio.minopp.gui.TurnDeadMan;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.BlockEntityMinoTableRenderer;
import com.hfstudio.minopp.render.ColorPicker3D;
import com.hfstudio.minopp.render.EntityAutoPlayerRenderer;
import com.hfstudio.minopp.render.HandCardsItemRenderer;
import com.hfstudio.minopp.render.TableFx;
import com.hfstudio.minopp.render.TableItemRenderer;
import com.hfstudio.minopp.render.TurnIndicator;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Mth;
import com.hfstudio.minopp.util.PlayerIdentity;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModelRegistry.registerModid(Mino.MOD_ID);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinoClient.init();
        ClientRegistry.bindTileEntitySpecialRenderer(BlockEntityMinoTable.class, new BlockEntityMinoTableRenderer());
        RenderingRegistry.registerEntityRenderingHandler(EntityAutoPlayer.class, new EntityAutoPlayerRenderer());
        TableItemRenderer.keepRegistered();
        MinecraftForgeClient.registerItemRenderer(Mino.ITEM_HAND_CARDS, new HandCardsItemRenderer());

        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    public static class ClientEvents {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            HandScreen.beginFrame();
            ColorPicker3D.beginFrame();
            BlockEntityMinoTableRenderer.beginFrame();
        }

        @SubscribeEvent
        public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
            if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR && HandScreen.isOpen()
                && !ClientConfig.display.classicInteraction) event.setCanceled(true);
        }

        @SubscribeEvent
        public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
            if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
            GameOverlayLayer.INSTANCE.render(event.partialTicks);
        }

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            TurnIndicator.renderPlayerMarkers(event.partialTicks);
        }

        @SubscribeEvent
        public void onFovUpdate(FOVUpdateEvent event) {
            event.newfov = event.newfov * (float) MinoClient.globalFovModifier;
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                HandScreen.beginFrame();
                MinoClient.tick();
                TableFx.tick();
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (event.world.isRemote) {
                MinoClient.SOUND_QUEUE.clear();
                PlayerPicker.clearSkinCache();
                TableFx.clear(event.world);
                GameOverlayLayer.INSTANCE.clearHand();
                HandScreen.beginFrame();
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onMouse(MouseEvent event) {
            if (event.button >= 0 && event.buttonstate
                && Minecraft.getMinecraft().currentScreen == null
                && GameControls.handle(event.button - 100)) {
                event.setCanceled(true);
                return;
            }
            if (event.button == 1 && event.buttonstate) {
                onRightClick(event);
                return;
            }
            if (event.dwheel == 0) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null) return;
            EntityClientPlayerMP player = mc.thePlayer;
            if (player == null) return;
            ItemStack holding = player.getHeldItem();
            if (holding == null || holding.getItem() != Mino.ITEM_HAND_CARDS) return;
            BlockPos handCardGamePos = ItemHandCards.getHandCardGamePos(player);
            if (handCardGamePos == null) return;
            BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, handCardGamePos);
            if (tableEntity == null || tableEntity.game == null) return;

            CardPlayer realPlayer = tableEntity.game.deAmputate(PlayerIdentity.of(player));
            if (realPlayer == null) return;

            int handIndex = ItemDataUtils.getHandIndex(holding);
            ItemDataUtils.setHandIndex(
                holding,
                Mth.clamp(handIndex - (int) Math.signum(event.dwheel), 0, realPlayer.hand.size() - 1));

            TurnDeadMan.pedal();
            event.setCanceled(true);
        }

        private void onRightClick(MouseEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null || HandScreen.Context.resolve(mc) == null) return;
            if (ClientConfig.display.classicInteraction && BlockMinoTable.Client.getCursorPickedGame() != null) return;
            mc.displayGuiScreen(new HandScreen());
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != null || !mc.inGameHasFocus || !Keyboard.getEventKeyState()) return;
            int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
            if (GameControls.handle(key)) {
                KeyBinding.setKeyBindState(key, false);
                for (KeyBinding binding : mc.gameSettings.keyBindings) if (binding.getKeyCode() == key) {
                    while (binding.isPressed()) {
                        // Consume queued vanilla actions only when the game takes this input.
                    }
                }
            }
        }
    }
}
