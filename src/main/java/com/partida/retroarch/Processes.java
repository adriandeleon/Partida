package com.partida.retroarch;

import java.util.ArrayList;
import java.util.List;

import com.partida.sync.Executables;
import com.partida.sync.ShellPath;

/**
 * Starts RetroArch and the tools around it (flatpak) as the user's terminal would (CLI-10): a bare program name
 * is looked up on the login shell's {@code PATH} and Homebrew's folders, and the process gets that {@code PATH},
 * so an app opened from the Finder or a desktop launcher finds the same {@code retroarch} a shell does. On
 * Windows, which has no login shell's {@code PATH}, the inherited one is used.
 */
final class Processes {

    private Processes() {}

    /** A builder for {@code command}, its program resolved and its {@code PATH} the login shell's. */
    static ProcessBuilder builder(List<String> command) {
        List<String> resolved = new ArrayList<>(command);
        if (!resolved.isEmpty() && isBareName(resolved.getFirst())) {
            resolved.set(0, Executables.find(resolved.getFirst()));
        }
        ProcessBuilder builder = new ProcessBuilder(resolved);
        builder.environment().put("PATH", ShellPath.path());
        return builder;
    }

    /** A program named with no folder: one the {@code PATH} finds. Pure. */
    static boolean isBareName(String program) {
        return !program.isEmpty() && program.indexOf('/') < 0 && program.indexOf('\\') < 0;
    }

    /** The {@code PATH} RetroArch is sought on: the login shell's (CLI-10). */
    static String path() {
        return ShellPath.path();
    }
}
