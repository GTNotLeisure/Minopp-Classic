package com.hfstudio.minopp.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class ActionMessageHistory {

    public static final int CAPACITY = 200;

    private final Deque<Entry> entries = new ArrayDeque<>(CAPACITY);
    private long revision;
    private List<Entry> snapshot;

    public record Entry(long sequence, ActionMessage message) {}

    public void add(ActionMessage message) {
        if (entries.size() == CAPACITY) entries.removeFirst();
        entries.addLast(new Entry(++revision, message));
        snapshot = null;
    }

    public void clear() {
        entries.clear();
        revision++;
        snapshot = null;
    }

    public long revision() {
        return revision;
    }

    public List<Entry> entries() {
        if (snapshot == null) snapshot = List.copyOf(entries);
        return snapshot;
    }
}
