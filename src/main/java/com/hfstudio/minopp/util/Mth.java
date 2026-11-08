package com.hfstudio.minopp.util;

public final class Mth {

    private Mth() {}

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }
}
