package com.partida.retroarch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finding RetroArch: a native install, the Flatpak, one that never ran, and the user's own choice. */
class RetroArchDetectTest {

    @TempDir
    Path home;

    private static void touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "");
    }

    /** A {@code retroarch} program in {@code bin}, executable. */
    private Path program(Path bin) throws Exception {
        Path exe = bin.resolve("retroarch");
        touch(exe);
        Files.setPosixFilePermissions(exe, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        return exe;
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aNativeInstallWithItsConfigComesFirst() throws Exception {
        Path exe = program(home.resolve("bin"));
        touch(home.resolve(".config/retroarch/retroarch.cfg"));
        touch(home.resolve(".var/app/org.libretro.RetroArch/config/retroarch/retroarch.cfg"));
        AtomicInteger askedFlatpak = new AtomicInteger();

        RetroArchInstall found = RetroArchInstall.detect(
                        home, home.resolve("bin").toString(), null, () -> {
                            askedFlatpak.incrementAndGet();
                            return Optional.empty();
                        })
                .orElseThrow();

        assertEquals(RetroArchInstall.Kind.NATIVE, found.kind());
        assertEquals(home.resolve(".config/retroarch"), found.configDir());
        assertEquals(List.of(exe.toString()), found.command());
        assertEquals(0, askedFlatpak.get(), "the Flatpak is only asked about when it is the one found");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void xdgConfigHomeMovesTheNativeConfig() throws Exception {
        program(home.resolve("bin"));
        touch(home.resolve("xdg/retroarch/retroarch.cfg"));
        RetroArchInstall found = RetroArchInstall.detect(
                        home,
                        home.resolve("bin").toString(),
                        home.resolve("xdg").toString(),
                        Optional::empty)
                .orElseThrow();
        assertEquals(home.resolve("xdg/retroarch"), found.configDir());
    }

    @Test
    void theFlatpakIsFoundByItsConfigFolder() throws Exception {
        touch(home.resolve(".var/app/org.libretro.RetroArch/config/retroarch/retroarch.cfg"));
        Path files = Files.createDirectories(home.resolve("flatpak/files"));

        RetroArchInstall found =
                RetroArchInstall.detect(home, "", "", () -> Optional.of(files)).orElseThrow();

        assertEquals(RetroArchInstall.Kind.FLATPAK, found.kind());
        assertEquals(List.of("flatpak", "run", RetroArchInstall.FLATPAK_ID), found.command());
        assertEquals("flatpak run org.libretro.RetroArch", found.commandLine());
        assertEquals(files.resolve("share/libretro/info"), found.resolve("/app/share/libretro/info"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aNativeInstallThatNeverRanIsStillFound() throws Exception {
        program(home.resolve("bin"));
        RetroArchInstall found = RetroArchInstall.detect(
                        home, home.resolve("bin").toString(), null, Optional::empty)
                .orElseThrow();
        assertEquals(RetroArchInstall.Kind.NATIVE, found.kind());
        assertFalse(Files.exists(found.configFile()));
    }

    @Test
    void nothingInstalledFindsNothing() {
        assertTrue(RetroArchInstall.detect(home, null, null, Optional::empty).isEmpty());
        assertTrue(RetroArchInstall.findOnPath("retroarch", null).isEmpty());
    }

    @Test
    void theUsersChoiceWinsOverDetection() {
        RetroArchInstall custom = RetroArchInstall.locate(home.toString(), List.of("my-retroarch", "--verbose"))
                .orElseThrow();
        assertEquals(RetroArchInstall.Kind.CUSTOM, custom.kind());
        assertEquals(home, custom.configDir());
        assertEquals("my-retroarch --verbose", custom.commandLine());

        RetroArchInstall defaulted =
                RetroArchInstall.locate(" " + home + " ", List.of()).orElseThrow();
        assertEquals(List.of("retroarch"), defaulted.command());
        assertEquals(home, defaulted.configDir());
    }

    @Test
    void aCommandNeedsAProgram() {
        assertThrows(IllegalArgumentException.class, () -> RetroArchInstall.custom(home, List.of(), home));
    }

    @Test
    void theInfoFolderFallsBackToTheFlatpaksOwn() throws Exception {
        Path files = Files.createDirectories(home.resolve("files/share/libretro/info"))
                .getParent()
                .getParent()
                .getParent();
        RetroArchInstall flatpak = new RetroArchInstall(
                RetroArchInstall.Kind.FLATPAK, home.resolve("cfg"), files, List.of("flatpak"), home);
        assertEquals(files.resolve("share/libretro/info"), flatpak.infoDir(CfgFile.empty()));
        assertEquals(home.resolve("cfg/system"), flatpak.systemDir(CfgFile.empty()));
        assertEquals(home.resolve("cfg/thumbnails"), flatpak.thumbnailsDir(CfgFile.empty()));
    }

    @Test
    void theFlatpakLookupAnswersEvenWithoutFlatpak() {
        // On a machine without flatpak (CI), the lookup comes back empty instead of failing.
        Optional<Path> files = RetroArchInstall.findFlatpakFilesDir();
        if (files.isPresent()) assertTrue(Files.isDirectory(files.get()));
        else assertNull(files.orElse(null));
    }
}
