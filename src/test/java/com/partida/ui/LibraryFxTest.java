package com.partida.ui;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.retroarch.CoreCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.press;
import static com.partida.ui.FxTestSupport.run;
import static com.partida.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The library in the running app: Settings → Library and its palette commands (SET-06), the Game window's
 * warnings, and Play and Stop, each against the fake install, never the developer's own RetroArch.
 */
@Tag("fx")
class LibraryFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;
    private Path config;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
        FakeLibrary.load(services, dir.resolve("library"));
        config = dir.resolve("library").resolve("retroarch");
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> window.overlays().hide());
        FxTestSupport.stop(app);
    }

    private String echo() throws Exception {
        return call(() -> window.statusBar().echo().getText());
    }

    private boolean runCommand(String id) throws Exception {
        return call(() -> services.commands().run(id));
    }

    /** Types {@code query} into the picker that is up, and takes its first match. */
    private void pick(String query) throws Exception {
        TextField field = call(() -> (TextField) window.overlays().root().lookup(".palette-query"));
        assertNotNull(field, "a picker is up");
        run(() -> field.setText(query));
        run(() -> press(field, KeyCode.ENTER, false, false, false, false));
    }

    // ---- the games folders, through the palette (SET-06) ----

    @Test
    void foldersAreAddedGivenACoreAndRemovedFromThePalette() throws Exception {
        assertNotNull(services.commands().get("library.removeFolder").unavailableReason(), "no folders yet");
        assertNotNull(services.commands().get("library.setFolderCore").unavailableReason());

        Path roms = Files.createDirectories(dir.resolve("my-games"));
        Files.writeString(roms.resolve("Extra.bin"), "");
        host.folderAnswer = null;
        assertTrue(runCommand("library.addFolder"));
        assertTrue(services.settings().library.folders.isEmpty(), "a cancel adds nothing");
        host.folderAnswer = roms;
        assertTrue(runCommand("library.addFolder"));
        assertEquals(roms.toString(), services.settings().library.folders.getFirst().path);
        assertEquals(Messages.tr("status.folderAdded", roms.toString()), echo());
        assertTrue(runCommand("library.addFolder"));
        assertEquals(1, services.settings().library.folders.size());
        assertEquals(Messages.tr("status.folderCannotAddTwice", roms.toString()), echo());
        FakeLibrary.waitLoaded(services);

        assertTrue(runCommand("library.setFolderCore"));
        pick("my-games");
        pick("Fake");
        assertEquals("fake_libretro", services.settings().library.folders.getFirst().coreId);
        assertEquals(FakeLibrary.CORE, call(() -> window.coreLabel("fake_libretro")));
        assertEquals(Messages.tr("settings.library.core.detect"), call(() -> window.coreLabel("")));
        assertEquals("gone_libretro", call(() -> window.coreLabel("gone_libretro")));
        FakeLibrary.waitLoaded(services);
        assertTrue(
                call(() -> services.library().state().library().collections().stream()
                        .anyMatch(
                                c -> c.games().stream().anyMatch(g -> g.title().equals("Extra")))),
                "the folder's game is in the library");

        assertTrue(runCommand("library.removeFolder"));
        pick("my-games");
        assertTrue(services.settings().library.folders.isEmpty());
        assertEquals(Messages.tr("status.folderRemoved", roms.toString()), echo());
    }

    @Test
    void theRetroArchFolderAndCommandHavePromptsThatValidate() throws Exception {
        TextInputDialog folder = call(() -> window.promptRetroArchFolder());
        Button ok = (Button) folder.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        run(() -> folder.getEditor().setText("relative/folder"));
        assertTrue(call(ok::isDisabled), "not a full path");
        run(() -> folder.getEditor().setText(config.toString()));
        assertFalse(call(ok::isDisabled));
        run(ok::fire);
        assertEquals(config.toString(), services.settings().library.retroarchConfigDir);

        TextInputDialog command = call(() -> window.promptRetroArchCommand());
        Button commandOk = (Button) command.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        run(() -> command.getEditor().setText("  retroarch --verbose "));
        run(commandOk::fire);
        assertEquals("retroarch --verbose", services.settings().library.retroarchCommand);

        assertTrue(runCommand("library.toggleRecentlyPlayed"));
        assertFalse(services.settings().library.showRecentlyPlayed);
        assertEquals(Messages.tr("status.recentlyPlayedOff"), echo());
        assertTrue(runCommand("library.toggleRecentlyPlayed"));
        assertEquals(Messages.tr("status.recentlyPlayedOn"), echo());

        assertTrue(runCommand("library.refresh"));
        FakeLibrary.waitLoaded(services);
        assertEquals(
                LibraryService.Status.READY,
                call(() -> services.library().state().status()));
    }

    // ---- Settings → Library ----

    @Test
    void theLibraryPageShowsRetroArchAndEachFolder() throws Exception {
        assertTrue(runCommand("library.settings"));
        SettingsWindow settings = Objects.requireNonNull(window.settingsWindow());
        assertEquals(
                SettingsWindow.Page.LIBRARY,
                call(() -> settings.sidebar().getSelectionModel().getSelectedItem()));
        assertTrue(call(() -> settings.retroarchStatus().getText()).contains("retroarch"), "the folder in use");
        assertTrue(call(() -> settings.folderRows().getChildren().size() == 1), "the no-folders line");

        // A full path commits; anything else is marked and kept out.
        run(() -> {
            settings.retroarchFolder().setText("not/full");
            settings.retroarchFolder().fireEvent(new javafx.event.ActionEvent());
        });
        assertTrue(call(() -> settings.retroarchFolder().getStyleClass().contains("invalid")));
        assertEquals(Messages.tr("status.retroarchFolderInvalid"), echo());
        assertEquals(config.toString(), services.settings().library.retroarchConfigDir);
        run(() -> {
            settings.retroarchCommand().setText("retroarch -v");
            settings.retroarchCommand().fireEvent(new javafx.event.ActionEvent());
        });
        assertEquals("retroarch -v", services.settings().library.retroarchCommand);
        run(() -> settings.recentlyPlayed().fire());
        assertFalse(services.settings().library.showRecentlyPlayed);

        // A folder gets a row: its core picked there is kept, and Remove takes it out.
        Path roms = Files.createDirectories(dir.resolve("roms-page"));
        run(() -> services.store()
                .update(s ->
                        s.library.folders.add(new com.partida.config.Settings.Library.Folder(roms.toString(), ""))));
        FakeLibrary.waitLoaded(services);
        HBox row = call(() -> (HBox) settings.folderRows().getChildren().getFirst());
        @SuppressWarnings("unchecked")
        ComboBox<String> core = (ComboBox<String>) row.getChildren().get(1);
        assertTrue(call(() -> core.getItems().contains("fake_libretro")));
        run(() -> core.setValue("fake_libretro"));
        assertEquals("fake_libretro", services.settings().library.folders.getFirst().coreId);
        // The core changes what the library reads: it loads again, and the rows are built anew with it.
        FakeLibrary.waitLoaded(services);
        run(() -> services.store().update(s -> s.library.folders.getFirst().coreId = ""));
        FakeLibrary.waitLoaded(services);
        HBox rebuilt = call(() -> (HBox) settings.folderRows().getChildren().getFirst());
        assertEquals(
                "", call(() -> ((ComboBox<?>) rebuilt.getChildren().get(1)).getValue()), "a change made elsewhere");
        Button remove = (Button) rebuilt.getChildren().get(2);
        run(remove::fire);
        assertTrue(services.settings().library.folders.isEmpty());

        // With RetroArch's folder emptied, there is none (finding one on its own is off in the tests).
        run(() -> {
            settings.retroarchFolder().setText("");
            settings.retroarchFolder().fireEvent(new javafx.event.ActionEvent());
        });
        FakeLibrary.waitLoaded(services);
        assertEquals(
                Messages.tr("systems.notFound"),
                call(() -> settings.retroarchStatus().getText()));
        assertEquals(Messages.tr("status.retroarchCannotFind"), echo(), "a load the user asked for says so");
    }

    // ---- the Game window ----

    @Test
    void theGameWindowSaysWhatIsMissing() throws Exception {
        // A second core for the same games that needs a BIOS file the system folder lacks.
        Files.writeString(config.resolve("cores").resolve("zed_libretro" + CoreCatalog.libraryExtension()), "");
        Files.writeString(
                config.resolve("info").resolve("zed_libretro.info"),
                "display_name = \"Zed (Needs BIOS)\"\nsupported_extensions = \"bin\"\n"
                        + "firmware_count = 1\nfirmware0_path = \"zed_bios.bin\"\nfirmware0_opt = \"false\"\n");
        assertTrue(runCommand("library.refresh"));
        FakeLibrary.waitLoaded(services);
        var system = FakeLibrary.collection(services, 0);
        run(() -> window.tabs().open(system));
        assertTrue(runCommand("tool.game"));
        LibraryPanels.GamePanel game = Objects.requireNonNull(window.game());
        waitUntil(() -> !game.reading() && !game.core().getItems().isEmpty());
        assertEquals(2, call(() -> game.core().getItems().size()));
        assertEquals(FakeLibrary.CORE, call(() -> game.core().getValue().label()));
        assertEquals("", call(() -> game.warning().getText()));

        run(() -> game.core().setValue(game.core().getItems().get(1)));
        assertEquals(
                Messages.tr("game.missingBios", "zed_bios.bin"),
                call(() -> game.warning().getText()));
        run(() -> game.core().setValue(null));
        assertEquals(
                Messages.tr("game.noCore", "bin"), call(() -> game.warning().getText()));
        assertNull(call(() -> game.selectedCore(new Game("Other", dir.resolve("other.bin"), "", null))));

        // On the Welcome tab there is no game: the window empties, and Play says why.
        run(() -> window.tabs().showWelcome());
        assertNotNull(services.commands().get("game.play").unavailableReason());
        assertFalse(call(() -> game.title().getParent().isVisible()), "no game's details on Welcome");
    }

    // ---- Play and Stop ----

    @Test
    void playSaysWhyAGameCantStart() throws Exception {
        LibraryService library = services.library();
        Game unknown = new Game("Odd", dir.resolve("odd.xyz"), "", null);
        run(() -> library.play(unknown, null));
        waitUntil(() -> !library.isPlaying());
        waitUntil(() -> Messages.tr("status.gameCannotFindCore", "xyz")
                .equals(window.statusBar().echo().getText()));

        // A launch command that doesn't exist: RetroArch didn't start.
        run(() -> services.store()
                .update(s -> s.library.retroarchCommand =
                        dir.resolve("no-such-retroarch").toString()));
        FakeLibrary.waitLoaded(services);
        Game game = FakeLibrary.collection(services, 0).games().getFirst();
        run(() -> library.play(game, null));
        waitUntil(() -> !library.isPlaying());
        waitUntil(() -> window.statusBar().echo().getText().startsWith(Messages.tr("status.gameStartFailed", "")));
        assertNotNull(library.stopUnavailable(), "nothing runs");
        run(library::stop); // nothing to stop: nothing happens

        // With RetroArch's folder emptied there is no RetroArch to start.
        run(() -> services.store().update(s -> s.library.retroarchConfigDir = ""));
        FakeLibrary.waitLoaded(services);
        assertEquals(Messages.tr("palette.disabled.noRetroArch"), call(() -> library.playUnavailable(game)));
        run(() -> library.play(game, null));
        assertEquals(Messages.tr("status.retroarchCannotFind"), echo());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void stopAsksRetroArchToQuitWhenNetworkCommandsAreOn() throws Exception {
        try (DatagramSocket retroArch = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            retroArch.setSoTimeout(10_000);
            Files.writeString(
                    config.resolve("retroarch.cfg"),
                    "network_cmd_enable = \"true\"\nnetwork_cmd_port = \"" + retroArch.getLocalPort() + "\"\n");
            String command = FakeLibrary.fakeRetroArch(dir);
            run(() -> services.store().update(s -> s.library.retroarchCommand = command));
            FakeLibrary.waitLoaded(services);
            Game game = FakeLibrary.collection(services, 0).games().getFirst();
            LibraryService library = services.library();
            run(() -> library.play(game, null));
            assertEquals(Messages.tr("palette.disabled.gameRunning"), call(() -> library.playUnavailable(game)));
            assertEquals(game, call(library::running));
            run(() -> library.play(game, null));
            assertEquals(Messages.tr("status.gameCannotStartRunning"), echo());
            waitUntil(() -> library.stopUnavailable() == null);

            run(library::stop);
            byte[] buffer = new byte[64];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            retroArch.receive(packet);
            assertEquals("QUIT", new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));

            // The stand-in doesn't listen: end it, as RetroArch would end itself.
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);
            waitUntil(() -> !library.isPlaying());
            assertNull(call(library::running));
        }
    }

    @Test
    void theSystemsWindowListsTheLoadedLibrary() throws Exception {
        // The Systems window built after a load lists what was loaded, and the console its backlog.
        assertTrue(runCommand("tool.systems"));
        LibraryPanels.Systems systems = Objects.requireNonNull(window.systems());
        assertEquals(
                FakeLibrary.SYSTEMS.size(), call(() -> systems.list().getItems().size()));
        assertNotNull(services.commands().get("game.stop").unavailableReason());
        assertTrue(call(() -> List.copyOf(window.toolWindows().all())).size() == 3);
    }
}
