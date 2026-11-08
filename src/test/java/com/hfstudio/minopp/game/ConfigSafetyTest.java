package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

public class ConfigSafetyTest {

    @Test
    public void hostileConfigIsClamped() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound rules = new NBTTagCompound();
        rules.setInteger(GameRules.MAX_PLAYERS, Integer.MAX_VALUE);
        rules.setInteger(GameRules.DECK_COPIES, Integer.MAX_VALUE);
        rules.setInteger("noSuchRule", 5);
        tag.setTag("rules", rules);
        NBTTagList packs = new NBTTagList();
        packs.appendTag(new NBTTagString(CardPack.CLASSIC));
        packs.appendTag(new NBTTagString("no_such_pack"));
        tag.setTag("packs", packs);
        NBTTagCompound counts = new NBTTagCompound();
        counts.setInteger("no_mercy:discard_all", Integer.MAX_VALUE);
        counts.setInteger("bogus:key", 3);
        tag.setTag("counts", counts);

        TableConfig config = new TableConfig();
        config.fromTag(tag);
        assertEquals(
            GameRules.get(GameRules.MAX_PLAYERS)
                .max(),
            config.getInt(GameRules.MAX_PLAYERS));
        assertEquals(
            GameRules.get(GameRules.DECK_COPIES)
                .max(),
            config.getInt(GameRules.DECK_COPIES));
        assertTrue(config.isPackEnabled(CardPack.CLASSIC));
        assertFalse(config.isPackEnabled("no_such_pack"));
        CardPack noMercy = CardPack.get(CardPack.NO_MERCY);
        CardPack.Entry discardAll = noMercy.entries.stream()
            .filter(
                e -> e.key()
                    .equals("discard_all"))
            .findFirst()
            .get();
        assertEquals(64, config.getCount(noMercy, discardAll));
        assertFalse(
            config.toTag()
                .getCompoundTag("counts")
                .hasKey("bogus:key"));
        assertTrue(
            config.buildDeck(20, new Random(1))
                .size() < 100_000);
    }
}
