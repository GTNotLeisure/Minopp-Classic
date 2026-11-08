package com.hfstudio.minopp.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class TeamModeTest {

    private static List<CardPlayer> seated(int... teams) {
        List<CardPlayer> list = new ArrayList<>();
        for (int i = 0; i < teams.length; i++) {
            CardPlayer p = new CardPlayer(UUID.randomUUID(), "P" + i);
            p.seat = i;
            p.team = teams[i];
            list.add(p);
        }
        return list;
    }

    @Test
    public void equalTeamsMayStart() {
        assertNull(Teams.problem(seated(0, 1, 0, 1), 2));
        assertNull(Teams.problem(seated(0, 1, 2, 0, 1, 2), 3));
        assertNull(Teams.problem(seated(1, 0), 2));
    }

    @Test
    public void unequalTeamsCannotStart() {
        assertNotNull(Teams.problem(seated(0, 0, 0, 1), 2));
        assertNotNull(Teams.problem(seated(0, 0, 1, 1, 2), 3));
        assertNotNull(Teams.problem(seated(0, 0, 1, 1), 3));
        assertNotNull(Teams.problem(seated(0, 1), 3));
    }

    @Test
    public void newcomersJoinTheSmallestTeam() {
        List<CardPlayer> list = seated(0, 0, 1, -1, -1);
        Teams.normalize(list, 2);
        int[] sizes = Teams.sizes(list, 2);
        assertEquals(5, sizes[0] + sizes[1]);
        assertTrue(Math.abs(sizes[0] - sizes[1]) <= 1);
        List<CardPlayer> three = seated(0, 1, 2, 2);
        Teams.normalize(three, 2);
        for (CardPlayer p : three) assertTrue(p.team == 0 || p.team == 1);
    }

    @Test
    public void autoTeamsAlternateAroundTheTable() {
        List<CardPlayer> list = seated(0, 0, 0, 0, 0, 0);
        Teams.autoAssign(list, 3);
        for (int i = 0; i < list.size(); i++) assertEquals(i % 3, list.get(i).team);
        assertNull(Teams.problem(list, 3));
    }

    @Test
    public void gameKeepsLobbyTeamsOnlyInTeamMode() {
        List<CardPlayer> players = seated(1, 1, 0, 0);
        TableConfig config = new TableConfig();
        config.set(GameRules.TEAM_MODE, 1);
        CardGame game = new CardGame(players);
        game.detachedConfig = config;
        game.initiate(players.get(0), 7);
        assertEquals(1, game.players.get(0).team);
        assertEquals(1, game.players.get(1).team);
        assertEquals(0, game.players.get(2).team);

        List<CardPlayer> others = seated(1, 1, 0, 0);
        CardGame plain = new CardGame(others);
        plain.detachedConfig = new TableConfig();
        plain.initiate(others.get(0), 7);
        for (CardPlayer p : plain.players) assertEquals(-1, p.team);
    }

    @Test
    public void seatCopyKeepsTheTeam() {
        CardPlayer p = seated(3).get(0);
        assertEquals(3, p.seatCopy().team);
    }
}
