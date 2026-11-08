package com.hfstudio.minopp.gui;

import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.hfstudio.minopp.entity.EntityAutoPlayer;
import com.hfstudio.minopp.network.C2SAutoPlayerConfigPacket;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * MinoBot preferences. The 1.20.1 version built this screen with YetAnotherConfigLib; as the options are bound to a
 * single entity rather than a static config class, it is a plain GuiScreen here.
 */
@SideOnly(Side.CLIENT)
public class AutoPlayerScreen extends GuiScreen {

    private final EntityAutoPlayer target;
    private final GuiScreen parent;

    // Pending values (applied on save)
    private boolean active, noPush, noWin, noPlayerDraw, startGame;
    private float forgetChance;
    private int noDelay;

    private GuiTextField nameField, skinField;
    private String hoverDescription = null;

    private static final int ROW_H = 24;
    private static final int COL_W = 200;

    public AutoPlayerScreen(EntityAutoPlayer target, GuiScreen parent) {
        this.target = target;
        this.parent = parent;
        active = target.getActive();
        noPush = target.getNoPush();
        noWin = target.autoPlayer.aiNoWin;
        noPlayerDraw = target.autoPlayer.aiNoPlayerDraw;
        startGame = target.autoPlayer.aiStartGame;
        forgetChance = target.autoPlayer.aiForgetChance;
        noDelay = target.autoPlayer.aiNoDelay;
    }

    private static String tr(String key, Object... args) {
        return Chat.translatable(key, args)
            .getFormattedText();
    }

    private int leftX() {
        return width / 2 - COL_W - 5;
    }

    private int rightX() {
        return width / 2 + 5;
    }

    private int topY() {
        return Math.max(30, height / 2 - 100);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int lx = leftX(), rx = rightX(), y = topY() + 12;

        buttonList.add(new GuiButton(0, lx, y, COL_W, 20, ""));
        nameField = new GuiTextField(fontRendererObj, lx + 1, y + ROW_H + 11, COL_W - 2, 16);
        nameField.setText(target.hasCustomNameTag() ? target.getCustomNameTag() : "");
        skinField = new GuiTextField(fontRendererObj, lx + 1, y + ROW_H * 2 + 22, COL_W - 2, 16);
        skinField.setMaxStringLength(64);
        skinField.setText(target.getSkin());
        buttonList.add(new GuiButton(1, lx, y + ROW_H * 3 + 22, COL_W, 20, ""));

        buttonList.add(new GuiButton(2, rx, y, COL_W, 20, ""));
        buttonList.add(new GuiButton(3, rx, y + ROW_H, COL_W, 20, ""));
        buttonList.add(
            new Slider(
                4,
                rx,
                y + ROW_H * 2,
                COL_W,
                20,
                () -> forgetChance,
                v -> forgetChance = v,
                v -> tr("gui.minopp.bot_config.forget_chance") + ": " + (int) (v * 100) + "%"));
        buttonList.add(new GuiButton(5, rx, y + ROW_H * 3 + 12, COL_W, 20, ""));
        buttonList.add(new GuiButton(6, rx, y + ROW_H * 4 + 12, COL_W, 20, ""));

        int by = y + ROW_H * 5 + 24;
        buttonList.add(
            new GuiButton(
                7,
                width / 2 - 155,
                by,
                100,
                20,
                EnumChatFormatting.RED + tr("gui.minopp.bot_config.remove")));
        buttonList.add(new GuiButton(8, width / 2 - 50, by, 100, 20, tr("gui.done")));
        buttonList.add(new GuiButton(9, width / 2 + 55, by, 100, 20, tr("gui.cancel")));
        refreshLabels();
    }

    private String yesNo(boolean v) {
        return v ? EnumChatFormatting.GREEN + tr("gui.yes") : EnumChatFormatting.RED + tr("gui.no");
    }

    private String onOff(boolean v) {
        return v ? EnumChatFormatting.GREEN + tr("options.on") : EnumChatFormatting.RED + tr("options.off");
    }

    private void refreshLabels() {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            switch (b.id) {
                case 0 -> b.displayString = tr("gui.minopp.bot_config.active") + ": " + onOff(active);
                case 1 -> b.displayString = tr("gui.minopp.bot_config.no_push") + ": " + yesNo(noPush);
                case 2 -> b.displayString = tr("gui.minopp.bot_config.no_win") + ": " + yesNo(noWin);
                case 3 -> b.displayString = tr("gui.minopp.bot_config.no_player_draw") + ": " + yesNo(noPlayerDraw);
                case 5 -> b.displayString = tr("gui.minopp.bot_config.no_delay") + ": "
                    + tr("gui.minopp.bot_config.no_delay." + noDelay);
                case 6 -> b.displayString = tr("gui.minopp.bot_config.start_game") + ": " + yesNo(startGame);
                default -> {}
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> active = !active;
            case 1 -> noPush = !noPush;
            case 2 -> noWin = !noWin;
            case 3 -> noPlayerDraw = !noPlayerDraw;
            case 5 -> noDelay = (noDelay + 1) % 3;
            case 6 -> startGame = !startGame;
            case 7 -> {
                C2SAutoPlayerConfigPacket.Client.sendDeleteC2S(target);
                mc.displayGuiScreen(null);
                return;
            }
            case 8 -> {
                save();
                mc.displayGuiScreen(parent);
                return;
            }
            case 9 -> {
                mc.displayGuiScreen(parent);
                return;
            }
            default -> {}
        }
        refreshLabels();
    }

