package com.partida.library;

import java.nio.file.Path;
import java.util.Locale;

import com.partida.retroarch.Playlist;
import org.jspecify.annotations.Nullable;

/**
 * One game in the library.
 *
 * @param system the libretro database name used for thumbnails, e.g. "Nintendo - Game Boy Advance";
 *               may be blank
 * @param core   the core a playlist assigned to it, or null to pick one by file extension
 */
public record Game(
        String title, Path rom, String system, @Nullable Path core) {

    /** File name inside an archive ("game.zip#game.sfc" → "game.sfc"), otherwise the file name. */
    public String contentName() {
        return Playlist.contentName(rom.toString());
    }

    public String fileStem() {
        return Playlist.stem(contentName());
    }

    public String extension() {
        String name = contentName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
