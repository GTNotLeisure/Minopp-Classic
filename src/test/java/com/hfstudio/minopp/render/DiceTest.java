package com.hfstudio.minopp.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

public class DiceTest {

    @Test
    public void everyDieHasItsFacesAndNumbers() {
        int[][] expect = { { 4, 4 }, { 6, 6 }, { 8, 8 }, { 12, 12 }, { 20, 20 } };
        for (int[] e : expect) {
            Dice.Poly p = Dice.poly(e[0]);
            assertEquals(e[1], p.faces.length, "faces of d" + e[0]);
            int[] vals = p.values.clone();
            Arrays.sort(vals);
            for (int i = 0; i < vals.length; i++) assertEquals(i + 1, vals[i], "numbers of d" + e[0]);
            int corners = switch (e[0]) {
                case 4, 6 -> e[0] == 4 ? 3 : 4;
                case 8, 20 -> 3;
                default -> 5;
            };
            for (int[] f : p.faces) assertEquals(corners, f.length, "corners per face of d" + e[0]);
        }
    }

    @Test
    public void throwsLandOnTheRolledNumber() {
        for (int sides : new int[] { 4, 6, 8, 12, 20 }) {
            for (int result = 1; result <= sides; result++) {
                Dice.Roll roll = Dice.simulate(
                    sides,
                    result,
                    sides * 1000L + result,
                    new double[] { 0.2, 1.6, 0.4 },
                    new double[] { 1.5, 0.95, 1.5 },
                    0.95,
                    0.12);
                double[] q = roll.rot.get(roll.frames() - 1);
                double[] up = Dice.rotate(
                    q,
                    Dice.poly(sides)
                        .upFor(result));
                assertTrue(up[1] > 0.999, "d" + sides + " shows " + result + " on top");
                assertTrue(roll.bounces.size() >= 2, "d" + sides + " bounces " + roll.bounces.size());
                double[] p = roll.pos.get(roll.frames() - 1);
                assertTrue(p[1] >= 0.95 && p[1] < 1.1, "rests on the table");
            }
        }
    }
}
