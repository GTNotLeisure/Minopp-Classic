package com.hfstudio.minopp.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.effect.EffectEvent;
import com.hfstudio.minopp.effect.EffectEvents;
import com.hfstudio.minopp.effect.SeatActionTakenEffectEvent;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.network.S2CActionEphemeralPacket;
import com.hfstudio.minopp.network.S2CEffectListPacket;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Chat;
import com.hfstudio.minopp.util.Direction;
import com.hfstudio.minopp.util.NbtUtil;
import com.hfstudio.minopp.util.Pair;
import com.hfstudio.minopp.util.Vec3d;
import com.hfstudio.minopp.util.WorldUtil;

public class BlockEntityMinoTable extends TileEntity {

    public Map<Direction, CardPlayer> players = new EnumMap<>(Direction.class);
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

    // game rules
    public Map<String, Boolean> rules = new HashMap<>();

    public boolean getRule(String name, boolean defaultValue) {
        Boolean value = rules.get(name);
        return value == null ? defaultValue : value;
    }

    public static final String RULE_JUMP_IN = "allowJumpIn";
    public static final String RULE_STACKING = "allowStacking";
    public static final String RULE_SEVEN0 = "allowSeven0";

    public static final List<Direction> PLAYER_ORDER = List
        .of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public Direction getPlayerDirection(UUID uuid) {
        if (uuid == null) return null;
        for (Map.Entry<Direction, CardPlayer> entry : players.entrySet()) {
            if (entry.getValue() != null && uuid.equals(entry.getValue().uuid)) {
                return entry.getKey();
            }
        }
        return null;
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

    public static Vec3d getSeatLocalPos(Direction dir) {
        return switch (dir) {
            case NORTH -> new Vec3d(1.5, 0.94, 0);
            case SOUTH -> new Vec3d(1.5, 0.94, 3);
            case WEST -> new Vec3d(0, 0.94, 1.5);
            case EAST -> new Vec3d(3, 0.94, 1.5);
        };
    }

    public BlockEntityMinoTable() {
        for (Direction direction : PLAYER_ORDER) {
            players.put(direction, null);
        }
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
        for (Map.Entry<Direction, CardPlayer> entry : players.entrySet()) {
            if (entry.getValue() != null) {
                playersTag.setTag(
                    entry.getKey()
                        .getSerializedName(),
                    entry.getValue()
                        .toTag());
            }
        }
        compoundTag.setTag("players", playersTag);
        if (game != null) {
            compoundTag.setTag("game", game.toTag());
        }
        compoundTag.setTag("state", state.toTag());
        if (award != null) compoundTag.setTag("award", award.writeToNBT(new NBTTagCompound()));
        compoundTag.setBoolean("demo", demo);
        NBTTagCompound rulesTag = new NBTTagCompound();
        for (Map.Entry<String, Boolean> entry : rules.entrySet()) {
            rulesTag.setBoolean(entry.getKey(), entry.getValue());
        }
        compoundTag.setTag("rules", rulesTag);
        compoundTag.setBoolean("gameEnd", gameEnd);
        if (lastPlayedByUuid != null) NbtUtil.putUUID(compoundTag, "lastPlayedBy", lastPlayedByUuid);
        compoundTag.setInteger("lastDiscardSize", lastDiscardSize);
    }

    @Override
    public void readFromNBT(NBTTagCompound compoundTag) {
        super.readFromNBT(compoundTag);
        NBTTagCompound playersTag = compoundTag.getCompoundTag("players");
        for (Direction direction : PLAYER_ORDER) {
            if (playersTag.hasKey(direction.getSerializedName())) {
                players.put(direction, new CardPlayer(playersTag.getCompoundTag(direction.getSerializedName())));
            } else {
                players.put(direction, null);
            }
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
        if (compoundTag.hasKey("award")) {
            award = ItemStack.loadItemStackFromNBT(compoundTag.getCompoundTag("award"));
        } else {
            award = null;
        }
        if (compoundTag.hasKey("demo", 1)) {
            demo = compoundTag.getBoolean("demo");
        } else {
            demo = false;
        }
        rules.clear();
        if (compoundTag.hasKey("rules")) {
            NBTTagCompound rulesTag = compoundTag.getCompoundTag("rules");
            for (Object key : rulesTag.func_150296_c()) {
                rules.put((String) key, rulesTag.getBoolean((String) key));
            }
        }
        gameEnd = compoundTag.getBoolean("gameEnd");

        // Default values for missing rules
        rules.putIfAbsent(RULE_JUMP_IN, true);
        rules.putIfAbsent(RULE_STACKING, true);
        rules.putIfAbsent(RULE_SEVEN0, false);

        // used for animation tracking
        lastPlayedByUuid = NbtUtil.hasUUID(compoundTag, "lastPlayedBy") ? NbtUtil.getUUID(compoundTag, "lastPlayedBy")
            : null;

        // intialise tracking values
        if (game != null && previousGame == null) {
            lastDiscardSize = game.discardDeck.size();
            lastHandSizes.clear();
            for (CardPlayer p : game.players) {
                lastHandSizes.put(p.uuid, p.hand.size());
            }
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
        boolean isHandSwapPlay = game.topCard != null && (game.topCard.number == 0 || game.topCard.number == 7)
            && game.discardDeck.size() > lastDiscardSize
            && getRule(RULE_SEVEN0, false);

        // 0 card animate
        if (isHandSwapPlay && game.topCard.number == 0 && game.topCard.family == Card.Family.NUMBER) {
            List<Direction> occupiedOrder = PLAYER_ORDER.stream()
                .filter(d -> players.get(d) != null)
                .collect(Collectors.toList());
            int occupiedSize = occupiedOrder.size();
            for (int i = 0; i < occupiedSize; i++) {
                Direction from = occupiedOrder.get(i);
                Direction to = occupiedOrder
                    .get(game.isAntiClockwise ? (i - 1 + occupiedSize) % occupiedSize : (i + 1) % occupiedSize);
                activeAnimations.add(new HandSwapAnimation(getSeatLocalPos(from), getSeatLocalPos(to), 5));
            }
        }

        // 7 card animate
        if (isHandSwapPlay && game.topCard.number == 7
            && game.lastSwapFromUuid != null
            && game.lastSwapToUuid != null) {
            Direction fromDir = getPlayerDirection(game.lastSwapFromUuid);
            Direction toDir = getPlayerDirection(game.lastSwapToUuid);
            if (fromDir != null && toDir != null) {
                CardPlayer fromP = game.deAmputate(game.lastSwapFromUuid);
                CardPlayer toP = game.deAmputate(game.lastSwapToUuid);
                int fromCount = toP != null ? toP.hand.size() : 5;
                int toCount = fromP != null ? fromP.hand.size() : 5;
                activeAnimations
                    .add(new HandSwapAnimation(getSeatLocalPos(fromDir), getSeatLocalPos(toDir), fromCount));
                activeAnimations.add(new HandSwapAnimation(getSeatLocalPos(toDir), getSeatLocalPos(fromDir), toCount));
            }
        }

        // Draw animation - skip entirely if hand swap just happened
        if (!isHandSwapPlay) {
            for (CardPlayer p : game.players) {
                if (p.uuid.equals(lastPlayedByUuid)) continue;
                Integer prevBoxed = lastHandSizes.get(p.uuid);
                int prev = prevBoxed == null ? 0 : prevBoxed;
                int curr = p.hand.size();
                if (curr > prev) {
                    Direction dir = getPlayerDirection(p.uuid);
                    if (dir != null) {
                        activeAnimations
                            .add(new HandSwapAnimation(new Vec3d(0.5, 0.94, 0.5), getSeatLocalPos(dir), curr - prev));
                    }
                }
            }
        }

        lastDiscardSize = game.discardDeck.size();
        lastHandSizes.clear();
        for (CardPlayer p : game.players) {
            lastHandSizes.put(p.uuid, p.hand.size());
        }
    }

    public List<CardPlayer> getPlayersList() {
        List<CardPlayer> playersList = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) != null) {
                playersList.add(players.get(direction));
            }
        }
        return playersList;
    }

