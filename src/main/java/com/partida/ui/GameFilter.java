package com.partida.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.partida.library.Game;
import org.jspecify.annotations.Nullable;

/**
 * What a system's filter box asks for (FIND-13), read from its text: plain words, which a game's title must all
 * hold; {@code key:value} terms, which become chips (a file extension, a core, a system); or, after {@code >},
 * the title of a command to run. Pure: no JavaFX, so the rules are tested without a toolkit (QUAL-05).
 */
final class GameFilter {

    /** The keys a term can name: {@code ext:zip}, {@code core:snes9x}, {@code system:snes}. */
    static final Set<String> KEYS = Set.of("ext", "core", "system");

    /** One {@code key:value} term, lower case. */
    record Term(String key, String value) {
        /** As it is typed, and as its chip reads. */
        String text() {
            return key + ":" + value;
        }
    }

    /**
     * The command a {@code >} query runs: of {@code commands} (id to title), the one whose title is the query,
     * else the first whose title starts with it, else the first that holds it, ignoring case and a trailing "…".
     * Empty for an empty query or no match.
     */
    static Optional<String> commandFor(String query, java.util.Map<String, String> commands) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return Optional.empty();
        String starts = null;
        String holds = null;
        for (java.util.Map.Entry<String, String> c : commands.entrySet()) {
            String title =
                    c.getValue().toLowerCase(Locale.ROOT).replace("…", "").strip();
            if (title.equals(q)) return Optional.of(c.getKey());
            if (starts == null && title.startsWith(q)) starts = c.getKey();
            if (holds == null && title.contains(q)) holds = c.getKey();
        }
        return Optional.ofNullable(starts != null ? starts : holds);
    }

    /** What a key in the filter box does (QUAL-05): where it leads is decided here, the box only acts on it. */
    enum KeyAction {
        /** Nothing of the box's own: the field types. */
        NONE,
        /** Empty the box and its chips (FIND-14's first Escape). */
        CLEAR,
        /** Leave for the list (FIND-07). */
        TO_LIST,
        /** Make the term just typed a chip. */
        CHIP,
        /** Take the last chip back. */
        UNCHIP,
        /** Run the {@code >} query's command. */
        RUN
    }

    /**
     * Where {@code key} (a key code's name: ESCAPE, ENTER, SPACE, DOWN, BACK_SPACE) leads in a box holding
     * {@code text} with the caret at {@code caret}, a selection or not, and chips or not. Pure.
     */
    static KeyAction keyAction(String key, String text, int caret, boolean selection, boolean chips) {
        return switch (key) {
            case "ESCAPE" -> text.isEmpty() && !chips ? KeyAction.TO_LIST : KeyAction.CLEAR;
            case "SPACE" -> chipAt(text, caret).isPresent() ? KeyAction.CHIP : KeyAction.NONE;
            case "BACK_SPACE" -> caret == 0 && !selection && chips ? KeyAction.UNCHIP : KeyAction.NONE;
            case "ENTER" ->
                command(text) != null
                        ? KeyAction.RUN
                        : chipAt(text, caret).isPresent() ? KeyAction.CHIP : KeyAction.TO_LIST;
            case "DOWN" -> KeyAction.TO_LIST;
            default -> KeyAction.NONE;
        };
    }

    /** The term that ends {@code text} with the caret at its end, ready to be a chip; empty in a command. */
    static Optional<Term> chipAt(String text, int caret) {
        if (caret != text.length() || command(text) != null) return Optional.empty();
        return term(text.substring(text.lastIndexOf(' ') + 1));
    }

    /** The plain words and the terms of a query. */
    record Query(List<String> words, List<Term> terms) {
        boolean isEmpty() {
            return words.isEmpty() && terms.isEmpty();
        }
    }

    private GameFilter() {}

    /** {@code word} as a term when it names a known key and a value ({@code ext:zip}), else empty. */
    static Optional<Term> term(String word) {
        int colon = word.indexOf(':');
        if (colon <= 0 || colon == word.length() - 1) return Optional.empty();
        String key = word.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!KEYS.contains(key)) return Optional.empty();
        return Optional.of(new Term(key, word.substring(colon + 1).toLowerCase(Locale.ROOT)));
    }

    /** The command a {@code >} query names, the rest stripped, or null when the text is no command. */
    static @Nullable String command(String text) {
        String t = text.stripLeading();
        return t.startsWith(">") ? t.substring(1).strip() : null;
    }

    /** The box's text, with the chips already made, as one query. A command query filters nothing. */
    static Query parse(String text, List<Term> chips) {
        List<String> words = new ArrayList<>();
        List<Term> terms = new ArrayList<>(chips);
        if (command(text) == null) {
            for (String word : text.strip().split("\\s+")) {
                if (word.isEmpty()) continue;
                Optional<Term> term = term(word);
                if (term.isPresent()) terms.add(term.get());
                else words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        return new Query(List.copyOf(words), List.copyOf(terms));
    }

    /** A game with its title and system in lower case, made once per load, not per keystroke (PERF-09). */
    record Indexed(Game game, String title, String system, String extension, String core) {
        static Indexed of(Game game) {
            Path core = game.core();
            return new Indexed(
                    game,
                    (game.title().isBlank() ? game.fileStem() : game.title()).toLowerCase(Locale.ROOT),
                    game.system().toLowerCase(Locale.ROOT),
                    game.extension(),
                    core == null ? "" : String.valueOf(core.getFileName()).toLowerCase(Locale.ROOT));
        }
    }

    /** Whether {@code game} is kept: its title holds every word, and it meets every term. */
    static boolean matches(Game game, Query query) {
        return matches(Indexed.of(game), query);
    }

    /** {@link #matches(Game, Query)} over a game whose lower-case text is made already. */
    static boolean matches(Indexed indexed, Query query) {
        Game game = indexed.game();
        String title = indexed.title();
        for (String word : query.words()) {
            if (!title.contains(word)) return false;
        }
        for (Term term : query.terms()) {
            if (!meets(indexed, term)) return false;
        }
        return true;
    }

    private static boolean meets(Indexed indexed, Term term) {
        return switch (term.key()) {
            case "ext" -> indexed.extension().equals(term.value());
            case "core" -> !indexed.core().isEmpty() && indexed.core().contains(term.value());
            default -> indexed.system().contains(term.value());
        };
    }
}
