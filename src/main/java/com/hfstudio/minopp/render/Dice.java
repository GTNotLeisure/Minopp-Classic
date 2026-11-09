package com.hfstudio.minopp.render;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Polyhedral dice thrown onto the table: the throw is simulated once (gravity, bounces losing speed, spin dying down)
 * and played back; after the last bounce the die tips over onto the rolled number.
 */
@SideOnly(Side.CLIENT)
final class Dice {

    private Dice() {}

    static final int FPS = 60;
    private static final double GRAVITY = -14, RESTITUTION = 0.42, BOUNCE_FRICTION = 0.62;
    private static final int SETTLE_FRAMES = 22;

    /** A convex die of unit circumradius: faces as vertex index loops, counter-clockwise seen from outside. */
    static final class Poly {

        final double[][] v;
        final int[][] faces;
        final double[][] normals;
        /** Number printed on each face, or on each vertex for the D4. */
        final int[] values;
        final boolean vertexValues;
        final double inradius;

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
        return POLYS.computeIfAbsent(sides, Dice::build);
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
        for (double[] c : cube()) out.add(c);
        for (int a = -1; a <= 1; a += 2) for (int b = -1; b <= 1; b += 2) {
            out.add(new double[] { a * ip, 0, b * p });
            out.add(new double[] { 0, a * p, b * ip });
            out.add(new double[] { a * p, b * ip, 0 });
        }
        return out.toArray(new double[0][]);
    }

