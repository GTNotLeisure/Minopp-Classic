package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.hfstudio.minopp.util.Chat;

public final class Teams {

    private Teams() {}

    public static final int MAX_TEAMS = 10;

    private static final EnumChatFormatting[] FORMATS = { EnumChatFormatting.RED, EnumChatFormatting.BLUE,
        EnumChatFormatting.GREEN, EnumChatFormatting.YELLOW, EnumChatFormatting.DARK_PURPLE, EnumChatFormatting.GOLD,
        EnumChatFormatting.AQUA, EnumChatFormatting.LIGHT_PURPLE, EnumChatFormatting.WHITE, EnumChatFormatting.GRAY };
    private static final int[] RGB = { 0xE04040, 0x3A6AE0, 0x3CB043, 0xE8C21E, 0x8A3AC8, 0xE88A1E, 0x2AC0D0, 0xE070C0,
        0xE8E8E8, 0x8A8A8A };

    public static EnumChatFormatting format(int team) {
        return FORMATS[Math.floorMod(team, MAX_TEAMS)];
    }

    public static int rgb(int team) {
        return RGB[Math.floorMod(team, MAX_TEAMS)];
    }

    public static IChatComponent name(int team) {
        IChatComponent c = Chat.translatable("gui.minopp.team." + Math.floorMod(team, MAX_TEAMS));
        c.setChatStyle(new ChatStyle().setColor(format(team)));
        return c;
    }

    public static boolean enabled(TableConfig config) {
        return config.getBool(GameRules.TEAM_MODE);
    }

    public static int count(TableConfig config) {
        return Math.max(2, Math.min(MAX_TEAMS, config.getInt(GameRules.TEAM_COUNT)));
    }

    public static int[] sizes(List<CardPlayer> seated, int teamCount) {
        int[] sizes = new int[teamCount];
        for (CardPlayer p : seated) if (p.team >= 0 && p.team < teamCount) sizes[p.team]++;
        return sizes;
    }

    public static int smallest(List<CardPlayer> seated, int teamCount) {
        int[] sizes = sizes(seated, teamCount);
        int best = 0;
        for (int t = 1; t < teamCount; t++) if (sizes[t] < sizes[best]) best = t;
        return best;
    }

    public static void normalize(List<CardPlayer> seated, int teamCount) {
        List<CardPlayer> valid = new ArrayList<>();
        List<CardPlayer> invalid = new ArrayList<>();
        for (CardPlayer p : seated) (p.team >= 0 && p.team < teamCount ? valid : invalid).add(p);
        for (CardPlayer p : invalid) {
            p.team = smallest(valid, teamCount);
            valid.add(p);
        }
    }

    public static void autoAssign(List<CardPlayer> seated, int teamCount) {
        List<CardPlayer> bySeat = new ArrayList<>(seated);
        bySeat.sort(Comparator.comparingInt(p -> p.seat));
        for (int i = 0; i < bySeat.size(); i++) bySeat.get(i).team = i % teamCount;
    }

    public static IChatComponent problem(List<CardPlayer> seated, int teamCount) {
        if (seated.size() < teamCount) {
            return Chat.translatable("game.minopp.play.teams_too_few", teamCount);
        }
        int[] sizes = sizes(seated, teamCount);
        boolean equal = true;
        for (int t = 0; t < teamCount; t++) {
            if (sizes[t] == 0 || sizes[t] != sizes[0]) {
                equal = false;
                break;
            }
        }
        if (equal) return null;
        return Chat.translatable("game.minopp.play.teams_unequal", summary(seated, teamCount));
    }

    public static IChatComponent summary(List<CardPlayer> seated, int teamCount) {
        int[] sizes = sizes(seated, teamCount);
        IChatComponent c = new ChatComponentText("");
        for (int t = 0; t < teamCount; t++) {
            if (t > 0) c.appendText(" · ");
            c.appendSibling(name(t));
            IChatComponent n = new ChatComponentText(" " + sizes[t]);
            n.setChatStyle(new ChatStyle().setColor(format(t)));
            c.appendSibling(n);
        }
        return c;
    }
}
