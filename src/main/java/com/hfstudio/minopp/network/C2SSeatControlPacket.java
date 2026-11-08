package com.hfstudio.minopp.network;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.Chat;

import io.netty.buffer.ByteBuf;

/**
 * Seat and table setup actions: start / stop / reset seats, and editing the table config (rules, packs, card counts,
 * presets). Config edits are only accepted while no game is running.
 */
public class C2SSeatControlPacket {

    public static final String ID = "minopp:seat_control";

    private static final int STOP = 0, START = 1, RESET = -1, RULE = 2, PACK = 3, COUNT = 4, PRESET = 5;

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        BlockPos gamePos = Buf.readBlockPos(packet);
        int action = packet.readInt();
        String key = (action >= RULE) ? Buf.readString(packet) : null;
        int value = (action == RULE || action == PACK || action == COUNT) ? packet.readInt() : 0;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null) return;
        List<CardPlayer> playersList = tableEntity.getPlayersList();
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        if (tableEntity.demo) {
            player.addChatComponentMessage(Chat.translatable("game.minopp.play.table_in_demo"));
            return;
        }
        switch (action) {
            case START -> {
                if (tableEntity.game == null) {
                    if (playersList.size() < 2) {
                        player.addChatComponentMessage(Chat.translatable("game.minopp.play.no_enough_player"));
                        return;
                    }
                    tableEntity.startGame(cardPlayer);
                }
            }
            case STOP -> {
                if (tableEntity.game != null) tableEntity.destroyGame(cardPlayer);
            }
            case RESET -> {
                if (tableEntity.game == null) tableEntity.resetSeats(cardPlayer);
            }
            case RULE -> {
                if (tableEntity.game == null && GameRules.get(key) != null) {
                    tableEntity.config.set(key, value);
                    if (key.equals(GameRules.MAX_PLAYERS)) tableEntity.onSeatCountChanged();
                    tableEntity.sync();
                }
            }
            case PACK -> {
                if (tableEntity.game == null && CardPack.get(key) != null) {
                    tableEntity.config.setPackEnabled(key, value != 0);
                    tableEntity.onSeatCountChanged();
                    tableEntity.sync();
                }
            }
            case COUNT -> {
                if (tableEntity.game == null) {
                    tableEntity.config.setCount(key, value);
                    tableEntity.sync();
                }
            }
            case PRESET -> {
                if (tableEntity.game == null && CardPack.get(key) != null) {
                    tableEntity.config.resetToPreset(key);
                    tableEntity.onSeatCountChanged();
                    tableEntity.sync();
                }
            }
            default -> {}
        }
    }

    public static class Client {

        private static ByteBuf header(BlockPos gamePos, int action) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            packet.writeInt(action);
            return packet;
        }

        public static void sendGameEnableC2S(BlockPos gamePos, boolean gameEnable) {
            MinoNetwork.sendPacketToServer(ID, header(gamePos, gameEnable ? START : STOP));
        }

        public static void sendResetSeatsC2S(BlockPos gamePos) {
            MinoNetwork.sendPacketToServer(ID, header(gamePos, RESET));
        }

        public static void sendRuleC2S(BlockPos gamePos, String ruleName, int value) {
            ByteBuf packet = header(gamePos, RULE);
            Buf.writeString(packet, ruleName);
            packet.writeInt(value);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendRuleC2S(BlockPos gamePos, String ruleName, boolean value) {
            sendRuleC2S(gamePos, ruleName, value ? 1 : 0);
        }

        public static void sendPackC2S(BlockPos gamePos, String packId, boolean enabled) {
            ByteBuf packet = header(gamePos, PACK);
            Buf.writeString(packet, packId);
            packet.writeInt(enabled ? 1 : 0);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        /** Count of -1 removes the override. */
        public static void sendCountC2S(BlockPos gamePos, String countKey, int count) {
            ByteBuf packet = header(gamePos, COUNT);
            Buf.writeString(packet, countKey);
            packet.writeInt(count);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendPresetC2S(BlockPos gamePos, String packId) {
            ByteBuf packet = header(gamePos, PRESET);
            Buf.writeString(packet, packId);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
