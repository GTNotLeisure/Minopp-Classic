package com.hfstudio.minopp.gui.ui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Smoothly animated vertical scroll offset with a draggable scrollbar.
 */
@SideOnly(Side.CLIENT)
public class SmoothScroll {

    private float target;
    private float current;
    private int contentHeight;
    private int viewHeight;
    private long lastFrame = System.nanoTime();

    private boolean dragging;
    private float dragOffset;

    public void setBounds(int contentHeight, int viewHeight) {
        this.contentHeight = contentHeight;
        this.viewHeight = viewHeight;
        clamp();
    }

    public int max() {
        return Math.max(0, contentHeight - viewHeight);
    }

    private void clamp() {
        target = Math.max(0, Math.min(max(), target));
        current = Math.max(0, Math.min(max(), current));
    }

    /** Advances the animation; call once per frame. */
    public float update() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (dragging) {
            current = target;
        } else {
            float k = 1 - (float) Math.exp(-dt * 14);
            current += (target - current) * k;
            if (Math.abs(target - current) < 0.3f) current = target;
        }
        return current;
    }

    public float offset() {
        return current;
    }

    public void scrollBy(float amount) {
        target += amount;
        clamp();
    }

    public void scrollTo(float value) {
        target = value;
        clamp();
    }

    // ---------------------------------------------------------------- scrollbar (track x, y, height)

    public boolean hasBar() {
        return max() > 0;
    }

    public int thumbHeight(int trackHeight) {
        if (contentHeight <= 0) return trackHeight;
        return Math.max(16, trackHeight * viewHeight / Math.max(viewHeight, contentHeight));
    }

    public int thumbY(int trackY, int trackHeight) {
        int th = thumbHeight(trackHeight);
        return trackY + (int) ((trackHeight - th) * (max() == 0 ? 0 : current / max()));
    }

    public void drawBar(int x, int y, int height, boolean hovered) {
        if (!hasBar()) return;
        UiDraw.roundRect(x, y, x + 4, y + height, 2, 0x60000000);
        int th = thumbHeight(height), ty = thumbY(y, height);
        int color = dragging ? UiDraw.GLOW : (hovered ? 0xFFFFFFFF : 0xFFDDDDDD);
        UiDraw.roundRect(x, ty, x + 4, ty + th, 2, color);
    }

    /** Starts dragging when the track was clicked; clicking the track outside the thumb jumps there. */
    public boolean mousePressed(int mouseX, int mouseY, int x, int y, int height) {
        if (!hasBar() || mouseX < x - 3 || mouseX > x + 7 || mouseY < y || mouseY > y + height) return false;
        int th = thumbHeight(height), ty = thumbY(y, height);
        if (mouseY < ty || mouseY > ty + th) {
            target = (mouseY - y - th / 2f) / (height - th) * max();
            clamp();
            current = target;
            ty = thumbY(y, height);
        }
        dragging = true;
        dragOffset = mouseY - ty;
        return true;
    }

    public void mouseDragged(int mouseY, int y, int height) {
        if (!dragging) return;
        int th = thumbHeight(height);
        target = (mouseY - dragOffset - y) / Math.max(1f, height - th) * max();
        clamp();
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }
}
