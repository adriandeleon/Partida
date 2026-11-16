package com.partida.ui;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.partida.i18n.Messages;
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

    /** QUAL-05: whether Play, Stop and the Game window can act is decided by pure rules, in this order. */
    @Test
    void playStopAndTheGameWindowFollowPureRules() {
        assertEquals(Messages.tr("palette.disabled.noRetroArch"), Chrome.playUnavailable(false, true, false));
        assertEquals(Messages.tr("palette.disabled.noGame"), Chrome.playUnavailable(true, false, false));
        assertEquals(Messages.tr("palette.disabled.gameRunning"), Chrome.playUnavailable(true, true, true));
        assertNull(Chrome.playUnavailable(true, true, false));
        assertNull(Chrome.stopUnavailable(true));
        assertEquals(Messages.tr("palette.disabled.noGameRunning"), Chrome.stopUnavailable(false));
        assertTrue(Chrome.gameWindowAvailable(true));
        assertFalse(Chrome.gameWindowAvailable(false));
        assertNull(Chrome.filterStepUnavailable(true));
        assertEquals(Messages.tr("palette.disabled.noFilter"), Chrome.filterStepUnavailable(false));
    }

    /** INTL-07: the play history shows by its name in the user's language; a system by its own. */
    @Test
    void thePlayHistoryIsNamedInTheUsersLanguage() {
        assertEquals(
                Messages.tr("systems.recentlyPlayed"),
                Chrome.systemName(com.partida.library.LibraryLoader.RECENTLY_PLAYED));
        assertEquals("Nintendo - SNES", Chrome.systemName("Nintendo - SNES"));
    }

    /**
     * FIND-03, FIND-13: the filter keeps a game whose title has every word typed, in any case and order, and that
     * meets every {@code key:value} term; a {@code >} query filters nothing and names a command.
     */
    @Test
    void theFilterReadsWordsTermsAndCommands() {
        com.partida.library.Game mario = new com.partida.library.Game(
                "Super Mario World",
                java.nio.file.Path.of("/roms/snes/smw.zip#Super Mario World.sfc"),
                "Nintendo - SNES",
                java.nio.file.Path.of("/cores/snes9x_libretro.so"));
        com.partida.library.Game zelda = new com.partida.library.Game(
                "Zelda", java.nio.file.Path.of("/roms/gb/zelda.gb"), "Nintendo - Game Boy", null);

        GameFilter.Query plain = GameFilter.parse(" Mario  WORLD ", List.of());
        assertEquals(List.of("mario", "world"), plain.words());
        assertTrue(GameFilter.matches(mario, GameFilter.parse("world mario", List.of())));
        assertTrue(GameFilter.parse("   ", List.of()).isEmpty());
        assertFalse(GameFilter.matches(mario, GameFilter.parse("mario kart", List.of())));

        assertEquals(
                new GameFilter.Term("ext", "sfc"), GameFilter.term("EXT:SFC").orElseThrow());
        assertTrue(GameFilter.term("colour:red").isEmpty(), "an unknown key is a plain word");
        assertTrue(GameFilter.term("ext:").isEmpty());
        assertTrue(GameFilter.term(":zip").isEmpty());
        assertEquals("ext:sfc", new GameFilter.Term("ext", "sfc").text());
        assertTrue(GameFilter.matches(mario, GameFilter.parse("ext:sfc", List.of())), "the file inside the archive");
        assertFalse(GameFilter.matches(zelda, GameFilter.parse("ext:sfc", List.of())));
        assertTrue(GameFilter.matches(mario, GameFilter.parse("", List.of(new GameFilter.Term("core", "snes9x")))));
        assertFalse(GameFilter.matches(zelda, GameFilter.parse("core:snes9x", List.of())), "no core: no match");
        assertTrue(GameFilter.matches(zelda, GameFilter.parse("system:boy zel", List.of())));

        assertEquals("play", GameFilter.command("  > play "));
        assertNull(GameFilter.command("play"));
        assertTrue(GameFilter.parse(">play", List.of()).isEmpty(), "a command filters nothing");
        java.util.Map<String, String> commands = new java.util.LinkedHashMap<>();
        commands.put("game.stop", "Stop Game");
        commands.put("game.play", "Play Game");
        commands.put("find.show", "Find…");
        commands.put("game.playAgain", "Play");
        assertEquals("game.playAgain", GameFilter.commandFor("play", commands).orElseThrow(), "the exact title first");
        assertEquals("game.play", GameFilter.commandFor("PLAY G", commands).orElseThrow(), "then a start");
        assertEquals("game.stop", GameFilter.commandFor("stop", commands).orElseThrow());
        assertEquals("find.show", GameFilter.commandFor("find", commands).orElseThrow(), "the … left out");
        assertEquals("game.stop", GameFilter.commandFor("game", commands).orElseThrow(), "then one that holds it");
        assertTrue(GameFilter.commandFor("  ", commands).isEmpty());
        assertTrue(GameFilter.commandFor("quit", commands).isEmpty());
    }

    /** FIND-07, FIND-13, FIND-14, QUAL-05: where each key in the filter box leads, decided without a toolkit. */
    @Test
    void theFilterBoxsKeysLeadWhereTheRulesSay() {
        assertEquals(GameFilter.KeyAction.TO_LIST, GameFilter.keyAction("ESCAPE", "", 0, false, false));
        assertEquals(GameFilter.KeyAction.CLEAR, GameFilter.keyAction("ESCAPE", "mario", 5, false, false));
        assertEquals(GameFilter.KeyAction.CLEAR, GameFilter.keyAction("ESCAPE", "", 0, false, true), "chips too");
        assertEquals(GameFilter.KeyAction.CHIP, GameFilter.keyAction("SPACE", "mario ext:zip", 13, false, false));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("SPACE", "mario ext:zip", 3, false, false));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("SPACE", "mario", 5, false, false));
        assertEquals(GameFilter.KeyAction.UNCHIP, GameFilter.keyAction("BACK_SPACE", "x", 0, false, true));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("BACK_SPACE", "x", 0, true, true));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("BACK_SPACE", "x", 1, false, true));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("BACK_SPACE", "", 0, false, false));
        assertEquals(GameFilter.KeyAction.RUN, GameFilter.keyAction("ENTER", ">play", 5, false, false));
        assertEquals(GameFilter.KeyAction.CHIP, GameFilter.keyAction("ENTER", "core:snes", 9, false, false));
        assertEquals(GameFilter.KeyAction.TO_LIST, GameFilter.keyAction("ENTER", "mario", 5, false, false));
        assertEquals(GameFilter.KeyAction.TO_LIST, GameFilter.keyAction("DOWN", "mario", 2, false, false));
        assertEquals(GameFilter.KeyAction.NONE, GameFilter.keyAction("A", "mario", 2, false, false));
        assertTrue(GameFilter.chipAt("> ext:zip", 9).isEmpty(), "never inside a command");
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
