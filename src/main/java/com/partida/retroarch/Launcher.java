package com.partida.retroarch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/** Starts RetroArch with a core and a game: {@code retroarch -L <core> [--appendconfig a|b] <game>}. */
public final class Launcher {

    private final RetroArchInstall install;
    private final Path logFile;
    private volatile @Nullable Process current;

    public Launcher(RetroArchInstall install, Path logFile) {
        this.install = install;
        this.logFile = logFile;
    }

    /**
     * @param appendConfigs extra .cfg files applied on top of retroarch.cfg for this launch only
     */
    public Process launch(Path core, Path content, List<Path> appendConfigs) throws IOException {
        List<String> cmd = buildCommand(core, content, appendConfigs);
        Files.createDirectories(logFile.getParent());
        ProcessBuilder pb = Processes.builder(cmd) // the login shell's PATH (CLI-10)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        current = pb.start();
        return current;
    }

    /**
     * Starts RetroArch with its output (stdout and stderr together) on the process's input stream, for a
     * console to show as it runs. Whoever starts it reads that stream, or RetroArch blocks once the pipe fills.
     */
    public Process start(Path core, Path content, List<Path> appendConfigs) throws IOException {
        current = Processes.builder(buildCommand(core, content, appendConfigs)) // the login shell's PATH (CLI-10)
                .redirectErrorStream(true)
                .start();
        return current;
    }

    List<String> buildCommand(Path core, Path content, List<Path> appendConfigs) {
        List<String> cmd = new ArrayList<>(install.command());
        cmd.add("-L");
        cmd.add(install.toRetroArchPath(core));
        if (!appendConfigs.isEmpty()) {
            cmd.add("--appendconfig");
            cmd.add(appendConfigs.stream().map(install::toRetroArchPath).collect(Collectors.joining("|")));
        }
        cmd.add(install.toRetroArchPath(content));
        return cmd;
    }

    public Path logFile() {
        return logFile;
    }

    /** The RetroArch process this app started, if it's still running. */
    public Optional<Process> current() {
        Process p = current;
        return p != null && p.isAlive() ? Optional.of(p) : Optional.empty();
    }

    /**
     * Whether any RetroArch is running, including one the user started themselves. RetroArch rewrites
     * retroarch.cfg when it exits, so settings shouldn't be saved while this is true.
     */
    public static boolean isRetroArchRunning() {
        return ProcessHandle.allProcesses()
                .anyMatch(ph ->
                        ph.info().command().map(Launcher::looksLikeRetroArch).orElse(false));
    }

    static boolean looksLikeRetroArch(String command) {
        try {
            Path name = Path.of(command).getFileName();
            if (name == null) {
                return false;
            }
            String n = name.toString().toLowerCase(Locale.ROOT);
            return n.equals("retroarch") || n.equals("retroarch.exe");
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
