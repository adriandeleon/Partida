package com.partida.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * Preferences, stored as {@code settings.json} in the config folder.
 *
 * <p>A plain Jackson POJO with public fields: the file is the documentation. Adding a setting touches
 * five places (docs/conventions.md): this field and its default with a {@link #SCHEMA_VERSION} bump and
 * a {@link Migrations} step, the control, its row on a Settings page, the line that loads the control,
 * and a palette command, plus its strings in every catalog.
 */
public final class Settings {

    /** Bump with every change to the shape of this file, and add the matching {@link Migrations} step. */
    public static final int SCHEMA_VERSION = 9;

    public int schemaVersion = SCHEMA_VERSION;

    public ThemeChoice theme = ThemeChoice.SYSTEM;

    /** A bundled language code, or blank to follow the system. */
    public String language = "";

    /** The base keymap: {@code cua} or {@code emacs}. */
    public String keymap = "cua";

    /** The user's own bindings, command id to chord, over the base keymap. A blank chord unbinds. */
    public Map<String, String> keybindings = new TreeMap<>();

    /** The daily update check. Off means no automatic network call at all. */
    public boolean checkForUpdates = true;

    /** When the update check last ran, epoch milliseconds; 0 for never. */
    public long lastUpdateCheck;

    /** The main toolbar under the menus (TBAR-18). */
    public boolean showToolbar = true;

    public boolean showStatusBar = true;

    /** The strip of document tabs (TABS-12). */
    public boolean showTabBar = true;

    /**
     * The menus and the title in one bar drawn by the app (JavaFX's {@code HeaderBar}), where the
     * platform supports it and the menus are in the window (not macOS). Applies on the next start.
     */
    public boolean titleBarMenus = true;

    /**
     * Simple mode (MENU-04, TBAR-16): a shorter menu bar and fewer toolbar groups. It hides, it never
     * changes another preference, so turning it off brings back the window as it was.
     */
    public boolean simpleMode;

    /**
     * The main toolbar's buttons in order (TBAR-09), command ids with {@code "|"} for a gap between groups;
     * empty for the app's own layout. The toolbar drops what it does not know when it reads it.
     */
    public List<String> toolbarLayout = new ArrayList<>();

    /** At most this many entries are kept: a hand-edited file can't make a bar of a thousand buttons. */
    public static final int MAX_TOOLBAR_ENTRIES = 64;

    /** The UI zoom (WIN-12), in percent: it scales the whole chrome, the menus included. */
    public int uiZoom = DEFAULT_UI_ZOOM;

    /** The UI zoom's default and its range, in percent. */
    public static final int DEFAULT_UI_ZOOM = 100;

    public static final int MIN_UI_ZOOM = 80;

    public static final int MAX_UI_ZOOM = 200;

    /** {@code percent} within the UI zoom's range. */
    public static int clampUiZoom(int percent) {
        return Math.min(MAX_UI_ZOOM, Math.max(MIN_UI_ZOOM, percent));
    }

    /**
     * Session state, not a preference (SET-08): where the window was and what was open. Kept in
     * {@code session.json} beside {@code settings.json}, never in it ({@link ConfigStore}); machine-local,
     * never synced.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Window window = new Window();

    /** The documents' text size (WIN-09), in percent: the content zoom, apart from the UI zoom. */
    public int textZoom = DEFAULT_TEXT_ZOOM;

    public static final int DEFAULT_TEXT_ZOOM = 100;

    public static final int MIN_TEXT_ZOOM = 50;

    public static final int MAX_TEXT_ZOOM = 300;

    /** {@code percent} within the text zoom's range. */
    public static int clampTextZoom(int percent) {
        return Math.min(MAX_TEXT_ZOOM, Math.max(MIN_TEXT_ZOOM, percent));
    }

    public Sync sync = new Sync();

    /**
     * The game library (Settings → Library): this computer's folders and RetroArch. Paths are machine-local, so
     * Import Configuration leaves this out ({@code ConfigImport.LOCAL_KEYS}), and Settings Sync never carries it.
     */
    public Library library = new Library();

    /** The window's size as read from the file is kept within these, in pixels (SET-11). */
    public static final double MIN_WINDOW_SIZE = 320;

    public static final double MAX_WINDOW_SIZE = 16_384;

    /** A saved position further from the origin than this is dropped, and the OS places the window. */
    public static final double MAX_WINDOW_POSITION = 100_000;

    /** The longest sync interval: a day. */
    public static final int MAX_SYNC_INTERVAL_MINUTES = 24 * 60;

    /** A tool window's share of the window, and a side's split, are kept within these. */
    public static final double MIN_SHARE = 0.05;

    public static final double MAX_SHARE = 0.95;

    /** The sides a tool window can be moved to ({@code ToolWindow.Side}'s names). */
    static final java.util.Set<String> SIDES = java.util.Set.of("LEFT", "RIGHT", "BOTTOM");

    /**
     * Brings every value read from the file into its range (SET-11): the file is plain JSON that people edit
     * by hand, so a number out of range is clamped, and a value this version does not know (a keymap, a
     * language, a chord, a branch name, a side) falls back to its default, as does a {@code null} that
     * Jackson writes into a field when the file says so. Called on every read, not only when a value is set
     * from the UI. Returns this.
     */
    public Settings clamped() {
        if (theme == null) theme = ThemeChoice.DEFAULT;
        if (language == null
                || !(language.isEmpty() || com.partida.i18n.Messages.available().containsKey(language))) {
            language = "";
        }
        if (keymap == null || !com.partida.command.Keymap.PRESETS.contains(keymap)) {
            keymap = com.partida.command.Keymap.DEFAULT;
        }
        Map<String, String> bindings = new TreeMap<>();
        if (keybindings != null) {
            keybindings.forEach((id, chord) -> {
                // A blank chord unbinds; any other must be keys this version knows.
                if (id == null || id.isBlank() || chord == null) return;
                if (chord.isBlank() || com.partida.command.KeyStroke.parseSequence(chord) != null) {
                    bindings.put(id, chord);
                }
            });
        }
        keybindings = bindings;
        lastUpdateCheck = Math.max(0, lastUpdateCheck);
        toolbarLayout = toolbarLayout == null
                ? new ArrayList<>()
                : toolbarLayout.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .limit(MAX_TOOLBAR_ENTRIES)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        uiZoom = clampUiZoom(uiZoom);
        textZoom = clampTextZoom(textZoom);
        if (window == null) window = new Window();
        window.clamp();
        if (sync == null) sync = new Sync();
        sync.clamp();
        if (library == null) library = new Library();
        library.clamp();
        return this;
    }

    /** {@code value} within {@code [min, max]}; {@code fallback} when it is not a number at all. */
    static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value)) return fallback;
        return Math.min(max, Math.max(min, value));
    }

    /**
     * A deep copy, made on the FX thread so the writer serializes a snapshot off it (PERF-01). A new
     * field is copied here too: {@code ConfigStoreTest} fails when the copy's JSON differs.
     */
    public Settings copy() {
        Settings c = new Settings();
        c.schemaVersion = schemaVersion;
        c.theme = theme;
        c.language = language;
        c.keymap = keymap;
        c.uiZoom = uiZoom;
        c.textZoom = textZoom;
        c.keybindings = new TreeMap<>(keybindings);
        c.checkForUpdates = checkForUpdates;
        c.lastUpdateCheck = lastUpdateCheck;
        c.showToolbar = showToolbar;
        c.showStatusBar = showStatusBar;
        c.showTabBar = showTabBar;
        c.titleBarMenus = titleBarMenus;
        c.simpleMode = simpleMode;
        c.toolbarLayout = new ArrayList<>(toolbarLayout);
        c.window.x = window.x;
        c.window.y = window.y;
        c.window.width = window.width;
        c.window.height = window.height;
        c.window.maximized = window.maximized;
        c.window.toolWindows = window.toolWindows.copy();
        c.window.documents = window.documents == null ? null : new java.util.ArrayList<>(window.documents);
        c.window.activeDocument = window.activeDocument;
        c.sync.repoUrl = sync.repoUrl;
        c.sync.branch = sync.branch;
        c.sync.auto = sync.auto;
        c.sync.intervalMinutes = sync.intervalMinutes;
        c.sync.lastSync = sync.lastSync;
        c.library = library.copy();
        return c;
    }

    /** The main window's last size and position. */
    public static final class Window {
        /** Null until the window has been placed once; then the OS chooses. */
        public @Nullable Double x;

        public @Nullable Double y;

        public double width = 960;
        public double height = 640;
        public boolean maximized;

        /** The tool windows' arrangement, restored on the next launch (TABS-15). */
        public ToolWindows toolWindows = new ToolWindows();

        /**
         * The documents open at the last quit, in tab order (WIN-08), or null before the first quit: the next
         * launch opens them again, unless it was started with {@code --no-session} (CLI-04).
         */
        public java.util.@Nullable List<String> documents;

        /** The document that was selected, or null for the Welcome page. */
        public @Nullable String activeDocument;

        /** At most this many documents are reopened: a hand-edited file can't open a thousand tabs. */
        public static final int MAX_DOCUMENTS = 100;

        void clamp() {
            Window defaults = new Window();
            width = Settings.clamp(width, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE, defaults.width);
            height = Settings.clamp(height, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE, defaults.height);
            if (!onScreen(x) || !onScreen(y)) {
                x = null;
                y = null;
            }
            if (toolWindows == null) toolWindows = new ToolWindows();
            toolWindows.clamp();
            if (documents != null) {
                documents = documents.stream()
                        .filter(d -> d != null && !d.isBlank())
                        .distinct()
                        .limit(MAX_DOCUMENTS)
                        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            }
            if (activeDocument != null && (documents == null || !documents.contains(activeDocument))) {
                activeDocument = null;
            }
        }

        private static boolean onScreen(@Nullable Double position) {
            return position != null && Double.isFinite(position) && Math.abs(position) <= MAX_WINDOW_POSITION;
        }
    }

    /**
     * The workspace state of the tool windows (TABS-15): which are open, on which side, in what order,
     * hidden or shown, floating, and every size. Machine-local, with the window it belongs to.
     */
    public static final class ToolWindows {
        /** The windows open, docked or floating, in the order they opened. */
        public List<String> open = new ArrayList<>();

        /** A window's side where the user moved it from its home: {@code LEFT}, {@code RIGHT} or {@code BOTTOM}. */
        public Map<String, String> sides = new TreeMap<>();

        /** The stripe buttons' order, one list across the three stripes; empty for the app's own order. */
        public List<String> order = new ArrayList<>();

        /** The stripe buttons the user hid. */
        public List<String> hidden = new ArrayList<>();

        /** The windows that float in a window of their own. */
        public List<String> floating = new ArrayList<>();

        /** Each window's share of the window's width (left, right) or height (bottom), once it has one. */
        public Map<String, Double> sizes = new TreeMap<>();

        /** Each side's split between its two stacked windows, by side. */
        public Map<String, Double> splits = new TreeMap<>();

        /** The maximized window's id, or blank. */
        public String maximized = "";

        void clamp() {
            open = known(open);
            order = known(order);
            hidden = known(hidden);
            floating = known(floating);
            Map<String, String> keptSides = new TreeMap<>();
            if (sides != null) {
                sides.forEach((id, side) -> {
                    if (id != null && side != null && SIDES.contains(side)) keptSides.put(id, side);
                });
            }
            sides = keptSides;
            sizes = shares(sizes);
            splits = shares(splits);
            if (maximized == null || !open.contains(maximized)) maximized = "";
        }

        /** The list without nulls, blanks or repeats. */
        private static List<String> known(@Nullable List<String> ids) {
            List<String> kept = new ArrayList<>();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && !id.isBlank() && !kept.contains(id)) kept.add(id);
                }
            }
            return kept;
        }

        /** Each share within {@link #MIN_SHARE} and {@link #MAX_SHARE}; one that is not a number is dropped. */
        private static Map<String, Double> shares(@Nullable Map<String, Double> raw) {
            Map<String, Double> kept = new TreeMap<>();
            if (raw != null) {
                raw.forEach((id, share) -> {
                    if (id != null && share != null && !share.isNaN()) {
                        kept.put(id, Settings.clamp(share, MIN_SHARE, MAX_SHARE, share));
                    }
                });
            }
            return kept;
        }

        public ToolWindows copy() {
            ToolWindows c = new ToolWindows();
            c.open = new ArrayList<>(open);
            c.sides = new TreeMap<>(sides);
            c.order = new ArrayList<>(order);
            c.hidden = new ArrayList<>(hidden);
            c.floating = new ArrayList<>(floating);
            c.sizes = new TreeMap<>(sizes);
            c.splits = new TreeMap<>(splits);
            c.maximized = maximized;
            return c;
        }
    }

    /** Settings Sync through a Git repository the user owns. */
    public static final class Sync {
        /** Blank when sync is not set up. */
        public String repoUrl = "";

        public String branch = "main";
        public boolean auto = true;
        public int intervalMinutes = 15;

        /** The interval a text means, or null for anything but a whole number of minutes from 1. */
        public static @Nullable Integer parseInterval(String text) {
            try {
                int minutes = Integer.parseInt(text.trim());
                return minutes >= 1 ? minutes : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        /** The last successful sync, epoch milliseconds; 0 for never. */
        public long lastSync;

        void clamp() {
            if (repoUrl == null) repoUrl = "";
            if (branch == null || com.partida.sync.SyncEngine.checkBranch(branch) != null) branch = "main";
            intervalMinutes = Math.min(MAX_SYNC_INTERVAL_MINUTES, Math.max(1, intervalMinutes));
            lastSync = Math.max(0, lastSync);
        }
    }

    /** The game library's sources: folders of games, and which RetroArch to read and start. */
    public static final class Library {
        /** Folders of games, in the order the user added them; scanned by file extension. */
        public List<Folder> folders = new ArrayList<>();

        /** RetroArch's config folder (the one holding retroarch.cfg); blank to find RetroArch on its own. */
        public String retroarchConfigDir = "";

        /** The command that starts RetroArch with a custom config folder, words split on spaces; blank for "retroarch". */
        public String retroarchCommand = "";

        /** Whether RetroArch's play history is the first collection. */
        public boolean showRecentlyPlayed = true;

        /** One folder of games and the core for all of it, blank to choose by file extension. */
        public static final class Folder {
            public String path = "";
            public String coreId = "";

            public Folder() {}

            public Folder(String path, String coreId) {
                this.path = path;
                this.coreId = coreId;
            }
        }

        /** The launch command as its words: {@link #retroarchCommand} split on spaces. Pure. */
        public List<String> commandWords() {
            String line = retroarchCommand == null ? "" : retroarchCommand.strip();
            return line.isEmpty() ? List.of() : List.of(line.split("\\s+"));
        }

        /** The folder at {@code path}, or null. */
        public @Nullable Folder folder(String path) {
            for (Folder f : folders) {
                if (f.path.equals(path)) return f;
            }
            return null;
        }

        /**
         * Drops a folder entry with no path, a path that isn't a full one and a second entry for the same path;
         * puts the default (blank: find RetroArch on its own) in place of a RetroArch folder that isn't a full
         * path; and turns a null string into blank. A hand-edited file can't carry any of these into the app, as
         * the UI can't (SET-11).
         */
        void clamp() {
            List<Folder> kept = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            if (folders != null) {
                for (Folder f : folders) {
                    if (f == null || f.path == null || !isFullPath(f.path) || !seen.add(f.path.strip())) continue;
                    f.path = f.path.strip();
                    if (f.coreId == null) f.coreId = "";
                    kept.add(f);
                }
            }
            folders = kept;
            if (retroarchConfigDir == null || !isFullPath(retroarchConfigDir)) retroarchConfigDir = "";
            else retroarchConfigDir = retroarchConfigDir.strip();
            if (retroarchCommand == null) retroarchCommand = "";
        }

        /** Whether {@code text} is a full (absolute) path this OS can parse. Pure: nothing is read from disk. */
        public static boolean isFullPath(String text) {
            if (text.isBlank()) return false;
            try {
                return java.nio.file.Path.of(text.strip()).isAbsolute();
            } catch (java.nio.file.InvalidPathException e) {
                return false;
            }
        }

        public Library copy() {
            Library c = new Library();
            for (Folder f : folders) c.folders.add(new Folder(f.path, f.coreId));
            c.retroarchConfigDir = retroarchConfigDir;
            c.retroarchCommand = retroarchCommand;
            c.showRecentlyPlayed = showRecentlyPlayed;
            return c;
        }
    }
}
