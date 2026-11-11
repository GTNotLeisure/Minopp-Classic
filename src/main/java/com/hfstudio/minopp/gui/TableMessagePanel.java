package com.hfstudio.minopp.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.hfstudio.minopp.block.BlockEntityMinoTable;
import com.hfstudio.minopp.game.ActionMessage;
import com.hfstudio.minopp.game.ActionMessageHistory;
import com.hfstudio.minopp.gui.ui.SmoothScroll;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.util.Chat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TableMessagePanel {

    private final SmoothScroll scroll = new SmoothScroll();
    private final List<MessageLine> lines = new ArrayList<>();
    private ActionMessageHistory shownHistory;
    private long shownRevision = -1;
    private int wrapWidth;
    private String language;
    private FontRenderer shownFont;
    private int lineHeight;
    private boolean followLatest = true;
    private int x;
    private int y;
    private int width;
    private int height;

    private record MessageLine(long sequence, String text, int color) {}

    public void invalidateLayout() {
        wrapWidth = 0;
    }

    public void draw(BlockEntityMinoTable table, UUID playerId, int left, int top, int panelWidth, int panelHeight,
        int mouseX, int mouseY) {
        if (panelWidth <= 12 || panelHeight <= 36) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int statusHeight = GameOverlayLayer.INSTANCE
            .renderFixedStatus(table, playerId, left, top, panelWidth, (panelHeight - 18) / 2);
        int titleY = top + statusHeight + 6;
        x = left;
        y = titleY + font.FONT_HEIGHT + 5;
        width = panelWidth;
        height = Math.max(1, top + panelHeight - y);

        ActionMessageHistory history = table.clientMessageHistory();
        refreshLines(history, font, mc.gameSettings.language);
        scroll.setBounds(lines.size() * lineHeight, height);
        if (followLatest) scroll.scrollTo(scroll.max());
        float offset = scroll.update();

        String title = Chat.translatable("gui.minopp.hand.messages")
            .getFormattedText();
        UiDraw.fittedCentered(font, title, x + width / 2, titleY, width, 0xFFFFD070);
        GuiDraw.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x48000000);
        UiDraw.scissor(x - 2, y, width + 4, height);
        if (lines.isEmpty()) {
            int textY = y;
            for (String text : font.listFormattedStringToWidth(
                Chat.translatable("gui.minopp.hand.messages_empty")
                    .getFormattedText(),
                width - 10)) {
                UiDraw.drawStringWithBackdrop(font, text, x, textY, 0xFFAAAAAA);
                textY += lineHeight;
            }
        } else {
            int first = Math.max(0, (int) (offset / lineHeight));
            int last = Math.min(lines.size(), (int) Math.ceil((offset + height) / lineHeight));
            for (int i = first; i < last; i++) {
                MessageLine line = lines.get(i);
                UiDraw.drawStringWithBackdrop(
                    font,
                    line.text(),
                    x,
                    y + Math.round(i * lineHeight - offset),
                    line.color());
            }
        }
        UiDraw.endScissor();
        scroll.drawBar(x + width - 4, y, height, contains(mouseX, mouseY));
    }

    private void refreshLines(ActionMessageHistory history, FontRenderer font, String currentLanguage) {
        int nextWidth = width - 10;
        int nextLineHeight = font.FONT_HEIGHT + 2;
        if (shownHistory == history && shownRevision == history.revision()
            && wrapWidth == nextWidth
            && Objects.equals(language, currentLanguage)
            && shownFont == font
            && lineHeight == nextLineHeight) return;

        // Keep the visible message anchored when wrapping changes or older messages are evicted.
        int anchor = lines.isEmpty() ? -1 : Math.min(lines.size() - 1, (int) (scroll.offset() / lineHeight));
        long sequence = anchor < 0 ? -1
            : lines.get(anchor)
                .sequence();
        int part = 0;
        if (anchor >= 0) {
            while (anchor > 0 && lines.get(anchor - 1)
                .sequence() == sequence) {
                anchor--;
                part++;
            }
        }
        float withinLine = lineHeight == 0 ? 0 : scroll.offset() % lineHeight;
        lines.clear();
        for (ActionMessageHistory.Entry entry : history.entries()) {
            ActionMessage message = entry.message();
            int color = switch (message.type()) {
                case FAIL -> 0xFFFF7070;
                case MESSAGE_ALL -> 0xFFFFD070;
                case STATE -> 0xFFE8E8E8;
            };
            for (String paragraph : message.message()
                .getFormattedText()
                .split("\n", -1)) {
                for (String text : font.listFormattedStringToWidth(paragraph, nextWidth)) {
                    lines.add(new MessageLine(entry.sequence(), text, color));
                }
            }
        }
        shownHistory = history;
        shownRevision = history.revision();
        wrapWidth = nextWidth;
        language = currentLanguage;
        shownFont = font;
        lineHeight = nextLineHeight;
        scroll.setBounds(lines.size() * lineHeight, height);
        if (followLatest) {
            scroll.jumpTo(scroll.max());
        } else {
            int newAnchor = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i)
                    .sequence() != sequence) continue;
                newAnchor = i;
                while (part > 0 && newAnchor + 1 < lines.size()
                    && lines.get(newAnchor + 1)
                        .sequence() == sequence) {
                    newAnchor++;
                    part--;
                }
                break;
            }
            scroll.jumpTo(newAnchor * lineHeight + withinLine);
        }
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x - 2 && mouseX < x + width + 2 && mouseY >= y && mouseY < y + height;
    }

    public boolean scroll(int mouseX, int mouseY, int direction) {
        if (!contains(mouseX, mouseY)) return false;
        scroll.scrollBy(direction * lineHeight * 3f);
        followLatest = scroll.target() >= scroll.max() - 1;
        return true;
    }

    public boolean mousePressed(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        if (button == 0 && scroll.mousePressed(mouseX, mouseY, x + width - 4, y, height)) {
            followLatest = scroll.target() >= scroll.max() - 1;
        }
        return true;
    }

    public void mouseDragged(int mouseY) {
        if (!scroll.isDragging()) return;
        scroll.mouseDragged(mouseY, y, height);
        followLatest = scroll.target() >= scroll.max() - 1;
    }

    public void mouseReleased() {
        scroll.mouseReleased();
    }
}
