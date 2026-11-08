package com.hfstudio.minopp.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class ItemCoupon extends Item {

    public ItemCoupon() {
        setUnlocalizedName("minopp.coupon");
        setTextureName("minopp:coupon");
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(StatCollector.translateToLocal("item.minopp.coupon.description"));
    }
}
