package com.hfstudio.minopp.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.config.ServerConfig;
import com.hfstudio.minopp.effect.EffectEvent;
import com.hfstudio.minopp.effect.EffectEvents;
import com.hfstudio.minopp.effect.SeatActionTakenEffectEvent;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.AutoPlayer;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.game.GameRules;
import com.hfstudio.minopp.game.HandMask;
import com.hfstudio.minopp.game.TableConfig;
import com.hfstudio.minopp.game.Teams;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.network.S2CActionEphemeralPacket;
import com.hfstudio.minopp.network.S2CEffectListPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.NbtUtil;
import com.hfstudio.minopp.util.Pair;
import com.hfstudio.minopp.util.Vec3d;
import com.hfstudio.minopp.util.WorldUtil;

public class BlockEntityMinoTable extends TileEntity {

    public static final int MAX_SEATS = 20;

    /** Seats around the table; index 0 is north, increasing clockwise. Only the first {@link #seatCount()} are used. */
    public final CardPlayer[] seats = new CardPlayer[MAX_SEATS];
    public CardGame game = null;
    public ActionMessage state = ActionMessage.NO_GAME;
    public List<Pair<ActionMessage, Long>> clientMessageList = new ArrayList<>();
    public static final Map<UUID, Long> hideHandUntil = new HashMap<>();
    public ItemStack award = null;
    public boolean demo = false;
    public boolean gameEnd = false;
    public UUID lastPlayedByUuid = null;
    public int lastDiscardSize = 0;
    public Map<UUID, Integer> lastHandSizes = new HashMap<>();
    private int lastHandMoveCounter = 0;

    /** Rules, packs and card counts of this table. */
    public final TableConfig config = new TableConfig();

    public boolean getRule(String name, boolean defaultValue) {
        return GameRules.get(name) == null ? defaultValue : config.getBool(name);
    }

    public static final String RULE_JUMP_IN = GameRules.JUMP_IN;
    public static final String RULE_STACKING = GameRules.STACKING;
    public static final String RULE_SEVEN0 = GameRules.SEVEN_ZERO;

    public int seatCount() {
        return config.getInt(GameRules.MAX_PLAYERS);
    }

    public int getPlayerSeat(UUID uuid) {
        if (uuid == null) return -1;
        for (int i = 0; i < MAX_SEATS; i++) {
            if (seats[i] != null && uuid.equals(seats[i].uuid)) return i;
        }
        return -1;
    }

    public static class HandSwapAnimation {

        public final Vec3d fromPos;
        public final Vec3d toPos;
        public final int cardCount;
        public final long startTime;
        public static final long DURATION_MS = 1200;
        public static final float STAGGER = 0.08f;

        public HandSwapAnimation(Vec3d fromPos, Vec3d toPos, int cardCount) {
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.cardCount = cardCount;
            this.startTime = System.currentTimeMillis();
        }

        public float progress() {
            return Math.min(1f, (System.currentTimeMillis() - startTime) / (float) DURATION_MS);
        }

        public float rawProgress() {
            return (System.currentTimeMillis() - startTime) / (float) DURATION_MS;
        }

        public boolean isDone() {
            int stackSize = Math.max(1, Math.min(cardCount, 10));
            return rawProgress() >= 1f + (stackSize - 1) * STAGGER;
        }
    }

    public static final List<HandSwapAnimation> activeAnimations = new ArrayList<>();

    /** Angle of a seat in radians, 0 = north, clockwise seen from above. */
    public static double seatAngle(int seat, int seatCount) {
        return seat * Math.PI * 2 / Math.max(1, seatCount);
    }

    /** Position of a seat on the edge of the 3x3 table, relative to the core block. */
    public static Vec3d getSeatLocalPos(int seat, int seatCount) {
        double a = seatAngle(seat, seatCount);
        double dx = Math.sin(a), dz = -Math.cos(a);
        double scale = 1.5 / Math.max(Math.abs(dx), Math.abs(dz));
        return new Vec3d(1.5 + dx * scale, 0.94, 1.5 + dz * scale);
    }

