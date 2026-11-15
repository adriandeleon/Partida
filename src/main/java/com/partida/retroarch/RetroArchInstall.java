package com.partida.retroarch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Where RetroArch lives on this machine and how to start it.
 *
 * <p>Paths inside retroarch.cfg are written from RetroArch's point of view. They can start with {@code ~},
 * with {@code :} (portable installs: relative to RetroArch's own folder), or, for the Flatpak, with
 * {@code /app/}, which only exists inside the sandbox. {@link #resolve(String)} turns them into paths this
 * app can open, and {@link #toRetroArchPath(Path)} goes the other way.
 */
public final class RetroArchInstall {

    public static final String FLATPAK_ID = "org.libretro.RetroArch";

    /** How RetroArch was installed: what {@link #describe()} says and whether {@code /app/} paths map. */
    public enum Kind {
        NATIVE,
        FLATPAK,
        CUSTOM
    }

    private final Kind kind;
    private final Path configDir;
    private final @Nullable Path flatpakFilesDir;
    private final List<String> command;
    private final Path home;

    public RetroArchInstall(
            Kind kind, Path configDir, @Nullable Path flatpakFilesDir, List<String> command, Path home) {
        if (command.isEmpty()) throw new IllegalArgumentException("A launch command needs at least a program");
        this.kind = kind;
        this.configDir = configDir;
        this.flatpakFilesDir = flatpakFilesDir;
        this.command = List.copyOf(command);
        this.home = home;
    }

    /** An install the user pointed at: its config folder and the command that starts it. */
    public static RetroArchInstall custom(Path configDir, List<String> command, Path home) {
        return new RetroArchInstall(Kind.CUSTOM, configDir, null, command, home);
    }

    /**
     * The user's own choice when they made one ({@code configDir} not blank), else what {@link #detect} finds
     * on this machine.
     */
    public static Optional<RetroArchInstall> locate(@Nullable String configDir, List<String> command) {
        Path home = Path.of(System.getProperty("user.home"));
        if (configDir != null && !configDir.isBlank()) {
            return Optional.of(
                    custom(Path.of(configDir.strip()), command.isEmpty() ? List.of("retroarch") : command, home));
        }
        return detect(
                home, System.getenv("PATH"), System.getenv("XDG_CONFIG_HOME"), RetroArchInstall::findFlatpakFilesDir);
    }

    /**
     * Looks for a native install first (a {@code retroarch} on {@code path} and its config folder), then the
     * Flatpak's config folder, then a native install that has never run. Pure apart from reading the disk.
     *
     * @param flatpakFiles the host path of the Flatpak's {@code /app}, asked only when the Flatpak is found
     */
    static Optional<RetroArchInstall> detect(
            Path home, @Nullable String path, @Nullable String xdgConfigHome, Supplier<Optional<Path>> flatpakFiles) {
        Optional<Path> exe = findOnPath("retroarch", path);
        Path configHome =
                xdgConfigHome != null && !xdgConfigHome.isBlank() ? Path.of(xdgConfigHome) : home.resolve(".config");
        Path nativeDir = configHome.resolve("retroarch");
        if (exe.isPresent() && Files.isRegularFile(nativeDir.resolve("retroarch.cfg"))) {
            return Optional.of(new RetroArchInstall(
                    Kind.NATIVE, nativeDir, null, List.of(exe.get().toString()), home));
        }
        Path flatpakDir = home.resolve(".var/app/" + FLATPAK_ID + "/config/retroarch");
        if (Files.isRegularFile(flatpakDir.resolve("retroarch.cfg"))) {
            return Optional.of(new RetroArchInstall(
                    Kind.FLATPAK,
                    flatpakDir,
                    flatpakFiles.get().orElse(null),
                    List.of("flatpak", "run", FLATPAK_ID),
                    home));
        }
        return exe.map(p -> new RetroArchInstall(Kind.NATIVE, nativeDir, null, List.of(p.toString()), home));
    }

    public Kind kind() {
        return kind;
    }

    public Path configDir() {
        return configDir;
    }

    public Path configFile() {
        return configDir.resolve("retroarch.cfg");
    }

    public List<String> command() {
        return command;
    }

    /** The command as one line, for Settings and the Debug Log. */
    public String commandLine() {
        return String.join(" ", command);
    }

    /** Turns a path as written in retroarch.cfg or a playlist into a path this app can open. */
    public Path resolve(String raw) {
        String v = raw.strip();
        if (v.equals("~") || v.startsWith("~/")) {
            return home.resolve(v.length() > 2 ? v.substring(2) : "");
        }
        if (v.startsWith(":/") || v.startsWith(":\\")) {
            return configDir.resolve(v.substring(2));
        }
        Path files = flatpakFilesDir;
        if (kind == Kind.FLATPAK && files != null && (v.equals("/app") || v.startsWith("/app/"))) {
            return files.resolve(v.length() > 5 ? v.substring(5) : "");
        }
        return Path.of(v);
    }

    /** Turns a path on this machine into the path RetroArch sees. Only differs for the Flatpak's /app. */
    public String toRetroArchPath(Path p) {
        Path files = flatpakFilesDir;
        if (kind == Kind.FLATPAK && files != null && p.startsWith(files)) {
            return "/app/" + files.relativize(p);
        }
        return p.toString();
    }

    public Path coresDir(CfgFile cfg) {
        return dir(cfg, "libretro_directory", List.of(configDir.resolve("cores")));
    }

    public Path infoDir(CfgFile cfg) {
        List<Path> fallbacks = new ArrayList<>(List.of(configDir.resolve("info"), configDir.resolve("cores")));
        Path files = flatpakFilesDir;
        if (files != null) fallbacks.add(files.resolve("share/libretro/info"));
        fallbacks.add(Path.of("/usr/share/libretro/info"));
        fallbacks.add(Path.of("/usr/lib/libretro"));
        return dir(cfg, "libretro_info_path", fallbacks);
    }

    public Path playlistsDir(CfgFile cfg) {
        return dir(cfg, "playlist_directory", List.of(configDir.resolve("playlists")));
    }

    public Path thumbnailsDir(CfgFile cfg) {
        return dir(cfg, "thumbnails_directory", List.of(configDir.resolve("thumbnails")));
    }

    public Path systemDir(CfgFile cfg) {
        return dir(cfg, "system_directory", List.of(configDir.resolve("system")));
    }

    private Path dir(CfgFile cfg, String key, List<Path> fallbacks) {
        String v = cfg.get(key, "");
        if (!v.isBlank() && !v.equals("default")) return resolve(v);
        return fallbacks.stream().filter(Files::isDirectory).findFirst().orElse(fallbacks.getFirst());
    }

    static Optional<Path> findOnPath(String name, @Nullable String path) {
        if (path == null) return Optional.empty();
        for (String dir : path.split(File.pathSeparator, -1)) {
            if (dir.isBlank()) continue;
            for (String candidate : List.of(name, name + ".exe")) {
                Path p = Path.of(dir, candidate);
                if (Files.isRegularFile(p) && Files.isExecutable(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Host path of the Flatpak's /app, e.g. /var/lib/flatpak/app/org.libretro.RetroArch/.../files. */
    static Optional<Path> findFlatpakFilesDir() {
        try {
            Process p = new ProcessBuilder("flatpak", "info", "--show-location", FLATPAK_ID)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!p.waitFor(5, TimeUnit.SECONDS) || p.exitValue() != 0 || out.isEmpty()) return Optional.empty();
            Path files = Path.of(out).resolve("files");
            return Files.isDirectory(files) ? Optional.of(files) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
