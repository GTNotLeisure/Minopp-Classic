package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class TableConfigLinksTest {

    @Test
    public void heavyDrawCardTurnsOnMercy() {
        TableConfig cfg = new TableConfig();
        cfg.set(GameRules.MERCY, 0);
        cfg.setCount("no_mercy:wild_draw10", 2);
        assertTrue(cfg.getBool(GameRules.MERCY));
    }

    @Test
    public void ruleRequirementsCascade() {
        TableConfig cfg = new TableConfig();
        cfg.set(GameRules.TEAM_MODE, 0);
        cfg.set(GameRules.TEAM_RELAY, 1);
        assertTrue(cfg.getBool(GameRules.TEAM_MODE));
        cfg.set(GameRules.TEAM_MODE, 0);
        assertFalse(cfg.getBool(GameRules.TEAM_RELAY));
    }

    @Test
    public void sharedCardsAreCountedOnce() {
        TableConfig classicOnly = new TableConfig();
        classicOnly.resetToPreset(CardPack.CLASSIC);
        int classic = classicOnly.countDeckSize();
        TableConfig both = new TableConfig();
        both.resetToPreset(CardPack.CLASSIC);
        both.setPackEnabled(CardPack.JUNGLE, true);
        both.setPackEnabled(CardPack.MODERN, true);
        List<CardFace> faces = both.buildFaces(false);
        long shuffles = faces.stream()
            .filter(f -> f.typeId.equals("wild_hand_shuffle"))
            .count();
        int jungleShuffle = CardPack.get(CardPack.JUNGLE).entries.stream()
            .filter(
                e -> e.typeId()
                    .equals("wild_hand_shuffle"))
            .mapToInt(CardPack.Entry::count)
            .max()
            .orElse(0);
        int modernShuffle = CardPack.get(CardPack.MODERN).entries.stream()
            .filter(
                e -> e.typeId()
                    .equals("wild_hand_shuffle"))
            .mapToInt(CardPack.Entry::count)
            .max()
            .orElse(0);
        assertEquals(Math.max(jungleShuffle, modernShuffle), shuffles);
        assertTrue(both.countDeckSize() > classic);
    }

    @Test
    public void addonPresetKeepsNumberCards() {
        TableConfig cfg = new TableConfig();
        cfg.resetToPreset(CardPack.BLASTER);
        assertTrue(cfg.isPackEnabled(CardPack.CLASSIC));
        assertTrue(cfg.countDeckSize() >= 108);
    }

    @Test
    public void legacyPackIdsMapToMergedPacks() {
        TableConfig cfg = new TableConfig();
        cfg.resetToPreset(CardPack.HERO);
        assertTrue(cfg.isPackEnabled(CardPack.JUNGLE));
        cfg.setCount("hero:wild_rebound", 3);
        assertTrue(cfg.counts.containsKey("jungle:wild_rebound"));
    }
}
