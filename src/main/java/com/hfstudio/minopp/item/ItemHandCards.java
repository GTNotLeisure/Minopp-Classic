package com.hfstudio.minopp.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.PlayerIdentity;

public class ItemHandCards extends Item {

    public ItemHandCards() {
        setMaxStackSize(1);
        setUnlocalizedName("minopp.hand_cards");
        setTextureName("minopp:hand_cards");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World level, EntityPlayer player) {
        if (!level.isRemote) {
            HandCardAccess.Status status = HandCardAccess.status(level, stack);
            if (status == HandCardAccess.Status.EXPIRED) stack.stackSize = 0;
            else if (status != HandCardAccess.Status.VALID)
                player.addChatComponentMessage(Chat.translatable("game.minopp.play.hand_return_to_table"));
        }
        return stack;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity holder, int slot, boolean held) {
        if (!world.isRemote && HandCardAccess.status(world, stack) == HandCardAccess.Status.EXPIRED) {
            stack.stackSize = 0;
            if (holder instanceof EntityPlayer player && player.inventory.getStackInSlot(slot) == stack) {
                player.inventory.setInventorySlotContents(slot, null);
                player.inventory.markDirty();
            }
        }
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entity) {
        if (!entity.worldObj.isRemote
            && HandCardAccess.status(entity.worldObj, entity.getEntityItem()) == HandCardAccess.Status.EXPIRED) {
            entity.setDead();
            return true;
        }
        return false;
    }

    public static CardPlayer getCardPlayer(EntityPlayer player) {
        return new CardPlayer(player);
    }

    public static BlockPos getHandCardGamePos(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != Mino.ITEM_HAND_CARDS) return null;
        if (!PlayerIdentity.of(player)
            .equals(ItemDataUtils.getBearerId(held))) return null;
        return getGamePos(player.worldObj, held);
    }

    public static BlockPos getGamePos(World world, ItemStack stack) {
        return HandCardAccess.status(world, stack) == HandCardAccess.Status.VALID ? ItemDataUtils.getBlockPos(stack)
            : null;
    }

    /**
     * True when the chunk holding pos is loaded; nothing in the mod may load one just to look at a table.
     */
    public static boolean isLoaded(World world, BlockPos pos) {
        return pos != null && world.blockExists(pos.x, pos.y, pos.z);
    }

    public static BlockEntityMinoTable getTable(World world, BlockPos pos) {
        if (!isLoaded(world, pos)) return null;
        TileEntity te = world.getTileEntity(pos.x, pos.y, pos.z);
        return te instanceof BlockEntityMinoTable ? (BlockEntityMinoTable) te : null;
    }

    public static List<Card> sortedHand(CardGame game, CardPlayer player) {
        List<Card> hand = new ArrayList<>(player.hand);
        hand.sort((a, b) -> a.compareTo(b, game.darkSide));
        return hand;
    }

    public static int getClientHandIndex(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() == Mino.ITEM_HAND_CARDS) {
            return ItemDataUtils.getHandIndex(held);
        } else {
            return 0;
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        BlockPos tablePos = ItemDataUtils.getBlockPos(stack);
        UUID bearerId = ItemDataUtils.getBearerId(stack);
        if (tablePos != null) {
            tooltip.add("Table: " + tablePos.toShortString());
        }
        if (bearerId != null && player != null && !bearerId.equals(PlayerIdentity.of(player))) {
            tooltip.add(EnumChatFormatting.RED + "NOT YOUR CARD!");
        }
        super.addInformation(stack, player, tooltip, advanced);
    }
}
