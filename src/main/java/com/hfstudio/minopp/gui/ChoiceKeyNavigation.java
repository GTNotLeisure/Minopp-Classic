package com.hfstudio.minopp.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;

import com.hfstudio.minopp.gui.ui.UiDraw;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tracks keyboard focus independently of the action represented by each choice button.
 */
@SideOnly(Side.CLIENT)
public class ChoiceKeyNavigation {

    private List<GuiButton> options = List.of();
    private GuiButton focused, hovered;
    private int mouseX = Integer.MIN_VALUE, mouseY;

    public void setOptions(List<GuiButton> buttons) {
        options = List.copyOf(buttons);
        focused = hovered = null;
    }

    public void move(int direction) {
        if (options.isEmpty() || direction == 0) return;
        direction = Integer.signum(direction);
        int index = options.indexOf(selected());
        if (index < 0) index = direction > 0 ? -1 : 0;
        for (int count = 0; count < options.size(); count++) {
            index = Math.floorMod(index + direction, options.size());
            GuiButton button = options.get(index);
            if (button.enabled && button.visible) {
                focused = button;
                return;
            }
        }
    }

    public void move(int dx, int dy) {
        if (options.isEmpty()) return;
        GuiButton from = selected();
        float x = 0, y = 0;
        for (GuiButton button : options) {
            x += button.xPosition + button.width / 2f;
            y += button.yPosition + button.height / 2f;
        }
        x /= options.size();
        y /= options.size();
        if (from != null) {
            x = from.xPosition + from.width / 2f;
            y = from.yPosition + from.height / 2f;
        }
        GuiButton best = null;
        float score = Float.MAX_VALUE;
        for (GuiButton button : options) {
            if (button == from || !button.enabled || !button.visible) continue;
            float offsetX = button.xPosition + button.width / 2f - x;
            float offsetY = button.yPosition + button.height / 2f - y;
            float along = offsetX * dx + offsetY * dy;
            float candidate = along + 2 * Math.abs(offsetX * dy - offsetY * dx);
            if (along > 1 && candidate < score) {
                best = button;
                score = candidate;
            }
        }
        if (best != null) focused = best;
        else move(dx + dy > 0 ? 1 : -1);
    }

    public void updatePointer(int x, int y) {
        if (mouseX != Integer.MIN_VALUE && (mouseX != x || mouseY != y)) focused = null;
        mouseX = x;
        mouseY = y;
        hovered = null;
        for (GuiButton button : options) {
            if (button.visible && button.enabled
                && x >= button.xPosition
                && x < button.xPosition + button.width
                && y >= button.yPosition
                && y < button.yPosition + button.height) {
                hovered = button;
                break;
            }
        }
    }

    public GuiButton selected() {
        GuiButton button = focused != null ? focused : hovered;
        return button != null && button.enabled && button.visible ? button : null;
    }

    public void drawFocus() {
        if (focused == null || !focused.visible || !focused.enabled) return;
        UiDraw.roundRect(
            focused.xPosition - 2,
            focused.yPosition - 3,
            focused.xPosition + focused.width + 2,
            focused.yPosition + focused.height + 2,
            8,
            0xFFFFC21E);
    }
}
