package com.hfstudio.minopp.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.BlockPos;

public class ItemHandCards extends Item {

    public ItemHandCards() {
        setMaxStackSize(1);
        setUnlocalizedName("minopp.hand_cards");
        setTextureName("minopp:hand_cards");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World level, EntityPlayer player) {
        BlockPos gamePos = getHandCardGamePos(player);
        if (gamePos != null && level.getTileEntity(gamePos.x, gamePos.y, gamePos.z) instanceof BlockEntityMinoTable tableEntity) {
            if (tableEntity.game != null && tableEntity.getPlayersList().stream()
                .anyMatch(p -> p.uuid.equals(player.getGameProfile().getId()))) {
                // Card is valid
                return stack;
            }
        }
        // Game table not found, card no longer usable
        stack.stackSize = 0;
        return null;
    }

    public static CardPlayer getCardPlayer(EntityPlayer player) {
        return new CardPlayer(player);
    }

    public static BlockPos getHandCardGamePos(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != Mino.ITEM_HAND_CARDS) return null;
        return getGamePos(player.worldObj, held);
    }

    public static BlockPos getGamePos(World world, ItemStack stack) {
        BlockPos tablePos = ItemDataUtils.getBlockPos(stack);
        if (tablePos == null) return null;
        if (world.getBlock(tablePos.x, tablePos.y, tablePos.z) != Mino.BLOCK_MINO_TABLE) return null;
        return BlockMinoTable.getCore(world, tablePos.x, tablePos.y, tablePos.z);
    }

    public static BlockEntityMinoTable getTable(World world, BlockPos pos) {
        if (pos == null) return null;
        TileEntity te = world.getTileEntity(pos.x, pos.y, pos.z);
        return te instanceof BlockEntityMinoTable ? (BlockEntityMinoTable) te : null;
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        BlockPos tablePos = ItemDataUtils.getBlockPos(stack);
        UUID bearerId = ItemDataUtils.getBearerId(stack);
        if (tablePos != null) {
            tooltip.add("Table: " + tablePos.toShortString());
        }
        if (bearerId != null && player != null && !bearerId.equals(player.getGameProfile().getId())) {
            tooltip.add(EnumChatFormatting.RED + "NOT YOUR CARD!");
        }
        super.addInformation(stack, player, tooltip, advanced);
    }
}
