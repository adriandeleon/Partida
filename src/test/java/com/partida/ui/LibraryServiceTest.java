package com.partida.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import com.partida.config.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One library load, off the toolkit: what it finds, and what makes the next one necessary. */
class LibraryServiceTest {

    @TempDir
    Path dir;

    @Test
    void withNoRetroArchChosenAndNoneSoughtThereIsNone() {
        LibraryService.State state = LibraryService.load(new Settings.Library(), false);
        assertEquals(LibraryService.Status.NOT_FOUND, state.status());
        assertNull(state.retroArch());
        assertTrue(state.library().collections().isEmpty());
        assertNull(state.collection("anything"));
    }

    @Test
    void aRetroArchThatCantBeReadFails() throws Exception {
        Path config = Files.createDirectories(dir.resolve("retroarch"));
        Files.createDirectories(config.resolve("retroarch.cfg")); // a folder where the file should be
        Settings.Library library = new Settings.Library();
        library.retroarchConfigDir = config.toString();

        LibraryService.State state = LibraryService.load(library, false);

        assertEquals(LibraryService.Status.FAILED, state.status());
        assertTrue(!state.detail().isEmpty());
    }

    @Test
    void aFakeInstallIsReadWithItsSystemsAndFolders() throws Exception {
        Path config = FakeLibrary.install(dir);
        Path roms = Files.createDirectories(dir.resolve("my-games"));
        Files.writeString(roms.resolve("Extra.bin"), "");
        Settings.Library library = new Settings.Library();
        library.retroarchConfigDir = config.toString();
        library.folders.add(new Settings.Library.Folder(roms.toString(), "fake_libretro"));

        LibraryService.State state = LibraryService.load(library, false);

        assertEquals(LibraryService.Status.READY, state.status());
        assertNotNull(state.retroArch());
        assertNotNull(state.collection(FakeLibrary.SYSTEMS.getFirst()));
        assertTrue(
                state.library().collections().stream()
                        .anyMatch(
                                c -> c.games().stream().anyMatch(g -> g.title().equals("Extra"))),
                "the folder's game, under its core's system");
    }

    @Test
    void onlyWhatALoadReadsChangesItsFingerprint() {
        Settings.Library library = new Settings.Library();
        String before = LibraryService.fingerprint(library);
        assertEquals(before, LibraryService.fingerprint(library.copy()));
        library.showRecentlyPlayed = false;
        assertNotEquals(before, LibraryService.fingerprint(library));
        String withHistoryOff = LibraryService.fingerprint(library);
        library.folders.add(new Settings.Library.Folder("/games", ""));
        assertNotEquals(withHistoryOff, LibraryService.fingerprint(library));
        String withFolder = LibraryService.fingerprint(library);
        library.folders.getFirst().coreId = "mgba_libretro";
        assertNotEquals(withFolder, LibraryService.fingerprint(library));
    }
}
