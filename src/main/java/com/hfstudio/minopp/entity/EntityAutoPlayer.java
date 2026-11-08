package com.hfstudio.minopp.entity;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.hfstudio.minopp.Mino;
import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.block.BlockMinoTable;
import com.hfstudio.minopp.game.ActionReport;
import com.hfstudio.minopp.game.AutoPlayer;
import com.hfstudio.minopp.game.CardGame;
import com.hfstudio.minopp.game.CardPlayer;
import com.hfstudio.minopp.item.ItemDataUtils;
import com.hfstudio.minopp.item.ItemHandCards;
import com.hfstudio.minopp.network.S2CAutoPlayerScreenPacket;
import com.hfstudio.minopp.render.SkinResolver;
import com.hfstudio.minopp.util.BlockPos;
import com.hfstudio.minopp.util.Vec3d;

public class EntityAutoPlayer extends EntityLiving {

    private static final int DW_HAND_STACK = 20;
    private static final int DW_ACTIVE = 21;
    private static final int DW_SKIN = 22;

    public CardPlayer cardPlayer;
    public BlockPos tablePos = null;
    private boolean noPush;

    public EntityAutoPlayer(World level) {
        super(level);
        setSize(0.6f, 1.8f);
        func_110163_bv(); // enablePersistence
    }

    private long lastTickGameTime = 0;
    private boolean isThinking = false;
    private long thinkingFinishTime = 0;
    private long gameEndTime = 0;

    public final AutoPlayer autoPlayer = new AutoPlayer();

