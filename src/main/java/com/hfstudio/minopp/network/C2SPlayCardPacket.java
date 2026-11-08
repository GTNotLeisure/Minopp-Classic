package com.hfstudio.minopp.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public class C2SPlayCardPacket {

    public static final String ID = "minopp:play_card";

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        BlockPos gamePos = Buf.readBlockPos(packet);
        World level = player.worldObj;
        UUID playerUuid = Buf.readUUID(packet);
        int actionType = packet.readInt();

        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(level, gamePos);
        switch (actionType) {
            case 0 -> {
                final Card card = new Card(Buf.readNbt(packet));
                final int wildSelectionOrdinal = packet.readInt();
                final boolean shout = packet.readBoolean();
                if (tableEntity == null || tableEntity.game == null) return;
                CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
                if (cardPlayer == null) return;
                Card.Suit wildSelection = wildSelectionOrdinal == -1 ? null : Card.Suit.values()[wildSelectionOrdinal];
                ActionReport result = tableEntity.game.playCard(cardPlayer, card, wildSelection, shout);
                tableEntity.handleActionResult(result, cardPlayer, player);
            }
            case 1 -> {
                if (tableEntity == null || tableEntity.game == null) return;
                CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
                if (cardPlayer == null) return;
                ActionReport result = tableEntity.game.playNoCard(cardPlayer);
                tableEntity.handleActionResult(result, cardPlayer, player);
            }
            case 2 -> {
                UUID targetPlayerUuid = Buf.readUUID(packet);
                if (tableEntity == null || tableEntity.game == null) return;
                CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
                if (cardPlayer == null) return;
                ActionReport result = tableEntity.game.doubtMino(cardPlayer, targetPlayerUuid);
                tableEntity.handleActionResult(result, cardPlayer, player);
            }
            case 3 -> {
                final Card card = new Card(Buf.readNbt(packet));
                final int wildSelectionOrdinal = packet.readInt();
                final boolean shout = packet.readBoolean();
                final UUID targetPlayerUuid = Buf.readUUID(packet);
                if (tableEntity == null || tableEntity.game == null) return;
                CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
                if (cardPlayer == null) return;
                Card.Suit wildSelection = wildSelectionOrdinal == -1 ? null : Card.Suit.values()[wildSelectionOrdinal];
                int discardSizeBefore = tableEntity.game.discardDeck.size();
                ActionReport result = tableEntity.game.playCard(cardPlayer, card, wildSelection, shout);
                if (tableEntity.game != null && card.number == 7
                    && tableEntity.game.discardDeck.size() > discardSizeBefore
                    && tableEntity.getRule(BlockEntityMinoTable.RULE_SEVEN0, false)) {
                    CardPlayer targetPlayer = tableEntity.game.deAmputate(targetPlayerUuid);
                    if (targetPlayer != null) {
                        tableEntity.game.swapHands(cardPlayer, targetPlayer);
                    }
                }
                tableEntity.handleActionResult(result, cardPlayer, player);
            }
            default -> {}
        }
    }

    public static class Client {

        public static void sendPlayCardC2S(BlockPos gamePos, CardPlayer player, Card card, Card.Suit wildSelection, boolean shout) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            Buf.writeUUID(packet, player.uuid);
            packet.writeInt(0);
            Buf.writeNbt(packet, card.toTag());
            packet.writeInt(wildSelection == null ? -1 : wildSelection.ordinal());
            packet.writeBoolean(shout);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendPlayNoCardC2S(BlockPos gamePos, CardPlayer player) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            Buf.writeUUID(packet, player.uuid);
            packet.writeInt(1);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendDoubtMinoC2S(BlockPos gamePos, CardPlayer player, UUID targetPlayer) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            Buf.writeUUID(packet, player.uuid);
            packet.writeInt(2);
            Buf.writeUUID(packet, targetPlayer);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendSwapHandC2S(BlockPos gamePos, CardPlayer player, Card card, Card.Suit wildSelection, boolean shout, UUID targetPlayerUuid) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            Buf.writeUUID(packet, player.uuid);
            packet.writeInt(3); // new action type
            Buf.writeNbt(packet, card.toTag());
            packet.writeInt(wildSelection == null ? -1 : wildSelection.ordinal());
            packet.writeBoolean(shout);
            Buf.writeUUID(packet, targetPlayerUuid); // the swap target
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
