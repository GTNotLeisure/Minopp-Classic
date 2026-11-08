package com.hfstudio.minopp;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/** The mod's own creative tab: the card table and the coupon. Hand cards only exist inside a running game. */
public class MinoCreativeTab extends CreativeTabs {

    public static final MinoCreativeTab INSTANCE = new MinoCreativeTab();

    private MinoCreativeTab() {
        super("minopp");
    }

    @Override
    public Item getTabIconItem() {
        return Item.getItemFromBlock(Mino.BLOCK_MINO_TABLE);
    }
}
