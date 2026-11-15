package com.partida.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The systems whose tabs the user closed in one window, newest first, for Reopen Closed Tab (TABS-06). Bounded
 * to twenty; closing a system again moves it to the top rather than listing it twice; each entry keeps which
 * game was selected, as a file keeps its caret. Kept in memory only: a window that closes forgets them. Pure,
 * as in Editora.
 */
final class ClosedTabs {

    /** A closed system's tab and the row that was selected in it. */
    record Entry(String name, int selected) {}

    static final int DEFAULT_CAPACITY = 20;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    ClosedTabs() {
        this(DEFAULT_CAPACITY);
    }

    ClosedTabs(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records that {@code name}'s tab was closed with row {@code selected} selected; a tab with none is not. */
    void closed(@Nullable String name, int selected) {
        if (name == null) return;
        forget(name);
        entries.addFirst(new Entry(name, Math.max(0, selected)));
        while (entries.size() > capacity) entries.removeLast();
    }

    /** The most recently closed system, removed from the list; null when there is none. */
    @Nullable
    Entry pop() {
        return entries.pollFirst();
    }

    /** Drops {@code name} from the list: it was opened again some other way. */
    void forget(String name) {
        entries.removeIf(e -> e.name().equals(name));
    }

    int size() {
        return entries.size();
    }

    /** The entries, newest first. */
    List<Entry> entries() {
        return List.copyOf(entries);
    }
}
