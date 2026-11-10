package com.hfstudio.minopp.game;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.minopp.Mino;

public class GameSounds {

    private GameSounds() {}

    public static final ResourceLocation PLAY = Mino.id("game.play");
    public static final ResourceLocation DRAW = Mino.id("game.draw");
    public static final ResourceLocation DRAW_MULTI = Mino.id("game.draw_multi");
    public static final ResourceLocation PASS = Mino.id("game.pass");
    public static final ResourceLocation TURN = Mino.id("game.turn_notice");
    public static final ResourceLocation TURN_AGAIN = Mino.id("game.turn_notice_again");
    public static final ResourceLocation WIN = Mino.id("game.win");
    public static final ResourceLocation HAND_CHANGE = Mino.id("game.hand_change");

    private static ResourceLocation mc(String name) {
        return new ResourceLocation("minecraft", name);
    }

    public static void draws(ActionReport report, int count, int startOffset) {
        if (report == null || count <= 0) return;
        int shown = Math.min(count, 6);
        for (int i = 0; i < shown; i++) {
            report.sound(DRAW, startOffset + i * 130, 0.8f, 0.9f + i * 0.06f);
        }
        if (count >= 4) report.sound(DRAW_MULTI, startOffset + shown * 130 + 150, 0.9f, 1f);
    }

    public static void deal(ActionReport report, int cards) {
        int n = Math.min(cards, 10);
        for (int i = 0; i < n; i++) report.sound(DRAW, i * 70, 0.5f, 1.1f + (i % 3) * 0.08f);
    }

    public static void turn(ActionReport report, CardPlayer player, boolean again, int offset) {
        report.sound(again ? TURN_AGAIN : TURN, offset, player, 0.8f, 1f);
    }

    public static void cardPlayed(ActionReport report, CardType type, boolean flex) {
        String id = type.id;
        if (type.isDraw() && !flex) {
            report.sound(mc("random.bowhit"), 120, 0.7f, 1.15f);
        }
        if (id.equals("wild_smoke")) {
            report.sound(mc("random.fizz"), 80, 0.6f, 0.8f);
        } else if (id.equals("wild_echo")) {
            report.sound(mc("random.orb"), 80, 0.5f, 1.2f);
            report.sound(mc("random.orb"), 260, 0.4f, 1.2f);
        } else if (id.equals("wild_crossfade") || id.equals("wild_even_out") || id.equals("wild_card_storm")) {
            report.sound(HAND_CHANGE, 150, 1f, 1f);
        } else if (id.equals("wild_surprise")) {
            for (int i = 0; i < 3; i++) report.sound(mc("note.pling"), 80 + i * 110, 0.6f, 1f + i * 0.25f);
        } else if (id.equals("wild_booby_trap")) {
            report.sound(mc("random.fuse"), 100, 0.6f, 1.0f);
        } else if (id.equals("wild_hurry_up")) {
            for (int i = 0; i < 4; i++) report.sound(mc("note.hat"), 80 + i * 150, 0.8f, 1.6f);
        } else if (id.equals("wild_shield")) {
            report.sound(mc("random.anvil_land"), 100, 0.3f, 1.6f);
        } else if (id.equals("wild_trickster")) {
            report.sound(mc("mob.endermen.portal"), 100, 0.6f, 1.2f);
        } else if (id.equals("wild_surge") || id.equals("wild_team_play")) {
            report.sound(mc("random.levelup"), 100, 0.4f, 1.4f);
        } else if (id.equals("wild_team_draw4")) {
            report.sound(mc("random.bowhit"), 120, 0.8f, 1.0f);
        } else if (id.equals("blaster")) {
            report.sound(mc("fireworks.blast"), 100, 0.9f, 0.8f);
        } else if (id.equals("wild_blast_all")) {
            report.sound(mc("fireworks.largeBlast"), 100, 0.9f, 0.9f);
            report.sound(mc("fireworks.blast"), 300, 0.7f, 1.1f);
        } else if (id.contains("trade") || id.contains("swap")) {
            report.sound(HAND_CHANGE, 150, 1f, 1f);
        } else if (id.equals("gift")) {
            report.sound(mc("random.orb"), 100, 0.6f, 1.7f);
        } else if (id.equals("bonus_turn")) {
            report.sound(mc("random.levelup"), 100, 0.4f, 1.6f);
        } else if (id.equals("wild_cyclone")) {
            report.sound(mc("mob.enderdragon.wings"), 80, 0.5f, 1.3f);
            report.sound(HAND_CHANGE, 300, 0.8f, 1.2f);
        } else if (id.equals("wild_thief")) {
            report.sound(mc("random.pop"), 120, 0.8f, 0.6f);
        } else if (id.contains("skip")) {
            report.sound(mc("note.snare"), 100, 0.9f, 0.8f);
            if (id.equals("skip_everyone") || id.equals("aw_skip_two") || (flex && id.equals("flex_skip")))
                report.sound(mc("note.snare"), 230, 0.9f, 0.8f);
        } else if (id.contains("reverse")) {
            report.sound(mc("random.bow"), 100, 0.6f, 1.6f);
        } else if (id.equals("flip")) {
            report.sound(mc("mob.enderdragon.wings"), 80, 0.45f, 1.8f);
        } else if (id.contains("discard_all")) {
            report.sound(mc("random.pop"), 120, 0.8f, 0.7f);
            report.sound(mc("random.pop"), 220, 0.8f, 0.9f);
        } else if (id.equals("wild_color_roulette") || id.equals("wild_draw_color")) {
            for (int i = 0; i < 4; i++) report.sound(mc("random.click"), 100 + i * 90, 0.5f, 1.6f - i * 0.1f);
        } else if (id.equals("wild_time_balloon")) {
            report.sound(mc("random.fuse"), 100, 0.5f, 1.4f);
        } else if (id.equals("wild_dance_floor")) {
            report.sound(mc("note.harp"), 80, 0.8f, 1.0f);
            report.sound(mc("note.harp"), 200, 0.8f, 1.26f);
            report.sound(mc("note.harp"), 320, 0.8f, 1.5f);
        } else if (id.equals("wild_rebound")) {
            report.sound(mc("random.bow"), 80, 0.7f, 0.8f);
            report.sound(mc("random.bowhit"), 260, 0.8f, 0.9f);
        } else if (id.equals("wild_hand_shuffle") || id.equals("aw_forced_swap")) {
            report.sound(HAND_CHANGE, 150, 1f, 1f);
        } else if (id.equals("wild_fifty_fifty")) {
            report.sound(mc("random.orb"), 100, 0.7f, 0.7f);
            report.sound(mc("random.orb"), 260, 0.7f, 1.3f);
        } else if (type.wild) {
            report.sound(mc("random.orb"), 100, 0.5f, 1.4f);
        }
        if (flex) report.sound(mc("random.levelup"), 60, 0.35f, 1.8f);
    }

    public static void elimination(ActionReport report) {
        report.sound(mc("random.explode"), 0, 0.35f, 1.5f);
    }

    public static void balloonPop(ActionReport report) {
        report.sound(mc("fireworks.blast"), 0, 1f, 1.2f);
    }
}
