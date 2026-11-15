package com.partida.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.partida.retroarch.CfgFile;
import com.partida.retroarch.CoreCatalog;
import com.partida.retroarch.CoreInfo;
import com.partida.retroarch.RetroArchInstall;
import com.partida.retroarch.Thumbnails;

/**
 * What was read from one RetroArch install: its config, its installed cores and where its box art lives. Read
 * off the FX thread (PERF-01) and replaced whole on a refresh.
 */
public record RetroArchContext(RetroArchInstall install, CfgFile config, CoreCatalog cores, Thumbnails thumbnails) {

    public static RetroArchContext load(RetroArchInstall install) throws IOException {
        CfgFile cfg = CfgFile.load(install.configFile());
        CoreCatalog cores = CoreCatalog.load(install.infoDir(cfg), install.coresDir(cfg));
        return new RetroArchContext(install, cfg, cores, new Thumbnails(install.thumbnailsDir(cfg)));
    }

    /** RetroArch's system folder, where cores look for BIOS files. */
    public Path systemDir() {
        return install.systemDir(config);
    }

    /** Cores that can run this game, best match first: cores covering the game's system come first. */
    public List<CoreInfo> compatibleCores(Game game) {
        Comparator<CoreInfo> bySystem = Comparator.comparing(c -> !c.databases().contains(game.system()));
        return cores.forExtension(game.extension()).stream()
                .sorted(bySystem.thenComparing(CoreInfo::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The playlist's core if it's still installed, otherwise the best compatible core. */
    public Optional<CoreInfo> preferredCore(Game game) {
        Path assigned = game.core();
        if (assigned != null && Files.isRegularFile(assigned)) {
            Optional<CoreInfo> core = cores.byLibrary(assigned);
            if (core.isPresent()) return core;
        }
        return compatibleCores(game).stream().findFirst();
    }
}