    private void save() {
        target.setActive(active);
        target.setNoPush(noPush);
        target.setCustomNameTag(nameField.getText());
        target.setSkin(
            skinField.getText()
                .trim());
        target.autoPlayer.aiNoWin = noWin;
        target.autoPlayer.aiNoPlayerDraw = noPlayerDraw;
        target.autoPlayer.aiForgetChance = forgetChance;
        target.autoPlayer.aiNoDelay = (byte) noDelay;
        target.autoPlayer.aiStartGame = startGame;
        C2SAutoPlayerConfigPacket.Client.sendC2S(target);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (nameField.textboxKeyTyped(c, key) || skinField.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        nameField.mouseClicked(x, y, button);
        skinField.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        skinField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int lx = leftX(), rx = rightX(), top = topY();
        GuiDraw.drawCenteredString(fontRendererObj, tr("gui.minopp.bot_config.title"), width / 2, top - 18, 0xFFFFFFFF);
        GuiDraw.drawString(
            fontRendererObj,
            EnumChatFormatting.UNDERLINE + tr("gui.minopp.bot_config.category.entity"),
            lx,
            top,
            0xFFFFFFAA);
        GuiDraw.drawString(
            fontRendererObj,
            EnumChatFormatting.UNDERLINE + tr("gui.minopp.bot_config.category.difficulty"),
            rx,
            top,
            0xFFFFFFAA);
        int y = top + 12;
        GuiDraw.drawString(fontRendererObj, tr("gui.minopp.bot_config.custom_name"), lx, y + ROW_H, 0xFFAAAAAA);
        GuiDraw.drawString(fontRendererObj, tr("gui.minopp.bot_config.skin"), lx, y + ROW_H * 2 + 11, 0xFFAAAAAA);
        GuiDraw.drawString(
            fontRendererObj,
            EnumChatFormatting.UNDERLINE + tr("gui.minopp.bot_config.category.behavior"),
            rx,
            y + ROW_H * 3,
            0xFFFFFFAA);
        nameField.drawTextBox();
        skinField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);

        hoverDescription = null;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX >= b.xPosition && mouseY >= b.yPosition
                && mouseX < b.xPosition + b.width
                && mouseY < b.yPosition + b.height) {
                hoverDescription = switch (b.id) {
                    case 1 -> "gui.minopp.bot_config.no_push.summary";
                    case 2 -> "gui.minopp.bot_config.no_win.summary";
                    case 3 -> "gui.minopp.bot_config.no_player_draw.summary";
                    case 4 -> "gui.minopp.bot_config.forget_chance.summary";
                    case 6 -> "gui.minopp.bot_config.start_game.summary";
                    default -> null;
                };
            }
        }
        if (mouseX >= skinField.xPosition && mouseY >= skinField.yPosition
            && mouseX < skinField.xPosition + skinField.width
            && mouseY < skinField.yPosition + skinField.height) {
            hoverDescription = "gui.minopp.bot_config.skin.summary";
        }
        if (hoverDescription != null) {
            String text = tr(hoverDescription);
            int w = Math.min(fontRendererObj.getStringWidth(text), 300);
            int h = fontRendererObj.listFormattedStringToWidth(text, 300)
                .size() * 10;
            GuiDraw.fill(width / 2 - w / 2 - 4, height - h - 12, width / 2 + w / 2 + 4, height - 4, 0xC0000000);
            fontRendererObj.drawSplitString(text, width / 2 - w / 2, height - h - 8, 300, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Simple float slider (0..1, step 0.05). */
    private static class Slider extends GuiButton {

        private final java.util.function.Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final Function<Float, String> formatter;
        private boolean dragging;

        Slider(int id, int x, int y, int w, int h, java.util.function.Supplier<Float> getter, Consumer<Float> setter,
            Function<Float, String> formatter) {
            super(id, x, y, w, h, "");
            this.getter = getter;
            this.setter = setter;
            this.formatter = formatter;
            this.displayString = formatter.apply(getter.get());
        }

        @Override
        public int getHoverState(boolean mouseOver) {
            return 0;
        }

        private void updateValue(int mouseX) {
            float v = (float) (mouseX - (xPosition + 4)) / (float) (width - 8);
            v = Math.max(0f, Math.min(1f, v));
            v = Math.round(v * 20f) / 20f;
            setter.accept(v);
            displayString = formatter.apply(v);
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (visible) {
                if (dragging) updateValue(mouseX);
                mc.getTextureManager()
                    .bindTexture(buttonTextures);
                org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                int pos = xPosition + (int) (getter.get() * (width - 8));
                drawTexturedModalRect(pos, yPosition, 0, 66, 4, 20);
                drawTexturedModalRect(pos + 4, yPosition, 196, 66, 4, 20);
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                updateValue(mouseX);
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            dragging = false;
        }
    }
}
