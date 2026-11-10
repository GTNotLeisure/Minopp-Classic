package com.hfstudio.minopp.gui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.hfstudio.minopp.game.Card;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

@SideOnly(Side.CLIENT)
public class CardGridSelection {

    private static final long HOLD_MS = 250;
    private final CardGrid grid;
    private final IntSet selected;
    private final IntSet initial = new IntLinkedOpenHashSet();
    private List<Card> cards = List.of();
    private int anchor = -1, end = -1, limit;
    private int startX, startY, startPage;
    private long pressedAt;
    private boolean selecting, dragging;

    public CardGridSelection(CardGrid grid, IntSet selected) {
        this.grid = Objects.requireNonNull(grid, "grid");
        this.selected = Objects.requireNonNull(selected, "selected");
    }

    public void selectRandom(int count) {
        release();
        selected.clear();
        List<Card> candidates = grid.cards();
        int remaining = Math.min(Math.max(0, count), candidates.size());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; remaining > 0; i++) {
            int available = candidates.size() - i;
            if (remaining == available || random.nextInt(available) < remaining) {
                selected.add(candidates.get(i).uid);
                remaining--;
            }
        }
    }

    public boolean press(int mouseX, int mouseY, int maximum, long now) {
        release();
        anchor = grid.hitIndex(mouseX, mouseY, selected::contains);
        if (anchor < 0) return false;
        cards = grid.cards();
        initial.addAll(selected);
        selecting = !selected.contains(cards.get(anchor).uid);
        limit = Math.max(0, maximum);
        pressedAt = now;
        startX = mouseX;
        startY = mouseY;
        startPage = grid.page();
        end = anchor;
        applyRange();
        return true;
    }

    public boolean drag(int mouseX, int mouseY, long now) {
        if (anchor < 0) return false;
        if (cards != grid.cards()) {
            release();
            return false;
        }
        if (now - pressedAt < HOLD_MS) return false;
        if (!dragging && grid.page() == startPage && Math.abs(mouseX - startX) < 3 && Math.abs(mouseY - startY) < 3)
            return false;
        int index = grid.nearestIndex(mouseX, mouseY);
        if (index < 0 || index == end) return false;
        dragging = true;
        end = index;
        applyRange();
        return true;
    }

    private void applyRange() {
        selected.clear();
        if (limit == 1) {
            if (selecting || dragging) selected.add(cards.get(end).uid);
            return;
        }
        selected.addAll(initial);
        int step = end < anchor ? -1 : 1;
        for (int index = anchor;; index += step) {
            int uid = cards.get(index).uid;
            if (selecting) {
                if (selected.size() < limit) selected.add(uid);
            } else selected.remove(uid);
            if (index == end) break;
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    public void release() {
        anchor = end = -1;
        dragging = false;
        initial.clear();
        cards = List.of();
    }
}
