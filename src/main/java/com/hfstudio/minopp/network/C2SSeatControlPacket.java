package com.hfstudio.minopp.network;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.Chat;

import io.netty.buffer.ByteBuf;

public class C2SSeatControlPacket {

    public static final String ID = "minopp:seat_control";

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        BlockPos gamePos = Buf.readBlockPos(packet);
        int action = packet.readInt();
        String ruleName = (action == 2) ? Buf.readString(packet) : null;
        boolean ruleValue = (action == 2) && packet.readBoolean();
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null) return;
        List<CardPlayer> playersList = tableEntity.getPlayersList();
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        if (tableEntity.demo) {
            player.addChatComponentMessage(Chat.translatable("game.minopp.play.table_in_demo"));
            return;
        }
        // Start or end the game
        switch (action) {
            case 1 -> {
                if (tableEntity.game == null) {
                    if (playersList.size() < 2) {
                        player.addChatComponentMessage(Chat.translatable("game.minopp.play.no_enough_player"));
                        return;
                    }
                    tableEntity.startGame(cardPlayer);
                }
            }
            case 0 -> {
                if (tableEntity.game != null) tableEntity.destroyGame(cardPlayer);
            }
            case -1 -> {
                if (tableEntity.game == null) tableEntity.resetSeats(cardPlayer);
            }
            case 2 -> {
                if (tableEntity.game == null) {
                    tableEntity.rules.put(ruleName, ruleValue);
                    tableEntity.sync();
                }
            }
            default -> {}
        }
    }

    public static class Client {

        public static void sendGameEnableC2S(BlockPos gamePos, boolean gameEnable) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            packet.writeInt(gameEnable ? 1 : 0);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendResetSeatsC2S(BlockPos gamePos) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            packet.writeInt(-1);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendRuleC2S(BlockPos gamePos, String ruleName, boolean value) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            packet.writeInt(2);
            Buf.writeString(packet, ruleName);
            packet.writeBoolean(value);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
