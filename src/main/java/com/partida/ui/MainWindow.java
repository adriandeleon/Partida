package com.partida.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.partida.AppInfo;
import com.partida.Os;
import com.partida.PautaConformance;
import com.partida.command.Command;
import com.partida.command.KeyStroke;
import com.partida.command.Keymap;
import com.partida.config.ConfigExporter;
import com.partida.config.ConfigImport;
import com.partida.config.ConfigStore;
import com.partida.config.Settings;
import com.partida.config.ThemeChoice;
import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.sync.SyncEngine;
import org.jspecify.annotations.Nullable;

/**
 * The main window: menus, the toolbar, a placeholder for the app's own content, the status bar, and the root
 * {@code StackPane} that carries the in-scene overlays. Every menu item, button and key binding runs a
 * command by id ({@link Commands}).
 */
public final class MainWindow {

    private final Stage stage;
    private final AppServices services;
    private final OverlayHost overlays;
    private final BorderPane layout = new BorderPane();
    private final AppMenus menus;
    private final OverlayToggles toggles;
    private final AppToolbar toolbar;
    /**
     * The top of the window: the title bar (or the menu row) over the toolbar, one band on one ground
     * with no rule between them, and one rule under the whole band (TBAR-06).
     */
    private final VBox band = new VBox();

    private final Label welcomeHint = new Label();
    /** The system tabs: the welcome page and a tab per open system (Pauta's Tabs and tool panels). */
    private final DocumentTabs tabs;
    /** The tool windows round the tabs, each built on its first open (TABS-14). */
    private final ToolWindowManager toolWindows;

    private final ToolWindow systemsWindow;
    private final ToolWindow gameWindow;
    private final ToolWindow retroarchWindow;
    private LibraryPanels.@Nullable Systems systems;
    private LibraryPanels.@Nullable GamePanel game;
    private LibraryPanels.@Nullable Console console;
    /** RetroArch's lines that arrived before its window was first opened, the newest kept (PERF-11). */
    private final java.util.ArrayDeque<String> consoleBacklog = new java.util.ArrayDeque<>();

    private @Nullable Switcher switcher;
    private final @Nullable HeaderBar headerBar;
    private final boolean systemMenu;
    private final boolean extendedSupported;
    private final StatusBar statusBar;
    /** The latest Import Configuration; a scan or result for an older one is dropped (PERF-03). */
    private final java.util.concurrent.atomic.AtomicLong imports = new java.util.concurrent.atomic.AtomicLong();

    private final java.util.concurrent.atomic.AtomicInteger importsDropped =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Built on first open, not before the first frame (PERF-14). */
    private @Nullable MessageLogView messageLog;

    private @Nullable SettingsWindow settings;
    private @Nullable DebugLogWindow debugLog;