    public Vec3d getSeatLocalPos(int seat) {
        return getSeatLocalPos(seat, seatCount());
    }

    public BlockPos getBlockPos() {
        return BlockPos.of(this);
    }

    @Override
    public void writeToNBT(NBTTagCompound compoundTag) {
        super.writeToNBT(compoundTag);
        saveAdditional(compoundTag);
    }

    protected void saveAdditional(NBTTagCompound compoundTag) {
        NBTTagCompound playersTag = new NBTTagCompound();
        for (int i = 0; i < MAX_SEATS; i++) {
            if (seats[i] != null) playersTag.setTag("seat" + i, seats[i].toTag());
        }
        compoundTag.setTag("seats", playersTag);
        if (game != null) compoundTag.setTag("game", game.toTag());
        compoundTag.setTag("state", state.toTag());
        if (award != null) compoundTag.setTag("award", award.writeToNBT(new NBTTagCompound()));
        compoundTag.setBoolean("demo", demo);
        compoundTag.setTag("config", config.toTag());
        compoundTag.setBoolean("gameEnd", gameEnd);
        if (lastPlayedByUuid != null) NbtUtil.putUUID(compoundTag, "lastPlayedBy", lastPlayedByUuid);
        compoundTag.setInteger("lastDiscardSize", lastDiscardSize);
    }

    @Override
    public void readFromNBT(NBTTagCompound compoundTag) {
        super.readFromNBT(compoundTag);
        NBTTagCompound playersTag = compoundTag.getCompoundTag("seats");
        for (int i = 0; i < MAX_SEATS; i++) {
            seats[i] = playersTag.hasKey("seat" + i) ? new CardPlayer(playersTag.getCompoundTag("seat" + i)) : null;
            if (seats[i] != null) seats[i].seat = i;
        }
        CardGame previousGame = game;
        if (compoundTag.hasKey("game")) {
            game = new CardGame(compoundTag.getCompoundTag("game"));
            game.tableEntity = this;
        } else {
            game = null;
        }
        ActionMessage newState = new ActionMessage(compoundTag.getCompoundTag("state"));
        if (!newState.equals(state)) {
            if (previousGame == null && game != null) {
                clientMessageList.clear();
            } else {
                clientMessageList.add(new Pair<>(state, System.currentTimeMillis() + 16000));
            }
            state = newState;
            clientMessageList.removeIf(
                entry -> entry.getFirst()
                    .type() == ActionMessage.Type.FAIL);
        }
        award = compoundTag.hasKey("award") ? ItemStack.loadItemStackFromNBT(compoundTag.getCompoundTag("award"))
            : null;
        demo = compoundTag.hasKey("demo", 1) && compoundTag.getBoolean("demo");
        if (compoundTag.hasKey("config")) {
            config.fromTag(compoundTag.getCompoundTag("config"));
        } else if (compoundTag.hasKey("rules")) {
            // Table saved by the 1:1 port: plain rules map
            NBTTagCompound legacy = new NBTTagCompound();
            legacy.setTag("rules", compoundTag.getCompoundTag("rules"));
            config.fromTag(legacy);
        }
        gameEnd = compoundTag.getBoolean("gameEnd");

        // used for animation tracking
        lastPlayedByUuid = NbtUtil.hasUUID(compoundTag, "lastPlayedBy") ? NbtUtil.getUUID(compoundTag, "lastPlayedBy")
            : null;

        // intialise tracking values
        if (game != null && previousGame == null) {
            lastDiscardSize = game.discardDeck.size();
            lastHandMoveCounter = game.handMoveCounter;
            lastHandSizes.clear();
            for (CardPlayer p : game.players) lastHandSizes.put(p.uuid, p.hand.size());
        }

        // Animation detection
        if (worldObj != null && worldObj.isRemote && game != null && previousGame != null) {
            detectAnimations();
        }

        // Reset tracking values
        if (game == null) {
            lastDiscardSize = 0;
            lastPlayedByUuid = null;
            lastHandSizes.clear();
        }
    }

