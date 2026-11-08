package com.hfstudio.minopp.util;

public final class Vec3d {

    public final double x, y, z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    public Vec3d subtract(double dx, double dy, double dz) {
        return new Vec3d(x - dx, y - dy, z - dz);
    }
}
