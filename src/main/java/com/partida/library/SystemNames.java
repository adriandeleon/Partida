package com.partida.library;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * File extensions that belong to exactly one system, mapped to the libretro database name (which is
 * also the thumbnail folder name). Shared extensions such as .zip, .bin, .iso, .cue and .chd are left
 * out on purpose: a library folder needs a core or system assigned for those.
 */
public final class SystemNames {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            entry("nes", "Nintendo - Nintendo Entertainment System"),
            entry("fds", "Nintendo - Family Computer Disk System"),
            entry("sfc", "Nintendo - Super Nintendo Entertainment System"),
            entry("smc", "Nintendo - Super Nintendo Entertainment System"),
            entry("gb", "Nintendo - Game Boy"),
            entry("gbc", "Nintendo - Game Boy Color"),
            entry("gba", "Nintendo - Game Boy Advance"),
            entry("n64", "Nintendo - Nintendo 64"),
            entry("z64", "Nintendo - Nintendo 64"),
            entry("v64", "Nintendo - Nintendo 64"),
            entry("nds", "Nintendo - Nintendo DS"),
            entry("vb", "Nintendo - Virtual Boy"),
            entry("min", "Nintendo - Pokemon Mini"),
            entry("md", "Sega - Mega Drive - Genesis"),
            entry("gen", "Sega - Mega Drive - Genesis"),
            entry("sms", "Sega - Master System - Mark III"),
            entry("gg", "Sega - Game Gear"),
            entry("32x", "Sega - 32X"),
            entry("pce", "NEC - PC Engine - TurboGrafx 16"),
            entry("ws", "Bandai - WonderSwan"),
            entry("wsc", "Bandai - WonderSwan Color"),
            entry("ngp", "SNK - Neo Geo Pocket"),
            entry("ngc", "SNK - Neo Geo Pocket Color"),
            entry("a26", "Atari - 2600"),
            entry("a78", "Atari - 7800"),
            entry("lnx", "Atari - Lynx"),
            entry("col", "Coleco - ColecoVision"));

    private SystemNames() {}

    public static Optional<String> forExtension(String extension) {
        return Optional.ofNullable(BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT)));
    }
}
