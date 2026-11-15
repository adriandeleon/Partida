package com.partida.library;

import org.jspecify.annotations.Nullable;

/**
 * A folder of games the user added to the library.
 *
 * @param coreId optional core for every game in the folder, e.g. "mednafen_psx_hw_libretro". Needed for shared
 *     extensions like .zip, .chd, .cue and .iso, which many systems use.
 * @param system optional system name for box art, e.g. "Sony - PlayStation"
 */
public record LibraryFolder(
        String path, @Nullable String coreId, @Nullable String system) {

    public boolean hasCore() {
        String id = coreId;
        return id != null && !id.isBlank();
    }

    public boolean hasSystem() {
        String s = system;
        return s != null && !s.isBlank();
    }
}
