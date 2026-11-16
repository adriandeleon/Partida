package com.partida.library;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * What a ROM's name says about it, read from the two naming schemes collections use: No-Intro and Redump
 * ({@code Super Mario Bros. (Europe) (Rev A)}, {@code (USA, Europe)}, {@code (En,Fr,De)}, {@code (Beta)},
 * {@code (Disc 1)}) and GoodTools ({@code Super Mario Bros. (E) (REVA) [!]}, {@code (U)}, {@code (JU)},
 * {@code [b1]}, {@code [h2C]}, {@code [T+Eng]}).
 *
 * <p>The {@link #title} is the name before its first tag, with a trailing article moved to the front
 * ({@code Legend of Zelda, The} → {@code The Legend of Zelda}); the {@link #key} is that title made
 * comparable, so every variant of one game has the same key. A tag this class does not know is kept in
 * {@link #other} as written, so nothing a name says is lost.
 *
 * @param title the game's name, for display
 * @param key the game's name for grouping: lower case, letters and digits only, the article in front
 * @param regions the regions, by their No-Intro names ({@code USA}, {@code Europe}, {@code Japan}, …), in the
 *     order written
 * @param languages the languages, as written ({@code En}, {@code Fr}); a translation's target is in {@code other}
 * @param revision the revision as written ({@code Rev A}, {@code REV01}, {@code v1.1}, {@code PRG1}), or empty
 * @param revisionRank the revision as a number to compare: none is 0, {@code Rev A} and {@code Rev 1} are 1
 * @param disc the disc number of a multi-disc game, or 0
 * @param flags what the tags say about the dump and the release
 * @param other the tags it does not know, as written, brackets included
 */
public record RomName(
        String title,
        String key,
        List<String> regions,
        List<String> languages,
        String revision,
        double revisionRank,
        int disc,
        Set<Flag> flags,
        List<String> other) {

    /** What a tag says about a ROM. */
    public enum Flag {
        /** GoodTools {@code [!]}: a verified good dump. */
        VERIFIED,
        /** GoodTools {@code [a]}: an alternate dump of the same release. */
        ALTERNATE,
        /** GoodTools {@code [b]}, {@code [x]}: a bad dump. */
        BAD,
        /** GoodTools {@code [o]}: an overdump, padded past the cartridge's size. */
        OVERDUMP,
        /** GoodTools {@code [h]}, No-Intro {@code (Hack)}: a hack. */
        HACK,
        /** GoodTools {@code [t]}: a trainer added. */
        TRAINER,
        /** GoodTools {@code [f]}: fixed to run on emulators or copiers. */
        FIXED,
        /** GoodTools {@code [T+…]}, {@code [T-…]}: a fan translation. */
        TRANSLATION,
        /** {@code (Beta)}. */
        BETA,
        /** {@code (Proto)}, {@code (Prototype)}. */
        PROTO,
        /** {@code (Demo)}, {@code (Sample)}, {@code (Kiosk)}. */
        DEMO,
        /** {@code (Unl)}: an unlicensed release. */
        UNLICENSED,
        /** {@code (Pirate)}, GoodTools {@code [p]}: a pirate release. */
        PIRATE;

        /** The flags that hide a ROM from browsing unless asked for: everything but a clean release. */
        public static final Set<Flag> HIDDEN =
                EnumSet.of(BAD, OVERDUMP, HACK, TRAINER, FIXED, TRANSLATION, BETA, PROTO, DEMO, UNLICENSED, PIRATE);
    }

    public RomName {
        regions = List.copyOf(regions);
        languages = List.copyOf(languages);
        flags = flags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(flags));
        other = List.copyOf(other);
    }

    /** Whether a tag hides this ROM from browsing (a bad dump, a hack, a beta, …): {@link Flag#HIDDEN}. */
    public boolean hidden() {
        for (Flag flag : flags) {
            if (Flag.HIDDEN.contains(flag)) return true;
        }
        return false;
    }

    public boolean has(Flag flag) {
        return flags.contains(flag);
    }

    // ---- parsing ----

    /** A tag: a {@code (...)} or {@code [...]} group, with the text between tags skipped. */
    private static final Pattern TAG = Pattern.compile("\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]");

    /** A trailing article, as in {@code Legend of Zelda, The} or {@code Legend of Zelda, The - A Link…}. */
    private static final Pattern ARTICLE =
            Pattern.compile("^(.+?), (The|A|An|Les|Le|La|L'|Die|Der|Das|El|Los|Las|Il|Lo|Gli)(\\s*[-:].*)?$");

    /** No-Intro's region names, and the other spellings collections use for them. */
    private static final Map<String, String> REGION_NAMES = Map.ofEntries(
            Map.entry("usa", "USA"),
            Map.entry("europe", "Europe"),
            Map.entry("japan", "Japan"),
            Map.entry("world", "World"),
            Map.entry("asia", "Asia"),
            Map.entry("australia", "Australia"),
            Map.entry("brazil", "Brazil"),
            Map.entry("canada", "Canada"),
            Map.entry("china", "China"),
            Map.entry("france", "France"),
            Map.entry("germany", "Germany"),
            Map.entry("hong kong", "Hong Kong"),
            Map.entry("italy", "Italy"),
            Map.entry("korea", "Korea"),
            Map.entry("netherlands", "Netherlands"),
            Map.entry("russia", "Russia"),
            Map.entry("spain", "Spain"),
            Map.entry("sweden", "Sweden"),
            Map.entry("taiwan", "Taiwan"),
            Map.entry("uk", "UK"),
            Map.entry("united kingdom", "UK"),
            Map.entry("scandinavia", "Scandinavia"),
            Map.entry("greece", "Greece"),
            Map.entry("finland", "Finland"),
            Map.entry("denmark", "Denmark"),
            Map.entry("norway", "Norway"),
            Map.entry("portugal", "Portugal"),
            Map.entry("poland", "Poland"),
            Map.entry("latin america", "Latin America"),
            Map.entry("unknown", "Unknown"));

    /** GoodTools' region codes, a tag of its own: {@code (U)}, {@code (E)}, {@code (JU)}, {@code (UK)}. */
    private static final Map<String, List<String>> GOOD_REGIONS = Map.ofEntries(
            Map.entry("u", List.of("USA")),
            Map.entry("e", List.of("Europe")),
            Map.entry("j", List.of("Japan")),
            Map.entry("w", List.of("World")),
            Map.entry("a", List.of("Australia")),
            Map.entry("b", List.of("Brazil")),
            Map.entry("c", List.of("China")),
            Map.entry("f", List.of("France")),
            Map.entry("fc", List.of("Canada")),
            Map.entry("fn", List.of("Finland")),
            Map.entry("g", List.of("Germany")),
            Map.entry("gr", List.of("Greece")),
            Map.entry("hk", List.of("Hong Kong")),
            Map.entry("h", List.of("Netherlands")),
            Map.entry("nl", List.of("Netherlands")),
            Map.entry("i", List.of("Italy")),
            Map.entry("k", List.of("Korea")),
            Map.entry("s", List.of("Spain")),
            Map.entry("sw", List.of("Sweden")),
            Map.entry("uk", List.of("UK")),
            Map.entry("unk", List.of("Unknown")),
            Map.entry("1", List.of("Japan", "Korea")),
            Map.entry("4", List.of("USA", "Brazil")));

    /** No-Intro's language codes: two letters, a capital first ({@code En}, {@code Fr}, {@code Pt-BR}). */
    private static final Pattern LANGUAGE = Pattern.compile("[A-Z][a-z](-[A-Z]{2})?");

    /** {@code Rev A}, {@code Rev 1}, {@code REVA}, {@code REV01}, {@code v1.1}, {@code V1.0a}, {@code PRG1}. */
    private static final Pattern REVISION =
            Pattern.compile("(?i)(?:rev\\s*([0-9]+|[a-z])|v\\s*([0-9]+(?:\\.[0-9]+)*[a-z]?)|prg\\s*([0-9]+))");

    private static final Pattern DISC = Pattern.compile("(?i)(?:disc|disk|cd)\\s*([0-9]+)(?:\\s*of\\s*[0-9]+)?");

    /**
     * A GoodTools code with what follows it: {@code b}, {@code b1}, {@code h2C}, {@code hM04} (a mapper hack),
     * {@code hI} (an intro hack), {@code a3}, {@code o1}.
     */
    private static final Pattern GOOD_CODE = Pattern.compile("([abfhoptx])([0-9A-Z][0-9a-zA-Z+]*)?");

    /** Reads {@code name}, a file's stem or a playlist's label. */
    public static RomName parse(String name) {
        String stem = name.strip();
        Matcher tags = TAG.matcher(stem);
        int first = tags.find() ? tags.start() : stem.length();
        String title = articleFirst(stem.substring(0, first).strip());
        if (title.isEmpty()) title = stem;

        Set<String> regions = new LinkedHashSet<>();
        List<String> languages = new ArrayList<>();
        String revision = "";
        double revisionRank = 0;
        int disc = 0;
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        List<String> other = new ArrayList<>();

        tags.reset();
        while (tags.find()) {
            boolean square = tags.group(2) != null;
            String body = (square ? tags.group(2) : tags.group(1)).strip();
            String lower = body.toLowerCase(Locale.ROOT);
            if (square ? squareTag(body, flags) : parenTag(body, lower, regions, languages, flags)) continue;
            Matcher rev = REVISION.matcher(body);
            if (revision.isEmpty() && rev.matches()) {
                revision = body;
                revisionRank = rank(rev);
                continue;
            }
            Matcher discs = DISC.matcher(body);
            if (disc == 0 && discs.matches()) {
                disc = Integer.parseInt(discs.group(1));
                continue;
            }
            other.add(square ? "[" + body + "]" : "(" + body + ")");
        }
        return new RomName(
                title, key(title), List.copyOf(regions), languages, revision, revisionRank, disc, flags, other);
    }

    /** A {@code (...)} tag it knows: regions, languages, or a release kind. */
    private static boolean parenTag(
            String body, String lower, Set<String> regions, List<String> languages, Set<Flag> flags) {
        switch (lower) {
            case "beta" -> flags.add(Flag.BETA);
            case "proto", "prototype" -> flags.add(Flag.PROTO);
            case "demo", "sample", "kiosk", "kiosk demo", "trial" -> flags.add(Flag.DEMO);
            case "unl", "unlicensed" -> flags.add(Flag.UNLICENSED);
            case "pirate" -> flags.add(Flag.PIRATE);
            case "hack" -> flags.add(Flag.HACK);
            default -> {
                if (lower.matches("(beta|proto|prototype|demo|sample)\\s*[0-9a-z.]*")) {
                    flags.add(
                            lower.startsWith("beta") ? Flag.BETA : lower.startsWith("proto") ? Flag.PROTO : Flag.DEMO);
                    return true;
                }
                List<String> good = goodRegions(lower);
                if (good != null) {
                    regions.addAll(good);
                    return true;
                }
                List<String> named = namedRegions(lower);
                if (named != null) {
                    regions.addAll(named);
                    return true;
                }
                List<String> codes = languages(body);
                if (codes != null) {
                    languages.addAll(codes);
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /** A {@code [...]} tag it knows: GoodTools' dump codes. */
    private static boolean squareTag(String body, Set<Flag> flags) {
        if (body.equals("!")) {
            flags.add(Flag.VERIFIED);
            return true;
        }
        if (body.startsWith("T+") || body.startsWith("T-")) {
            flags.add(Flag.TRANSLATION);
            return true;
        }
        Matcher code = GOOD_CODE.matcher(body);
        if (!code.matches()) return false;
        flags.add(
                switch (code.group(1)) {
                    case "a" -> Flag.ALTERNATE;
                    case "b", "x" -> Flag.BAD;
                    case "f" -> Flag.FIXED;
                    case "h" -> Flag.HACK;
                    case "o" -> Flag.OVERDUMP;
                    case "p" -> Flag.PIRATE;
                    default -> Flag.TRAINER; // "t"
                });
        return true;
    }

    /** GoodTools' region code, or a run of {@code J}, {@code U} and {@code E} ({@code JU}, {@code UE}). */
    private static @Nullable List<String> goodRegions(String lower) {
        List<String> one = GOOD_REGIONS.get(lower);
        if (one != null) return one;
        if (!lower.matches("[jue]{2,3}")) return null;
        List<String> regions = new ArrayList<>();
        for (char c : lower.toCharArray()) regions.addAll(GOOD_REGIONS.get(String.valueOf(c)));
        return regions;
    }

    /** No-Intro's regions, one or several: {@code USA}, {@code USA, Europe}. */
    private static @Nullable List<String> namedRegions(String lower) {
        List<String> regions = new ArrayList<>();
        for (String part : lower.split(",")) {
            String region = REGION_NAMES.get(part.strip());
            if (region == null) return null;
            regions.add(region);
        }
        return regions;
    }

    /** No-Intro's languages: {@code En}, {@code En,Fr,De}. */
    private static @Nullable List<String> languages(String body) {
        List<String> codes = new ArrayList<>();
        for (String part : body.split(",")) {
            String code = part.strip();
            if (!LANGUAGE.matcher(code).matches()) return null;
            codes.add(code);
        }
        return codes;
    }

    /** A revision as a number: a letter by its place in the alphabet, a version by its value. */
    private static double rank(Matcher rev) {
        for (int group = 1; group <= 3; group++) {
            String value = rev.group(group);
            if (value == null) continue;
            if (value.length() == 1 && Character.isLetter(value.charAt(0))) {
                return Character.toLowerCase(value.charAt(0)) - 'a' + 1;
            }
            String digits = value.replaceAll("[a-zA-Z]$", "");
            String[] parts = digits.split("\\.");
            double number = Integer.parseInt(parts[0]);
            double scale = 1;
            for (int i = 1; i < parts.length; i++) {
                scale /= 1000;
                number += Integer.parseInt(parts[i]) * scale;
            }
            return number;
        }
        return 0;
    }

    /** {@code Legend of Zelda, The - A Link to the Past} → {@code The Legend of Zelda - A Link to the Past}. */
    static String articleFirst(String title) {
        Matcher m = ARTICLE.matcher(title);
        if (!m.matches()) return title;
        String article = m.group(2);
        String rest = m.group(3) == null ? "" : m.group(3);
        return article + (article.endsWith("'") ? "" : " ") + m.group(1) + rest;
    }

    /**
     * The grouping key of a title: lower case, accents and punctuation gone, {@code &} read as {@code and}, so
     * {@code Super Mario Bros.} and {@code Super Mario Bros} are one game.
     */
    static String key(String title) {
        String plain = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ");
        return plain.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
