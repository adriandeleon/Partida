package com.partida.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.partida.i18n.Messages;
import com.partida.library.GameTitle;
import com.partida.library.RomName;

/**
 * The game browser's rules, as pure decisions (QUAL-05): how a ROM and a game read, where type-ahead lands, how
 * the grid lays its tiles out and where its keys move. No JavaFX here, so each is unit-tested without a
 * toolkit.
 */
final class BrowseRules {

    private BrowseRules() {}

    /**
     * A ROM in a few words, the way its tags read to a person: its regions, its disc, its revision, its
     * languages and what its dump and release are ({@code Europe · Rev A · Verified}). Blank for a ROM whose name
     * has no tag.
     */
    static String describe(RomName rom) {
        List<String> parts = new ArrayList<>();
        if (!rom.regions().isEmpty()) parts.add(String.join(", ", rom.regions()));
        if (rom.disc() > 0) parts.add(Messages.tr("rom.disc", rom.disc()));
        if (!rom.revision().isEmpty()) parts.add(revision(rom.revision()));
        if (!rom.languages().isEmpty()) parts.add(String.join(", ", rom.languages()));
        for (RomName.Flag flag : RomName.Flag.values()) {
            if (rom.has(flag)) parts.add(Messages.tr("rom.flag." + flag.name().toLowerCase(Locale.ROOT)));
        }
        parts.addAll(rom.other());
        return String.join(" · ", parts);
    }

    /** A revision as No-Intro writes it: GoodTools' {@code REVA} and {@code REV01} read {@code Rev A}, {@code Rev 1}. */
    static String revision(String written) {
        java.util.regex.Matcher rev =
                java.util.regex.Pattern.compile("(?i)rev\\s*0*([0-9]+|[a-z])").matcher(written);
        if (rev.matches()) return "Rev " + rev.group(1).toUpperCase(Locale.ROOT);
        return written;
    }

    /** What a game's row says beside its title: how many ROMs it has, when more than one, and their regions. */
    static String summary(GameTitle title, boolean showHidden) {
        int roms = showHidden ? title.roms().size() : title.shown().size();
        String regions = String.join(", ", title.regions());
        if (roms <= 1) return regions;
        String count = Messages.tr("systems.roms", roms);
        return regions.isEmpty() ? count : count + " · " + regions;
    }

    /** A game read out by a screen reader (ACC-01): its title and its summary. */
    static String accessible(GameTitle title, boolean showHidden) {
        String summary = summary(title, showHidden);
        return summary.isEmpty() ? title.title() : title.title() + ", " + summary;
    }

    /**
     * Type-ahead in the games: the first game at or after {@code from} whose title, a leading article ignored,
     * starts with {@code typed}, wrapping round to the top; -1 when none does. The titles are compared as
     * {@link GameTitle#key()}: lower case, letters and digits.
     */
    static int typeAhead(List<GameTitle> titles, String typed, int from) {
        String prefix = GameTitle.sortKey(typed.toLowerCase(Locale.ROOT).strip());
        if (prefix.isEmpty() || titles.isEmpty()) return -1;
        int n = titles.size();
        int start = Math.floorMod(from, n);
        for (int i = 0; i < n; i++) {
            int at = (start + i) % n;
            GameTitle title = titles.get(at);
            if (GameTitle.sortKey(title.key()).startsWith(prefix) || title.key().startsWith(prefix)) return at;
        }
        return -1;
    }

    /** The letters typed so far: a new key within {@link #TYPE_AHEAD_MILLIS} of the last adds to them. */
    static final long TYPE_AHEAD_MILLIS = 1000;

    /** The type-ahead text after {@code c} at {@code now}, given what was typed before at {@code last}. */
    static String typed(String before, long last, char c, long now) {
        return (now - last <= TYPE_AHEAD_MILLIS ? before : "") + c;
    }

    /** How many tiles fit across {@code width}, each {@code tile} wide with {@code gap} between: at least one. */
    static int columns(double width, double tile, double gap) {
        if (width <= 0 || tile <= 0) return 1;
        return Math.max(1, (int) Math.floor((width + gap) / (tile + gap)));
    }

    /**
     * Where a key moves the selection in a grid of {@code size} tiles, {@code columns} across: the arrows by a
     * tile or a row, Home and End to the first and last; a move past an edge stays put, except Down into a
     * shorter last row, which lands on its last tile. -1 for a key that moves nothing, or no tiles.
     */
    static int gridMove(String key, int at, int size, int columns) {
        if (size == 0) return -1;
        int from = Math.clamp(at, 0, size - 1);
        int cols = Math.max(1, columns);
        return switch (key) {
            case "LEFT" -> Math.max(0, from - 1);
            case "RIGHT" -> Math.min(size - 1, from + 1);
            case "UP" -> from - cols >= 0 ? from - cols : from;
            case "DOWN" -> from + cols < size ? from + cols : (from / cols < (size - 1) / cols ? size - 1 : from);
            case "HOME" -> 0;
            case "END" -> size - 1;
            default -> -1;
        };
    }

    /** {@code items} cut into rows of {@code columns}, for the grid's virtual list of rows. */
    static <T> List<List<T>> rows(List<T> items, int columns) {
        int cols = Math.max(1, columns);
        List<List<T>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += cols)
            rows.add(List.copyOf(items.subList(i, Math.min(items.size(), i + cols))));
        return rows;
    }

    /** The first letters of a title's first two words, for a tile with no box art. */
    static String initials(String title) {
        StringBuilder out = new StringBuilder();
        for (String word : title.split("[\\s\\-:]+")) {
            if (word.isEmpty() || out.length() == 2) continue;
            int first = word.codePointAt(0);
            if (Character.isLetterOrDigit(first)) out.appendCodePoint(Character.toUpperCase(first));
        }
        return out.toString();
    }
}
