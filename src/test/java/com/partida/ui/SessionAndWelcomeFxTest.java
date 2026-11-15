package com.partida.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.control.Tab;

import com.partida.config.ConfigStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session apart from the settings (SET-08), the systems that come back (WIN-08), the Welcome page's
 * place, menu and refresh (WELC-03, WELC-05, WELC-11) and the game lists' own text zoom (WIN-09).
 */
@Tag("fx")
class SessionAndWelcomeFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    /** Three of the fake library's systems. */
    private static final String R = FakeLibrary.SYSTEMS.get(0);

    private static final String N = FakeLibrary.SYSTEMS.get(1);
    private static final String L = FakeLibrary.SYSTEMS.get(7);

    private static List<String> titles(MainWindow window) throws Exception {
        return call(() ->
                window.tabs().tabs().stream().map(t -> window.tabs().title(t)).toList());
    }

    /** SET-08, WIN-08, WELC-03: what was open comes back, from a file of its own; --no-session starts afresh. */
    @Test
    void theOpenDocumentsComeBackFromTheSessionFile() throws Exception {
        Startup.Running first = FxTestSupport.start(dir, host);
        try {
            // No session yet: the Welcome page, selected (WELC-03).
            assertEquals(
                    "Welcome",
                    call(() -> first.window().tabs().title(first.window().tabs().selected())));
            assertEquals(List.of("Welcome"), titles(first.window()), "a first run opens nothing of its own");
            FakeLibrary.load(first.services(), dir.resolve("library"));
            for (String name : List.of(R, N, L)) {
                var system = FakeLibrary.collection(first.services(), name);
                run(() -> first.window().tabs().open(system));
            }
            run(() -> first.window().tabs().closeTab(first.window().tabs().welcome()));
            Tab notes = call(() -> first.window().tabs().tabFor(N));
            run(() -> first.window().tabs().tabPane().getSelectionModel().select(notes));
            run(() -> first.window().saveWindowState());
        } finally {
            FxTestSupport.stop(first);
        }
        String settings = Files.readString(dir.resolve(ConfigStore.FILE));
        String session = Files.readString(dir.resolve(ConfigStore.SESSION_FILE));
        assertFalse(settings.contains("\"window\""), "settings.json holds no session state");
        assertFalse(settings.contains(N));
        assertTrue(session.contains(N) && session.contains("\"width\""), session);

        Startup.Running second = FxTestSupport.start(dir, host);
        try {
            assertEquals(List.of(R, N, L), titles(second.window()), "no Welcome page");
            assertEquals(
                    N,
                    call(() ->
                            second.window().tabs().title(second.window().tabs().selected())));
        } finally {
            FxTestSupport.stop(second);
        }

        Startup.Running third = FxTestSupport.start(dir, host, "--no-session");
        try {
            assertEquals(
                    "Welcome",
                    call(() -> third.window().tabs().title(third.window().tabs().selected())));
            assertEquals(List.of("Welcome"), titles(third.window()), "--no-session opens nothing of its own (CLI-04)");
            run(() -> third.window().tabs().closeAllTabs());
            run(() -> third.window().saveWindowState());
        } finally {
            FxTestSupport.stop(third);
        }
        assertTrue(
                Files.readString(dir.resolve(ConfigStore.SESSION_FILE)).contains(N),
                "a --no-session run leaves the last session alone");
    }

    /** WELC-03, WELC-05, WELC-11: Help → Welcome Page; a Close-only menu; rebuilt on every show. */
    @Test
    void theWelcomePageIsInHelpClosesAloneAndIsRebuiltWhenShown() throws Exception {
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            MainWindow window = app.window();
            assertTrue(call(() -> window.menus().groups().stream()
                    .flatMap(List::stream)
                    .anyMatch(r -> r.commandId().equals("view.welcome"))));
            assertTrue(
                    call(() -> window.menuBar().getMenus().getLast().getItems().stream()
                            .anyMatch(i -> "view.welcome".equals(i.getId()))),
                    "in the Help menu");
            CommandMenu menu = call(() -> window.tabs().welcomeMenu());
            run(menu::menu);
            assertEquals(
                    List.of("buffer.close"),
                    call(() -> menu.items().stream()
                            .map(javafx.scene.control.MenuItem::getId)
                            .toList()));
            assertFalse(call(() -> window.tabs().isPinned(window.tabs().welcome())), "not a pinned document");
            int before = call(window::welcomeRefreshes);
            FakeLibrary.load(app.services(), dir.resolve("library"));
            var system = FakeLibrary.collection(app.services(), 0);
            run(() -> window.tabs().open(system)); // away from Welcome, to a system
            assertTrue(call(() -> app.services().commands().run("view.welcome")));
            assertTrue(call(window::welcomeRefreshes) > before, "rebuilt when shown");
        } finally {
            FxTestSupport.stop(app);
        }
    }

    /** WIN-09: the documents zoom on their own, kept as a setting, and every keymap binds the three commands. */
    @Test
    void theDocumentsTextZoomsOnItsOwn() throws Exception {
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            MainWindow window = app.window();
            FakeLibrary.load(app.services(), dir.resolve("library"));
            var system = FakeLibrary.collection(app.services(), 0);
            run(() -> window.tabs().open(system));
            assertTrue(call(() -> app.services().commands().run("view.zoomIn")));
            assertEquals(110, app.services().settings().textZoom);
            assertTrue(call(() -> window.tabs().activeView().list().getStyle()).contains("1.1em"));
            assertEquals(100, app.services().settings().uiZoom, "the UI zoom is its own");
            assertTrue(call(() -> app.services().commands().run("view.zoomReset")));
            assertEquals("", call(() -> window.tabs().activeView().list().getStyle()));
        } finally {
            FxTestSupport.stop(app);
        }
        for (String id : com.partida.command.Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                var bindings = new com.partida.command.Keybindings(
                        com.partida.command.Keymap.load(id, mac), java.util.Map.of(), mac);
                for (String command : List.of("view.zoomIn", "view.zoomOut", "view.zoomReset")) {
                    assertFalse(bindings.label(command).isEmpty(), id + (mac ? " mac " : " ") + command);
                }
            }
        }
    }
}
