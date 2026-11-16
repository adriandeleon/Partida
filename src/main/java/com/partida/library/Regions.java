package com.partida.library;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The regions ranked for choosing a game's ROM, best first: the user's own ranking from Settings, or one from
 * the system's country when there is none, so a player in Spain gets the Spanish release, then the European
 * one, before an American one, and a player in the USA the other way round.
 */
public final class Regions {

    private Regions() {}

    /** The country's own region in No-Intro's names, where a collection has releases of its own for it. */
    private static final Map<String, String> BY_COUNTRY = Map.ofEntries(
            Map.entry("US", "USA"),
            Map.entry("CA", "Canada"),
            Map.entry("GB", "UK"),
            Map.entry("FR", "France"),
            Map.entry("DE", "Germany"),
            Map.entry("AT", "Germany"),
            Map.entry("ES", "Spain"),
            Map.entry("IT", "Italy"),
            Map.entry("NL", "Netherlands"),
            Map.entry("SE", "Sweden"),
            Map.entry("DK", "Denmark"),
            Map.entry("NO", "Norway"),
            Map.entry("FI", "Finland"),
            Map.entry("PT", "Portugal"),
            Map.entry("PL", "Poland"),
            Map.entry("GR", "Greece"),
            Map.entry("RU", "Russia"),
            Map.entry("AU", "Australia"),
            Map.entry("NZ", "Australia"),
            Map.entry("BR", "Brazil"),
            Map.entry("JP", "Japan"),
            Map.entry("KR", "Korea"),
            Map.entry("CN", "China"),
            Map.entry("HK", "Hong Kong"),
            Map.entry("TW", "Taiwan"));

    /** Countries that play NTSC releases, where the USA's comes before Europe's. */
    private static final Set<String> AMERICAS = Set.of("US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE");

    /** Countries that play their own release first and then the world's: Japan and Korea. */
    private static final Set<String> ASIA = Set.of("JP", "KR");

    /**
     * The ranking for {@code locale}'s country: its own region first. In the Americas (or with no country) the
     * world release, then the USA's and Europe's; in Japan and Korea the world release, then Europe's and the
     * USA's; anywhere else, Europe's (a PAL release) before the world's and the USA's. Japan last, as most
     * players outside it can't read its releases.
     */
    public static List<String> forLocale(Locale locale) {
        String country = locale.getCountry().toUpperCase(Locale.ROOT);
        Set<String> order = new LinkedHashSet<>();
        String own = BY_COUNTRY.get(country);
        if (own != null) order.add(own);
        if (country.isEmpty() || AMERICAS.contains(country)) {
            order.addAll(List.of("World", "USA", "Europe"));
        } else if (ASIA.contains(country)) {
            order.addAll(List.of("World", "Europe", "USA"));
        } else {
            order.addAll(List.of("Europe", "World", "USA"));
        }
        order.add("Japan");
        return List.copyOf(order);
    }

    /** The ranking in force: the user's own, else the one for {@code locale}. */
    public static List<String> effective(List<String> setting, Locale locale) {
        return setting.isEmpty() ? forLocale(locale) : List.copyOf(setting);
    }

    /**
     * Reads a ranking as typed, regions by name split on commas, any case ({@code europe, usa}): their names as
     * {@link RomName#REGIONS} writes them, without repeats. Blank is no ranking of the user's own (empty). Null
     * when a word is no region; {@link #unknown} says which.
     */
    public static @Nullable List<String> parse(String text) {
        if (text.isBlank()) return List.of();
        Set<String> regions = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            String region = canonical(part.strip());
            if (region == null) return null;
            regions.add(region);
        }
        return List.copyOf(regions);
    }

    /** The first word of {@code text} that is no region, or null when every one is. */
    public static @Nullable String unknown(String text) {
        if (text.isBlank()) return null;
        for (String part : text.split(",")) {
            if (canonical(part.strip()) == null) return part.strip();
        }
        return null;
    }

    /** A ranking as the field and the prompt show it: {@code Europe, World, USA, Japan}. */
    public static String format(List<String> regions) {
        return String.join(", ", regions);
    }

    private static @Nullable String canonical(String name) {
        for (String region : RomName.REGIONS) {
            if (region.equalsIgnoreCase(name)) return region;
        }
        return null;
    }

    /** {@code regions} without the ones no name gives: what a hand-edited settings file may hold. */
    public static List<String> known(@Nullable List<String> regions) {
        List<String> known = new ArrayList<>();
        if (regions == null) return known;
        for (String region : regions) {
            String name = region == null ? null : canonical(region.strip());
            if (name != null && !known.contains(name)) known.add(name);
        }
        return known;
    }
}
