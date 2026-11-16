package com.partida.ui;

import com.partida.AppInfo;
import com.partida.command.Command;
import com.partida.command.CommandRegistry;

/**
 * Every command the starter registers, in one place. Each needs {@code command.<id>} and
 * {@code command.<id>.desc} in every catalog ({@code MessagesTest} checks), and runs in
 * {@code CommandSweepFxTest}. Every setting has one too.
 */
final class Commands {

    private Commands() {}

    static void register(MainWindow window, AppServices services) {
        CommandRegistry r = services.commands();
        r.register("palette.show", window::openPalette);
        r.register("app.settings", () -> window.openSettings(SettingsWindow.Page.GENERAL));
        r.register("app.quit", window::quit);

        r.register("view.messageLog", window::openMessageLog);
        r.register("messageLog.copy", window::copyMessageLog);
        r.register("messageLog.clear", window::clearMessageLog);
        r.register("help.doctor", window::openDoctor); // DIAG-07
        // The window's regions, in turn (ACC-06): the documents and each open tool window.
        r.register("window.focusNextRegion", () -> window.toolWindows().focusRegion(1));
        r.register("window.focusPreviousRegion", () -> window.toolWindows().focusRegion(-1));
        // The documents' text zoom (WIN-09), Ctrl/Cmd+=, - and 0; kept across launches as a setting.
        r.register(
                "view.zoomIn",
                () -> zoomText(services, services.settings().textZoom + TEXT_ZOOM_STEP),
                () -> Chrome.textZoomInUnavailable(services.settings().textZoom));
        r.register(
                "view.zoomOut",
                () -> zoomText(services, services.settings().textZoom - TEXT_ZOOM_STEP),
                () -> Chrome.textZoomOutUnavailable(services.settings().textZoom));
        r.register(
                "view.zoomReset",
                () -> zoomText(services, com.partida.config.Settings.DEFAULT_TEXT_ZOOM),
                () -> Chrome.textZoomResetUnavailable(services.settings().textZoom));
        // The UI zoom (WIN-12): three commands, kept across launches through the setting.
        r.register(
                "view.uiZoomIn",
                () -> zoomUi(services, UiZoom.in(services.settings().uiZoom)),
                () -> Chrome.uiZoomInUnavailable(services.settings().uiZoom));
        r.register(
                "view.uiZoomOut",
                () -> zoomUi(services, UiZoom.out(services.settings().uiZoom)),
                () -> Chrome.uiZoomOutUnavailable(services.settings().uiZoom));
        r.register(
                "view.uiZoomReset",
                () -> zoomUi(services, com.partida.config.Settings.DEFAULT_UI_ZOOM),
                () -> Chrome.uiZoomResetUnavailable(services.settings().uiZoom));
        r.register(
                "view.toggleTitleBarMenus",
                () -> {
                    services.store().update(s -> s.titleBarMenus = !s.titleBarMenus);
                    services.echo("status.titleBarRestart");
                },
                window::titleBarMenusUnavailable);
        r.register("view.toggleToolbar", () -> {
            services.store().update(s -> s.showToolbar = !s.showToolbar);
            services.echo(services.settings().showToolbar ? "status.toolbarOn" : "status.toolbarOff");
        });
        // Simple mode: a setting with its own command, the toolbar's toggle and a View menu row in both menus.
        r.register("view.toggleSimpleMode", () -> {
            services.store().update(s -> s.simpleMode = !s.simpleMode);
            services.echo(services.settings().simpleMode ? "status.simpleModeOn" : "status.simpleModeOff");
        });
        // TBAR-09: the toolbar's layout is the user's, edited in Settings → General and restored in one step.
        r.register("view.customizeToolbar", window::customizeToolbar);
        r.register(
                "view.restoreToolbar",
                () -> {
                    services.store().update(s -> s.toolbarLayout = new java.util.ArrayList<>());
                    services.echo("status.toolbarRestored");
                },
                () -> services.settings().toolbarLayout.isEmpty()
                        ? com.partida.i18n.Messages.tr("palette.disabled.toolbarDefault")
                        : null);
        // WIN-10: Zen shows no chrome, Expert only the status bar; each its own command, for this session.
        r.register("view.toggleZenMode", () -> {
            window.toggleFocus(Chrome.Focus.ZEN);
            services.echo(window.focus() == Chrome.Focus.ZEN ? "status.zenModeOn" : "status.zenModeOff");
        });
        r.register("view.toggleExpertMode", () -> {
            window.toggleFocus(Chrome.Focus.EXPERT);
            services.echo(window.focus() == Chrome.Focus.EXPERT ? "status.expertModeOn" : "status.expertModeOff");
        });
        r.register("view.toggleStatusBar", () -> {
            services.store().update(s -> s.showStatusBar = !s.showStatusBar);
            services.echo(services.settings().showStatusBar ? "status.statusBarOn" : "status.statusBarOff");
        });

        r.register("view.toggleTabBar", () -> {
            services.store().update(s -> s.showTabBar = !s.showTabBar);
            services.echo(services.settings().showTabBar ? "status.tabBarOn" : "status.tabBarOff");
        });

        registerTabs(window, r);
        registerToolWindows(window, r);
        registerLibrary(window, services);

        r.register("settings.theme", window::chooseTheme);
        r.register("settings.keymap", window::chooseKeymap);
        r.register("settings.language", window::chooseLanguage);
        r.register("settings.bindKey", window::chooseBinding);
        // The actions of a key-binding row in Settings, on its right-click menu (MENU-05).
        r.register(
                "keymap.changeBinding",
                () -> {
                    Command selected = window.selectedBinding();
                    if (selected != null) window.promptBinding(selected);
                },
                () -> Chrome.bindingRowUnavailable(window.selectedBinding() != null));
        r.register(
                "keymap.resetBinding",
                window::resetSelectedBinding,
                () -> Chrome.bindingRowUnavailable(window.selectedBinding() != null));
        r.register("settings.toggleUpdateCheck", () -> {
            services.store().update(s -> s.checkForUpdates = !s.checkForUpdates);
            services.echo(services.settings().checkForUpdates ? "status.updateCheckOn" : "status.updateCheckOff");
        });

        r.register("config.export", window::exportConfig);
        r.register("config.import", window::importConfig);
        r.register(
                "config.openFolder",
                () -> services.host().openUrl(services.store().dir().toUri().toString()));

        r.register(
                "sync.openFolder",
                () -> services.host()
                        .openUrl(services.store().dir().resolve("sync").toUri().toString()));
        r.register("sync.setup", () -> window.openSettings(SettingsWindow.Page.SYNC));
        // The keyboard reference (DOCS-08): the key binding editor, searchable, from the Help menu.
        r.register("help.keyboardShortcuts", () -> window.openSettings(SettingsWindow.Page.KEYMAP));
        r.register("sync.createGitHubRepo", window::createSyncRepo);
        r.register("sync.setRepository", window::promptSyncRepository);
        r.register("sync.setBranch", window::promptSyncBranch);
        r.register("sync.setInterval", window::promptSyncInterval);
        r.register(
                "sync.now",
                () -> services.sync().syncNow(true),
                () -> Chrome.syncNowUnavailable(services.sync().isConfigured()));
        r.register("sync.toggleAuto", () -> {
            services.store().update(s -> s.sync.auto = !s.sync.auto);
            services.echo(services.settings().sync.auto ? "status.syncAutoOn" : "status.syncAutoOff");
        });

        r.register("help.documentation", () -> services.host().openUrl(AppInfo.docsUrl()));
        r.register("help.whatsNew", () -> services.host().openUrl(AppInfo.releaseNotesUrl()));
        r.register("help.about", window::openAbout);
        r.register(
                "help.openNewRelease",
                () -> {
                    com.partida.update.ReleaseInfo latest = services.updates().latest();
                    if (latest != null) services.host().openUrl(latest.url());
                },
                () -> Chrome.newReleaseUnavailable(services.updates().latest() != null));
        r.register("help.homepage", () -> services.host().openUrl(AppInfo.HOMEPAGE));
        r.register("help.releases", () -> services.host().openUrl(AppInfo.RELEASES_PAGE));
        r.register("help.license", () -> services.host().openUrl(AppInfo.LICENSE_URL));
        // The Pauta window (PWIN-01) and its links; the edition's own page is About's first Pauta link.
        r.register("help.pauta", window::openPauta);
        r.register("help.pautaRules", () -> services.host().openUrl(AppInfo.pautaUrl()));
        r.register("help.pautaDocs", () -> services.host().openUrl(AppInfo.pautaDocsUrl()));
        r.register("help.copyPauta", window::copyPauta);
        r.register(
                "config.openSettingsFile",
                () -> services.host().openUrl(services.store().file().toUri().toString()));
        r.register("help.copyDetails", window::copyDetails);
        r.register(
                "help.checkForUpdates",
                () -> services.updates().checkNow(true),
                () -> Chrome.checkForUpdatesUnavailable(
                        services.updates().state() == UpdateCoordinator.State.CHECKING));
        r.register("help.debugLog", window::openDebugLog);
        r.register(
                "debugLog.refresh",
                window::refreshDebugLog,
                () -> Chrome.debugLogRefreshUnavailable(window.isDebugLogOpen()));
        r.register("debugLog.find", window::findInDebugLog);
        r.register(
                "debugLog.findNext",
                () -> window.stepDebugLogFind(true),
                () -> Chrome.findBarUnavailable(window.isDebugLogFindOpen()));
        r.register(
                "debugLog.findPrevious",
                () -> window.stepDebugLogFind(false),
                () -> Chrome.findBarUnavailable(window.isDebugLogFindOpen()));
        r.register("debugLog.copy", () -> Clipboards.copy(DebugLog.text()));
        r.register("debugLog.clear", window::clearDebugLog);
        r.register("debugLog.export", window::exportDebugLog);
        r.register("help.reportProblem", window::openReportProblem);
    }

