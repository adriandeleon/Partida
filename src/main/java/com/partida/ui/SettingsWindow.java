package com.partida.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import com.partida.AppInfo;
import com.partida.command.Command;
import com.partida.command.KeyStroke;
import com.partida.command.Keymap;
import com.partida.config.Settings;
import com.partida.config.ThemeChoice;
import com.partida.i18n.Messages;
import com.partida.sync.SyncEngine;
import org.jspecify.annotations.Nullable;

/**
 * Settings: a sidebar of pages, the page on the right, and a search field on top that hides every
 * non-matching row and card and opens the page with the best match. There is no Save button: a change
 * applies as it is made and reaches every window through the config store. Text fields commit on Enter
 * or focus loss; an invalid value is marked and never committed.
 */
public final class SettingsWindow {

    /** The pages, in sidebar order. */
    public enum Page {
        GENERAL("app.settings"),
        LIBRARY("library.settings"),
        APPEARANCE("settings.theme"),
        KEYMAP("settings.keymap"),
        SYNC("sync.setup"),
        ADVANCED("config.openFolder");

        /** The command whose glyph the page wears in the sidebar ({@link Icons#forCommand}). */
        final String command;

        Page(String command) {
            this.command = command;
        }

        String title() {
            return Messages.tr("settings.page." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Each page's commands, for its sidebar row's right-click menu; {@code null} is a separator. */
    static final Map<Page, List<@Nullable String>> PAGE_COMMANDS = Map.of(
            Page.GENERAL,
                    java.util.Arrays.asList(
                            "settings.language",
                            "settings.toggleUpdateCheck",
                            "view.toggleToolbar",
                            "view.toggleTabBar",
                            "view.toggleStatusBar",
                            "view.toggleSimpleMode"),
            Page.LIBRARY,
                    java.util.Arrays.asList(
                            "library.addFolder",
                            "library.removeFolder",
                            "library.setFolderCore",
                            null,
                            "library.setRetroArchFolder",
                            "library.setRetroArchCommand",
                            "library.toggleRecentlyPlayed",
                            null,
                            "library.refresh"),
            Page.APPEARANCE, java.util.Arrays.asList("settings.theme", "view.toggleTitleBarMenus"),
            Page.KEYMAP, java.util.Arrays.asList("settings.keymap", "settings.bindKey"),
            Page.SYNC,
                    java.util.Arrays.asList(
                            "sync.now",
                            "sync.toggleAuto",
                            null,
                            "sync.setRepository",
                            "sync.setBranch",
                            "sync.setInterval"),
            Page.ADVANCED,
                    java.util.Arrays.asList(
                            "config.export", "config.import", null, "config.openFolder", "help.doctor"));

    private final Map<Page, CommandMenu> pageMenus = new EnumMap<>(Page.class);

    private CommandMenu pageMenu(Page page) {
        return pageMenus.computeIfAbsent(
                page, p -> new CommandMenu(services, PAGE_COMMANDS.getOrDefault(p, List.of())));
    }

    /** A searchable row and the card it sits in. */
    record Row(Page page, Node node, VBox card, SettingsSearch.Entry entry) {}

    private final AppServices services;
    private final Stage stage = new Stage();
    private final TextField search = new TextField();
    private final ListView<Page> sidebar = new ListView<>();
    private final ScrollPane scroll = new ScrollPane();
    private final Map<Page, VBox> pages = new EnumMap<>(Page.class);
    private final List<Row> rows = new ArrayList<>();
    private final List<Runnable> loaders = new ArrayList<>();
    private final Map<Page, Boolean> pageHits = new EnumMap<>(Page.class);
    private final ListView<Command> bindingList = new ListView<>();
    private final javafx.collections.ObservableList<Command> bindingSource =
            javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<Command> bindingFilter =
            new javafx.collections.transformation.FilteredList<>(bindingSource);
    private final Map<Command, SettingsSearch.Entry> bindingEntries = new java.util.LinkedHashMap<>();

    private final ComboBox<String> language = new ComboBox<>();
    private final SettingSwitch updates = new SettingSwitch(Messages.tr("settings.updates.title"));
    private final SettingSwitch toolbar = new SettingSwitch(Messages.tr("settings.toolbar.title"));
    private final SettingSwitch tabBar = new SettingSwitch(Messages.tr("settings.tabBar.title"));
    private final SettingSwitch simpleMode = new SettingSwitch(Messages.tr("settings.simpleMode.title"));
    private final SettingSwitch statusBar = new SettingSwitch(Messages.tr("settings.statusBar.title"));
    private final ComboBox<ThemeChoice> theme = new ComboBox<>();
    private final ComboBox<Integer> uiZoom = new ComboBox<>();
    private final ComboBox<Integer> textZoom = new ComboBox<>();
    private final SettingSwitch titleBar = new SettingSwitch(Messages.tr("settings.titleBar.title"));
    private final ComboBox<String> keymap = new ComboBox<>();
    private final TextField syncUrl = new TextField();
    private final TextField syncBranch = new TextField();
    private final SettingSwitch syncAuto = new SettingSwitch(Messages.tr("settings.sync.auto.title"));
    private final TextField syncInterval = new TextField();
    private final Label syncPill = new Label();
    private final Label retroarchStatus = new Label();
    private final TextField retroarchFolder = new TextField();
    private final TextField retroarchCommand = new TextField();
    private final SettingSwitch recentlyPlayed =
            new SettingSwitch(Messages.tr("settings.library.recentlyPlayed.title"));
    /**
     * One row per games folder (its path, its core and Remove): a list the user can grow without a limit, so a
     * virtualized ListView with a fixed cell size (PERF-08), narrowed by the search like the key bindings
     * (SET-03).
     */
    private final ListView<Settings.Library.Folder> folderList = new ListView<>();

    private final javafx.collections.ObservableList<Settings.Library.Folder> folderSource =
            javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<Settings.Library.Folder> folderFilter =
            new javafx.collections.transformation.FilteredList<>(folderSource);
    /** The cores a folder can be given, "" (detect) first: the library's, re-read when it loads. */
    private List<String> coreIds = List.of("");

    private String query = "";
    /** Each folder's search entry, by path and core; emptied when the cores' names may change. */
    private final Map<String, SettingsSearch.Entry> folderEntries = new java.util.HashMap<>();

    private final Label noFolders = new Label(Messages.tr("settings.library.folders.none"));
    /** What the list says when the search hides every folder: there are folders, none matches. */
    private final Label noFolderMatch = new Label(Messages.tr("settings.library.folders.noMatch"));

    /**
     * The library's status and the folders' core pickers, read once per pulse: a library change and the
     * settings change behind it both ask (PERF-06).
     */
    private final Redraw libraryStatus = new Redraw(this::loadLibraryStatus);

    private final Runnable libraryListener = libraryStatus::request;
    private final Consumer<Settings> storeListener = s -> load();
    private final Runnable syncListener = this::loadSyncStatus;
    private final @Nullable String noTitleBar;

    public SettingsWindow(AppServices services, @Nullable Window owner) {
        this(
                services,
                owner,
                Chrome.titleBarMenusUnavailable(
                        com.partida.Os.isMac(),
                        javafx.application.Platform.isSupported(
                                javafx.application.ConditionalFeature.EXTENDED_WINDOW)));
    }

    /** With {@code noTitleBar}, why this window's title bar can't hold the menus, or null when it can (OS-01). */
    SettingsWindow(AppServices services, @Nullable Window owner, @Nullable String noTitleBar) {
        this.services = services;
        this.noTitleBar = noTitleBar;
        for (Page page : Page.values()) {
            VBox content = UiZoom.vbox(14);
            content.getStyleClass().add("settings-page");
            UiZoom.pad(content, 18, 22, 18, 22);
            Label title = new Label(page.title());
            title.getStyleClass().add("settings-page-title");
            content.getChildren().add(title);
            pages.put(page, content);
        }
        buildGeneral();
        buildLibrary();
        buildAppearance();
        buildKeymap();
        buildSync();
        buildAdvanced();

        search.setPromptText(Messages.tr("settings.search"));
        search.getStyleClass().add("settings-search");
        search.setAccessibleText(Messages.tr("settings.search"));
        Debounce searching = new Debounce(() -> applySearch(search.getText()));
        search.textProperty().addListener((obs, old, text) -> searching.request());
        sidebar.getItems().setAll(Page.values());
        // A page row's right-click menu holds that page's commands (MENU-05).
        sidebar.setCellFactory(v -> {
            PageCell cell = new PageCell();
            return CommandMenu.onRow(cell, () -> pageMenu(java.util.Objects.requireNonNull(cell.getItem())));
        });
        RowContextMenu.install(sidebar);
        sidebar.getStyleClass().add("settings-sidebar");
        sidebar.setAccessibleText(Messages.tr("settings.title"));
        sidebar.getSelectionModel().selectedItemProperty().addListener((obs, old, page) -> {
            if (page != null) scroll.setContent(pages.get(page));
        });
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");

        VBox side = UiZoom.vbox(8, search, sidebar);
        UiZoom.pad(side, 10);
        UiZoom.bind(side.prefWidthProperty(), 220);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        Label note = new Label(Messages.tr("settings.footer"));
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        Button close = new Button(Messages.tr("button.close"));
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = UiZoom.hbox(10, note, spacer, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        UiZoom.pad(bottom, 8, 12, 10, 12);
        bottom.getStyleClass().add("settings-bottom");

        BorderPane root = new BorderPane(scroll, null, null, bottom, side);
        root.getStyleClass().add("settings-window");
        Scene scene = Themes.style(new Scene(root, 860, 600));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.setTitle(Messages.tr("settings.title"));
        if (owner != null) stage.initOwner(owner);

        // One Settings window per app, kept for the session, so these listeners live as long as it does.
        services.store().addListener(storeListener);
        services.sync().addListener(syncListener);
        load();
        sidebar.getSelectionModel().select(Page.GENERAL);
    }

    /** Opens the window on a page. */
    public void show(Page page) {
        sidebar.getSelectionModel().select(page);
        stage.show();
        stage.toFront();
        search.requestFocus();
    }

    // ---- pages ----

    private void buildGeneral() {
        language.getItems().add("");
        language.getItems().addAll(Messages.available().keySet());
        language.setConverter(converter(code -> code.isEmpty()
                ? Messages.tr("settings.language.system")
                : Messages.available().getOrDefault(code, code)));
        language.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(picked, s -> s.language, v -> {
                    services.store().update(s -> s.language = v);
                    services.echo("status.languageRestart");
                }));
        VBox card = card(Page.GENERAL, null);
        row(card, Page.GENERAL, "settings.language", language, "locale translation");
        updates.setOnAction(e -> services.store().update(s -> s.checkForUpdates = updates.isSelected()));
        row(card, Page.GENERAL, "settings.updates", updates, "release version network");
        toolbar.setOnAction(e -> services.store().update(s -> s.showToolbar = toolbar.isSelected()));
        row(card, Page.GENERAL, "settings.toolbar", toolbar, "buttons icons");
        tabBar.setOnAction(e -> services.store().update(s -> s.showTabBar = tabBar.isSelected()));
        row(card, Page.GENERAL, "settings.tabBar", tabBar, "tabs documents strip");
        statusBar.setOnAction(e -> services.store().update(s -> s.showStatusBar = statusBar.isSelected()));
        row(card, Page.GENERAL, "settings.statusBar", statusBar, "echo messages");
        simpleMode.setOnAction(e -> services.store().update(s -> s.simpleMode = simpleMode.isSelected()));
        row(card, Page.GENERAL, "settings.simpleMode", simpleMode, "beginner basic minimal fewer menus buttons");
        loaders.add(() -> {
            Settings s = services.settings();
            language.setValue(s.language);
            updates.setSelected(s.checkForUpdates);
            toolbar.setSelected(s.showToolbar);
            tabBar.setSelected(s.showTabBar);
            statusBar.setSelected(s.showStatusBar);
            simpleMode.setSelected(s.simpleMode);
        });
    }

    private void buildLibrary() {
        VBox retroarch = card(Page.LIBRARY, Messages.tr("settings.library.retroarch.heading"));
        retroarchStatus.getStyleClass().add("muted");
        retroarchStatus.setWrapText(true);
        Button refresh = new Button(Messages.tr("command.library.refresh"));
        refresh.setOnAction(e -> services.commands().run("library.refresh"));
        HBox statusControl = UiZoom.hbox(8, retroarchStatus, refresh);
        statusControl.setAlignment(Pos.CENTER_RIGHT);
        row(
                retroarch,
                Page.LIBRARY,
                "settings.library.status",
                statusControl,
                "retroarch found detected flatpak install");
        retroarchFolder.setPromptText(Messages.tr("settings.library.retroarchFolder.prompt"));
        retroarchFolder.setOnAction(e -> commitRetroArchFolder());
        retroarchFolder.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitRetroArchFolder();
        });
        row(
                retroarch,
                Page.LIBRARY,
                "settings.library.retroarchFolder",
                retroarchFolder,
                "retroarch.cfg config portable");
        retroarchCommand.setPromptText("retroarch");
        retroarchCommand.setOnAction(e -> commitRetroArchCommand());
        retroarchCommand.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitRetroArchCommand();
        });
        row(
                retroarch,
                Page.LIBRARY,
                "settings.library.retroarchCommand",
                retroarchCommand,
                "launch program executable");
        recentlyPlayed.setOnAction(
                e -> services.store().update(s -> s.library.showRecentlyPlayed = recentlyPlayed.isSelected()));
        row(retroarch, Page.LIBRARY, "settings.library.recentlyPlayed", recentlyPlayed, "history recent");

