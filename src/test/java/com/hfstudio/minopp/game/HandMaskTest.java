package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import com.hfstudio.minopp.util.NbtUtil;

public class HandMaskTest {

    private static NBTTagCompound table(UUID holder) {
        Card card = new Card(1, new CardFace("number", CardColor.RED, 5), new CardFace("number", CardColor.PURPLE, 2));
        NBTTagList hand = new NBTTagList();
        hand.appendTag(card.toTag());
        NBTTagCompound player = new NBTTagCompound();
        NbtUtil.putUUID(player, "uuid", holder);
        player.setTag("hand", hand);
        NBTTagList players = new NBTTagList();
        players.appendTag(player);
        NBTTagCompound game = new NBTTagCompound();
        game.setTag("players", players);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("game", game);
        return tag;
    }

    private static NBTTagCompound heldCard(NBTTagCompound tag) {
        return tag.getCompoundTag("game")
            .getTagList("players", 10)
            .getCompoundTagAt(0)
            .getTagList("hand", 10)
            .getCompoundTagAt(0);
    }

    private static String type(NBTTagCompound card, String side) {
        return card.getCompoundTag(side)
            .getString("t");
    }

    @Test
    public void othersSeeTheBackSideByDefault() {
        NBTTagCompound card = heldCard(HandMask.maskTableTag(table(UUID.randomUUID()), UUID.randomUUID(), true));
        assertEquals(HandMask.HIDDEN_TYPE, type(card, "l"));
        assertEquals("number", type(card, "d"));
    }

    @Test
    public void bothSidesHiddenWhenTheRuleIsOff() {
        NBTTagCompound card = heldCard(HandMask.maskTableTag(table(UUID.randomUUID()), UUID.randomUUID(), false));
        assertEquals(HandMask.HIDDEN_TYPE, type(card, "l"));
        assertEquals(HandMask.HIDDEN_TYPE, type(card, "d"));
    }

    @Test
    public void holderSeesOwnCards() {
        UUID me = UUID.randomUUID();
        NBTTagCompound card = heldCard(HandMask.maskTableTag(table(me), me, false));
        assertNotEquals(HandMask.HIDDEN_TYPE, type(card, "l"));
    }
}