    private void detectAnimations() {
        boolean handsMoved = game.handMoveCounter != lastHandMoveCounter;
        lastHandMoveCounter = game.handMoveCounter;
        if (handsMoved) {
            List<CardPlayer> active = game.activePlayers();
            switch (game.handMoveKind) {
                case 0 -> { // 0 card: everyone passes their hand on
                    int n = active.size();
                    for (int i = 0; i < n; i++) {
                        CardPlayer from = active.get(i);
                        CardPlayer to = active.get(game.isAntiClockwise ? (i - 1 + n) % n : (i + 1) % n);
                        activeAnimations
                            .add(new HandSwapAnimation(getSeatLocalPos(from.seat), getSeatLocalPos(to.seat), 5));
                    }
                }
                case 7 -> { // 7 card / forced swap
                    CardPlayer fromP = game.deAmputate(game.lastSwapFromUuid);
                    CardPlayer toP = game.deAmputate(game.lastSwapToUuid);
                    if (fromP != null && toP != null) {
                        activeAnimations.add(
                            new HandSwapAnimation(
                                getSeatLocalPos(fromP.seat),
                                getSeatLocalPos(toP.seat),
                                toP.hand.size()));
                        activeAnimations.add(
                            new HandSwapAnimation(
                                getSeatLocalPos(toP.seat),
                                getSeatLocalPos(fromP.seat),
                                fromP.hand.size()));
                    }
                }
                case 1 -> { // shuffle: everything goes to the middle
                    for (CardPlayer p : active) {
                        activeAnimations
                            .add(new HandSwapAnimation(getSeatLocalPos(p.seat), new Vec3d(1.5, 0.94, 1.5), 4));
                    }
                }
                default -> {}
            }
        } else {
            // Draw animation - skip entirely if hands just moved
            for (CardPlayer p : game.players) {
                if (p.uuid.equals(lastPlayedByUuid)) continue;
                Integer prevBoxed = lastHandSizes.get(p.uuid);
                int prev = prevBoxed == null ? 0 : prevBoxed;
                int curr = p.hand.size();
                if (curr > prev) {
                    activeAnimations
                        .add(new HandSwapAnimation(new Vec3d(0.5, 0.94, 0.5), getSeatLocalPos(p.seat), curr - prev));
                }
            }
        }

        lastDiscardSize = game.discardDeck.size();
        lastHandSizes.clear();
        for (CardPlayer p : game.players) lastHandSizes.put(p.uuid, p.hand.size());
    }

    public List<CardPlayer> getPlayersList() {
        List<CardPlayer> playersList = new ArrayList<>();
        int n = seatCount();
        for (int i = 0; i < n; i++) {
            if (seats[i] != null) playersList.add(seats[i]);
        }
        return playersList;
    }

    private static final int PLAYER_RANGE = 20;

    /** Seats a player at the free seat closest to where they stand. */
    public void joinPlayerToTable(CardPlayer cardPlayer, Vec3d playerPos) {
        if (game != null) return;
        int n = seatCount();
        double dx = playerPos.x - (xCoord + 1.5), dz = playerPos.z - (zCoord + 1.5);
        double angle = Math.atan2(dx, -dz);
        if (angle < 0) angle += Math.PI * 2;
        int preferred = (int) Math.round(angle / (Math.PI * 2 / n)) % n;

        // moving to another seat keeps the team
        int previousTeam = -1;
        for (int i = 0; i < MAX_SEATS; i++) {
            if (cardPlayer.equals(seats[i])) {
                previousTeam = seats[i].team;
                seats[i] = null;
            }
        }
        int chosen = -1;
        for (int d = 0; d <= n / 2 && chosen < 0; d++) {
            for (int sign : new int[] { 1, -1 }) {
                int idx = ((preferred + sign * d) % n + n) % n;
                if (seats[idx] == null) {
                    chosen = idx;
                    break;
                }
            }
        }
        if (chosen < 0) {
            // Table full: take the preferred seat like the original did (replacing its occupant)
            chosen = preferred;
        }
        cardPlayer.seat = chosen;
        cardPlayer.team = previousTeam;
        seats[chosen] = cardPlayer;
        // newcomers join the smallest team
        Teams.normalize(getPlayersList(), Teams.count(config));
        sync();
    }

