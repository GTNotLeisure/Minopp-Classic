package com.hfstudio.minopp.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import io.netty.buffer.ByteBuf;

public class C2SPlayCardPacket {

    public static final String ID = "minopp:play_card";

    private static final int ACTION_PLAY = 0, ACTION_NO_CARD = 1, ACTION_DOUBT = 2, ACTION_DECISION = 3,
        ACTION_COIN = 4, ACTION_CHOOSING = 5;

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        BlockPos gamePos = Buf.readBlockPos(packet);
        UUID playerUuid = player.getGameProfile()
            .getId();
        int actionType = packet.readInt();

        if (!C2SSeatControlPacket.inReach(player, gamePos)) return;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null || tableEntity.game == null) return;
        CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
        if (cardPlayer == null) return;
        if (actionType == ACTION_PLAY || actionType == ACTION_NO_CARD) tableEntity.lastPlayedByUuid = playerUuid;

        if (actionType == ACTION_CHOOSING) {
            choosing(tableEntity, cardPlayer, packet);
            return;
        }
        ActionReport result = switch (actionType) {
            case ACTION_PLAY -> {
                int cardUid = packet.readInt();
                PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
                int colorOrdinal = packet.readByte();
                choices.color = colorOrdinal < 0 || colorOrdinal >= CardColor.values().length ? null
                    : CardColor.values()[colorOrdinal];
                int targets = Math.min(packet.readByte(), BlockEntityMinoTable.MAX_SEATS);
                for (int i = 0; i < targets; i++) choices.targets.add(Buf.readUUID(packet));
                choices.useFlex = packet.readBoolean();
                choices.shout = packet.readBoolean();
                if (packet.isReadable()) {
                    choices.number = packet.readByte();
                    int picks = Math.min(packet.readUnsignedByte(), 120);
                    for (int i = 0; i < picks; i++) choices.picks.add(packet.readInt());
                }
                yield tableEntity.game.playCard(cardPlayer, cardUid, choices);
            }
            case ACTION_NO_CARD -> tableEntity.game.playNoCard(cardPlayer);
            case ACTION_COIN -> tableEntity.game.useCoin(cardPlayer, packet.readBoolean());
            case ACTION_DOUBT -> tableEntity.game.doubtMino(cardPlayer, Buf.readUUID(packet));
            case ACTION_DECISION -> {
                int answer = packet.readInt();
                UUID target = packet.readBoolean() ? Buf.readUUID(packet) : null;
                yield tableEntity.game.answerDecision(cardPlayer, answer, target);
            }
            default -> null;
        };
        tableEntity.handleActionResult(result, cardPlayer, player);
    }

    private static void choosing(BlockEntityMinoTable tableEntity, CardPlayer cardPlayer, ByteBuf packet) {
        boolean active = packet.readBoolean(), ring = packet.readBoolean();
        int hover = Math.max(-1, Math.min(3, packet.readByte()));
        // only whoever is choosing right now may show a picker, and only changes go out to the table
        boolean mayChoose = tableEntity.game.currentPlayer()
            .equals(cardPlayer)
            || tableEntity.game.decisions.stream()
                .anyMatch(d -> d.player.equals(cardPlayer.uuid));
        UUID who = active && mayChoose ? cardPlayer.uuid : null;
        if (who == null && !cardPlayer.uuid.equals(tableEntity.choosingUuid)) return;
        if (Objects.equals(who, tableEntity.choosingUuid) && ring == tableEntity.choosingRing
            && hover == tableEntity.choosingHover) return;
        tableEntity.choosingUuid = who;
        tableEntity.choosingRing = ring;
        tableEntity.choosingHover = hover;
        tableEntity.markDirty();
        S2CChoosingPacket.send(tableEntity);
    }

    public static class Client {

        private static ByteBuf header(BlockPos gamePos, int action) {
            ByteBuf packet = MinoNetwork.buffer();
            Buf.writeBlockPos(packet, gamePos);
            packet.writeInt(action);
            return packet;
        }

        public static void sendPlayCardC2S(BlockPos gamePos, CardPlayer player, int cardUid,
            PlayContext.PlayChoices choices) {
            ByteBuf packet = header(gamePos, ACTION_PLAY);
            packet.writeInt(cardUid);
            packet.writeByte(choices.color == null ? -1 : choices.color.ordinal());
            packet.writeByte(choices.targets.size());
            for (UUID target : choices.targets) Buf.writeUUID(packet, target);
            packet.writeBoolean(choices.useFlex);
            packet.writeBoolean(choices.shout);
            packet.writeByte(choices.number);
            int picks = Math.min(choices.picks.size(), 120);
            packet.writeByte(picks);
            for (int i = 0; i < picks; i++) packet.writeInt(choices.picks.get(i));
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendChoosingC2S(BlockPos gamePos, boolean active, boolean ring, int hover) {
            ByteBuf packet = header(gamePos, ACTION_CHOOSING);
            packet.writeBoolean(active);
            packet.writeBoolean(ring);
            packet.writeByte(hover);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendCoinC2S(BlockPos gamePos, boolean flip) {
            ByteBuf packet = header(gamePos, ACTION_COIN);
            packet.writeBoolean(flip);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendPlayNoCardC2S(BlockPos gamePos, CardPlayer player) {
            MinoNetwork.sendPacketToServer(ID, header(gamePos, ACTION_NO_CARD));
        }

        public static void sendDoubtMinoC2S(BlockPos gamePos, CardPlayer player, UUID targetPlayer) {
            ByteBuf packet = header(gamePos, ACTION_DOUBT);
            Buf.writeUUID(packet, targetPlayer);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendDecisionC2S(BlockPos gamePos, int answer, UUID target) {
            ByteBuf packet = header(gamePos, ACTION_DECISION);
            packet.writeInt(answer);
            packet.writeBoolean(target != null);
            if (target != null) Buf.writeUUID(packet, target);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
