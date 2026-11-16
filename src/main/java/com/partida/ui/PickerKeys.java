package com.partida.ui;

import java.util.List;

import com.partida.command.KeyStroke;
import com.partida.command.Keybindings;
import org.jspecify.annotations.Nullable;

/**
 * The keys of every list overlay, the palette, the pickers and the tab switcher alike (PAL-06, QOPEN-06,
 * QOPEN-12), after Editora's {@code PickerKeys}: the active keymap's line and page chords (the {@code nav.*}
 * commands, {@code C-n}, {@code C-p}, {@code C-v} and {@code M-v} in Emacs) move the selection, as do the keys
 * that always work: ↑ ↓, Page Up and Down, and Ctrl+Home and Ctrl+End (Cmd on macOS). Plain Home and End stay
 * with the field's caret. Pure: no JavaFX here (QUAL-05); the overlays read the event and pass its key code's
 * name, whether the shortcut modifier is down, and the command the keymap binds the chord to.
 */
final class PickerKeys {

    /** What a press does in a list overlay. */
    enum Move {
        LINE_DOWN,
        LINE_UP,
        PAGE_DOWN,
        PAGE_UP,
        FIRST,
        LAST,
        NONE
    }

    /** The commands whose chords move a list overlay's selection, in the keymaps that bind them. */
    static final String LINE_DOWN = "nav.lineDown";

    static final String LINE_UP = "nav.lineUp";
    static final String PAGE_DOWN = "nav.pageDown";
    static final String PAGE_UP = "nav.pageUp";

    /** The help chords a palette tries, in order: the first the keymap leaves free is its own (PAL-08). */
    static final List<String> HELP_CHORDS = List.of("Ctrl+H", "F1", "Shift+F1");

    private PickerKeys() {}

    /**
     * What a press does in a list overlay: {@code code} is the key code's name, {@code shortcut} whether Ctrl
     * (Cmd on macOS) is down, {@code command} the command the keymap binds the chord to, or null.
     */
    static Move move(String code, boolean shortcut, @Nullable String command) {
        switch (code) {
            case "DOWN" -> {
                return Move.LINE_DOWN;
            }
            case "UP" -> {
                return Move.LINE_UP;
            }
            case "PAGE_DOWN" -> {
                return Move.PAGE_DOWN;
            }
            case "PAGE_UP" -> {
                return Move.PAGE_UP;
            }
            case "HOME" -> {
                if (shortcut) return Move.FIRST;
            }
            case "END" -> {
                if (shortcut) return Move.LAST;
            }
            default -> {
                // the keymap's chords, below
            }
        }
        if (command == null) return Move.NONE;
        return switch (command) {
            case LINE_DOWN -> Move.LINE_DOWN;
            case LINE_UP -> Move.LINE_UP;
            case PAGE_DOWN -> Move.PAGE_DOWN;
            case PAGE_UP -> Move.PAGE_UP;
            default -> Move.NONE;
        };
    }

    /** The first row from {@code start} that {@code enabled} accepts, stepping by {@code step}, or -1. Pure. */
    static int enabledFrom(int size, java.util.function.IntPredicate enabled, int start, int step) {
        for (int i = start; i >= 0 && i < size; i += step) {
            if (enabled.test(i)) return i;
        }
        return -1;
    }

    /**
     * Where a move of {@code delta} rows from {@code selected} lands in a list of {@code size}: clamped to the
     * list, then on the next enabled row that way, else the nearest one back; -1 when no row is enabled (QOPEN-08).
     */
    static int moveTarget(int size, int selected, int delta, java.util.function.IntPredicate enabled) {
        if (size == 0) return -1;
        int step = delta > 0 ? 1 : -1;
        int target = Math.clamp((long) selected + delta, 0, size - 1);
        int found = enabledFrom(size, enabled, target, step);
        return found >= 0 ? found : enabledFrom(size, enabled, target, -step);
    }

    /** A page down or up of {@code page} rows from {@code at}, held to the list. Pure. */
    static int page(int at, int size, int page, boolean down) {
        return down ? Math.min(size - 1, at + page) : Math.max(0, at - page);
    }

    /** What a key does in the tab switcher (TABS-03): moves, open, close, or nothing of its own. */
    enum SwitcherKey {
        NEXT,
        PREVIOUS,
        PAGE_DOWN,
        PAGE_UP,
        FIRST,
        LAST,
        OPEN,
        CLOSE,
        NONE
    }

    /**
     * The switcher's keys: the overlay's moves, Tab and Shift+Tab to step, Enter to open, Backspace, Delete or
     * Ctrl+D to close the selected tab. Pure.
     */
    static SwitcherKey switcher(Move move, String code, boolean shift, boolean ctrl) {
        if (move == Move.LINE_DOWN || ("TAB".equals(code) && !shift)) return SwitcherKey.NEXT;
        if (move == Move.LINE_UP || "TAB".equals(code)) return SwitcherKey.PREVIOUS;
        if (move == Move.PAGE_DOWN) return SwitcherKey.PAGE_DOWN;
        if (move == Move.PAGE_UP) return SwitcherKey.PAGE_UP;
        if (move == Move.FIRST) return SwitcherKey.FIRST;
        if (move == Move.LAST) return SwitcherKey.LAST;
        if ("ENTER".equals(code)) return SwitcherKey.OPEN;
        if ("BACK_SPACE".equals(code) || "DELETE".equals(code) || (ctrl && "D".equals(code))) return SwitcherKey.CLOSE;
        return SwitcherKey.NONE;
    }

    /** Whether a key in a filter field over a list leads into the list: Down or Enter, when it has rows. */
    static boolean intoList(String code, boolean listHasRows) {
        return listHasRows && ("DOWN".equals(code) || "ENTER".equals(code));
    }

    /** What a key in a find bar's field does (FIND-02, FIND-07). */
    enum FindKey {
        NEXT,
        PREVIOUS,
        CLOSE,
        NONE
    }

    /** Enter finds the next match, Shift+Enter the previous; Escape closes the bar. Pure. */
    static FindKey findBar(String code, boolean shift) {
        return switch (code) {
            case "ENTER" -> shift ? FindKey.PREVIOUS : FindKey.NEXT;
            case "ESCAPE" -> FindKey.CLOSE;
            default -> FindKey.NONE;
        };
    }

    /**
     * The palette's help chord (PAL-08): the first of {@link #HELP_CHORDS} that the keymap neither binds nor
     * starts a longer chord with, or null when it takes all three.
     */
    static @Nullable KeyStroke helpChord(Keybindings bindings, boolean mac) {
        for (String text : HELP_CHORDS) {
            KeyStroke stroke =
                    java.util.Objects.requireNonNull(KeyStroke.parse(text)).resolve(mac);
            List<KeyStroke> sequence = List.of(stroke);
            if (bindings.commandFor(sequence) == null && !bindings.isPrefix(sequence)) return stroke;
        }
        return null;
    }
}
