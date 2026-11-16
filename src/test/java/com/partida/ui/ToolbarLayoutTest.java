package com.partida.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TBAR-09: the toolbar's layout rules, without a toolkit (QUAL-05). */
class ToolbarLayoutTest {

    private static final List<@Nullable String> DEFAULTS = layout("a", null, "b", "c");
    private static final List<String> AVAILABLE = List.of("a", "b", "c", "d");

    private static List<@Nullable String> layout(@Nullable String... ids) {
        return new ArrayList<>(Arrays.asList(ids));
    }

    @Test
    void emptyOrNothingKnownIsTheDefault() {
        assertEquals(DEFAULTS, ToolbarLayout.read(List.of(), DEFAULTS, AVAILABLE));
        assertEquals(DEFAULTS, ToolbarLayout.read(null, DEFAULTS, AVAILABLE));
        assertEquals(DEFAULTS, ToolbarLayout.read(List.of("gone", "|", "|"), DEFAULTS, AVAILABLE));
    }

    @Test
    void readingDropsTheUnknownTheRepeatedAndStrayGaps() {
        assertEquals(
                layout("d", null, "a", "c"),
                ToolbarLayout.read(List.of("|", "d", "gone", "|", "|", "a", "d", "c", "|"), DEFAULTS, AVAILABLE));
    }

    @Test
    void theDefaultIsSavedAsNothingAndAnythingElseAsItIs() {
        assertEquals(List.of(), ToolbarLayout.write(layout("a", null, "b", "c", null), DEFAULTS));
        assertEquals(List.of("c", "|", "a", "b"), ToolbarLayout.write(layout("c", null, "a", "b"), DEFAULTS));
        List<@Nullable String> round = layout("b", null, "d");
        assertEquals(round, ToolbarLayout.read(ToolbarLayout.write(round, DEFAULTS), DEFAULTS, AVAILABLE));
    }

    @Test
    void anEntryMovesWithinTheList() {
        assertEquals(layout(null, "a", "b", "c"), ToolbarLayout.moved(DEFAULTS, 0, 1));
        assertEquals(layout("a", null, "c", "b"), ToolbarLayout.moved(DEFAULTS, 3, -1));
        assertEquals(DEFAULTS, ToolbarLayout.moved(DEFAULTS, 0, -1), "the first stays first");
        assertEquals(DEFAULTS, ToolbarLayout.moved(DEFAULTS, 3, 5), "the last stays last");
        assertEquals(DEFAULTS, ToolbarLayout.moved(DEFAULTS, -1, 1), "nothing selected");
    }

    @Test
    void anEntryGoesButNeverTheLastButton() {
        assertEquals(layout("a", "b", "c"), ToolbarLayout.removed(DEFAULTS, 1));
        assertEquals(layout("a", null, "c"), ToolbarLayout.removed(DEFAULTS, 2));
        assertFalse(ToolbarLayout.canRemove(layout("a", null), 0), "the last button");
        assertTrue(ToolbarLayout.canRemove(layout("a", null), 1), "a gap always can");
        assertFalse(ToolbarLayout.canRemove(DEFAULTS, 9));
        assertEquals(layout("a"), ToolbarLayout.removed(layout("a"), 0));
    }

    @Test
    void anEntryIsAddedAtTheEndOnce() {
        assertEquals(List.of("d"), ToolbarLayout.addable(DEFAULTS, AVAILABLE));
        assertEquals(layout("a", null, "b", "c", "d"), ToolbarLayout.added(DEFAULTS, "d"));
        assertEquals(DEFAULTS, ToolbarLayout.added(DEFAULTS, "a"), "a button the bar has");
        assertEquals(layout("a", null, "b", "c", null), ToolbarLayout.added(DEFAULTS, null));
        assertEquals(layout("a", null, "b"), ToolbarLayout.tidy(layout(null, "a", null, null, "b", null)));
    }
}
