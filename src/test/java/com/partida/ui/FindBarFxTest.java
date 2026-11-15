package com.partida.ui;

import java.nio.file.Path;
import java.util.logging.Logger;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.press;
import static com.partida.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The find bars: the Debug Log's, a system tab's and the RetroArch console's (FIND-01/02/04/05/06/07/09). */
@Tag("fx")
class FindBarFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        FakeLibrary.load(app.services(), dir.resolve("library"));
        Logger log = Logger.getLogger("findbar.test");
        log.warning("alpha one");
        log.warning("beta");
        log.warning("Alpha two");
    }

    @AfterEach
    void stop() throws Exception {
        Debounce.immediate = true;
        FxTestSupport.stop(app);
    }

    @Test
    void theBarIsInTheWindowSearchesAsYouTypeAndSteps() throws Exception {
        run(() -> app.services().commands().run("debugLog.find"));
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        FindBar bar = window.findBar();
        assertTrue(call(bar::isOpen));
        // FIND-01: a node in the Debug Log's own scene, not a popup or a stage of its own.
        assertTrue(call(() -> bar.getScene() == window.stage().getScene()));

        // FIND-02: searching as you type (debounced) updates the count.
        run(() -> bar.field().setText("alpha"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        String text = call(() -> window.text().getText());
        int first = text.indexOf("alpha one");
        int second = text.indexOf("Alpha two");
        assertTrue(call(bar::countText).matches("\\d+ of \\d+"), call(bar::countText));
        int total = Integer.parseInt(call(bar::countText).replaceAll(".* of ", ""));
        assertTrue(total >= 2);

        // FIND-06: Enter forward, Shift+Enter back, wrapping at the ends.
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> press(bar.field(), KeyCode.ENTER, false, false, false, false));
        assertTrue(call(() -> window.text().getSelection().getStart()) > first);
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> press(bar.field(), KeyCode.ENTER, true, false, false, false));
        int back = call(() -> window.text().getSelection().getStart());
        assertTrue(back < first || back >= second, "Shift+Enter went back, wrapping: " + back);

        // The steps are commands too (KBD-01): the palette reaches them while the bar is open.
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> app.services().commands().run("debugLog.findNext"));
        assertTrue(call(() -> window.text().getSelection().getStart()) > first);
        assertEquals(
                null, app.services().commands().get("debugLog.findPrevious").unavailableReason());

        run(() -> bar.matchCase().setSelected(true));
        run(() -> bar.field().setText("zzz-not-there"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertEquals("No matches", call(bar::countText));

        // FIND-07: Escape closes it, drops a pending search, clears the match, and the log has focus.
        Debounce.immediate = false;
        run(() -> bar.field().setText("beta"));
        run(() -> press(bar.field(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(bar::isOpen));
        assertEquals(0, call(() -> window.text().getSelection().getLength()));
        // Nothing is left to run: the typing pause and the search were both dropped with the bar (QUAL-07).
        assertFalse(call(bar::searching), "a search is still pending after the bar closed");
        assertEquals("No matches", call(bar::countText), "a pending search ran after the bar closed");
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == window.text()));
        assertNotNull(app.services().commands().get("debugLog.findNext").unavailableReason(), "closed bar");
    }

    @Test
    void aNewQueryIsSearchedOffTheFxThreadAndAGrowingOneIsNarrowedInPlace() throws Exception {
        run(() -> app.services().commands().run("debugLog.find"));
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        FindBar bar = window.findBar();

        // PERF-01: a query the bar has no matches for goes to the background; this FX turn sees it pending.
        assertTrue(call(() -> {
            bar.field().setText("al");
            return bar.searching();
        }));
        FxTestSupport.waitUntil(() -> !bar.searching());
        String two = call(bar::countText);
        assertTrue(two.matches("\\d+ of \\d+"), two);

        // PERF-08: a letter more is answered from those matches in the same turn, without a search.
        String narrowed = call(() -> {
            bar.field().setText("alpha t");
            return bar.searching() ? "searching" : bar.countText();
        });
        assertTrue(narrowed.matches("1 of \\d+"), narrowed);
        int growing = Integer.parseInt(narrowed.replaceAll(".* of ", ""));
        assertTrue(growing <= Integer.parseInt(two.replaceAll(".* of ", "")));
        assertTrue(call(() -> window.text().getSelectedText()).equalsIgnoreCase("alpha t"));

        // A step asked for while a search runs waits for it, then steps on its matches.
        call(() -> {
            bar.field().setText("beta");
            bar.step(true);
            return null;
        });
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertTrue(call(() -> window.text().getSelectedText()).equalsIgnoreCase("beta"));
        int betas = Integer.parseInt(call(bar::countText).replaceAll(".* of ", ""));

        // PERF-03: a Refresh brings a new text; the open bar searches it, never the old one's matches.
        Logger.getLogger("findbar.test").warning("beta again");
        run(window::refresh);
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertTrue(call(bar::countText).endsWith(" of " + (betas + 1)), call(bar::countText));
    }

    /**
     * FIND-01, FIND-02, FIND-04, FIND-06, FIND-07: a system's tab has a find bar of its own in the window, over
     * its games' titles, opened by find.show, which steps to the next game when run again; the keymaps bind it.
     */
    @Test
    void aSystemHasAFindBarThatFindShowOpensAndSteps() throws Exception {
        MainWindow window = app.window();
        SystemView view = openSystem(1);
        FindBar bar = view.findBar();
        assertFalse(call(bar::isOpen));
        assertTrue(call(() -> app.services().commands().run("find.show")));
        assertTrue(call(bar::isOpen));
        assertTrue(call(() -> bar.getScene() == window.stage().getScene()), "in the window, not a popup");
        run(() -> bar.field().setText("Game"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertEquals("1 of " + FakeLibrary.games(1), call(bar::countText));
        assertEquals(0, (int) call(view::selectedIndex));
        // find.show again, with the bar open, selects the next game that matches.
        run(() -> app.services().commands().run("find.show"));
        assertEquals(1, (int) call(view::selectedIndex));
        assertEquals("2 of " + FakeLibrary.games(1), call(bar::countText));
        run(() -> press(bar.field(), KeyCode.ENTER, true, false, false, false));
        assertEquals(0, (int) call(view::selectedIndex), "Shift+Enter went back");
        run(() -> press(bar.field(), KeyCode.ENTER, true, false, false, false));
        assertEquals(FakeLibrary.games(1) - 1, (int) call(view::selectedIndex), "and wraps to the last");
        run(() -> bar.field().setText("no such game"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertEquals(call(() -> com.partida.i18n.Messages.tr("find.none")), call(bar::countText));
        run(() -> press(bar.field(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(bar::isOpen));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == view.list()), "focus is back");
        assertNotNull(app.services().commands().get("find.next").unavailableReason(), "closed bar");

        // On the Welcome tab there is nothing to search: find.show says why.
        run(() -> window.tabs().tabPane().getSelectionModel().select(0));
        assertNotNull(app.services().commands().get("find.show").unavailableReason());
    }

    /** Opens the fake library's system {@code index} in a tab and returns its view. */
    private SystemView openSystem(int index) throws Exception {
        var system = FakeLibrary.collection(app.services(), index);
        run(() -> app.window().tabs().open(system));
        SystemView view = call(() -> app.window().tabs().activeView());
        assertNotNull(view);
        return view;
    }

    /** FIND-01, FIND-04: the RetroArch console has a find bar of its own, opened by retroarch.find. */
    @Test
    void theConsoleHasAFindBarOfItsOwn() throws Exception {
        MainWindow window = app.window();
        assertTrue(call(() -> app.services().commands().run("retroarch.find")));
        LibraryPanels.Console console = call(window::console);
        assertNotNull(console);
        run(() -> console.append(java.util.List.of("first line", "second line")));
        FindBar bar = console.findBar();
        assertTrue(call(bar::isOpen));
        run(() -> bar.field().setText("line"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertTrue(call(bar::countText).endsWith("of 2"), call(bar::countText));
        run(() -> app.services().commands().run("retroarch.findNext"));
        assertTrue(call(bar::countText).startsWith("2 of"), call(bar::countText));
        run(() -> app.services().commands().run("retroarch.findPrevious"));
        assertTrue(call(bar::countText).startsWith("1 of"), call(bar::countText));
        run(() -> press(bar.field(), KeyCode.ESCAPE, false, false, false, false));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == console.console()));
    }

    /** FIND-04: every bundled keymap binds find.show to its chord; CUA's F3 (Cmd+G on macOS) runs it too. */
    @Test
    void everyKeymapBindsFindShow() throws Exception {
        for (boolean mac : new boolean[] {false, true}) {
            var bindings = new com.partida.command.Keybindings(
                    com.partida.command.Keymap.load("cua", mac), java.util.Map.of(), mac);
            var also = com.partida.command.KeyStroke.parseSequence(mac ? "Cmd+G" : "F3").stream()
                    .map(s -> s.resolve(mac))
                    .toList();
            assertEquals("find.show", bindings.commandFor(also), mac ? "Cmd+G" : "F3");
        }
        assertEquals(
                "Shortcut+F",
                com.partida.command.Keymap.load("cua", false).bindings().get("find.show"));
        assertEquals(
                "Cmd+F", com.partida.command.Keymap.load("cua", true).bindings().get("find.show"));
        assertEquals(
                "C-s",
                com.partida.command.Keymap.load("emacs", false).bindings().get("find.show"));
    }

    /**
     * FIND-05: a search starts from the game that was selected when the bar opened. FIND-09: in the Emacs keymap
     * the field's C-a, C-e and C-k act on the field, never on a command bound to the same keys.
     */
    @Test
    void theSearchStartsAtTheSelectionAndTheFieldKeepsItsEditingKeys() throws Exception {
        SystemView view = openSystem(1);
        run(() -> view.list().getSelectionModel().select(1));
        run(() -> app.services().commands().run("find.show"));
        FindBar bar = view.findBar();
        run(() -> bar.field().setText("Game"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertEquals(1, (int) call(view::selectedIndex), "from the anchor, not the top");
        run(() -> bar.field().setText("Ga"));
        FxTestSupport.waitUntil(() -> !bar.searching());
        assertEquals(1, (int) call(view::selectedIndex), "refining stays put");

        run(() -> app.services().store().update(s -> {
            s.keymap = "emacs";
            s.keybindings.put("view.toggleStatusBar", "C-a"); // a command on a field's editing key
        }));
        run(() -> bar.field().setText("one two"));
        run(() -> bar.field().positionCaret(4));
        run(() -> press(bar.field(), KeyCode.A, false, true, false, false));
        assertEquals(0, (int) call(() -> bar.field().getCaretPosition()), "C-a went to the start of the field");
        assertTrue(app.services().settings().showStatusBar, "C-a ran no command");
        run(() -> press(bar.field(), KeyCode.E, false, true, false, false));
        assertEquals(7, (int) call(() -> bar.field().getCaretPosition()), "C-e went to the end");
        run(() -> bar.field().positionCaret(3));
        run(() -> press(bar.field(), KeyCode.K, false, true, false, false));
        assertEquals("one", call(() -> bar.field().getText()), "C-k killed the rest");
        run(() -> app.services().store().update(s -> {
            s.keymap = "cua";
            s.keybindings.remove("view.toggleStatusBar");
        }));
    }
}
