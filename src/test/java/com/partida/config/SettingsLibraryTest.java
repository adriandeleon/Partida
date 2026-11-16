package com.partida.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings → Library's model: clamped on every read, copied deep, and never imported from another computer. */
class SettingsLibraryTest {

    /** A full path on this OS: "/games/x" isn't one on Windows. */
    private static String full(String name) {
        return java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "games", name)
                .toAbsolutePath()
                .toString();
    }

    @Test
    void aHandEditedLibraryIsClampedOnRead() {
        Settings s = new Settings();
        Settings.Library.Folder blank = new Settings.Library.Folder("  ", "x");
        Settings.Library.Folder spaced = new Settings.Library.Folder(" " + full("snes") + " ", "snes9x_libretro");
        Settings.Library.Folder again = new Settings.Library.Folder(full("snes"), "other");
        Settings.Library.Folder nullCore = new Settings.Library.Folder(full("gba"), "");
        Settings.Library.Folder relative = new Settings.Library.Folder("games/nes", "");
        nullCore.coreId = null;
        Settings.Library.Folder nullPath = new Settings.Library.Folder();
        nullPath.path = null;
        s.library.folders = new ArrayList<>(Arrays.asList(blank, null, spaced, again, nullCore, nullPath, relative));
        s.library.retroarchConfigDir = null;
        s.library.retroarchCommand = null;

        s.clamped();

        assertEquals(
                List.of(full("snes"), full("gba")),
                s.library.folders.stream().map(f -> f.path).toList());
        assertEquals("snes9x_libretro", s.library.folders.getFirst().coreId, "the first entry for a path wins");
        assertEquals("", s.library.folders.getLast().coreId);
        assertEquals("", s.library.retroarchConfigDir);
        assertEquals("", s.library.retroarchCommand);

        // SET-11: a RetroArch folder that isn't a full path, which the UI refuses, falls back to the default
        // (find RetroArch on its own) on read; so does one the OS can't parse.
        s.library.retroarchConfigDir = "relative/retroarch";
        s.clamped();
        assertEquals("", s.library.retroarchConfigDir);
        s.library.retroarchConfigDir = "\0bad";
        s.clamped();
        assertEquals("", s.library.retroarchConfigDir);
        s.library.retroarchConfigDir = " " + full("retroarch") + " ";
        s.clamped();
        assertEquals(full("retroarch"), s.library.retroarchConfigDir);
        assertFalse(Settings.Library.isFullPath("   "));

        s.library = null;
        s.clamped();
        assertNotNull(s.library);
        s.library.folders = null;
        s.clamped();
        assertTrue(s.library.folders.isEmpty());
    }

    @Test
    void theLibraryIsCopiedDeep() {
        Settings s = new Settings();
        s.library.folders.add(new Settings.Library.Folder("/games", "mgba_libretro"));
        s.library.retroarchConfigDir = "/opt/retroarch";
        s.library.retroarchCommand = "/opt/retroarch/retroarch --verbose";
        s.library.showRecentlyPlayed = false;

        Settings copy = s.copy();
        assertNotSame(s.library, copy.library);
        assertNotSame(s.library.folders.getFirst(), copy.library.folders.getFirst());
        assertEquals("mgba_libretro", copy.library.folders.getFirst().coreId);
        assertEquals("/opt/retroarch", copy.library.retroarchConfigDir);
        assertFalse(copy.library.showRecentlyPlayed);
        copy.library.folders.getFirst().coreId = "changed";
        assertEquals("mgba_libretro", s.library.folders.getFirst().coreId);
    }

    @Test
    void theCommandSplitsOnSpacesAndAFolderIsFoundByPath() {
        Settings.Library library = new Settings.Library();
        assertEquals(List.of(), library.commandWords());
        library.retroarchCommand = "  flatpak   run org.libretro.RetroArch ";
        assertEquals(List.of("flatpak", "run", "org.libretro.RetroArch"), library.commandWords());
        library.retroarchCommand = null;
        assertEquals(List.of(), library.commandWords());

        library.folders.add(new Settings.Library.Folder("/games", ""));
        assertNotNull(library.folder("/games"));
        assertNull(library.folder("/elsewhere"));
    }

    @Test
    void theLibraryNeverComesInWithAnImport() {
        assertTrue(ConfigImport.LOCAL_KEYS.contains("library"), "paths are this computer's own");
        ObjectNode v8 =
                JsonNodeFactory.instance.objectNode().put("schemaVersion", 8).put("keymap", "emacs");
        ObjectNode migrated = Migrations.migrate(v8);
        assertEquals(Settings.SCHEMA_VERSION, Migrations.versionOf(migrated));
        assertEquals("emacs", migrated.get("keymap").asText(), "8 to 9 only adds the library");
        assertTrue(Migrations.complete());
    }
}
