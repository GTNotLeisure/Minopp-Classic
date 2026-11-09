package com.hfstudio.minopp.network;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.CardPack;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;
import com.hfstudio.minopp.util.Chat;

import io.netty.buffer.ByteBuf;

public class C2SSeatControlPacket {

    public static final String ID = "minopp:seat_control";

    private static final int STOP = 0, START = 1, RESET = -1, RULE = 2, PACK = 3, COUNT = 4, PRESET = 5, APPLY = 6,
        TEAM = 7, AUTO_TEAMS = 8, ADD_BOT = 9, FILL_BOTS = 10, CLEAR_BOTS = 11, LEAVE = 12, REMOVE_BOT = 13;

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        BlockPos gamePos = Buf.readBlockPos(packet);
        int action = packet.readInt();
        NBTTagCompound applyTag = action == APPLY ? Buf.readNbt(packet) : null;
        String key = (action == RULE || action == PACK || action == COUNT || action == PRESET || action == TEAM)
            ? Buf.readString(packet)
            : null;
        int value = (action == RULE || action == PACK || action == COUNT || action == TEAM) ? packet.readInt() : 0;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null) return;
        if (player.getDistanceSq(gamePos.x + 1.5, gamePos.y + 0.5, gamePos.z + 1.5) > 16 * 16) return;
        List<CardPlayer> playersList = tableEntity.getPlayersList();
        CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
        if (tableEntity.demo) {
            player.addChatComponentMessage(Chat.translatable("game.minopp.play.table_in_demo"));
            return;
        }
        boolean manager = tableEntity.canManage(player);
        boolean hostOnly = switch (action) {
            case START, STOP, RESET, RULE, PACK, COUNT, PRESET, APPLY, AUTO_TEAMS -> true;
            default -> false;
        };
        if (hostOnly && !manager) {
            player.addChatComponentMessage(
                Chat.translatable(
                    "game.minopp.play.host_only",
                    tableEntity.hostName.isEmpty() ? "?" : tableEntity.hostName));
            return;
        }
        switch (action) {
            case START -> {
                if (tableEntity.game == null) {
                    if (playersList.size() < 2) {
                        player.addChatComponentMessage(Chat.translatable("game.minopp.play.no_enough_player"));
                        return;
                    }
                    IChatComponent teamProblem = tableEntity.startProblem();
                    if (teamProblem != null) {
                        player.addChatComponentMessage(teamProblem);
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
                    if (key.equals(GameRules.MAX_PLAYERS) || key.equals(GameRules.TEAM_COUNT)
                        || key.equals(GameRules.TEAM_MODE)) tableEntity.onSeatCountChanged();
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
            case APPLY -> {
                if (tableEntity.game == null && applyTag != null) {
                    tableEntity.config.fromTag(applyTag);
                    tableEntity.onSeatCountChanged();
                    tableEntity.sync();
                }
            }
            case TEAM -> {
                UUID target;
                try {
                    target = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    return;
                }
                boolean self = target.equals(cardPlayer.uuid);
                boolean bot = MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList.stream()
                        .noneMatch(
                            o -> o.getGameProfile()
                                .getId()
                                .equals(target));
                if (self || bot && manager) tableEntity.setTeam(target, value);
            }
            case AUTO_TEAMS -> tableEntity.autoTeams();
            case ADD_BOT, FILL_BOTS, CLEAR_BOTS, REMOVE_BOT -> {
                if (!com.hfstudio.minopp.config.ServerConfig.gameplay.everyoneCanManageBots && !manager) {
                    player.addChatComponentMessage(Chat.translatable("game.minopp.play.bot_permission"));
                    return;
                }
                if (tableEntity.game != null) return;
                if (action == CLEAR_BOTS || action == REMOVE_BOT) {
                    int n = tableEntity.removeBots(action == REMOVE_BOT ? 1 : BlockEntityMinoTable.MAX_SEATS);
                    player.addChatComponentMessage(
                        Chat.translatable(n == 0 ? "game.minopp.play.no_bots" : "game.minopp.play.bots_removed", n));
                } else {
                    int n = tableEntity.addBots(action == ADD_BOT ? 1 : BlockEntityMinoTable.MAX_SEATS);
                    if (n == 0) player.addChatComponentMessage(Chat.translatable("game.minopp.play.table_full"));
                }
            }
            case LEAVE -> tableEntity.leaveSeat(cardPlayer.uuid);
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

        public static void sendTeamC2S(BlockPos gamePos, UUID player, int team) {
            ByteBuf packet = header(gamePos, TEAM);
            Buf.writeString(packet, player.toString());
            packet.writeInt(team);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendBotsC2S(BlockPos gamePos, int mode) {
            MinoNetwork.sendPacketToServer(
                ID,
                header(gamePos, mode == 0 ? ADD_BOT : mode == 1 ? FILL_BOTS : mode == 3 ? REMOVE_BOT : CLEAR_BOTS));
        }

        public static void sendLeaveC2S(BlockPos gamePos) {
            MinoNetwork.sendPacketToServer(ID, header(gamePos, LEAVE));
        }

        public static void sendAutoTeamsC2S(BlockPos gamePos) {
            MinoNetwork.sendPacketToServer(ID, header(gamePos, AUTO_TEAMS));
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

        public static void sendCountC2S(BlockPos gamePos, String countKey, int count) {
            ByteBuf packet = header(gamePos, COUNT);
            Buf.writeString(packet, countKey);
            packet.writeInt(count);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendApplyC2S(BlockPos gamePos, TableConfig config) {
            ByteBuf packet = header(gamePos, APPLY);
            Buf.writeNbt(packet, config.toTag());
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendPresetC2S(BlockPos gamePos, String packId) {
            ByteBuf packet = header(gamePos, PRESET);
            Buf.writeString(packet, packId);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
