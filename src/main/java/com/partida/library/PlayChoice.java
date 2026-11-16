package com.partida.library;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Which of a game's ROMs Play starts (decision 3): the one played last, else the best for the region ranking.
 * Pure: the play history, the remembered ROM and the ranking are read by the caller.
 */
public final class PlayChoice {

    private PlayChoice() {}

    /**
     * The ROM to start for {@code title}: the one of its ROMs nearest the top of RetroArch's play history
     * ({@code history}, the latest first), which every launch, from Partida or from RetroArch itself, puts there;
     * else {@code remembered}, the ROM Partida last started for it, for when the history is off or has dropped
     * it; else its best ROM for {@code regions}.
     */
    public static GameTitle.Rom choose(
            GameTitle title, List<Path> history, @Nullable String remembered, List<String> regions) {
        for (Path played : history) {
            for (GameTitle.Rom rom : title.roms()) {
                if (same(rom.game().rom(), played)) return rom;
            }
        }
        if (remembered != null) {
            for (GameTitle.Rom rom : title.roms()) {
                if (rom.game().rom().toString().equals(remembered)) return rom;
            }
        }
        return title.best(regions);
    }

    private static boolean same(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
