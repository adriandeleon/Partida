package com.partida.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.partida.library.GameCollection;

import static com.partida.ui.FxTestSupport.call;
import static com.partida.ui.FxTestSupport.run;
import static com.partida.ui.FxTestSupport.waitUntil;

/**
 * A RetroArch install laid out in a temporary folder for the FX tests: eight systems, each a playlist of a few
 * games, and no cores. The tests point Settings → Library at it, so none reads the developer's own RetroArch
 * ({@code partida.retroarch=off} in the pom keeps finding one on its own off).
 */
final class FakeLibrary {

    /** The systems, in the order the Systems window lists them (by name). */
    static final List<String> SYSTEMS = List.of(
            "Atari - 2600",
            "NEC - PC Engine - TurboGrafx 16",
            "Nintendo - Game Boy",
            "Nintendo - Game Boy Advance",
            "Nintendo - Nintendo Entertainment System",
            "Nintendo - Super Nintendo Entertainment System",
            "Sega - Master System - Mark III",
            "Sega - Mega Drive - Genesis");

    /** The one core's name. */
    static final String CORE = "Fake (Test)";

    /** How many games each system holds: the n-th system has n + 2. */
    static int games(int system) {
        return system + 2;
    }

    private FakeLibrary() {}

    /** Writes the install under {@code root}; returns its config folder (the one holding retroarch.cfg). */
    static Path install(Path root) throws IOException {
        Path config = root.resolve("retroarch");
        Path playlists = Files.createDirectories(config.resolve("playlists"));
        Files.writeString(config.resolve("retroarch.cfg"), "menu_driver = \"ozone\"\n");
        // One core, for every game's .bin: Play has something to start with.
        Path cores = Files.createDirectories(config.resolve("cores"));
        Files.writeString(cores.resolve("fake_libretro" + com.partida.retroarch.CoreCatalog.libraryExtension()), "");
        Files.writeString(
                Files.createDirectories(config.resolve("info")).resolve("fake_libretro.info"),
                "display_name = \"" + CORE + "\"\nsupported_extensions = \"bin\"\n");
        Files.writeString(config.resolve("retroarch.cfg"), "libretro_info_path = \"" + config.resolve("info") + "\"\n");
        for (int s = 0; s < SYSTEMS.size(); s++) {
            String system = SYSTEMS.get(s);
            List<String> items = new ArrayList<>();
            for (int g = 1; g <= games(s); g++) {
                Path rom = root.resolve("roms").resolve(String.valueOf(s)).resolve("Game " + g + ".bin");
                items.add("{\"path\": \"" + json(rom.toString()) + "\", \"label\": \"" + json(title(s, g))
                        + "\", \"core_path\": \"DETECT\"}");
            }
            Files.writeString(playlists.resolve(system + ".lpl"), "{\"items\": [" + String.join(",", items) + "]}");
        }
        return config;
    }

    /** The g-th game of system s: "Game g of <short name>", where the short name is the system's last part. */
    static String title(int system, int game) {
        String name = SYSTEMS.get(system);
        return "Game " + game + " of " + name.substring(name.lastIndexOf(" - ") + 3);
    }

    private static String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * A stand-in RetroArch for Play: a shell script that prints its arguments, then waits until it is ended.
     * Returns the launch command for Settings → Library.
     */
    static String fakeRetroArch(Path root) throws IOException {
        Path script = root.resolve("fake-retroarch.sh");
        Files.writeString(script, "echo started \"$@\"\nsleep 30\n");
        return "/bin/sh " + script;
    }

    /** Points Settings → Library at the install under {@code root} and waits for the library to load. */
    static void load(AppServices services, Path root) throws Exception {
        Path config = install(root);
        run(() -> {
            services.store().update(s -> s.library.retroarchConfigDir = config.toString());
            services.library().start();
            services.library().reload();
        });
        waitLoaded(services);
    }

    /** Waits for the library's latest load to land. */
    static void waitLoaded(AppServices services) throws Exception {
        waitUntil(() ->
                !services.library().loading() && services.library().state().status() != LibraryService.Status.LOADING);
    }

    /** The loaded collection called {@code name}. */
    static GameCollection collection(AppServices services, String name) throws Exception {
        GameCollection found = call(() -> services.library().state().collection(name));
        if (found == null) throw new AssertionError("no system " + name);
        return found;
    }

    /** The loaded collection at {@code index} of {@link #SYSTEMS}. */
    static GameCollection collection(AppServices services, int index) throws Exception {
        return collection(services, SYSTEMS.get(index));
    }
}
