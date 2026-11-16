package com.partida.library;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grouping a system's ROMs into one entry per game, best ROM first. */
class GameTitleTest {

    private static final String NES = "Nintendo - Nintendo Entertainment System";
    private static final String SNES = "Nintendo - Super Nintendo Entertainment System";

    /** A ROM as a scanned folder has it: no label, the name in the file. */
    private static Game file(String system, String fileName) {
        return new Game("", Path.of("/roms", fileName), system, null);
    }

    private static List<String> files(GameTitle title) {
        return title.roms().stream().map(r -> r.game().contentName()).toList();
    }

    @Test
    void aGamesVariantsAreOneEntryWithItsRomsBestFirst() {
        List<GameTitle> titles = GameTitle.group(List.of(
                file(NES, "Super Mario Bros. (JU) [b1].nes"),
                file(NES, "Super Mario Bros. (E) (REVA) [!].nes"),
                file(NES, "Super Mario Bros. (W) [!].nes"),
                file(NES, "Super Mario Bros. (E) [a1].nes"),
                file(NES, "Super Mario Bros. (E).nes"),
                file(NES, "Contra (U) [!].nes")));
        assertEquals(
                List.of("Contra", "Super Mario Bros."),
                titles.stream().map(GameTitle::title).toList());
        GameTitle mario = titles.get(1);
        assertEquals(
                List.of(
                        "Super Mario Bros. (E) (REVA) [!].nes",
                        "Super Mario Bros. (W) [!].nes",
                        "Super Mario Bros. (E).nes",
                        "Super Mario Bros. (E) [a1].nes",
                        "Super Mario Bros. (JU) [b1].nes"),
                files(mario),
                "verified, then the latest revision, an alternate after the original, a bad dump last");
        assertEquals("Super Mario Bros. (E) (REVA) [!].nes", mario.best().game().contentName());
        assertEquals(4, mario.shown().size(), "the bad dump is kept but not shown");
        assertEquals(5, mario.roms().size());
        assertEquals(List.of("Europe", "World"), mario.regions());
        assertFalse(mario.hidden());
    }

    @Test
    void schemesAndPunctuationMeet() {
        List<GameTitle> titles = GameTitle.group(List.of(
                new Game("Legend of Zelda, The (U) (PRG1) [!]", Path.of("/a.nes"), NES, null),
                new Game("The Legend of Zelda (Europe) (Rev A)", Path.of("/b.nes"), NES, null),
                file(NES, "Legend of Zelda, The (U) (PRG0).nes")));
        assertEquals(1, titles.size());
        assertEquals("The Legend of Zelda", titles.getFirst().title());
        assertEquals(3, titles.getFirst().roms().size());
    }

    @Test
    void theSameGameOnTwoSystemsIsTwoEntries() {
        List<GameTitle> titles = GameTitle.group(
                List.of(file(NES, "Tetris (U) [!].nes"), file("Nintendo - Game Boy", "Tetris (W) (V1.1) [!].gb")));
        assertEquals(2, titles.size());
        assertTrue(titles.stream().allMatch(t -> t.title().equals("Tetris")));
    }

    @Test
    void aGameOfHiddenRomsOnlyIsHiddenAndShowsThemWhenAskedFor() {
        GameTitle hack = GameTitle.group(List.of(
                        file(SNES, "Super Mario World (U) [h1].smc"), file(SNES, "Super Mario World (U) [h2C].smc")))
                .getFirst();
        assertTrue(hack.hidden());
        assertEquals(2, hack.shown().size(), "all of them, since none is clean");
        GameTitle unlicensed = GameTitle.group(List.of(file(NES, "Super Donkey Kong 2 (Unl) [!].nes")))
                .getFirst();
        assertTrue(unlicensed.hidden());
    }

    @Test
    void discsOfOneGameAreOneEntryFirstDiscFirst() {
        String psx = "Sony - PlayStation";
        GameTitle ff7 = GameTitle.group(List.of(
                        file(psx, "Final Fantasy VII (USA) (Disc 3).chd"),
                        file(psx, "Final Fantasy VII (USA) (Disc 1).chd"),
                        file(psx, "Final Fantasy VII (USA) (Disc 2).chd")))
                .getFirst();
        assertEquals(
                List.of(
                        "Final Fantasy VII (USA) (Disc 1).chd",
                        "Final Fantasy VII (USA) (Disc 2).chd",
                        "Final Fantasy VII (USA) (Disc 3).chd"),
                files(ff7));
    }

    @Test
    void gamesAreInTitleOrderALeadingArticleIgnored() {
        List<GameTitle> titles = GameTitle.group(List.of(
                file(NES, "Zelda II - The Adventure of Link (U).nes"),
                file(NES, "Legend of Zelda, The (U).nes"),
                file(NES, "Kirby's Adventure (U).nes"),
                file(NES, "Adventures of Lolo (U).nes")));
        assertEquals(
                List.of(
                        "Adventures of Lolo",
                        "Kirby's Adventure",
                        "The Legend of Zelda",
                        "Zelda II - The Adventure of Link"),
                titles.stream().map(GameTitle::title).toList());
    }

    @Test
    void aGameHasARom() {
        assertThrows(IllegalArgumentException.class, () -> new GameTitle("k", "K", NES, List.of()));
        assertEquals("boy and his blob", GameTitle.sortKey("a boy and his blob"));
        assertEquals("a", GameTitle.sortKey("a"));
    }

    @Test
    void theRegionRankingComesBeforeTheDump() {
        List<Game> games = List.of(
                file(NES, "Contra (U) [!].nes"), file(NES, "Contra (E).nes"), file(NES, "Probotector (E) [!].nes"));
        GameTitle europe = GameTitle.group(games, List.of("Europe", "USA")).getFirst();
        assertEquals("Contra (E).nes", europe.best().game().contentName());
        GameTitle plain = GameTitle.group(games).getFirst();
        assertEquals("Contra (U) [!].nes", plain.best().game().contentName(), "unranked: the verified dump");
        assertEquals("Contra (E).nes", plain.best(List.of("Europe")).game().contentName());
    }

    @Test
    void aGameKeepsItsIdAcrossItsRoms() {
        GameTitle mario = GameTitle.group(
                        List.of(file(NES, "Super Mario Bros. (E) [!].nes"), file(NES, "Super Mario Bros (U).nes")))
                .getFirst();
        assertEquals(NES + " | super mario bros", mario.id());
        for (GameTitle.Rom rom : mario.roms()) assertEquals(mario.id(), GameTitle.idOf(rom.game()));
    }
}
