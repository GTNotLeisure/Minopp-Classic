package com.hfstudio.minopp.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.MinoClient;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardFace;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.CardType;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.gui.HandScreen;
import com.hfstudio.minopp.gui.PlayChoiceScreen;
import com.hfstudio.minopp.gui.SeatControlScreen;
import com.hfstudio.minopp.gui.TurnDeadMan;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.C2SPlayCardPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Mth;
import com.hfstudio.minopp.util.Vec3d;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockMinoTable extends Block {

    public static final List<String> PART_NAMES = Arrays.stream(TablePartType.values())
        .map(TablePartType::getSerializedName)
        .collect(Collectors.toList());

    public BlockMinoTable() {
        super(Material.wood);
        setHardness(2.0F);
        setStepSound(soundTypeWood);
        setBlockName("minopp.mino_table");
        setBlockTextureName("minopp:mino_table");
        setBlockBounds(0, 0, 0, 1, 14.9f / 16f, 1);
        setLightOpacity(0);
    }

    @Override
    public boolean onBlockActivated(World level, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        BlockPos corePos = getCore(level, x, y, z);
        TileEntity blockEntity = level.getTileEntity(corePos.x, corePos.y, corePos.z);

        ItemStack itemStack = player.getHeldItem();
        if (itemStack != null) {
            if (level.isRemote && itemStack.getItem() == Mino.ITEM_HAND_CARDS
                && blockEntity instanceof BlockEntityMinoTable tableEntity
                && tableEntity.game != null) {
                BlockPos tablePos = ItemDataUtils.getBlockPos(itemStack);
                if (tablePos == null || !tablePos.equals(corePos)) {
                    Client.displayActionBar(
                        Chat.translatable("game.minopp.play.no_player")
                            .getFormattedText());
                    return false;
                }
                TurnDeadMan.pedal();
                CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
                CardPlayer realPlayer = tableEntity.game.deAmputate(playerWithoutHand);
                if (realPlayer == null || realPlayer.hand.isEmpty()) return false;
                if (Client.isCursorHittingPile()) {
                    C2SPlayCardPacket.Client.sendPlayNoCardC2S(corePos, playerWithoutHand);
                } else {
                    List<Card> hand = ItemHandCards.sortedHand(tableEntity.game, realPlayer);
                    Card selectedCard = hand.get(Mth.clamp(ItemDataUtils.getHandIndex(itemStack), 0, hand.size() - 1));
                    Client.playCard(corePos, tableEntity, realPlayer, selectedCard);
                }
                return true;
            }
        } else if (blockEntity instanceof BlockEntityMinoTable tableEntity) {
            CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
            if (tableEntity.demo) {
                if (level.isRemote) Client.displayActionBar(
                    Chat.translatable("game.minopp.play.table_in_demo")
                        .getFormattedText());
                return false;
            }
            if (level.isRemote) {
                Client.openSeatControlScreen(corePos);
                return true;
            }
            if (tableEntity.game == null && !player.isSneaking()) {
                tableEntity.joinPlayerToTable(cardPlayer, new Vec3d(player.posX, player.posY, player.posZ));
                return true;
            }
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    public static class Client {

        public static void displayActionBar(String text) {
            Minecraft.getMinecraft().ingameGUI.func_110326_a(text, false);
            HandScreen.notice(text);
        }

        public static void playCard(BlockPos corePos, BlockEntityMinoTable tableEntity, CardPlayer realPlayer,
            Card card) {
            CardGame game = tableEntity.game;
            CardFace face = game.face(card);
            CardType type = face.type();
            boolean useFlex = isFlexModifierHeld() && tableEntity.config.getBool(GameRules.FLEX_POWER)
                && realPlayer.powerOn
                && (type.hasFlex() || face.flexColor != null);
            String reason = game.whyCannotPlay(realPlayer, card, useFlex);
            if (reason != null) {
                displayActionBar(
                    Chat.translatable(reason)
                        .getFormattedText());
                return;
            }
            PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
            choices.useFlex = useFlex;
            choices.shout = isShoutModifierHeld() && realPlayer.hand.size() <= 2;

            CardType.Choice choice = type.choiceFor(useFlex);
            boolean needsColor = choice.needsColor();
            int targets = choice.targetCount();
            if (tableEntity.config.getBool(GameRules.SEVEN_ZERO) && face.isNumber()
                && face.number == 7
                && game.checkPlayable(realPlayer, card, useFlex) == null
                && game.activePlayers()
                    .size() > 2) {
                targets = Math.max(targets, 1);
            }
            boolean answeringDraw = game.pendingDraw > 0;
            if ((needsColor || targets > 0)
                && (game.checkPlayable(realPlayer, card, useFlex) == null || answeringDraw)) {
                Minecraft.getMinecraft()
                    .displayGuiScreen(
                        new PlayChoiceScreen(corePos, realPlayer, card, choices, needsColor, targets, game.darkSide));
            } else {
                if (targets > 0 && game.activePlayers()
                    .size() == 2) {
                    for (CardPlayer p : game.activePlayers()) if (!p.equals(realPlayer)) choices.targets.add(p.uuid);
                }
                C2SPlayCardPacket.Client.sendPlayCardC2S(corePos, realPlayer, card.uid, choices);
            }
        }

        public static void openSeatControlScreen(BlockPos corePos) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new SeatControlScreen(corePos));
        }

        private static boolean isKeyHeld(int keyCode) {
            return keyCode > 0 && keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
        }

        public static boolean isShoutModifierHeld() {
            return isKeyHeld(MinoClient.KEY_SHOUT_MODIFIER.getKeyCode());
        }

        public static boolean isFlexModifierHeld() {
            return isKeyHeld(MinoClient.KEY_FLEX_MODIFIER.getKeyCode());
        }

        public static BlockPos getCursorPickedGame() {
            Minecraft mc = Minecraft.getMinecraft();
            MovingObjectPosition hitResult = mc.objectMouseOver;
            World level = mc.theWorld;
            if (hitResult != null && level != null
                && hitResult.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (level.getBlock(hitResult.blockX, hitResult.blockY, hitResult.blockZ) == Mino.BLOCK_MINO_TABLE) {
                    return BlockMinoTable.getCore(level, hitResult.blockX, hitResult.blockY, hitResult.blockZ);
                }
            }
            return null;
        }

        public static boolean isCursorHittingPile() {
            BlockPos gamePos = getCursorPickedGame();
            if (gamePos == null) return false;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld
                .getTileEntity(gamePos.x, gamePos.y, gamePos.z) instanceof BlockEntityMinoTable tableEntity) {
                if (tableEntity.game == null) return false;
                AxisAlignedBB pileAabb = getPileAabb(tableEntity).getOffsetBoundingBox(gamePos.x, gamePos.y, gamePos.z);
                EntityLivingBase cameraEntity = mc.renderViewEntity;
                float hitDistance = 20;
                Vec3 rayBegin = cameraEntity.getPosition(1.0f);
                Vec3 rayDir = cameraEntity.getLook(1.0f);
                Vec3 rayEnd = rayBegin
                    .addVector(rayDir.xCoord * hitDistance, rayDir.yCoord * hitDistance, rayDir.zCoord * hitDistance);
                return pileAabb.calculateIntercept(rayBegin, rayEnd) != null || pileAabb.isVecInside(rayBegin);
            }
            return true;
        }

        public static AxisAlignedBB getPileAabb(BlockEntityMinoTable tableEntity) {
            if (tableEntity.game == null) return AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
            float deckHeight = (float) Math.ceil(tableEntity.game.deck.size() / 5f) * (1f / 48f) * 0.4f;
            double cy = 0.94 + deckHeight / 2;
            return AxisAlignedBB
                .getBoundingBox(0.25, cy - deckHeight / 2, 0.25, 0.75, cy + deckHeight / 2 + 0.02, 0.75);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World level, int x, int y, int z) {
        for (TablePartType part : TablePartType.values()) {
            int px = x + part.xOff, pz = z + part.zOff;
            Block existing = level.getBlock(px, y, pz);
            if (!existing.isReplaceable(level, px, y, pz)) return false;
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World level, int x, int y, int z, EntityLivingBase placer, ItemStack itemStack) {
        super.onBlockPlacedBy(level, x, y, z, placer, itemStack);
        if (!level.isRemote) {
            level.setBlockMetadataWithNotify(x, y, z, 0, 2);
            // Place silently first so the integrity check in onNeighborBlockChange doesn't see a half-built table
            for (int i = 1; i < 9; i++) {
                TablePartType thisPart = TablePartType.values()[i];
                level.setBlock(x + thisPart.xOff, y, z + thisPart.zOff, this, i, 2);
            }
            for (TablePartType thisPart : TablePartType.values()) {
                level.notifyBlocksOfNeighborChange(x + thisPart.xOff, y, z + thisPart.zOff, this);
            }
        }
    }

    @Override
    public void onNeighborBlockChange(World level, int x, int y, int z, Block neighbor) {
        BlockPos core = getCore(level, x, y, z);
        for (TablePartType part : TablePartType.values()) {
            if (level.getBlock(core.x + part.xOff, core.y, core.z + part.zOff) != this
                || level.getBlockMetadata(core.x + part.xOff, core.y, core.z + part.zOff) != part.ordinal()) {
                level.setBlockToAir(x, y, z);
                return;
            }
        }
    }

    @Override
    public void onBlockHarvested(World level, int x, int y, int z, int meta, EntityPlayer player) {
        if (!level.isRemote && player.capabilities.isCreativeMode) {
            BlockPos firstPartPos = getCore(level, x, y, z);
            for (TablePartType thisPart : TablePartType.values()) {
                int px = firstPartPos.x + thisPart.xOff, pz = firstPartPos.z + thisPart.zOff;
                if (level.getBlock(px, firstPartPos.y, pz) == this) {
                    level.setBlock(px, firstPartPos.y, pz, Blocks.air, 0, 2);
                }
            }
        }
        super.onBlockHarvested(level, x, y, z, meta, player);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        // Removed parts of a table break silently through neighbour updates, so whichever part is harvested
        // drops the table item.
        ArrayList<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Item.getItemFromBlock(this)));
        return drops;
    }

    @Override
    public int damageDropped(int meta) {
        return 0;
    }

    public static BlockPos getCore(IBlockAccess level, int x, int y, int z) {
        if (level.getBlock(x, y, z) != Mino.BLOCK_MINO_TABLE) return new BlockPos(x, y, z);
        int meta = level.getBlockMetadata(x, y, z);
        TablePartType part = TablePartType.values()[Math.min(meta, 8)];
        return new BlockPos(x - part.xOff, y, z - part.zOff);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return metadata == 0;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        if (metadata != 0) return null;
        return new BlockEntityMinoTable();
    }

    public enum TablePartType {

        X_LESS_Z_LESS(0, 0),
        X_LESS_Z_MID(0, 1),
        X_LESS_Z_MORE(0, 2),
        X_MID_Z_LESS(1, 0),
        X_MID_Z_MID(1, 1),
        X_MID_Z_MORE(1, 2),
        X_MORE_Z_LESS(2, 0),
        X_MORE_Z_MID(2, 1),
        X_MORE_Z_MORE(2, 2);

        public final int xOff;
        public final int zOff;

        TablePartType(int xOff, int zOff) {
            this.xOff = xOff;
            this.zOff = zOff;
        }

        public String getSerializedName() {
            return this.name()
                .toLowerCase();
        }
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        // Rendered by GTNHLib's ModelISBRH from assets/minopp/blockstates/mino_table.json
        return 0;
    }

    @Override
    public boolean isNormalCube(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getAmbientOcclusionLightValue() {
        return 1.0F;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon("minecraft:planks_spruce");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return "minopp:mino_table";
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return true;
    }
}
