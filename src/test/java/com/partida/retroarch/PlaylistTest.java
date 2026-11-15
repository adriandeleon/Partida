package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaylistTest {

    @Test
    void readsJsonPlaylists() throws Exception {
        String json = """
                {
                  "version": "1.5",
                  "default_core_path": "/cores/snes9x_libretro.so",
                  "items": [
                    {
                      "path": "/roms/Super Mario World (USA).sfc",
                      "label": "Super Mario World (USA)",
                      "core_path": "DETECT",
                      "core_name": "DETECT",
                      "crc32": "B19ED489|crc",
                      "db_name": "Nintendo - Super Nintendo Entertainment System.lpl"
                    },
                    { "path": "/roms/Untitled Hack.smc", "label": "" }
                  ]
                }
                """;
        Playlist p = Playlist.parse("Nintendo - Super Nintendo Entertainment System", Path.of("x.lpl"), json);
        assertEquals("/cores/snes9x_libretro.so", p.defaultCorePath());
        assertEquals(2, p.entries().size());
        assertEquals("Super Mario World (USA)", p.entries().get(0).title());
        assertEquals("Untitled Hack", p.entries().get(1).title());
        assertEquals("", p.entries().get(1).corePath());
    }

    @Test
    void readsLegacySixLinePlaylists() throws Exception {
        String legacy = """
                /roms/a.nes
                Game A
                DETECT
                DETECT
                00000000|crc
                Nintendo - Nintendo Entertainment System.lpl
                /roms/b.nes
                Game B
                /cores/nestopia_libretro.so
                Nestopia
                DETECT
                Nintendo - Nintendo Entertainment System.lpl
                """;
        Playlist p = Playlist.parse("NES", Path.of("x.lpl"), legacy);
        assertEquals(
                List.of("Game A", "Game B"),
                p.entries().stream().map(Playlist.Entry::title).toList());
        assertEquals("/cores/nestopia_libretro.so", p.entries().get(1).corePath());
    }

    @Test
    void readAllSkipsBrokenFilesWithAWarning(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Good.lpl"), "{\"items\":[{\"path\":\"/a.gb\",\"label\":\"A\"}]}");
        Files.writeString(dir.resolve("Broken.lpl"), "{ not json");
        Files.writeString(dir.resolve("notes.txt"), "ignored");
        List<String> warnings = new ArrayList<>();

        List<Playlist> playlists = Playlist.readAll(dir, warnings);

        assertEquals(List.of("Good"), playlists.stream().map(Playlist::name).toList());
        assertEquals(1, warnings.size());
    }

    @Test
    void namesContentInsideArchives() {
        assertEquals("inner.sfc", Playlist.contentName("/roms/pack.zip#inner.sfc"));
        assertEquals("game.sfc", Playlist.contentName("C:\\roms\\game.sfc"));
        assertEquals("game", Playlist.stem("game.sfc"));
    }
}
