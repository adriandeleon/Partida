package com.partida.retroarch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes RetroArch's {@code key = "value"} files: retroarch.cfg, core .info files,
 * per-core/per-game overrides and core option (.opt) files.
 *
 * <p>Comments, blank lines and lines it doesn't understand are kept as they are, so saving
 * only changes the keys you set.
 */
public final class CfgFile {

    private static final Pattern ENTRY = Pattern.compile("^\\s*([A-Za-z0-9_.\\-]+)\\s*=\\s*(.*?)\\s*$");

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Integer> lineOfKey = new HashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    private CfgFile() {}

    public static CfgFile empty() {
        return new CfgFile();
    }

    /** Loads a file, or returns an empty config if it doesn't exist. */
    public static CfgFile load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }
        // Decode leniently: a stray non-UTF-8 byte in a path shouldn't make the whole file unreadable. A file past
        // the limit is refused before it is read (PERF-12).
        return parse(Inputs.read(file, Inputs.CONFIG_LIMIT));
    }

    public static CfgFile parse(String text) {
        CfgFile cfg = new CfgFile();
        for (String line : text.split("\\R", -1)) {
            cfg.addLine(line);
        }
        // A trailing newline produces one empty last element; serialize() adds it back.
        if (!cfg.lines.isEmpty() && cfg.lines.getLast().isEmpty()) {
            cfg.lines.removeLast();
        }
        return cfg;
    }

    private void addLine(String line) {
        lines.add(line);
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        Matcher m = ENTRY.matcher(line);
        if (m.matches()) {
            String key = m.group(1);
            values.put(key, unquote(m.group(2)));
            lineOfKey.put(key, lines.size() - 1);
        }
    }

    static String unquote(String raw) {
        if (raw.startsWith("\"")) {
            int end = raw.lastIndexOf('"');
            return end > 0 ? raw.substring(1, end) : raw.substring(1);
        }
        return raw.strip();
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    public boolean getBool(String key, boolean fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        return v.equalsIgnoreCase("true") || v.equals("1");
    }

    public int getInt(String key, int fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double getDouble(String key, double fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(v.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }

    /** Sets a key, replacing its existing line in place or adding a new line at the end. */
    public void set(String key, String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (!ENTRY.matcher(key + " = x").matches()) {
            throw new IllegalArgumentException("Not a valid config key: " + key);
        }
        if (value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("RetroArch config values can't contain quotes or line breaks");
        }
        String line = key + " = \"" + value + "\"";
        Integer at = lineOfKey.get(key);
        if (at != null) {
            lines.set(at, line);
        } else {
            lines.add(line);
            lineOfKey.put(key, lines.size() - 1);
        }
        values.put(key, value);
    }

    public String serialize() {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    /** Writes to a temporary file first, then moves it over the original. */
    public void save(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, serialize(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
