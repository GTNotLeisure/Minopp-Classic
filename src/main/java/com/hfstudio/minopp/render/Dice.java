package com.hfstudio.minopp.render;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.hfstudio.minopp.Mino;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Polyhedral dice and coins thrown onto the table: the throw is simulated once as a tumbling rigid body and played
 * back. Where it comes to rest decides nothing: the art is turned onto the solid so the rolled number ends on top.
 */
@SideOnly(Side.CLIENT)
public final class Dice {

    private Dice() {}

    static final int FPS = 60;
    private static final int SUBSTEPS = 8, MAX_FRAMES = FPS * 4;
    private static final double GRAVITY = -14, RESTITUTION = 0.38, FRICTION = 0.5;
    private static final double TABLE_CENTER = 1.5, TABLE_REACH = 1.2;

    /** A convex die of unit circumradius: faces as vertex index loops, counter-clockwise seen from outside. */
    static final class Poly {

        final double[][] v;
        final int[][] faces;
        final double[][] normals;
        /** Number printed on each face, or on each vertex for the D4. */
        final int[] values;
        final boolean vertexValues;
        final double inradius;
        /** Atlas coordinates {u, v} of every corner of every face. */
        double[][][] uv;

        Poly(double[][] v, int[][] faces, double[][] normals, int[] values, boolean vertexValues, double inradius) {
            this.v = v;
            this.faces = faces;
            this.normals = normals;
            this.values = values;
            this.vertexValues = vertexValues;
            this.inradius = inradius;
        }

