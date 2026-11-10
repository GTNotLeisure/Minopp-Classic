package com.hfstudio.minopp.network;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public record S2CChoosingPacket(BlockPos pos, long gameId, UUID chooser, boolean ring, int hover, long revision) {

    public static final String ID = "minopp:choosing";

    public static void send(BlockEntityMinoTable table, long revision) {
        if (table.game == null) return;
        ByteBuf packet = MinoNetwork.buffer();
        Buf.writeBlockPos(packet, table.getBlockPos());
        packet.writeLong(table.game.id);
        packet.writeBoolean(table.choosingUuid != null);
        if (table.choosingUuid != null) Buf.writeUUID(packet, table.choosingUuid);
        packet.writeBoolean(table.choosingRing);
        packet.writeByte(table.choosingHover);
        packet.writeLong(revision);
        table.forEachViewer(player -> MinoNetwork.sendPacketToPlayer(player, ID, packet));
    }

    @SideOnly(Side.CLIENT)
    public static void handle(ByteBuf packet) {
        S2CChoosingPacket update = new S2CChoosingPacket(
            Buf.readBlockPos(packet),
            packet.readLong(),
            packet.readBoolean() ? Buf.readUUID(packet) : null,
            packet.readBoolean(),
            Math.max(-1, Math.min(3, packet.readByte())),
            packet.readLong());
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, update.pos());
        if (table == null || table.game == null || table.game.id != update.gameId()) return;
        table.updateChoosing(update.chooser(), update.ring(), update.hover(), update.revision());
    }
}
