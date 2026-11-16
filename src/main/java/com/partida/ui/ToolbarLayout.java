package com.partida.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The main toolbar's customizable layout (TBAR-09), as pure rules (QUAL-05): no JavaFX here. A layout is
 * the icon cluster's commands in order, {@code null} a gap between groups; the pinned tail is never in it
 * (TBAR-16). Settings keeps it as strings, {@link #GAP} for a gap, and an empty list for the app's own.
 */
final class ToolbarLayout {

    /** A gap between groups, as the settings write it. */
    static final String GAP = "|";

    private ToolbarLayout() {}

    /**
     * The layout the settings hold, made safe: an unknown or repeated command is dropped (a hand-edited file
     * or a command a later version removed), and the gaps tidied. Empty, or nothing left, is the default.
     */
    static List<@Nullable String> read(
            @Nullable List<String> saved, List<@Nullable String> defaults, Collection<String> available) {
        if (saved == null || saved.isEmpty()) return new ArrayList<>(defaults);
        List<@Nullable String> layout = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : saved) {
            if (GAP.equals(id)) {
                layout.add(null);
            } else if (id != null && available.contains(id) && seen.add(id)) {
                layout.add(id);
            }
        }
        List<@Nullable String> tidied = tidy(layout);
        return tidied.isEmpty() ? new ArrayList<>(defaults) : tidied;
    }

    /** What the settings keep for {@code layout}: empty when it is the default, so the default can evolve. */
    static List<String> write(List<@Nullable String> layout, List<@Nullable String> defaults) {
        List<@Nullable String> tidied = tidy(layout);
        if (tidied.equals(tidy(defaults))) return new ArrayList<>();
        List<String> saved = new ArrayList<>();
        for (String id : tidied) saved.add(id == null ? GAP : id);
        return saved;
    }

    /** No gap leads, trails or follows another: a gap only ever stands between two groups (TBAR-05). */
    static List<@Nullable String> tidy(List<@Nullable String> layout) {
        List<@Nullable String> tidied = new ArrayList<>();
        for (String id : layout) {
            if (id == null && (tidied.isEmpty() || tidied.getLast() == null)) continue;
            tidied.add(id);
        }
        if (!tidied.isEmpty() && tidied.getLast() == null) tidied.removeLast();
        return tidied;
    }

    /** {@code layout} with the entry at {@code index} moved {@code delta} places, kept inside the list. */
    static List<@Nullable String> moved(List<@Nullable String> layout, int index, int delta) {
        List<@Nullable String> moved = new ArrayList<>(layout);
        if (index < 0 || index >= moved.size()) return moved;
        int to = Math.clamp((long) index + delta, 0, moved.size() - 1);
        moved.add(to, moved.remove(index));
        return moved;
    }

    /** {@code layout} without the entry at {@code index}; the last button always stays, so the bar is never bare. */
    static List<@Nullable String> removed(List<@Nullable String> layout, int index) {
        List<@Nullable String> removed = new ArrayList<>(layout);
        if (!canRemove(layout, index)) return removed;
        removed.remove(index);
        return removed;
    }

    /** Whether the entry at {@code index} can go: a gap can, a button unless it is the last one. */
    static boolean canRemove(List<@Nullable String> layout, int index) {
        if (index < 0 || index >= layout.size()) return false;
        return layout.get(index) == null || buttons(layout) > 1;
    }

    /** {@code layout} with {@code id} (a command, or {@code null} for a gap) added at its end. */
    static List<@Nullable String> added(List<@Nullable String> layout, @Nullable String id) {
        List<@Nullable String> added = new ArrayList<>(layout);
        if (id == null || !added.contains(id)) added.add(id);
        return added;
    }

    /** The commands that can still go on the bar: the available ones it does not hold, in their order. */
    static List<String> addable(List<@Nullable String> layout, Collection<String> available) {
        List<String> addable = new ArrayList<>();
        for (String id : available) {
            if (!layout.contains(id)) addable.add(id);
        }
        return addable;
    }

    private static int buttons(List<@Nullable String> layout) {
        int buttons = 0;
        for (String id : layout) {
            if (id != null) buttons++;
        }
        return buttons;
    }
}
