package com.partida.ui;

import java.nio.file.Path;
import java.util.List;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameTitle;
import com.partida.library.RomName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The game browser's rules, without a toolkit (QUAL-05). */
class BrowseRulesTest {

    private static final String NES = "Nintendo - Nintendo Entertainment System";

    @BeforeEach
    void english() {
        Messages.init("en");
    }

    private static List<GameTitle> titles(String... files) {
        return GameTitle.group(java.util.Arrays.stream(files)
                .map(f -> new Game("", Path.of("/roms", f), NES, null))
                .toList());
    }

    @Test
    void aRomReadsAsItsTags() {
        assertEquals(
                "Europe · Rev A · Verified", BrowseRules.describe(RomName.parse("Super Mario Bros. (E) (REVA) [!]")));
        assertEquals(
                "USA · Disc 2 · En, Fr · Beta · (SGB Enhanced)",
                BrowseRules.describe(RomName.parse("Game (USA) (Disc 2) (En,Fr) (Beta) (SGB Enhanced)")));
        assertEquals("", BrowseRules.describe(RomName.parse("Tetris")));
        assertEquals("Rev 1", BrowseRules.revision("REV01"));
        assertEquals("Rev 2", BrowseRules.revision("Rev 2"));
        assertEquals("v1.1", BrowseRules.revision("v1.1"));
        assertEquals("PRG1", BrowseRules.revision("PRG1"));
    }

    @Test
    void aGameReadsAsItsRomsAndRegions() {
        GameTitle mario = titles(
                        "Super Mario Bros. (E) [!].nes",
                        "Super Mario Bros. (U) [!].nes",
                        "Super Mario Bros. (U) [b1].nes")
                .getFirst();
        assertEquals("2 ROMs · Europe, USA", BrowseRules.summary(mario, false));
        assertEquals("3 ROMs · Europe, USA", BrowseRules.summary(mario, true));
        assertEquals("Super Mario Bros., 2 ROMs · Europe, USA", BrowseRules.accessible(mario, false));
        GameTitle tetris = titles("Tetris.nes").getFirst();
        assertEquals("", BrowseRules.summary(tetris, false));
        assertEquals("Tetris", BrowseRules.accessible(tetris, false));
        assertEquals("USA", BrowseRules.summary(titles("Contra (U).nes").getFirst(), false));
        assertEquals(
                "2 ROMs",
                BrowseRules.summary(titles("Pong.nes", "Pong (a1).nes").getFirst(), false));
    }

    @Test
    void typeAheadFindsTheNextGameStartingSoArticlesAside() {
        List<GameTitle> games =
                titles("Contra (U).nes", "Castlevania (U).nes", "Legend of Zelda, The (U).nes", "Zelda II (U).nes");
        // Title order: Castlevania, Contra, The Legend of Zelda, Zelda II.
        assertEquals(0, BrowseRules.typeAhead(games, "c", 0));
        assertEquals(1, BrowseRules.typeAhead(games, "c", 1), "from the one after the selection");
        assertEquals(0, BrowseRules.typeAhead(games, "c", 2), "wrapping round");
        assertEquals(1, BrowseRules.typeAhead(games, "co", 0));
        assertEquals(2, BrowseRules.typeAhead(games, "l", 0), "The Legend of Zelda is under L");
        assertEquals(2, BrowseRules.typeAhead(games, "the l", 0), "and under The");
        assertEquals(-1, BrowseRules.typeAhead(games, "q", 0));
        assertEquals(-1, BrowseRules.typeAhead(List.of(), "c", 0));
        assertEquals(-1, BrowseRules.typeAhead(games, " ", 0));
        assertEquals("co", BrowseRules.typed("c", 1000, 'o', 1500));
        assertEquals("o", BrowseRules.typed("c", 1000, 'o', 2500), "a pause starts again");
    }

    @Test
    void theGridFitsItsTilesAndMovesByTileAndRow() {
        assertEquals(3, BrowseRules.columns(420, 132, 10));
        assertEquals(1, BrowseRules.columns(50, 132, 10), "at least one");
        assertEquals(1, BrowseRules.columns(0, 132, 10));
        // 10 tiles, 4 across: rows 0-3, 4-7, 8-9.
        assertEquals(4, BrowseRules.gridMove("RIGHT", 3, 10, 4));
        assertEquals(9, BrowseRules.gridMove("RIGHT", 9, 10, 4), "the last stays");
        assertEquals(0, BrowseRules.gridMove("LEFT", 0, 10, 4));
        assertEquals(1, BrowseRules.gridMove("UP", 5, 10, 4));
        assertEquals(2, BrowseRules.gridMove("UP", 2, 10, 4), "the top row stays");
        assertEquals(9, BrowseRules.gridMove("DOWN", 6, 10, 4), "into a shorter last row: its last tile");
        assertEquals(9, BrowseRules.gridMove("DOWN", 9, 10, 4));
        assertEquals(5, BrowseRules.gridMove("DOWN", 1, 10, 4));
        assertEquals(0, BrowseRules.gridMove("HOME", 7, 10, 4));
        assertEquals(9, BrowseRules.gridMove("END", 0, 10, 4));
        assertEquals(-1, BrowseRules.gridMove("A", 0, 10, 4));
        assertEquals(-1, BrowseRules.gridMove("DOWN", 0, 0, 4));
        assertEquals(List.of(List.of(1, 2), List.of(3)), BrowseRules.rows(List.of(1, 2, 3), 2));
        assertEquals(List.of(), BrowseRules.rows(List.of(), 3));
    }

    @Test
    void initialsAreTheFirstTwoWords() {
        assertEquals("SM", BrowseRules.initials("Super Mario Bros."));
        assertEquals("TL", BrowseRules.initials("The Legend of Zelda - A Link"));
        assertEquals("T", BrowseRules.initials("Tetris"));
        assertEquals("", BrowseRules.initials("!!"));
    }
}
