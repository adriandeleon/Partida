package com.partida.ui;

import java.util.Locale;

/**
 * Settings search scoring (SET-03). A row is found by its title, description, visible text (its
 * controls' labels), card heading, page name and English keywords. Title hits rank above description
 * and visible-text hits, which rank above heading, page and keyword hits; whole words above word starts
 * above anywhere. Every word of the query has to match somewhere. Pure, so the ranking is unit-tested.
 */
public final class SettingsSearch {

    /**
     * What a row can be found by, with each field also in lower case, made once when the row is (PERF-09): a
     * search runs over every row on each pause in typing and lowercases nothing.
     */
    public record Entry(
            String title,
            String description,
            String visibleText,
            String context,
            String keywords,
            String lowerTitle,
            String lowerDescription,
            String lowerVisibleText,
            String lowerRest) {

        public Entry(String title, String description, String visibleText, String context, String keywords) {
            this(
                    title,
                    description,
                    visibleText,
                    context,
                    keywords,
                    title.toLowerCase(Locale.ROOT),
                    description.toLowerCase(Locale.ROOT),
                    visibleText.toLowerCase(Locale.ROOT),
                    (context + " " + keywords).toLowerCase(Locale.ROOT));
        }

        public Entry(String title, String description, String context, String keywords) {
            this(title, description, "", context, keywords);
        }
    }

    /** A query, read once for a whole search: its words in lower case, and the text as typed. */
    public record Query(String text, String[] words) {
        public static Query of(String text) {
            String trimmed = text.trim();
            return new Query(
                    trimmed,
                    trimmed.isEmpty()
                            ? new String[0]
                            : trimmed.toLowerCase(Locale.ROOT).split("\\s+"));
        }
    }

    private SettingsSearch() {}

    /** The row's score for {@code query}; 0 means no match. A blank query matches everything with 1. */
    public static int score(String query, Entry entry) {
        return score(Query.of(query), entry);
    }

    /** {@link #score(String, Entry)} for a query read once: what a search over many rows uses. */
    public static int score(Query query, Entry entry) {
        if (query.words().length == 0) return 1;
        int total = 0;
        for (String word : query.words()) {
            int best = Math.max(
                    lowered(word, entry.lowerTitle()) * 4,
                    Math.max(
                            Math.max(lowered(word, entry.lowerDescription()), lowered(word, entry.lowerVisibleText()))
                                    * 2,
                            lowered(word, entry.lowerRest())));
            if (best == 0) return 0;
            total += best;
        }
        // A query that is a row's whole title names that row: it wins any tie.
        return entry.title().equalsIgnoreCase(query.text()) ? total + 100 : total;
    }

    /** 3 for a whole word, 2 for a word start, 1 anywhere, 0 for no match. */
    static int field(String word, String text) {
        return lowered(word, text.toLowerCase(Locale.ROOT));
    }

    /** {@link #field} over a text already in lower case. */
    private static int lowered(String word, String t) {
        int best = 0;
        for (int at = t.indexOf(word); at >= 0; at = t.indexOf(word, at + 1)) {
            boolean start = at == 0 || !Character.isLetterOrDigit(t.charAt(at - 1));
            int end = at + word.length();
            boolean whole = start && (end == t.length() || !Character.isLetterOrDigit(t.charAt(end)));
            best = Math.max(best, whole ? 3 : start ? 2 : 1);
        }
        return best;
    }
}
