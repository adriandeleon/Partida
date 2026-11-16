package com.partida.library;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One game of a system and every ROM of it: {@code Super Mario Bros. (E) (REVA) [!]}, {@code (U) [!]} and
 * {@code (Europe) (Rev A)} are one entry, Super Mario Bros., with three ROMs. A library is browsed by these, so
 * a game's regional releases, revisions and dumps don't each take a row; the ROMs are listed under it.
 *
 * <p>A ROM is {@linkplain RomName#hidden() hidden} when a tag marks it as a bad dump, a hack, a beta and the
 * like: it stays in {@link #roms}, out of {@link #shown}. A game whose ROMs are all hidden is
 * {@linkplain #hidden() hidden} itself.
 *
 * @param key the grouping key every ROM's name shares ({@link RomName#key})
 * @param title the game's name, from its best ROM
 * @param system the system its ROMs are on
 * @param roms every ROM, best first ({@link #ORDER})
 */
public record GameTitle(String key, String title, String system, List<Rom> roms) {

    /** One ROM of a game: the library's entry and what its name says. */
    public record Rom(Game game, RomName name) {

        /** Reads {@code game}'s name: its playlist label, or its file's stem when it has none. */
        public static Rom of(Game game) {
            String label = game.title().isBlank() ? game.fileStem() : game.title();
            return new Rom(game, RomName.parse(label));
        }
    }

    /**
     * The order of a game's ROMs, best first, which is also the one Play starts when no ROM of it was played: a
     * clean release before a hidden one, the region ranked first in {@code regions} (a ROM of no region listed
     * after them all), a verified dump before an unverified one, the original before an alternate dump, the
     * latest revision, the first disc, the fewest tags this app does not know, then by file name.
     */
    public static Comparator<Rom> order(List<String> regions) {
        return Comparator.<Rom, Boolean>comparing(r -> r.name().hidden())
                .thenComparingInt(r -> regionRank(r.name(), regions))
                .thenComparing(r -> !r.name().has(RomName.Flag.VERIFIED))
                .thenComparing(r -> r.name().has(RomName.Flag.ALTERNATE))
                .thenComparing(r -> -r.name().revisionRank())
                .thenComparingInt(r -> r.name().disc())
                .thenComparingInt(r -> r.name().other().size())
                .thenComparing(r -> r.game().contentName().toLowerCase(Locale.ROOT));
    }

    /** {@link #order} with no region ranked above another. */
    public static final Comparator<Rom> ORDER = order(List.of());

    /** Where a ROM's best region stands in {@code regions}; past the end when none of its regions is there. */
    static int regionRank(RomName name, List<String> regions) {
        int rank = regions.size();
        for (String region : name.regions()) {
            int at = regions.indexOf(region);
            if (at >= 0) rank = Math.min(rank, at);
        }
        return rank;
    }

    public GameTitle {
        roms = List.copyOf(roms);
        if (roms.isEmpty()) throw new IllegalArgumentException("a game has a ROM");
    }

    /** The ROMs browsing shows: those no tag hides, or all of them when every one is hidden. */
    public List<Rom> shown() {
        List<Rom> shown = roms.stream().filter(r -> !r.name().hidden()).toList();
        return shown.isEmpty() ? roms : shown;
    }

    /** Whether every ROM is hidden, so the game itself is left out of browsing unless asked for. */
    public boolean hidden() {
        return roms.stream().allMatch(r -> r.name().hidden());
    }

    /** The best ROM: the first of {@link #roms}, the one Play starts when no ROM was played before. */
    public Rom best() {
        return roms.getFirst();
    }

    /** The best ROM with {@code regions} ranked: {@link #order}. */
    public Rom best(List<String> regions) {
        return roms.stream().min(order(regions)).orElseThrow();
    }

    /** What names this game across launches: its system and its key ({@link #idOf}). */
    public String id() {
        return system + ID_SEPARATOR + key;
    }

    private static final String ID_SEPARATOR = " | ";

    /** The {@link #id} of the game {@code game} is a ROM of, read from its name. */
    public static String idOf(Game game) {
        return game.system() + ID_SEPARATOR + keyOf(Rom.of(game));
    }

    private static String keyOf(Rom rom) {
        String key = rom.name().key();
        return key.isEmpty() ? rom.game().contentName().toLowerCase(Locale.ROOT) : key;
    }

    /** The regions of the shown ROMs, without repeats, in the order of the ROMs. */
    public List<String> regions() {
        Set<String> regions = new LinkedHashSet<>();
        for (Rom rom : shown()) regions.addAll(rom.name().regions());
        return List.copyOf(regions);
    }

    /**
     * The order of games in a system: by title as read, a leading article ignored, so The Legend of Zelda is
     * under L, as in most libraries and shops.
     */
    public static final Comparator<GameTitle> BY_TITLE =
            Comparator.comparing((GameTitle t) -> sortKey(t.key())).thenComparing(GameTitle::key);

    /** {@code key} with a leading article taken off, for ordering and type-ahead. */
    public static String sortKey(String key) {
        for (String article : List.of("the ", "a ", "an ")) {
            if (key.startsWith(article) && key.length() > article.length()) return key.substring(article.length());
        }
        return key;
    }

    /**
     * Groups {@code games} into one entry per game and system ({@link RomName#key}), each with its ROMs best
     * first, the entries in {@link #BY_TITLE} order. A ROM is never in two entries.
     */
    public static List<GameTitle> group(List<Game> games) {
        return group(games, List.of());
    }

    /** {@link #group(List)}, each game's ROMs in {@link #order} for {@code regions}. */
    public static List<GameTitle> group(List<Game> games, List<String> regions) {
        Map<String, List<Rom>> bySystemAndKey = new LinkedHashMap<>();
        for (Game game : games) {
            Rom rom = Rom.of(game);
            String key = keyOf(rom);
            bySystemAndKey
                    .computeIfAbsent(game.system() + '\u0000' + key, k -> new ArrayList<>())
                    .add(rom);
        }
        List<GameTitle> titles = new ArrayList<>();
        bySystemAndKey.forEach((systemAndKey, roms) -> {
            roms.sort(order(regions));
            Rom best = roms.getFirst();
            String key = systemAndKey.substring(systemAndKey.indexOf('\u0000') + 1);
            titles.add(new GameTitle(key, best.name().title(), best.game().system(), roms));
        });
        titles.sort(BY_TITLE);
        return titles;
    }
}
