package com.hfstudio.minopp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.gui.GameOverlayLayer;
import com.hfstudio.minopp.gui.TurnDeadMan;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.render.BlockEntityMinoTableRenderer;
import com.hfstudio.minopp.render.EntityAutoPlayerRenderer;
import com.hfstudio.minopp.render.HandCardsItemRenderer;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Mth;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Lets GTNHLib pick up assets/minopp/blockstates + models for the table
        ModelRegistry.registerModid(Mino.MOD_ID);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinoClient.init();
        ClientRegistry.bindTileEntitySpecialRenderer(BlockEntityMinoTable.class, new BlockEntityMinoTableRenderer());
        RenderingRegistry.registerEntityRenderingHandler(EntityAutoPlayer.class, new EntityAutoPlayerRenderer());
        MinecraftForgeClient.registerItemRenderer(Mino.ITEM_HAND_CARDS, new HandCardsItemRenderer());

        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    public static class ClientEvents {

        @SubscribeEvent
        public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
            if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
            GameOverlayLayer.INSTANCE.render(event.partialTicks);
        }

        @SubscribeEvent
        public void onFovUpdate(FOVUpdateEvent event) {
            event.newfov = event.newfov * (float) MinoClient.globalFovModifier;
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) MinoClient.tick();
        }

        /** Replaces the Inventory#swapPaint mixin: scrolling selects a card instead of a hotbar slot. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onMouse(MouseEvent event) {
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

            CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
            CardPlayer realPlayer = tableEntity.game.players.stream()
                .filter(p -> p.equals(playerWithoutHand))
                .findFirst()
                .orElse(null);
            if (realPlayer == null) return;

            int handIndex = ItemDataUtils.getHandIndex(holding);
            ItemDataUtils.setHandIndex(
                holding,
                Mth.clamp(handIndex - (int) Math.signum(event.dwheel), 0, realPlayer.hand.size() - 1));

            TurnDeadMan.pedal();
            event.setCanceled(true);
        }
    }
}
