package com.partida.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.partida.config.ConfigStore;
import com.partida.i18n.Messages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session apart from the settings (SET-08), the system that comes back (WIN-08), the browser's empty
 * state and its refresh (WELC-02, WELC-05) and the game lists' own text zoom (WIN-09).
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

    /** The system the window shows, or null on the browser's empty state. */
    private static String shown(MainWindow window) throws Exception {
        return call(() -> window.browser().activePath());
    }

    /** SET-08, WIN-08: the system shown comes back, from a file of its own; --no-session starts afresh. */
    @Test
    void theSystemShownComesBackFromTheSessionFile() throws Exception {
        Startup.Running first = FxTestSupport.start(dir, host);
        try {
            // No session yet: the browser's empty state (WELC-02).
            assertNull(shown(first.window()), "a first run shows no system of its own");
            assertTrue(call(() -> first.window()
                    .browser()
                    .node()
                    .getChildren()
                    .contains(first.window().browser().empty())));
            FakeLibrary.load(first.services(), dir.resolve("library"));
            for (String name : List.of(R, N, L)) {
                var system = FakeLibrary.collection(first.services(), name);
                run(() -> first.window().browser().open(system));
            }
            run(() -> first.window().browser().show(N));
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
            assertEquals(N, shown(second.window()));
        } finally {
            FxTestSupport.stop(second);
        }

        Startup.Running third = FxTestSupport.start(dir, host, "--no-session");
        try {
            assertNull(shown(third.window()), "--no-session shows nothing of its own (CLI-04)");
            run(() -> third.window().saveWindowState());
        } finally {
            FxTestSupport.stop(third);
        }
        assertTrue(
                Files.readString(dir.resolve(ConfigStore.SESSION_FILE)).contains(N),
                "a --no-session run leaves the last session alone");
    }

    /** WELC-02, WELC-05: the empty state lists the ways in with their chords, and is rebuilt on every show. */
    @Test
    void theEmptyStateListsTheWaysInAndIsRebuiltWhenShown() throws Exception {
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            MainWindow window = app.window();
            String waysIn = call(() -> window.waysIn().getText());
            String systems = app.services().keybindings().label("tool.systems");
            assertFalse(systems.isEmpty());
            assertTrue(waysIn.contains(Messages.tr("command.tool.systems") + "   " + systems), waysIn);
            String addFolder = app.services().keybindings().label("library.addFolder");
            assertFalse(addFolder.isEmpty(), "every way in has its chord");
            assertTrue(waysIn.contains(Messages.tr("command.library.addFolder") + "   " + addFolder), waysIn);

            int before = call(window::welcomeRefreshes);
            FakeLibrary.load(app.services(), dir.resolve("library"));
            var system = FakeLibrary.collection(app.services(), 0);
            run(() -> window.browser().open(system)); // away from the empty state, to a system
            assertFalse(call(() -> window.browser()
                    .node()
                    .getChildren()
                    .contains(window.browser().empty())));
            run(() -> window.browser().showEmpty());
            assertTrue(call(window::welcomeRefreshes) > before, "rebuilt when shown");
            assertNull(shown(window));

            // A keymap change rewrites the chords.
            run(() -> app.services().store().update(s -> s.keymap = "emacs"));
            String emacs = app.services().keybindings().label("tool.systems");
            assertTrue(call(() -> window.waysIn().getText()).contains(emacs));
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
            run(() -> window.browser().open(system));
            assertTrue(call(() -> app.services().commands().run("view.zoomIn")));
            assertEquals(110, app.services().settings().textZoom);
            assertTrue(
                    call(() -> window.browser().activeView().list().getStyle()).contains("1.1em"));
            assertEquals(100, app.services().settings().uiZoom, "the UI zoom is its own");
            assertTrue(call(() -> app.services().commands().run("view.zoomReset")));
            assertEquals("", call(() -> window.browser().activeView().list().getStyle()));
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
