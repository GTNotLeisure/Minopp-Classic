package com.hfstudio.minopp.item;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.NbtUtil;

public final class ItemDataUtils {

    public static final String MINO_DATA = "minopp_data";

    public static final String CARD_GAME_BINDING_TABLE_POS = "card_game_binding_table_pos";
    public static final String CARD_GAME_BINDING_BEARER_ID = "card_game_binding_bearer_id";
    public static final String CLIENT_HAND_INDEX = "client_hand_index";

    private ItemDataUtils() {}

    public static NBTTagCompound getDataMap(ItemStack stack) {
        if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
        NBTTagCompound tag = stack.stackTagCompound;
        if (tag.hasKey(MINO_DATA, 10)) {
            return tag.getCompoundTag(MINO_DATA);
        }
        NBTTagCompound dataMap = new NBTTagCompound();
        tag.setTag(MINO_DATA, dataMap);
        return dataMap;
    }

    public static NBTTagCompound getDataMapIfPresent(ItemStack stack) {
        if (stack == null) return null;
        NBTTagCompound tag = stack.stackTagCompound;
        if (tag != null && tag.hasKey(MINO_DATA, 10)) {
            return tag.getCompoundTag(MINO_DATA);
        }
        return null;
    }

    public static boolean hasData(ItemStack stack, String key, int type) {
        NBTTagCompound dataMap = getDataMapIfPresent(stack);
        return dataMap != null && dataMap.hasKey(key, type);
    }

    public static void removeData(ItemStack stack, String key) {
        NBTTagCompound dataMap = getDataMapIfPresent(stack);
        if (dataMap != null) {
            dataMap.removeTag(key);
            if (dataMap.hasNoTags()) {
                stack.stackTagCompound.removeTag(MINO_DATA);
            }
        }
    }

    public static int getHandIndex(ItemStack stack) {
        NBTTagCompound dataMap = getDataMapIfPresent(stack);
        return dataMap == null ? 0 : dataMap.getInteger(CLIENT_HAND_INDEX);
    }

    public static BlockPos getBlockPos(ItemStack stack) {
        NBTTagCompound dataMap = getDataMapIfPresent(stack);
        return dataMap == null ? null : BlockPos.of(dataMap.getLong(CARD_GAME_BINDING_TABLE_POS));
    }

    public static UUID getBearerId(ItemStack stack) {
        NBTTagCompound dataMap = getDataMapIfPresent(stack);
        if (dataMap != null && NbtUtil.hasUUID(dataMap, CARD_GAME_BINDING_BEARER_ID)) {
            return NbtUtil.getUUID(dataMap, CARD_GAME_BINDING_BEARER_ID);
        }
        return null;
    }

    public static void setHandIndex(ItemStack stack, int index) {
        getDataMap(stack).setInteger(CLIENT_HAND_INDEX, index);
    }

    public static void setBlockPos(ItemStack stack, BlockPos blockPos) {
        getDataMap(stack).setLong(CARD_GAME_BINDING_TABLE_POS, blockPos.asLong());
    }

    public static void setBearerId(ItemStack stack, UUID uuid) {
        if (uuid == null) {
            removeData(stack, CARD_GAME_BINDING_BEARER_ID + "M");
            removeData(stack, CARD_GAME_BINDING_BEARER_ID + "L");
        } else {
            NbtUtil.putUUID(getDataMap(stack), CARD_GAME_BINDING_BEARER_ID, uuid);
        }
    }

    public static void setCardGameBinding(ItemStack stack, BlockPos blockPos, UUID uuid) {
        setBlockPos(stack, blockPos);
        setBearerId(stack, uuid);
    }
}