        VBox folders = card(Page.LIBRARY, Messages.tr("settings.library.folders.heading"));
        Button add = new Button(Messages.tr("command.library.addFolder"));
        add.setOnAction(e -> services.commands().run("library.addFolder"));
        row(folders, Page.LIBRARY, "settings.library.addFolder", add, "games roms folder directory scan core");
        folderList.getStyleClass().add("library-folder-list");
        folderList.setAccessibleText(Messages.tr("settings.library.folders.heading"));
        UiZoom.bind(folderList.fixedCellSizeProperty(), FOLDER_ROW_HEIGHT); // follows the zoom (WIN-12)
        for (Label line : List.of(noFolders, noFolderMatch)) {
            line.getStyleClass().add("muted");
            line.setWrapText(true);
        }
        folderList.setPlaceholder(noFolders);
        folderList.setCellFactory(v -> new FolderCell());
        folderFilter.setPredicate(f -> true);
        folderList.setItems(folderFilter);
        folderList
                .prefHeightProperty()
                .bind(javafx.beans.binding.Bindings.max(
                                1,
                                javafx.beans.binding.Bindings.min(
                                        javafx.beans.binding.Bindings.size(folderFilter), FOLDER_ROWS_SHOWN))
                        .multiply(UiZoom.times(FOLDER_ROW_HEIGHT))
                        .add(2));
        folders.getChildren().add(folderList);
        services.library().onChanged(libraryListener);
        loaders.add(() -> {
            Settings.Library library = services.settings().library;
            retroarchFolder.setText(library.retroarchConfigDir);
            retroarchFolder.getStyleClass().remove("invalid");
            retroarchCommand.setText(library.retroarchCommand);
            recentlyPlayed.setSelected(library.showRecentlyPlayed);
            loadFolders();
            libraryStatus.request();
        });
    }

    /** What the library service found: the RetroArch in use, or why there is none. */
    private void loadLibraryStatus() {
        LibraryService.State state = services.library().state();
        com.partida.library.RetroArchContext ctx = state.retroArch();
        retroarchStatus.setText(
                switch (state.status()) {
                    case LOADING -> Messages.tr("systems.loading");
                    case NOT_FOUND -> Messages.tr("systems.notFound");
                    case FAILED -> Messages.tr("systems.failed", state.detail());
                    case READY ->
                        ctx == null
                                ? ""
                                : Messages.tr(
                                        "settings.library.status.found",
                                        AppInfo.tildeHome(
                                                ctx.install().configDir().toString()),
                                        ctx.cores().all().size());
                });
        // The cores a folder can be given come with the library: the rows' pickers follow it.
        List<String> ids = new ArrayList<>();
        ids.add("");
        if (ctx != null) ctx.cores().all().forEach(c -> ids.add(c.id()));
        if (!ids.equals(coreIds)) {
            coreIds = List.copyOf(ids);
            folderEntries.clear(); // a core's name comes with the library
            folderList.refresh();
        }
    }

    /** The folders, as Settings has them now; each row reads its own folder when it is shown. */
    private void loadFolders() {
        List<Settings.Library.Folder> folders = services.settings().library.folders.stream()
                .map(f -> new Settings.Library.Folder(f.path, f.coreId))
                .toList();
        if (sameFolders(folders, folderSource)) return;
        folderEntries.keySet().removeIf(k -> folders.stream().noneMatch(f -> k.equals(f.path + "\t" + f.coreId)));
        folderSource.setAll(folders);
        applyFolderSearch();
    }

    private static boolean sameFolders(List<Settings.Library.Folder> a, List<Settings.Library.Folder> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).path.equals(b.get(i).path) || !a.get(i).coreId.equals(b.get(i).coreId)) return false;
        }
        return true;
    }

    /** A core's name, or "Detect by file extension" for none. */
    private String coreLabel(String id) {
        if (id.isEmpty()) return Messages.tr("settings.library.core.detect");
        com.partida.library.RetroArchContext ctx = services.library().state().retroArch();
        return ctx == null
                ? id
                : ctx.cores()
                        .byId(id)
                        .map(com.partida.retroarch.CoreInfo::label)
                        .orElse(id);
    }

    /** What the search reads of a folder's row: its path, its core and its Remove (SET-03). */
    SettingsSearch.Entry folderEntry(Settings.Library.Folder folder) {
        return new SettingsSearch.Entry(
                AppInfo.tildeHome(folder.path) + " " + folder.path,
                "",
                coreLabel(folder.coreId) + " " + Messages.tr("settings.library.remove"),
                Page.LIBRARY.title() + " " + Messages.tr("settings.library.folders.heading"),
                "games roms folder directory core");
    }

    /** The best score a folder row has for the search, and narrows the list to the rows that match. */
    private int applyFolderSearch() {
        if (query.isBlank()) {
            folderFilter.setPredicate(f -> true);
            folderList.setPlaceholder(noFolders);
            return 0;
        }
        SettingsSearch.Query read = SettingsSearch.Query.of(query);
        int best = 0;
        java.util.Set<String> matched = new java.util.HashSet<>();
        for (Settings.Library.Folder folder : folderSource) {
            // Each folder's entry is made once, when the folders or the cores change, not per search (PERF-09).
            SettingsSearch.Entry entry =
                    folderEntries.computeIfAbsent(folder.path + "\t" + folder.coreId, k -> folderEntry(folder));
            int score = SettingsSearch.score(read, entry);
            if (score > 0) matched.add(folder.path);
            best = Math.max(best, score);
        }
        folderFilter.setPredicate(f -> matched.contains(f.path));
        folderList.setPlaceholder(folderSource.isEmpty() ? noFolders : noFolderMatch);
        return best;
    }

    static final double FOLDER_ROW_HEIGHT = 40;
    static final int FOLDER_ROWS_SHOWN = 6;

    /** A games-folder row: its path, the core it is given, and Remove, which asks first (CONF-01). */
    private final class FolderCell extends ListCell<Settings.Library.Folder> {
        private final Label path = new Label();
        private final ComboBox<String> core = new ComboBox<>();
        private final Button remove = new Button(Messages.tr("settings.library.remove"));
        private final javafx.scene.control.Tooltip pathTip = new javafx.scene.control.Tooltip();
        private final HBox row;
        private boolean updating;
        private @Nullable List<String> shownCores;
        private @Nullable String shownCore;

        FolderCell() {
            HBox.setHgrow(path, Priority.ALWAYS);
            path.setMaxWidth(Double.MAX_VALUE);
            path.setMinWidth(0);
            path.setTooltip(pathTip);
            core.setConverter(converter(SettingsWindow.this::coreLabel));
            core.valueProperty().addListener((obs, old, picked) -> {
                Settings.Library.Folder folder = getItem();
                if (updating || picked == null || folder == null || picked.equals(folder.coreId)) return;
                services.store().update(s -> {
                    Settings.Library.Folder f = s.library.folder(folder.path);
                    if (f != null) f.coreId = picked;
                });
            });
            remove.setOnAction(e -> {
                Settings.Library.Folder folder = getItem();
                if (folder != null) MainWindow.removeFolder(services, stage, folder.path);
            });
            row = UiZoom.hbox(8, path, core, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("library-folder-row");
        }

        @Override
        protected void updateItem(Settings.Library.@Nullable Folder folder, boolean empty) {
            super.updateItem(folder, empty);
            setText(null);
            if (empty || folder == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            String shown = AppInfo.tildeHome(folder.path);
            path.setText(shown);
            pathTip.setText(folder.path);
            updating = true;
            try {
                // The picker's list is built again only when the cores or this row's own core changed (PERF-09).
                if (shownCores != coreIds || !folder.coreId.equals(shownCore)) {
                    List<String> items = new ArrayList<>(coreIds);
                    if (!items.contains(folder.coreId)) items.add(folder.coreId);
                    core.getItems().setAll(items);
                    shownCores = coreIds;
                    shownCore = folder.coreId;
                }
                core.setValue(folder.coreId);
            } finally {
                updating = false;
            }
            core.setAccessibleText(Messages.tr("settings.library.core.name", shown));
            remove.setAccessibleText(Messages.tr("settings.library.remove.name", shown));
            setAccessibleText(shown);
            setGraphic(row);
        }
    }

    private void commitRetroArchFolder() {
        String text = retroarchFolder.getText().strip();
        String problem = MainWindow.retroArchFolderProblem(text);
        if (problem != null) {
            if (!retroarchFolder.getStyleClass().contains("invalid"))
                retroarchFolder.getStyleClass().add("invalid");
            services.echo("status.retroarchFolderInvalid");
            return;
        }
        retroarchFolder.getStyleClass().remove("invalid");
        if (!text.equals(services.settings().library.retroarchConfigDir)) {
            services.store().update(s -> s.library.retroarchConfigDir = text);
        }
    }

    private void commitRetroArchCommand() {
        String text = retroarchCommand.getText().strip();
        if (!text.equals(services.settings().library.retroarchCommand)) {
            services.store().update(s -> s.library.retroarchCommand = text);
        }
    }

    private void buildAppearance() {
        theme.getItems().setAll(ThemeChoice.values());
        theme.setConverter(converter(t -> Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT))));
        theme.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.theme, v -> services.store().update(s -> s.theme = v)));
        VBox card = card(Page.APPEARANCE, null);
        row(card, Page.APPEARANCE, "settings.theme", theme, "dark light mode colour color");
        // The UI zoom (WIN-12): every step the zoom commands take, applied as it is picked.
        for (int p = Settings.MIN_UI_ZOOM; p <= Settings.MAX_UI_ZOOM; p += UiZoom.STEP)
            uiZoom.getItems().add(p);
        uiZoom.setConverter(converter(p -> Messages.tr("settings.uiZoom.value", p)));
        uiZoom.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.uiZoom, v -> services.store().update(s -> s.uiZoom = v)));
        row(card, Page.APPEARANCE, "settings.uiZoom", uiZoom, "zoom scale size larger smaller bigger interface font");
        // The documents' text size (WIN-09), apart from the window's zoom.
        for (int p = Settings.MIN_TEXT_ZOOM; p <= Settings.MAX_TEXT_ZOOM; p += Commands.TEXT_ZOOM_STEP) {
            textZoom.getItems().add(p);
        }
        textZoom.setConverter(converter(p -> Messages.tr("settings.uiZoom.value", p)));
        textZoom.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.textZoom, v -> services.store().update(s -> s.textZoom = v)));
        row(card, Page.APPEARANCE, "settings.textZoom", textZoom, "text font size zoom document larger smaller");
        titleBar.setOnAction(e -> {
            services.store().update(s -> s.titleBarMenus = titleBar.isSelected());
            services.echo("status.titleBarRestart");
        });
        // OS-01: on every OS; where the system can't draw the menus there, disabled and saying why.
        titleBar.setDisable(noTitleBar != null);
        addRow(
                card,
                Page.APPEARANCE,
                Messages.tr("settings.titleBar.title"),
                Messages.tr("settings.titleBar.desc") + (noTitleBar == null ? "" : "\n" + noTitleBar),
                titleBar,
                "header bar window title menus");
        loaders.add(() -> {
            theme.setValue(services.settings().theme);
            uiZoom.setValue(services.settings().uiZoom);
            textZoom.setValue(services.settings().textZoom);
            titleBar.setSelected(services.settings().titleBarMenus);
        });
    }

    private void buildKeymap() {
        keymap.getItems().setAll(Keymap.PRESETS);
        keymap.setConverter(converter(id -> Keymap.load(id).name()));
        keymap.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.keymap, v -> services.store().update(s -> s.keymap = v)));
        VBox presets = card(Page.KEYMAP, null);
        row(presets, Page.KEYMAP, "settings.keymap", keymap, "emacs cua shortcuts keys");
        loaders.add(() -> keymap.setValue(services.settings().keymap));

        // One row per registered command: a list that grows with the registry, so a virtualized
        // ListView with a fixed cell size, filtered by the search (PERF-08).
        VBox bindings = card(Page.KEYMAP, Messages.tr("settings.bindings.heading"));
        bindingList.getStyleClass().add("binding-list");
        bindingList.setAccessibleText(Messages.tr("settings.bindings.heading"));
        UiZoom.bind(bindingList.fixedCellSizeProperty(), BINDING_ROW_HEIGHT); // follows the zoom (WIN-12)
        CommandMenu rowMenu =
                new CommandMenu(services, java.util.Arrays.asList("keymap.changeBinding", null, "keymap.resetBinding"));
        bindingList.setCellFactory(v -> CommandMenu.onRow(new BindingCell(), () -> rowMenu));
        RowContextMenu.install(bindingList);
        for (Command command : services.commands().all()) {
            bindingEntries.put(
                    command,
                    new SettingsSearch.Entry(
                            command.title(),
                            command.description(),
                            Messages.tr("settings.bindings.reset"),
                            Page.KEYMAP.title() + " " + Messages.tr("settings.bindings.heading"),
                            command.id()));
        }
        bindingSource.setAll(bindingEntries.keySet());
        bindingFilter.setPredicate(c -> true);
        bindingList.setItems(bindingFilter);
        bindingList
                .prefHeightProperty()
                .bind(javafx.beans.binding.Bindings.min(
                                javafx.beans.binding.Bindings.size(bindingFilter), BINDING_ROWS_SHOWN)
                        .multiply(UiZoom.times(BINDING_ROW_HEIGHT))
                        .add(2));
        bindings.getChildren().add(bindingList);
        loaders.add(bindingList::refresh);
    }

    static final double BINDING_ROW_HEIGHT = 58;
    static final int BINDING_ROWS_SHOWN = 8;

    /** A key-binding row: the command's title and description, its keys, and Reset. */
    private final class BindingCell extends ListCell<Command> {
        private final javafx.scene.control.Tooltip conflictTip = new javafx.scene.control.Tooltip();
        private final Label title = new Label();
        private final Label description = new Label();
        private final TextField field = new TextField();
        private final Button reset = new Button(Messages.tr("settings.bindings.reset"));
        private final HBox row;

        BindingCell() {
            title.getStyleClass().add("setting-title");
            description.getStyleClass().add("setting-desc");
            field.setPrefColumnCount(14);
            reset.getStyleClass().add("flat");
            reset.setOnAction(e -> {
                Command command = getItem();
                if (command != null) resetBinding(command);
            });
            field.setOnAction(e -> commit());
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) commit();
            });
            VBox text = UiZoom.vbox(2, title, description);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox control = UiZoom.hbox(6, field, reset);
            control.setAlignment(Pos.CENTER_RIGHT);
            row = UiZoom.hbox(16, text, control);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("setting-row");
        }

        private void commit() {
            Command command = getItem();
            if (command != null && !isEmpty()) commitBinding(command.id(), field);
        }

        @Override
        protected void updateItem(@Nullable Command command, boolean empty) {
            super.updateItem(command, empty);
            setText(null);
            if (empty || command == null) {
                setGraphic(null);
                return;
            }
            title.setText(command.title());
            description.setText(command.description());
            field.setAccessibleText(command.title());
            String user = services.settings().keybindings.get(command.id());
            field.setText(
                    user != null
                            ? user
                            : services.keybindings().keymap().bindings().getOrDefault(command.id(), ""));
            field.getStyleClass().remove("invalid");
            String other = services.keybindings().conflicts().get(command.id());
            if (other != null) {
                conflictTip.setText(Messages.tr("settings.bindings.conflict", Messages.tr("command." + other)));
            }
            field.setTooltip(other == null ? null : conflictTip); // one tooltip per cell, its text set (PERF-09)
            if (other != null) field.getStyleClass().add("invalid");
            setGraphic(row);
        }
    }

    /** Removes the user's own binding for a command, after asking: it is something the user made. */
    void resetBinding(Command command) {
        String user = services.settings().keybindings.get(command.id());
        if (user == null) return;
        String keymapChord = services.keybindings().keymap().bindings().getOrDefault(command.id(), "");
        if (!services.host().confirm(stage, Confirmations.resetBinding(command.title(), user, keymapChord))) return;
        services.store().update(s -> s.keybindings.remove(command.id()));
    }

    /** Commits a binding: blank unbinds, an unparsable chord is marked and not saved. */
    void commitBinding(String id, TextField field) {
        String text = field.getText().trim();
        if (!text.isEmpty() && KeyStroke.parseSequence(text) == null) {
            if (!field.getStyleClass().contains("invalid"))
                field.getStyleClass().add("invalid");
            return;
        }
        field.getStyleClass().remove("invalid");
        String base = services.keybindings().keymap().bindings().getOrDefault(id, "");
        String current = services.settings().keybindings.getOrDefault(id, base);
        if (text.equals(current)) return;
        services.store().update(s -> {
            if (text.equals(base)) s.keybindings.remove(id);
            else s.keybindings.put(id, text);
        });
    }

    private void buildSync() {
        Label subtitle = new Label(Messages.tr("settings.sync.subtitle"));
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("muted");
        page(Page.SYNC).getChildren().add(subtitle);

        VBox repo = card(Page.SYNC, null);
        syncUrl.setPromptText(Messages.tr("settings.sync.repo.prompt", AppInfo.ID));
        syncUrl.setOnAction(e -> commitUrl());
        syncUrl.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitUrl();
        });
        row(repo, Page.SYNC, "settings.sync.repo", syncUrl, "git github repository url");
        syncBranch.setPrefColumnCount(8);
        syncBranch.setAccessibleText(Messages.tr("settings.sync.branch.title")); // it shares its row (ACC-01)
        syncBranch.setOnAction(e -> commitBranch());
        syncBranch.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitBranch();
        });
        Button connect = new Button(Messages.tr("settings.sync.connect"));
        connect.getStyleClass().add("accent");
        connect.setOnAction(e -> {
            commitUrl();
            if (!syncUrl.getStyleClass().contains("invalid")) services.sync().syncNow(true);
        });
        Button create = new Button(Messages.tr("settings.sync.createGithub"));
        create.setOnAction(e -> services.commands().run("sync.createGitHubRepo"));
        HBox branchControl = UiZoom.hbox(6, syncBranch, connect, create);
        branchControl.setAlignment(Pos.CENTER_RIGHT);
        row(repo, Page.SYNC, "settings.sync.branch", branchControl, "branch connect");

        Button now = new Button(Messages.tr("settings.sync.now"));
        now.setOnAction(e -> services.commands().run("sync.now"));
        Button folder = new Button(Messages.tr("settings.sync.openFolder"));
        folder.setOnAction(e -> services.commands().run("sync.openFolder"));
        syncPill.getStyleClass().add("pill");
        HBox status = UiZoom.hbox(8, syncPill, now, folder);
        status.setAlignment(Pos.CENTER_RIGHT);
        row(repo, Page.SYNC, "settings.sync.status", status, "status");

        VBox automatic = card(Page.SYNC, null);
        syncAuto.setOnAction(e -> services.store().update(s -> s.sync.auto = syncAuto.isSelected()));
        syncInterval.setPrefColumnCount(3);
        syncInterval.setAccessibleText(Messages.tr("settings.sync.interval.prompt"));
        syncInterval.setOnAction(e -> commitInterval());
        syncInterval.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitInterval();
        });
        HBox autoControl = UiZoom.hbox(6, syncInterval, new Label(Messages.tr("settings.sync.minutes")), syncAuto);
        autoControl.setAlignment(Pos.CENTER_RIGHT);
        row(automatic, Page.SYNC, "settings.sync.auto", autoControl, "interval automatic");
        Label notes = new Label(Messages.tr("settings.sync.notes"));
        notes.setWrapText(true);
        notes.getStyleClass().add("note");
        addRow(automatic, Page.SYNC, "", Messages.tr("settings.sync.notes"), notes, "private conflict");

        loaders.add(() -> {
            Settings.Sync s = services.settings().sync;
            syncUrl.setText(s.repoUrl);
            syncUrl.getStyleClass().remove("invalid");
            syncBranch.setText(s.branch);
            syncBranch.getStyleClass().remove("invalid");
            syncAuto.setSelected(s.auto);
            syncInterval.setText(String.valueOf(s.intervalMinutes));
            syncInterval.getStyleClass().remove("invalid");
            loadSyncStatus();
        });
    }

    private void commitUrl() {
        String url = syncUrl.getText().trim();
        String refused = url.isEmpty() ? null : SyncEngine.checkUrl(url);
        if (refused != null) {
            if (!syncUrl.getStyleClass().contains("invalid"))
                syncUrl.getStyleClass().add("invalid");
            services.echo("status.sync.invalidUrl", Messages.tr("settings.sync.url." + refused));
            return;
        }
        syncUrl.getStyleClass().remove("invalid");
        if (!url.equals(services.settings().sync.repoUrl)) services.store().update(s -> s.sync.repoUrl = url);
    }

    /**
     * Asks for a repository name (suggesting {@code <app>-sync}), then creates it on GitHub through the
     * user's gh and connects to it. Not modal-blocking: the answer arrives when the dialog closes.
     */
    TextInputDialog promptCreateOnGitHub() {
        TextInputDialog dialog = new TextInputDialog(SyncController.suggestedRepoName());
        dialog.setTitle(Messages.tr("settings.sync.createGithub.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.tr("settings.sync.createGithub.prompt"));
        dialog.getEditor().setAccessibleText(Messages.tr("settings.sync.createGithub.prompt")); // ACC-01
        Dialogs.styled(dialog, stage);
        dialog.setOnHidden(e -> {
            String name = dialog.getResult();
            if (name != null && !name.isBlank()) services.sync().createOnGitHub(name);
        });
        dialog.show();
        return dialog;
    }

    /** Commits the branch: a blank or invalid name is marked, said why, and never committed (SET-04). */
    void commitBranch() {
        String branch = syncBranch.getText().trim();
        String refused = SyncEngine.checkBranch(branch);
        if (refused != null) {
            if (!syncBranch.getStyleClass().contains("invalid"))
                syncBranch.getStyleClass().add("invalid");
            services.echo("status.sync.invalidBranch", Messages.tr("settings.sync.branch." + refused));
            return;
        }
        syncBranch.getStyleClass().remove("invalid");
        if (!branch.equals(services.settings().sync.branch)) services.store().update(s -> s.sync.branch = branch);
    }

    void commitInterval() {
        Integer minutes = Settings.Sync.parseInterval(syncInterval.getText());
        if (minutes == null) {
            if (!syncInterval.getStyleClass().contains("invalid"))
                syncInterval.getStyleClass().add("invalid");
            return;
        }
        syncInterval.getStyleClass().remove("invalid");
        if (minutes != services.settings().sync.intervalMinutes) {
            services.store().update(s -> s.sync.intervalMinutes = minutes);
        }
    }

    private void loadSyncStatus() {
        syncPill.setText(services.sync().pillText(System.currentTimeMillis()));
        syncPill.setAccessibleText(Messages.tr("settings.sync.pill.accessible", syncPill.getText()));
        syncPill.getStyleClass().removeAll("green", "amber", "red");
        syncPill.getStyleClass()
                .add(
                        switch (services.sync().pill()) {
                            // Green only once a sync is verified; one in flight is not durable yet (ICON-18).
                            case CONNECTED -> "green";
                            case SYNCING, NOT_CONNECTED -> "amber";
                            case FAILING -> "red";
                        });
    }

    private void buildAdvanced() {
        VBox card = card(Page.ADVANCED, null);
        Label folder = new Label(AppInfo.tildeHome(services.store().dir().toString()));
        folder.getStyleClass().add("muted");
        Button open = new Button(Messages.tr("settings.config.open"));
        open.setOnAction(e -> services.commands().run("config.openFolder"));
        HBox folderControl = UiZoom.hbox(8, folder, open);
        folderControl.setAlignment(Pos.CENTER_RIGHT);
        row(card, Page.ADVANCED, "settings.config", folderControl, "folder directory path");
        Button export = new Button(Messages.tr("command.config.export"));
        export.setOnAction(e -> services.commands().run("config.export"));
        Button importButton = new Button(Messages.tr("command.config.import"));
        importButton.setOnAction(e -> services.commands().run("config.import"));
        HBox backup = UiZoom.hbox(8, export, importButton);
        backup.setAlignment(Pos.CENTER_RIGHT);
        row(card, Page.ADVANCED, "settings.backup", backup, "export import zip backup");
        // The Doctor (DIAG-07): the tools and the folder the app depends on, each checked.
        Button doctor = new Button(Messages.tr("command.help.doctor"));
        doctor.setOnAction(e -> services.commands().run("help.doctor"));
        row(card, Page.ADVANCED, "settings.doctor", doctor, "doctor check tools git gh java environment problem");
    }

    // ---- rows, cards, search ----

    private VBox page(Page page) {
        return java.util.Objects.requireNonNull(pages.get(page));
    }

    private VBox card(Page page, @Nullable String heading) {
        VBox card = new VBox();
        card.getStyleClass().add("settings-card");
        if (heading != null) {
            Label label = new Label(heading);
            label.getStyleClass().add("settings-card-heading");
            card.getChildren().add(label);
        }
        page(page).getChildren().add(card);
        return card;
    }

    private void row(VBox card, Page page, String key, Node control, String keywords) {
        addRow(card, page, Messages.tr(key + ".title"), Messages.tr(key + ".desc"), control, keywords);
    }

    private void addRow(VBox card, Page page, String title, String description, Node control, String keywords) {
        VBox text = UiZoom.vbox(2);
        if (!title.isEmpty()) {
            Label label = new Label(title);
            label.getStyleClass().add("setting-title");
            label.setWrapText(true);
            // The row's title names its control for a screen reader (ACC-01).
            if (control instanceof javafx.scene.control.Control named && !(control instanceof Label)) {
                label.setLabelFor(named);
            }
            text.getChildren().add(label);
        }
        if (!description.isEmpty() && !(control instanceof Label)) {
            Label desc = new Label(description);
            desc.getStyleClass().add("setting-desc");
            desc.setWrapText(true);
            text.getChildren().add(desc);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = UiZoom.hbox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        if (control instanceof Label) {
            row.getChildren().setAll(control);
            HBox.setHgrow(control, Priority.ALWAYS);
        }
        card.getChildren().add(row);
        String heading = card.getChildren().isEmpty()
                        || !(card.getChildren().getFirst() instanceof Label first)
                        || !first.getStyleClass().contains("settings-card-heading")
                ? ""
                : first.getText();
        rows.add(new Row(
                page,
                row,
                card,
                new SettingsSearch.Entry(
                        title, description, visibleText(control), page.title() + " " + heading, keywords)));
    }

    /** The text a row's controls show (button and label text), so a search finds a row by it. */
    static String visibleText(Node control) {
        StringBuilder text = new StringBuilder();
        if (control instanceof javafx.scene.control.Labeled labeled && labeled.getText() != null) {
            text.append(labeled.getText()).append(' ');
        }
        if (control instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) text.append(visibleText(child));
        }
        return text.toString();
    }

    /** Filters every page by {@code query} and opens the page with the best match. */
    void applySearch(String query) {
        Page best = null;
        int bestScore = 0;
        SettingsSearch.Query read = SettingsSearch.Query.of(query); // once for every row (PERF-09)
        for (Page page : PAGES) pageHits.put(page, false);
        for (Row row : rows) {
            int score = SettingsSearch.score(read, row.entry());
            boolean show = score > 0;
            row.node().setVisible(show);
            row.node().setManaged(show);
            if (show) pageHits.put(row.page(), true);
            if (score > bestScore) {
                bestScore = score;
                best = row.page();
            }
        }
        // The key bindings are a filtered list rather than rows: the search narrows the list.
        java.util.Set<Command> matched = new java.util.HashSet<>();
        for (Map.Entry<Command, SettingsSearch.Entry> binding : bindingEntries.entrySet()) {
            int score = SettingsSearch.score(read, binding.getValue());
            if (score > 0) {
                matched.add(binding.getKey());
                pageHits.put(Page.KEYMAP, true);
            }
            if (score > bestScore) {
                bestScore = score;
                best = Page.KEYMAP;
            }
        }
        bindingFilter.setPredicate(matched::contains);
        // So are the games folders: a search finds a folder by its path or its core.
        this.query = query;
        int folderScore = applyFolderSearch();
        if (folderScore > 0) pageHits.put(Page.LIBRARY, true);
        if (folderScore > bestScore) {
            bestScore = folderScore;
            best = Page.LIBRARY;
        }
        for (VBox page : pages.values()) {
            for (Node child : page.getChildren()) {
                if (child instanceof VBox card && card.getStyleClass().contains("settings-card")) {
                    boolean any = card.getChildren().stream()
                            .anyMatch(n -> (n.getStyleClass().contains("setting-row") && n.isVisible())
                                    || (n == bindingList && !bindingFilter.isEmpty())
                                    || (n == folderList && !folderFilter.isEmpty()));
                    card.setVisible(any);
                    card.setManaged(any);
                }
            }
        }
        // Each shown row marks itself again; no cell is rebuilt, so no glyph is made again (PERF-09).
        pageCells.forEach(PageCell::markMatch);
        if (best != null && !query.isBlank()) sidebar.getSelectionModel().select(best);
    }

    /** Sets every control from the settings. Safe to run at any time. */
    void load() {
        loaders.forEach(Runnable::run);
    }

    /** Commits a picked value; load() setting the value already stored must not write the file again. */
    private <T> void commitIfChanged(
            @Nullable T value, java.util.function.Function<Settings, T> current, Consumer<T> commit) {
        if (value != null && !value.equals(current.apply(services.settings()))) commit.accept(value);
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(@Nullable T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public @Nullable T fromString(String string) {
                return null;
            }
        };
    }

    // ---- accessors for tests ----

    TextField retroarchFolder() {
        return retroarchFolder;
    }

    TextField retroarchCommand() {
        return retroarchCommand;
    }

    Label retroarchStatus() {
        return retroarchStatus;
    }

    SettingSwitch recentlyPlayed() {
        return recentlyPlayed;
    }

    ListView<Settings.Library.Folder> folderList() {
        return folderList;
    }

    Stage stage() {
        return stage;
    }

    TextField search() {
        return search;
    }

    ListView<Page> sidebar() {
        return sidebar;
    }

    ListView<Command> bindingList() {
        return bindingList;
    }

    List<Row> rows() {
        return rows;
    }

    Map<Page, Boolean> pageHits() {
        return pageHits;
    }

    ComboBox<ThemeChoice> theme() {
        return theme;
    }

    ComboBox<String> language() {
        return language;
    }

    ComboBox<String> keymap() {
        return keymap;
    }

    SettingSwitch updatesSwitch() {
        return updates;
    }

    SettingSwitch titleBarSwitch() {
        return titleBar;
    }

    ComboBox<Integer> uiZoomBox() {
        return uiZoom;
    }

    SettingSwitch simpleModeSwitch() {
        return simpleMode;
    }

    SettingSwitch toolbarSwitch() {
        return toolbar;
    }

    SettingSwitch tabBarSwitch() {
        return tabBar;
    }

    SettingSwitch statusBarSwitch() {
        return statusBar;
    }

    TextField syncUrl() {
        return syncUrl;
    }

    TextField syncBranch() {
        return syncBranch;
    }

    TextField syncInterval() {
        return syncInterval;
    }

    SettingSwitch syncAuto() {
        return syncAuto;
    }

    Label syncPill() {
        return syncPill;
    }

    /** The pages, once: a search runs over them on every pause in typing (PERF-09). */
    private static final List<Page> PAGES = List.of(Page.values());

    /** The sidebar's cells, so a search restyles them in place. */
    private final List<PageCell> pageCells = new ArrayList<>();

    /** A sidebar row: the page's icon and name, greyed when a search finds nothing on it. */
    private final class PageCell extends ListCell<Page> {
        /** Each page's glyph, made the first time this cell shows the page and reused after (PERF-09). */
        private final Map<Page, Node> glyphs = new EnumMap<>(Page.class);

        PageCell() {
            pageCells.add(this);
        }

        /** Greys the row when a search finds nothing on its page. */
        void markMatch() {
            getStyleClass().remove("no-match");
            Page page = getItem();
            if (page != null && !isEmpty() && !search.getText().isBlank() && !pageHits.getOrDefault(page, true))
                getStyleClass().add("no-match");
        }

        @Override
        protected void updateItem(@Nullable Page page, boolean empty) {
            super.updateItem(page, empty);
            getStyleClass().remove("no-match");
            if (empty || page == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(page.title());
            setGraphic(glyphs.computeIfAbsent(page, p -> Icons.commandGlyph(p.command)));
            if (!search.getText().isBlank() && !pageHits.getOrDefault(page, true))
                getStyleClass().add("no-match");
        }
    }
}
