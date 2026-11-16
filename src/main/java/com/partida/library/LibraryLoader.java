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

    /** The loaded library, and what couldn't be read (for the Debug Log and the status bar). */
    public record Library(List<GameCollection> collections, List<String> warnings) {

        /** Games in the library, the play history left out (it repeats games listed elsewhere). */
        public int gameCount() {
            return collections.stream()
                    .filter(c -> !c.name().equals(RECENTLY_PLAYED))
                    .mapToInt(c -> c.games().size())
                    .sum();
        }
    }

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
        if (showRecentlyPlayed) {
            Path history = playlistsDir.resolve("builtin").resolve("content_history.lpl");
            if (Files.isRegularFile(history)) {
                try {
                    Playlist p = Playlist.read(history);
                    List<Game> recent = p.entries().stream()
                            .map(e -> toGame(e, "", p.defaultCorePath(), install))
                            .toList();
                    if (!recent.isEmpty()) collections.add(new GameCollection(RECENTLY_PLAYED, recent));
                } catch (IOException | RuntimeException e) {
                    warnings.add("Couldn't read the play history: " + e.getMessage());
                }
            }
        }
        bySystem.forEach((name, games) -> {
            List<Game> sorted = new ArrayList<>(games.values());
            sorted.sort(Comparator.comparing(Game::title, String.CASE_INSENSITIVE_ORDER));
            collections.add(new GameCollection(name, List.copyOf(sorted)));
        });
        return new Library(List.copyOf(collections), List.copyOf(warnings));
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