    /** Applies a seat count change, removing players from seats that no longer exist. */
    public void onSeatCountChanged() {
        int n = seatCount();
        for (int i = n; i < MAX_SEATS; i++) seats[i] = null;
        Teams.normalize(getPlayersList(), Teams.count(config));
    }

    /** Why a game cannot start right now (team sizes), or null. */
    public net.minecraft.util.IChatComponent startProblem() {
        if (!Teams.enabled(config)) return null;
        List<CardPlayer> seated = getPlayersList();
        Teams.normalize(seated, Teams.count(config));
        return Teams.problem(seated, Teams.count(config));
    }

    /** Moves a seated player to a team (lobby only). */
    public boolean setTeam(java.util.UUID uuid, int team) {
        if (game != null || !Teams.enabled(config) || team < 0 || team >= Teams.count(config)) return false;
        for (CardPlayer p : getPlayersList()) {
            if (p.uuid.equals(uuid)) {
                p.team = team;
                sync();
                return true;
            }
        }
        return false;
    }

    /** Spreads the seated players evenly over the teams, alternating around the table. */
    public void autoTeams() {
        if (game != null || !Teams.enabled(config)) return;
        Teams.autoAssign(getPlayersList(), Teams.count(config));
        sync();
    }

    public void startGame(CardPlayer initiator) {
        if (game != null) return;
        List<CardPlayer> playerList = getPlayersList();
        if (playerList.size() < 2) return;
        net.minecraft.util.IChatComponent teamProblem = startProblem();
        if (teamProblem != null) {
            state = ActionReport.builder(initiator)
                .panic(teamProblem).state;
            sync();
            return;
        }

        double r = PLAYER_RANGE / 2.0;
        AxisAlignedBB searchArea = AxisAlignedBB.getBoundingBox(
            xCoord + 1 - r,
            yCoord + 1 - r,
            zCoord + 1 - r,
            xCoord + 1 + r,
            yCoord + 1 + r,
            zCoord + 1 + r);
        List<?> entities = worldObj.getEntitiesWithinAABB(Entity.class, searchArea);
        for (CardPlayer cardPlayer : playerList) {
            boolean playerFound = false;
            for (Object o : entities) {
                Entity entity = (Entity) o;
                if (entity instanceof EntityPlayer mcPlayer) {
                    if (cardPlayer.uuid.equals(
                        mcPlayer.getGameProfile()
                            .getId())) {
                        giveHandCards(mcPlayer, cardPlayer);
                        playerFound = true;
                    }
                } else if (cardPlayer.uuid.equals(entity.getUniqueID())) {
                    playerFound = true;
                }
                if (playerFound) break;
            }
            if (!playerFound) {
                destroyGame(initiator);
                state = ActionReport.builder(initiator)
                    .panic(Chat.translatable("game.minopp.play.player_unavailable", cardPlayer.name)).state;
                return;
            }
        }

        List<CardPlayer> gamePlayers = new ArrayList<>();
        for (CardPlayer p : playerList) {
            p.hand.clear();
            p.hasShoutedMino = false;
            gamePlayers.add(p.seatCopy());
        }
        game = new CardGame(gamePlayers);
        game.tableEntity = this;
        state = game.initiate(initiator, config.getInt(GameRules.INITIAL_HAND)).state;
        lastHandMoveCounter = 0;
        sendSeatActionTakenToAll();
        sync();
    }

