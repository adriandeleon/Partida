package com.partida.ui;

import com.partida.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The window's visibility and enablement rules, as pure decisions (QUAL-05): no JavaFX here, so each
 * is unit-tested without a toolkit, and the windows only ask.
 */
final class Chrome {

    private Chrome() {}

    /**
     * Whether a window draws its own title bar with the menus in it: JavaFX 27's extended stage style.
     * Never under a screen menu bar (macOS: there are no menus in the window to put there), and only
     * where the platform supports the style: JavaFX quietly downgrades an unsupported {@code EXTENDED}
     * to {@code DECORATED}, which would draw the header under a native title bar as a second row.
     */
    static boolean usesHeaderBar(boolean systemMenu, boolean setting, boolean extendedSupported) {
        return !systemMenu && setting && extendedSupported;
    }

    /**
     * Why the menus can't go in the title bar here, or null when they can (OS-01): the setting and its command
     * exist on every OS and stay in place, disabled with this reason where the system can't do it.
     */
    static @Nullable String titleBarMenusUnavailable(boolean systemMenu, boolean extendedSupported) {
        if (systemMenu) return Messages.tr("palette.disabled.titleBarScreenMenu");
        if (!extendedSupported) return Messages.tr("palette.disabled.titleBarUnsupported");
        return null;
    }

    /** Why Zoom Text In cannot run: the text is at its largest. */
    static @Nullable String textZoomInUnavailable(int percent) {
        return percent >= com.partida.config.Settings.MAX_TEXT_ZOOM
                ? Messages.tr("palette.disabled.textZoomLargest")
                : null;
    }

    /** Why Zoom Text Out cannot run: the text is at its smallest. */
    static @Nullable String textZoomOutUnavailable(int percent) {
        return percent <= com.partida.config.Settings.MIN_TEXT_ZOOM
                ? Messages.tr("palette.disabled.textZoomSmallest")
                : null;
    }

    /** Why Actual Text Size cannot run: the text is at its default size already. */
    static @Nullable String textZoomResetUnavailable(int percent) {
        return percent == com.partida.config.Settings.DEFAULT_TEXT_ZOOM
                ? Messages.tr("palette.disabled.textZoomDefault")
                : null;
    }

    /** Why Zoom In cannot run: the UI is at its largest. */
    static @Nullable String uiZoomInUnavailable(int percent) {
        return percent >= com.partida.config.Settings.MAX_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomLargest", com.partida.config.Settings.MAX_UI_ZOOM)
                : null;
    }

    /** Why Zoom Out cannot run: the UI is at its smallest. */
    static @Nullable String uiZoomOutUnavailable(int percent) {
        return percent <= com.partida.config.Settings.MIN_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomSmallest", com.partida.config.Settings.MIN_UI_ZOOM)
                : null;
    }

    /** Why Reset Zoom cannot run: the UI is at its default size already. */
    static @Nullable String uiZoomResetUnavailable(int percent) {
        return percent == com.partida.config.Settings.DEFAULT_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomDefault")
                : null;
    }

    /**
     * Why a text command cannot run: no text has the keyboard, or (for Cut and Paste, which {@code edits})
     * the text is read-only.
     */
    static @Nullable String editUnavailable(boolean focusedText, boolean editable, boolean edits) {
        if (!focusedText) return Messages.tr("palette.disabled.noText");
        if (edits && !editable) return Messages.tr("palette.disabled.readOnlyText");
        return null;
    }

    /** Why Open cannot run in the Systems window: no system is selected. */
    static @Nullable String systemsOpenUnavailable(boolean selected) {
        return selected ? null : Messages.tr("palette.disabled.noSystemSelected");
    }

    /** Why Clear cannot run in the RetroArch console: there is nothing in it. */
    static @Nullable String consoleClearUnavailable(boolean any) {
        return any ? null : Messages.tr("palette.disabled.consoleEmpty");
    }

    /** Why Cancel cannot run: no palette, picker or Message Log is open. */
    static @Nullable String cancelUnavailable(boolean overlay) {
        return overlay ? null : Messages.tr("palette.disabled.nothingToCancel");
    }

    /**
     * Why Play can't start a game, or null when it can (QUAL-05): RetroArch found, a game selected, and none
     * running already, in that order. Pure: the toolbar, the menus and the palette all read it.
     */
    static @Nullable String playUnavailable(boolean retroArchFound, boolean gameSelected, boolean playing) {
        if (!retroArchFound) return Messages.tr("palette.disabled.noRetroArch");
        if (!gameSelected) return Messages.tr("palette.disabled.noGame");
        if (playing) return Messages.tr("palette.disabled.gameRunning");
        return null;
    }

    /** Why Stop can't act, or null: only a game Partida started can be stopped (QUAL-05). */
    static @Nullable String stopUnavailable(boolean running) {
        return running ? null : Messages.tr("palette.disabled.noGameRunning");
    }

    /**
     * A system as the user reads it: its name, or for the play history, which the library names by a fixed
     * id, that name in the user's language (INTL-07).
     */
    static String systemName(String name) {
        return name.equals(com.partida.library.LibraryLoader.RECENTLY_PLAYED)
                ? Messages.tr("systems.recentlyPlayed")
                : name;
    }

