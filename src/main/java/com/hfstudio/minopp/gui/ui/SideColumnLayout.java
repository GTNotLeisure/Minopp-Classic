package com.hfstudio.minopp.gui.ui;

/** Proportional geometry for a sidebar and its bottom-aligned controls. */
public record SideColumnLayout(int boundary, int left, int width, int top, int bottom, int controlHeight, int gap) {

    public static int boundary(int screenWidth, float fraction) {
        return Math.round(screenWidth * fraction);
    }

    public static SideColumnLayout of(int screenWidth, int screenHeight, int fontHeight, float fraction) {
        int boundary = boundary(screenWidth, fraction);
        int margin = Math.max(2, Math.round(boundary * 0.05f));
        return new SideColumnLayout(
            boundary,
            margin,
            Math.max(1, boundary - margin * 2),
            Math.round(screenHeight * 0.025f),
            Math.round(screenHeight * 0.975f),
            Math.max(fontHeight + 7, Math.round(screenHeight * 0.05f)),
            Math.max(2, Math.round(screenHeight * 0.0125f)));
    }

    public int centerX() {
        return left + width / 2;
    }

    public int buttonTop(int rowFromBottom) {
        return bottom - controlHeight - rowFromBottom * (controlHeight + gap);
    }
}
