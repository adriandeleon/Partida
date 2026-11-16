package com.partida.retroarch;

import java.util.List;

import com.partida.sync.ShellPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CLI-10: RetroArch and flatpak start with the login shell's PATH, a bare name looked up on it. */
class ProcessesTest {

    @Test
    void aBareNameIsLookedUpAndThePathIsTheLoginShells() {
        assertTrue(Processes.isBareName("retroarch"));
        assertFalse(Processes.isBareName("/usr/bin/retroarch"));
        assertFalse(Processes.isBareName("C:\\RetroArch\\retroarch.exe"));
        assertFalse(Processes.isBareName(""));

        ProcessBuilder full = Processes.builder(List.of("/bin/sh", "-c", "true"));
        assertEquals(List.of("/bin/sh", "-c", "true"), full.command(), "a full path is left as it is");
        assertEquals(ShellPath.path(), full.environment().get("PATH"));
        assertEquals(ShellPath.path(), Processes.path());

        ProcessBuilder bare = Processes.builder(List.of("no-such-tool-partida", "x"));
        assertEquals("no-such-tool-partida", bare.command().getFirst(), "not found: the bare name, for the OS");
        assertEquals("x", bare.command().get(1));
    }
}
