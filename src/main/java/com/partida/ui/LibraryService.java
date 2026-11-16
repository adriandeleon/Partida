package com.partida.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import com.partida.AppInfo;
import com.partida.config.Settings;
import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameCollection;
import com.partida.library.LibraryFolder;
import com.partida.library.LibraryLoader;
import com.partida.library.RetroArchContext;
import com.partida.retroarch.CoreInfo;
import com.partida.retroarch.Launcher;
import com.partida.retroarch.NetworkCommands;
import com.partida.retroarch.RetroArchInstall;
import org.jspecify.annotations.Nullable;

/**
 * The library and the game running now. RetroArch and the games are read off the FX thread, and only the latest
 * load lands (PERF-01, PERF-03); a change to Settings → Library loads again. A game's output reaches the
 * RetroArch window in batches (PERF-07), read on a daemon thread of its own while the game runs.
 *
 * <p>The first load waits for the first frame (PERF-14): {@link #start()} is called after it. The tests turn
 * finding RetroArch on its own off ({@value #DETECT_PROPERTY} {@code = off}), so no test reads the developer's
 * own RetroArch; a test that needs one points Settings → Library at a fake install in a temporary folder.
 */
final class LibraryService {

    /** Where loading stands. */
    enum Status {
        /** Not loaded yet, or loading again. */
        LOADING,
        READY,
        /** No RetroArch found, or none chosen. */
        NOT_FOUND,
        FAILED
    }

    /** One load's outcome: RetroArch, if found, and the games read from it. */
    record State(Status status, @Nullable RetroArchContext retroArch, LibraryLoader.Library library, String detail) {

        static final LibraryLoader.Library EMPTY = new LibraryLoader.Library(List.of(), List.of());

        static State of(Status status) {
            return new State(status, null, EMPTY, "");
        }

        /** The collection called {@code name}, or null. */
        @Nullable
        GameCollection collection(String name) {
            for (GameCollection c : library.collections()) {
                if (c.name().equals(name)) return c;
            }
            return null;
        }
    }

    /** {@code off} turns finding RetroArch on its own off: the tests set it. */
    static final String DETECT_PROPERTY = AppInfo.ID + ".retroarch";

    private static final Logger LOG = Logger.getLogger(LibraryService.class.getName());

    private final AppServices services;
    private final LatestOnly loads = new LatestOnly(Background::run, Platform::runLater);
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Consumer<List<String>>> outputListeners = new CopyOnWriteArrayList<>();
    private final FxBatcher<String> output = new FxBatcher<>(lines -> outputListeners.forEach(l -> l.accept(lines)));
    /** Stop was asked between Play and RetroArch's process starting. FX thread. */
    private boolean stopRequested;

    private final ReadOnlyBooleanWrapper playing = new ReadOnlyBooleanWrapper(this, "playing");

    private State state = State.of(Status.LOADING);
    /** The library settings the current state was loaded from: a change to them loads again. */
    private String source = "";

    private boolean started;
    private @Nullable Process process;
    private @Nullable Game running;

    LibraryService(AppServices services) {
        this.services = services;
        services.store().addListener(settings -> {
            if (started && !fingerprint(settings.library).equals(source)) reload();
        });
    }

    /**
     * The first load, after the first frame (PERF-14); a settings change loads again from then on. It says
     * nothing when there is no RetroArch: the Systems window, the Welcome page and Settings already say why.
     */
    void start() {
        if (started) return;
        started = true;
        load(false);
    }

    /** Reads RetroArch and the library again, off the FX thread; the last state stays until the new one lands. */
    void reload() {
        load(true);
    }

    private void load(boolean announceNotFound) {
        Settings.Library library = services.settings().library.copy();
        source = fingerprint(library);
        boolean detect = detects();
        state = new State(Status.LOADING, state.retroArch(), state.library(), "");
        notifyListeners();
        loads.submit(() -> load(library, detect), loaded -> loaded(loaded, announceNotFound));
    }

    private void loaded(State loaded, boolean announceNotFound) {
        state = loaded;
        for (String warning : loaded.library().warnings()) LOG.warning(warning);
        switch (loaded.status()) {
            case READY -> {
                int systems = (int) loaded.library().collections().stream()
                        .filter(c -> !c.name().equals(LibraryLoader.RECENTLY_PLAYED))
                        .count();
                services.echo("status.libraryLoaded", loaded.library().gameCount(), systems);
                if (!loaded.library().warnings().isEmpty()) {
                    services.echo(
                            "status.libraryCannotReadAll",
                            loaded.library().warnings().size());
                }
            }
            case NOT_FOUND -> {
                if (announceNotFound) services.echo("status.retroarchCannotFind");
            }
            case FAILED -> services.echo("status.libraryLoadFailed", loaded.detail());
            case LOADING -> {
                // A load never ends LOADING.
            }
        }
        notifyListeners();
    }

