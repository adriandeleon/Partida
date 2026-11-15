package com.partida.ui;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.application.Platform;

import com.partida.Os;
import com.partida.command.CommandRegistry;
import com.partida.command.Keybindings;
import com.partida.command.Keymap;
import com.partida.config.ConfigStore;
import com.partida.config.Settings;
import com.partida.i18n.Messages;
import com.partida.sync.Git;
import com.partida.sync.GitHubCli;
import com.partida.sync.SyncEngine;
import com.partida.update.UpdateService;
import org.jspecify.annotations.Nullable;

/**
 * The app's long-lived parts, built once per process: config, commands, key bindings, the Message Log,
 * the update check and sync. Windows read from here; a settings change reaches all of them.
 */
public final class AppServices {

    private final ConfigStore store;
    private final Host host;
    private final CommandRegistry commands = new CommandRegistry();
    private final MessageLog messages = new MessageLog();
    private final UpdateCoordinator updates;
    private final SyncController sync;
    private final LibraryService library;
    private final List<Runnable> keybindingListeners = new CopyOnWriteArrayList<>();
    /** Messages echoed off the FX thread, drained there in batches (PERF-07). */
    private final FxBatcher<MessageLog.Entry> offThread =
            new FxBatcher<>(batch -> batch.forEach(e -> messages.add(e.text(), e.severity(), e.time())));

    private Keybindings keybindings;
    private String keymapState = "";

    public AppServices(ConfigStore store, Host host) {
        this(store, host, new UpdateService(), new GitHubCli());
    }

    private AppServices(ConfigStore store, Host host, UpdateService updates, GitHubCli gh) {
        // The sync engine asks gh which account can read the repository, and keeps it (SET-18).
        this(store, host, updates, new SyncEngine(store.dir().resolve("sync"), new Git(), gh), gh);
    }

    AppServices(ConfigStore store, Host host, UpdateService updateService, SyncEngine syncEngine, GitHubCli gitHub) {
        this.store = store;
        this.host = host;
        this.keybindings = build(store.settings());
        this.updates = new UpdateCoordinator(this, updateService::check);
        this.sync = new SyncController(this, syncEngine, gitHub);
        this.library = new LibraryService(this);
        this.syncEngine = syncEngine;
        this.gitHub = gitHub;
        store.addListener(settings -> {
            String state = settings.keymap + settings.keybindings;
            if (!state.equals(keymapState)) {
                keybindings = build(settings);
                keybindingListeners.forEach(Runnable::run);
            }
        });
    }

    private final SyncEngine syncEngine;
    private final GitHubCli gitHub;

    /** The Doctor's checks (DIAG-07): blocking, off the FX thread. */
    public java.util.List<com.partida.sync.Doctor.Check> doctor() {
        return com.partida.sync.Doctor.run(store.dir(), syncEngine.git(), gitHub);
    }

    private Keybindings build(Settings settings) {
        keymapState = settings.keymap + settings.keybindings;
        return new Keybindings(Keymap.load(settings.keymap), settings.keybindings, Os.isMac());
    }

    public ConfigStore store() {
        return store;
    }

    public Settings settings() {
        return store.settings();
    }

    public Host host() {
        return host;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public MessageLog messages() {
        return messages;
    }

    public UpdateCoordinator updates() {
        return updates;
    }

    public SyncController sync() {
        return sync;
    }

    /** The game library and the game running now. */
    LibraryService library() {
        return library;
    }

    public Keybindings keybindings() {
        return keybindings;
    }

    /** Runs whenever the keymap or the user's bindings change. */
    public void onKeybindingsChanged(Runnable listener) {
        keybindingListeners.add(listener);
    }

    /** Stops telling {@code listener} about keymap changes: an overlay that closed. */
    public void offKeybindingsChanged(Runnable listener) {
        keybindingListeners.remove(listener);
    }

    /** Shows a catalog message in the status bar and the Message Log; severity comes from the key. */
    public void echo(String key, @Nullable Object... args) {
        echoText(Messages.tr(key, args), MessageLog.severityOf(key));
    }

    /** Shows text in the status bar and the Message Log. Safe from any thread. */
    public void echoText(String text, MessageLog.Severity severity) {
        if (Platform.isFxApplicationThread()) {
            offThread.flush(); // an earlier message from another thread stays earlier
            messages.add(text, severity, LocalTime.now());
        } else {
            offThread.offer(new MessageLog.Entry(LocalTime.now(), text, severity));
        }
    }
}
