package com.partida.ui;

import com.partida.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window's visibility and enablement rules, without a toolkit (QUAL-05). */
class ChromeTest {

    @BeforeEach
    void english() {
        Messages.init("en");
    }

    @Test
    void theHeaderBarIsUsedOnlyWithMenusInTheWindowTheSettingOnAndPlatformSupport() {
        assertTrue(Chrome.usesHeaderBar(false, true, true));
        assertFalse(Chrome.usesHeaderBar(true, true, true), "macOS screen menu bar");
        assertFalse(Chrome.usesHeaderBar(false, false, true), "setting off");
        assertFalse(Chrome.usesHeaderBar(false, true, false), "EXTENDED unsupported");
    }

    /** OS-01: the title-bar setting and command exist everywhere; where they can't work they say why. */
    @Test
    void theTitleBarMenusSayWhyTheyAreUnavailable() {
        assertNull(Chrome.titleBarMenusUnavailable(false, true));
        assertEquals(Messages.tr("palette.disabled.titleBarScreenMenu"), Chrome.titleBarMenusUnavailable(true, true));
        assertEquals(
                Messages.tr("palette.disabled.titleBarUnsupported"), Chrome.titleBarMenusUnavailable(false, false));
    }

    @Test
    void eachGateSaysWhyACommandCannotRun() {
        assertNull(Chrome.syncNowUnavailable(true));
        assertTrue(Chrome.syncNowUnavailable(false).contains("not set up"));
        assertNull(Chrome.checkForUpdatesUnavailable(false));
        assertTrue(Chrome.checkForUpdatesUnavailable(true).contains("already running"));
        assertNull(Chrome.debugLogRefreshUnavailable(true));
        assertEquals(Messages.tr("palette.disabled.debugLogClosed"), Chrome.debugLogRefreshUnavailable(false));
    }

    /** QUAL-05: the text commands' gates, decided from plain facts. */
    @Test
    void theTextGatesAreDecidedFromFacts() {
        assertEquals(Messages.tr("palette.disabled.noText"), Chrome.editUnavailable(false, false, false));
        assertNull(Chrome.editUnavailable(true, false, false), "Copy reads a read-only text");
        assertEquals(Messages.tr("palette.disabled.readOnlyText"), Chrome.editUnavailable(true, false, true));
        assertNull(Chrome.editUnavailable(true, true, true));
    }

    /** QUAL-05: the text zoom's three commands each say why they can't run, at an end or at the default. */
    @Test
    void theTextZoomGatesStopAtTheEnds() {
        var settings = com.partida.config.Settings.class;
        assertEquals(
                Messages.tr("palette.disabled.textZoomLargest"),
                Chrome.textZoomInUnavailable(com.partida.config.Settings.MAX_TEXT_ZOOM));
        assertNull(Chrome.textZoomInUnavailable(com.partida.config.Settings.DEFAULT_TEXT_ZOOM));
        assertEquals(
                Messages.tr("palette.disabled.textZoomSmallest"),
                Chrome.textZoomOutUnavailable(com.partida.config.Settings.MIN_TEXT_ZOOM));
        assertNull(Chrome.textZoomOutUnavailable(com.partida.config.Settings.DEFAULT_TEXT_ZOOM));
        assertEquals(
                Messages.tr("palette.disabled.textZoomDefault"),
                Chrome.textZoomResetUnavailable(com.partida.config.Settings.DEFAULT_TEXT_ZOOM));
        assertNull(Chrome.textZoomResetUnavailable(com.partida.config.Settings.MAX_TEXT_ZOOM));
        assertNotNull(settings);
    }

    /** WIN-10: one truth table decides which chrome each mode shows, over the user's own choices. */
    @Test
    void eachFocusModeShowsItsChrome() {
        assertEquals(new Chrome.Shown(true, false, true, true), Chrome.shown(Chrome.Focus.NONE, false, false, true));
        assertEquals(new Chrome.Shown(false, false, false, true), Chrome.shown(Chrome.Focus.EXPERT, false, true, true));
        assertEquals(
                new Chrome.Shown(false, false, false, false),
                Chrome.shown(Chrome.Focus.EXPERT, false, true, false),
                "Expert keeps the status bar only where the user has it");
        assertEquals(new Chrome.Shown(false, false, false, false), Chrome.shown(Chrome.Focus.ZEN, false, true, true));
        assertTrue(Chrome.shown(Chrome.Focus.ZEN, true, true, true).menus(), "the screen menu bar stays");
    }

    @Test
    void aModesCommandTurnsItOnOrOff() {
        assertEquals(Chrome.Focus.ZEN, Chrome.toggled(Chrome.Focus.NONE, Chrome.Focus.ZEN));
        assertEquals(Chrome.Focus.NONE, Chrome.toggled(Chrome.Focus.ZEN, Chrome.Focus.ZEN));
        assertEquals(Chrome.Focus.EXPERT, Chrome.toggled(Chrome.Focus.ZEN, Chrome.Focus.EXPERT));
    }
}
