package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfgFileTest {

    private static final String SAMPLE = """
            # Software Information
            display_name = "Nintendo - Game Boy Advance (mGBA)"
            video_fullscreen = "false"
            firmware_count = 4
            audio_volume = "-3.500000"

            #include "extra.cfg"
            weird line without equals
            """;

    @Test
    void readsQuotedAndUnquotedValues() {
        CfgFile cfg = CfgFile.parse(SAMPLE);
        assertEquals("Nintendo - Game Boy Advance (mGBA)", cfg.get("display_name", ""));
        assertEquals(4, cfg.getInt("firmware_count", 0));
        assertFalse(cfg.getBool("video_fullscreen", true));
        assertEquals(-3.5, cfg.getDouble("audio_volume", 0), 1e-9);
        assertTrue(cfg.get("missing").isEmpty());
    }

    @Test
    void setChangesOnlyThatLineAndKeepsEverythingElse() {
        CfgFile cfg = CfgFile.parse(SAMPLE);
        cfg.set("video_fullscreen", "true");
        cfg.set("new_key", "1");

        String expected =
                SAMPLE.replace("video_fullscreen = \"false\"", "video_fullscreen = \"true\"") + "new_key = \"1\"\n";
        assertEquals(expected, cfg.serialize());
    }

    @Test
    void roundTripsUnchangedFileExactly() {
        assertEquals(SAMPLE, CfgFile.parse(SAMPLE).serialize());
    }

    @Test
    void rejectsValuesRetroArchCantRead() {
        CfgFile cfg = CfgFile.empty();
        assertThrows(IllegalArgumentException.class, () -> cfg.set("a", "has \"quote\""));
        assertThrows(IllegalArgumentException.class, () -> cfg.set("a", "two\nlines"));
        assertThrows(IllegalArgumentException.class, () -> cfg.set("bad key", "x"));
    }

    @Test
    void savesAndLoads(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("retroarch.cfg");
        CfgFile cfg = CfgFile.load(file);
        cfg.set("menu_driver", "rgui");
        cfg.save(file);

        assertEquals("menu_driver = \"rgui\"\n", Files.readString(file));
        assertEquals("rgui", CfgFile.load(file).get("menu_driver", ""));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "temporary file should be gone");
        }
    }

    /** PERF-12: a file past the limit is refused before its content is read; one at the limit is read. */
    @Test
    void aFilePastTheLimitIsNotRead(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("big.cfg");
        Files.writeString(file, "a = \"1\"\n");
        long size = Files.size(file);
        assertEquals("a = \"1\"\n", Inputs.read(file, size));
        java.io.IOException e = assertThrows(java.io.IOException.class, () -> Inputs.read(file, size - 1));
        assertTrue(e.getMessage().contains("big.cfg"), e.getMessage());
        assertTrue(Inputs.CONFIG_LIMIT < Inputs.PLAYLIST_LIMIT);
        Files.write(file, new byte[(int) Inputs.CONFIG_LIMIT + 1]);
        assertThrows(java.io.IOException.class, () -> CfgFile.load(file), "retroarch.cfg goes through the limit");
    }
}
