package com.hfstudio.minopp.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;

public class HandCardAccess {

    private long gameId;
    private final Map<UUID, UUID> tokens = new HashMap<>();

    public enum Status {
        VALID,
        UNAVAILABLE,
        LEGACY,
        EXPIRED
    }

    public static boolean inReach(EntityPlayer player, BlockPos table) {
        return table != null && player.getDistanceSq(table.x + 1.5, table.y + 0.5, table.z + 1.5) <= 16 * 16;
    }

    public static Status status(World world, ItemStack stack) {
        if (world == null) return Status.UNAVAILABLE;
        HandCardBinding binding = ItemDataUtils.getBinding(stack);
        if (binding == null) return ItemDataUtils.getBlockPos(stack) == null ? Status.EXPIRED : Status.LEGACY;
        if (binding.dimension() != world.provider.dimensionId || !ItemHandCards.isLoaded(world, binding.table()))
            return Status.UNAVAILABLE;
        BlockEntityMinoTable table = ItemHandCards.getTable(world, binding.table());
        return table != null && table.handCardAccess()
            .matches(table, binding) ? Status.VALID : Status.EXPIRED;
    }

    public boolean matches(BlockEntityMinoTable table, HandCardBinding binding) {
        if (binding == null || table.game == null
            || table.getWorldObj() == null
            || binding.dimension() != table.getWorldObj().provider.dimensionId
            || !binding.table()
                .equals(table.getBlockPos())
            || binding.gameId() != table.game.id) return false;
        // Clients check the binding; only the server holds and verifies access tokens.
        if (!table.getWorldObj().isRemote && (gameId != table.game.id || !binding.token()
            .equals(tokens.get(binding.bearer())))) return false;
        CardPlayer player = table.game.deAmputate(binding.bearer());
        return player != null && !player.eliminated;
    }

    public ItemStack create(BlockEntityMinoTable table, UUID bearer) {
        if (table.getWorldObj() == null || table.getWorldObj().isRemote)
            throw new IllegalStateException("Access items must be issued on the server");
        CardPlayer player = table.game == null ? null : table.game.deAmputate(bearer);
        if (player == null || player.eliminated)
            throw new IllegalArgumentException("No active seat for the access item");
        if (gameId != table.game.id) {
            tokens.clear();
            gameId = table.game.id;
        }
        UUID token = UUID.randomUUID();
        tokens.put(bearer, token);
        ItemStack stack = new ItemStack(Mino.ITEM_HAND_CARDS);
        ItemDataUtils.setCardGameBinding(
            stack,
            new HandCardBinding(table.getWorldObj().provider.dimensionId, table.getBlockPos(), gameId, bearer, token));
        table.markDirty();
        return stack;
    }

    private static boolean belongsTo(ItemStack stack, BlockEntityMinoTable table, UUID bearer) {
        if (stack == null || stack.stackSize <= 0
            || stack.getItem() != Mino.ITEM_HAND_CARDS
            || !bearer.equals(ItemDataUtils.getBearerId(stack))
            || !table.getBlockPos()
                .equals(ItemDataUtils.getBlockPos(stack)))
            return false;
        HandCardBinding binding = ItemDataUtils.getBinding(stack);
        return binding == null || binding.dimension() == table.getWorldObj().provider.dimensionId;
    }

    public boolean hasItem(BlockEntityMinoTable table, EntityPlayer player) {
        UUID bearer = player.getGameProfile()
            .getId();
        for (ItemStack stack : player.inventory.mainInventory) {
            if (belongsTo(stack, table, bearer) && matches(table, ItemDataUtils.getBinding(stack))) return true;
        }
        ItemStack cursor = player.inventory.getItemStack();
        return belongsTo(cursor, table, bearer) && matches(table, ItemDataUtils.getBinding(cursor));
    }

