package com.partida.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameTitle;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game browser (decision 3): games, not files, as a list or a grid, beside the selected game's ROMs. */
@Tag("fx")
class BrowserFxTest {

    private static final String NES = "Nintendo - Nintendo Entertainment System";

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
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private static Game rom(String file) {
        return new Game("", Path.of("/roms", file), NES, null);
    }

    private static final List<Game> ROMS = List.of(
            rom("Super Mario Bros. (E) (REVA) [!].nes"),
            rom("Super Mario Bros. (U) [!].nes"),
            rom("Super Mario Bros. (U) [b1].nes"),
            rom("Contra (U) [!].nes"),
            rom("Castlevania (U) [!].nes"),
            rom("Super Mario World (U) [h1].nes"));

    /** A view of {@link #ROMS} in a scene of its own, laid out, with hooks that count what they're asked to do. */
    private record Probe(SystemView view, AtomicInteger plays, AtomicInteger romPlays, AtomicBoolean showHidden) {}

    private Probe probe() throws Exception {
        AtomicInteger plays = new AtomicInteger();
        AtomicInteger romPlays = new AtomicInteger();
        AtomicBoolean showHidden = new AtomicBoolean();
        SystemView view = call(() -> {
            SystemView made = new SystemView(
                    NES,
                    ROMS,
                    -1,
                    new SystemView.Hooks(
                            plays::incrementAndGet,
                            romPlays::incrementAndGet,
                            t -> t.best().game(),
                            List::of,
                            showHidden::get,
                            new BoxArt(() -> null),
                            () -> null,
                            () -> null,
                            () -> null));
            new Scene(made.node(), 900, 600);
            return made;
        });
        layout(view);
        return new Probe(view, plays, romPlays, showHidden);
    }

    private static void layout(SystemView view) throws Exception {
        run(() -> {
            Parent root = view.node();
            root.applyCss();
            root.layout();
        });
    }

    private static List<String> titles(SystemView view) throws Exception {
        return call(() -> view.list().getItems().stream().map(GameTitle::title).toList());
    }

