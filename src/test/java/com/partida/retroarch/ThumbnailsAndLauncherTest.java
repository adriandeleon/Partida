package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailsAndLauncherTest {

    @Test
    void sanitizesLikeRetroArch() {
        assertEquals("Q_bert _ Tom _ Jerry_", Thumbnails.sanitize("Q*bert / Tom & Jerry?"));
        assertEquals("Mario _ Luigi_ Superstar Saga", Thumbnails.sanitize("Mario & Luigi: Superstar Saga"));
    }

    @Test
    void findsBoxartByLabelThenFileName(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("Nintendo - Game Boy Advance/Named_Boxarts"));
        Files.writeString(dir.resolve("Mario _ Luigi_ Superstar Saga (USA).png"), "");
        Files.writeString(dir.resolve("my-rom.png"), "");
        Thumbnails t = new Thumbnails(root);

        assertTrue(t.find(
                        "Nintendo - Game Boy Advance",
                        "Mario & Luigi: Superstar Saga (USA)",
                        "x",
                        Thumbnails.Kind.BOXART)
                .isPresent());
        assertTrue(t.find("Nintendo - Game Boy Advance", "No Match", "my-rom", Thumbnails.Kind.BOXART)
                .isPresent());
        assertFalse(t.find("", "Anything", "my-rom", Thumbnails.Kind.BOXART).isPresent());
        assertFalse(t.find("../escape", "Anything", "my-rom", Thumbnails.Kind.BOXART)
                .isPresent());
    }

    @Test
    void buildsLaunchCommandInRetroArchPaths() {
        Path files = Path.of("/flatpak/files");
        RetroArchInstall install = new RetroArchInstall(
                RetroArchInstall.Kind.FLATPAK,
                Path.of("/cfg"),
                files,
                List.of("flatpak", "run", RetroArchInstall.FLATPAK_ID),
                Path.of("/home/p"));
        Launcher launcher = new Launcher(install, Path.of("/tmp/log"));

        List<String> cmd = launcher.buildCommand(
                files.resolve("lib/libretro/core.so"),
                Path.of("/roms/My Game.gba"),
                List.of(Path.of("/cfg/extra.cfg"), Path.of("/cfg/more.cfg")));

        assertEquals(
                List.of(
                        "flatpak",
                        "run",
                        "org.libretro.RetroArch",
                        "-L",
                        "/app/lib/libretro/core.so",
                        "--appendconfig",
                        "/cfg/extra.cfg|/cfg/more.cfg",
                        "/roms/My Game.gba"),
                cmd);
    }

    @Test
    void recognizesRetroArchProcesses() {
        assertTrue(Launcher.looksLikeRetroArch("/app/bin/retroarch"));
        assertTrue(Launcher.looksLikeRetroArch("C:\\RetroArch\\retroarch.exe".replace('\\', '/')));
        assertFalse(Launcher.looksLikeRetroArch("/usr/bin/retroarch-assets-helper"));
        assertFalse(Launcher.looksLikeRetroArch("/usr/bin/java"));
    }
}
