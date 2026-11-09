package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ConflictsTest {

    @Test
    public void everyKeyExistsAndLinksBothWays() {
        assertFalse(
            Conflicts.keys()
                .isEmpty());
        for (String key : Conflicts.keys()) {
            if (key.startsWith("rule:")) assertNotNull(GameRules.get(key.substring(5)), key);
            else assertNotNull(CardTypes.get(key.substring(5)), key);
            for (String other : Conflicts.of(key)) assertTrue(
                Conflicts.of(other)
                    .contains(key),
                key + " <-> " + other);
        }
    }

    @Test
    public void clashOnlyWhenBothAreOn() {
        TableConfig cfg = new TableConfig();
        String multi = Conflicts.rule(GameRules.MULTI_PLAY), bring = Conflicts.rule(GameRules.BRING_CARD);
        cfg.set(GameRules.MULTI_PLAY, 1);
        cfg.set(GameRules.BRING_CARD, 0);
        assertTrue(
            Conflicts.clashes(multi, cfg)
                .isEmpty());
        cfg.set(GameRules.BRING_CARD, 1);
        assertTrue(
            Conflicts.clashes(multi, cfg)
                .contains(bring));
    }
}
