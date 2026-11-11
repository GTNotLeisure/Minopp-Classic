package com.hfstudio.minopp.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.CardColor;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.PendingDecision;
import com.hfstudio.minopp.game.PlayContext;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Buf;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class C2SPlayCardPacket {

    public static final String ID = "minopp:play_card";

    private static final int ACTION_PLAY = 0, ACTION_NO_CARD = 1, ACTION_DOUBT = 2, ACTION_DECISION = 3,
        ACTION_COIN = 4, ACTION_CHOOSING = 5, ACTION_DECISION_BATCH = 6;
    private static final int EXTENDED_PICKS = 255;

    public static void handleC2S(EntityPlayerMP player, ByteBuf packet) {
        if (packet.readableBytes() < 16) return;
        BlockPos gamePos = Buf.readBlockPos(packet);
        UUID playerUuid = player.getGameProfile()
            .getId();
        int actionType = packet.readInt();

        if (!C2SSeatControlPacket.inReach(player, gamePos)) return;
        boolean holdsAccess = gamePos.equals(ItemHandCards.getHandCardGamePos(player));
        if (!holdsAccess && actionType != ACTION_CHOOSING) return;
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(player.worldObj, gamePos);
        if (tableEntity == null || tableEntity.game == null) return;
        CardPlayer cardPlayer = tableEntity.game.deAmputate(playerUuid);
        if (cardPlayer == null || cardPlayer.eliminated) return;

        if (actionType == ACTION_CHOOSING) {
            choosing(tableEntity, cardPlayer, packet, holdsAccess);
            return;
        }
        ActionReport result = switch (actionType) {
            case ACTION_PLAY -> {
                if (packet.readableBytes() < 6) yield null;
                int cardUid = packet.readInt();
                PlayContext.PlayChoices choices = new PlayContext.PlayChoices();
                int colorOrdinal = packet.readByte();
                choices.color = colorOrdinal < 0 || colorOrdinal >= CardColor.values().length ? null
                    : CardColor.values()[colorOrdinal];
                int targets = packet.readUnsignedByte();
                if (targets > BlockEntityMinoTable.MAX_SEATS || packet.readableBytes() < targets * 16 + 2) yield null;
                for (int i = 0; i < targets; i++) choices.targets.add(Buf.readUUID(packet));
                choices.useFlex = packet.readBoolean();
                choices.shout = packet.readBoolean();
                if (packet.isReadable()) {
                    if (packet.readableBytes() < 2) yield null;
                    choices.number = packet.readByte();
                    int picks = packet.readUnsignedByte();
                    if (picks == EXTENDED_PICKS) {
                        if (packet.readableBytes() < 4) yield null;
                        picks = packet.readInt();
                    }
                    if (picks < 0 || picks > cardPlayer.hand.size()
                        || picks > packet.readableBytes() / 4
                        || packet.readableBytes() != picks * 4) yield null;
                    for (int i = 0; i < picks; i++) choices.picks.add(packet.readInt());
                }
                yield tableEntity.game.playCard(cardPlayer, cardUid, choices);
            }
            case ACTION_NO_CARD -> packet.isReadable() ? null : tableEntity.game.playNoCard(cardPlayer);
            case ACTION_COIN -> packet.readableBytes() == 1 ? tableEntity.game.useCoin(cardPlayer, packet.readBoolean())
                : null;
            case ACTION_DOUBT -> packet.readableBytes() == 16
                ? tableEntity.game.doubtMino(cardPlayer, Buf.readUUID(packet))
                : null;
            case ACTION_DECISION -> {
                if (packet.readableBytes() < 5) yield null;
                int answer = packet.readInt();
                boolean hasTarget = packet.readBoolean();
                if (packet.readableBytes() != (hasTarget ? 32 : 16)) yield null;
                UUID target = hasTarget ? Buf.readUUID(packet) : null;
                UUID decisionId = Buf.readUUID(packet);
                yield tableEntity.game.answerDecision(cardPlayer, decisionId, answer, target);
            }
            case ACTION_DECISION_BATCH -> {
                if (packet.readableBytes() < 21) yield null;
                UUID decisionId = Buf.readUUID(packet);
                boolean hasTarget = packet.readBoolean();
                if (packet.readableBytes() < (hasTarget ? 20 : 4)) yield null;
                UUID target = hasTarget ? Buf.readUUID(packet) : null;
                int count = packet.readInt();
                if (count <= 0 || count > packet.readableBytes() / 4 || packet.readableBytes() != count * 4) yield null;
                int[] answers = new int[count];
                for (int i = 0; i < count; i++) answers[i] = packet.readInt();
                yield tableEntity.game.answerDecision(cardPlayer, decisionId, answers, target);
            }
            default -> null;
        };
        if (result != null && !result.isFail && (actionType == ACTION_PLAY || actionType == ACTION_NO_CARD)) {
            tableEntity.lastPlayedByUuid = playerUuid;
        }
        tableEntity.handleActionResult(result, cardPlayer, player);
    }

    private static void choosing(BlockEntityMinoTable tableEntity, CardPlayer cardPlayer, ByteBuf packet,
        boolean holdsAccess) {
        if (packet.readableBytes() != 3) return;
        boolean active = packet.readBoolean(), ring = packet.readBoolean();
        if (active && !holdsAccess) return;
        int hover = Math.max(
            -1,
            Math.min(
                tableEntity.game.colors()
                    .size() - 1,
                packet.readByte()));
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
        tableEntity.syncChoosing();
    }

    @SideOnly(Side.CLIENT)
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
            int targets = Math.min(choices.targets.size(), BlockEntityMinoTable.MAX_SEATS);
            packet.writeByte(targets);
            for (int i = 0; i < targets; i++) Buf.writeUUID(packet, choices.targets.get(i));
            packet.writeBoolean(choices.useFlex);
            packet.writeBoolean(choices.shout);
            packet.writeByte(choices.number);
            int picks = choices.picks.size();
            packet.writeByte(Math.min(picks, EXTENDED_PICKS));
            if (picks >= EXTENDED_PICKS) packet.writeInt(picks);
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

        /**
         * Uses the currently synchronized decision. Custom screens should pass their displayed decision explicitly.
         */
        public static void sendDecisionC2S(BlockPos gamePos, int answer, UUID target) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.theWorld == null) return;
            BlockEntityMinoTable table = ItemHandCards.getTable(mc.theWorld, gamePos);
            if (table == null || table.game == null) return;
            PendingDecision decision = table.game.decisionFor(
                mc.thePlayer.getGameProfile()
                    .getId());
            if (decision != null) sendDecisionC2S(gamePos, decision, answer, target);
        }

        public static void sendDecisionC2S(BlockPos gamePos, PendingDecision decision, int answer, UUID target) {
            Objects.requireNonNull(decision, "decision");
            ByteBuf packet = header(gamePos, ACTION_DECISION);
            packet.writeInt(answer);
            packet.writeBoolean(target != null);
            if (target != null) Buf.writeUUID(packet, target);
            Buf.writeUUID(packet, decision.id);
            MinoNetwork.sendPacketToServer(ID, packet);
        }

        public static void sendDecisionC2S(BlockPos gamePos, PendingDecision decision, int[] answers, UUID target) {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(answers, "answers");
            if (answers.length == 0) return;
            ByteBuf packet = header(gamePos, ACTION_DECISION_BATCH);
            Buf.writeUUID(packet, decision.id);
            packet.writeBoolean(target != null);
            if (target != null) Buf.writeUUID(packet, target);
            packet.writeInt(answers.length);
            for (int answer : answers) packet.writeInt(answer);
            MinoNetwork.sendPacketToServer(ID, packet);
        }
    }
}