    private void giveHandCards(EntityPlayer mcPlayer, CardPlayer cardPlayer) {
        ItemStack handCard = new ItemStack(Mino.ITEM_HAND_CARDS);
        ItemDataUtils.setCardGameBinding(handCard, getBlockPos(), cardPlayer.uuid);
        InventoryPlayer inv = mcPlayer.inventory;
        if (inv.currentItem < InventoryPlayer.getHotbarSize() && inv.getCurrentItem() == null) {
            inv.setInventorySlotContents(inv.currentItem, handCard);
        } else {
            boolean addSuccessful = inv.addItemStackToInventory(handCard);
            if (!addSuccessful) {
                EntityItem itemEntity = mcPlayer.dropPlayerItemWithRandomChoice(handCard, false);
                if (itemEntity != null) {
                    itemEntity.delayBeforeCanPickup = 0;
                    itemEntity.func_145797_a(mcPlayer.getCommandSenderName());
                }
            }
            mcPlayer.addChatComponentMessage(Chat.translatable("game.minopp.play.hand_card_in_inventory"));
        }
        mcPlayer.inventoryContainer.detectAndSendChanges();
    }

    public void destroyGame(CardPlayer initiator) {
        if (game != null) sendSeatActionTakenToAll();
        gameEnd = true;
        game = null;

        for (Object o : worldObj.playerEntities) {
            EntityPlayer mcPlayer = (EntityPlayer) o;
            ItemStack[] items = mcPlayer.inventory.mainInventory;
            for (int i = 0; i < items.length; i++) {
                ItemStack invItem = items[i];
                if (invItem == null || invItem.getItem() != Mino.ITEM_HAND_CARDS) continue;
                BlockPos tablePos = ItemDataUtils.getBlockPos(invItem);
                if (tablePos != null && tablePos.equals(getBlockPos())) items[i] = null;
            }
            mcPlayer.inventoryContainer.detectAndSendChanges();
        }

        for (CardPlayer cardPlayer : getPlayersList()) {
            Entity entity = WorldUtil.getEntityByUUID(worldObj, cardPlayer.uuid);
            if (entity instanceof EntityLivingBase livingEntity && !(entity instanceof EntityPlayer)) {
                ItemStack stack = livingEntity.getHeldItem();
                if (stack != null && stack.getItem() == Mino.ITEM_HAND_CARDS)
                    livingEntity.setCurrentItemOrArmor(0, null);
            }
            cardPlayer.hand.clear();
            cardPlayer.hasShoutedMino = false;
        }
        if (initiator != null) state = ActionReport.builder(initiator)
            .gameDestroyed().state;
        sync();
    }

    public void resetSeats(CardPlayer initiator) {
        sendSeatActionTakenToAll();
        for (int i = 0; i < MAX_SEATS; i++) seats[i] = null;
        state = ActionReport.builder(initiator)
            .panic(Chat.translatable("game.minopp.play.seats_reset", initiator.name)).state;
        sync();
    }

