package com.partida.retroarch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A RetroArch playlist (.lpl). The file name, without .lpl, is also the system name RetroArch uses for
 * thumbnail folders, e.g. "Nintendo - Super Nintendo Entertainment System".
 */
public record Playlist(String name, Path file, String defaultCorePath, List<Playlist.Entry> entries) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** One game in a playlist, as RetroArch wrote it. Empty strings for what it left out. */
    public record Entry(String path, String label, String corePath, String coreName, String crc32, String dbName) {

        /** The label, or the file name without extension when RetroArch left the label empty. */
        public String title() {
            return label.isBlank() ? stem(contentName(path)) : label;
        }
    }

    public static Playlist read(Path file) throws IOException {
        String text = Inputs.read(file, Inputs.PLAYLIST_LIMIT); // refused past the limit (PERF-12)
        return parse(stripLpl(String.valueOf(file.getFileName())), file, text);
    }

    /** Reads either format: JSON (RetroArch 1.7.6 and later) or the older six lines per entry. */
    public static Playlist parse(String name, Path file, String text) throws IOException {
        return text.stripLeading().startsWith("{") ? parseJson(name, file, text) : parseLegacy(name, file, text);
    }

    /** Reads every .lpl directly inside a folder, sorted by name. Unreadable files are reported, not thrown. */
    public static List<Playlist> readAll(Path dir, List<String> warnings) {
        List<Playlist> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) return result;
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> String.valueOf(p.getFileName()).endsWith(".lpl") && Files.isRegularFile(p))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            warnings.add("Couldn't list playlists in " + dir + ": " + e.getMessage());
            return result;
        }
        for (Path file : files) {
            try {
                result.add(read(file));
            } catch (IOException | RuntimeException e) {
                warnings.add("Skipped playlist " + file.getFileName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    private static Playlist parseJson(String name, Path file, String text) throws IOException {
        JsonNode root = JSON.readTree(text);
        if (root == null || !root.isObject()) throw new IOException("not a playlist object");
        List<Entry> entries = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode o : items) {
                if (!o.isObject()) throw new IOException("playlist item is not an object");
                entries.add(new Entry(
                        str(o, "path"),
                        str(o, "label"),
                        str(o, "core_path"),
                        str(o, "core_name"),
                        str(o, "crc32"),
                        str(o, "db_name")));
            }
        }
        return new Playlist(name, file, str(root, "default_core_path"), List.copyOf(entries));
    }

    /** Playlists from before RetroArch 1.7.6: six lines per entry. */
    private static Playlist parseLegacy(String name, Path file, String text) {
        String[] lines = text.split("\\R", -1);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i + 6 <= lines.length; i += 6) {
            entries.add(new Entry(lines[i], lines[i + 1], lines[i + 2], lines[i + 3], lines[i + 4], lines[i + 5]));
        }
        return new Playlist(name, file, "", List.copyOf(entries));
    }

    private static String str(JsonNode o, String key) {
        JsonNode v = o.get(key);
        return v != null && v.isValueNode() && !v.isNull() ? v.asText() : "";
    }

    public static String stripLpl(String name) {
        return name.endsWith(".lpl") ? name.substring(0, name.length() - 4) : name;
    }

    /** "dir/game.zip#inner.sfc" → "inner.sfc"; "dir/game.sfc" → "game.sfc". */
    public static String contentName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = path.substring(slash + 1);
        int hash = name.lastIndexOf('#');
        return hash >= 0 && hash < name.length() - 1 ? name.substring(hash + 1) : name;
    }

    public static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
