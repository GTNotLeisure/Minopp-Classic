package com.hfstudio.minopp.gui;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Keeps configurable action keys out of the vanilla key-state map so shared keys preserve normal movement.
 */
@SideOnly(Side.CLIENT)
public class ActionKeyBinding extends KeyBinding {

    private final int defaultKey;
    private int configuredKey;

    public ActionKeyBinding(String description, int keyCode, String category) {
        super(description, Keyboard.KEY_NONE, category);
        defaultKey = configuredKey = keyCode;
    }

    @Override
    public int getKeyCode() {
        return configuredKey;
    }

    @Override
    public int getKeyCodeDefault() {
        return defaultKey;
    }

    @Override
    public void setKeyCode(int keyCode) {
        configuredKey = keyCode;
    }

    @Override
    public boolean getIsKeyPressed() {
        if (configuredKey > 0 && configuredKey < Keyboard.KEYBOARD_SIZE)
            return Keyboard.isCreated() && Keyboard.isKeyDown(configuredKey);
        int button = configuredKey + 100;
        return configuredKey < 0 && Mouse.isCreated()
            && button >= 0
            && button < Mouse.getButtonCount()
            && Mouse.isButtonDown(button);
    }
}