    /** Whether RetroArch may be sought on its own: off in the tests ({@value #DETECT_PROPERTY} = off). */
    static boolean detects() {
        return !"off".equals(System.getProperty(DETECT_PROPERTY));
    }

    /** Where to get RetroArch: the Doctor's link when it isn't found. */
    static final String RETROARCH_DOWNLOAD = "https://www.retroarch.com/?page=platforms";

    /**
     * The Doctor's RetroArch row (DIAG-07): found, with its folder and how many cores; found with no cores, which
     * plays nothing; not found, with where to get it; or unreadable, with what was wrong. Blocking, off the FX
     * thread; it reads RetroArch's config and cores but scans no games folder.
     */
    static com.partida.sync.Doctor.Check doctorCheck(Settings.Library library, boolean detect) {
        String name = "RetroArch";
        try {
            Optional<RetroArchInstall> install = library.retroarchConfigDir.isBlank() && !detect
                    ? Optional.empty()
                    : RetroArchInstall.locate(library.retroarchConfigDir, library.commandWords());
            if (install.isEmpty()) {
                return new com.partida.sync.Doctor.Check(
                        name,
                        com.partida.sync.Doctor.Status.FAILED,
                        Messages.tr("doctor.notFound"),
                        Messages.tr("doctor.retroarch.fix"),
                        RETROARCH_DOWNLOAD);
            }
            RetroArchContext ctx = RetroArchContext.load(install.get());
            String where = AppInfo.tildeHome(ctx.install().configDir().toString());
            int cores = ctx.cores().all().size();
            if (cores == 0) {
                return new com.partida.sync.Doctor.Check(
                        name,
                        com.partida.sync.Doctor.Status.WARNING,
                        where,
                        Messages.tr("doctor.retroarch.noCores"),
                        null);
            }
            return new com.partida.sync.Doctor.Check(
                    name,
                    com.partida.sync.Doctor.Status.OK,
                    Messages.tr("settings.library.status.found", where, cores),
                    null,
                    null);
        } catch (IOException | RuntimeException e) {
            return new com.partida.sync.Doctor.Check(
                    name,
                    com.partida.sync.Doctor.Status.FAILED,
                    String.valueOf(e.getMessage()),
                    Messages.tr("doctor.retroarch.unreadable"),
                    null);
        }
    }

    /** Reads one library: blocking, off the FX thread. Pure apart from the disk. */
    static State load(Settings.Library library, boolean detect) {
        try {
            // Inside the try: whatever a hand-edited path makes the lookup throw fails this load, never the
            // loader, so the library can't stay loading (SET-11).
            Optional<RetroArchInstall> install = library.retroarchConfigDir.isBlank() && !detect
                    ? Optional.empty()
                    : RetroArchInstall.locate(library.retroarchConfigDir, library.commandWords());
            if (install.isEmpty()) return State.of(Status.NOT_FOUND);
            RetroArchContext ctx = RetroArchContext.load(install.get());
            List<LibraryFolder> folders = library.folders.stream()
                    .map(f -> new LibraryFolder(f.path, f.coreId.isBlank() ? null : f.coreId, null))
                    .toList();
            return new State(Status.READY, ctx, LibraryLoader.load(ctx, folders, library.showRecentlyPlayed), "");
        } catch (IOException | RuntimeException e) {
            return new State(Status.FAILED, null, State.EMPTY, String.valueOf(e.getMessage()));
        }
    }

    /** What a load depends on, as one string: a settings change that leaves it alone loads nothing. */
    static String fingerprint(Settings.Library library) {
        StringBuilder key = new StringBuilder()
                .append(library.retroarchConfigDir)
                .append('\n')
                .append(library.retroarchCommand)
                .append('\n')
                .append(library.showRecentlyPlayed);
        for (Settings.Library.Folder f : library.folders)
            key.append('\n').append(f.path).append('\t').append(f.coreId);
        return key.toString();
    }

    State state() {
        return state;
    }

    /** Runs {@code listener} on the FX thread whenever the state changes. */
    void onChanged(Runnable listener) {
        listeners.add(listener);
    }

