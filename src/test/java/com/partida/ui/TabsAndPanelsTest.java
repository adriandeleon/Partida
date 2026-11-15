package com.partida.ui;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure decisions behind the tabs and the tool windows (Pauta's Tabs and tool panels), no toolkit. */
class TabsAndPanelsTest {

    private static final String A = "Nintendo - Game Boy";
    private static final String B = "Sega - Game Gear";

    /** TABS-06: newest first, bounded, one entry per system, the selected game kept. */
    @Test
    void closedTabsKeepOrderBoundOneEntryPerSystemAndTheSelection() {
        ClosedTabs closed = new ClosedTabs();
        assertEquals(20, ClosedTabs.DEFAULT_CAPACITY);
        closed.closed(A, 5);
        closed.closed(B, 7);
        closed.closed(A, 9); // closed again: to the top, listed once, with its new selection
        closed.closed(null, 3); // a tab with no system is not listed
        assertEquals(List.of(new ClosedTabs.Entry(A, 9), new ClosedTabs.Entry(B, 7)), closed.entries());

        ClosedTabs bounded = new ClosedTabs();
        for (int i = 0; i < 25; i++) bounded.closed("system " + i, i);
        assertEquals(20, bounded.size());
        assertEquals("system 24", bounded.entries().getFirst().name());
        assertEquals("system 5", bounded.entries().getLast().name());

        assertEquals(new ClosedTabs.Entry(A, 9), closed.pop());
        closed.forget(B); // opened again some other way
        assertNull(closed.pop());
        closed.closed(A, -4);
        assertEquals(0, java.util.Objects.requireNonNull(closed.pop()).selected());
    }

    /** TABS-04: no bulk close takes a pinned tab. */
    @Test
    void bulkCloseSkipsPinnedTabs() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c", "d");
        Predicate<String> pinned = Set.of("p1", "p2")::contains;
        Predicate<String> dirty = Set.of("b")::contains;
        assertEquals(List.of("a", "c", "d"), TabStrip.closeOtherTabs(tabs, "b", pinned));
        assertEquals(List.of("a", "b", "c", "d"), TabStrip.closeAllTabs(tabs, pinned));
        assertEquals(List.of("a", "c", "d"), TabStrip.closeUnmodifiedTabs(tabs, pinned, dirty));
        assertEquals(List.of("a", "b"), TabStrip.eligibleToLeft(tabs, "c", pinned));
        assertEquals(List.of("d"), TabStrip.eligibleToRight(tabs, "c", pinned));
        assertEquals(List.of(), TabStrip.eligibleToLeft(tabs, "a", pinned));
        assertEquals(List.of(), TabStrip.eligibleToRight(tabs, "missing", pinned));
    }

    /** TABS-05: pinning goes to the end of the pinned group, unpinning to just after it. */
    @Test
    void pinningMovesToThePinBoundary() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c");
        // "c" just pinned: after p1 and p2.
        assertEquals(2, TabStrip.moveToPinBoundary(tabs, "c", Set.of("p1", "p2", "c")::contains));
        // "p1" just unpinned: just after the one pinned tab left.
        assertEquals(1, TabStrip.moveToPinBoundary(tabs, "p1", Set.of("p2")::contains));
    }

    /** TABS-07: a dragged tab lands beside its target, held to its own group. */
    @Test
    void aDraggedTabStaysInItsGroup() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c");
        Predicate<String> pinned = Set.of("p1", "p2")::contains;
        assertEquals(4, TabStrip.dropIndex(tabs, "a", "c", true, pinned)); // after c, "a" taken out
        assertEquals(2, TabStrip.dropIndex(tabs, "c", "p1", false, pinned)); // not into the pinned group
        assertEquals(1, TabStrip.dropIndex(tabs, "p1", "b", true, pinned)); // a pinned one stays pinned
        assertEquals(-1, TabStrip.dropIndex(tabs, "a", "a", true, pinned));
        assertEquals(0, TabStrip.cycle(4, 5, 1));
        assertEquals(4, TabStrip.cycle(0, 5, -1));
        assertEquals(-1, TabStrip.cycle(0, 0, 1));
    }

    /** TABS-10: an app with no files names what the window shows, then the app. */
    @Test
    void theWindowTitleNamesTheSystemAndTheApp() {
        assertEquals("Nintendo - Game Boy — Partida", DocumentTabs.windowTitle("Nintendo - Game Boy"));
        assertEquals("Partida", DocumentTabs.windowTitle(null));
    }

    /** The find bar's list target: a match's offset names its row. */
    @Test
    void aMatchOffsetNamesItsRow() {
        int[] starts = {0, 6, 13};
        assertEquals(0, FindBar.ListTarget.rowAt(starts, 0));
        assertEquals(0, FindBar.ListTarget.rowAt(starts, 5));
        assertEquals(1, FindBar.ListTarget.rowAt(starts, 6));
        assertEquals(2, FindBar.ListTarget.rowAt(starts, 40));
    }

    /** The Game window's initials, where there is no box art. */
    @Test
    void initialsAreTheFirstLettersOfTwoWords() {
        assertEquals("SM", LibraryPanels.GamePanel.initialsOf("Super Mario World"));
        assertEquals("Z", LibraryPanels.GamePanel.initialsOf("Zelda"));
        assertEquals("", LibraryPanels.GamePanel.initialsOf("!!"));
    }

    /** Settings → Library's RetroArch folder: blank or a full path. */
    @Test
    void theRetroArchFolderIsBlankOrAFullPath() {
        assertNull(MainWindow.retroArchFolderProblem(""));
        assertNull(MainWindow.retroArchFolderProblem(System.getProperty("java.io.tmpdir")));
        assertNotNull(MainWindow.retroArchFolderProblem("relative/folder"));
    }

    /** TABS-20: a button shows only when the user has it visible and it is available. */
    @Test
    void aStripeButtonShowsOnlyWhenVisibleAndAvailable() {
        assertTrue(ToolWindowVisibility.buttonShown(true, false));
        assertFalse(ToolWindowVisibility.buttonShown(true, true));
        assertFalse(ToolWindowVisibility.buttonShown(false, false));
        assertFalse(ToolWindowVisibility.buttonShown(false, true));
        // TABS-17: an empty stripe shows only while a button is dragged, to be dropped on.
        assertTrue(ToolWindowVisibility.stripeShown(false, false));
        assertFalse(ToolWindowVisibility.stripeShown(true, false));
        assertTrue(ToolWindowVisibility.stripeShown(true, true));
    }

    /** TABS-17: dropping a stripe button reorders one list across the stripes. */
    @Test
    void dockOrderArithmetic() {
        List<String> order = List.of("a", "b", "c");
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropOnto(order, "a", "c", true));
        assertEquals(List.of("b", "a", "c"), ToolWindowDock.dropOnto(order, "a", "c", false));
        assertEquals(List.of("c", "a", "b"), ToolWindowDock.dropOnto(order, "c", "a", false));
        assertEquals(order, ToolWindowDock.dropOnto(order, "a", "a", true));
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropOnto(order, "a", "zz", true));
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropAtEnd(order, "a"));
    }

    /** TABS-16: three sides, at most two windows on each. */
    @Test
    void threeSidesTwoWindowsEach() {
        assertEquals(3, ToolWindow.Side.values().length);
        assertEquals(2, ToolWindowManager.MAX_PER_SIDE);
    }

    @Test
    void theToolWindowReasons() {
        assertNull(Chrome.toolWindowUnavailable(true));
        assertNotNull(Chrome.toolWindowUnavailable(false));
        assertNull(Chrome.splitUnavailable(true));
        assertNotNull(Chrome.splitUnavailable(false));
        assertNull(Chrome.restoreStripeButtonsUnavailable(true));
        assertNotNull(Chrome.restoreStripeButtonsUnavailable(false));
        assertNull(Chrome.noFoldersUnavailable(true));
        assertNotNull(Chrome.noFoldersUnavailable(false));
        assertNull(Chrome.systemsOpenUnavailable(true));
        assertNotNull(Chrome.systemsOpenUnavailable(false));
        assertNull(Chrome.consoleClearUnavailable(true));
        assertNotNull(Chrome.consoleClearUnavailable(false));
    }
}
