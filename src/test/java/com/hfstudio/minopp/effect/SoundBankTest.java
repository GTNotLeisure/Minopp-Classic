package com.hfstudio.minopp.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hfstudio.minopp.config.ClientConfig;
import com.hfstudio.minopp.config.Prefs;

public class SoundBankTest {

    @BeforeEach
    void reset() {
        ClientConfig.display.soundPacks = "";
    }

    @Test
    void defaultPlaysTheSoundAsSent() {
        SoundBank.Sound s = SoundBank.resolve("minopp:game.play", 0.8f, 1.1f);
        assertEquals("minopp:game.play", s.name());
        assertEquals(1.1f, s.pitch(), 1e-6);
    }

    @Test
    void vanillaCardSoundsHaveSlots() {
        assertEquals("skip", SoundBank.slotOf("minecraft:note.snare"));
        assertEquals("blast", SoundBank.slotOf("random.explode"));
        assertEquals("misc", SoundBank.slotOf("minecraft:random.anvil_land"));
    }

    @Test
    void slotsSwapOrGoSilentOnTheirOwn() {
        SoundBank.set("mino_shout", "note");
        SoundBank.set("draw", SoundBank.SILENT);
        SoundBank.Sound shout = SoundBank.resolve("minopp:game.mino_shout", 1f, 1f);
        assertEquals("note.pling", shout.name());
        assertNull(SoundBank.resolve("minopp:game.draw", 1f, 1f));
        assertEquals(
            "minopp:game.play",
            SoundBank.resolve("minopp:game.play", 1f, 1f)
                .name());
    }

    @Test
    void packForEverythingKeepsDefaultsWhereItHasNoSound() {
        SoundBank.setAll("minecraft");
        assertTrue(SoundBank.allUse("minecraft"));
        assertEquals("minecraft", SoundBank.choice("win"));
        // the Minecraft pack has no card effect sounds, those stay as they are
        assertEquals(SoundBank.DEFAULT, SoundBank.choice("skip"));
        SoundBank.setAll(SoundBank.SILENT);
        assertNull(SoundBank.resolve("minecraft:note.snare", 1f, 1f));
    }

    @Test
    void modsRegisterPacksAndSlots() {
        SoundBank.registerSlot("testmod:beep", "testmod", "testmod:beep", "testmod:beep");
        SoundBank.pack("testmod:arcade", new ResourceLocation("testmod", "textures/arcade.png"))
            .sound("play", "testmod:arcade.play", 1.5f)
            .sound("testmod:beep", "testmod:arcade.beep", 1f)
            .register();
        List<SoundBank.Pack> packs = SoundBank.packs();
        assertEquals(
            SoundBank.SILENT,
            packs.get(packs.size() - 1)
                .id());
        assertEquals(
            "gui.testmod.sound.pack.arcade",
            SoundBank.getPack("testmod:arcade")
                .langKey());
        assertEquals("testmod:beep", SoundBank.slotOf("testmod:beep"));
        SoundBank.set("testmod:beep", "testmod:arcade");
        assertEquals(
            "testmod:arcade.beep",
            SoundBank.resolve("testmod:beep", 1f, 1f)
                .name());
        assertTrue(
            SoundBank.options("play")
                .contains("testmod:arcade"));
    }

    @Test
    void modsRegisterPersonalSettings() {
        int[] value = { 3 };
        Prefs.Pref p = Prefs.integer("testmod:size", 3, 1, 9, 1, "x", () -> value[0], v -> value[0] = v, () -> {});
        assertEquals("gui.testmod.pref.size", p.langKey());
        p.change(42);
        assertEquals(9, value[0]);
        assertEquals("9x", p.format(p.value()));
        assertTrue(
            Prefs.all()
                .contains(p));
    }
}