    /** One step of the text zoom, in percent. */
    static final int TEXT_ZOOM_STEP = 10;

    private static void zoomText(AppServices services, int percent) {
        int clamped = com.partida.config.Settings.clampTextZoom(percent);
        services.store().update(s -> s.textZoom = clamped);
        services.echo("status.textZoom", clamped);
    }

    private static void zoomUi(AppServices services, int percent) {
        services.store().update(s -> s.uiZoom = percent);
        services.echo("status.uiZoom", percent);
    }

    /**
     * Every tab operation is a command (TABS-01): bound to a chord, in the palette, a row in the Window menu,
     * and the per-tab ones on the tab's right-click menu ({@link TabContextMenu}). Each acts on the selected
     * tab. The ids are Editora's.
     */
    private static void registerTabs(MainWindow window, CommandRegistry r) {
        r.register(
                "buffer.close",
                () -> window.onSelectedTab(t -> window.tabs().closeTab(t)),
                () -> window.tabs().closeUnavailable());
        for (String id : java.util.List.of(
                "buffer.closeOthers",
                "buffer.closeAll",
                "buffer.closeUnmodified",
                "buffer.closeLeft",
                "buffer.closeRight")) {
            r.register(
                    id,
                    () -> window.onSelectedTab(t -> {
                        switch (id) {
                            case "buffer.closeOthers" -> window.tabs().closeOtherTabs(t);
                            case "buffer.closeAll" -> window.tabs().closeAllTabs();
                            case "buffer.closeUnmodified" -> window.tabs().closeUnmodifiedTabs();
                            case "buffer.closeLeft" -> window.tabs().closeTabsToLeft(t);
                            default -> window.tabs().closeTabsToRight(t);
                        }
                    }),
                    () -> window.tabs().nothingToClose(id));
        }
        r.register(
                "buffer.togglePin",
                () -> window.onSelectedTab(t -> window.tabs().togglePin(t)),
                () -> window.tabs().closeUnavailable());
        r.register(
                "buffer.reopenClosed",
                () -> window.tabs().reopenClosed(),
                () -> window.tabs().reopenUnavailable());
        r.register(
                "buffer.next",
                () -> window.tabs().selectNext(1),
                () -> window.tabs().cycleUnavailable());
        r.register(
                "buffer.previous",
                () -> window.tabs().selectNext(-1),
                () -> window.tabs().cycleUnavailable());
        r.register("switcher.show", window::showSwitcher, () -> window.tabs().closeUnavailable());
        r.register("view.welcome", () -> window.tabs().showWelcome());
    }

