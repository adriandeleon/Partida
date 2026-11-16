package com.partida.ui;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.scene.image.Image;

import com.partida.library.Game;
import com.partida.library.RetroArchContext;
import com.partida.retroarch.Thumbnails;
import org.jspecify.annotations.Nullable;

/**
 * The game browser's box art: a game's thumbnail found off the FX thread (it reads the disk, PERF-01), then
 * decoded in the background at the size it is drawn, and kept for the next time it is drawn. A file past
 * {@link LibraryPanels.GameInfo#BOXART_LIMIT} is never read (PERF-12). One per window; FX thread only, except
 * the lookups it starts.
 */
final class BoxArt {

    /** At most this many decoded images are kept, the least recently drawn dropped first. */
    static final int MAX_IMAGES = 400;

    private final Supplier<@Nullable RetroArchContext> context;
    /** Each ROM's art file, once looked up: empty when it has none. */
    private final Map<Path, Optional<Path>> files = new HashMap<>();

    private final Map<String, Image> images = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_IMAGES;
        }
    };

    BoxArt(Supplier<@Nullable RetroArchContext> context) {
        this.context = context;
    }

    /**
     * Hands {@code done} the art of {@code game}, decoded {@code width} wide, or null when it has none; at once
     * when it was drawn before, else on the FX thread once found. The caller checks that what it asked for is
     * still what it shows.
     */
    void load(Game game, double width, Consumer<@Nullable Image> done) {
        Optional<Path> known = files.get(game.rom());
        if (known != null) {
            done.accept(known.map(file -> image(file, width)).orElse(null));
            return;
        }
        RetroArchContext ctx = context.get();
        if (ctx == null) {
            done.accept(null);
            return;
        }
        Background.run(() -> {
            Optional<Path> found = ctx.thumbnails()
                    .find(game.system(), game.title(), game.fileStem(), Thumbnails.Kind.BOXART)
                    .filter(LibraryPanels.GameInfo::fits);
            Platform.runLater(() -> {
                files.put(game.rom(), found);
                done.accept(found.map(file -> image(file, width)).orElse(null));
            });
        });
    }

    /** The image of {@code file} at {@code width}, decoded in the background the first time. */
    private Image image(Path file, double width) {
        String key = file + "@" + Math.round(width);
        return images.computeIfAbsent(
                key, k -> new Image(file.toUri().toString(), width, width * 1.5, true, true, true));
    }

    /** Forgets every lookup and image: the library loaded again, and art may have come or gone. */
    void clear() {
        files.clear();
        images.clear();
    }

    /** How many ROMs' art files are known now: for the tests. */
    int known() {
        return files.size();
    }
}