    private static void key(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static void typed(Node target, String c) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, c, "", KeyCode.UNDEFINED, false, false, false, false));
    }

    @Test
    void aGamesRomsAreOneEntryAndTheHackOnlyGameWaitsForShowHidden() throws Exception {
        Probe p = probe();
        SystemView view = p.view();
        assertEquals(List.of("Castlevania", "Contra", "Super Mario Bros."), titles(view));
        assertEquals(3, (int) call(view::size));
        assertEquals("Castlevania", call(() -> view.selectedTitle().title()), "the first game is selected");

        p.showHidden().set(true);
        run(view::applyFilter);
        assertEquals(List.of("Castlevania", "Contra", "Super Mario Bros.", "Super Mario World"), titles(view));
        assertEquals(4, (int) call(view::size));

        // A word of any ROM's name keeps its game: "europe" is in one of Mario's.
        run(() -> view.filter().setText("(e)"));
        assertEquals(List.of("Super Mario Bros."), titles(view));
        run(() -> view.filter().clear());
    }

    @Test
    void theDetailsListEveryRomAndPlayStartsTheChosenOne() throws Exception {
        Probe p = probe();
        SystemView view = p.view();
        run(() -> view.list().getSelectionModel().select(2)); // Super Mario Bros.
        GameDetails details = view.details();
        assertEquals(3, (int) call(() -> details.roms().getItems().size()), "the bad dump too");
        assertEquals(
                Messages.tr("details.starts", "Europe · Rev A · Verified"),
                call(() -> details.starts().getText()));
        assertEquals(
                "Super Mario Bros. (E) (REVA) [!].nes",
                call(() -> view.selected().contentName()));

        // A ROM of the list: Enter plays it (library.playRom), and it is the one selected.
        run(() -> details.roms().getSelectionModel().select(1));
        assertEquals(
                "Super Mario Bros. (U) [!].nes", call(() -> view.selectedRom().contentName()));
        run(() -> key(details.roms(), KeyCode.ENTER));
        assertEquals(1, p.romPlays().get());
        run(() -> details.play().fire());
        assertEquals(1, p.plays().get(), "the Play button runs game.play");
        run(() -> key(view.list(), KeyCode.ENTER));
        assertEquals(2, p.plays().get());

        // A hidden ROM is dimmed; the ROM Play starts wears the marker (read out first).
        layout(view);
        List<String> read = call(() -> details.roms().lookupAll(".list-cell").stream()
                .map(n -> String.valueOf(n.getAccessibleText()))
                .filter(t -> !t.equals("null"))
                .toList());
        assertTrue(
                read.stream().anyMatch(t -> t.startsWith(Messages.tr("details.startsMark")) && t.contains("Europe")),
                read.toString());
        assertTrue(
                read.stream().noneMatch(t -> t.startsWith(Messages.tr("details.startsMark")) && t.contains("USA")),
                "one marker");
        assertTrue(call(() -> details.roms().lookupAll(".hidden-rom").size()) >= 1);

        run(() -> view.list().getSelectionModel().clearSelection());
        assertNull(call(details::shown));
        assertNull(call(view::selected));
    }

    @Test
    void typingJumpsToTheGameItStarts() throws Exception {
        Probe p = probe();
        SystemView view = p.view();
        run(() -> typed(view.list(), "s"));
        assertEquals("Super Mario Bros.", call(() -> view.selectedTitle().title()));
        run(() -> typed(view.list(), "c"));
        assertEquals(
                "Castlevania", call(() -> view.selectedTitle().title()), "\"sc\" matches nothing: \"c\" starts over");
        run(() -> {
            typed(view.list(), "c");
            typed(view.list(), "o");
        });
        assertEquals("Contra", call(() -> view.selectedTitle().title()));
        run(() -> typed(view.list(), "?"));
        assertEquals("Contra", call(() -> view.selectedTitle().title()), "not a letter: nothing moves");
    }

    @Test
    void theGridDrawsTilesThatClickAndMove() throws Exception {
        Probe p = probe();
        SystemView view = p.view();
        run(() -> view.setGrid(true));
        layout(view);
        assertTrue(call(view::isGrid));
        assertFalse(call(() -> view.list().isVisible()));
        assertTrue(call(() -> view.grid().isVisible()));
        int columns = call(view::columns);
        assertTrue(columns >= 3, "900 px fit several tiles: " + columns);
        assertEquals(1, (int) call(() -> view.grid().getItems().size()), "three games, one row");
        List<Node> tiles = call(view::tiles);
        assertEquals(3, tiles.size());
        assertTrue(
                tiles.getFirst().getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("selected")),
                "the selected game's tile");
        // ACC-01, ACC-02: a row reads as its games, a tile as a button whose press plays.
        assertEquals(javafx.scene.AccessibleRole.BUTTON, tiles.getFirst().getAccessibleRole());
        assertTrue(call(() -> tiles.getFirst().getAccessibleText()).startsWith("Castlevania"));
        assertEquals(
                "Castlevania, Contra, Super Mario Bros.",
                call(() -> view.grid().lookup(".tile-row").getAccessibleText()));
        run(() -> tiles.getFirst().executeAccessibleAction(javafx.scene.AccessibleAction.FIRE));
        assertEquals(1, p.plays().get(), "a screen reader's press plays");
        p.plays().set(0);

        run(() -> key(view.grid(), KeyCode.RIGHT));
        assertEquals("Contra", call(() -> view.selectedTitle().title()));
        run(() -> key(view.grid(), KeyCode.END));
        assertEquals("Super Mario Bros.", call(() -> view.selectedTitle().title()));
        run(() -> key(view.grid(), KeyCode.ENTER));
        assertEquals(1, p.plays().get());
        run(() -> key(view.grid(), KeyCode.F5)); // a key the grid doesn't move by: nothing

        Node castlevania = call(() -> view.tiles().getFirst());
        run(() -> castlevania.fireEvent(click(castlevania, 1)));
        assertEquals("Castlevania", call(() -> view.selectedTitle().title()));
        run(() -> castlevania.fireEvent(click(castlevania, 2)));
        assertEquals(2, p.plays().get(), "a double-click plays");

        run(() -> typed(view.grid(), "s"));
        assertEquals("Super Mario Bros.", call(() -> view.selectedTitle().title()), "type-ahead in the grid too");
        run(() -> view.setGrid(false));
        assertTrue(call(() -> view.list().isVisible()));
        assertEquals(0, (int) call(() -> view.grid().getItems().size()), "no rows kept for a hidden grid");
    }

    private static MouseEvent click(Node node, int count) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                1,
                1,
                1,
                1,
                MouseButton.PRIMARY,
                count,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }

    /** The commands: Toggle Grid View per system, kept in the settings; Toggle Hidden ROMs; Play This ROM. */
    @Test
    void theBrowsersCommandsFollowTheSettings() throws Exception {
        MainWindow window = app.window();
        FakeLibrary.load(services, dir);
        assertNotNull(services.commands().get("library.toggleGrid").unavailableReason(), "no system open");
        var nes = FakeLibrary.collection(services, 4);
        run(() -> window.browser().open(nes));
        SystemView view = Objects.requireNonNull(call(() -> window.browser().activeView()));
        assertNull(services.commands().get("library.toggleGrid").unavailableReason());
        assertTrue(call(() -> services.commands().run("library.toggleGrid")));
        assertTrue(services.settings().library.gridSystems.contains(nes.name()));
        assertTrue(call(view::isGrid));
        assertTrue(call(() -> services.commands().run("library.toggleGrid")));
        assertFalse(call(view::isGrid));
        assertTrue(services.settings().library.gridSystems.isEmpty());

        assertTrue(call(() -> services.commands().run("library.toggleHiddenRoms")));
        assertTrue(services.settings().library.showHiddenRoms);
        assertTrue(call(() -> services.commands().run("library.toggleHiddenRoms")));
        assertFalse(services.settings().library.showHiddenRoms);

        // Play This ROM starts the ROM selected in the details; Play, the one it picks: here the same.
        assertEquals(
                call(() -> window.browser().selectedGame()),
                call(() -> window.browser().selectedRom()));
        assertNull(services.commands().get("library.playRom").unavailableReason());
        run(() -> view.details().roms().getSelectionModel().clearSelection());
        assertNotNull(services.commands().get("library.playRom").unavailableReason());

        // The Systems window counts games.
        run(() -> window.toolWindows().openWindow(window.toolWindows().get("systems"), false));
        assertEquals(
                FakeLibrary.games(4),
                services.library().state().library().counts(nes.name()).shown());
    }
}
