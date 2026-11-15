package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetroArchInstallTest {

    private final Path home = Path.of("/home/player");
    private final Path config = home.resolve(".var/app/org.libretro.RetroArch/config/retroarch");
    private final Path flatpakFiles = Path.of("/var/lib/flatpak/app/org.libretro.RetroArch/x86_64/stable/abc/files");
    private final RetroArchInstall flatpak = new RetroArchInstall(
            RetroArchInstall.Kind.FLATPAK,
            config,
            flatpakFiles,
            List.of("flatpak", "run", RetroArchInstall.FLATPAK_ID),
            home);

    @Test
    void resolvesHomeRelativePaths() {
        assertEquals(home.resolve("roms/snes"), flatpak.resolve("~/roms/snes"));
        assertEquals(home, flatpak.resolve("~"));
    }

    @Test
    void mapsFlatpakAppPathsBothWays() {
        Path info = flatpak.resolve("/app/share/libretro/info");
        assertEquals(flatpakFiles.resolve("share/libretro/info"), info);
        assertEquals("/app/share/libretro/info", flatpak.toRetroArchPath(info));
        assertEquals("/media/roms/game.sfc", flatpak.toRetroArchPath(Path.of("/media/roms/game.sfc")));
    }

    @Test
    void nativeInstallLeavesAppPathsAlone() {
        RetroArchInstall nativeInstall = new RetroArchInstall(
                RetroArchInstall.Kind.NATIVE,
                home.resolve(".config/retroarch"),
                null,
                List.of("/usr/bin/retroarch"),
                home);
        assertEquals(Path.of("/app/share"), nativeInstall.resolve("/app/share"));
    }

    @Test
    void portablePathsAreRelativeToTheConfigFolder() {
        assertEquals(config.resolve("cores"), flatpak.resolve(":/cores"));
    }

    @Test
    void directoriesFallBackWhenSetToDefault(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("playlists"));
        RetroArchInstall install = RetroArchInstall.custom(dir, List.of("retroarch"), dir);
        CfgFile cfg = CfgFile.parse("playlist_directory = \"default\"\nthumbnails_directory = \"/srv/art\"\n");
        assertEquals(dir.resolve("playlists"), install.playlistsDir(cfg));
        assertEquals(Path.of("/srv/art"), install.thumbnailsDir(cfg));
        assertEquals(dir.resolve("cores"), install.coresDir(cfg));
    }
}
