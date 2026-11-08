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

    /** Seat index at the table (0 = north, clockwise). */
    public int seat = -1;
    /** Knocked out by the mercy rule. */
    public boolean eliminated = false;
    /** Flex power card side (true = on/green). */
    public boolean powerOn = true;
    /** Team index in 2v2 mode, -1 otherwise. */
    public int team = -1;

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
        return tag;
    }

    /** Copy without hand, for seat bookkeeping. */
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
