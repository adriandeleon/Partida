package com.partida.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import javafx.scene.control.TextField;
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
     * FIND-03: a system's games are the app's main content, a list, so a filter box over it narrows it as it is
     * typed, in place of a find bar; find.show focuses the box, and while it narrows the list find.next and
     * find.previous step through the games it keeps, wrapping. FIND-07 and FIND-14: Escape empties the box,
     * and in an empty box leads back to the list. FIND-15: the box is out of the Tab order.
     */
    @Test
    void aSystemsGamesAreNarrowedByAFilterBox() throws Exception {
        MainWindow window = app.window();
        SystemView view = openSystem(1);
        TextField filter = view.filter();
        int all = FakeLibrary.games(1);
        assertFalse(call(filter::isFocusTraversable), "out of the Tab order");
        assertTrue(call(() -> filter.getScene() == window.stage().getScene()), "in the window, not a popup");
        assertNotNull(app.services().commands().get("find.next").unavailableReason(), "an empty box steps nothing");
        assertTrue(call(() -> app.services().commands().run("find.show")));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == filter));

        run(() -> filter.setText("game 2"));
        assertEquals(
                List.of(FakeLibrary.title(1, 2)),
                call(() -> view.list().getItems().stream()
                        .map(com.partida.library.Game::title)
                        .toList()));
        assertEquals(all, (int) call(view::size), "the system still has every game");
        run(() -> filter.setText("GAME"));
        assertEquals(all, (int) call(() -> view.list().getItems().size()), "any case");
        run(() -> view.list().getSelectionModel().select(0));
        assertTrue(call(() -> app.services().commands().run("find.next")));
        assertEquals(1, (int) call(() -> view.list().getSelectionModel().getSelectedIndex()));
        run(() -> app.services().commands().run("find.previous"));
        run(() -> app.services().commands().run("find.previous"));
        assertEquals(all - 1, (int) call(() -> view.list().getSelectionModel().getSelectedIndex()), "wraps");
        run(() -> filter.setText("no such game"));
        assertTrue(call(() -> view.list().getItems().isEmpty()));
        assertEquals(
                com.partida.i18n.Messages.tr("systems.noMatch"),
                call(() -> ((javafx.scene.control.Label) view.list().getPlaceholder()).getText()));

        run(() -> press(filter, KeyCode.ESCAPE, false, false, false, false));
        assertEquals("", call(filter::getText), "the first Escape empties the box");
        assertEquals(all, (int) call(() -> view.list().getItems().size()));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == filter), "and stays in it");
        run(() -> press(filter, KeyCode.ESCAPE, false, false, false, false));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == view.list()), "the next leads back");

        // Enter in the box goes to the list, on the game the box kept.
        run(() -> {
            filter.requestFocus();
            filter.setText("game 3");
        });
        run(() -> press(filter, KeyCode.ENTER, false, false, false, false));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == view.list()));
        assertEquals(
                FakeLibrary.title(1, 3),
                call(() -> java.util.Objects.requireNonNull(view.selected()).title()));
        assertEquals(2, (int) call(view::selectedIndex), "its index among all the system's games");

        // On the Welcome tab there is nothing to filter: find.show says why.
        run(() -> window.tabs().tabPane().getSelectionModel().select(0));
        assertNotNull(app.services().commands().get("find.show").unavailableReason());
    }

    /**
     * FIND-13: a {@code key:value} term becomes a chip on Space or Enter and Backspace takes it back; {@code >}
     * runs the command it names; Filter Games focuses the box on its own chord, which the box draws as a badge
     * read from the keymap; Escape empties the chips with the text.
     */
    @Test
    void theFilterBoxMakesChipsRunsCommandsAndShowsItsChord() throws Exception {
        MainWindow window = app.window();
        SystemView view = openSystem(1);
        TextField filter = view.filter();
        int all = FakeLibrary.games(1);
        assertEquals(
                call(() -> app.services().keybindings().label("library.filter")),
                call(() -> view.badge().getText()));
        assertFalse(call(() -> view.badge().getText()).isEmpty(), "Ctrl+K in CUA");
        run(() -> app.services().store().update(s -> s.keymap = "emacs"));
        assertEquals("C-c /", call(() -> view.badge().getText()), "the badge follows the keymap");
        run(() -> app.services().store().update(s -> s.keymap = "cua"));

        assertTrue(call(() -> app.services().commands().run("library.filter")));
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == filter));

        run(() -> {
            filter.setText("ext:zip");
            filter.positionCaret(7);
        });
        run(() -> press(filter, KeyCode.SPACE, false, false, false, false));
        assertEquals(List.of(new GameFilter.Term("ext", "zip")), call(view::chipTerms));
        assertEquals("", call(filter::getText));
        assertTrue(call(() -> view.list().getItems().isEmpty()), "no .zip game");
        assertTrue(call(view::filtering), "a chip alone filters");
        run(() -> press(filter, KeyCode.BACK_SPACE, false, false, false, false));
        assertEquals(List.of(), call(view::chipTerms), "Backspace at the start takes the chip back");
        assertEquals(all, (int) call(() -> view.list().getItems().size()));

        run(() -> {
            filter.setText("game ext:bin");
            filter.positionCaret(12);
        });
        run(() -> press(filter, KeyCode.ENTER, false, false, false, false));
        assertEquals(List.of(new GameFilter.Term("ext", "bin")), call(view::chipTerms));
        assertEquals("game", call(filter::getText));
        assertEquals(all, (int) call(() -> view.list().getItems().size()), "every game is a .bin");
        run(() -> {
            filter.requestFocus();
            press(filter, KeyCode.ESCAPE, false, false, false, false);
        });
        assertEquals(List.of(), call(view::chipTerms), "Escape empties the chips too");
        assertEquals("", call(filter::getText));

        boolean shown = app.services().settings().showStatusBar;
        run(() -> filter.setText("> toggle status"));
        assertEquals(all, (int) call(() -> view.list().getItems().size()), "a command filters nothing");
        run(() -> press(filter, KeyCode.ENTER, false, false, false, false));
        assertEquals(!shown, app.services().settings().showStatusBar, "> ran Toggle Status Bar");
        assertEquals("", call(filter::getText));
        run(() -> app.services().commands().run("view.toggleStatusBar"));
        run(() -> filter.setText("> no such command"));
        run(() -> press(filter, KeyCode.ENTER, false, false, false, false));
        assertEquals("> no such command", call(filter::getText), "kept, to fix");
        assertEquals(
                com.partida.i18n.Messages.tr("systems.filter.noCommand", "no such command"),
                call(() -> window.statusBar().echo().getText()));
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
     * FIND-09: in the Emacs keymap the filter box's C-a, C-e and C-k act on the box, never on a command bound to
     * the same keys.
     */
    @Test
    void theFilterBoxKeepsItsEditingKeys() throws Exception {
        SystemView view = openSystem(1);
        TextField filter = view.filter();
        run(() -> app.services().store().update(s -> {
            s.keymap = "emacs";
            s.keybindings.put("view.toggleStatusBar", "C-a"); // a command on a field's editing key
        }));
        run(() -> {
            filter.requestFocus();
            filter.setText("one two");
            filter.positionCaret(4);
        });
        run(() -> press(filter, KeyCode.A, false, true, false, false));
        assertEquals(0, (int) call(filter::getCaretPosition), "C-a went to the start of the field");
        assertTrue(app.services().settings().showStatusBar, "C-a ran no command");
        run(() -> press(filter, KeyCode.E, false, true, false, false));
        assertEquals(7, (int) call(filter::getCaretPosition), "C-e went to the end");
        run(() -> filter.positionCaret(3));
        run(() -> press(filter, KeyCode.K, false, true, false, false));
        assertEquals("one", call(filter::getText), "C-k killed the rest");
        run(() -> app.services().store().update(s -> {
            s.keymap = "cua";
            s.keybindings.remove("view.toggleStatusBar");
        }));
    }

    /**
     * FIND-03 on one view, outside a window: Down goes to the list on its first kept game, a step with nothing
     * selected starts at an end, a step over an emptied list does nothing, and a reload keeps the selected game.
     */
    @Test
    void aFilteredListStepsAndKeepsItsSelection() throws Exception {
        List<com.partida.library.Game> games = List.of(
                new com.partida.library.Game("Alpha", Path.of("/r/a.bin"), "S", null),
                new com.partida.library.Game("Beta", Path.of("/r/b.bin"), "S", null),
                new com.partida.library.Game("Alphabet", Path.of("/r/c.bin"), "", null));
        SystemView view = call(() -> new SystemView("S", games, 1, () -> {}, () -> null));
        assertEquals("Beta", call(() -> view.selected().title()), "the session's selection");
        assertEquals(3, (int) call(view::size));
        assertFalse(call(view::filtering));

        run(() -> view.filter().setText("alpha"));
        assertTrue(call(view::filtering));
        assertEquals("Alpha", call(() -> view.selected().title()), "Beta went; the first kept is selected");
        run(() -> view.list().getSelectionModel().clearSelection());
        run(() -> view.step(false));
        assertEquals("Alphabet", call(() -> view.selected().title()), "back from nothing: the last");
        run(() -> view.list().getSelectionModel().clearSelection());
        run(() -> view.step(true));
        assertEquals("Alpha", call(() -> view.selected().title()), "on from nothing: the first");
        run(() -> view.list().getSelectionModel().clearSelection());
        run(() -> press(view.filter(), KeyCode.DOWN, false, false, false, false));
        assertEquals("Alpha", call(() -> view.selected().title()), "Down picks the first kept game");
        run(() -> press(view.filter(), KeyCode.A, false, false, false, false)); // typing is the box's own
        run(() -> view.step(true));
        assertEquals("Alphabet", call(() -> view.selected().title()));

        // A reload keeps the selected game when it is still there, and leaves the rest alone.
        run(() -> view.setGames(List.of(games.get(2), games.get(0))));
        assertEquals("Alphabet", call(() -> view.selected().title()));
        assertEquals(0, (int) call(view::selectedIndex));
        run(() -> view.setGames(List.of(games.get(1))));
        assertTrue(call(() -> view.list().getItems().isEmpty()), "the filter keeps nothing");
        run(() -> view.step(true));
        assertEquals(-1, (int) call(view::selectedIndex));
        run(() -> view.filter().clear());
        assertEquals(1, (int) call(() -> view.list().getItems().size()));
        assertEquals(1, (int) call(() -> new SystemView("T", List.of(), 5, () -> {}, () -> null).size() + 1));
    }
}
