package com.hfstudio.minopp.util;

public enum Direction {

    NORTH(0, -1, 2),
    EAST(1, 0, 3),
    SOUTH(0, 1, 0),
    WEST(-1, 0, 1);

    public final int stepX, stepZ;
    /**
     * 2D data value as in 1.20: SOUTH=0, WEST=1, NORTH=2, EAST=3
     */
    public final int data2d;

    Direction(int stepX, int stepZ, int data2d) {
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.data2d = data2d;
    }

    public String getSerializedName() {
        return name().toLowerCase();
    }

    public static Direction from2DDataValue(int value) {
        value = Math.floorMod(value, 4);
        for (Direction d : values()) if (d.data2d == value) return d;
        return SOUTH;
    }

    public static Direction fromYRot(double yRot) {
        return from2DDataValue((int) Math.floor(yRot / 90.0 + 0.5) & 3);
    }
}