    /**
     * Reuses one valid item or replaces missing access. A full inventory never spills a replacement into the world.
     */
    public boolean recover(BlockEntityMinoTable table, EntityPlayer player, boolean requested) {
        World world = table.getWorldObj();
        if (world == null || world.isRemote
            || player.worldObj != world
            || !player.isEntityAlive()
            || !inReach(player, table.getBlockPos())
            || table.game == null) return false;
        UUID bearer = player.getGameProfile()
            .getId();
        CardPlayer seated = table.game.deAmputate(bearer);
        if (seated == null || seated.eliminated) return false;
        InventoryPlayer inv = player.inventory;
        boolean cursorValid = belongsTo(inv.getItemStack(), table, bearer)
            && matches(table, ItemDataUtils.getBinding(inv.getItemStack()));
        boolean currentValid = !cursorValid && belongsTo(inv.getCurrentItem(), table, bearer)
            && matches(table, ItemDataUtils.getBinding(inv.getCurrentItem()));
        int keep = currentValid ? inv.currentItem : -1;
        boolean changed = cursorValid && inv.getItemStack().stackSize != 1;
        if (changed) inv.getItemStack().stackSize = 1;
        for (int i = 0; i < inv.mainInventory.length; i++) {
            ItemStack stack = inv.mainInventory[i];
            if (!belongsTo(stack, table, bearer)) continue;
            if (!cursorValid && (keep < 0 || keep == i) && matches(table, ItemDataUtils.getBinding(stack))) {
                keep = i;
                if (stack.stackSize != 1) {
                    stack.stackSize = 1;
                    changed = true;
                }
            } else {
                inv.setInventorySlotContents(i, null);
                changed = true;
            }
        }
        if (belongsTo(inv.getItemStack(), table, bearer) && !cursorValid) {
            inv.setItemStack(null);
            changed = true;
        }
        boolean issued = false;
        if (keep < 0 && !cursorValid) {
            int empty = inv.currentItem >= 0 && inv.currentItem < InventoryPlayer.getHotbarSize()
                && inv.getCurrentItem() == null ? inv.currentItem : inv.getFirstEmptyStack();
            if (empty < 0) {
                if (requested)
                    player.addChatComponentMessage(Chat.translatable("game.minopp.play.hand_inventory_full"));
                if (changed) syncInventory(player);
                return false;
            }
            inv.setInventorySlotContents(empty, create(table, bearer));
            keep = empty;
            issued = changed = true;
        }
        if (requested && keep >= 0
            && keep != inv.currentItem
            && inv.currentItem >= 0
            && inv.currentItem < InventoryPlayer.getHotbarSize()) {
            if (inv.getCurrentItem() == null) {
                inv.setInventorySlotContents(inv.currentItem, inv.mainInventory[keep]);
                inv.setInventorySlotContents(keep, null);
                changed = true;
            } else player.addChatComponentMessage(Chat.translatable("game.minopp.play.hand_card_in_inventory"));
        }
        if (changed) syncInventory(player);
        if (issued) {
            table.sync();
            player.addChatComponentMessage(Chat.translatable("game.minopp.play.hand_recovered"));
        }
        return true;
    }

    private static void syncInventory(EntityPlayer player) {
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        if (player.openContainer != player.inventoryContainer) player.openContainer.detectAndSendChanges();
        if (player instanceof EntityPlayerMP serverPlayer) serverPlayer.updateHeldItem();
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("game", gameId);
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<UUID, UUID> entry : tokens.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            NbtUtil.putUUID(item, "player", entry.getKey());
            NbtUtil.putUUID(item, "token", entry.getValue());
            entries.appendTag(item);
        }
        tag.setTag("items", entries);
        return tag;
    }

    public void fromTag(NBTTagCompound tag) {
        tokens.clear();
        gameId = tag.getLong("game");
        NBTTagList entries = tag.getTagList("items", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound item = entries.getCompoundTagAt(i);
            if (NbtUtil.hasUUID(item, "player") && NbtUtil.hasUUID(item, "token"))
                tokens.put(NbtUtil.getUUID(item, "player"), NbtUtil.getUUID(item, "token"));
        }
    }
}
