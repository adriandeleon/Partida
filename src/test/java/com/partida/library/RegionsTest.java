package com.partida.library;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The region ranking: the user's own, or the one for the system's country. */
class RegionsTest {

    @Test
    void eachCountryRanksItsOwnReleaseFirst() {
        assertEquals(List.of("USA", "World", "Europe", "Japan"), Regions.forLocale(Locale.US));
        assertEquals(List.of("Spain", "Europe", "World", "USA", "Japan"), Regions.forLocale(Locale.of("es", "ES")));
        assertEquals(List.of("UK", "Europe", "World", "USA", "Japan"), Regions.forLocale(Locale.UK));
        assertEquals(List.of("Brazil", "World", "USA", "Europe", "Japan"), Regions.forLocale(Locale.of("pt", "BR")));
        assertEquals(List.of("World", "USA", "Europe", "Japan"), Regions.forLocale(Locale.of("es", "MX")));
        assertEquals(List.of("Japan", "World", "Europe", "USA"), Regions.forLocale(Locale.JAPAN));
        assertEquals(List.of("Australia", "Europe", "World", "USA", "Japan"), Regions.forLocale(Locale.of("en", "AU")));
        assertEquals(List.of("Europe", "World", "USA", "Japan"), Regions.forLocale(Locale.of("en", "IN")));
        assertEquals(List.of("World", "USA", "Europe", "Japan"), Regions.forLocale(Locale.ENGLISH), "no country");
    }

    @Test
    void theUsersRankingWinsWhenThereIsOne() {
        assertEquals(List.of("Japan"), Regions.effective(List.of("Japan"), Locale.US));
        assertEquals(Regions.forLocale(Locale.US), Regions.effective(List.of(), Locale.US));
    }

    @Test
    void aRankingIsTypedAsNamesSplitOnCommas() {
        assertEquals(List.of("Europe", "USA", "Hong Kong"), Regions.parse(" europe ,USA, hong kong, Europe"));
        assertEquals(List.of(), Regions.parse("  "));
        assertNull(Regions.parse("Europe, Narnia"));
        assertEquals("Narnia", Regions.unknown("Europe, Narnia"));
        assertNull(Regions.unknown("Europe, USA"));
        assertNull(Regions.unknown(""));
        assertEquals("Europe, USA", Regions.format(List.of("Europe", "USA")));
    }

    @Test
    void aHandEditedRankingKeepsOnlyRegions() {
        assertEquals(List.of("Europe", "USA"), Regions.known(Arrays.asList("europe", null, "Narnia", "USA", "EUROPE")));
        assertEquals(List.of(), Regions.known(null));
    }
}
