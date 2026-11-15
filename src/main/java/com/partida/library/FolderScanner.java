package com.partida.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.partida.retroarch.CoreCatalog;
import com.partida.retroarch.CoreInfo;
import com.partida.retroarch.Playlist;

/**
 * Finds games in a folder by file extension.
 *
 * <ul>
 *   <li>With a core assigned to the folder, every file that core supports is a game.</li>
 *   <li>Without one, a file counts only if its extension belongs to a single system
 *       ({@link SystemNames}) or to installed cores that all cover the same single system.</li>
 *   <li>Track files of multi-file discs are hidden: .bin/.img/.sub next to a .cue, .gdi or .ccd,
 *       and all disc files next to an .m3u.</li>
 * </ul>
 */
public final class FolderScanner {

    public record Result(List<Game> games, List<Path> unmatched) {}

    private static final int MAX_DEPTH = 6;

    private static final Set<String> IGNORED = Set.of(
            // Not "md": that's Mega Drive.
            "txt",
            "nfo",
            "diz",
            "pdf",
            "jpg",
            "jpeg",
            "png",
            "gif",
            "bmp",
            "webp",
            "srm",
            "sav",
            "state",
            "auto",
            "dat",
            "xml",
            "cfg",
            "ini",
            "db",
            "lpl",
            "sha1",
            "md5",
            "sfv");
    private static final Set<String> SHEETS = Set.of("cue", "gdi", "ccd");
    private static final Set<String> SHEET_TRACKS = Set.of("bin", "img", "sub", "raw");
    private static final Set<String> DISC_FILES =
            Set.of("cue", "gdi", "ccd", "bin", "img", "sub", "iso", "chd", "cso", "pbp");
    private static final Set<String> ARCHIVES = Set.of("zip", "7z");

    private FolderScanner() {}

    public static Result scan(LibraryFolder folder, CoreCatalog catalog) throws IOException {
        Path root = Path.of(folder.path());
        String coreId = folder.coreId();
        Optional<CoreInfo> forced = coreId != null && !coreId.isBlank() ? catalog.byId(coreId) : Optional.empty();

        List<Path> files;
        try (Stream<Path> s = Files.walk(root, MAX_DEPTH)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        }

        Map<Path, Set<String>> extensionsByDir = new HashMap<>();
        for (Path f : files) {
            extensionsByDir.computeIfAbsent(f.getParent(), d -> new HashSet<>()).add(extension(f));
        }

        List<Game> games = new ArrayList<>();
        List<Path> unmatched = new ArrayList<>();
        for (Path file : files) {
            String ext = extension(file);
            if (ext.isEmpty() || IGNORED.contains(ext)) {
                continue;
            }
            Set<String> siblings = extensionsByDir.getOrDefault(file.getParent(), Set.of());
            if (siblings.contains("m3u") && DISC_FILES.contains(ext)) {
                continue;
            }
            if (SHEET_TRACKS.contains(ext) && siblings.stream().anyMatch(SHEETS::contains)) {
                continue;
            }

            String title = Playlist.stem(file.getFileName().toString());
            Optional<String> system = forced.isPresent()
                    ? systemForForcedCore(folder, forced.get(), ext)
                    : systemForExtension(folder, catalog, ext);
            if (system.isEmpty()) {
                unmatched.add(file);
                continue;
            }
            games.add(new Game(
                    title, file, system.get(), forced.map(CoreInfo::library).orElse(null)));
        }
        games.sort(Comparator.comparing(Game::title, String.CASE_INSENSITIVE_ORDER));
        return new Result(List.copyOf(games), List.copyOf(unmatched));
    }

    private static Optional<String> systemForForcedCore(LibraryFolder folder, CoreInfo core, String ext) {
        if (!core.supports(ext) && !ARCHIVES.contains(ext) && !ext.equals("m3u")) {
            return Optional.empty();
        }
        String chosen = folder.system();
        if (chosen != null && !chosen.isBlank()) {
            return Optional.of(chosen);
        }
        return SystemNames.forExtension(ext)
                .or(() -> core.databases().size() == 1
                        ? Optional.of(core.databases().getFirst())
                        : Optional.empty())
                .or(() -> Optional.of(core.systemName()));
    }

    private static Optional<String> systemForExtension(LibraryFolder folder, CoreCatalog catalog, String ext) {
        List<CoreInfo> cores = catalog.forExtension(ext);
        if (cores.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> system = SystemNames.forExtension(ext).or(() -> {
            Set<String> systems = new HashSet<>();
            for (CoreInfo c : cores) {
                if (c.databases().size() != 1) {
                    return Optional.empty();
                }
                systems.add(c.databases().getFirst());
            }
            return systems.size() == 1 ? Optional.of(systems.iterator().next()) : Optional.empty();
        });
        String chosen = folder.system();
        return system.map(s -> chosen != null && !chosen.isBlank() ? chosen : s);
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
