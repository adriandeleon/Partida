package com.partida.retroarch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file RetroArch wrote, or the user pointed at, only when it fits (PERF-12): its size is checked against a
 * named limit before its content is read, and the read itself stops at the limit, so a file that grew meanwhile is
 * refused too. Nothing is read in part: RetroArch's files are read whole or not at all.
 */
final class Inputs {

    /** retroarch.cfg and a core's .info file: a few hundred KB at most in practice. */
    static final long CONFIG_LIMIT = 4L * 1024 * 1024;

    /** A playlist: tens of thousands of entries still fit well under this. */
    static final long PLAYLIST_LIMIT = 64L * 1024 * 1024;

    private Inputs() {}

    /** The file's text, decoded leniently as UTF-8; an {@link IOException} naming the limit when it is larger. */
    static String read(Path file, long limit) throws IOException {
        if (Files.size(file) > limit) throw tooLarge(file, limit);
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
        }
        if (bytes.length > limit) throw tooLarge(file, limit);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static IOException tooLarge(Path file, long limit) {
        return new IOException(file + " is larger than " + limit / (1024 * 1024) + " MB, so it was not read");
    }
}