    /** Stops running {@code listener}: an open picker's, once it closes. */
    void offChanged(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    /** Whether a load is still running: the tests wait on it. */
    boolean loading() {
        return loads.pending();
    }

    // ---- the game running now ----

    /** Receives each batch of lines RetroArch prints while a game runs. */
    void onOutput(Consumer<List<String>> listener) {
        outputListeners.add(listener);
    }

    ReadOnlyBooleanProperty playingProperty() {
        return playing.getReadOnlyProperty();
    }

    boolean isPlaying() {
        return playing.get();
    }

    @Nullable
    Game running() {
        return running;
    }

    /** Why {@code game} can't start now, as a palette reason, or null when it can (the command's unavailable). */
    @Nullable
    String playUnavailable(@Nullable Game game) {
        return Chrome.playUnavailable(state.retroArch() != null, game != null, isPlaying());
    }

    /**
     * Starts {@code game} in RetroArch with {@code chosen}, or the core it would pick. The game counts as playing
     * at once, so a second Play waits; choosing the core and starting the process run off the FX thread
     * (PERF-01). Says in the status bar when it can't: no RetroArch, no core for the file, or a game running.
     */
    void play(Game game, @Nullable CoreInfo chosen) {
        RetroArchContext ctx = state.retroArch();
        if (ctx == null) {
            services.echo("status.retroarchCannotFind");
            return;
        }
        if (isPlaying()) {
            services.echo("status.gameCannotStartRunning");
            return;
        }
        running = game;
        playing.set(true);
        Path log = services.store().dir().resolve("retroarch.log");
        Background.run(() -> {
            CoreInfo core = chosen != null ? chosen : ctx.preferredCore(game).orElse(null);
            if (core == null) {
                services.echo("status.gameCannotFindCore", game.extension());
                Platform.runLater(() -> notStarted(game));
                return;
            }
            Process started;
            try {
                started = new Launcher(ctx.install(), log).start(core.library(), game.rom(), List.of());
            } catch (IOException e) {
                services.echo("status.gameStartFailed", String.valueOf(e.getMessage()));
                Platform.runLater(() -> notStarted(game));
                return;
            }
            Platform.runLater(() -> {
                process = started;
                // Stop asked while RetroArch was starting: it ends now that there is a process to end.
                if (stopRequested) {
                    stopRequested = false;
                    stop();
                }
            });
            output.offer("▶ " + game.title() + " · " + core.label());
            output.offer("$ " + ctx.install().commandLine() + " -L " + core.library() + " " + game.rom());
            services.echo("status.gameStarted", game.title(), core.label());
            started.onExit().thenAccept(done -> Platform.runLater(() -> finished(done, game)));
            pump(started); // this task's own virtual thread reads the output until RetroArch exits
        });
    }

    private void notStarted(Game game) {
        if (running != game || process != null) return;
        stopRequested = false;
        running = null;
        playing.set(false);
    }

    /** Copies RetroArch's output to the console, a line at a time, until it exits. */
    private void pump(Process p) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.offer(line);
        } catch (IOException e) {
            LOG.fine("RetroArch's output closed: " + e.getMessage());
        }
    }

    private void finished(Process done, Game game) {
        if (running != game) return;
        process = null;
        stopRequested = false;
        running = null;
        output.flush();
        playing.set(false);
        int code = done.exitValue();
        output.offer("■ " + game.title() + " · " + code);
        if (code == 0) services.echo("status.gameEnded", game.title());
        else services.echo("status.gameExitError", game.title(), code);
    }

    /**
     * Quits the running game: RetroArch's own QUIT when network commands are on, so it saves on its way out; else
     * the process is ended. Off the FX thread (PERF-01).
     */
    void stop() {
        Process p = process;
        if (p == null) {
            // Play has begun but RetroArch isn't started yet: stop it as soon as it is.
            if (isPlaying()) stopRequested = true;
            return;
        }
        RetroArchContext ctx = state.retroArch();
        Background.run(() -> {
            if (ctx != null && NetworkCommands.enabledIn(ctx.config())) {
                try {
                    NetworkCommands.fromConfig(ctx.config()).send("QUIT");
                    return;
                } catch (IOException e) {
                    LOG.info("RetroArch didn't take QUIT, ending it: " + e.getMessage());
                }
            }
            p.destroy();
        });
    }

    /**
     * Why there is no game to stop, or null when one runs or is starting: Stop follows {@code playing}, the
     * state the toolbar refreshes on (TBAR-17), never the process, which comes a moment later.
     */
    @Nullable
    String stopUnavailable() {
        return Chrome.stopUnavailable(isPlaying());
    }
}
