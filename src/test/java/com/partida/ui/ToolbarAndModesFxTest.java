package com.partida.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.jspecify.annotations.Nullable;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The customizable toolbar (TBAR-09) and the focus modes (WIN-10), in a real headless toolkit. */
@Tag("fx")
class ToolbarAndModesFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;
    private AppToolbar toolbar;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
        toolbar = window.toolbar();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    /** The cluster's command ids in order, {@code null} for a gap, as the bar holds them now. */
    private List<@Nullable String> barIds() throws Exception {
        return call(() -> {
            List<@Nullable String> ids = new ArrayList<>();
            for (Node item : toolbar.bar().getItems()) {
                if (item instanceof Separator) {
                    ids.add(null);
                } else if (item instanceof Button button) {
                    ids.add(button.getId().substring("toolbar-".length()));
                }
            }
            return ids;
        });
    }

    private SettingsWindow customize() throws Exception {
        assertTrue(call(() -> services.commands().run("view.customizeToolbar")));
        SettingsWindow settings = Objects.requireNonNull(window.settingsWindow());
        assertSame(
                SettingsWindow.Page.GENERAL,
                call(() -> settings.sidebar().getSelectionModel().getSelectedItem()));
        return settings;
    }

    private static void key(Node target, KeyCode code, boolean alt) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, alt, false));
    }

    /** TBAR-09: a reorder and a removal in Settings reach the bar and the settings file, and Restore undoes them. */
    @Test
    void theUserReordersRemovesAndRestoresTheButtons() throws Exception {
        assertEquals(AppToolbar.CLUSTER, barIds());
        SettingsWindow settings = customize();
        var list = settings.toolbarList();
        assertTrue(call(list::isFocused) || call(() -> list.getScene().getFocusOwner() == list));
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()), "the first entry is selected");
        assertTrue(call(() -> settings.toolbarRestore().isDisabled()), "nothing to restore yet");

        // Alt+Down moves the palette's button over the gap after it, into Play's group: no gap is left
        // leading the bar. Again, past Play; the selection follows the button each time.
        run(() -> key(list, KeyCode.DOWN, true));
        assertEquals(java.util.Arrays.asList("palette.show", "game.play", "game.stop", null), barIds().subList(0, 4));
        assertEquals(0, call(() -> list.getSelectionModel().getSelectedIndex()));
        run(() -> key(list, KeyCode.DOWN, true));
        assertEquals(java.util.Arrays.asList("game.play", "palette.show", "game.stop", null), barIds().subList(0, 4));
        assertEquals(1, call(() -> list.getSelectionModel().getSelectedIndex()));
        assertEquals("palette.show", services.settings().toolbarLayout.get(1));
        assertFalse(call(() -> settings.toolbarRestore().isDisabled()));

        // Delete removes it from the bar; it is then one of the buttons Add offers.
        run(() -> key(list, KeyCode.DELETE, false));
        assertFalse(barIds().contains("palette.show"));
        assertNull(toolbar.button("palette.show"));
        List<String> offered = call(() ->
                settings.toolbarAdd().getItems().stream().map(MenuItem::getText).toList());
        assertTrue(offered.contains(services.commands().get("palette.show").title()), offered.toString());

        // Add puts it back, at the end; a command the bar did not have by default can go on it too.
        run(() -> settings.toolbarAdd().getItems().stream()
                .filter(i -> Objects.equals(
                        i.getText(), services.commands().get("tool.systems").title()))
                .findFirst()
                .orElseThrow()
                .fire());
        assertEquals("tool.systems", barIds().getLast());
        assertNotNull(toolbar.button("tool.systems"));

        // Restore Default brings the app's own layout back, and saves nothing of the user's.
        assertTrue(call(() -> services.commands().run("view.restoreToolbar")));
        assertEquals(AppToolbar.CLUSTER, barIds());
        assertTrue(services.settings().toolbarLayout.isEmpty());
        assertNotNull(services.commands().get("view.restoreToolbar").unavailableReason());
        assertEquals(
                AppToolbar.CLUSTER.stream().filter(Objects::nonNull).count(),
                call(() -> list.getItems().stream()
                        .filter(id -> !ToolbarLayout.GAP.equals(id))
                        .count()));
    }

    /** TBAR-09: the layout lives in the settings; a layout set there (or by another window) reaches the bar. */
    @Test
    void theBarFollowsTheSavedLayout() throws Exception {
        run(() -> services.store()
                .update(s -> s.toolbarLayout = new ArrayList<>(
                        List.of("game.play", "|", "settings.theme", "settings.keymap", "no.such.command"))));
        assertEquals(java.util.Arrays.asList("game.play", null, "settings.theme", "settings.keymap"), barIds());
        Button play = Objects.requireNonNull(toolbar.button("game.play"));

        // Simple mode keeps Play and hides the rest, and the gap they leave.
        run(() -> services.store().update(s -> s.simpleMode = true));
        assertTrue(call(play::isVisible));
        assertFalse(call(() -> toolbar.button("settings.theme").isVisible()));
        assertFalse(call(() -> toolbar.bar().getItems().get(1).isVisible()), "a gap with nothing after it");
        run(() -> services.store().update(s -> s.simpleMode = false));
        assertTrue(call(() -> toolbar.button("settings.theme").isVisible()));

        // A button that comes back is the same button, named again for the keymap.
        run(() -> services.store().update(s -> s.toolbarLayout = new ArrayList<>()));
        assertSame(play, toolbar.button("game.play"));
        assertEquals(AppToolbar.CLUSTER, barIds());
    }

    /** WIN-10: Zen shows no chrome, Expert only the status bar; each its own command, and off again as it was. */
    @Test
    void zenAndExpertHideTheChromeAndGiveItBack() throws Exception {
        Node menuBar = window.menuBar();
        Node bar = toolbar.node();
        Node status = window.statusBar();
        Parent tabPane = window.tabs().tabPane();
        Node stripe = window.toolWindows().stripe(ToolWindow.Side.LEFT);
        boolean stripeBefore = call(stripe::isVisible);

        assertTrue(call(() -> services.commands().run("view.toggleZenMode")));
        assertSame(Chrome.Focus.ZEN, window.focus());
        for (Node hidden : List.of(menuBar, bar, status, stripe)) {
            assertFalse(call(hidden::isVisible), hidden + " shows in Zen mode");
            assertFalse(call(hidden::isManaged));
        }
        assertTrue(tabPane.getStyleClass().contains("no-tab-header"), "the tab strip shows in Zen mode");

        // Expert from Zen: the status bar comes back, nothing else.
        assertTrue(call(() -> services.commands().run("view.toggleExpertMode")));
        assertSame(Chrome.Focus.EXPERT, window.focus());
        assertTrue(call(status::isVisible));
        assertFalse(call(menuBar::isVisible));
        assertFalse(call(bar::isVisible));
        assertFalse(call(stripe::isVisible));

        // Off again: the window as it was, and no setting was touched.
        assertTrue(call(() -> services.commands().run("view.toggleExpertMode")));
        assertSame(Chrome.Focus.NONE, window.focus());
        assertTrue(call(menuBar::isVisible));
        assertTrue(call(bar::isVisible));
        assertTrue(call(status::isVisible));
        assertEquals(stripeBefore, call(stripe::isVisible));
        assertFalse(tabPane.getStyleClass().contains("no-tab-header"));
        assertTrue(services.settings().showToolbar && services.settings().showStatusBar);

        // Both modes are rows of the View menu.
        List<String> rows = window.menus().groups().stream()
                .flatMap(List::stream)
                .map(AppMenus.Row::commandId)
                .toList();
        assertTrue(rows.contains("view.toggleZenMode") && rows.contains("view.toggleExpertMode"));
    }
}
