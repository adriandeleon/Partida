package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCatalogTest {

    static final String MGBA_INFO = """
            display_name = "Nintendo - Game Boy Advance (mGBA)"
            supported_extensions = "gb|gbc|GBA"
            corename = "mGBA"
            systemname = "Game Boy/Game Boy Color/Game Boy Advance"
            database = "Nintendo - Game Boy|Nintendo - Game Boy Color|Nintendo - Game Boy Advance"
            hw_render = "false"
            firmware_count = 2
            firmware0_desc = "gba_bios.bin (Game Boy Advance BIOS)"
            firmware0_path = "gba_bios.bin"
            firmware0_opt = "true"
            firmware1_desc = "required.bin"
            firmware1_path = "sub/required.bin"
            firmware1_opt = "false"
            """;

    @Test
    void loadsInstalledCoresWithTheirInfo(@TempDir Path dir) throws Exception {
        Path cores = Files.createDirectories(dir.resolve("cores"));
        Path info = Files.createDirectories(dir.resolve("info"));
        String ext = CoreCatalog.libraryExtension();
        Files.writeString(cores.resolve("mgba_libretro" + ext), "");
        Files.writeString(cores.resolve("nameless_libretro" + ext), "");
        Files.writeString(cores.resolve("readme.txt"), "");
        Files.writeString(info.resolve("mgba_libretro.info"), MGBA_INFO);
        Files.writeString(info.resolve("not_installed_libretro.info"), "display_name = \"Nope\"\n");

        CoreCatalog catalog = CoreCatalog.load(info, cores);

        // Sorted by display name; a core without an .info file falls back to its id.
        assertEquals(
                List.of("nameless_libretro", "mgba_libretro"),
                catalog.all().stream().map(CoreInfo::id).toList());
        CoreInfo mgba = catalog.byId("mgba_libretro").orElseThrow();
        assertEquals(List.of("gb", "gbc", "gba"), mgba.extensions());
        assertTrue(mgba.supports("GBA"));
        assertEquals(3, mgba.databases().size());
        assertEquals(2, mgba.firmware().size());
        assertEquals(List.of(mgba), catalog.forExtension("gba"));
        assertEquals(
                mgba, catalog.byLibrary(cores.resolve("mgba_libretro" + ext)).orElseThrow());
        assertEquals(
                "nameless_libretro",
                catalog.byId("nameless_libretro").orElseThrow().label());
    }

    @Test
    void reportsOnlyMissingRequiredFirmware(@TempDir Path system) throws Exception {
        CoreInfo core = CoreInfo.fromInfo("mgba_libretro", CfgFile.parse(MGBA_INFO), Path.of("x.so"));
        assertEquals(
                List.of("sub/required.bin"),
                core.missingFirmware(system).stream()
                        .map(CoreInfo.Firmware::path)
                        .toList());

        Files.createDirectories(system.resolve("sub"));
        Files.writeString(system.resolve("sub/required.bin"), "");
        assertFalse(!core.missingFirmware(system).isEmpty());
    }
}
