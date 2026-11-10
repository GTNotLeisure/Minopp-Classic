package com.hfstudio.minopp.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.config.ServerConfig;
import com.hfstudio.minopp.effect.EffectEvent;
import com.hfstudio.minopp.effect.EffectEvents;
import com.hfstudio.minopp.effect.SeatActionTakenEffectEvent;
import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.AutoPlayer;
import com.hfstudio.minopp.game.Card;
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

    public final CardPlayer[] seats = new CardPlayer[MAX_SEATS];
    public CardGame game = null;
    public ActionMessage state = ActionMessage.NO_GAME;
    public List<Pair<ActionMessage, Long>> clientMessageList = new ArrayList<>();
    public static final Map<UUID, Long> hideHandUntil = new HashMap<>();
    public ItemStack award = null;
    public boolean demo = false;
    public boolean gameEnd = false;
    public UUID lastPlayedByUuid = null;
    public UUID choosingUuid;
    public boolean choosingRing;
    public int choosingHover = -1;
    /**
     * Client side: when the current choice was first seen, for its pop-in.
     */
    public long choosingSeenAt;
    public UUID ownerUuid;
    public String ownerName = "";
    public UUID hostUuid;
    public String hostName = "";

    /**
     * Who runs the room: the player who placed the table while they are online, otherwise the first online player
     * seated at it. Null when nobody qualifies, and then anyone may manage it.
     */
    public UUID currentHost() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return hostUuid;
        Set<UUID> online = new HashSet<>();
        for (EntityPlayerMP o : server.getConfigurationManager().playerEntityList) {
            online.add(
                o.getGameProfile()
                    .getId());
        }
        if (ownerUuid != null && online.contains(ownerUuid)) {
            hostName = ownerName;
            return ownerUuid;
        }
        for (CardPlayer p : getPlayersList()) {
            if (online.contains(p.uuid)) {
                hostName = p.name;
                return p.uuid;
            }
        }
        hostName = "";
        return null;
    }

    /**
     * Operators and the single player world's owner can do anything; otherwise only the room's host can.
     */
    public boolean canManage(EntityPlayer player) {
        if (player.canCommandSenderUseCommand(2, "minopp")) return true;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.isSinglePlayer()
            && player.getCommandSenderName()
                .equals(server.getServerOwner()))
            return true;
        UUID host = currentHost();
        return host == null || host.equals(
            player.getGameProfile()
                .getId());
    }

    /**
     * Everyone sees every hand only when the server allows it and the room turned it on.
     */
    public boolean peekAll() {
        return ServerConfig.gameplay.allowPeeking && config.getBool(GameRules.PEEK_HANDS);
    }

    public int lastDiscardSize = 0;
    public Map<UUID, Integer> lastHandSizes = new HashMap<>();
    private int lastHandMoveCounter = 0;

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
        /**
         * The cards in flight, whose backs show (their other side in Flip); empty for plain backs.
         */
        public final List<Card> cards;
        public final long startTime;
        public static final long DURATION_MS = 1200;
        public static final float STAGGER = 0.08f;

        public HandSwapAnimation(Vec3d fromPos, Vec3d toPos, int cardCount, List<Card> cards) {
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.cardCount = cardCount;
            this.cards = cards.isEmpty() ? Collections.emptyList()
                : new ArrayList<>(cards.subList(Math.max(0, cards.size() - 10), cards.size()));
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

    /**
     * Client side: hand swaps flying across this table, in table space.
     */
    public final List<HandSwapAnimation> activeAnimations = new ArrayList<>();

    /**
     * Angle of a seat in radians, 0 = north, clockwise seen from above.
     */
    public static double seatAngle(int seat, int seatCount) {
        return seat * Math.PI * 2 / Math.max(1, seatCount);
    }

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
        if (ownerUuid != null) NbtUtil.putUUID(compoundTag, "owner", ownerUuid);
        if (choosingUuid != null) {
            NbtUtil.putUUID(compoundTag, "chooser", choosingUuid);
            compoundTag.setBoolean("chooserRing", choosingRing);
            compoundTag.setInteger("chooserHover", choosingHover);
        }
        compoundTag.setString("ownerName", ownerName);
        if (worldObj != null && !worldObj.isRemote) hostUuid = currentHost();
        if (hostUuid != null) NbtUtil.putUUID(compoundTag, "host", hostUuid);
        compoundTag.setString("hostName", hostName);
        compoundTag.setInteger("lastDiscardSize", lastDiscardSize);
        NBTTagCompound scoresTag = new NBTTagCompound();
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("points", e.getValue());
            entry.setString("name", scoreNames.getOrDefault(e.getKey(), "?"));
            scoresTag.setTag(
                e.getKey()
                    .toString(),
                entry);
        }
        compoundTag.setTag("scores", scoresTag);
        NBTTagList settleTag = new NBTTagList();
        for (Settlement s : settlement) settleTag.appendTag(s.toTag());
        compoundTag.setTag("settlement", settleTag);
        compoundTag.setInteger("settleTarget", settleTarget);
        compoundTag.setString("matchWinner", matchWinner);
    }

    @Override
    public void readFromNBT(NBTTagCompound compoundTag) {
        super.readFromNBT(compoundTag);
        scores.clear();
        scoreNames.clear();
        NBTTagCompound scoresTag = compoundTag.getCompoundTag("scores");
        for (String k : scoresTag.func_150296_c()) {
            try {
                UUID id = UUID.fromString(k);
                NBTTagCompound entry = scoresTag.getCompoundTag(k);
                scores.put(id, entry.getInteger("points"));
                scoreNames.put(id, entry.getString("name"));
            } catch (IllegalArgumentException ignored) {}
        }
        settlement.clear();
        NBTTagList settleTag = compoundTag.getTagList("settlement", 10);
        for (int i = 0; i < settleTag.tagCount() && i < MAX_SEATS; i++)
            settlement.add(Settlement.fromTag(settleTag.getCompoundTagAt(i)));
        settleTarget = compoundTag.getInteger("settleTarget");
        matchWinner = compoundTag.getString("matchWinner");
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
            // Legacy saves store rules directly in the table tag.
            NBTTagCompound legacy = new NBTTagCompound();
            legacy.setTag("rules", compoundTag.getCompoundTag("rules"));
            config.fromTag(legacy);
        }
        gameEnd = compoundTag.getBoolean("gameEnd");

        ownerUuid = NbtUtil.hasUUID(compoundTag, "owner") ? NbtUtil.getUUID(compoundTag, "owner") : null;
        UUID chooser = NbtUtil.hasUUID(compoundTag, "chooser") ? NbtUtil.getUUID(compoundTag, "chooser") : null;
        if (chooser != null && !chooser.equals(choosingUuid)) choosingSeenAt = System.currentTimeMillis();
        choosingUuid = chooser;
        choosingRing = compoundTag.getBoolean("chooserRing");
        choosingHover = chooser == null ? -1 : compoundTag.getInteger("chooserHover");
        ownerName = compoundTag.getString("ownerName");
        hostUuid = NbtUtil.hasUUID(compoundTag, "host") ? NbtUtil.getUUID(compoundTag, "host") : null;
        hostName = compoundTag.getString("hostName");
        lastPlayedByUuid = NbtUtil.hasUUID(compoundTag, "lastPlayedBy") ? NbtUtil.getUUID(compoundTag, "lastPlayedBy")
            : null;

        if (game != null && previousGame == null) {
            lastDiscardSize = game.discardDeck.size();
            lastHandMoveCounter = game.handMoveCounter;
            lastHandSizes.clear();
            for (CardPlayer p : game.players) lastHandSizes.put(p.uuid, p.hand.size());
        }

        if (worldObj != null && worldObj.isRemote && game != null && previousGame != null) {
            detectAnimations();
        }

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
                case 0 -> {
                    int n = active.size();
                    for (int i = 0; i < n; i++) {
                        CardPlayer from = active.get(i);
                        CardPlayer to = active.get(game.isAntiClockwise ? (i - 1 + n) % n : (i + 1) % n);
                        activeAnimations.add(
                            new HandSwapAnimation(getSeatLocalPos(from.seat), getSeatLocalPos(to.seat), 5, to.hand));
                    }
                }
                case 7 -> {
                    CardPlayer fromP = game.deAmputate(game.lastSwapFromUuid);
                    CardPlayer toP = game.deAmputate(game.lastSwapToUuid);
                    if (fromP != null && toP != null) {
                        activeAnimations.add(
                            new HandSwapAnimation(
                                getSeatLocalPos(fromP.seat),
                                getSeatLocalPos(toP.seat),
                                toP.hand.size(),
                                toP.hand));
                        activeAnimations.add(
                            new HandSwapAnimation(
                                getSeatLocalPos(toP.seat),
                                getSeatLocalPos(fromP.seat),
                                fromP.hand.size(),
                                fromP.hand));
                    }
                }
                case 1 -> {
                    for (CardPlayer p : active) {
                        activeAnimations
                            .add(new HandSwapAnimation(getSeatLocalPos(p.seat), new Vec3d(1.5, 0.94, 1.5), 4, p.hand));
                    }
                }
                default -> {}
            }
        } else {
            for (CardPlayer p : game.players) {
                if (p.uuid.equals(lastPlayedByUuid)) continue;
                Integer prevBoxed = lastHandSizes.get(p.uuid);
                int prev = prevBoxed == null ? 0 : prevBoxed;
                int curr = p.hand.size();
                if (curr > prev) {
                    activeAnimations.add(
                        new HandSwapAnimation(
                            new Vec3d(0.5, 0.94, 0.5),
                            getSeatLocalPos(p.seat),
                            curr - prev,
                            p.hand.subList(prev, curr)));
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

    public void joinPlayerToTable(CardPlayer cardPlayer, Vec3d playerPos) {
        if (game != null) return;
        int n = seatCount();
        double dx = playerPos.x - (xCoord + 1.5), dz = playerPos.z - (zCoord + 1.5);
        double angle = Math.atan2(dx, -dz);
        if (angle < 0) angle += Math.PI * 2;
        int preferred = (int) Math.round(angle / (Math.PI * 2 / n)) % n;

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
            chosen = preferred;
        }
        cardPlayer.seat = chosen;
        cardPlayer.team = previousTeam;
        seats[chosen] = cardPlayer;
        Teams.normalize(getPlayersList(), Teams.count(config));
        sync();
    }

    private static final String[] BOT_NAMES = { "Pudding", "Luna", "Dusty", "Pepper", "Mochi", "Biscuit", "Nova",
        "Ziggy", "Clover", "Maple", "Pixel", "Sprout", "Tofu", "Juniper", "Waffle", "Comet", "Hazel", "Noodle", "Bean",
        "Pebble" };

    private String freeBotName() {
        Set<String> used = new HashSet<>();
        for (CardPlayer p : getPlayersList()) used.add(p.name);
        for (String n : BOT_NAMES) if (!used.contains(n)) return n;
        return "MinoBot #" + (100 + worldObj.rand.nextInt(900));
    }

    public int addBots(int max) {
        if (game != null || worldObj == null || worldObj.isRemote) return 0;
        int n = seatCount(), added = 0;
        for (int i = 0; i < n && added < max; i++) {
            if (seats[i] != null) continue;
            Vec3d seat = getSeatLocalPos(i, n);
            double ox = seat.x - 1.5, oz = seat.z - 1.5;
            Vec3d spot = botSpot(i);
            EntityAutoPlayer bot = new EntityAutoPlayer(worldObj);
            bot.setPosition(spot.x, spot.y, spot.z);
            bot.rotationYaw = (float) Math.toDegrees(Math.atan2(-ox, oz)) + 180;
            worldObj.spawnEntityInWorld(bot);
            bot.seatAt(this, freeBotName());
            added++;
        }
        return added;
    }

    public int removeBots(int max) {
        if (game != null || worldObj == null || worldObj.isRemote) return 0;
        int removed = 0;
        for (int i = MAX_SEATS - 1; i >= 0 && removed < max; i--) {
            if (seats[i] == null) continue;
            Entity e = WorldUtil.getEntityByUUID(worldObj, seats[i].uuid);
            if (e instanceof EntityAutoPlayer) {
                e.setDead();
                seats[i] = null;
                removed++;
            }
        }
        if (removed > 0) sync();
        return removed;
    }

    public boolean leaveSeat(UUID uuid) {
        if (game != null) return false;
        for (int i = 0; i < MAX_SEATS; i++) {
            if (seats[i] != null && seats[i].uuid.equals(uuid)) {
                seats[i] = null;
                sync();
                return true;
            }
        }
        return false;
    }

    public void onSeatCountChanged() {
        int n = seatCount();
        for (int i = n; i < MAX_SEATS; i++) seats[i] = null;
        Teams.normalize(getPlayersList(), Teams.count(config));
    }

    public IChatComponent startProblem() {
        if (!Teams.enabled(config)) return null;
        List<CardPlayer> seated = getPlayersList();
        Teams.normalize(seated, Teams.count(config));
        return Teams.problem(seated, Teams.count(config));
    }

    public boolean setTeam(UUID uuid, int team) {
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

    public void autoTeams() {
        if (game != null || !Teams.enabled(config)) return;
        Teams.autoAssign(getPlayersList(), Teams.count(config));
        sync();
    }

    public void startGame(CardPlayer initiator) {
        startGame(initiator, null);
    }

    public void startGame(CardPlayer initiator, UUID debugger) {
        if (game != null) return;
        List<CardPlayer> playerList = getPlayersList();
        if (playerList.size() < (debugger != null ? 1 : 2)) return;
        IChatComponent teamProblem = debugger != null ? null : startProblem();
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
        if (debugger != null) {
            game.debug = true;
            game.debugger = debugger;
            game.debugHold = true;
        }
        settlement.clear();
        matchWinner = "";
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

        for (EntityPlayer o : worldObj.playerEntities) {
            EntityPlayer mcPlayer = o;
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
        scores.clear();
        scoreNames.clear();
        for (int i = 0; i < MAX_SEATS; i++) seats[i] = null;
        state = ActionReport.builder(initiator)
            .panic(Chat.translatable("game.minopp.play.seats_reset", initiator.name)).state;
        sync();
    }

    public final Map<UUID, Integer> scores = new LinkedHashMap<>();
    public final Map<UUID, String> scoreNames = new HashMap<>();

    public record Settlement(String name, int cards, int points, int gained, int total, boolean winner) {

        NBTTagCompound toTag() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("name", name);
            t.setInteger("cards", cards);
            t.setInteger("points", points);
            t.setInteger("gained", gained);
            t.setInteger("total", total);
            t.setBoolean("winner", winner);
            return t;
        }

        static Settlement fromTag(NBTTagCompound t) {
            return new Settlement(
                t.getString("name"),
                t.getInteger("cards"),
                t.getInteger("points"),
                t.getInteger("gained"),
                t.getInteger("total"),
                t.getBoolean("winner"));
        }
    }

    public final List<Settlement> settlement = new ArrayList<>();
    public int settleTarget;
    public String matchWinner = "";

    private void scoreRound(List<CardPlayer> winners, ActionReport result) {
        if (game == null || winners == null || winners.isEmpty()) return;
        int target = config.getInt(GameRules.SCORE_TARGET);
        List<CardPlayer> losers = new ArrayList<>(game.players);
        losers.removeAll(winners);
        int points = game.handPoints(losers);
        settlement.clear();
        settleTarget = target;
        matchWinner = "";
        CardPlayer best = null;
        int bestScore = -1;
        for (CardPlayer w : winners) {
            int total = target > 0 ? scores.merge(w.uuid, points, Integer::sum) : points;
            if (target > 0) {
                scoreNames.put(w.uuid, w.name);
                sendMessageToAll(
                    new ActionMessage(
                        ActionMessage.Type.MESSAGE_ALL,
                        Chat.translatable("game.minopp.play.points", w.name, points, total, target)));
            }
            settlement.add(new Settlement(w.name, w.hand.size(), 0, points, total, true));
            if (total > bestScore) {
                bestScore = total;
                best = w;
            }
        }
        losers.sort(Comparator.comparingInt(p -> game.handPoints(List.of(p))));
        for (CardPlayer p : losers) {
            int own = game.handPoints(List.of(p));
            settlement.add(new Settlement(p.name, p.hand.size(), own, 0, scores.getOrDefault(p.uuid, 0), false));
        }
        if (target > 0 && best != null && bestScore >= target) {
            matchWinner = best.name;
            sendMessageToAll(
                new ActionMessage(
                    ActionMessage.Type.MESSAGE_ALL,
                    Chat.translatable("game.minopp.play.match_won", best.name, bestScore)));
            scores.clear();
            scoreNames.clear();
        }
    }

    public void handleActionResult(ActionReport result, CardPlayer cardPlayer, EntityPlayerMP player) {
        if (result != null && !result.isFail) choosingUuid = null;
        if (result != null) {
            if (result.shouldDestroyGame) {
                scoreRound(result.winners, result);
                destroyGame(cardPlayer);
            }
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
        if (worldObj.isRemote || game == null) return;
        long time = worldObj.getTotalWorldTime();
        if (time % 10 != 0) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (!game.decisions.isEmpty()) {
            ActionReport report = game
                .tickDecisions(System.currentTimeMillis(), uuid -> AutoPlayer.isOnlinePlayer(server, uuid));
            if (report != null) handleActionResult(report, game != null ? game.currentPlayer() : null, null);
            return;
        }
        if (time % 20 == 0) tickAbsent(server);
    }

    private static final long ABSENT_GRACE_MS = 15_000, STAND_IN_DELAY_MS = 2_000;
    private final AutoPlayer standIn = new AutoPlayer();
    private final Set<UUID> standingIn = new HashSet<>();
    private UUID absentId;
    private long absentSince;

    private boolean present(MinecraftServer server, UUID uuid) {
        return AutoPlayer.isOnlinePlayer(server, uuid)
            || WorldUtil.getEntityByUUID(worldObj, uuid) instanceof EntityAutoPlayer;
    }

    // Play disconnected seats automatically until their players return.
    private void tickAbsent(MinecraftServer server) {
        if (game.awaitingCutIn) return;
        CardPlayer current = game.currentPlayer();
        if (current == null || present(server, current.uuid)) {
            if (current != null) standingIn.remove(current.uuid);
            absentId = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (!current.uuid.equals(absentId)) {
            absentId = current.uuid;
            absentSince = now;
            return;
        }
        if (now - absentSince < (standingIn.contains(current.uuid) ? STAND_IN_DELAY_MS : ABSENT_GRACE_MS)) return;
        if (standingIn.add(current.uuid)) {
            sendMessageToAll(
                new ActionMessage(
                    ActionMessage.Type.MESSAGE_ALL,
                    Chat.translatable("game.minopp.play.stand_in", current.name)));
        }
        standIn.aiForgetChance = 0;
        CardPlayer real = game.deAmputate(current);
        absentId = null;
        handleActionResult(standIn.playAtGame(game, real, server), real, null);
    }

    public int seatOf(UUID uuid) {
        for (int i = 0; i < MAX_SEATS; i++) if (seats[i] != null && seats[i].uuid.equals(uuid)) return i;
        return -1;
    }

    public Vec3d botSpot(int seat) {
        Vec3d local = getSeatLocalPos(seat, seatCount());
        double ox = local.x - 1.5, oz = local.z - 1.5;
        double len = Math.max(1e-3, Math.sqrt(ox * ox + oz * oz));
        return new Vec3d(xCoord + local.x + ox / len * 0.45, yCoord, zCoord + local.z + oz / len * 0.45);
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
        if (worldObj.isRemote || peekAll() || game == null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return;
        }
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

    public void sendTo(EntityPlayerMP player) {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        sendTo(player, tag);
    }

    private void sendTo(EntityPlayerMP player, NBTTagCompound fullTag) {
        UUID viewer = player.getGameProfile()
            .getId();
        boolean reveal = peekAll() || game != null && game.isPeeking(viewer);
        NBTTagCompound tag = reveal ? fullTag
            : HandMask.maskTableTag(fullTag, viewer, config.getBool(GameRules.FLIP_BACKS));
        player.playerNetServerHandler.sendPacket(new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag));
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        // ChunkWatchEvent sends each viewer their hand after this masked chunk update.
        if (!peekAll()) tag = HandMask.maskTableTag(tag, null, config.getBool(GameRules.FLIP_BACKS));
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 5, yCoord + 3, zCoord + 5);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 64 * 64;
    }
}