    /**
     * Each tool window's {@code tool.<id>} command (TABS-13), which its stripe button runs, and the commands
     * that float, maximize, resize, close and re-show them from the keyboard (TABS-18). Those act on the
     * window whose stripe menu or header was used, else the one holding focus, else the last one opened.
     */
    private static void registerToolWindows(MainWindow window, CommandRegistry r) {
        for (String id : java.util.List.of("systems", "game", "retroarch")) {
            r.register(
                    "tool." + id,
                    () -> window.toolWindows().toggle(toolWindow(window, id)),
                    () -> window.toolWindows().unavailableReason(toolWindow(window, id)));
        }
        r.register(
                "toolWindow.openInSplit",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().openInSplit(tw)),
                () -> Chrome.splitUnavailable(splitTarget(window)));
        r.register(
                "toolWindow.hide",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().setVisible(tw, false)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.restoreStripeButtons",
                () -> window.toolWindows()
                        .all()
                        .forEach(tw -> window.toolWindows().setVisible(tw, true)),
                () -> Chrome.restoreStripeButtonsUnavailable(window.toolWindows().all().stream()
                        .anyMatch(tw -> !window.toolWindows().isVisible(tw))));
        r.register(
                "view.floatToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().toggleFloating(tw)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.maximizeToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().toggleMaximized(tw)),
                () -> Chrome.toolWindowUnavailable(
                        docked(window, window.toolWindows().target())));
        r.register(
                "view.closeFocusedToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().close(tw)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.resizeHorizontalToolWindowGreater",
                () -> window.onToolWindow(
                        window.toolWindows().horizontalTarget(),
                        tw -> window.toolWindows().resize(tw, ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().horizontalTarget() != null));
        r.register(
                "view.resizeHorizontalToolWindowLess",
                () -> window.onToolWindow(
                        window.toolWindows().horizontalTarget(),
                        tw -> window.toolWindows().resize(tw, -ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().horizontalTarget() != null));
        r.register(
                "view.resizeBottomToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().bottomTarget(),
                        tw -> window.toolWindows().resize(tw, ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().bottomTarget() != null));
        r.register(
                "view.resizeBottomToolWindowLess",
                () -> window.onToolWindow(
                        window.toolWindows().bottomTarget(),
                        tw -> window.toolWindows().resize(tw, -ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().bottomTarget() != null));
        // Find (FIND-03, FIND-04): find.show focuses the filter box over the active system's games, and while it
        // narrows the list find.next and find.previous step through the games it keeps; retroarch.find is the
        // console's find bar, whose Enter, Shift+Enter and buttons run its own two.
        r.register(
                "find.show",
                () -> {
                    SystemView view = window.activeSystem();
                    if (view != null) view.focusFilter();
                },
                () -> Chrome.findUnavailable(window.activeSystem() != null));
        // FIND-13: the filter box's own command, on Ctrl+K (Cmd+K on macOS), drawn as the box's key badge.
        r.register(
                "library.filter",
                () -> {
                    SystemView view = window.activeSystem();
                    if (view != null) view.focusFilter();
                },
                () -> Chrome.findUnavailable(window.activeSystem() != null));
        r.register(
                "find.next",
                () -> stepFilter(window.activeSystem(), true),
                () -> Chrome.filterStepUnavailable(filtering(window.activeSystem())));
        r.register(
                "find.previous",
                () -> stepFilter(window.activeSystem(), false),
                () -> Chrome.filterStepUnavailable(filtering(window.activeSystem())));
        r.register("retroarch.find", window::findInConsole);
        r.register(
                "retroarch.findNext",
                () -> stepFind(window.consoleFindBar(), true),
                () -> Chrome.findStepUnavailable(isOpen(window.consoleFindBar())));
        r.register(
                "retroarch.findPrevious",
                () -> stepFind(window.consoleFindBar(), false),
                () -> Chrome.findStepUnavailable(isOpen(window.consoleFindBar())));
        // Cancel (PAL-07, QOPEN-07, MLOG-03): the keymap's chord closes the palette, a picker or the Message
        // Log, as Esc does; C-g in the Emacs keymap.
        r.register(
                "edit.cancel",
                () -> window.overlays().hide(),
                () -> Chrome.cancelUnavailable(window.overlays().isShowing()));
        // The text commands (MENU-05): on the text control that has the keyboard, as the stock menu did.
        r.register(
                "edit.cut",
                () -> onText(window, javafx.scene.control.TextInputControl::cut),
                () -> editUnavailable(window, true));
        r.register(
                "edit.copy",
                () -> onText(window, javafx.scene.control.TextInputControl::copy),
                () -> editUnavailable(window, false));
        r.register(
                "edit.paste",
                () -> onText(window, javafx.scene.control.TextInputControl::paste),
                () -> editUnavailable(window, true));
        r.register(
                "edit.selectAll",
                () -> onText(window, javafx.scene.control.TextInputControl::selectAll),
                () -> editUnavailable(window, false));
        r.register(
                "systems.open",
                () -> {
                    LibraryPanels.Systems list = window.systems();
                    if (list != null) list.openSelected();
                },
                () -> Chrome.systemsOpenUnavailable(
                        window.systems() != null && window.systems().hasSelection()));
        r.register(
                "retroarch.clear",
                () -> {
                    LibraryPanels.Console console = window.console();
                    if (console != null) console.clear();
                },
                () -> Chrome.consoleClearUnavailable(window.console() != null
                        && !window.console().console().getText().isEmpty()));
    }

    /**
     * The library's commands: play and stop a game, and a palette command for each of Settings → Library's
     * settings (SET-06).
     */
    private static void registerLibrary(MainWindow window, AppServices services) {
        CommandRegistry r = services.commands();
        r.register("game.play", window::playSelected, window::playUnavailable);
        r.register(
                "game.stop",
                () -> services.library().stop(),
                () -> services.library().stopUnavailable());
        r.register("library.refresh", () -> {
            services.library().reload();
            services.echo("status.libraryRefreshing");
        });
        r.register("library.settings", () -> window.openSettings(SettingsWindow.Page.LIBRARY));
        r.register("library.addFolder", window::addLibraryFolder);
        r.register(
                "library.removeFolder",
                window::removeLibraryFolder,
                () -> Chrome.noFoldersUnavailable(
                        !services.settings().library.folders.isEmpty()));
        r.register(
                "library.setFolderCore",
                window::chooseFolderCore,
                () -> Chrome.noFoldersUnavailable(
                        !services.settings().library.folders.isEmpty()));
        r.register("library.setRetroArchFolder", window::promptRetroArchFolder);
        r.register("library.setRetroArchCommand", window::promptRetroArchCommand);
        r.register("library.toggleRecentlyPlayed", () -> {
            services.store().update(s -> s.library.showRecentlyPlayed = !s.library.showRecentlyPlayed);
            services.echo(
                    services.settings().library.showRecentlyPlayed
                            ? "status.recentlyPlayedOn"
                            : "status.recentlyPlayedOff");
        });
    }

    private static void onText(
            MainWindow window, java.util.function.Consumer<javafx.scene.control.TextInputControl> action) {
        javafx.scene.control.TextInputControl text = window.focusedText();
        if (text != null) action.accept(text);
    }

    /** The facts {@link Chrome#editUnavailable} decides on: the text that has the keyboard, if any. */
    private static @org.jspecify.annotations.Nullable String editUnavailable(MainWindow window, boolean edits) {
        javafx.scene.control.TextInputControl text = window.focusedText();
        return Chrome.editUnavailable(text != null, text != null && text.isEditable(), edits);
    }

    private static boolean filtering(@org.jspecify.annotations.Nullable SystemView view) {
        return view != null && view.filtering();
    }

    private static void stepFilter(@org.jspecify.annotations.Nullable SystemView view, boolean forward) {
        if (view != null && view.filtering()) view.step(forward);
    }

    private static boolean isOpen(@org.jspecify.annotations.Nullable FindBar bar) {
        return bar != null && bar.isOpen();
    }

    private static void stepFind(@org.jspecify.annotations.Nullable FindBar bar, boolean forward) {
        if (bar != null && bar.isOpen()) bar.step(forward);
    }

    private static ToolWindow toolWindow(MainWindow window, String id) {
        return java.util.Objects.requireNonNull(window.toolWindows().get(id), id);
    }

    /** The window Open in Split would open: the stripe button's whose menu is open, if it can. */
    private static boolean splitTarget(MainWindow window) {
        ToolWindow tw = window.toolWindows().target();
        return tw != null && window.toolWindows().canSplitWith(tw);
    }

    private static boolean docked(MainWindow window, @org.jspecify.annotations.Nullable ToolWindow tw) {
        return tw != null
                && window.toolWindows().isOpen(tw)
                && !window.toolWindows().isFloating(tw);
    }
}
