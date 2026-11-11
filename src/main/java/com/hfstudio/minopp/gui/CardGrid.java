package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.function.IntPredicate;

import com.hfstudio.minopp.game.Card;
import com.hfstudio.minopp.gui.ui.UiDraw;
import com.hfstudio.minopp.render.CardRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CardGrid {

    private List<Card> cards = List.of();
    private int width, cardWidth, cardHeight, columns, rows, gap, top, page, left;

    public void setCards(List<Card> cards) {
        this.cards = List.copyOf(cards);
        page = 0;
    }

    public List<Card> cards() {
        return cards;
    }

    public void layout(int width, int height, int minimumWidth) {
        layout(width, height, minimumWidth, 34, 34);
    }

    public void layout(int width, int height, int minimumWidth, int topInset, int bottomInset) {
        left = 0;
        if (width <= 0 || height <= 0 || minimumWidth < 1 || minimumWidth > 30 || topInset < 0 || bottomInset < 0)
            throw new IllegalArgumentException("Invalid card grid dimensions");
        this.width = width;
        cardWidth = 30;
        gap = 4;
        int available = Math.max(1, width - 40);
        while (true) {
            columns = Math.max(1, (available + gap) / (cardWidth + gap));
            cardHeight = Math.round(cardWidth * 8.9f / 5.6f);
            rows = Math.max(1, (cards.size() + columns - 1) / columns);
            if (rows * (cardHeight + gap) <= height - topInset - bottomInset || cardWidth <= minimumWidth) break;
            cardWidth--;
            if (cardWidth < 18) gap = 2;
        }
        rows = Math.min(rows, Math.max(1, (height - topInset - bottomInset) / (cardHeight + gap)));
        page = Math.min(page, pages() - 1);
        top = height - bottomInset - (rows * (cardHeight + gap) - gap);
    }

    public void layoutFromTop(int width, int height, int minimumWidth, int topInset, int bottomInset) {
        layout(width, height, minimumWidth, topInset, bottomInset);
        top = topInset;
    }

    public void layoutIn(int left, int width, int height, int minimumWidth, int topInset, int bottomInset) {
        layout(width, height, minimumWidth, topInset, bottomInset);
        this.left = left;
    }

    public void layoutFromTopIn(int left, int width, int height, int minimumWidth, int topInset, int bottomInset) {
        layoutIn(left, width, height, minimumWidth, topInset, bottomInset);
        top = topInset;
    }

    public int top() {
        return top;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return Math.max(1, (cards.size() + capacity() - 1) / capacity());
    }

    private int capacity() {
        return Math.max(1, columns * rows);
    }

    public void scroll(int direction) {
        page = Math.max(0, Math.min(pages() - 1, page + direction));
    }

    private int x(int index) {
        int count = Math.min(capacity(), cards.size() - page * capacity());
        int inRow = Math.min(columns, count - index / columns * columns);
        return left + width / 2 - (inRow * (cardWidth + gap) - gap) / 2 + index % columns * (cardWidth + gap);
    }

    public Card hit(int mouseX, int mouseY, IntPredicate selected) {
        int index = hitIndex(mouseX, mouseY, selected);
        return index < 0 ? null : cards.get(index);
    }

    public int hitIndex(int mouseX, int mouseY, IntPredicate selected) {
        int start = page * capacity();
        for (int i = 0; i < capacity() && start + i < cards.size(); i++) {
            Card card = cards.get(start + i);
            int x = x(i), y = top + i / columns * (cardHeight + gap) - (selected.test(card.uid) ? 5 : 0);
            if (mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight) return start + i;
        }
        return -1;
    }

    public int nearestIndex(int mouseX, int mouseY) {
        int start = page * capacity();
        int count = Math.min(capacity(), cards.size() - start);
        if (count <= 0 || columns <= 0) return -1;
        int row = Math.max(0, Math.min((count - 1) / columns, Math.floorDiv(mouseY - top + gap / 2, cardHeight + gap)));
        int rowStart = row * columns;
        int inRow = Math.min(columns, count - rowStart);
        int column = Math.max(0, Math.min(inRow - 1, Math.floorDiv(mouseX - x(rowStart) + gap / 2, cardWidth + gap)));
        return start + rowStart + column;
    }

    public void draw(String theme, boolean darkSide, int mouseX, int mouseY, IntPredicate selected) {
        int start = page * capacity();
        Card hover = hit(mouseX, mouseY, selected);
        for (int i = 0; i < capacity() && start + i < cards.size(); i++) {
            Card card = cards.get(start + i);
            boolean chosen = selected.test(card.uid);
            int x = x(i), y = top + i / columns * (cardHeight + gap) - (chosen ? 5 : 0);
            if (chosen || card == hover) UiDraw
                .roundRect(x - 2, y - 2, x + cardWidth + 2, y + cardHeight + 2, 3, chosen ? 0xFFFFC020 : 0x90FFFFFF);
            CardRenderer.drawFace2D(card.face(darkSide), theme, x, y, cardWidth, cardHeight);
        }
    }
}