        /** Local direction that points up when {@code result} is rolled. */
        double[] upFor(int result) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == result) return vertexValues ? v[i] : normals[i];
            }
            return vertexValues ? v[0] : normals[0];
        }
    }

    private static final Map<Integer, Poly> POLYS = new HashMap<>();

    static Poly poly(int sides) {
        return POLYS.computeIfAbsent(sides, s -> {
            Poly p = build(s);
            if (s == 4) matchD4(p);
            p.uv = atlasUv(s, p);
            return p;
        });
    }

    // the face art of each die, one tile per number (the D4: one per face) in rows of COLS
    private static int cols(int sides) {
        return switch (sides) {
            case 4 -> 2;
            case 6 -> 3;
            case 8, 12 -> 4;
            default -> 5;
        };
    }

    private static int tiles(int sides) {
        return sides;
    }

    private static int rows(int sides) {
        return (tiles(sides) + cols(sides) - 1) / cols(sides);
    }

    /** The D4 faces of the art: the numbers at its top, bottom left and bottom right corners. */
    private static final int[][] D4_TILES = { { 2, 1, 3 }, { 3, 1, 4 }, { 2, 3, 4 }, { 2, 4, 1 } };

    private static int d4Tile(Poly p, int[] face) {
        for (int t = 0; t < D4_TILES.length; t++) {
            int hit = 0;
            for (int i : face) for (int n : D4_TILES[t]) if (p.values[i] == n) hit++;
            if (hit == 3) return t;
        }
        return 0;
    }

    private static int vertexWith(Poly p, int[] face, int value) {
        for (int i : face) if (p.values[i] == value) return i;
        return face[0];
    }

    // numbers the corners so that every face reads like its art, not mirrored
    private static void matchD4(Poly p) {
        int[][] perms = { { 1, 2, 3, 4 }, { 1, 2, 4, 3 }, { 1, 3, 2, 4 }, { 1, 3, 4, 2 }, { 1, 4, 2, 3 },
            { 1, 4, 3, 2 } };
        for (int[] perm : perms) {
            System.arraycopy(perm, 0, p.values, 0, 4);
            boolean ok = true;
            for (int f = 0; f < p.faces.length && ok; f++) {
                int[] face = p.faces[f];
                int[] tile = D4_TILES[d4Tile(p, face)];
                double[] c = centroid(p, face);
                double[] up = sub(p.v[vertexWith(p, face, tile[0])], c);
                double[] right = cross(up, p.normals[f]);
                ok = dot(sub(p.v[vertexWith(p, face, tile[2])], c), right)
                    > dot(sub(p.v[vertexWith(p, face, tile[1])], c), right);
            }
            if (ok) return;
        }
    }

    private static double[] centroid(Poly p, int[] face) {
        double[] c = new double[3];
        for (int i : face) for (int k = 0; k < 3; k++) c[k] += p.v[i][k] / face.length;
        return c;
    }

    // each face is laid on its tile with one corner up (an edge for the D6) and stretched to the tile's bounds,
    // which is how the art was cut
    private static double[][][] atlasUv(int sides, Poly p) {
        int cols = cols(sides), rows = rows(sides);
        double[][][] out = new double[p.faces.length][][];
        for (int f = 0; f < p.faces.length; f++) {
            int[] face = p.faces[f];
            double[] c = centroid(p, face);
            double[][] local = new double[face.length][2];
            int tile;
            if (sides == 4) {
                tile = d4Tile(p, face);
                int[] corners = D4_TILES[tile];
                for (int k = 0; k < face.length; k++) {
                    int value = p.values[face[k]];
                    local[k] = value == corners[0] ? new double[] { 0.5, 0.03 }
                        : value == corners[1] ? new double[] { 0.03, 0.97 } : new double[] { 0.97, 0.97 };
                }
            } else {
                tile = p.values[f] - 1;
                double[] top = sides == 6 ? mid(p.v[face[0]], p.v[face[1]]) : p.v[face[0]];
                double[] up = norm(sub(top, c));
                double[] right = cross(up, p.normals[f]);
                double minX = 9, maxX = -9, minY = 9, maxY = -9;
                for (int k = 0; k < face.length; k++) {
                    double[] d = sub(p.v[face[k]], c);
                    local[k] = new double[] { dot(d, right), dot(d, up) };
                    minX = Math.min(minX, local[k][0]);
                    maxX = Math.max(maxX, local[k][0]);
                    minY = Math.min(minY, local[k][1]);
                    maxY = Math.max(maxY, local[k][1]);
                }
                for (double[] l : local) {
                    l[0] = 0.015 + 0.97 * (l[0] - minX) / (maxX - minX);
                    l[1] = 0.015 + 0.97 * (maxY - l[1]) / (maxY - minY);
                }
            }
            out[f] = new double[face.length][];
            for (int k = 0; k < face.length; k++) {
                out[f][k] = new double[] { (tile % cols + local[k][0]) / cols, (tile / cols + local[k][1]) / rows };
            }
        }
        return out;
    }

    private static double[] mid(double[] a, double[] b) {
        return new double[] { (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2 };
    }

    private static Poly build(int sides) {
        double p = (1 + Math.sqrt(5)) / 2, ip = 1 / p;
        return switch (sides) {
            case 4 -> make(
                new double[][] { { 1, 1, 1 }, { 1, -1, -1 }, { -1, 1, -1 }, { -1, -1, 1 } },
                new double[][] { { -1, -1, -1 }, { -1, 1, 1 }, { 1, -1, 1 }, { 1, 1, -1 } },
                true);
            case 6 -> make(cube(), axes(), false);
            case 8 -> make(axes(), cube(), false);
            case 12 -> make(dodeca(p, ip), dualOfDodeca(p), false);
            default -> make(icosa(p), dualOfIcosa(p, ip), false);
        };
    }

    private static double[][] cube() {
        List<double[]> out = new ArrayList<>();
        for (int x = -1; x <= 1; x += 2)
            for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) out.add(new double[] { x, y, z });
        return out.toArray(new double[0][]);
    }

    private static double[][] axes() {
        return new double[][] { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    }

    private static double[][] icosa(double p) {
        List<double[]> out = new ArrayList<>();
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            out.add(new double[] { 0, a, b * p });
            out.add(new double[] { a, b * p, 0 });
            out.add(new double[] { b * p, 0, a });
        }
        return out.toArray(new double[0][]);
    }

    // face directions: the vertices of the dual solid, in the matching orientation
    private static double[][] dualOfDodeca(double p) {
        List<double[]> out = new ArrayList<>();
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            out.add(new double[] { 0, a * p, b });
            out.add(new double[] { a * p, b, 0 });
            out.add(new double[] { a, 0, b * p });
        }
        return out.toArray(new double[0][]);
    }

    private static double[][] dualOfIcosa(double p, double ip) {
        List<double[]> out = new ArrayList<>();
        Collections.addAll(out, cube());
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            out.add(new double[] { a * ip, 0, b * p });
            out.add(new double[] { 0, a * p, b * ip });
            out.add(new double[] { a * p, b * ip, 0 });
        }
        return out.toArray(new double[0][]);
    }

    private static double[][] dodeca(double p, double ip) {
        List<double[]> out = new ArrayList<>();
        Collections.addAll(out, cube());
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            out.add(new double[] { 0, a * ip, b * p });
            out.add(new double[] { a * ip, b * p, 0 });
            out.add(new double[] { a * p, 0, b * ip });
        }
        return out.toArray(new double[0][]);
    }

    // each face is the set of vertices furthest along its normal, sorted around the normal
    private static Poly make(double[][] verts, double[][] normals, boolean vertexValues) {
        double[][] v = new double[verts.length][];
        for (int i = 0; i < verts.length; i++) v[i] = norm(verts[i]);
        double[][] n = new double[normals.length][];
        for (int i = 0; i < normals.length; i++) n[i] = norm(normals[i]);
        int[][] faces = new int[n.length][];
        double inradius = 1;
        for (int f = 0; f < n.length; f++) {
            double max = -9;
            for (double[] x : v) max = Math.max(max, dot(x, n[f]));
            inradius = Math.min(inradius, max);
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < v.length; i++) if (dot(v[i], n[f]) > max - 1e-6) ids.add(i);
            double[] c = new double[3];
            for (int i : ids) for (int k = 0; k < 3; k++) c[k] += v[i][k] / ids.size();
            double[] u = norm(sub(v[ids.get(0)], c)), w = cross(n[f], u);
            ids.sort((a, b) -> {
                double[] da = sub(v[a], c), db = sub(v[b], c);
                return Double.compare(Math.atan2(dot(da, w), dot(da, u)), Math.atan2(dot(db, w), dot(db, u)));
            });
            faces[f] = ids.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        }
        int[] values = new int[vertexValues ? v.length : n.length];
        if (vertexValues) {
            for (int i = 0; i < values.length; i++) values[i] = i + 1;
        } else {
            // opposite faces add up to sides + 1, as on real dice
            int next = 1, total = n.length;
            for (int f = 0; f < total; f++) {
                if (values[f] != 0) continue;
                values[f] = next;
                for (int g = 0; g < total; g++) {
                    if (g != f && values[g] == 0 && dot(n[f], n[g]) < -0.999) values[g] = total + 1 - next;
                }
                next++;
            }
        }
        return new Poly(v, faces, n, values, vertexValues, inradius);
    }

    /** A throw played back frame by frame: positions, orientations and the frames where it hits the table. */
    static final class Roll {

        final int sides, result;
        final List<double[]> pos = new ArrayList<>();
        final List<double[]> rot = new ArrayList<>();
        final List<Integer> bounces = new ArrayList<>();
        int settleEnd;

        Roll(int sides, int result) {
            this.sides = sides;
            this.result = result;
        }

        int frames() {
            return pos.size();
        }
    }

    /** A die thrown from {@code start} towards {@code target}, ending with {@code result} on top. */
    static Roll simulate(int sides, int result, long seed, double[] start, double[] target, double floor, double r) {
        Poly poly = poly(sides);
        Roll roll = new Roll(sides, result);
        double[] q = fly(roll, poly.v, 0.36, seed, start, target, floor, r);
        int down = 0;
        for (int f = 1; f < poly.normals.length; f++) {
            if (rotate(q, poly.normals[f])[1] < rotate(q, poly.normals[down])[1]) down = f;
        }
        q = settle(roll, q, rotate(q, poly.normals[down]), floor + r * poly.inradius);
        // the die lies the same either way: its art is turned with a turn of the solid onto itself instead of the die
        double[] localUp = rotate(conj(q), new double[] { 0, 1, 0 });
        double[][] features = poly.vertexValues ? poly.v : poly.normals;
        double[] top = features[0];
        for (double[] f : features) if (dot(f, localUp) > dot(top, localUp)) top = f;
        turnArt(roll, symmetry(poly, poly.upFor(result), top));
        return roll;
    }

    /** A coin of radius {@code r} thrown onto the table, ending with its top side up when {@code topUp}. */
    static Roll simulateCoin(boolean topUp, long seed, double[] start, double[] target, double floor, double r,
        double thickness) {
        Roll roll = new Roll(2, topUp ? 1 : 2);
        double h = thickness / 2 / r;
        int seg = 16;
        double[][] rim = new double[seg * 2][];
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg;
            rim[i * 2] = new double[] { Math.cos(a), h, Math.sin(a) };
            rim[i * 2 + 1] = new double[] { Math.cos(a), -h, Math.sin(a) };
        }
        double[] q = fly(roll, rim, 0.3, seed, start, target, floor, r);
        boolean up = rotate(q, new double[] { 0, 1, 0 })[1] >= 0;
        q = settle(roll, q, rotate(q, new double[] { 0, up ? -1 : 1, 0 }), floor + thickness / 2);
        if (up != topUp) turnArt(roll, axisAngle(new double[] { 1, 0, 0 }, Math.PI));
        return roll;
    }

    private static void turnArt(Roll roll, double[] s) {
        for (int i = 0; i < roll.rot.size(); i++) roll.rot.set(i, mul(roll.rot.get(i), s));
    }

    /**
     * The free flight and tumble of a rigid body given by its corner points (in units of {@code r}): gravity, impacts
     * and friction on every corner touching the table, until it lies still. Returns the last orientation.
     */
    private static double[] fly(Roll roll, double[][] shape, double inertia, long seed, double[] start, double[] target,
        double floor, double r) {
        Random rnd = new Random(seed);
        double flight = 0.42 + rnd.nextDouble() * 0.12;
        double[] x = start.clone();
        double[] v = { (target[0] - start[0]) / flight * 0.62, 0, (target[2] - start[2]) / flight * 0.62 };
        v[1] = (floor + r - start[1] - 0.5 * GRAVITY * flight * flight) / flight;
        double[] q = norm4(
            new double[] { rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian() });
        // a throw rolls forwards off the fingers, with some wobble
        double[] forward = cross(new double[] { 0, 1, 0 }, v);
        double roll0 = (0.5 + rnd.nextDouble() * 0.4) / r;
        double[] w = { forward[0] * roll0 + rnd.nextGaussian() * 4, rnd.nextGaussian() * 4,
            forward[2] * roll0 + rnd.nextGaussian() * 4 };
        double[][] pts = new double[shape.length][];
        for (int i = 0; i < shape.length; i++)
            pts[i] = new double[] { shape[i][0] * r, shape[i][1] * r, shape[i][2] * r };
        double iinv = 1 / (inertia * r * r);
        double dt = 1.0 / (FPS * SUBSTEPS);
        int still = 0, lastHit = -99;
        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            boolean touching = false;
            double hit = 0;
            for (int sub = 0; sub < SUBSTEPS; sub++) {
                v[1] += GRAVITY * dt;
                List<double[]> arms = new ArrayList<>();
                for (double[] p : pts) {
                    double[] arm = rotate(q, p);
                    if (x[1] + arm[1] < floor + 0.002) arms.add(arm);
                }
                if (!arms.isEmpty()) {
                    touching = true;
                    hit = Math.max(hit, contact(arms, v, w, iinv));
                }
                for (int k = 0; k < 3; k++) x[k] += v[k] * dt;
                double[] spin = mul(new double[] { 0, w[0], w[1], w[2] }, q);
                q = norm4(
                    new double[] { q[0] + spin[0] * dt / 2, q[1] + spin[1] * dt / 2, q[2] + spin[2] * dt / 2,
                        q[3] + spin[3] * dt / 2 });
                double deepest = 0;
                for (double[] p : pts) deepest = Math.max(deepest, floor - x[1] - rotate(q, p)[1]);
                x[1] += deepest;
                keepOnTable(x, v);
            }
            if (touching) {
                for (int k = 0; k < 3; k++) w[k] *= 0.97;
            }
            if (hit > 0.45 && frame - lastHit > 5) {
                roll.bounces.add(frame);
                lastHit = frame;
            }
            roll.pos.add(x.clone());
            roll.rot.add(q.clone());
            still = touching && Math.sqrt(dot(v, v)) < 0.04 && Math.sqrt(dot(w, w)) < 0.6 ? still + 1 : 0;
            if (still > 10) break;
        }
        return q;
    }

    // sequential impulses on the corners touching the table; returns the hardest impact speed
    private static double contact(List<double[]> arms, double[] v, double[] w, double iinv) {
        int n = arms.size();
        double[] jn = new double[n], jx = new double[n], jz = new double[n], bounce = new double[n];
        double impact = 0;
        for (int i = 0; i < n; i++) {
            double vn = v[1] + cross(w, arms.get(i))[1];
            impact = Math.max(impact, -vn);
            bounce[i] = vn < -0.6 ? -RESTITUTION * vn : 0;
        }
        for (int it = 0; it < 8; it++) {
            for (int i = 0; i < n; i++) {
                double[] a = arms.get(i);
                double[] vel = add(v, cross(w, a));
                double dj = (bounce[i] - vel[1]) / (1 + iinv * (a[0] * a[0] + a[2] * a[2]));
                double nj = Math.max(0, jn[i] + dj);
                push(v, w, a, 0, nj - jn[i], 0, iinv);
                jn[i] = nj;
                double limit = FRICTION * jn[i];
                vel = add(v, cross(w, a));
                nj = Math.max(-limit, Math.min(limit, jx[i] - vel[0] / (1 + iinv * (a[1] * a[1] + a[2] * a[2]))));
                push(v, w, a, nj - jx[i], 0, 0, iinv);
                jx[i] = nj;
                vel = add(v, cross(w, a));
                nj = Math.max(-limit, Math.min(limit, jz[i] - vel[2] / (1 + iinv * (a[0] * a[0] + a[1] * a[1]))));
                push(v, w, a, 0, 0, nj - jz[i], iinv);
                jz[i] = nj;
            }
        }
        return impact;
    }

    private static void push(double[] v, double[] w, double[] arm, double px, double py, double pz, double iinv) {
        double[] p = { px, py, pz }, dw = cross(arm, p);
        for (int k = 0; k < 3; k++) {
            v[k] += p[k];
            w[k] += dw[k] * iinv;
        }
    }

    // nothing rolls off the table: its rim pushes back
    private static void keepOnTable(double[] x, double[] v) {
        double dx = x[0] - TABLE_CENTER, dz = x[2] - TABLE_CENTER, d = Math.sqrt(dx * dx + dz * dz);
        if (d < TABLE_REACH) return;
        double out = (v[0] * dx + v[2] * dz) / d;
        if (out <= 0) return;
        v[0] -= 1.4 * out * dx / d;
        v[2] -= 1.4 * out * dz / d;
    }

    // the last few degrees onto the face it lies nearest to, falling faster as it goes
    private static double[] settle(Roll roll, double[] q, double[] down, double restY) {
        double[] fix = fromTo(down, new double[] { 0, -1, 0 });
        double angle = 2 * Math.acos(Math.min(1, Math.abs(fix[0])));
        double[] q1 = mul(fix, q);
        double[] from = roll.pos.get(roll.frames() - 1);
        int n = 2 + (int) (angle * 14);
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n, e = t * t;
            roll.pos.add(new double[] { from[0], from[1] + (restY - from[1]) * e, from[2] });
            roll.rot.add(slerp(q, q1, e));
        }
        roll.settleEnd = roll.frames();
        return q1;
    }

    /** A turn of the solid onto itself that takes the local direction {@code from} to {@code to}. */
    private static double[] symmetry(Poly p, double[] from, double[] to) {
        double[] a = p.v[0];
        int k = 1;
        while (Math.abs(dot(a, p.v[k])) > 0.99) k++;
        double ab = dot(a, p.v[k]);
        double[][] f1 = frame(a, p.v[k]);
        for (double[] vi : p.v) for (double[] vj : p.v) {
            if (Math.abs(dot(vi, vj) - ab) > 1e-6) continue;
            double[][] f2 = frame(vi, vj);
            double[][] m = new double[3][3];
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
                for (int e = 0; e < 3; e++) m[r][c] += f2[e][r] * f1[e][c];
            }
            double[] moved = apply(m, from);
            if (Math.sqrt(dot(sub(moved, to), sub(moved, to))) > 1e-6 || !onto(p, m)) continue;
            return quat(m);
        }
        return new double[] { 1, 0, 0, 0 };
    }

    private static double[][] frame(double[] a, double[] b) {
        double[] e1 = norm(sub(b, new double[] { a[0] * dot(a, b), a[1] * dot(a, b), a[2] * dot(a, b) }));
        return new double[][] { a, e1, cross(a, e1) };
    }

    private static double[] apply(double[][] m, double[] v) {
        return new double[] { m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] };
    }

    private static boolean onto(Poly p, double[][] m) {
        for (double[] v : p.v) {
            double[] u = apply(m, v);
            boolean found = false;
            for (double[] w : p.v) if (Math.abs(dot(u, w) - 1) < 1e-6) {
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static double[] quat(double[][] m) {
        double tr = m[0][0] + m[1][1] + m[2][2];
        if (tr > 0) {
            double s = Math.sqrt(tr + 1) * 2;
            return norm4(
                new double[] { s / 4, (m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s });
        }
        if (m[0][0] > m[1][1] && m[0][0] > m[2][2]) {
            double s = Math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2;
            return norm4(
                new double[] { (m[2][1] - m[1][2]) / s, s / 4, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s });
        }
        if (m[1][1] > m[2][2]) {
            double s = Math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2;
            return norm4(
                new double[] { (m[0][2] - m[2][0]) / s, (m[0][1] + m[1][0]) / s, s / 4, (m[1][2] + m[2][1]) / s });
        }
        double s = Math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2;
        return norm4(new double[] { (m[1][0] - m[0][1]) / s, (m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, s / 4 });
    }

    // ------------------------------------------------------------------ drawing

    // created on first draw, so the geometry and the simulation also run without a GL context
    private static final class Gl {

        static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    }

    private static final Map<Integer, ResourceLocation> ART = new HashMap<>(), BADGE = new HashMap<>();

    private static ResourceLocation art(int sides, boolean badge) {
        return (badge ? BADGE : ART)
            .computeIfAbsent(sides, s -> Mino.id("textures/fx/dice_d" + s + (badge ? "_hud" : "") + ".png"));
    }

    /** Position and orientation at {@code seconds}: {x, y, z} and {w, x, y, z}. */
    private static double[][] pose(Roll roll, double seconds) {
        double fi = Math.min(roll.frames() - 1, Math.max(0, seconds * FPS));
        int i0 = (int) fi, i1 = Math.min(roll.frames() - 1, i0 + 1);
        double f = fi - i0;
        double[] a = roll.pos.get(i0), b = roll.pos.get(i1);
        return new double[][] { { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f },
            slerp(roll.rot.get(i0), roll.rot.get(i1), f) };
    }

    /** Draws the die at frame time {@code seconds}, in table space; returns its center. */
    static double[] draw(Roll roll, double seconds, double r, float alpha) {
        Poly poly = poly(roll.sides);
        double[][] pose = pose(roll, seconds);
        double[] p = pose[0], q = pose[1];
        GL11.glPushMatrix();
        GL11.glTranslated(p[0], p[1], p[2]);
        multiply(q);
        GL11.glScaled(r, r, r);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(art(roll.sides, false));
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int fIdx = 0; fIdx < poly.faces.length; fIdx++) {
            int[] face = poly.faces[fIdx];
            double[][] uv = poly.uv[fIdx];
            int g = (int) (255 * (0.6 + 0.4 * Math.max(0, dot(rotate(q, poly.normals[fIdx]), LIGHT))));
            t.setColorRGBA(g, g, g, (int) (alpha * 255));
            for (int k = 1; k + 1 < face.length; k++) {
                vertex(t, poly.v[face[0]], uv[0]);
                vertex(t, poly.v[face[k]], uv[k]);
                vertex(t, poly.v[face[k + 1]], uv[k + 1]);
            }
        }
        t.draw();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
        return p;
    }

    /**
     * A coin of radius {@code r} and the given thickness: its two faces carry their own art, its rim is plain metal.
     * Returns its center.
     */
    static double[] drawCoin(Roll roll, double seconds, double r, double thickness, ResourceLocation top,
        ResourceLocation bottom, int rimRgb, float alpha) {
        double[][] pose = pose(roll, seconds);
        double[] p = pose[0], q = pose[1];
        double h = thickness / 2 / r;
        int seg = 24;
        GL11.glPushMatrix();
        GL11.glTranslated(p[0], p[1], p[2]);
        multiply(q);
        GL11.glScaled(r, r, r);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg, am = (a0 + a1) / 2;
            double[] n = rotate(q, new double[] { Math.cos(am), 0, Math.sin(am) });
            int c = shade(rimRgb, 0.55 + 0.45 * Math.max(0, dot(n, LIGHT)));
            t.setColorRGBA((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (alpha * 255));
            t.addVertex(Math.cos(a0), -h, Math.sin(a0));
            t.addVertex(Math.cos(a1), -h, Math.sin(a1));
            t.addVertex(Math.cos(a1), h, Math.sin(a1));
            t.addVertex(Math.cos(a0), h, Math.sin(a0));
        }
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int side = 0; side < 2; side++) {
            double y = side == 0 ? h : -h;
            double[] n = rotate(q, new double[] { 0, side == 0 ? 1 : -1, 0 });
            int g = (int) (255 * (0.6 + 0.4 * Math.max(0, dot(n, LIGHT))));
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(side == 0 ? top : bottom);
            t.startDrawing(GL11.GL_TRIANGLE_FAN);
            t.setColorRGBA(g, g, g, (int) (alpha * 255));
            t.addVertexWithUV(0, y, 0, 0.5, 0.5);
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2 * i / seg;
                // the underside is mirrored so its art reads the right way round from below
                double u = 0.5 + 0.5 * Math.cos(a) * (side == 0 ? 1 : -1), v = 0.5 + 0.5 * Math.sin(a);
                t.addVertexWithUV(Math.cos(a), y, Math.sin(a), u, v);
            }
            t.draw();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
        return p;
    }

    /** The corner badge: the face with the rolled number, its art straight from the texture. */
    public static void drawBadge(int sides, int result, float cx, float cy, float size, float alpha) {
        int cols = cols(sides), rows = rows(sides);
        int tile = result - 1;
        float turn = 0;
        if (sides == 4) {
            // the D4 reads at a corner: the face showing the number turns that corner to the top
            for (int t = 0; t < D4_TILES.length; t++) for (int k = 0; k < 3; k++) if (D4_TILES[t][k] == result) {
                tile = t;
                turn = k == 0 ? 0 : k == 1 ? 120 : -120;
                t = D4_TILES.length;
                break;
            }
        }
        float aspect = sides == 6 ? 1f : sides == 12 ? 1.04f : 1.13f;
        float w = size * Math.min(1f, aspect), hgt = size / Math.max(1f, aspect);
        double u0 = (tile % cols) / (double) cols, u1 = u0 + 1.0 / cols;
        double v0 = (tile / cols) / (double) rows, v1 = v0 + 1.0 / rows;
        // triangles turn about their middle, two thirds of the way down
        float pivot = sides == 4 || sides == 8 || sides == 20 ? hgt * 2 / 3f - hgt / 2f : 0;
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy + pivot, 0);
        GL11.glRotatef(turn, 0, 0, 1);
        GL11.glTranslatef(0, -pivot, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, alpha);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(art(sides, true));
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(-w / 2, hgt / 2, 0, u0, v1);
        t.addVertexWithUV(w / 2, hgt / 2, 0, u1, v1);
        t.addVertexWithUV(w / 2, -hgt / 2, 0, u1, v0);
        t.addVertexWithUV(-w / 2, -hgt / 2, 0, u0, v0);
        t.draw();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static final double[] LIGHT = norm(new double[] { 0.35, 1, 0.25 });

    private static void vertex(Tessellator t, double[] v, double[] uv) {
        t.addVertexWithUV(v[0], v[1], v[2], uv[0], uv[1]);
    }

    private static int shade(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k), g = (int) Math.min(255, ((rgb >> 8) & 255) * k),
            b = (int) Math.min(255, (rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }

    private static void multiply(double[] q) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        float[] m = { (float) (1 - 2 * (y * y + z * z)), (float) (2 * (x * y + w * z)), (float) (2 * (x * z - w * y)),
            0, (float) (2 * (x * y - w * z)), (float) (1 - 2 * (x * x + z * z)), (float) (2 * (y * z + w * x)), 0,
            (float) (2 * (x * z + w * y)), (float) (2 * (y * z - w * x)), (float) (1 - 2 * (x * x + y * y)), 0, 0, 0, 0,
            1 };
        Gl.MATRIX.clear();
        Gl.MATRIX.put(m);
        Gl.MATRIX.flip();
        GL11.glMultMatrix(Gl.MATRIX);
    }

    // ------------------------------------------------------------------ vector and quaternion math ({w, x, y, z})

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] add(double[] a, double[] b) {
        return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    private static double[] conj(double[] q) {
        return new double[] { q[0], -q[1], -q[2], -q[3] };
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? new double[] { 0, 1, 0 } : new double[] { a[0] / l, a[1] / l, a[2] / l };
    }

    private static double[] norm4(double[] q) {
        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        return new double[] { q[0] / l, q[1] / l, q[2] / l, q[3] / l };
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[] { a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
            a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
            a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
            a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0] };
    }

    private static double[] axisAngle(double[] axis, double angle) {
        double s = Math.sin(angle / 2);
        return new double[] { Math.cos(angle / 2), axis[0] * s, axis[1] * s, axis[2] * s };
    }

    static double[] rotate(double[] q, double[] v) {
        double[] r = mul(mul(q, new double[] { 0, v[0], v[1], v[2] }), new double[] { q[0], -q[1], -q[2], -q[3] });
        return new double[] { r[1], r[2], r[3] };
    }

    private static double[] fromTo(double[] a, double[] b) {
        double d = dot(a, b);
        if (d > 0.99999) return new double[] { 1, 0, 0, 0 };
        if (d < -0.99999) return axisAngle(
            norm(cross(a, Math.abs(a[0]) < 0.9 ? new double[] { 1, 0, 0 } : new double[] { 0, 0, 1 })),
            Math.PI);
        double[] c = cross(a, b);
        return norm4(new double[] { 1 + d, c[0], c[1], c[2] });
    }

    private static double[] slerp(double[] a, double[] b, double t) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        double[] bb = b;
        if (d < 0) {
            d = -d;
            bb = new double[] { -b[0], -b[1], -b[2], -b[3] };
        }
        if (d > 0.9995) {
            return norm4(
                new double[] { a[0] + (bb[0] - a[0]) * t, a[1] + (bb[1] - a[1]) * t, a[2] + (bb[2] - a[2]) * t,
                    a[3] + (bb[3] - a[3]) * t });
        }
        double th = Math.acos(d), s = Math.sin(th);
        double wa = Math.sin((1 - t) * th) / s, wb = Math.sin(t * th) / s;
        return new double[] { a[0] * wa + bb[0] * wb, a[1] * wa + bb[1] * wb, a[2] * wa + bb[2] * wb,
            a[3] * wa + bb[3] * wb };
    }
}
