package com.partida.library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.partida.retroarch.CoreCatalog;
import com.partida.retroarch.CoreInfo;
import com.partida.retroarch.RetroArchInstall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The library from a whole RetroArch install laid out in a temporary folder. */
class LibraryLoaderTest {

    @TempDir
    Path dir;

    private Path config;
    private Path cores;
    private Path roms;
    private RetroArchInstall install;

    private static void write(Path p, String text) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    @BeforeEach
    void layOutAnInstall() throws Exception {
        config = dir.resolve("retroarch");
        cores = config.resolve("cores");
        roms = dir.resolve("roms");
        String ext = CoreCatalog.libraryExtension();
        write(config.resolve("retroarch.cfg"), "libretro_info_path = \"" + config.resolve("info") + "\"\n");
        write(cores.resolve("snes9x_libretro" + ext), "");
        write(cores.resolve("bsnes_libretro" + ext), "");
        write(cores.resolve("mgba_libretro" + ext), "");
        write(
                config.resolve("info/snes9x_libretro.info"),
                "display_name = \"SNES (Snes9x)\"\nsupported_extensions = \"sfc|smc\"\n"
                        + "database = \"Nintendo - Super Nintendo Entertainment System\"\n");
        write(
                config.resolve("info/bsnes_libretro.info"),
                "display_name = \"SNES (bsnes)\"\nsupported_extensions = \"sfc|smc\"\ndatabase = \"Other\"\n");
        write(
                config.resolve("info/mgba_libretro.info"),
                "display_name = \"GBA (mGBA)\"\nsupported_extensions = \"gba\"\n"
                        + "database = \"Nintendo - Game Boy Advance\"\n"
                        + "firmware_count = 1\nfirmware0_path = \"gba_bios.bin\"\nfirmware0_opt = \"false\"\n");
        write(roms.resolve("snes/Zelda.sfc"), "");
        write(roms.resolve("gba/Golden Sun.gba"), "");
        write(config.resolve("playlists/Nintendo - Super Nintendo Entertainment System.lpl"), """
                {"default_core_path": "%s", "items": [
                  {"path": "%s", "label": "The Legend of Zelda", "core_path": "DETECT"},
                  {"path": "%s", "label": "Super Metroid", "core_path": ""}
                ]}
                """.formatted(
                        cores.resolve("snes9x_libretro" + ext),
                        roms.resolve("snes/Zelda.sfc"),
                        roms.resolve("snes/Metroid.sfc")));
        write(config.resolve("playlists/builtin/content_history.lpl"), """
                {"items": [{"path": "%s", "label": "", "core_path": "%s",
                  "db_name": "Nintendo - Game Boy Advance.lpl"}]}
                """.formatted(
                        roms.resolve("gba/Golden Sun.gba"), cores.resolve("mgba_libretro" + ext)));
        write(config.resolve("playlists/Broken.lpl"), "{ not json");
        install = RetroArchInstall.custom(config, List.of("retroarch"), dir);
    }

