package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hfstudio.minopp.util.NbtUtil;

public class CardPlayer {

    public UUID uuid;
    public String name;

    public List<Card> hand = new ArrayList<>();

    public boolean hasShoutedMino = false;

    public int seat = -1;
    public boolean eliminated = false;
    public boolean powerOn = true;
    public int team = -1;
    /**
     * Show 'Em No Mercy coin: which side was declared, whether it is spent, locked in, or armed to double a draw.
     */
    public boolean coinNoMercy, coinUsed, coinLocked, coinArmed;
    public int coinUseSeq;

    public CardPlayer(EntityPlayer mcPlayer) {
        this.uuid = mcPlayer.getGameProfile()
            .getId();
        this.name = mcPlayer.getGameProfile()
            .getName();
    }

    public CardPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public CardPlayer(NBTTagCompound tag) {
        this.uuid = NbtUtil.getUUID(tag, "uuid");
        this.name = tag.getString("name");
        this.hand = new ArrayList<>();
        NBTTagList handTag = tag.getTagList("hand", 10);
        for (int i = 0; i < handTag.tagCount(); i++) hand.add(Card.fromTag(handTag.getCompoundTagAt(i)));
        this.hasShoutedMino = tag.getBoolean("hasShoutedMino");
        this.seat = tag.hasKey("seat") ? tag.getInteger("seat") : -1;
        this.eliminated = tag.getBoolean("eliminated");
        this.powerOn = !tag.hasKey("powerOn") || tag.getBoolean("powerOn");
        this.team = tag.hasKey("team") ? tag.getInteger("team") : -1;
        this.coinNoMercy = tag.getBoolean("coinNoMercy");
        this.coinUsed = tag.getBoolean("coinUsed");
        this.coinUseSeq = tag.hasKey("coinUseSeq") ? tag.getInteger("coinUseSeq") : coinUsed ? 1 : 0;
        this.coinLocked = tag.getBoolean("coinLocked");
        this.coinArmed = tag.getBoolean("coinArmed");
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NbtUtil.putUUID(tag, "uuid", uuid);
        tag.setString("name", name);
        NBTTagList handTag = new NBTTagList();
        for (Card card : hand) handTag.appendTag(card.toTag());
        tag.setTag("hand", handTag);
        tag.setBoolean("hasShoutedMino", hasShoutedMino);
        tag.setInteger("seat", seat);
        tag.setBoolean("eliminated", eliminated);
        tag.setBoolean("powerOn", powerOn);
        tag.setInteger("team", team);
        tag.setBoolean("coinNoMercy", coinNoMercy);
        tag.setBoolean("coinUsed", coinUsed);
        tag.setInteger("coinUseSeq", coinUseSeq);
        tag.setBoolean("coinLocked", coinLocked);
        tag.setBoolean("coinArmed", coinArmed);
        return tag;
    }

    public CardPlayer seatCopy() {
        CardPlayer p = new CardPlayer(uuid, name);
        p.seat = seat;
        p.team = team;
        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardPlayer that = (CardPlayer) o;
        return Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