    public void handleActionResult(ActionReport result, CardPlayer cardPlayer, EntityPlayerMP player) {
        if (result != null) {
            if (result.shouldDestroyGame) destroyGame(cardPlayer);
            if (result.state != null) state = result.state;
            for (ActionMessage message : result.messages) {
                switch (message.type()) {
                    case FAIL -> {
                        if (player != null) S2CActionEphemeralPacket.sendS2C(player, getBlockPos(), message);
                    }
                    case MESSAGE_ALL -> sendMessageToAll(message);
                    default -> {}
                }
            }
            if (!result.effects.isEmpty()) {
                BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
                for (EffectEvent effect : result.effects) {
                    effect.summonServer((WorldServer) worldObj, tableCenterPos, this);
                }
                for (Object o : worldObj.playerEntities) {
                    EntityPlayerMP serverPlayer = (EntityPlayerMP) o;
                    double dx = serverPlayer.posX - (tableCenterPos.x + 0.5);
                    double dy = serverPlayer.posY - (tableCenterPos.y + 0.5);
                    double dz = serverPlayer.posZ - (tableCenterPos.z + 0.5);
                    if (dx * dx + dy * dy + dz * dz <= EffectEvents.EFFECT_RADIUS * EffectEvents.EFFECT_RADIUS) {
                        UUID id = serverPlayer.getGameProfile()
                            .getId();
                        boolean playerPartOfGame = getPlayersList().stream()
                            .anyMatch(p -> p.uuid.equals(id));
                        S2CEffectListPacket.sendS2C(serverPlayer, result.effects, tableCenterPos, playerPartOfGame);
                    }
                }
            }
        }
        sync();
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote || game == null || game.decisions.isEmpty()) return;
        if (worldObj.getTotalWorldTime() % 10 != 0) return;
        MinecraftServer server = MinecraftServer.getServer();
        ActionReport report = game
            .tickDecisions(System.currentTimeMillis(), uuid -> AutoPlayer.isOnlinePlayer(server, uuid));
        if (report != null) handleActionResult(report, game != null ? game.currentPlayer() : null, null);
    }

    private void sendMessageToAll(ActionMessage message) {
        for (CardPlayer player : getPlayersList()) {
            EntityPlayer mcPlayer = WorldUtil.getPlayerByUUID(worldObj, player.uuid);
            if (mcPlayer instanceof EntityPlayerMP) {
                S2CActionEphemeralPacket.sendS2C((EntityPlayerMP) mcPlayer, getBlockPos(), message);
            }
        }
    }

    public void sendSeatActionTakenToAll() {
        for (CardPlayer player : getPlayersList()) {
            EntityPlayer mcPlayer = WorldUtil.getPlayerByUUID(worldObj, player.uuid);
            BlockPos tableCenterPos = getBlockPos().offset(1, 0, 1);
            List<EffectEvent> events = List.of(new SeatActionTakenEffectEvent());
            if (mcPlayer instanceof EntityPlayerMP) {
                S2CEffectListPacket.sendS2C((EntityPlayerMP) mcPlayer, events, tableCenterPos, true);
            }
        }
    }

    public void sync() {
        markDirty();
        if (worldObj == null) return;
        if (worldObj.isRemote || ServerConfig.gameplay.allowPeeking || game == null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return;
        }
        // Without peeking every viewer gets their own copy, revealing only their own hand
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        double range = Math.max(
            4,
            MinecraftServer.getServer()
                .getConfigurationManager()
                .getViewDistance())
            * 16 + 16;
        for (Object o : worldObj.playerEntities) {
            if (!(o instanceof EntityPlayerMP player)) continue;
            if (player.getDistanceSq(xCoord + 1.5, player.posY, zCoord + 1.5) > range * range) continue;
            sendTo(player, tag);
        }
    }

    /** Sends this table's state to one player, with other players' cards hidden unless peeking is allowed. */
    public void sendTo(EntityPlayerMP player) {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        sendTo(player, tag);
    }

    private void sendTo(EntityPlayerMP player, NBTTagCompound fullTag) {
        NBTTagCompound tag = ServerConfig.gameplay.allowPeeking ? fullTag
            : HandMask.maskTableTag(
                fullTag,
                player.getGameProfile()
                    .getId());
        player.playerNetServerHandler.sendPacket(new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag));
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        // Sent to whoever loads the chunk: hide every hand; ChunkWatchEvent then sends the viewer their own hand
        if (!ServerConfig.gameplay.allowPeeking) tag = HandMask.maskTableTag(tag, null);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 64 * 64;
    }
}
