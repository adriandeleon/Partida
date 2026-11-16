package com.partida.ui;

import java.util.List;

import com.partida.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure decisions behind the browser and the tool windows (Pauta's Tabs and tool panels), no toolkit. */
class TabsAndPanelsTest {

    /** TABS-10: an app with no files names what the window shows, then the app. */
    @Test
    void theWindowTitleNamesTheSystemAndTheApp() {
        assertEquals("Nintendo - Game Boy — Partida", Browser.windowTitle("Nintendo - Game Boy"));
        assertEquals("Partida", Browser.windowTitle(null));
    }

    /** QUAL-05: whether Play and Stop can act is decided by pure rules, in this order. */
    @Test
    void playStopAndTheGameWindowFollowPureRules() {
        assertEquals(Messages.tr("palette.disabled.noRetroArch"), Chrome.playUnavailable(false, true, false));
        assertEquals(Messages.tr("palette.disabled.noGame"), Chrome.playUnavailable(true, false, false));
        assertEquals(Messages.tr("palette.disabled.gameRunning"), Chrome.playUnavailable(true, true, true));
        assertNull(Chrome.playUnavailable(true, true, false));
        assertNull(Chrome.stopUnavailable(true));
        assertEquals(Messages.tr("palette.disabled.noGameRunning"), Chrome.stopUnavailable(false));
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