    public List<Direction> getEmptyDirections() {
        List<Direction> emptyDirections = new ArrayList<>();
        for (Direction direction : PLAYER_ORDER) {
            if (players.get(direction) == null) {
                emptyDirections.add(direction);
            }
        }
        return emptyDirections;
    }

    private static final int PLAYER_RANGE = 20;

    public void joinPlayerToTable(CardPlayer cardPlayer, Vec3d playerPos) {
        if (game != null) return;
        double cx = xCoord + 1.5, cz = zCoord + 1.5;
        double ox = playerPos.x - cx, oz = playerPos.z - cz;
        Direction playerDirection = Direction.fromYRot(Math.atan2(oz, ox) * 180 / Math.PI - 90);
        for (Direction checkDir : PLAYER_ORDER) {
            if (cardPlayer.equals(players.get(checkDir))) {
                players.put(checkDir, null);
            }
        }
        players.put(playerDirection, cardPlayer);
        sync();
    }

    public void startGame(CardPlayer initiator) {
        if (game != null) return;
        List<CardPlayer> playerList = getPlayersList();
        if (playerList.size() < 2) return;

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
                        ItemStack handCard = new ItemStack(Mino.ITEM_HAND_CARDS);
                        ItemDataUtils.setCardGameBinding(handCard, getBlockPos(), cardPlayer.uuid);
                        InventoryPlayer inv = mcPlayer.inventory;
                        if (inv.currentItem < InventoryPlayer.getHotbarSize() && inv.getCurrentItem() == null) {
                            inv.setInventorySlotContents(inv.currentItem, handCard);
                            playerFound = true;
                        } else {
                            boolean addSuccessful = inv.addItemStackToInventory(handCard);
                            if (!addSuccessful) {
                                EntityItem itemEntity = mcPlayer.dropPlayerItemWithRandomChoice(handCard, false);
                                if (itemEntity != null) {
                                    itemEntity.delayBeforeCanPickup = 0;
                                    itemEntity.func_145797_a(mcPlayer.getCommandSenderName());
                                }
                            }
                            mcPlayer
                                .addChatComponentMessage(Chat.translatable("game.minopp.play.hand_card_in_inventory"));
                            playerFound = true;
                        }
                        mcPlayer.inventoryContainer.detectAndSendChanges();
                    }
                } else {
                    if (cardPlayer.uuid.equals(entity.getUniqueID())) {
                        playerFound = true;
                    }
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

        players.values()
            .forEach(p -> {
                if (p != null) {
                    p.hand.clear();
                    p.hasShoutedMino = false;
                }
            });
        game = new CardGame(getPlayersList());
        game.tableEntity = this;
        state = game.initiate(initiator, 7).state;
        sendSeatActionTakenToAll();
        sync();
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
                if (tablePos != null && tablePos.equals(getBlockPos())) {
                    items[i] = null;
                }
            }
            mcPlayer.inventoryContainer.detectAndSendChanges();
        }

        for (CardPlayer cardPlayer : players.values()) {
            if (cardPlayer == null) continue;
            Entity entity = WorldUtil.getEntityByUUID(worldObj, cardPlayer.uuid);
            if (entity instanceof EntityLivingBase livingEntity && !(entity instanceof EntityPlayer)) {
                ItemStack stack = livingEntity.getHeldItem();
                if (stack != null && stack.getItem() == Mino.ITEM_HAND_CARDS) {
                    livingEntity.setCurrentItemOrArmor(0, null);
                }
            }
        }

        players.values()
            .forEach(p -> {
                if (p != null) {
                    p.hand.clear();
                    p.hasShoutedMino = false;
                }
            });
        state = ActionReport.builder(initiator)
            .gameDestroyed().state;
        sync();
    }

    public void resetSeats(CardPlayer initiator) {
        sendSeatActionTakenToAll();
        players.replaceAll((d, v) -> null);
        state = ActionReport.builder(initiator)
            .panic(Chat.translatable("game.minopp.play.seats_reset", initiator.name)).state;
        sync();
    }

    public void handleActionResult(ActionReport result, CardPlayer cardPlayer, EntityPlayerMP player) {
        if (result != null) {
            if (result.shouldDestroyGame) {
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
                        boolean playerPartOfGame = getPlayersList().stream()
                            .anyMatch(
                                p -> p.uuid.equals(
                                    serverPlayer.getGameProfile()
                                        .getId()));
                        S2CEffectListPacket.sendS2C(serverPlayer, result.effects, tableCenterPos, playerPartOfGame);
                    }
                }
            }

            // Auto-resolve draw penalty for next player if they can't stack
            if (game != null && game.drawCount > 0) {
                CardPlayer penalisedPlayer = game.players.get(game.currentPlayerIndex);
                ActionReport penaltyReport = game.resolveDrawPenalty();
                if (penaltyReport != null) {
                    handleActionResult(penaltyReport, penalisedPlayer, null);
                }
            }
        }
        sync();
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
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    @Override
    public boolean canUpdate() {
        return false;
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
