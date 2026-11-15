package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * An installed libretro core, described by its .info file.
 *
 * @param id        file name without extension, e.g. {@code mgba_libretro}
 * @param databases the libretro database / thumbnail folder names this core covers
 * @param library   the core's .so/.dll/.dylib on this machine
 */
public record CoreInfo(
        String id,
        String displayName,
        String coreName,
        String systemName,
        List<String> extensions,
        List<String> databases,
        List<Firmware> firmware,
        boolean hardwareRendered,
        Path library) {

    /** A BIOS or firmware file the core reads from RetroArch's system folder. */
    public record Firmware(String path, String description, boolean optional) {}

    public static CoreInfo fromInfo(String id, CfgFile info, Path library) {
        List<Firmware> firmware = new ArrayList<>();
        int count = info.getInt("firmware_count", 0);
        for (int i = 0; i < count; i++) {
            String path = info.get("firmware" + i + "_path", "");
            if (!path.isBlank()) {
                firmware.add(new Firmware(
                        path, info.get("firmware" + i + "_desc", path), info.getBool("firmware" + i + "_opt", false)));
            }
        }
        return new CoreInfo(
                id,
                info.get("display_name", id),
                info.get("corename", id),
                info.get("systemname", ""),
                splitPipe(info.get("supported_extensions", "")).stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .toList(),
                splitPipe(info.get("database", "")),
                List.copyOf(firmware),
                info.getBool("hw_render", false),
                library);
    }

    public String label() {
        return displayName.isBlank() ? id : displayName;
    }

    public boolean supports(String extension) {
        return extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** Required firmware files that aren't in the given system folder. */
    public List<Firmware> missingFirmware(Path systemDir) {
        return firmware.stream()
                .filter(f -> !f.optional())
                .filter(f -> !Files.isRegularFile(systemDir.resolve(f.path())))
                .toList();
    }

    static List<String> splitPipe(String s) {
        return Arrays.stream(s.split("\\|"))
                .map(String::strip)
                .filter(x -> !x.isEmpty())
                .toList();
    }

    @Override
    public String toString() {
        return label();
    }
}
