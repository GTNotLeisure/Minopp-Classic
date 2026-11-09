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
                assertTrue(roll.settleEnd < Dice.FPS * 4.5, "d" + sides + " comes to rest");
                double[] p = roll.pos.get(roll.frames() - 1);
                assertTrue(p[1] >= 0.95 && p[1] < 1.1, "rests on the table");
            }
        }
    }

    @Test
    public void throwsTumbleWithoutJumps() {
        for (int sides : new int[] { 4, 6, 8, 12, 20 }) {
            for (int result = 1; result <= sides; result++) {
                Dice.Roll roll = Dice.simulate(
                    sides,
                    result,
                    sides * 77L + result,
                    new double[] { 0.2, 1.6, 0.4 },
                    new double[] { 1.5, 0.95, 1.5 },
                    0.95,
                    0.12);
                for (int i = 1; i < roll.frames(); i++) {
                    double[] a = roll.rot.get(i - 1), b = roll.rot.get(i);
                    double d = Math.abs(a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]);
                    double turn = 2 * Math.acos(Math.min(1, d));
                    assertTrue(turn < 0.6, "d" + sides + " turns " + Math.toDegrees(turn) + " deg in frame " + i);
                }
                double[] p = roll.pos.get(roll.frames() - 1);
                assertTrue(Math.hypot(p[0] - 1.5, p[2] - 1.5) < 1.3, "stays on the table");
            }
        }
    }

    @Test
    public void everyFaceMapsInsideItsArt() {
        for (int sides : new int[] { 4, 6, 8, 12, 20 }) {
            Dice.Poly p = Dice.poly(sides);
            for (double[][] face : p.uv) for (double[] uv : face) {
                assertTrue(uv[0] > 0 && uv[0] < 1 && uv[1] > 0 && uv[1] < 1, "d" + sides);
            }
        }
    }

    @Test
    public void d4FacesReadLikeTheirArt() {
        Dice.Poly p = Dice.poly(4);
        // each face carries three different numbers and leaves one out
        java.util.Set<Integer> missing = new java.util.HashSet<>();
        for (int[] face : p.faces) {
            int sum = 0;
            for (int i : face) sum += p.values[i];
            missing.add(10 - sum);
        }
        assertEquals(4, missing.size());
        // the art's corner order (top, bottom left, bottom right) runs the same way round as the face
        for (int f = 0; f < p.faces.length; f++) {
            double[][] uv = p.uv[f];
            double cross = (uv[1][0] - uv[0][0]) * (uv[2][1] - uv[0][1])
                - (uv[1][1] - uv[0][1]) * (uv[2][0] - uv[0][0]);
            double[][] uv0 = p.uv[0];
            double cross0 = (uv0[1][0] - uv0[0][0]) * (uv0[2][1] - uv0[0][1])
                - (uv0[1][1] - uv0[0][1]) * (uv0[2][0] - uv0[0][0]);
            assertTrue(Math.signum(cross) == Math.signum(cross0), "face " + f + " is not mirrored");
        }
    }

    @Test
    public void coinsLandOnTheirSide() {
        for (int side : new int[] { 1, -1 }) {
            Dice.Roll roll = Dice.simulateCoin(
                side > 0,
                42 + side,
                new double[] { 0.2, 1.6, 0.4 },
                new double[] { 1.5, 0.95, 1.5 },
                0.95,
                0.11,
                0.026);
            double[] up = Dice.rotate(roll.rot.get(roll.frames() - 1), new double[] { 0, side, 0 });
            assertTrue(up[1] > 0.999);
            assertTrue(roll.pos.get(roll.frames() - 1)[1] < 0.95 + 0.11 * 0.2, "lies flat");
        }
    }
}
