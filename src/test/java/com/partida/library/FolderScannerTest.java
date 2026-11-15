package com.partida.library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.partida.retroarch.CfgFile;
import com.partida.retroarch.CoreCatalog;
import com.partida.retroarch.CoreInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FolderScannerTest {

    private static CoreInfo core(String id, String exts, String databases) {
        CfgFile info = CfgFile.parse("supported_extensions = \"" + exts + "\"\ndatabase = \"" + databases + "\"\n");
        return CoreInfo.fromInfo(id, info, Path.of("/cores/" + id + ".so"));
    }

    private final CoreCatalog catalog = new CoreCatalog(List.of(
            core(
                    "mgba_libretro",
                    "gb|gbc|gba",
                    "Nintendo - Game Boy|Nintendo - Game Boy Color|Nintendo - Game Boy Advance"),
            core("swanstation_libretro", "cue|bin|chd|m3u|pbp", "Sony - PlayStation"),
            core(
                    "genesis_plus_gx_libretro",
                    "md|gen|bin|cue|chd|sms|gg",
                    "Sega - Mega Drive - Genesis|Sega - Mega-CD - Sega CD"),
            core("fbneo_libretro", "zip|7z", "FBNeo - Arcade Games")));

    private static void touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "");
    }

    @Test
    void detectsSystemsByExtension(@TempDir Path root) throws Exception {
        touch(root.resolve("gba/Golden Sun.gba"));
        touch(root.resolve("gba/Golden Sun.sav"));
        touch(root.resolve("md/Sonic.md"));
        touch(root.resolve("disc/Game.cue")); // shared by two systems: not added
        touch(root.resolve("disc/Game.bin")); // track of the .cue: hidden
        touch(root.resolve("arcade/sf2.zip")); // single-database core: added
        touch(root.resolve("readme.txt"));
        touch(root.resolve(".hidden.gba"));

        FolderScanner.Result r = FolderScanner.scan(new LibraryFolder(root.toString(), null, null), catalog);

        assertEquals(
                List.of("Golden Sun", "sf2", "Sonic"),
                r.games().stream().map(Game::title).toList());
        assertEquals("Nintendo - Game Boy Advance", r.games().get(0).system());
        assertEquals("FBNeo - Arcade Games", r.games().get(1).system());
        assertEquals("Sega - Mega Drive - Genesis", r.games().get(2).system());
        assertNull(r.games().get(0).core());
        assertEquals(List.of(root.resolve("disc/Game.cue")), r.unmatched());
    }

    @Test
    void assignedCoreClaimsSharedExtensions(@TempDir Path root) throws Exception {
        touch(root.resolve("Final Fantasy VII/FF7.m3u"));
        touch(root.resolve("Final Fantasy VII/FF7 (Disc 1).cue"));
        touch(root.resolve("Final Fantasy VII/FF7 (Disc 1).bin"));
        touch(root.resolve("Crash.chd"));
        touch(root.resolve("notes.gba")); // not supported by the assigned core

        LibraryFolder folder = new LibraryFolder(root.toString(), "swanstation_libretro", null);
        FolderScanner.Result r = FolderScanner.scan(folder, catalog);

        assertEquals(
                List.of("Crash", "FF7"), r.games().stream().map(Game::title).toList());
        r.games().forEach(g -> {
            assertEquals("Sony - PlayStation", g.system());
            assertEquals(Path.of("/cores/swanstation_libretro.so"), g.core());
        });
        assertEquals(List.of(root.resolve("notes.gba")), r.unmatched());
    }

    @Test
    void folderSystemOverridesDetectedSystem(@TempDir Path root) throws Exception {
        touch(root.resolve("hack.gba"));
        FolderScanner.Result r = FolderScanner.scan(
                new LibraryFolder(root.toString(), null, "Nintendo - Game Boy Advance (Hacks)"), catalog);
        assertEquals("Nintendo - Game Boy Advance (Hacks)", r.games().getFirst().system());
    }
}