    @Test
    void playlistsFoldersAndHistoryBecomeCollections() throws Exception {
        RetroArchContext ctx = RetroArchContext.load(install);
        LibraryLoader.Library library = LibraryLoader.load(
                ctx,
                List.of(
                        new LibraryFolder(roms.toString(), null, null),
                        new LibraryFolder(dir.resolve("gone").toString(), null, null)),
                true);

        assertEquals(
                List.of(
                        LibraryLoader.RECENTLY_PLAYED,
                        "Nintendo - Game Boy Advance",
                        "Nintendo - Super Nintendo Entertainment System"),
                library.collections().stream().map(GameCollection::name).toList());
        GameCollection snes = library.collections().get(2);
        // The playlist's Zelda and the scanned Zelda.sfc are one game; the playlist's label wins.
        assertEquals(
                List.of("Super Metroid", "The Legend of Zelda"),
                snes.games().stream().map(Game::title).toList());
        assertEquals(3, library.gameCount(), "the history repeats games and isn't counted");
        assertEquals(
                "Golden Sun",
                library.collections().getFirst().games().getFirst().title());

        List<String> warnings = library.warnings();
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Broken.lpl")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not found")));
    }

    @Test
    void theHistoryCanBeLeftOut() throws Exception {
        LibraryLoader.Library library = LibraryLoader.load(RetroArchContext.load(install), List.of(), false);
        assertEquals(
                List.of("Nintendo - Super Nintendo Entertainment System"),
                library.collections().stream().map(GameCollection::name).toList());
    }

    @Test
    void theContextPicksTheRightCore() throws Exception {
        RetroArchContext ctx = RetroArchContext.load(install);
        String ext = CoreCatalog.libraryExtension();
        assertEquals(3, ctx.cores().all().size());
        assertEquals(config.resolve("system"), ctx.systemDir());

        Game zelda = new Game(
                "Zelda", roms.resolve("snes/Zelda.sfc"), "Nintendo - Super Nintendo Entertainment System", null);
        // The core covering the game's system first, then by name.
        assertEquals(
                List.of("SNES (Snes9x)", "SNES (bsnes)"),
                ctx.compatibleCores(zelda).stream().map(CoreInfo::label).toList());
        assertEquals("SNES (Snes9x)", ctx.preferredCore(zelda).orElseThrow().label());

        Game assigned = new Game("Zelda", zelda.rom(), zelda.system(), cores.resolve("bsnes_libretro" + ext));
        assertEquals("SNES (bsnes)", ctx.preferredCore(assigned).orElseThrow().label());

        Game uninstalled = new Game("Zelda", zelda.rom(), zelda.system(), cores.resolve("gone_libretro" + ext));
        assertEquals(
                "SNES (Snes9x)", ctx.preferredCore(uninstalled).orElseThrow().label());

        Game unknown = new Game("Doc", roms.resolve("readme.xyz"), "", null);
        assertTrue(ctx.preferredCore(unknown).isEmpty());

        CoreInfo mgba = ctx.cores().byId("mgba_libretro").orElseThrow();
        assertEquals(
                List.of("gba_bios.bin"),
                mgba.missingFirmware(ctx.systemDir()).stream()
                        .map(CoreInfo.Firmware::path)
                        .toList());
    }

    @Test
    void aPlaylistEntryTakesItsOwnCoreThenThePlaylistsDefault() {
        String ext = CoreCatalog.libraryExtension();
        String own = cores.resolve("bsnes_libretro" + ext).toString();
        String fallback = cores.resolve("snes9x_libretro" + ext).toString();
        var entry = new com.partida.retroarch.Playlist.Entry("~/roms/a.sfc", "A", own, "", "", "");
        Game game = LibraryLoader.toGame(entry, "SNES", fallback, install);
        assertEquals(Path.of(own), game.core());
        assertEquals(dir.resolve("roms/a.sfc"), game.rom());
        assertEquals("SNES", game.system());

        var detect = new com.partida.retroarch.Playlist.Entry("/a.sfc", "A", "DETECT", "", "", "Other.lpl");
        Game defaulted = LibraryLoader.toGame(detect, "SNES", fallback, install);
        assertEquals(Path.of(fallback), defaulted.core());
        assertEquals("Other", defaulted.system());

        assertNull(LibraryLoader.toGame(detect, "SNES", "DETECT", install).core());
    }

    @Test
    void aGameKnowsItsNameInsideAnArchive() {
        Game zipped = new Game("Pack", Path.of("/roms/pack.zip#Inner Game.SFC"), "", null);
        assertEquals("Inner Game.SFC", zipped.contentName());
        assertEquals("Inner Game", zipped.fileStem());
        assertEquals("sfc", zipped.extension());
        assertEquals("", new Game("x", Path.of("/roms/README"), "", null).extension());
        assertEquals(
                "Nintendo - Game Boy Advance", SystemNames.forExtension("GBA").orElseThrow());
        assertTrue(SystemNames.forExtension("zip").isEmpty());
        assertEquals("SNES", new GameCollection("SNES", List.of()).toString());
    }

    @Test
    void aFolderKnowsWhatWasChosenForIt() {
        assertTrue(new LibraryFolder("/r", "core", "Sys").hasCore());
        assertTrue(new LibraryFolder("/r", "core", "Sys").hasSystem());
        assertEquals(false, new LibraryFolder("/r", " ", null).hasCore());
        assertEquals(false, new LibraryFolder("/r", null, "").hasSystem());
    }
}