    public MainWindow(Stage stage, AppServices services, boolean restoreSession) {
        this(stage, services, restoreSession, Os.isMac(), Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW));
    }

    /**
     * With the platform given (a screen menu bar, support for extended windows), so every combination
     * is testable on any OS.
     */
    MainWindow(
            Stage stage, AppServices services, boolean restoreSession, boolean systemMenu, boolean extendedSupported) {
        this.stage = stage;
        this.services = services;
        this.overlays = new OverlayHost(layout);
        overlays.cancelOn(e -> services.keybindings().isKeyOf("edit.cancel", e));
        this.statusBar = new StatusBar(services);
        Commands.register(this, services);
        this.toggles = new OverlayToggles(overlays, services.commands());

        this.systemMenu = systemMenu;
        this.extendedSupported = extendedSupported;
        this.menus = new AppMenus(services, systemMenu);
        this.toolbar = new AppToolbar(services, toggles);
        // The stage style is fixed before the window first shows, so this is decided once per window.
        this.headerBar = usesHeaderBar(systemMenu, services.settings().titleBarMenus, extendedSupported)
                ? buildHeaderBar()
                : null;
        if (headerBar != null && !stage.isShowing()) stage.initStyle(StageStyle.EXTENDED);
        band.getStyleClass().add("chrome-band");
        band.getChildren().setAll(headerBar != null ? headerBar : menus.bar(), toolbar.node());
        layout.setTop(band);
        if (headerBar != null) stage.fullScreenProperty().addListener((o, was, full) -> placeHeaderBar(full));
        LibraryService library = services.library();
        this.tabs = new DocumentTabs(services, library, placeholder(), stage::setTitle, restoreSession);
        this.restoreSession = restoreSession;
        tabs.onWelcomeShown(this::refreshWelcome);
        this.toolWindows = new ToolWindowManager(services, tabs.node());
        this.systemsWindow =
                new ToolWindow("systems", Messages.tr("tool.systems"), ToolWindow.Side.LEFT, Icons.DOCUMENTS, () -> {
                    LibraryPanels.Systems built = new LibraryPanels.Systems(
                            tabs, library, () -> new CommandMenu(services, java.util.Arrays.asList("systems.open")));
                    systems = built;
                    return built.node();
                });
        this.gameWindow = new ToolWindow("game", Messages.tr("tool.game"), ToolWindow.Side.RIGHT, Icons.OUTLINE, () -> {
            LibraryPanels.GamePanel built =
                    new LibraryPanels.GamePanel(() -> services.commands().run("game.play"));
            game = built;
            built.show(library.state().retroArch(), tabs.selectedGame());
            return built.node();
        });
        this.retroarchWindow = new ToolWindow(
                        "retroarch", Messages.tr("tool.retroarch"), ToolWindow.Side.BOTTOM, Icons.OUTPUT, () -> {
                            LibraryPanels.Console built = new LibraryPanels.Console();
                            console = built;
                            built.findBar()
                                    .editingKeys(() ->
                                            services.keybindings().keymap().emacs());
                            built.findBar().runCommandsThrough(services.commands()::run);
                            // A read-only console: its right-click menu is the console's commands (MENU-05).
                            CommandMenu.onConsole(
                                    built.console(),
                                    new CommandMenu(
                                            services,
                                            java.util.Arrays.asList(
                                                    "edit.copy",
                                                    "edit.selectAll",
                                                    null,
                                                    "retroarch.find",
                                                    null,
                                                    "game.stop",
                                                    "retroarch.clear")));
                            built.append(List.copyOf(consoleBacklog));
                            consoleBacklog.clear();
                            return built.node();
                        })
                .firstOpenShare(0.36); // a console at a quarter of the window shows three lines (TABS-21)
        // The stripe button is busy while a game runs, whether or not the window was ever opened (TABS-22).
        retroarchWindow.busyProperty().bind(library.playingProperty());
        library.onOutput(this::appendConsole);
        for (ToolWindow tw : List.of(systemsWindow, gameWindow, retroarchWindow)) toolWindows.register(tw);
        // The Game window follows the active tab and its selected game (TABS-23); on a tab with no system it is
        // unavailable, which hides its stripe button without touching the user's own show or hide (TABS-20).
        tabs.onActiveChanged(this::followActiveTab);
        tabs.onSelectedGameChanged(this::followActiveTab);
        library.onChanged(this::followActiveTab);
        followActiveTab();
        toolWindows.restore();
        layout.setCenter(toolWindows.node());
        layout.setBottom(statusBar);

        Scene scene = Themes.style(new Scene(overlays.root()));
        new KeyDispatcher(services, overlays).install(scene);
        stage.setScene(scene);
        Themes.titleButtons(stage);
        tabs.updateWindowTitle(); // the file, the project and the app (TABS-10)
        for (int size : new int[] {16, 32, 64, 128, 256}) {
            var icon = MainWindow.class.getResource("/com/partida/icons/app-" + size + ".png");
            if (icon != null) stage.getIcons().add(new Image(icon.toExternalForm()));
        }
        Settings.Window window = services.settings().window;
        stage.setWidth(window.width);
        stage.setHeight(window.height);
        if (restoreSession) {
            if (window.x != null && window.y != null) {
                stage.setX(window.x);
                stage.setY(window.y);
            }
            stage.setMaximized(window.maximized);
        }
        // The window's close button is a Quit like any other: one path (TRAY-04).
        stage.setOnCloseRequest(e -> {
            e.consume();
            quit();
        });

        services.store().addListener(this::apply);
        services.onKeybindingsChanged(this::refreshWelcomeHint);
        apply(services.settings());
    }

    public void show() {
        stage.show();
        if (systemMenu && !appMenuInstalled) {
            // macOS: About and Settings in the application menu (ABOUT-01, WIN-15), with Settings' chord in its
            // title, written again after a keymap change.
            appMenuInstalled = true;
            String done = MacAppMenu.install(
                    Messages.tr("command.help.about"),
                    () -> services.commands().run("help.about"),
                    () -> {
                        String title = Messages.tr("command.app.settings");
                        String chord = services.keybindings().label("app.settings");
                        return chord.isEmpty() ? title : title + "   " + chord;
                    },
                    () -> services.commands().run("app.settings"));
            java.util.logging.Logger.getLogger(MainWindow.class.getName()).fine("Application menu: " + done);
            services.onKeybindingsChanged(MacAppMenu::refresh);
        }
    }

    private boolean appMenuInstalled;

    /** Applies the settings that live in this window: the theme, the bars and Simple mode. */
    private void apply(Settings settings) {
        if (settings.theme != Themes.choice()) Themes.apply(settings.theme);
        if (settings.uiZoom != Themes.zoom()) zoomTo(settings.uiZoom);
        tabs.textZoom(settings.textZoom); // the documents' own zoom (WIN-09)
        toolbar.node().setVisible(settings.showToolbar);
        toolbar.node().setManaged(settings.showToolbar);
        // Simple mode: fewer toolbar groups, the toggle lit, and the shorter menu, rebuilt only on a change.
        toolbar.setSimple(settings.simpleMode);
        menus.setSimple(settings.simpleMode);
        statusBar.setVisible(settings.showStatusBar);
        statusBar.setManaged(settings.showStatusBar);
        tabs.setTabBarShown(settings.showTabBar);
    }

    /** Scales the sheets off the FX thread, then swaps them in at once on it (WIN-12, PERF-01). */
    private void zoomTo(int percent) {
        Background.run(() -> {
            Themes.prepareZoom(percent);
            Platform.runLater(() -> {
                if (services.settings().uiZoom == percent) Themes.zoom(percent);
            });
        });
    }

    private void followActiveTab() {
        toolWindows.setAvailable(gameWindow, tabs.activeView() != null);
        LibraryPanels.GamePanel built = game;
        if (built != null) built.show(services.library().state().retroArch(), tabs.selectedGame());
    }

    /** RetroArch's output: into the console once it is built, else held until it is (the newest kept). */
    private void appendConsole(List<String> lines) {
        LibraryPanels.Console built = console;
        if (built != null) {
            built.append(lines);
            return;
        }
        consoleBacklog.addAll(lines);
        while (consoleBacklog.size() > LibraryPanels.Console.MAX_LINES) consoleBacklog.removeFirst();
    }

    /** The Welcome tab's page; the app's own content goes in tabs beside it. */
    private VBox placeholder() {
        Label title = new Label(AppInfo.NAME);
        title.getStyleClass().add("welcome-title");
        Label tagline = new Label(Messages.tr("app.tagline"));
        tagline.getStyleClass().add("muted");
        refreshWelcomeHint();
        HBox actions = welcomeActions;
        actions.setAlignment(Pos.CENTER);
        for (String id :
                List.of("palette.show", "library.addFolder", "app.settings", "help.documentation", "help.whatsNew")) {
            Command command = services.commands().get(id);
            if (command == null) continue;
            Button button = new Button(command.title());
            button.setUserData(id);
            button.setOnAction(e -> services.commands().run(id));
            toggles.track(button, id); // the palette's button looks pressed while the palette is up (TBAR-07)
            actions.getChildren().add(button);
        }
        VBox box = new VBox(12, title, tagline, welcomeHint, actions);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("welcome");
        return box;
    }

    /**
     * Rebuilds the Welcome page each time it is shown (WELC-05): its actions' titles and the hint's chord are
     * current, and the focus stays where it was.
     */
    void refreshWelcome() {
        javafx.scene.Node owner =
                stage.getScene() == null ? null : stage.getScene().getFocusOwner();
        refreshWelcomeHint();
        for (javafx.scene.Node node : welcomeActions.getChildren()) {
            if (node instanceof Button button && button.getUserData() instanceof String id) {
                Command command = services.commands().get(id);
                if (command != null) button.setText(command.title());
            }
        }
        welcomeRefreshes++;
        if (owner != null && owner.getScene() != null) owner.requestFocus();
    }

    /** How many times the Welcome page was rebuilt: the tests read it. */
    int welcomeRefreshes() {
        return welcomeRefreshes;
    }

    private int welcomeRefreshes;
    private final HBox welcomeActions = new HBox(8);
    private final boolean restoreSession;

    /** The welcome hint names the palette's chord in the active keymap, so it follows a keymap change. */
    private void refreshWelcomeHint() {
        String chord = services.keybindings().label("palette.show");
        welcomeHint.setText(Messages.tr("welcome.hint", chord.isEmpty() ? "—" : chord));
    }

    /** Whether this window draws its own title bar: the pure rule in {@link Chrome#usesHeaderBar}. */
    /** Why the title-bar menus can't be had in this window, or null (OS-01). */
    @Nullable
    String titleBarMenusUnavailable() {
        return Chrome.titleBarMenusUnavailable(systemMenu, extendedSupported);
    }

    static boolean usesHeaderBar(boolean systemMenu, boolean setting, boolean extendedSupported) {
        return Chrome.usesHeaderBar(systemMenu, setting, extendedSupported);
    }

    /**
     * The title bar: the menus on the left, the window title in the middle. The bar's background drags
     * the window and its controls do not; the title is marked draggable too, since a title bar you
     * cannot drag by its title is a trap.
     */
    HeaderBar buildHeaderBar() {
        HeaderBar bar = new HeaderBar();
        bar.getStyleClass().add("header-bar");
        bar.setLeft(menus.bar());
        Label title = new Label();
        title.getStyleClass().add("header-title");
        title.textProperty().bind(stage.titleProperty());
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE);
        bar.setCenter(title);
        return bar;
    }

    /** Full screen has no title bar: the menus become an ordinary row, and come back on the way out. */
    void placeHeaderBar(boolean fullScreen) {
        HeaderBar header = headerBar;
        if (header == null) return;
        if (fullScreen) {
            header.setLeft(null);
            band.getChildren().set(0, menus.bar());
        } else {
            band.getChildren().set(0, header);
            header.setLeft(menus.bar());
        }
        HeaderBar.setSystemButtonHeight(stage, fullScreen ? 0 : HeaderBar.USE_DEFAULT_SIZE);
    }

    // ---- what the commands do ----

    /** Runs {@code action} on the selected tab, if there is one. */
    void onSelectedTab(java.util.function.Consumer<javafx.scene.control.Tab> action) {
        javafx.scene.control.Tab tab = tabs.selected();
        if (tab != null) action.accept(tab);
    }

    /** The tab switcher (TABS-09), built on first use. */
    void showSwitcher() {
        Switcher built = switcher;
        if (built == null) {
            built = new Switcher(tabs, overlays);
            built.keymap(services::keybindings, Os.isMac()); // the pickers' keys (QOPEN-12)
            switcher = built;
        }
        built.show();
    }

    /** Runs {@code action} on the tool window a command acts on, if there is one. */
    void onToolWindow(@Nullable ToolWindow tw, java.util.function.Consumer<ToolWindow> action) {
        if (tw != null) action.accept(tw);
    }

    // ---- the text commands (MENU-05): on the text control that has the keyboard ----

    /** The text field or area of this window that has the keyboard, or null. */
    javafx.scene.control.@Nullable TextInputControl focusedText() {
        return stage.getScene() != null
                        && stage.getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl text
                ? text
                : null;
    }

    /** The Systems window's list, once built. */
    LibraryPanels.@Nullable Systems systems() {
        return systems;
    }

    // ---- find (FIND-01, FIND-04) ----

    /** The active tab's find bar, or null on the Welcome tab. */
    @Nullable
    FindBar documentFindBar() {
        SystemView view = tabs.activeView();
        return view == null ? null : view.findBar();
    }

    /** The RetroArch console's find bar, or null before the window was first opened. */
    @Nullable
    FindBar consoleFindBar() {
        LibraryPanels.Console built = console;
        return built == null ? null : built.findBar();
    }

    /** Find in the console: opens the window and its bar, or steps to the next match when the bar is open. */
    void findInConsole() {
        if (!toolWindows.isOpen(retroarchWindow)) toolWindows.openWindow(retroarchWindow, false);
        FindBar bar = consoleFindBar();
        if (bar != null) bar.openOrNext();
    }

    // ---- the library ----

    /**
     * The game selected in the active tab, or null. The menus and the toolbar ask the commands whether they can
     * run while this window is still being built, before the tabs exist: then there is none.
     */
    @Nullable
    Game selectedGame() {
        DocumentTabs built = tabs;
        return built == null ? null : built.selectedGame();
    }

    /** Play: the game selected in the active tab, with the core the Game window shows for it. */
    void playSelected() {
        Game selected = selectedGame();
        if (selected == null) return;
        LibraryPanels.GamePanel built = game;
        services.library().play(selected, built == null ? null : built.selectedCore(selected));
    }

    @Nullable
    String playUnavailable() {
        return services.library().playUnavailable(selectedGame());
    }

    /** Add Games Folder: a folder the user picks joins Settings → Library, scanned at once. */
    void addLibraryFolder() {
        Path folder = services.host().chooseFolder(stage, Messages.tr("command.library.addFolder"));
        if (folder == null) return;
        String path = folder.toAbsolutePath().toString();
        if (services.settings().library.folder(path) != null) {
            services.echo("status.folderCannotAddTwice", path);
            return;
        }
        services.store().update(s -> s.library.folders.add(new Settings.Library.Folder(path, "")));
        services.echo("status.folderAdded", path);
    }

    /** Remove Games Folder: pick one of Settings → Library's folders; its games leave the library. */
    void removeLibraryFolder() {
        List<Picker.Item<String>> items = services.settings().library.folders.stream()
                .map(f -> new Picker.Item<>(f.path, AppInfo.tildeHome(f.path), "", "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.folder"), "", items, path -> {
                    services.store().update(s -> s.library.folders.removeIf(f -> f.path.equals(path)));
                    services.echo("status.folderRemoved", path);
                })
                .titled(Messages.tr("command.library.removeFolder")) // QOPEN-02
                .keymap(services::keybindings, Os.isMac())
                .show();
    }

    /** Set a Folder's Core: pick a folder, then the core for all of it (or detection by extension). */
    void chooseFolderCore() {
        List<Picker.Item<String>> folders = services.settings().library.folders.stream()
                .map(f -> new Picker.Item<>(f.path, AppInfo.tildeHome(f.path), coreLabel(f.coreId), "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.folder"), "", folders, this::chooseCoreFor)
                .titled(Messages.tr("command.library.setFolderCore"))
                .keymap(services::keybindings, Os.isMac())
                .show();
    }

    private void chooseCoreFor(String path) {
        List<Picker.Item<String>> cores = new java.util.ArrayList<>();
        cores.add(new Picker.Item<>("", Messages.tr("settings.library.core.detect"), "", "", null));
        com.partida.library.RetroArchContext ctx = services.library().state().retroArch();
        if (ctx != null) {
            for (com.partida.retroarch.CoreInfo core : ctx.cores().all()) {
                cores.add(new Picker.Item<>(core.id(), core.label(), core.id(), "", null));
            }
        }
        Settings.Library.Folder folder = services.settings().library.folder(path);
        Picker<String> picker = new Picker<>(overlays, Messages.tr("picker.core"), "", cores, id -> {
                    services.store().update(s -> {
                        Settings.Library.Folder f = s.library.folder(path);
                        if (f != null) f.coreId = id;
                    });
                    services.echo("status.folderCore", AppInfo.tildeHome(path), coreLabel(id));
                })
                .titled(AppInfo.tildeHome(path))
                .keymap(services::keybindings, Os.isMac());
        if (folder != null) picker.current(folder.coreId);
        picker.show();
    }

    /** A core's name for a folder row: the installed core's, else its id, else "Detect by file extension". */
    String coreLabel(String coreId) {
        if (coreId.isBlank()) return Messages.tr("settings.library.core.detect");
        com.partida.library.RetroArchContext ctx = services.library().state().retroArch();
        if (ctx == null) return coreId;
        return ctx.cores()
                .byId(coreId)
                .map(com.partida.retroarch.CoreInfo::label)
                .orElse(coreId);
    }

    @Nullable
    String foldersUnavailable() {
        return services.settings().library.folders.isEmpty() ? Messages.tr("palette.disabled.noFolders") : null;
    }

    void openPalette() {
        Picker<String> palette = new Picker<>(
                        overlays,
                        Messages.tr("palette.prompt"),
                        services.keybindings().label("palette.show"),
                        paletteItems(),
                        services.commands()::run)
                .keymap(services::keybindings, Os.isMac())
                // PAL-08: the help chord opens the selected command's page in this version's docs.
                .help(id -> services.host().openUrl(AppInfo.docsUrl() + "/commands/#" + id));
        palette.hint().setText(legend(palette));
        // PAL-09: a keymap switch while it is open draws its chips and its legend again.
        Runnable rebind =
                () -> palette.rebind(services.keybindings().label("palette.show"), paletteItems(), legend(palette));
        services.onKeybindingsChanged(rebind);
        palette.onHidden(() -> services.offKeybindingsChanged(rebind)).show();
    }

    /** Every registered command (PAL-02): one that cannot run now stays listed, grayed with the reason. */
    private List<Picker.Item<String>> paletteItems() {
        return services.commands().all().stream()
                .sorted(java.util.Comparator.comparing(Command::title))
                .map(c -> new Picker.Item<>(
                        c.id(),
                        c.title(),
                        c.description(),
                        services.keybindings().label(c.id()),
                        c.unavailableReason(),
                        Picker.idWords(c.id())))
                .toList();
    }

    /** The palette's key legend in the active keymap: its moves, run, cancel and help (PAL-06, PAL-08). */
    private String legend(Picker<String> palette) {
        var keys = services.keybindings();
        String moves = java.util.stream.Stream.of("nav.lineDown", "nav.lineUp")
                .map(keys::label)
                .filter(l -> !l.isEmpty())
                .collect(java.util.stream.Collectors.joining(" "));
        String cancel = keys.label("edit.cancel");
        String help = palette.helpLabel(keys);
        String legend = Messages.tr(
                "palette.legend",
                moves.isEmpty() ? "↑↓" : "↑↓ / " + moves,
                cancel.isEmpty() ? "Esc" : "Esc / " + cancel);
        return help.isEmpty() ? legend : legend + " · " + Messages.tr("palette.legendHelp", help);
    }

    void chooseTheme() {
        List<Picker.Item<ThemeChoice>> items = java.util.Arrays.stream(ThemeChoice.values())
                .map(t -> new Picker.Item<>(
                        t, Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT)), "", "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.theme"), "", items, t -> {
                    services.store().update(s -> s.theme = t);
                    services.echo(
                            "status.theme", Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT)));
                })
                .titled(Messages.tr("command.settings.theme")) // QOPEN-02
                .keymap(services::keybindings, Os.isMac())
                .current(services.settings().theme) // QOPEN-04
                .show();
    }

    void chooseKeymap() {
        List<Picker.Item<String>> items = Keymap.PRESETS.stream()
                .map(id -> new Picker.Item<>(id, Keymap.load(id).name(), "", "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.keymap"), "", items, id -> {
                    services.store().update(s -> s.keymap = id);
                    services.echo("status.keymap", Keymap.load(id).name());
                })
                .titled(Messages.tr("command.settings.keymap"))
                .keymap(services::keybindings, Os.isMac())
                .current(services.settings().keymap)
                .show();
    }

    void chooseLanguage() {
        List<Picker.Item<String>> items = new java.util.ArrayList<>();
        items.add(new Picker.Item<>("", Messages.tr("settings.language.system"), "", "", null));
        for (Map.Entry<String, String> language : Messages.available().entrySet()) {
            items.add(new Picker.Item<>(language.getKey(), language.getValue(), language.getKey(), "", null));
        }
        new Picker<>(overlays, Messages.tr("picker.language"), "", items, code -> {
                    services.store().update(s -> s.language = code);
                    services.echo("status.languageRestart");
                })
                .titled(Messages.tr("command.settings.language"))
                .keymap(services::keybindings, Os.isMac())
                .current(services.settings().language)
                .show();
    }

    // ---- a palette command per setting that a picker or a toggle does not cover (SET-06) ----

    TextInputDialog promptSyncRepository() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setRepository"),
                Messages.tr("settings.sync.repo.title"),
                services.settings().sync.repoUrl,
                url -> url.isEmpty() ? null : reason(SyncEngine.checkUrl(url), "settings.sync.url."),
                url -> services.store().update(s -> s.sync.repoUrl = url));
    }

    TextInputDialog promptSyncBranch() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setBranch"),
                Messages.tr("settings.sync.branch.title"),
                services.settings().sync.branch,
                branch -> reason(SyncEngine.checkBranch(branch), "settings.sync.branch."),
                branch -> services.store().update(s -> s.sync.branch = branch));
    }

    TextInputDialog promptSyncInterval() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setInterval"),
                Messages.tr("settings.sync.interval.prompt"),
                String.valueOf(services.settings().sync.intervalMinutes),
                text -> Settings.Sync.parseInterval(text) == null
                        ? Messages.tr("settings.sync.interval.invalid")
                        : null,
                text -> {
                    Integer minutes = Settings.Sync.parseInterval(text);
                    if (minutes != null) services.store().update(s -> s.sync.intervalMinutes = minutes);
                });
    }

    private static @Nullable String reason(@Nullable String suffix, String prefix) {
        return suffix == null ? null : Messages.tr(prefix + suffix);
    }

    TextInputDialog promptRetroArchFolder() {
        return Prompts.text(
                stage,
                Messages.tr("command.library.setRetroArchFolder"),
                Messages.tr("settings.library.retroarchFolder.title"),
                services.settings().library.retroarchConfigDir,
                MainWindow::retroArchFolderProblem,
                text -> services.store().update(s -> s.library.retroarchConfigDir = text.strip()));
    }

    /**
     * Why {@code text} can't be RetroArch's config folder, or null: blank (find RetroArch on its own) or a full
     * path. Pure: whether the folder holds a RetroArch shows when the library loads, off the FX thread (PERF-01).
     */
    static @Nullable String retroArchFolderProblem(String text) {
        if (text.isBlank()) return null;
        try {
            return Path.of(text.strip()).isAbsolute() ? null : Messages.tr("settings.library.retroarchFolder.invalid");
        } catch (java.nio.file.InvalidPathException e) {
            return Messages.tr("settings.library.retroarchFolder.invalid");
        }
    }

    TextInputDialog promptRetroArchCommand() {
        return Prompts.text(
                stage,
                Messages.tr("command.library.setRetroArchCommand"),
                Messages.tr("settings.library.retroarchCommand.title"),
                services.settings().library.retroarchCommand,
                text -> null,
                text -> services.store().update(s -> s.library.retroarchCommand = text.strip()));
    }

    /** Settings → Keymap from the palette: pick a command, then type its keys (blank unbinds). */
    void chooseBinding() {
        List<Picker.Item<Command>> items = services.commands().all().stream()
                .sorted(java.util.Comparator.comparing(Command::title))
                .map(c -> new Picker.Item<>(
                        c, c.title(), c.description(), services.keybindings().label(c.id()), null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.bindKey"), "", items, this::promptBinding)
                .titled(Messages.tr("command.settings.bindKey")) // QOPEN-02
                .keymap(services::keybindings, Os.isMac()) // QOPEN-06
                .show();
    }

    TextInputDialog promptBinding(Command command) {
        String base = services.keybindings().keymap().bindings().getOrDefault(command.id(), "");
        String current = services.settings().keybindings.getOrDefault(command.id(), base);
        return Prompts.text(
                stage,
                Messages.tr("command.settings.bindKey"),
                command.title(),
                current,
                text -> text.isEmpty() || KeyStroke.parseSequence(text) != null
                        ? null
                        : Messages.tr("settings.bindings.invalid"),
                text -> services.store().update(s -> {
                    if (text.equals(base)) s.keybindings.remove(command.id());
                    else s.keybindings.put(command.id(), text);
                }));
    }

    void openMessageLog() {
        messageLog().show();
    }

    /** Copies what the Message Log lists: the filtered entries while it is open, else every entry. */
    void copyMessageLog() {
        MessageLogView view = messageLog;
        Clipboards.copy(MessageLogView.asText(
                view != null ? view.listed() : services.messages().entries()));
        services.echo("status.messageLogCopied");
    }

    /** Empties the Message Log and its unread count. */
    void clearMessageLog() {
        services.messages().clear();
        MessageLogView view = messageLog;
        if (view != null) view.refresh();
        statusBar.refreshUnread();
    }

    /** About's Copy details: the environment block, for a bug report. */
    void copyDetails() {
        Clipboards.copy(AppInfo.environment(services.store().dir()));
        services.echo("status.detailsCopied");
    }

    boolean isDebugLogOpen() {
        DebugLogWindow window = debugLog;
        return window != null && window.stage().isShowing();
    }

    void refreshDebugLog() {
        DebugLogWindow window = debugLog;
        if (window != null) window.refresh();
    }

    /** Debug Log: Find: opens the Debug Log and its find bar. */
    void findInDebugLog() {
        openDebugLog();
        DebugLogWindow window = debugLog;
        if (window != null) window.find();
    }

    boolean isDebugLogFindOpen() {
        DebugLogWindow window = debugLog;
        return window != null && window.stage().isShowing() && window.findBar().isOpen();
    }

    void stepDebugLogFind(boolean forward) {
        DebugLogWindow window = debugLog;
        if (window != null) window.findBar().step(forward);
    }

    /** The command whose key-binding row is selected in an open Settings → Keymap, or null. */
    @Nullable
    Command selectedBinding() {
        SettingsWindow window = settings;
        if (window == null || !window.stage().isShowing()) return null;
        return window.bindingList().getSelectionModel().getSelectedItem();
    }

    void resetSelectedBinding() {
        SettingsWindow window = settings;
        Command selected = selectedBinding();
        if (window != null && selected != null) window.resetBinding(selected);
    }

    void clearDebugLog() {
        DebugLog.clear();
        refreshDebugLog();
    }

    /** Saves the Debug Log as a text file the user chooses; the write runs off the FX thread. */
    void exportDebugLog() {
        DebugLogWindow window = debugLog;
        Stage owner = window != null && window.stage().isShowing() ? window.stage() : stage;
        Path target = services.host().chooseSaveFile(owner, Messages.tr("debuglog.export"), AppInfo.ID + "-debug.log");
        if (target == null) return;
        String text = DebugLog.text();
        Background.run(() -> {
            try {
                java.nio.file.Files.writeString(target, text, java.nio.charset.StandardCharsets.UTF_8);
                services.echo("status.logExported", String.valueOf(target.getFileName()));
            } catch (IOException e) {
                services.echo("status.logExportFailed", String.valueOf(e.getMessage()));
            }
        });
    }

    void openSettings(SettingsWindow.Page page) {
        SettingsWindow window = settings;
        if (window == null) {
            window = new SettingsWindow(services, stage, titleBarMenusUnavailable());
            settings = window;
        }
        window.show(page);
    }

    /** Settings Sync's "Create on GitHub…", from the palette: the Sync page with the name prompt open. */
    void createSyncRepo() {
        openSettings(SettingsWindow.Page.SYNC);
        SettingsWindow window = settings;
        if (window != null) window.promptCreateOnGitHub();
    }

    /** The Doctor (DIAG-07). */
    void openDoctor() {
        new DoctorWindow(services, stage).show();
    }

    void openAbout() {
        new AboutDialog(services, stage).show();
    }

    /** The Pauta window: the edition, the conformance and every exception in {@code pauta.json}. */
    void openPauta() {
        new PautaWindow(services, stage).show();
    }

    /** The Pauta window's Copy as text: the conformance and the exceptions, as Markdown. */
    void copyPauta() {
        Clipboards.copy(PautaWindow.markdown(PautaConformance.exceptions()));
        services.echo("status.pautaCopied");
    }

    void openDebugLog() {
        DebugLogWindow window = debugLog;
        if (window == null) {
            window = new DebugLogWindow(services, stage);
            debugLog = window;
        }
        window.show();
    }

    void openReportProblem() {
        new ReportProblemWindow(services, stage).show();
    }

    void exportConfig() {
        Path target = services.host()
                .chooseSaveFile(
                        stage, Messages.tr("command.config.export"), ConfigExporter.defaultName(LocalDateTime.now()));
        if (target == null) return;
        Path dir = services.store().dir();
        Background.run(
                () -> { // the walk and the zip write stay off the FX thread (PERF-01)
                    services.store().flush();
                    try {
                        int count = ConfigExporter.export(dir, target);
                        services.echo("status.configExported", count, String.valueOf(target.getFileName()));
                    } catch (IOException e) {
                        services.echo("status.configExportFailed", String.valueOf(e.getMessage()));
                    }
                });
    }

    /**
     * Import Configuration: the zip is read and checked off the FX thread, then the question lists the
     * parts it holds, and only the ticked ones replace this computer's (SET-12, WIN-05). Each import takes
     * a generation, and its scan and its result are applied only while it is still the latest, checked
     * again on the FX thread (PERF-03): a second import, or quitting, drops the first.
     */
    void importConfig() {
        Path source = services.host().chooseOpenFile(stage, Messages.tr("command.config.import"));
        if (source == null) return;
        Path dir = services.store().dir();
        long mine = imports.incrementAndGet();
        Background.run(() -> {
            ConfigImport.Scan scan;
            try {
                scan = ConfigImport.read(source);
            } catch (IOException e) {
                if (mine == imports.get()) services.echo("status.configImportFailed", String.valueOf(e.getMessage()));
                return;
            }
            Platform.runLater(() -> {
                if (current(mine)) askAndImport(source, scan, dir, mine);
            });
        });
    }

    /** Whether import {@code generation} is still the latest; a stale one is counted for the tests. */
    private boolean current(long generation) {
        if (generation == imports.get()) return true;
        importsDropped.incrementAndGet();
        return false;
    }

    /** How many stale import results were dropped (PERF-03). */
    int importsDropped() {
        return importsDropped.get();
    }

    private void askAndImport(Path source, ConfigImport.Scan scan, Path dir, long mine) {
        List<ConfigImport.Part> parts = List.copyOf(scan.available());
        Confirmations.Question question = Confirmations.importConfig(String.valueOf(source.getFileName()), parts);
        if (!services.host().confirm(stage, question) || !current(mine)) return;
        java.util.Set<ConfigImport.Part> ticked = java.util.EnumSet.noneOf(ConfigImport.Part.class);
        for (int i = 0; i < parts.size(); i++) {
            if (question.choices().get(i).ticked()) ticked.add(parts.get(i));
        }
        if (ticked.isEmpty()) return;
        Background.run(() -> {
            // A save still on its way to disk would land after the import and undo it: wait for it first,
            // as Export does. Off the FX thread (PERF-01).
            services.store().flush();
            try {
                int count = ConfigImport.apply(scan, dir, ticked);
                ConfigStore.Loaded loaded = services.store().read(); // the file work, here
                Platform.runLater(() -> {
                    if (!current(mine)) return; // a later import, or the quit, supersedes this one
                    services.store().install(loaded);
                    services.echo("status.configImported", count);
                });
            } catch (IOException e) {
                services.echo("status.configImportFailed", String.valueOf(e.getMessage()));
            }
        });
    }

    /** Another launch handed its arguments over: come to the front and say so. */
    public void handOff(List<String> args) {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        services.echo("status.handOff");
    }

    /**
     * The one quit path: File → Quit, the window's close button and the system's Quit all come here. It
     * closes the app's other windows and dialogs first, so no nested event loop is left running, then
     * saves the window geometry and the settings, then exits (TRAY-04).
     */
    /**
     * The one quit method (WIN-13): the Quit row, {@code app.quit}, the window's close button and, on macOS,
     * the application menu's and the Dock's Quit (JavaFX turns those into this window's close request) all
     * come here. It asks before the session's typing is lost (CONF-01), then closes every other window and
     * makes the save a close makes.
     */
    void quit() {
        List<String> unsaved = tabs.unsavedNames();
        if (!unsaved.isEmpty() && !services.host().confirm(stage, Confirmations.quitUnsaved(unsaved))) return;
        imports.incrementAndGet(); // an import still in flight is never applied
        toolWindows.saveForQuit(); // before the floating windows close with the rest
        for (javafx.stage.Window other : List.copyOf(javafx.stage.Window.getWindows())) {
            if (other != stage) other.hide();
        }
        saveWindowState();
        // The last save is waited for off the FX thread (PERF-01); the exit comes back to it.
        Background.run(() -> {
            services.store().flush();
            DebugLog.flushFile(); // the log's last lines, before the JVM goes
            Platform.runLater(services.host()::exit);
        });
    }

    /** Remembers size, position and maximized state for the next launch. */
    void saveWindowState() {
        // What was open, for the next launch (WIN-08); a run started with --no-session leaves the last
        // session's list as it was (CLI-04).
        java.util.List<String> open = tabs.openPaths();
        String active = tabs.activePath();
        if (restoreSession) {
            services.store().update(s -> {
                s.window.documents = open;
                s.window.activeDocument = active;
            });
        }
        boolean maximized = stage.isMaximized();
        double x = stage.getX();
        double y = stage.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();
        services.store().update(s -> {
            s.window.maximized = maximized;
            if (!maximized && width > 0 && height > 0 && !Double.isNaN(x)) {
                s.window.x = x;
                s.window.y = y;
                s.window.width = width;
                s.window.height = height;
            }
        });
    }

    // ---- accessors ----

    public Stage stage() {
        return stage;
    }

    OverlayHost overlays() {
        return overlays;
    }

    StatusBar statusBar() {
        return statusBar;
    }

    MenuBar menuBar() {
        return menus.bar();
    }

    AppToolbar toolbar() {
        return toolbar;
    }

    /** The top band: the title bar or menu row, then the toolbar. */
    VBox band() {
        return band;
    }

    DocumentTabs tabs() {
        return tabs;
    }

    ToolWindowManager toolWindows() {
        return toolWindows;
    }

    ToolWindow systemsWindow() {
        return systemsWindow;
    }

    ToolWindow gameWindow() {
        return gameWindow;
    }

    ToolWindow retroarchWindow() {
        return retroarchWindow;
    }

    LibraryPanels.@Nullable Console console() {
        return console;
    }

    LibraryPanels.@Nullable GamePanel game() {
        return game;
    }

    @Nullable
    Switcher switcher() {
        return switcher;
    }

    OverlayToggles toggles() {
        return toggles;
    }

    AppMenus menus() {
        return menus;
    }

    @Nullable
    HeaderBar headerBar() {
        return headerBar;
    }

    MessageLogView messageLog() {
        MessageLogView view = messageLog;
        if (view == null) {
            view = new MessageLogView(services, overlays, statusBar::refreshUnread);
            messageLog = view;
        }
        return view;
    }

    /** The Message Log if it has been built, without building it. */
    @Nullable
    MessageLogView builtMessageLog() {
        return messageLog;
    }

    @Nullable
    SettingsWindow settingsWindow() {
        return settings;
    }

    @Nullable
    DebugLogWindow debugLogWindow() {
        return debugLog;
    }
}
