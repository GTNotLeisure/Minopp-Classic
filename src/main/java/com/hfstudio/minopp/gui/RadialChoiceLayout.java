package com.hfstudio.minopp.gui;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public record RadialChoiceLayout(int centerX, int centerY, int radius) {

    public static RadialChoiceLayout arrange(List<GuiButton> buttons, FontRenderer font, int centerX, int centerY,
        int left, int top, int right, int bottom) {
        int[] widths = new int[buttons.size()];
        for (int i = 0; i < widths.length; i++)
            widths[i] = Math.max(22, Math.min(160, font.getStringWidth(buttons.get(i).displayString) + 16));
        for (int size = 100; size >= 10; size -= 5) {
            for (int i = 0; i < widths.length; i++) buttons.get(i).width = Math.max(18, widths[i] * size / 100);
            RadialChoiceLayout layout = arrangeFixed(buttons, centerX, centerY, left, top, right, bottom);
            if (layout != null) return layout;
        }
        return null;
    }

    /**
     * Fits fixed-size controls on one circle; callers can paginate when the viewport cannot fit them.
     */
    public static RadialChoiceLayout arrangeFixed(List<GuiButton> buttons, int centerX, int centerY, int left, int top,
        int right, int bottom) {
        if (buttons.isEmpty()) return new RadialChoiceLayout(centerX, centerY, 0);
        double[] angles = new double[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) {
            angles[i] = buttons.size() == 1 ? -Math.PI / 2 : (i + 0.5) * Math.PI * 2 / buttons.size() - Math.PI / 2;
        }
        int maximum = Math
            .min(120, Math.min(Math.min(centerX - left, right - centerX), Math.min(centerY - top, bottom - centerY)));
        for (int radius = maximum; radius >= 12; radius -= 2) {
            // Both phases preserve mirror symmetry, even when labels have different widths.
            for (int rotation = 0; rotation < 2; rotation++) {
                double offset = rotation * Math.PI / buttons.size();
                for (int i = 0; i < buttons.size(); i++) {
                    GuiButton button = buttons.get(i);
                    button.xPosition = centerX + (int) Math.round(Math.cos(angles[i] + offset) * radius)
                        - button.width / 2;
                    button.yPosition = centerY + (int) Math.round(Math.sin(angles[i] + offset) * radius)
                        - button.height / 2;
                }
                if (fits(buttons, left, top, right, bottom)) return new RadialChoiceLayout(centerX, centerY, radius);
            }
        }
        return null;
    }

    private static boolean fits(List<GuiButton> buttons, int left, int top, int right, int bottom) {
        for (int i = 0; i < buttons.size(); i++) {
            GuiButton a = buttons.get(i);
            if (a.xPosition < left || a.yPosition < top
                || a.xPosition + a.width > right
                || a.yPosition + a.height > bottom) return false;
            for (int j = 0; j < i; j++) {
                GuiButton b = buttons.get(j);
                if (a.xPosition < b.xPosition + b.width + 4 && a.xPosition + a.width + 4 > b.xPosition
                    && a.yPosition < b.yPosition + b.height + 4
                    && a.yPosition + a.height + 4 > b.yPosition) return false;
            }
        }
        return true;
    }

    public int top() {
        return Math.max(24, centerY - radius - 40);
    }

    public void draw() {
        if (radius <= 0) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 0.85f, 0.4f, 0.3f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI / 32;
            GL11.glVertex2d(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
