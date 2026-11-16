package com.partida.ui;

import java.nio.file.Path;
import java.util.List;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Right-click menus (MENU-05..09): a Message Log row's menu holds its commands, one menu at a time, a
 * press anywhere else closes it, and the Menu key or Shift+F10 opens the selected row's.
 */
@Tag("fx")
class RightClickMenusFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
        run(() -> {
            services.echo("status.ready");
            services.echo("status.titleBarRestart");
            services.echo("status.languageRestart");
            app.window().openMessageLog();
            app.window().stage().getScene().getRoot().applyCss();
            app.window().stage().getScene().getRoot().layout();
        });
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> openMenus().forEach(ContextMenu::hide));
        FxTestSupport.stop(app);
    }

    private static List<ContextMenu> openMenus() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof ContextMenu m && m.isShowing())
                .map(w -> (ContextMenu) w)
                .toList();
    }

    private ListCell<?> cell(int index) throws Exception {
        return call(() -> app.window().messageLog().list().lookupAll(".list-cell").stream()
                .map(n -> (ListCell<?>) n)
                .filter(c -> c.getIndex() == index && !c.isEmpty())
                .findFirst()
                .orElseThrow());
    }

    @Test
    void aRowsMenuHoldsItsCommandsAndARightClickSelectsTheRow() throws Exception {
        ListCell<?> second = cell(1);
        run(() -> rightClick(second));
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size());
        List<String> ids = call(() -> open.getFirst().getItems().stream()
                .map(MenuItem::getId)
                .filter(java.util.Objects::nonNull)
                .toList());
        assertEquals(List.of("messageLog.copy", "messageLog.clear"), ids);
        assertEquals(
                1,
                call(() -> app.window().messageLog().list().getSelectionModel().getSelectedIndex()));
        // A row runs its command through the registry.
        run(() -> open.getFirst().getItems().stream()
                .filter(i -> "messageLog.clear".equals(i.getId()))
                .findFirst()
                .orElseThrow()
                .fire());
        assertTrue(services.messages().entries().isEmpty());
    }

    @Test
    void aRightClickOnAnotherRowLeavesOneMenu() throws Exception {
        ListCell<?> first = cell(0);
        ListCell<?> second = cell(1);
        run(() -> rightClick(first));
        FxTestSupport.run(() -> {});
        run(() -> rightClick(second));
        FxTestSupport.run(() -> {});
        assertEquals(1, call(() -> openMenus().size()), "MENU-06: one right-click menu at a time");
        assertEquals(
                1,
                call(() -> app.window().messageLog().list().getSelectionModel().getSelectedIndex()));
    }

    @Test
    void aPressInTheOwnerControlOrAnotherWindowClosesIt() throws Exception {
        ListCell<?> first = cell(0);
        run(() -> rightClick(first));
        FxTestSupport.run(() -> {});
        assertEquals(1, call(() -> openMenus().size()));
        Node list = app.window().messageLog().list();
        run(() -> Event.fireEvent(list, press(list, 30, 5, MouseButton.PRIMARY)));
        assertEquals(0, call(() -> openMenus().size()), "MENU-07: a click in the owner list closes it");

        Stage other = call(() -> {
            Stage s = new Stage();
            s.setScene(new Scene(new StackPane(), 200, 200));
            s.show();
            return s;
        });
        try {
            run(() -> rightClick(first));
            FxTestSupport.run(() -> {});
            assertEquals(1, call(() -> openMenus().size()));
            run(() -> {
                Node root = other.getScene().getRoot();
                Event.fireEvent(root, press(root, 20, 20, MouseButton.PRIMARY));
            });
            assertEquals(0, call(() -> openMenus().size()), "MENU-07: a click in another window closes it");
        } finally {
            run(other::close);
        }
    }

    @Test
    void theMenuKeyOpensTheSelectedRowsMenuAtThatRow() throws Exception {
        var list = app.window().messageLog().list();
        run(() -> {
            list.requestFocus();
            list.getSelectionModel().select(2);
            Event.fireEvent(
                    list, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, true, null));
        });
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size(), "MENU-09: the keyboard request opened the row's menu");
        ListCell<?> third = cell(2);
        Bounds row = call(() -> third.localToScreen(third.getBoundsInLocal()));
        assertEquals(row.getMaxY(), call(() -> open.getFirst().getAnchorY()), 1.0, "anchored to the selected row");
        // Escape closes it (the stock ContextMenu, MENU-08).
        run(() -> open.getFirst().hide());
        assertFalse(call(() -> open.getFirst().isShowing()));
    }

    /**
     * MENU-05: a game's row, the RetroArch console and the Systems list each have a right-click menu of
     * commands, and its rows act on that thing.
     */
    @Test
    void theGameRowsTheConsoleAndTheSystemsListHaveMenusOfCommands() throws Exception {
        MainWindow window = app.window();
        FakeLibrary.load(services, dir.resolve("library"));
        var system = FakeLibrary.collection(services, 2);
        run(() -> window.tabs().open(system));
        SystemView view = call(() -> window.tabs().activeView());
        assertNotNull(view);
        CommandMenu gameMenu = call(() -> window.tabs().gameMenu());
        run(gameMenu::menu);
        assertEquals(
                List.of("game.play", "find.show"),
                call(() -> gameMenu.items().stream()
                        .map(javafx.scene.control.MenuItem::getId)
                        .filter(java.util.Objects::nonNull)
                        .toList()));

        assertTrue(call(() -> services.commands().run("retroarch.find")));
        LibraryPanels.Console console = call(window::console);
        ContextMenu consoleMenu = call(() -> console.console().getContextMenu());
        assertNotNull(consoleMenu);
        assertTrue(call(() -> consoleMenu.getItems().stream().anyMatch(i -> "retroarch.clear".equals(i.getId()))));
        assertNotNull(services.commands().get("retroarch.clear").unavailableReason(), "an empty console");
        run(() -> console.append(List.of("a line")));
        assertTrue(call(() -> services.commands().run("retroarch.clear")));
        assertEquals("", call(() -> console.console().getText()));

        assertTrue(call(() -> services.commands().run("tool.systems")));
        LibraryPanels.Systems systems = call(window::systems);
        assertNotNull(systems);
        assertNotNull(services.commands().get("systems.open").unavailableReason(), "nothing selected yet");
        run(() -> systems.list().getSelectionModel().select(0));
        assertTrue(call(() -> services.commands().run("systems.open")));
        assertEquals(
                call(() -> systems.list().getSelectionModel().getSelectedItem().name()),
                call(() -> window.tabs().activeView().title()));
    }

    /** MENU-09: the Menu key and Shift+F10 open the selected game's menu, and the selected system's. */
    @Test
    void theMenuKeyOpensAGamesAndASystemsMenu() throws Exception {
        MainWindow window = app.window();
        FakeLibrary.load(services, dir.resolve("library"));
        var system = FakeLibrary.collection(services, 2);
        run(() -> window.tabs().open(system));
        SystemView view =
                java.util.Objects.requireNonNull(call(() -> window.tabs().activeView()));
        run(
                () -> { // laid out, as a shown window has it
                    window.stage().getScene().getRoot().applyCss();
                    window.stage().getScene().getRoot().layout();
                });
        run(() -> {
            view.list().requestFocus();
            view.list().getSelectionModel().select(1);
            Event.fireEvent(
                    view.list(), new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, true, null));
        });
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size(), "the keyboard request opened the game's menu");
        assertSame(call(() -> window.tabs().gameMenu().menu()), open.getFirst());
        run(() -> open.getFirst().hide());

        assertTrue(call(() -> services.commands().run("tool.systems")));
        LibraryPanels.Systems systems = java.util.Objects.requireNonNull(call(window::systems));
        run(
                () -> { // laid out, as a shown window has it
                    window.stage().getScene().getRoot().applyCss();
                    window.stage().getScene().getRoot().layout();
                });
        run(() -> {
            systems.list().requestFocus();
            systems.list().getSelectionModel().select(0);
            Event.fireEvent(
                    systems.list(),
                    new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, true, null));
        });
        assertEquals(1, call(() -> openMenus().size()), "and the system's");
    }

    @Test
    void theDebugLogConsoleMenuIsItsCommands() throws Exception {
        run(app.window()::openDebugLog);
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        ContextMenu menu = call(() -> window.text().getContextMenu());
        assertNotNull(menu);
        List<String> ids = call(() -> menu.getItems().stream()
                .map(MenuItem::getId)
                .filter(java.util.Objects::nonNull)
                .toList());
        assertEquals(
                List.of("debugLog.find", "debugLog.copy", "debugLog.export", "debugLog.refresh", "debugLog.clear"),
                ids);
        run(window.stage()::close);
    }

    @Test
    void aKeyBindingRowsMenuRunsItsCommandsOnThatRow() throws Exception {
        run(() -> app.window().openSettings(SettingsWindow.Page.KEYMAP));
        SettingsWindow settings = app.window().settingsWindow();
        assertNotNull(settings);
        assertNotNull(services.commands().get("keymap.resetBinding").unavailableReason(), "nothing selected yet");
        run(() -> services.store().update(s -> s.keybindings.put("help.about", "Ctrl+Alt+J")));
        run(() -> settings.search().setText("about"));
        run(() -> {
            settings.stage().getScene().getRoot().applyCss();
            settings.stage().getScene().getRoot().layout();
        });
        ListCell<?> row = call(() -> settings.bindingList().lookupAll(".list-cell").stream()
                .map(n -> (ListCell<?>) n)
                .filter(c -> !c.isEmpty()
                        && ((com.partida.command.Command) c.getItem()).id().equals("help.about"))
                .findFirst()
                .orElseThrow());
        run(() -> rightClick(row));
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size());
        assertEquals(
                List.of("keymap.changeBinding", "keymap.resetBinding"),
                call(() -> open.getFirst().getItems().stream()
                        .map(MenuItem::getId)
                        .filter(java.util.Objects::nonNull)
                        .toList()));
        assertEquals("help.about", call(() -> app.window().selectedBinding().id()), "the right-click selected the row");
        run(() -> open.getFirst().getItems().stream()
                .filter(i -> "keymap.resetBinding".equals(i.getId()))
                .findFirst()
                .orElseThrow()
                .fire());
        assertEquals(null, services.settings().keybindings.get("help.about"), "Reset ran on that row");
        assertTrue(host.questions.getLast().message().contains("Ctrl+Alt+J"));
    }

    @Test
    void aSettingsPageRowsMenuHoldsThatPagesCommands() throws Exception {
        run(() -> app.window().openSettings(SettingsWindow.Page.GENERAL));
        SettingsWindow settings = app.window().settingsWindow();
        assertNotNull(settings);
        run(() -> {
            settings.stage().getScene().getRoot().applyCss();
            settings.stage().getScene().getRoot().layout();
        });
        ListCell<?> sync = call(() -> settings.sidebar().lookupAll(".list-cell").stream()
                .map(n -> (ListCell<?>) n)
                .filter(c -> c.getItem() == SettingsWindow.Page.SYNC)
                .findFirst()
                .orElseThrow());
        run(() -> rightClick(sync));
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size());
        assertEquals(
                SettingsWindow.PAGE_COMMANDS.get(SettingsWindow.Page.SYNC).stream()
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                call(() -> open.getFirst().getItems().stream()
                        .map(MenuItem::getId)
                        .filter(java.util.Objects::nonNull)
                        .toList()));
        for (List<String> ids : SettingsWindow.PAGE_COMMANDS.values()) {
            ids.stream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(id -> assertNotNull(services.commands().get(id), id + " is not a command"));
        }
    }

    private static void rightClick(Node node) {
        Bounds b = node.getBoundsInLocal();
        double x = b.getMinX() + 10;
        double y = b.getMinY() + b.getHeight() / 2;
        Event.fireEvent(node, press(node, x, y, MouseButton.SECONDARY));
        Point2D scene = node.localToScene(x, y);
        Point2D screen = node.localToScreen(x, y);
        Event.fireEvent(
                node,
                new ContextMenuEvent(
                        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        scene.getX(),
                        scene.getY(),
                        screen.getX(),
                        screen.getY(),
                        false,
                        null));
    }

    private static MouseEvent press(Node node, double x, double y, MouseButton button) {
        Point2D scene = node.localToScene(x, y);
        Point2D screen = node.localToScreen(x, y);
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                scene.getX(),
                scene.getY(),
                screen.getX(),
                screen.getY(),
                button,
                1,
                false,
                false,
                false,
                false,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                true,
                button == MouseButton.SECONDARY,
                false,
                null);
    }

    /** MENU-12: a row that deletes, discards or resets wears the danger style; others do not. */
    @Test
    void destructiveRowsAreStyledAsDangerous() throws Exception {
        CommandMenu log = app.window().messageLog().rowMenu();
        run(log::menu);
        javafx.scene.control.MenuItem clear = call(() -> log.items().stream()
                .filter(i -> "messageLog.clear".equals(i.getId()))
                .findFirst()
                .orElseThrow());
        javafx.scene.control.MenuItem copy = call(() -> log.items().stream()
                .filter(i -> "messageLog.copy".equals(i.getId()))
                .findFirst()
                .orElseThrow());
        assertTrue(clear.getStyleClass().contains("danger"));
        assertFalse(copy.getStyleClass().contains("danger"));
    }

    /** KBD-04: a row whose command can't run stays in the menu, grayed, its reason in a tooltip and said aloud. */
    @Test
    void anUnavailableRowStaysAndSaysWhy() throws Exception {
        CommandMenu tabs = app.window().tabs().menu();
        run(() -> {
            tabs.menu();
            tabs.refresh();
        });
        // On the first tab nothing is left of it: Close Tabs to the Left can't run.
        run(() -> app.window().tabs().tabPane().getSelectionModel().select(0));
        run(tabs::refresh);
        javafx.scene.control.CustomMenuItem reopen =
                (javafx.scene.control.CustomMenuItem) call(() -> tabs.items().stream()
                        .filter(i -> "buffer.closeLeft".equals(i.getId()))
                        .findFirst()
                        .orElseThrow());
        String reason = services.commands().get("buffer.closeLeft").unavailableReason();
        assertNotNull(reason);
        assertFalse(call(reopen::isDisable), "not hidden, not dead: a tooltip shows on it");
        assertTrue(call(() -> reopen.getStyleClass().contains("unavailable")));
        assertEquals(reason, call(() -> reopen.getContent().getAccessibleHelp()));
        run(reopen::fire);
        assertTrue(call(() -> app.window().statusBar().echo().getText()).contains(reason), "choosing it says why");
    }
}
