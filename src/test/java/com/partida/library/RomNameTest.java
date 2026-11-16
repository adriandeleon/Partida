package com.partida.library;

import java.util.List;
import java.util.Set;

import com.partida.library.RomName.Flag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a ROM's name says, read from real No-Intro, Redump and GoodTools names. */
class RomNameTest {

    /** Every variant of one game reads as the same title and key, whatever the scheme. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Super Mario Bros. (E) (REVA) [!]|Super Mario Bros.|super mario bros",
                "Super Mario Bros. (Europe) (Rev A)|Super Mario Bros.|super mario bros",
                "Super Mario Bros (W) [!]|Super Mario Bros|super mario bros",
                "Super Mario Bros. (JU) [b1]|Super Mario Bros.|super mario bros",
                "Legend of Zelda, The (U) (PRG1) [!]|The Legend of Zelda|the legend of zelda",
                "Legend of Zelda, The - A Link to the Past (USA)|The Legend of Zelda - A Link to the Past|"
                        + "the legend of zelda a link to the past",
                "The Legend of Zelda - A Link to the Past (Europe) (En,Fr,De)|"
                        + "The Legend of Zelda - A Link to the Past|the legend of zelda a link to the past",
                "Teenage Mutant Ninja Turtles II - The Arcade Game (A)|"
                        + "Teenage Mutant Ninja Turtles II - The Arcade Game|"
                        + "teenage mutant ninja turtles ii the arcade game",
                "Pokémon Red (UE) [S][!]|Pokémon Red|pokemon red",
                "Pokemon - Red Version (USA, Europe) (SGB Enhanced)|Pokemon - Red Version|pokemon red version",
                "Bomberman '93 (USA)|Bomberman '93|bomberman 93",
                "Ghosts'n Goblins (U) [!]|Ghosts'n Goblins|ghosts n goblins",
                "Mario & Luigi - Superstar Saga (USA)|Mario & Luigi - Superstar Saga|mario and luigi superstar saga",
                "Final Fantasy VII (USA) (Disc 1)|Final Fantasy VII|final fantasy vii",
                "Tetris|Tetris|tetris",
            })
    void aTitleAndItsKeyComeFromTheNameBeforeItsTags(String name, String title, String key) {
        RomName rom = RomName.parse(name);
        assertEquals(title, rom.title(), name);
        assertEquals(key, rom.key(), name);
    }

    @Test
    void regionsInEitherScheme() {
        assertEquals(
                List.of("Europe"),
                RomName.parse("Super Mario Bros. (E) (REVA) [!]").regions());
        assertEquals(List.of("USA"), RomName.parse("Contra (U) [!]").regions());
        assertEquals(List.of("Japan", "USA"), RomName.parse("Tetris (JU) [!]").regions());
        assertEquals(List.of("USA", "Europe"), RomName.parse("Tetris (UE)").regions());
        assertEquals(List.of("World"), RomName.parse("Tetris (W)").regions());
        assertEquals(
                List.of("Australia"),
                RomName.parse("Teenage Mutant Ninja Turtles II (A)").regions());
        assertEquals(
                List.of("USA", "Europe"),
                RomName.parse("Pokemon Red (USA, Europe)").regions());
        assertEquals(List.of("Japan", "Korea"), RomName.parse("Some Game (1)").regions());
        assertEquals(List.of(), RomName.parse("Homebrew Thing").regions());
    }

    @Test
    void languagesRevisionsAndDiscs() {
        RomName zelda = RomName.parse("Legend of Zelda, The - A Link to the Past (Europe) (En,Fr,De) (Rev 1)");
        assertEquals(List.of("En", "Fr", "De"), zelda.languages());
        assertEquals("Rev 1", zelda.revision());
        assertEquals(1, zelda.revisionRank());

        assertEquals(1, RomName.parse("Super Mario Bros. (E) (REVA) [!]").revisionRank());
        assertEquals(2, RomName.parse("Game (Rev B)").revisionRank());
        assertEquals(1, RomName.parse("Game (REV01)").revisionRank());
        assertEquals(1, RomName.parse("Zelda (U) (PRG1)").revisionRank());
        assertEquals(1.001, RomName.parse("Game (v1.1)").revisionRank(), 1e-9);
        assertTrue(RomName.parse("Game (V1.10)").revisionRank()
                > RomName.parse("Game (v1.9)").revisionRank());
        assertEquals(0, RomName.parse("Game (USA)").revisionRank());

        assertEquals(2, RomName.parse("Final Fantasy VII (USA) (Disc 2)").disc());
        assertEquals(1, RomName.parse("Policenauts (Japan) (Disk 1 of 3)").disc());
        assertEquals(0, RomName.parse("Final Fantasy VI (USA)").disc());
    }

    @Test
    void goodToolsDumpCodes() {
        assertEquals(Set.of(Flag.VERIFIED), RomName.parse("Contra (U) [!]").flags());
        assertEquals(Set.of(Flag.ALTERNATE), RomName.parse("Contra (U) [a1]").flags());
        assertEquals(Set.of(Flag.BAD), RomName.parse("Contra (U) [b2]").flags());
        assertEquals(Set.of(Flag.BAD), RomName.parse("Contra (U) [x]").flags());
        assertEquals(Set.of(Flag.OVERDUMP), RomName.parse("Contra (U) [o1]").flags());
        assertEquals(Set.of(Flag.HACK), RomName.parse("Contra (U) [h2C]").flags());
        assertEquals(Set.of(Flag.HACK), RomName.parse("Contra (U) [hM04]").flags());
        assertEquals(Set.of(Flag.TRAINER), RomName.parse("Contra (U) [t1]").flags());
        assertEquals(Set.of(Flag.FIXED), RomName.parse("Contra (U) [f1]").flags());
        assertEquals(Set.of(Flag.PIRATE), RomName.parse("Contra (U) [p1]").flags());
        assertEquals(
                Set.of(Flag.TRANSLATION),
                RomName.parse("Mother (J) [T+Eng1.0_Demiforce]").flags());
        assertEquals(
                Set.of(Flag.BAD, Flag.HACK),
                RomName.parse("Contra (U) [b1][h1]").flags());
    }

    @Test
    void releaseKinds() {
        assertEquals(
                Set.of(Flag.BETA), RomName.parse("Star Fox 2 (Japan) (Beta)").flags());
        assertEquals(
                Set.of(Flag.BETA), RomName.parse("Star Fox 2 (Japan) (Beta 2)").flags());
        assertEquals(Set.of(Flag.PROTO), RomName.parse("Game (USA) (Proto)").flags());
        assertEquals(Set.of(Flag.DEMO), RomName.parse("Game (Europe) (Demo)").flags());
        assertEquals(Set.of(Flag.DEMO), RomName.parse("Game (Europe) (Sample)").flags());
        assertEquals(
                Set.of(Flag.UNLICENSED, Flag.VERIFIED),
                RomName.parse("Super Donkey Kong 2 (Unl) [!]").flags());
        assertEquals(Set.of(Flag.PIRATE), RomName.parse("Game (Pirate)").flags());
        assertEquals(Set.of(Flag.HACK), RomName.parse("Game (Hack)").flags());
    }

    @Test
    void cleanReleasesShowAndTheRestHide() {
        assertFalse(RomName.parse("Contra (U) [!]").hidden());
        assertFalse(RomName.parse("Contra (U) [a1]").hidden(), "an alternate dump is still a clean release");
        assertFalse(RomName.parse("Super Mario Bros. (Europe) (Rev A)").hidden());
        for (String hidden : List.of(
                "Contra (U) [b1]",
                "Contra (U) [o1]",
                "Contra (U) [h1]",
                "Contra (U) [t1]",
                "Contra (U) [f1]",
                "Mother (J) [T+Eng]",
                "Star Fox 2 (Japan) (Beta)",
                "Game (USA) (Proto)",
                "Game (Demo)",
                "Super Donkey Kong 2 (Unl) [!]",
                "Game (Pirate)")) {
            assertTrue(RomName.parse(hidden).hidden(), hidden);
        }
    }

    @Test
    void anUnknownTagIsKeptAsWritten() {
        RomName rom = RomName.parse("Pokemon - Yellow Version (USA, Europe) (CGB+SGB Enhanced) [c] (PD)");
        assertEquals(List.of("(CGB+SGB Enhanced)", "[c]", "(PD)"), rom.other());
        assertEquals("Pokemon - Yellow Version", rom.title());
    }

    @Test
    void aNameThatIsAllTagsKeepsItAsItsTitle() {
        assertEquals("(Unl)", RomName.parse("(Unl)").title());
        assertEquals("Ms. Pac-Man", RomName.parse("  Ms. Pac-Man  ").title());
    }

    @Test
    void anArticleMovesFrontOnlyWhenItTrails() {
        assertEquals("The Adventures of Lolo", RomName.articleFirst("Adventures of Lolo, The"));
        assertEquals("L'Empereur", RomName.articleFirst("Empereur, L'"));
        assertEquals("Hello, World", RomName.articleFirst("Hello, World"));
        assertEquals("A Boy and His Blob", RomName.articleFirst("Boy and His Blob, A"));
    }
}