    public ResourceLocation clientSkinLocation = null;
    public String clientSkinValidFor = "";

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObjectByDataType(DW_HAND_STACK, 5);
        dataWatcher.addObject(DW_ACTIVE, (byte) 0);
        dataWatcher.addObject(DW_SKIN, "");
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
    }

    @Override
    protected boolean isAIEnabled() {
        return false;
    }

    @Override
    protected void updateEntityActionState() {}

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (worldObj.isRemote) {
            if (!clientSkinValidFor.equals(getSkin())) {
                clientSkinValidFor = getSkin();
                clientSkinLocation = null;
                if (!clientSkinValidFor.isEmpty()) {
                    SkinResolver.resolve(
                        clientSkinValidFor,
                        loc -> { if (clientSkinValidFor.equals(getSkin())) clientSkinLocation = loc; });
                }
            }
            return;
        }

        if (!getActive()) {
            tablePos = null;
            heal(10);
            return;
        }
        if (autoPlayer.aiNoDelay < 2 && worldObj.getTotalWorldTime() - lastTickGameTime < 10) {
            return;
        }
        lastTickGameTime = worldObj.getTotalWorldTime();

        if (tablePos == null) {
            boolean tableFound = false;
            int bx = MathHelper.floor_double(posX), by = MathHelper.floor_double(posY),
                bz = MathHelper.floor_double(posZ);
            for (int dx = -2; dx <= 2 && !tableFound; dx++) {
                for (int dz = -2; dz <= 2 && !tableFound; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int tx = bx + dx, ty = by + dy, tz = bz + dz;
                        Block tobeTableBlock = worldObj.getBlock(tx, ty, tz);
                        if (tobeTableBlock == Mino.BLOCK_MINO_TABLE) {
                            BlockPos corePos = BlockMinoTable.getCore(worldObj, tx, ty, tz);
                            BlockEntityMinoTable tableEntity = ItemHandCards.getTable(worldObj, corePos);
                            if (tableEntity != null) {
                                if (tableEntity.game != null) continue;
                                String playerName = hasCustomNameTag() ? getCustomNameTag()
                                    : "MinoBot #" + (100 + rand.nextInt(900));
                                cardPlayer = new CardPlayer(getUniqueID(), playerName);
                                tableEntity.joinPlayerToTable(cardPlayer, new Vec3d(posX, posY, posZ));
                                tablePos = corePos;
                                tableFound = true;
                                ItemStack handStack = new ItemStack(Mino.ITEM_HAND_CARDS);
                                ItemDataUtils.setCardGameBinding(handStack, tablePos, cardPlayer.uuid);
                                setHandStack(handStack);
                                break;
                            }
                        }
                    }
                }
            }
            if (!tableFound) {
                setActive(false);
                return;
            }
        }

        lookAt(tablePos.x + 1.5, tablePos.y + 1, tablePos.z + 1.5);
        BlockEntityMinoTable tableEntity = ItemHandCards.getTable(worldObj, tablePos);
        if (tableEntity != null) {
            if (tableEntity.game != null) {
                heal(10);
                CardGame game = tableEntity.game;
                if (game.players.get(game.currentPlayerIndex)
                    .equals(cardPlayer)) {
                    if (autoPlayer.aiNoDelay > 0) {
                        isThinking = false;
                    } else {
                        if (!isThinking) {
                            if (game.currentPlayerPhase == CardGame.PlayerActionPhase.DISCARD_DRAWN) {
                                thinkingFinishTime = worldObj.getTotalWorldTime() + 10 + rand.nextInt(10);
                            } else {
                                thinkingFinishTime = worldObj.getTotalWorldTime() + 10 + rand.nextInt(40);
                            }
                            isThinking = true;
                            return;
                        } else {
                            if (worldObj.getTotalWorldTime() >= thinkingFinishTime) {
                                isThinking = false;
                            } else {
                                return;
                            }
                        }
                    }
                    CardPlayer realPlayer = game.deAmputate(cardPlayer);
                    ActionReport result = autoPlayer.playAtGame(game, realPlayer, MinecraftServer.getServer());
                    tableEntity.handleActionResult(result, realPlayer, null);
                    gameEndTime = -1;
                } else {
                    isThinking = false;
                }
            } else {
                if (gameEndTime == -1L) {
                    gameEndTime = worldObj.getTotalWorldTime() + 100;
                } else if (worldObj.getTotalWorldTime() - gameEndTime <= 20 * 3) {
                    if (onGround) jump();
                } else {
                    if (autoPlayer.aiStartGame && tableEntity.getPlayersList()
                        .size() >= 2) {
                        tableEntity.startGame(cardPlayer);
                    }
                }
                if (tableEntity.getPlayersList()
                    .stream()
                    .noneMatch(p -> p.equals(cardPlayer))) {
                    cardPlayer = null;
                    setHandStack(null);
                    tablePos = null;
                }
            }
        } else {
            cardPlayer = null;
            setHandStack(null);
            tablePos = null;
        }
    }

    private void lookAt(double x, double y, double z) {
        double dx = x - posX;
        double dy = y - (posY + getEyeHeight());
        double dz = z - posZ;
        double horizontal = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI));
        rotationYaw = yaw;
        rotationYawHead = yaw;
        renderYawOffset = yaw;
        rotationPitch = pitch;
    }

    @Override
    protected boolean interact(EntityPlayer player) {
        if (worldObj.isRemote) {
            return player.isSneaking() || !getActive();
        } else {
            if (player.isSneaking() && player.canCommandSenderUseCommand(2, "")) {
                S2CAutoPlayerScreenPacket.sendS2C((EntityPlayerMP) player, this);
                return true;
            } else if (!getActive() && !player.isSneaking()) {
                setActive(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack getHeldItem() {
        return dataWatcher.getWatchableObjectItemStack(DW_HAND_STACK);
    }

    @Override
    public ItemStack getEquipmentInSlot(int slot) {
        return slot == 0 ? getHeldItem() : null;
    }

    @Override
    public void setCurrentItemOrArmor(int slot, ItemStack stack) {}

    @Override
    public ItemStack[] getLastActiveItems() {
        return new ItemStack[5];
    }

    private void setHandStack(ItemStack stack) {
        dataWatcher.updateObject(DW_HAND_STACK, stack);
        dataWatcher.setObjectWatched(DW_HAND_STACK);
    }

    @Override
    public boolean isEntityInvulnerable() {
        return true;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (source == DamageSource.outOfWorld) return super.attackEntityFrom(source, amount);
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (tablePos != null) compound.setLong("TablePos", tablePos.asLong());
        if (cardPlayer != null) compound.setTag("CardPlayer", cardPlayer.toTag());
        if (getHeldItem() != null) compound.setTag("HandStack", getHeldItem().writeToNBT(new NBTTagCompound()));
        writeConfigToTag(compound);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("TablePos", 4)) {
            tablePos = BlockPos.of(compound.getLong("TablePos"));
        } else {
            tablePos = null;
        }
        if (compound.hasKey("CardPlayer", 10)) {
            cardPlayer = new CardPlayer(compound.getCompoundTag("CardPlayer"));
        } else {
            cardPlayer = null;
        }
        readConfigFromTag(compound);

        if (tablePos != null && cardPlayer != null) {
            ItemStack handStack = new ItemStack(Mino.ITEM_HAND_CARDS);
            ItemDataUtils.setCardGameBinding(handStack, tablePos, cardPlayer.uuid);
            setHandStack(handStack);
        } else {
            setHandStack(null);
        }
    }

    @Override
    public boolean canBePushed() {
        return !noPush;
    }

    @Override
    public void applyEntityCollision(Entity entity) {
        if (!noPush) super.applyEntityCollision(entity);
    }

    public boolean getActive() {
        return dataWatcher.getWatchableObjectByte(DW_ACTIVE) != 0;
    }

    public void setActive(boolean active) {
        dataWatcher.updateObject(DW_ACTIVE, (byte) (active ? 1 : 0));
    }

    public boolean getNoPush() {
        return noPush;
    }

    public void setNoPush(boolean noPush) {
        this.noPush = noPush;
    }

    public String getSkin() {
        return dataWatcher.getWatchableObjectString(DW_SKIN);
    }

    public void setSkin(String skin) {
        dataWatcher.updateObject(DW_SKIN, skin == null ? "" : skin);
    }

    public NBTTagCompound writeConfigToTag() {
        NBTTagCompound tag = new NBTTagCompound();
        writeConfigToTag(tag);
        return tag;
    }

    public void writeConfigToTag(NBTTagCompound tag) {
        tag.setBoolean("Active", getActive());
        tag.setBoolean("NoPush", getNoPush());
        tag.setString("Skin", getSkin());
        tag.setTag("AI", autoPlayer.toConfigNbt());
        if (hasCustomNameTag()) {
            tag.setString("CustomName", getCustomNameTag());
        }
    }

    public void readConfigFromTag(NBTTagCompound tag) {
        setActive(tag.getBoolean("Active"));
        setNoPush(tag.getBoolean("NoPush"));
        setSkin(tag.getString("Skin"));
        autoPlayer.useConfigNbt(tag.getCompoundTag("AI"));
        if (tag.hasKey("CustomName", 8)) {
            setCustomNameTag(tag.getString("CustomName"));
        } else {
            setCustomNameTag("");
        }
    }
}