    /** Why Find Next or Find Previous cannot run: their find bar is not open. */
    static @Nullable String findStepUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.findBarClosed");
    }

    /** Why Find Next or Find Previous can't step the game list: its filter box is empty. */
    static @Nullable String filterStepUnavailable(boolean filtering) {
        return filtering ? null : Messages.tr("palette.disabled.noFilter");
    }

    /** Why Find cannot run: the active tab is the Welcome page, which has no games to filter. */
    static @Nullable String findUnavailable(boolean document) {
        return document ? null : Messages.tr("palette.disabled.noDocument");
    }

    /** Why Sync Now cannot run, or null when it can. */
    static @Nullable String syncNowUnavailable(boolean configured) {
        return configured ? null : Messages.tr("palette.disabled.syncNotSetUp");
    }

    /** Why Check for Updates cannot run, or null when it can. */
    static @Nullable String checkForUpdatesUnavailable(boolean checking) {
        return checking ? Messages.tr("palette.disabled.checking") : null;
    }

    /** Why a key-binding row's command cannot run: no row is selected in Settings → Keymap. */
    static @Nullable String bindingRowUnavailable(boolean selected) {
        return selected ? null : Messages.tr("palette.disabled.noBindingSelected");
    }

    /** Why Open the New Release cannot run: no newer release has been found. */
    static @Nullable String newReleaseUnavailable(boolean found) {
        return found ? null : Messages.tr("palette.disabled.noNewRelease");
    }

    /** Why Find Next or Previous cannot run: the Debug Log's find bar is not open. */
    static @Nullable String findBarUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.findClosed");
    }

    /** Why a command on a tool window cannot run: none is open to act on. */
    static @Nullable String toolWindowUnavailable(boolean target) {
        return target ? null : Messages.tr("palette.disabled.noToolWindow");
    }

    /** Why Open in Split cannot run: no closed window's side has room beside an open one. */
    static @Nullable String splitUnavailable(boolean possible) {
        return possible ? null : Messages.tr("palette.disabled.noSplit");
    }

    /** Why Show Hidden Stripe Buttons cannot run: none is hidden. */
    static @Nullable String restoreStripeButtonsUnavailable(boolean anyHidden) {
        return anyHidden ? null : Messages.tr("palette.disabled.noHiddenButtons");
    }

    /** Why a library command that acts on a games folder cannot run: there is none. */
    static @Nullable String noFoldersUnavailable(boolean any) {
        return any ? null : Messages.tr("palette.disabled.noFolders");
    }

    /** Why Debug Log: Refresh cannot run, or null when it can. */
    static @Nullable String debugLogRefreshUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.debugLogClosed");
    }

    /**
     * Which separators stay, given the bar's items in order: {@code null} for a separator, else whether
     * that item is visible. A separator stays only with a visible item somewhere before it, back to the
     * last separator kept, and one somewhere after it. Entries for items echo their own visibility.
     */
    static boolean[] separatorsShown(java.util.List<@Nullable Boolean> items) {
        boolean[] shown = new boolean[items.size()];
        boolean itemSinceKept = false;
        for (int i = 0; i < items.size(); i++) {
            Boolean item = items.get(i);
            if (item != null) {
                shown[i] = item;
                itemSinceKept |= item;
                continue;
            }
            boolean itemAfter = false;
            for (int j = i + 1; j < items.size() && !itemAfter; j++) {
                itemAfter = Boolean.TRUE.equals(items.get(j));
            }
            shown[i] = itemSinceKept && itemAfter;
            if (shown[i]) itemSinceKept = false;
        }
        return shown;
    }

    /**
     * The window's focus modes (WIN-10): {@code ZEN} shows no chrome, {@code EXPERT} keeps only the status
     * bar. Simple mode, the third, is a setting of its own ({@code simpleMode}) and shortens the menus.
     */
    enum Focus {
        NONE,
        ZEN,
        EXPERT
    }

    /** What chrome the window shows: one answer of {@link #shown}'s truth table. */
    record Shown(boolean menus, boolean toolbar, boolean stripes, boolean statusBar) {}

    /**
     * The one truth table of the window's chrome per mode (WIN-10), over the user's own show and hide
     * choices, which no mode changes:
     *
     * <pre>
     *            menus   toolbar  stripes  status bar
     *   none     yes     setting  yes      setting
     *   expert   no      no       no       setting
     *   zen      no      no       no       no
     * </pre>
     *
     * A screen menu bar (macOS) is the system's, not the window's: it stays in every mode. In a window that
     * draws its own title bar the menus go and the title and the window buttons stay.
     */
    static Shown shown(Focus focus, boolean systemMenu, boolean toolbar, boolean statusBar) {
        return switch (focus) {
            case NONE -> new Shown(true, toolbar, true, statusBar);
            case EXPERT -> new Shown(systemMenu, false, false, statusBar);
            case ZEN -> new Shown(systemMenu, false, false, false);
        };
    }

    /** The mode after its command runs: into {@code pressed}, or out of it when it is the one on. */
    static Focus toggled(Focus current, Focus pressed) {
        return current == pressed ? Focus.NONE : pressed;
    }
}
