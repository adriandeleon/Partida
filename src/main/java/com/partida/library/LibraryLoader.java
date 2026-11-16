package com.partida.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.partida.retroarch.Playlist;
import com.partida.retroarch.RetroArchInstall;
import org.jspecify.annotations.Nullable;

/**
 * Builds the library from RetroArch's playlists plus the user's own library folders. Games for the same system
 * are merged into one collection, and the same file is listed once. Reads the disk: run it off the FX thread
 * (PERF-01).
 */
public final class LibraryLoader {

    /**
     * The play history's id among the systems, as {@link Library#collections()} lists it first: what the session
     * and Reopen Closed Tab keep. The UI shows it in the user's language ({@code Chrome.systemName}), never as
     * written here.
     */
    public static final String RECENTLY_PLAYED = "Recently played";

    /**
     * The loaded library, and what couldn't be read (for the Debug Log and the status bar).
     *
     * @param history the ROMs in RetroArch's play history, the latest first, read whether or not Recently played
     *     is shown: Play starts the ROM of a game played last ({@link PlayChoice})
     * @param counts each system's games, by its name: its ROMs grouped ({@link GameTitle}), counted where the
     *     load runs, off the FX thread
     */
    public record Library(
            List<GameCollection> collections, List<String> warnings, List<Path> history, Map<String, Counts> counts) {

        public Library {
            collections = List.copyOf(collections);
            warnings = List.copyOf(warnings);
            history = List.copyOf(history);
            counts = Map.copyOf(counts);
        }

        public Library(List<GameCollection> collections, List<String> warnings, List<Path> history) {
            this(collections, warnings, history, count(collections));
        }

        public Library(List<GameCollection> collections, List<String> warnings) {
            this(collections, warnings, List.of());
        }

        /** Games in the library, those browsing shows, the play history left out (it repeats the others). */
        public int gameCount() {
            return collections.stream()
                    .filter(c -> !c.name().equals(RECENTLY_PLAYED))
                    .mapToInt(c -> counts(c.name()).shown())
                    .sum();
        }

        /** The games of the system {@code name}; none for a system the library doesn't have. */
        public Counts counts(String name) {
            return counts.getOrDefault(name, new Counts(0, 0));
        }

        private static Map<String, Counts> count(List<GameCollection> collections) {
            Map<String, Counts> counts = new java.util.HashMap<>();
            for (GameCollection c : collections) {
                List<GameTitle> titles = GameTitle.group(c.games());
                int shown = (int) titles.stream().filter(t -> !t.hidden()).count();
                counts.put(c.name(), new Counts(shown, titles.size()));
            }
            return counts;
        }
    }

    /**
     * A system's games: {@code shown}, those browsing shows; {@code all}, with the games of hidden ROMs only
     * (decision 3).
     */
    public record Counts(int shown, int all) {}

    private LibraryLoader() {}

    public static Library load(RetroArchContext ctx, List<LibraryFolder> folders, boolean showRecentlyPlayed) {
        List<String> warnings = new ArrayList<>();
        RetroArchInstall install = ctx.install();
        Path playlistsDir = install.playlistsDir(ctx.config());

        Map<String, Map<Path, Game>> bySystem = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Playlist playlist : Playlist.readAll(playlistsDir, warnings)) {
            for (Playlist.Entry entry : playlist.entries()) {
                add(bySystem, playlist.name(), toGame(entry, playlist.name(), playlist.defaultCorePath(), install));
            }
        }

        for (LibraryFolder folder : folders) {
            Path root = Path.of(folder.path());
            if (!Files.isDirectory(root)) {
                warnings.add("Library folder not found: " + root);
                continue;
            }
            try {
                FolderScanner.Result result = FolderScanner.scan(folder, ctx.cores());
                for (Game g : result.games()) {
                    add(bySystem, g.system().isBlank() ? String.valueOf(root.getFileName()) : g.system(), g);
                }
                if (result.capped()) {
                    warnings.add("Only the first " + FolderScanner.MAX_FILES + " files in " + root
                            + " were scanned: is it the right folder?");
                }
                if (!result.unmatched().isEmpty()) {
                    warnings.add(result.unmatched().size() + " files in " + root
                            + " weren't added: no single system matches their extension");
                }
            } catch (IOException e) {
                warnings.add("Couldn't scan " + root + ": " + e.getMessage());
            }
        }

        List<GameCollection> collections = new ArrayList<>();
        List<Path> played = new ArrayList<>();
        Path history = playlistsDir.resolve("builtin").resolve("content_history.lpl");
        if (Files.isRegularFile(history)) {
            try {
                Playlist p = Playlist.read(history);
                List<Game> recent = p.entries().stream()
                        .map(e -> toGame(e, "", p.defaultCorePath(), install))
                        .toList();
                recent.forEach(g -> played.add(g.rom()));
                if (showRecentlyPlayed && !recent.isEmpty()) {
                    collections.add(new GameCollection(RECENTLY_PLAYED, recent));
                }
            } catch (IOException | RuntimeException e) {
                warnings.add("Couldn't read the play history: " + e.getMessage());
            }
        }
        bySystem.forEach((name, games) -> {
            List<Game> sorted = new ArrayList<>(games.values());
            sorted.sort(Comparator.comparing(Game::title, String.CASE_INSENSITIVE_ORDER));
            collections.add(new GameCollection(name, List.copyOf(sorted)));
        });
        return new Library(collections, warnings, played);
    }

    private static void add(Map<String, Map<Path, Game>> bySystem, String collection, Game game) {
        bySystem.computeIfAbsent(collection, k -> new LinkedHashMap<>())
                .putIfAbsent(game.rom().toAbsolutePath().normalize(), game);
    }

    static Game toGame(Playlist.Entry entry, String playlistName, String defaultCorePath, RetroArchInstall install) {
        String system = entry.dbName().isBlank() ? playlistName : Playlist.stripLpl(entry.dbName());
        String core =
                usableCore(entry.corePath()) ? entry.corePath() : usableCore(defaultCorePath) ? defaultCorePath : null;
        return new Game(
                entry.title(), install.resolve(entry.path()), system, core == null ? null : install.resolve(core));
    }

    private static boolean usableCore(@Nullable String path) {
        return path != null && !path.isBlank() && !path.equals("DETECT");
    }
}
