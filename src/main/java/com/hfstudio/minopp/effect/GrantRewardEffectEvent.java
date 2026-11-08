package com.hfstudio.minopp.effect;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.WorldUtil;

import io.netty.buffer.ByteBuf;

public record GrantRewardEffectEvent(UUID targetPlayer) implements EffectEvent {

    @Override
    public Type<? extends EffectEvent> type() {
        return EffectEvents.GRANT_REWARD;
    }

    @Override
    public int timeOffset() {
        return 0;
    }

    @Override
    public Optional<UUID> target() {
        return Optional.empty();
    }

    @Override
    public void summonClient(World level, BlockPos origin, boolean selfPartOfSourceGame) {}

    @Override
    public void summonServer(WorldServer level, BlockPos origin, BlockEntityMinoTable tableEntity) {
        if (tableEntity.award == null) return;
        EntityPlayer player = WorldUtil.getPlayerByUUID(level, targetPlayer);
        if (player != null) {
            giveOrDrop(player, tableEntity.award.copy());
        }
    }

    public static void giveOrDrop(EntityPlayer player, ItemStack stack) {
        boolean addSuccessful = player.inventory.addItemStackToInventory(stack);
        if (!addSuccessful || stack.stackSize > 0) {
            EntityItem itemEntity = player.dropPlayerItemWithRandomChoice(stack, false);
            if (itemEntity != null) {
                itemEntity.delayBeforeCanPickup = 0;
                itemEntity.func_145797_a(player.getCommandSenderName());
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    @Override
    public void encode(EffectEvent event, ByteBuf buffer) {
        Buf.writeUUID(buffer, targetPlayer);
    }

    public static GrantRewardEffectEvent decode(ByteBuf buffer) {
        return new GrantRewardEffectEvent(Buf.readUUID(buffer));
    }
}
