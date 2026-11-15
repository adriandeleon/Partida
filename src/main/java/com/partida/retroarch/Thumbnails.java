package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Finds artwork in RetroArch's thumbnails folder:
 * {@code thumbnails/<System>/Named_Boxarts/<Game Label>.png}.
 *
 * <p>These come from the libretro-thumbnails project and are named after No-Intro / Redump
 * titles. A file called "Super Mario World (U) [!].smc" won't match unless a playlist gives it the
 * clean label "Super Mario World (USA)".
 */
public final class Thumbnails {

    public enum Kind {
        BOXART("Named_Boxarts"),
        SNAP("Named_Snaps"),
        TITLE("Named_Titles");

        final String folder;

        Kind(String folder) {
            this.folder = folder;
        }
    }

    /** Characters RetroArch replaces with '_' in thumbnail file names. */
    private static final String UNSAFE = "&*/:`<>?\\|";

    private final Path root;

    public Thumbnails(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public static String sanitize(String label) {
        StringBuilder sb = new StringBuilder(label.length());
        for (char c : label.toCharArray()) {
            sb.append(UNSAFE.indexOf(c) >= 0 ? '_' : c);
        }
        return sb.toString();
    }

    /** Tries the label first, then the file name without extension. */
    public Optional<Path> find(String system, String label, String fileStem, Kind kind) {
        if (system == null
                || system.isBlank()
                || system.contains("/")
                || system.contains("\\")
                || system.startsWith(".")) {
            return Optional.empty();
        }
        Path dir = root.resolve(system).resolve(kind.folder);
        Set<String> names = new LinkedHashSet<>();
        if (label != null && !label.isBlank()) {
            names.add(label);
        }
        if (fileStem != null && !fileStem.isBlank()) {
            names.add(fileStem);
        }
        for (String name : names) {
            Path p = dir.resolve(sanitize(name) + ".png");
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
