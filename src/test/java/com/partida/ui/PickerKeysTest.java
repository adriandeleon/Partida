package com.partida.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QUAL-05: where a key leads in each overlay, decided without a toolkit (PAL-06, QOPEN-06, TABS-03, FIND-02). */
class PickerKeysTest {

    @Test
    void theFixedKeysAndTheKeymapsChordsMoveTheSelection() {
        assertEquals(PickerKeys.Move.LINE_DOWN, PickerKeys.move("DOWN", false, null));
        assertEquals(PickerKeys.Move.LINE_UP, PickerKeys.move("UP", false, null));
        assertEquals(PickerKeys.Move.PAGE_DOWN, PickerKeys.move("PAGE_DOWN", false, null));
        assertEquals(PickerKeys.Move.PAGE_UP, PickerKeys.move("PAGE_UP", false, null));
        assertEquals(PickerKeys.Move.FIRST, PickerKeys.move("HOME", true, null));
        assertEquals(PickerKeys.Move.LAST, PickerKeys.move("END", true, null));
        assertEquals(PickerKeys.Move.NONE, PickerKeys.move("HOME", false, null), "plain Home is the caret's");
        assertEquals(PickerKeys.Move.NONE, PickerKeys.move("END", false, null));
        assertEquals(PickerKeys.Move.LINE_DOWN, PickerKeys.move("N", true, PickerKeys.LINE_DOWN));
        assertEquals(PickerKeys.Move.LINE_UP, PickerKeys.move("P", true, PickerKeys.LINE_UP));
        assertEquals(PickerKeys.Move.PAGE_DOWN, PickerKeys.move("V", true, PickerKeys.PAGE_DOWN));
        assertEquals(PickerKeys.Move.PAGE_UP, PickerKeys.move("V", false, PickerKeys.PAGE_UP));
        assertEquals(PickerKeys.Move.NONE, PickerKeys.move("S", true, "find.show"), "another command's chord");
        assertEquals(PickerKeys.Move.NONE, PickerKeys.move("A", false, null));
    }

    @Test
    void aFilterFieldLeadsIntoItsListAndAFindBarStepsAndCloses() {
        assertTrue(PickerKeys.intoList("DOWN", true));
        assertTrue(PickerKeys.intoList("ENTER", true));
        assertFalse(PickerKeys.intoList("DOWN", false), "an empty list keeps the focus in the field");
        assertFalse(PickerKeys.intoList("UP", true));
        assertEquals(PickerKeys.FindKey.NEXT, PickerKeys.findBar("ENTER", false));
        assertEquals(PickerKeys.FindKey.PREVIOUS, PickerKeys.findBar("ENTER", true));
        assertEquals(PickerKeys.FindKey.CLOSE, PickerKeys.findBar("ESCAPE", false));
        assertEquals(PickerKeys.FindKey.NONE, PickerKeys.findBar("A", false));
    }

    /** PERF-09: a search reads its query once and each row's lower-case text was made with the row. */
    @Test
    void aQueryIsReadOnceAndAnEntryLowercasesOnce() {
        SettingsSearch.Query query = SettingsSearch.Query.of("  Theme  DARK ");
        assertEquals("Theme  DARK", query.text());
        assertEquals(2, query.words().length);
        assertEquals(0, SettingsSearch.Query.of("   ").words().length);
        SettingsSearch.Entry entry = new SettingsSearch.Entry("Theme", "Light or Dark", "", "Appearance", "colors");
        assertEquals("light or dark", entry.lowerDescription());
        assertEquals("appearance colors", entry.lowerRest());
        assertEquals(SettingsSearch.score("theme dark", entry), SettingsSearch.score(query, entry));
        assertEquals(1, SettingsSearch.score(SettingsSearch.Query.of(""), entry));
        GameFilter.Indexed game = GameFilter.Indexed.of(new com.partida.library.Game(
                "Super Metroid", java.nio.file.Path.of("/r/sm.sfc"), "Nintendo - SNES", null));
        assertEquals("super metroid", game.title());
        assertTrue(GameFilter.matches(game, GameFilter.parse("METROID system:snes", java.util.List.of())));
        assertEquals("sfc", game.extension());
        assertEquals("", game.core(), "no core");
    }

    /** QOPEN-08, QUAL-05: a move skips unavailable rows, turns back at the end, and a page stays in the list. */
    @Test
    void aMoveLandsOnAnEnabledRowAndAPageStaysInTheList() {
        boolean[] enabled = {true, false, true, false};
        java.util.function.IntPredicate on = i -> enabled[i];
        assertEquals(2, PickerKeys.moveTarget(4, 0, 1, on), "over the unavailable row");
        assertEquals(2, PickerKeys.moveTarget(4, 2, 1, on), "nothing enabled further down: back to the nearest");
        assertEquals(0, PickerKeys.moveTarget(4, 2, -1, on));
        assertEquals(0, PickerKeys.moveTarget(4, 0, -5, on), "held to the list");
        assertEquals(-1, PickerKeys.moveTarget(0, -1, 1, on), "an empty list");
        assertEquals(-1, PickerKeys.moveTarget(2, 0, 1, i -> false), "no row enabled");
        assertEquals(2, PickerKeys.enabledFrom(4, on, 1, 1));
        assertEquals(-1, PickerKeys.enabledFrom(4, on, 3, 1));
        assertEquals(9, PickerKeys.page(5, 10, 8, true));
        assertEquals(0, PickerKeys.page(5, 10, 8, false));
        assertEquals(4, PickerKeys.page(12, 20, 8, false));
    }
}
