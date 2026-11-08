package com.hfstudio.minopp.block;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class ItemMinoTable extends ItemBlock {

    public ItemMinoTable(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        int facing = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int cx, cz;
        switch (facing) {
            case 0 -> {
                cx = x - 1;
                cz = z;
            }
            case 1 -> {
                cx = x - 2;
                cz = z - 1;
            }
            case 2 -> {
                cx = x - 1;
                cz = z - 2;
            }
            default -> {
                cx = x;
                cz = z - 1;
            }
        }
        for (BlockMinoTable.TablePartType part : BlockMinoTable.TablePartType.values()) {
            int px = cx + part.xOff, pz = cz + part.zOff;
            if (!world.getBlock(px, y, pz)
                .isReplaceable(world, px, y, pz)) return false;
            if (!player.canPlayerEdit(px, y, pz, side, stack)) return false;
        }
        if (!world.setBlock(cx, y, cz, field_150939_a, 0, 3)) return false;
        field_150939_a.onBlockPlacedBy(world, cx, y, cz, player, stack);
        field_150939_a.onPostBlockPlaced(world, cx, y, cz, 0);
        return true;
    }
}
