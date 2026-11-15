package com.partida.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import com.partida.command.Command;
import com.partida.config.Settings;
import com.partida.i18n.Messages;
import com.partida.library.GameCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.run;
import static com.partida.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tabs and the tool windows in a real headless toolkit, a test per rule of Pauta's Tabs and tool panels. */
@Tag("fx")
class TabsAndPanelsFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;
    private DocumentTabs tabs;
    private ToolWindowManager tools;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        launch();
    }

    /** Three of the fake library's systems, open beside Welcome as a first run's samples were. */
    private static final String R = FakeLibrary.SYSTEMS.get(0);

    private static final String N = FakeLibrary.SYSTEMS.get(1);
    private static final String L = FakeLibrary.SYSTEMS.get(7);

    private void launch() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
        tabs = window.tabs();
        tools = window.toolWindows();
        FakeLibrary.load(services, dir.resolve("library"));
        if (titles().equals(List.of("Welcome"))) {
            for (String name : List.of(R, N, L)) {
                GameCollection system = FakeLibrary.collection(services, name);
                run(() -> tabs.open(system));
            }
            select(tab("Welcome"));
        }
        settle();
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> openMenus().forEach(ContextMenu::hide));
        FxTestSupport.stop(app);
    }

    private static List<ContextMenu> openMenus() {
        List<ContextMenu> menus = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (w instanceof ContextMenu menu && menu.isShowing()) menus.add(menu);
        }
        return menus;
    }

    /** CSS, layout, the stripe alignment, and layout again: what a pulse does. */
    private void settle() throws Exception {
        run(() -> {
            var root = window.stage().getScene().getRoot();
            root.applyCss();
            root.layout();
            tools.afterLayout();
            root.applyCss();
            root.layout();
        });
    }

    private boolean runCommand(String id) throws Exception {
        return call(() -> services.commands().run(id));
    }

    private Tab tab(String name) throws Exception {
        return call(() -> tabs.tabs().stream()
                .filter(t -> tabs.title(t).equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tab " + name)));
    }

    private void select(Tab tab) throws Exception {
        run(() -> tabs.tabPane().getSelectionModel().select(tab));
    }

    private List<String> titles() throws Exception {
        return call(() -> tabs.tabs().stream().map(tabs::title).toList());
    }

    private static Region header(Tab tab) {
        return (Region) Objects.requireNonNull(tab.getGraphic());
    }

    private static Label titleLabel(Tab tab) {
        return (Label) Objects.requireNonNull(header(tab).lookup(".tab-title"));
    }

    // ---- Tabs ----

    /** TABS-01: every tab operation is a command with a menu row; the tab's menu lists the per-tab ones. */
    @Test
    void everyTabOperationIsACommandWithAMenuRow() throws Exception {
        List<String> operations = List.of(
                "buffer.close",
                "buffer.closeOthers",
                "buffer.closeAll",
                "buffer.closeUnmodified",
                "buffer.closeLeft",
                "buffer.closeRight",
                "buffer.togglePin",
                "buffer.reopenClosed",
                "buffer.next",
                "buffer.previous");
        List<String> rows = call(() -> window.menus().groups().stream()
                .flatMap(List::stream)
                .map(AppMenus.Row::commandId)
                .toList());
        for (String id : operations) {
            assertNotNull(services.commands().get(id), id);
            assertTrue(rows.contains(id), id + " has no menu row");
            assertNotNull(Icons.forCommand(id), id + " has no glyph");
        }
        List<String> perTab = List.of(
                "buffer.close", "buffer.closeOthers", "buffer.closeLeft", "buffer.closeRight", "buffer.togglePin");
        assertTrue(TabContextMenu.COMMANDS.containsAll(perTab));

        // A right-click on a tab selects it first and opens its menu of commands.
        Tab notes = tab(N);
        run(() -> Event.fireEvent(
                header(notes), new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        assertSame(notes, call(tabs::selected));
        List<String> items = call(() -> tabs.menu().items().stream()
                .map(MenuItem::getId)
                .filter(Objects::nonNull)
                .toList());
        assertEquals(perTab.size() + 2, items.size()); // and Close Unmodified, Close All
        assertTrue(items.containsAll(perTab));
    }

    /** TABS-02, TABS-03, TABS-10: the header is the title and carries the pinned state; a system is never unsaved. */
    @Test
    void theHeaderIsTheTitleAndCarriesTheStates() throws Exception {
        Tab notes = tab(N);
        assertEquals("", notes.getText());
        assertEquals(N, call(() -> titleLabel(notes).getText()));
        assertEquals(
                N + " — " + Messages.tr("systems.games", FakeLibrary.games(1)),
                call(() -> notes.getTooltip().getText()));
        select(notes);
        assertEquals(N + " — Partida", call(() -> window.stage().getTitle()), "the system, then the app");
        assertFalse(call(() -> header(notes).getStyleClass().contains("dirty")));
        assertTrue(call(() -> tabs.unsavedNames().isEmpty()), "a quit loses nothing");

        assertTrue(runCommand("buffer.togglePin"));
        assertTrue(call(() -> header(notes).getStyleClass().contains("pinned")));
    }

    /** TABS-04, TABS-05: pinning moves to the pin boundary; bulk closes leave pinned tabs. */
    @Test
    void pinnedTabsSurviveBulkCloses() throws Exception {
        assertEquals(List.of("Welcome", R, N, L), titles());
        select(tab(L));
        assertTrue(runCommand("buffer.togglePin")); // to the end of the pinned group: the front
        assertEquals(List.of(L, "Welcome", R, N), titles());

        // The Welcome page is no document and not pinned (WELC-11): a bulk close closes it too.
        select(tab(N));
        assertTrue(runCommand("buffer.closeLeft"));
        assertEquals(List.of(L, N), titles());
        assertTrue(runCommand("buffer.closeAll"));
        assertEquals(List.of(L), titles());
        assertFalse(runCommand("buffer.closeAll"), "nothing left to close but pinned tabs");

        select(tab(L));
        assertTrue(runCommand("buffer.togglePin")); // unpinned: just after the pinned group
        assertEquals(List.of(L), titles());
        assertFalse(call(() -> header(tab(L)).getStyleClass().contains("pinned")));
    }

    /** TABS-06: Reopen Closed Tab brings the system back with the game that was selected. */
    @Test
    void reopenBringsBackTheSystemAndItsSelectedGame() throws Exception {
        Tab notes = tab(N);
        select(notes);
        SystemView view = Objects.requireNonNull(call(() -> tabs.view(notes)));
        run(() -> view.list().getSelectionModel().select(2));
        assertTrue(runCommand("buffer.close"));
        assertFalse(titles().contains(N));

        assertTrue(runCommand("buffer.reopenClosed"));
        SystemView back = Objects.requireNonNull(call(tabs::activeView));
        assertEquals(N, back.title());
        assertEquals(2, call(back::selectedIndex));
        assertEquals(
                FakeLibrary.title(1, 3),
                call(() -> Objects.requireNonNull(back.selected()).title()));
        assertFalse(runCommand("buffer.reopenClosed"), "the list is empty again");
    }

    /** TABS-07: a dragged tab lands beside its target and stays in its group. */
    @Test
    void aDroppedTabLandsBesideItsTarget() throws Exception {
        Tab readme = tab(R);
        Tab license = tab(L);
        Tab welcome = tab("Welcome");
        run(() -> tabs.moveBeside(readme, license, true));
        assertEquals(List.of("Welcome", N, L, R), titles());
        run(() -> tabs.moveBeside(readme, welcome, false)); // the Welcome tab is not pinned (WELC-11)
        assertEquals(List.of(R, "Welcome", N, L), titles());
    }

    /** The Systems window lists every system with its game count, and says why when it lists none. */
    @Test
    void theSystemsWindowListsEverySystemWithItsCount() throws Exception {
        assertTrue(runCommand("tool.systems"));
        settle();
        LibraryPanels.Systems systems = Objects.requireNonNull(window.systems());
        assertEquals(
                FakeLibrary.SYSTEMS,
                call(() -> systems.list().getItems().stream()
                        .map(GameCollection::name)
                        .toList()));
        List<String> rows = call(() -> systems.list().lookupAll(".list-cell").stream()
                .map(n -> ((ListCell<?>) n).getText())
                .filter(Objects::nonNull)
                .toList());
        assertTrue(rows.contains(R + "   " + FakeLibrary.games(0)), rows::toString);

        // A RetroArch folder with nothing in it: an empty list that says why.
        run(() -> services.store()
                .update(st ->
                        st.library.retroarchConfigDir = dir.resolve("empty").toString()));
        FakeLibrary.waitLoaded(services);
        assertTrue(call(() -> systems.list().getItems().isEmpty()));
        assertEquals(
                Messages.tr("systems.empty"), call(() -> systems.placeholder().getText()));
        assertEquals(0, call(() -> Objects.requireNonNull(tabs.view(tab(N))).size()), "an open tab empties too");
    }

    /** TABS-09: the switcher moves, closes the highlighted file, and keeps its width. */
    @Test
    void theSwitcherMovesClosesAndKeepsItsWidth() throws Exception {
        // C-n is the Emacs keymap's line chord; the switcher moves on it as the pickers do (QOPEN-12).
        run(() -> services.store().update(st -> st.keymap = "emacs"));
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        assertTrue(call(() -> window.overlays().isShowing()));
        double width = call(() -> switcher.card().getPrefWidth());
        assertSame(
                call(tabs::selected),
                call(() -> switcher.list().getSelectionModel().getSelectedItem()));

        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.N, false, true, false, false)));
        assertEquals(1, call(() -> switcher.list().getSelectionModel().getSelectedIndex()));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.BACK_SPACE, false, false, false, false)));
        assertFalse(titles().contains(R));
        assertEquals(width, call(() -> switcher.card().getPrefWidth()));

        // Releasing Ctrl after moving with it held activates the highlighted file.
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.N, false, true, false, false)));
        Tab highlighted = call(() -> switcher.list().getSelectionModel().getSelectedItem());
        run(() -> Event.fireEvent(
                switcher.list(),
                new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.CONTROL, false, false, false, false)));
        assertFalse(call(() -> window.overlays().isShowing()));
        assertSame(highlighted, call(tabs::selected));
        run(() -> services.store().update(st -> st.keymap = "cua"));
    }

    /** TABS-09: opened with C-x C-b, letting go of Ctrl does not close the list. */
    @Test
    void releasingCtrlWithoutMovingKeepsTheSwitcherOpen() throws Exception {
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        run(() -> Event.fireEvent(
                switcher.list(),
                new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.CONTROL, false, false, false, false)));
        assertTrue(call(() -> window.overlays().isShowing()));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.ENTER, false, false, false, false)));
        assertFalse(call(() -> window.overlays().isShowing()));
    }

    /** TABS-12: a command hides the tab bar, and the choice is kept. */
    @Test
    void aCommandHidesTheTabBar() throws Exception {
        assertTrue(runCommand("view.toggleTabBar"));
        assertFalse(services.settings().showTabBar);
        assertTrue(call(() -> tabs.tabPane().getStyleClass().contains("no-tab-header")));
        assertTrue(runCommand("view.toggleTabBar"));
        assertFalse(call(() -> tabs.tabPane().getStyleClass().contains("no-tab-header")));
    }

    /** Next Tab and Previous Tab cycle the strip; Welcome comes back. */
    @Test
    void nextAndPreviousCycleTheStrip() throws Exception {
        select(tab("Welcome"));
        assertTrue(runCommand("buffer.previous"));
        assertEquals(L, call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertTrue(runCommand("buffer.next"));
        assertEquals("Welcome", call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertTrue(runCommand("buffer.close"));
        assertFalse(titles().contains("Welcome"));
        assertTrue(runCommand("view.welcome"));
        assertEquals("Welcome", titles().getFirst());
    }

    // ---- Tool panels ----

    /** TABS-13: each window has a tool.id command, and its stripe button runs it. */
    @Test
    void eachToolWindowHasACommandItsButtonRuns() throws Exception {
        for (ToolWindow tw : tools.all()) {
            Command command = services.commands().get(tw.commandId());
            assertNotNull(command, tw.id());
            assertEquals("tool." + tw.id(), tw.commandId());
        }
        Button button = Objects.requireNonNull(tools.stripeButton(window.systemsWindow()));
        run(button::fire);
        assertTrue(tools.isOpen(window.systemsWindow()));
        assertTrue(call(() -> button.getPseudoClassStates().contains(OverlayToggles.OPEN)));
        assertTrue(runCommand("tool.systems"));
        assertFalse(tools.isOpen(window.systemsWindow()));
        // M-1 … in the Emacs keymap.
        assertEquals("M-1", com.partida.command.Keymap.load("emacs").bindings().get("tool.systems"));
    }

    /** TABS-14: no closed window builds its content before the first paint. */
    @Test
    void noClosedToolWindowIsBuiltAtStart() throws Exception {
        for (ToolWindow tw : tools.all()) assertNull(tw.contentIfBuilt(), tw.id());
        assertNull(window.console());
        assertNull(window.game());
        assertTrue(runCommand("tool.retroarch"));
        assertNotNull(window.retroarchWindow().contentIfBuilt());
        assertNull(window.systemsWindow().contentIfBuilt());
    }

    /** TABS-15: what is open, where, in what order, hidden, and the sizes come back on the next launch. */
    @Test
    void theArrangementComesBackOnTheNextLaunch() throws Exception {
        assertTrue(runCommand("tool.systems"));
        settle();
        assertNull(call(tools::state).sizes.get("systems"), "no size of its own yet");
        double before = ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT);
        assertEquals(before, call(() -> tools.horizontalSplit().getDividerPositions()[0]), 0.002);
        run(() -> tools.moveTo(window.retroarchWindow(), ToolWindow.Side.LEFT));
        run(() -> tools.setVisible(window.gameWindow(), false));
        run(() -> tools.resize(window.systemsWindow(), ToolWindowManager.RESIZE_STEP));
        Settings.ToolWindows saved = call(tools::state);
        assertEquals(List.of("systems"), saved.open);
        assertEquals("LEFT", saved.sides.get("retroarch"));
        assertEquals(List.of("game"), saved.hidden);
        double share = Objects.requireNonNull(saved.sizes.get("systems"));
        assertEquals(before + ToolWindowManager.RESIZE_STEP, share, 0.005);
        run(window::quit);
        FxTestSupport.stop(app);

        launch();
        assertTrue(tools.isOpen(window.systemsWindow()));
        assertEquals(ToolWindow.Side.LEFT, tools.side(window.retroarchWindow()));
        assertFalse(tools.isVisible(window.gameWindow()));
        assertEquals(share, Objects.requireNonNull(call(tools::state).sizes.get("systems")), 1e-9);
        // and the divider is where the share says, after the first layout too
        assertEquals(share, call(() -> tools.horizontalSplit().getDividerPositions()[0]), 0.002);
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.LEFT).getChildren().size() == 2));
    }

    /** TABS-16: a side holds two windows at most, stacked. */
    @Test
    void aSideHoldsTwoWindowsAtMost() throws Exception {
        run(() -> {
            tools.moveTo(window.gameWindow(), ToolWindow.Side.LEFT);
            tools.moveTo(window.retroarchWindow(), ToolWindow.Side.LEFT);
        });
        select(tab(N));
        assertTrue(runCommand("tool.systems"));
        run(() -> tools.openInSplit(window.gameWindow()));
        assertEquals(2, tools.openOn(ToolWindow.Side.LEFT).size());
        assertFalse(call(() -> tools.canSplitWith(window.retroarchWindow())), "a third would not fit");
        assertTrue(runCommand("tool.retroarch")); // in place of what is open there
        assertEquals(List.of(window.retroarchWindow()), tools.openOn(ToolWindow.Side.LEFT));
    }

    /** TABS-18: float, maximize (one at a time), resize and close from the keyboard. */
    @Test
    void floatMaximizeResizeAndCloseAreCommands() throws Exception {
        List<String> emacs = List.of(
                "view.floatToolWindow",
                "view.maximizeToolWindow",
                "view.closeFocusedToolWindow",
                "view.resizeHorizontalToolWindowGreater",
                "view.resizeHorizontalToolWindowLess",
                "view.resizeBottomToolWindow",
                "view.resizeBottomToolWindowLess");
        var keymap = com.partida.command.Keymap.load("emacs");
        for (String id : emacs) assertTrue(keymap.bindings().containsKey(id), id + " is not bound in Emacs");

        assertTrue(runCommand("tool.systems"));
        assertTrue(runCommand("tool.retroarch"));
        double before = call(tools::state)
                .sizes
                .getOrDefault("systems", ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT));
        assertTrue(runCommand("view.resizeHorizontalToolWindowGreater"));
        assertEquals(
                before + ToolWindowManager.RESIZE_STEP, call(tools::state).sizes.get("systems"), 0.02);

        run(() -> tools.focus(window.systemsWindow()));
        assertTrue(runCommand("view.maximizeToolWindow"));
        assertSame(window.systemsWindow(), tools.maximized());
        run(() -> tools.toggleMaximized(window.retroarchWindow()));
        assertSame(window.retroarchWindow(), tools.maximized(), "one at a time");
        run(() -> tools.toggleMaximized(window.retroarchWindow()));
        assertNull(tools.maximized());

        run(() -> tools.focus(window.retroarchWindow()));
        assertTrue(runCommand("view.floatToolWindow"));
        assertTrue(tools.isFloating(window.retroarchWindow()));
        assertTrue(call(() -> Objects.requireNonNull(tools.floatingStage(window.retroarchWindow()))
                .isShowing()));
        // Focus left with the panel; the floating window's own Float button (its header) docks it again.
        run(() -> tools.toggleFloating(window.retroarchWindow()));
        assertFalse(tools.isFloating(window.retroarchWindow()));
        assertTrue(tools.isOpen(window.retroarchWindow()));

        run(() -> tools.focus(window.retroarchWindow()));
        assertTrue(runCommand("view.closeFocusedToolWindow"));
        assertFalse(tools.isOpen(window.retroarchWindow()));
    }

    /** TABS-19: closing the window that holds focus returns it to where it was in the content. */
    @Test
    void closingTheFocusedWindowReturnsFocusToTheContent() throws Exception {
        Tab notes = tab(N);
        select(notes);
        SystemView view = Objects.requireNonNull(call(() -> tabs.view(notes)));
        run(() -> view.list().requestFocus());
        assertTrue(runCommand("tool.systems"));
        settle();
        assertTrue(call(() -> tools.activeToolWindow() == window.systemsWindow()), "focus went into it");
        assertTrue(runCommand("view.closeFocusedToolWindow"));
        assertSame(view.list(), call(() -> window.stage().getScene().getFocusOwner()));
    }

    /** TABS-20: a button shows only when the user has it shown and it is available; neither clobbers the other. */
    @Test
    void hiddenIsNotUnavailable() throws Exception {
        ToolWindow game = window.gameWindow();
        Button button = Objects.requireNonNull(tools.stripeButton(game));
        select(tab("Welcome")); // no system: the Game window is unavailable
        assertFalse(tools.isAvailable(game));
        assertFalse(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        assertTrue(tools.isVisible(game), "the user's choice is untouched");
        assertFalse(runCommand("tool.game"));

        select(tab(N));
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        run(() -> tools.setVisible(game, false)); // the user hides it
        select(tab("Welcome"));
        select(tab(R));
        assertFalse(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        assertTrue(runCommand("view.restoreStripeButtons"));
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
    }

    /** TABS-21: the Output window opens taller on its first open; the others keep their side's size. */
    @Test
    void anOutputWindowAsksForMoreOnItsFirstOpen() throws Exception {
        assertTrue(runCommand("tool.retroarch"));
        assertEquals(0.36, Objects.requireNonNull(call(tools::state).sizes.get("retroarch")), 1e-9);
        assertTrue(0.36 > ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.BOTTOM), "taller than the side's own");
        assertTrue(runCommand("tool.systems"));
        settle();
        assertNull(call(tools::state).sizes.get("systems"), "the others keep their side's size");
        assertEquals(
                ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT),
                call(() -> tools.horizontalSplit().getDividerPositions()[0]),
                0.002);
        run(() -> tools.resize(window.retroarchWindow(), -ToolWindowManager.RESIZE_STEP));
        run(() -> tools.close(window.retroarchWindow()));
        assertTrue(runCommand("tool.retroarch"));
        assertEquals(0.32, Objects.requireNonNull(call(tools::state).sizes.get("retroarch")), 0.02, "yours wins");
    }

    /** TABS-22: the RetroArch window's stripe button badges a spinner while a game runs, without resizing. */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aBusyWindowBadgesItsButtonWithoutResizingIt() throws Exception {
        String command = FakeLibrary.fakeRetroArch(dir);
        run(() -> services.store().update(st -> st.library.retroarchCommand = command));
        FakeLibrary.waitLoaded(services);
        Button button = Objects.requireNonNull(tools.stripeButton(window.retroarchWindow()));
        settle();
        double width = call(button::getWidth);
        double height = call(button::getHeight);
        Node badge = Objects.requireNonNull(call(() -> button.lookup(".tool-stripe-busy")));
        assertFalse(call(badge::isVisible));

        select(tab(N));
        assertTrue(runCommand("game.play"));
        assertTrue(call(() -> window.retroarchWindow().busyProperty().get()));
        assertFalse(runCommand("game.play"), "one game at a time");
        settle();
        assertTrue(call(badge::isVisible));
        assertEquals(width, call(button::getWidth), 0.01);
        assertEquals(height, call(button::getHeight), 0.01);

        // RetroArch's output reaches the console, held until the window first opens.
        assertTrue(runCommand("tool.retroarch"));
        LibraryPanels.Console console = Objects.requireNonNull(window.console());
        waitUntil(() -> console.console().getText().contains("started -L"));
        assertTrue(call(() -> console.console().getText().contains(FakeLibrary.title(1, 1))));

        waitUntil(() -> services.library().stopUnavailable() == null);
        assertTrue(runCommand("game.stop"));
        waitUntil(() -> !badge.isVisible());
        assertFalse(call(services.library()::isPlaying));
        assertFalse(runCommand("game.stop"), "nothing left to stop");
    }

    /** TABS-23: the Game window follows the active tab and its selected game. */
    @Test
    void theGameWindowFollowsTheActiveTab() throws Exception {
        select(tab(R));
        assertTrue(runCommand("tool.game"));
        LibraryPanels.GamePanel game = Objects.requireNonNull(window.game());
        // What it shows is read off the FX thread (PERF-01): each step waits for the read to land.
        waitUntil(() ->
                !game.reading() && FakeLibrary.title(0, 1).equals(game.title().getText()));
        select(tab(N));
        waitUntil(() ->
                !game.reading() && FakeLibrary.title(1, 1).equals(game.title().getText()));
        SystemView notes = Objects.requireNonNull(call(tabs::activeView));
        run(() -> notes.list().getSelectionModel().select(2));
        waitUntil(() ->
                !game.reading() && FakeLibrary.title(1, 3).equals(game.title().getText()));
        assertEquals(
                FakeLibrary.CORE,
                call(() -> Objects.requireNonNull(game.core().getValue()).label()));
        assertEquals("", call(() -> game.warning().getText()), "nothing missing");
        assertNotNull(call(() -> game.selectedCore(notes.selected())));
    }

    // ---- the stripes are toolbars (TBAR-12, TBAR-13) ----

    /** TBAR-12: the tooltip and the accessible text are the title and the chord; Open in Split and Hide. */
    @Test
    void aStripeButtonIsBuiltLikeAToolbarButton() throws Exception {
        ToolWindow systems = window.systemsWindow();
        Button button = Objects.requireNonNull(tools.stripeButton(systems));
        String chord = services.keybindings().label("tool.systems");
        assertFalse(chord.isEmpty());
        assertEquals(
                Icons.withChord("Systems", chord),
                call(() -> button.getTooltip().getText()));
        assertEquals(call(() -> button.getTooltip().getText()), call(button::getAccessibleText));
        assertFalse(call(button::isFocusTraversable), "out of the Tab order, as a toolbar button");

        run(() -> systems.titleProperty().set("Files"));
        assertEquals(Icons.withChord("Files", chord), call(button::getAccessibleText), "bound to the title");

        run(() -> Event.fireEvent(
                button, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        assertTrue(call(() -> button.getContextMenu() == null), "one shared menu, not one per button");
        List<String> items = call(() -> openMenus().stream()
                .flatMap(m -> m.getItems().stream())
                .map(MenuItem::getId)
                .filter(Objects::nonNull)
                .toList());
        assertEquals(List.of("toolWindow.openInSplit", "toolWindow.hide"), items);
        // Hide, run from the button's menu, hides that button.
        assertTrue(runCommand("toolWindow.hide"));
        assertFalse(tools.isVisible(systems));
    }

    /** TBAR-13: the right stripe's first glyph sits on the content's first line: the first game's row. */
    @Test
    void theRightStripesFirstIconSitsOnTheFirstLineOfText() throws Exception {
        Tab notes = tab(N);
        select(notes);
        settle();
        settle();
        SystemView view = Objects.requireNonNull(call(() -> tabs.view(notes)));
        Button first = call(
                () -> (Button) tools.stripe(ToolWindow.Side.RIGHT).getChildren().getFirst());
        double line = call(() -> firstRowCenter(view));
        assertEquals(line, call(() -> glyphCenter(first)), 2.0, "on the first row, not the tab strip");

        // Hiding the tab strip moves the list up, and the glyph follows it.
        assertTrue(runCommand("view.toggleTabBar"));
        settle();
        settle();
        double after = call(() -> firstRowCenter(view));
        assertTrue(after < line - 10, "the list moved up");
        assertEquals(after, call(() -> glyphCenter(first)), 2.0);
    }

    private static double firstRowCenter(SystemView view) {
        for (Node n : view.list().lookupAll(".list-cell")) {
            if (n instanceof ListCell<?> cell && cell.getIndex() == 0) {
                return cell.localToScene(cell.getLayoutBounds()).getCenterY();
            }
        }
        throw new AssertionError("no first row");
    }

    private static double glyphCenter(Button button) {
        Node icon = Objects.requireNonNull(button.getGraphic());
        return icon.localToScene(icon.getLayoutBounds()).getCenterY();
    }
    // ---- the rest of the showcase, through its own controls ----

    /** The Systems window opens a system on Enter or a double-click, and the system's list has the keyboard. */
    @Test
    void theSystemsWindowOpensASystem() throws Exception {
        assertTrue(runCommand("tool.systems"));
        settle();
        var list = (javafx.scene.control.ListView<?>) Objects.requireNonNull(
                Objects.requireNonNull(window.systemsWindow().contentIfBuilt()).lookup(".systems-list"));
        run(() -> list.getSelectionModel().select(3));
        run(() -> Event.fireEvent(list, FxTestSupport.key(KeyCode.ENTER, false, false, false, false)));
        assertEquals(FakeLibrary.SYSTEMS.get(3), call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        run(() -> list.getSelectionModel().select(4));
        run(() -> Event.fireEvent(list, click(list, 2)));
        assertEquals(FakeLibrary.SYSTEMS.get(4), call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertSame(
                Objects.requireNonNull(call(tabs::activeView)).list(),
                call(() -> window.stage().getScene().getFocusOwner()));
        assertTrue(runCommand("systems.open"), "an open system is selected again, not opened twice");
        assertEquals(
                1, titles().stream().filter(FakeLibrary.SYSTEMS.get(4)::equals).count());
    }

    private static javafx.scene.input.MouseEvent click(Node target, int count) {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                javafx.scene.input.MouseButton.PRIMARY,
                count,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                new javafx.scene.input.PickResult(target, 5, 5));
    }

    /** TABS-17: while a stripe button is dragged, an empty stripe shows as a dashed drop zone; moves re-dock. */
    @Test
    void anEmptyStripeIsADropZoneWhileDragging() throws Exception {
        var right = tools.stripe(ToolWindow.Side.RIGHT);
        assertFalse(call(right::isVisible), "Welcome: nothing on the right");
        run(() -> tools.setDragging(true));
        assertTrue(call(right::isVisible));
        assertTrue(call(() -> right.getStyleClass().contains("tool-stripe-drop-target")));
        run(() -> tools.setDragging(false));
        assertFalse(call(right::isVisible));

        assertTrue(runCommand("tool.systems"));
        run(() -> tools.moveTo(window.systemsWindow(), ToolWindow.Side.RIGHT)); // open, it reopens there
        assertEquals(List.of(window.systemsWindow()), tools.openOn(ToolWindow.Side.RIGHT));
        assertEquals("RIGHT", call(tools::state).sides.get("systems"));
        run(() -> tools.moveTo(window.systemsWindow(), ToolWindow.Side.LEFT)); // home again
        assertNull(call(tools::state).sides.get("systems"));
        assertTrue(tools.isOpen(window.systemsWindow()));
    }

    /** A floating window's own close button closes the tool window; float and maximize come back on launch. */
    @Test
    void floatingAndMaximizedComeBackAndAFloatingWindowCloses() throws Exception {
        assertTrue(runCommand("tool.retroarch"));
        run(() -> tools.toggleFloating(window.retroarchWindow()));
        javafx.stage.Stage stage = Objects.requireNonNull(tools.floatingStage(window.retroarchWindow()));
        run(stage::hide);
        assertFalse(tools.isOpen(window.retroarchWindow()));
        assertTrue(call(tools::state).floating.isEmpty());

        assertTrue(runCommand("tool.retroarch"));
        run(() -> tools.toggleFloating(window.retroarchWindow()));
        assertTrue(runCommand("tool.systems"));
        run(() -> tools.toggleMaximized(window.systemsWindow()));
        assertEquals("systems", call(tools::state).maximized);
        run(window::quit);
        FxTestSupport.stop(app);

        launch();
        assertTrue(tools.isFloating(window.retroarchWindow()));
        assertSame(window.systemsWindow(), tools.maximized());
        assertSame(
                window.systemsWindow().contentIfBuilt(),
                call(() -> ((javafx.scene.layout.VBox) tools.node().getCenter())
                        .getChildren()
                        .getLast()));
        run(() -> tools.close(window.retroarchWindow()));
        assertFalse(call(() -> Objects.requireNonNull(window.retroarchWindow().contentIfBuilt())
                                .getScene()
                        != null
                && window.retroarchWindow()
                        .contentIfBuilt()
                        .getScene()
                        .getWindow()
                        .isShowing()));
    }

    /** A divider the user drags is kept when they let go of it (TABS-15). */
    @Test
    void aDraggedDividerIsKept() throws Exception {
        assertTrue(runCommand("tool.systems"));
        settle();
        var split = tools.horizontalSplit();
        Node divider = Objects.requireNonNull(call(() -> split.lookup(".split-pane-divider")));
        run(() -> {
            Event.fireEvent(split, press(divider, javafx.scene.input.MouseEvent.MOUSE_PRESSED));
            split.setDividerPositions(0.3); // where the drag took it
            Event.fireEvent(split, press(divider, javafx.scene.input.MouseEvent.MOUSE_RELEASED));
        });
        assertEquals(0.3, Objects.requireNonNull(call(tools::state).sizes.get("systems")), 1e-6);
        assertEquals(
                0.3,
                Objects.requireNonNull(
                        services.settings().window.toolWindows.sizes.get("systems")),
                1e-6);
    }

    private static javafx.scene.input.MouseEvent press(
            Node target, javafx.event.EventType<javafx.scene.input.MouseEvent> type) {
        return new javafx.scene.input.MouseEvent(
                type,
                1,
                1,
                1,
                1,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                new javafx.scene.input.PickResult(target, 1, 1));
    }

    /** A tool window's header buttons act on their own window, whichever has focus. */
    @Test
    void aPanelsHeaderButtonsActOnItsOwnWindow() throws Exception {
        assertTrue(runCommand("tool.systems"));
        assertTrue(runCommand("tool.retroarch"));
        settle();
        Region panel = (Region)
                Objects.requireNonNull(window.systemsWindow().contentIfBuilt()).getParent();
        List<Button> buttons = call(() -> panel.lookupAll(".tool-window-header-button").stream()
                .map(Button.class::cast)
                .toList());
        assertEquals(3, buttons.size()); // float, maximize, close
        run(() -> buttons.get(1).fire());
        assertSame(window.systemsWindow(), tools.maximized());
        run(() -> buttons.get(1).fire());
        assertNull(tools.maximized());
        run(() -> buttons.get(2).fire());
        assertFalse(tools.isOpen(window.systemsWindow()));
        assertTrue(tools.isOpen(window.retroarchWindow()));
    }

    /** TBAR-03: a header button's tooltip and accessible name carry the chord of the keymap in use. */
    @Test
    void aPanelsHeaderButtonsFollowAKeymapChange() throws Exception {
        assertTrue(runCommand("tool.systems"));
        settle();
        Region panel = (Region)
                Objects.requireNonNull(window.systemsWindow().contentIfBuilt()).getParent();
        Button close = call(() -> panel.lookupAll(".tool-window-header-button").stream()
                .map(Button.class::cast)
                .toList()
                .get(2));
        String id = "view.closeFocusedToolWindow";
        run(() -> services.store().update(s -> s.keybindings.put(id, "C-M-F9")));
        String chord = call(() -> services.keybindings().label(id));
        assertFalse(chord.isEmpty());
        assertTrue(call(close::getAccessibleText).endsWith("(" + chord + ")"), call(close::getAccessibleText));
        assertEquals(
                call(close::getAccessibleText), call(() -> close.getTooltip().getText()));
    }

    /** The other closes, and the switcher's other keys. */
    @Test
    void theOtherClosesAndSwitcherKeys() throws Exception {
        select(tab(R));
        assertTrue(runCommand("buffer.closeRight"));
        assertEquals(List.of("Welcome", R), titles());
        assertTrue(runCommand("buffer.reopenClosed"));
        assertTrue(runCommand("buffer.reopenClosed"));
        select(tab(N));
        assertTrue(runCommand("buffer.closeOthers"));
        assertEquals(List.of(N), titles());
        assertTrue(runCommand("buffer.closeUnmodified"));
        assertEquals(List.of(), titles());
        // WELC-04: the last tab closed leaves the empty area's hint, with the ways in, and the keyboard.
        assertTrue(call(() -> tabs.emptyHint().isVisible()));
        assertTrue(call(
                () -> tabs.emptyHint().getText().contains(services.keybindings().label("palette.show"))));
        settle();
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == tabs.emptyHint()), "it has the keyboard");
        assertTrue(runCommand("view.welcome"));
        assertFalse(call(() -> tabs.emptyHint().isVisible()), "a tab hides it");
        assertFalse(runCommand("buffer.next"), "one tab: nothing to cycle");

        assertTrue(runCommand("buffer.reopenClosed"));
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.TAB, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.TAB, true, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.DOWN, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.UP, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.A, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.DELETE, false, false, false, false)));
        assertEquals(1, titles().size());
        run(() -> Event.fireEvent(switcher.list(), click(switcher.list(), 1)));
        assertFalse(call(() -> window.overlays().isShowing()));
    }

    /** PERF-11: the RetroArch console is bounded, and the newest lines are the ones it keeps. */
    @Test
    void theConsoleKeepsTheNewestLinesUpToItsCap() throws Exception {
        LibraryPanels.Console console = call(LibraryPanels.Console::new);
        int total = LibraryPanels.Console.MAX_LINES + 25;
        run(() -> {
            for (int i = 1; i <= total; i += 5) {
                List<String> batch = new ArrayList<>();
                for (int j = i; j < i + 5 && j <= total; j++) batch.add("line " + j);
                console.append(batch);
            }
            console.append(List.of());
        });
        String text = call(() -> console.console().getText());
        assertEquals(LibraryPanels.Console.MAX_LINES, text.split("\n").length);
        assertTrue(text.startsWith("line 26\n"), "the oldest went first");
        assertTrue(text.endsWith("line " + total + "\n"), "the newest is kept");
        run(console::clear);
        assertEquals("", call(() -> console.console().getText()));
    }

    /** ACC-06: the region commands move the keyboard from the documents to each open tool window and back. */
    @Test
    void theRegionCommandsWalkTheDocumentsAndTheOpenToolWindows() throws Exception {
        select(tab(R));
        assertTrue(runCommand("tool.systems"));
        assertTrue(runCommand("tool.retroarch"));
        settle();
        run(() -> tools.focusEditor());
        assertTrue(runCommand("window.focusNextRegion"));
        assertTrue(call(() -> tools.isFocusIn(window.systemsWindow())), "the left tool window next");
        assertTrue(runCommand("window.focusNextRegion"));
        assertTrue(call(() -> tools.isFocusIn(window.retroarchWindow())), "then the bottom one");
        assertTrue(runCommand("window.focusNextRegion"));
        assertFalse(
                call(() -> tools.isFocusIn(window.systemsWindow()) || tools.isFocusIn(window.retroarchWindow())),
                "and round to the documents");
        assertTrue(runCommand("window.focusPreviousRegion"));
        assertTrue(call(() -> tools.isFocusIn(window.retroarchWindow())), "back to the bottom one");
    }
}
