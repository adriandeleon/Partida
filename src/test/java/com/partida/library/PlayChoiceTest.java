package com.partida.library;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which of a game's ROMs Play starts: the one played last, else the best for the region ranking. */
class PlayChoiceTest {

    private static final String NES = "Nintendo - Nintendo Entertainment System";

    private static final GameTitle MARIO = GameTitle.group(List.of(
                    rom("Super Mario Bros. (E) (REVA) [!].nes"),
                    rom("Super Mario Bros. (U) [!].nes"),
                    rom("Super Mario Bros. (J) [!].nes"),
                    rom("Super Mario Bros. (U) [h1].nes")))
            .getFirst();

    private static Game rom(String file) {
        return new Game("", Path.of("/roms", file), NES, null);
    }

    private static String chosen(List<Path> history, String remembered, List<String> regions) {
        return PlayChoice.choose(MARIO, history, remembered, regions).game().contentName();
    }

    @Test
    void theRomPlayedLastWinsWhereverItIsInTheHistory() {
        List<Path> history = List.of(
                Path.of("/roms/Contra (U) [!].nes"),
                Path.of("/roms/../roms/Super Mario Bros. (J) [!].nes"),
                Path.of("/roms/Super Mario Bros. (U) [!].nes"));
        assertEquals(
                "Super Mario Bros. (J) [!].nes", chosen(history, "/roms/Super Mario Bros. (U) [!].nes", List.of()));
    }

    @Test
    void partidasOwnRecordWhenTheHistoryHasNone() {
        assertEquals(
                "Super Mario Bros. (U) [h1].nes",
                chosen(List.of(), "/roms/Super Mario Bros. (U) [h1].nes", List.of("Europe")),
                "a hack played on purpose is played again");
        assertEquals(
                "Super Mario Bros. (E) (REVA) [!].nes",
                chosen(List.of(), "/roms/gone.nes", List.of("Europe")),
                "a remembered ROM that is gone counts for nothing");
    }

    @Test
    void neverPlayedTakesTheBestForTheRanking() {
        assertEquals("Super Mario Bros. (U) [!].nes", chosen(List.of(), null, List.of("USA", "Europe")));
        assertEquals("Super Mario Bros. (J) [!].nes", chosen(List.of(), null, List.of("Japan")));
        assertEquals(
                "Super Mario Bros. (E) (REVA) [!].nes",
                chosen(List.of(), null, List.of("Brazil")),
                "no ranked region: the latest revision");
    }
}
