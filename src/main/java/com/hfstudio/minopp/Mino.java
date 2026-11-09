package com.hfstudio.minopp;

import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizon.gtnhlib.blockstate.properties.IntegerBlockProperty;
import com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.block.ItemMinoTable;
import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.config.ServerConfig;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.TaskScheduler;
import com.hfstudio.minopp.item.ItemCoupon;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.network.MinoNetwork;
import com.hfstudio.minopp.network.ThemeSyncPackets;
import com.hfstudio.minopp.util.BlockPos;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(
    modid = Mino.MOD_ID,
    name = Mino.MOD_NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gtnhlib",
    guiFactory = "com.hfstudio.minopp.config.MinoGuiFactory",
    acceptedMinecraftVersions = "[1.7.10]")
public final class Mino {

    public static final String MOD_ID = "minopp";
    public static final String MOD_NAME = "Mino++: Classic";
    public static final Logger LOGGER = LogManager.getLogger("Mino++");

    @Mod.Instance(MOD_ID)
    public static Mino instance;

    @SidedProxy(clientSide = "com.hfstudio.minopp.ClientProxy", serverSide = "com.hfstudio.minopp.CommonProxy")
    public static CommonProxy proxy;

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static Block BLOCK_MINO_TABLE;
    public static Item ITEM_HAND_CARDS;
    public static Item ITEM_COUPON;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            ClientConfig.registerConfig();
            ServerConfig.registerConfig();
        } catch (Exception e) {
            LOGGER.error("Failed to register config", e);
        }

        CardPack.init();

        BLOCK_MINO_TABLE = new BlockMinoTable().setCreativeTab(MinoCreativeTab.INSTANCE);
        GameRegistry.registerBlock(BLOCK_MINO_TABLE, ItemMinoTable.class, "mino_table");
        GameRegistry.registerTileEntity(BlockEntityMinoTable.class, "minopp:mino_table");
        BlockPropertyRegistry.registerProperty(
            BLOCK_MINO_TABLE,
            // Multipart conditions are compared against stringify(): keep the plain value (the default JSON-encodes
            // strings, i.e. adds quotes, which would never match "x_less_z_less" in the blockstate file)
            new IntegerBlockProperty.MappedBlockProperty(IntegerBlockProperty.meta("part"), BlockMinoTable.PART_NAMES) {

                @Override
                public String stringify(String value) {
                    return value;
                }

                @Override
                public String parse(String text) {
                    return text;
                }
            });

        ITEM_HAND_CARDS = new ItemHandCards();
        GameRegistry.registerItem(ITEM_HAND_CARDS, "hand_cards");
        ITEM_COUPON = new ItemCoupon();
        ITEM_COUPON.setCreativeTab(MinoCreativeTab.INSTANCE);
        GameRegistry.registerItem(ITEM_COUPON, "coupon");

        EntityRegistry.registerModEntity(EntityAutoPlayer.class, "mino_auto_player", 0, this, 64, 3, true);

        MinoNetwork.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(BLOCK_MINO_TABLE),
                "_G_",
                " L ",
                '_',
                new ItemStack(Blocks.stone_slab, 1, 0),
                'G',
                new ItemStack(Blocks.wool, 1, 13),
                'L',
                "logWood"));

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(TaskScheduler.Holder.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(new MinoNetwork.ServerTicker());
        FMLCommonHandler.instance()
            .bus()
            .register(new ThemeSyncPackets.ServerEvents());
        if (DevPreview.enabled()) FMLCommonHandler.instance()
            .bus()
            .register(new DevPreview());
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new MinoCommand());
    }

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        EntityPlayerMP player = event.player;
        if (!player.worldObj.getChunkProvider()
            .chunkExists(event.chunk.chunkXPos, event.chunk.chunkZPos)) return;
        Chunk chunk = player.worldObj.getChunkFromChunkCoords(event.chunk.chunkXPos, event.chunk.chunkZPos);
        for (Object te : chunk.chunkTileEntityMap.values()) {
            if (te instanceof BlockEntityMinoTable table && table.game != null && !table.peekAll())
                table.sendTo(player);
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        onServerChatMessage(event.message, event.player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(AttackEntityEvent event) {
        onPlayerAttackEntity(event.target, event.entityPlayer);
    }

    public static boolean onServerChatMessage(String rawText, EntityPlayerMP sender) {
        String normalized = rawText.toLowerCase()
            .replace(" ", "")
            .replace("!", "")
            .replace("！", "");
        if (normalized.equals("mino") || normalized.equals("uno") || normalized.equals("minopp")) {
            BlockPos gamePos = ItemHandCards.getHandCardGamePos(sender);
            if (gamePos == null) return false;
            BlockEntityMinoTable tableEntity = ItemHandCards.getTable(sender.worldObj, gamePos);
            if (tableEntity != null) {
                if (tableEntity.game == null) return false;
                CardPlayer cardPlayer = tableEntity.game.deAmputate(ItemHandCards.getCardPlayer(sender));
                if (cardPlayer == null) return false;
                ActionReport result = tableEntity.game.shoutMino(cardPlayer);
                tableEntity.handleActionResult(result, cardPlayer, sender);
                return true;
            }
        }
        return false;
    }

    public static void onPlayerAttackEntity(Entity targetMaybePlayer, EntityPlayer srcPlayer) {
        if (!srcPlayer.worldObj.isRemote) return;
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(srcPlayer);
        if (gamePos == null) return;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(srcPlayer.worldObj, gamePos);
        if (tableEntity != null) {
            if (tableEntity.game == null) return;
            UUID targetId;
            if (targetMaybePlayer instanceof EntityPlayer targetPlayer) {
                targetId = ItemHandCards.getCardPlayer(targetPlayer).uuid;
            } else {
                targetId = targetMaybePlayer.getUniqueID();
            }
            C2SPlayCardPacket.Client.sendDoubtMinoC2S(gamePos, ItemHandCards.getCardPlayer(srcPlayer), targetId);
        }
    }
}