    private static double[][] dodeca(double p, double ip) {
        List<double[]> out = new ArrayList<>();
        for (double[] c : cube()) out.add(c);
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

    static Roll simulate(int sides, int result, long seed, double[] start, double[] target, double floor, double r) {
        Roll roll = new Roll(sides, result);
        Poly poly = poly(sides);
        Random rnd = new Random(seed);
        double flight = 0.5;
        double[] p = start.clone();
        double[] vel = { (target[0] - start[0]) / flight * 0.8, 0, (target[2] - start[2]) / flight * 0.8 };
        vel[1] = (floor + r - start[1] - 0.5 * GRAVITY * flight * flight) / flight;
        double[] spin = norm(new double[] { rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian() });
        double spinSpeed = 14 + rnd.nextDouble() * 6;
        double[] q = norm4(
            new double[] { rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian() });
        double dt = 1.0 / FPS;
        boolean grounded = false;
        int frame = 0;
        for (; frame < FPS * 3; frame++) {
            if (!grounded) vel[1] += GRAVITY * dt;
            for (int k = 0; k < 3; k++) p[k] += vel[k] * dt;
            if (p[1] <= floor + r && vel[1] < 0) {
                p[1] = floor + r;
                roll.bounces.add(frame);
                vel[1] = -vel[1] * RESTITUTION;
                vel[0] *= BOUNCE_FRICTION;
                vel[2] *= BOUNCE_FRICTION;
                spinSpeed *= BOUNCE_FRICTION;
                spin = norm(
                    new double[] { spin[0] + rnd.nextGaussian() * 0.5, spin[1] + rnd.nextGaussian() * 0.5,
                        spin[2] + rnd.nextGaussian() * 0.5 });
                if (vel[1] < 0.6) {
                    grounded = true;
                    vel[1] = 0;
                }
            }
            if (grounded) {
                vel[0] *= 0.86;
                vel[2] *= 0.86;
                spinSpeed *= 0.86;
            }
            q = mul(axisAngle(spin, spinSpeed * dt), q);
            roll.pos.add(p.clone());
            roll.rot.add(q.clone());
            if (grounded && spinSpeed < 1.2 && Math.hypot(vel[0], vel[2]) < 0.05) break;
        }
        // tip over: the shortest turn that brings the rolled number to the top, while sinking to rest on a face
        double[] upNow = rotate(q, poly.upFor(result));
        double[] fix = fromTo(upNow, new double[] { 0, 1, 0 });
        double[] q1 = mul(fix, q);
        double restY = floor + r * poly.inradius;
        double[] from = p.clone();
        for (int i = 1; i <= SETTLE_FRAMES; i++) {
            double t = i / (double) SETTLE_FRAMES, e = t * t * (3 - 2 * t);
            roll.pos.add(new double[] { from[0], from[1] + (restY - from[1]) * e, from[2] });
            roll.rot.add(slerp(q, q1, e));
        }
        roll.settleEnd = roll.frames();
        return roll;
    }

    // ------------------------------------------------------------------ drawing

    // created on first draw, so the geometry and the simulation also run without a GL context
    private static final class Gl {

        static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    }

    /** Draws the die at frame time {@code seconds}, in table space; returns its center. */
    static double[] draw(Roll roll, double seconds, double r, int rgb, float alpha) {
        Poly poly = poly(roll.sides);
        double fi = Math.min(roll.frames() - 1, Math.max(0, seconds * FPS));
        int i0 = (int) fi, i1 = Math.min(roll.frames() - 1, i0 + 1);
        double f = fi - i0;
        double[] a = roll.pos.get(i0), b = roll.pos.get(i1);
        double[] p = { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f };
        double[] q = slerp(roll.rot.get(i0), roll.rot.get(i1), f);

        GL11.glPushMatrix();
        GL11.glTranslated(p[0], p[1], p[2]);
        multiply(q);
        GL11.glScaled(r, r, r);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        for (int fIdx = 0; fIdx < poly.faces.length; fIdx++) {
            int[] face = poly.faces[fIdx];
            double[] worldN = rotate(q, poly.normals[fIdx]);
            double light = 0.55 + 0.45 * Math.max(0, dot(worldN, LIGHT));
            int shade = shade(rgb, light);
            t.setColorRGBA((shade >> 16) & 255, (shade >> 8) & 255, shade & 255, (int) (alpha * 255));
            for (int k = 1; k + 1 < face.length; k++) {
                vertex(t, poly.v[face[0]]);
                vertex(t, poly.v[face[k]]);
                vertex(t, poly.v[face[k + 1]]);
            }
        }
        t.draw();
        drawNumbers(poly, alpha);
        GL11.glPopMatrix();
        return p;
    }

    private static final double[] LIGHT = norm(new double[] { 0.35, 1, 0.25 });

    private static void drawNumbers(Poly poly, float alpha) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int a = Math.max(4, (int) (alpha * 255)) << 24;
        for (int f = 0; f < poly.faces.length; f++) {
            int[] face = poly.faces[f];
            double[] c = new double[3];
            for (int i : face) for (int k = 0; k < 3; k++) c[k] += poly.v[i][k] / face.length;
            double[] n = poly.normals[f];
            if (poly.vertexValues) {
                // a D4 reads at its top corner: each face shows the number of each corner next to it
                for (int i : face) {
                    double[] toCorner = sub(poly.v[i], c);
                    double[] at = { c[0] + toCorner[0] * 0.55, c[1] + toCorner[1] * 0.55, c[2] + toCorner[2] * 0.55 };
                    label(font, String.valueOf(poly.values[i]), at, n, norm(toCorner), 0.05, a);
                }
            } else {
                double[] up = norm(sub(poly.v[face[0]], c));
                double size = switch (poly.faces.length) {
                    case 6 -> 0.085;
                    case 8 -> 0.07;
                    case 12 -> 0.06;
                    default -> 0.045;
                };
                label(font, String.valueOf(poly.values[f]), c, n, up, size, a);
            }
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    // text lying on a face: +X to its right, down along -up, facing out along the normal
    private static void label(FontRenderer font, String text, double[] at, double[] n, double[] up, double size,
        int alpha) {
        double[] down = { -up[0], -up[1], -up[2] };
        double[] right = cross(down, n);
        GL11.glPushMatrix();
        Gl.MATRIX.clear();
        float[] m = { (float) right[0], (float) right[1], (float) right[2], 0, (float) down[0], (float) down[1],
            (float) down[2], 0, (float) n[0], (float) n[1], (float) n[2], 0, (float) (at[0] + n[0] * 0.012),
            (float) (at[1] + n[1] * 0.012), (float) (at[2] + n[2] * 0.012), 1 };
        Gl.MATRIX.put(m);
        Gl.MATRIX.flip();
        GL11.glMultMatrix(Gl.MATRIX);
        GL11.glScaled(size / 8, size / 8, size / 8);
        int w = font.getStringWidth(text);
        font.drawString(text, -w / 2, -4, alpha | 0xFFFFFF);
        GL11.glPopMatrix();
    }

    private static void vertex(Tessellator t, double[] v) {
        t.addVertex(v[0], v[1], v[2]);
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
