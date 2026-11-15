package com.partida.retroarch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/** The cores installed in RetroArch's cores folder, with what their .info files say about them. */
public final class CoreCatalog {

    private final List<CoreInfo> cores;

    public CoreCatalog(List<CoreInfo> cores) {
        this.cores = List.copyOf(cores);
    }

    public static CoreCatalog load(Path infoDir, Path coresDir) throws IOException {
        String libraryExtension = libraryExtension();
        Map<String, Path> installed = new TreeMap<>();
        if (Files.isDirectory(coresDir)) {
            try (Stream<Path> files = Files.list(coresDir)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    String name = p.getFileName().toString();
                    if (name.endsWith(libraryExtension)) {
                        installed.put(name.substring(0, name.length() - libraryExtension.length()), p);
                    }
                });
            }
        }

        List<CoreInfo> result = new ArrayList<>();
        for (Map.Entry<String, Path> core : installed.entrySet()) {
            Path info = infoDir.resolve(core.getKey() + ".info");
            CfgFile cfg = Files.isRegularFile(info) ? CfgFile.load(info) : CfgFile.empty();
            result.add(CoreInfo.fromInfo(core.getKey(), cfg, core.getValue()));
        }
        result.sort(Comparator.comparing(CoreInfo::label, String.CASE_INSENSITIVE_ORDER));
        return new CoreCatalog(result);
    }

    public List<CoreInfo> all() {
        return cores;
    }

    public Optional<CoreInfo> byId(String id) {
        return cores.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Finds a core by its library path, falling back to matching the file name. */
    public Optional<CoreInfo> byLibrary(Path library) {
        Path normalized = library.toAbsolutePath().normalize();
        return cores.stream()
                .filter(c -> c.library().toAbsolutePath().normalize().equals(normalized))
                .findFirst()
                .or(() -> cores.stream()
                        .filter(c -> c.library().getFileName().equals(library.getFileName()))
                        .findFirst());
    }

    public List<CoreInfo> forExtension(String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return cores.stream().filter(c -> c.supports(ext)).toList();
    }

    /** The file extension of a core library on this OS: .so, .dll or .dylib. */
    public static String libraryExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }
}
