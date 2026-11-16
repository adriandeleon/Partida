package com.partida.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import com.partida.AppInfo;
import com.partida.config.Settings;
import com.partida.library.Game;
import com.partida.library.GameCollection;
import org.jspecify.annotations.Nullable;

/**
 * The game browser (decision 3): the window's content, one system's games at a time ({@link SystemView}), the
 * system chosen in the Systems tool window on its left, the selected game's details on its right. There are no
 * tabs: choosing another system shows it in the same place. Before any system is chosen, or when the library
 * has none, the browser shows its own empty state: the app's ways in, each with its chord (WELC-02).
 *
 * <p>The views of the last few systems shown are kept, each with its filter and its selected game, so going
 * back to one is instant; the library's reloads hand each kept view its system's games again.
 */
final class Browser {

    /** How many systems' views are kept for going back to; older ones are built again when shown. */
    static final int KEPT_VIEWS = 8;

    private final AppServices services;
    private final LibraryService library;
    private final StackPane node = new StackPane();
    private final Node empty;
    /** The systems' views, the one shown last at the end. */
    private final LinkedHashMap<String, SystemView> views = new LinkedHashMap<>(16, 0.75f, true);

    private @Nullable SystemView active;

    /** Each game row's right-click menu (MENU-05), one for every system. */
    private final CommandMenu gameMenu;
    /** A ROM's right-click menu in a game's details. */
    private final CommandMenu romMenu;
    /** The box art every system's grid and details draw, shared so each image is read once. */
    private final BoxArt boxArt;

    private final Consumer<String> setTitle;
    private final List<Runnable> activeListeners = new ArrayList<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private boolean showHiddenRoms;
    private int textZoom = Settings.DEFAULT_TEXT_ZOOM;

    /**
     * With {@code restore}, the system shown at the last quit is shown again (WIN-08); without (a first run, or
     * {@code --no-session}: CLI-04), the empty state.
     */
    Browser(AppServices services, LibraryService library, Node empty, Consumer<String> setTitle, boolean restore) {
        this.services = services;
        this.library = library;
        this.empty = empty;
        this.setTitle = setTitle;
        this.showHiddenRoms = services.settings().library.showHiddenRoms;
        this.gameMenu = new CommandMenu(services, java.util.Arrays.asList("game.play", null, "find.show"));
        this.romMenu = new CommandMenu(services, java.util.Arrays.asList("library.playRom"));
        this.boxArt = new BoxArt(() -> library.state().retroArch());
        node.getStyleClass().add("browser");
        node.setMinSize(0, 0); // it never holds a tool window's divider back
        node.getChildren().setAll(empty);
        // A reload refreshes every kept view once per pulse, however many changes it brings (PERF-06).
        Redraw games = new Redraw(this::refreshGames);
        library.onChanged(games::request);
        // The grid or the list, and hidden ROMs shown or not, follow the settings; the ROM Play starts follows
        // each game started (decision 3).
        services.store().addListener(s -> applyBrowseSettings());
        library.playingProperty().addListener((o, was, now) -> views.values().forEach(SystemView::showDetails));
        // The filter boxes' key badges follow the keymap (FIND-13).
        services.onKeybindingsChanged(() -> views.values().forEach(SystemView::refreshBadge));

        String last = services.settings().window.activeDocument;
        if (restore && last != null && !last.isBlank()) show(last); // its games arrive with the library's load
        updateWindowTitle();
    }

    /** What the window shows: the system's games, or the empty state. */
    StackPane node() {
        return node;
    }

    /** The empty state's content: the app's ways in (WELC-02). */
    Node empty() {
        return empty;
    }

    /** Shows {@code collection}'s games ({@code systems.open}) and gives them the keyboard. */
    SystemView open(GameCollection collection) {
        SystemView view = show(collection.name());
        view.focusGames();
        return view;
    }

    /** Shows the system {@code name}; the keyboard stays where it is, so the Systems list can be walked. */
    SystemView show(String name) {
        SystemView view = views.get(name);
        if (view == null) view = build(name);
        if (view != active) {
            active = view;
            node.getChildren().setAll(view.node());
            updateWindowTitle();
            activeListeners.forEach(Runnable::run);
            selectionListeners.forEach(Runnable::run);
        }
        return view;
    }

    /** Shows the empty state: no system is shown. */
    void showEmpty() {
        if (active == null) return;
        active = null;
        node.getChildren().setAll(empty);
        updateWindowTitle();
        activeListeners.forEach(Runnable::run);
        selectionListeners.forEach(Runnable::run);
    }

    private SystemView build(String name) {
        GameCollection collection = library.state().collection(name);
        SystemView view = new SystemView(
                name,
                collection == null ? List.of() : collection.games(),
                -1,
                new SystemView.Hooks(
                        () -> services.commands().run("game.play"),
                        () -> services.commands().run("library.playRom"),
                        library::romToPlay,
                        library::regions,
                        () -> services.settings().library.showHiddenRoms,
                        boxArt,
                        () -> library.state().retroArch(),
                        () -> gameMenu,
                        () -> romMenu));
        view.setGrid(services.settings().library.gridSystems.contains(name));
        TextInputKeys.install(
                view.filter(), () -> services.keybindings().keymap().emacs());
        view.connect(() -> services.keybindings().label("library.filter"), this::runByTitle);
        applyTextZoom(view);
        view.list().getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (view == active) selectionListeners.forEach(Runnable::run);
        });
        views.put(name, view);
        trim();
        return view;
    }

    /** Drops the views shown longest ago past {@link #KEPT_VIEWS}; never the one shown now. */
    private void trim() {
        var oldest = views.entrySet().iterator();
        while (views.size() > KEPT_VIEWS && oldest.hasNext()) {
            if (oldest.next().getValue() != active) oldest.remove();
        }
    }

    /** The library loaded again: every kept view gets its system's games. */
    private void refreshGames() {
        boxArt.clear();
        for (Map.Entry<String, SystemView> e : views.entrySet()) {
            GameCollection collection = library.state().collection(e.getKey());
            e.getValue().setGames(collection == null ? List.of() : collection.games());
        }
        selectionListeners.forEach(Runnable::run);
    }

    /** Each view's grid or list, and its hidden games, as the settings have them now. */
    private void applyBrowseSettings() {
        Settings.Library settings = services.settings().library;
        if (settings.showHiddenRoms != showHiddenRoms) {
            showHiddenRoms = settings.showHiddenRoms;
            views.values().forEach(SystemView::applyFilter);
        }
        for (SystemView view : views.values()) {
            boolean grid = settings.gridSystems.contains(view.title());
            if (grid != view.isGrid()) view.setGrid(grid);
        }
    }

    /** The game lists' text size, in percent (WIN-09): relative to the window's own, so it follows the UI zoom. */
    void textZoom(int percent) {
        textZoom = percent;
        for (SystemView view : views.values()) applyTextZoom(view);
    }

    private void applyTextZoom(SystemView view) {
        String style = textZoom == Settings.DEFAULT_TEXT_ZOOM ? "" : "-fx-font-size: " + (textZoom / 100.0) + "em;";
        view.list().setStyle(style);
        view.grid().setStyle(style);
        view.details().roms().setStyle(style);
    }

    /** Runs {@code listener} whenever the system shown changes. */
    void onActiveChanged(Runnable listener) {
        activeListeners.add(listener);
    }

    /** Runs {@code listener} whenever the shown system's selected game changes. */
    void onSelectedGameChanged(Runnable listener) {
        selectionListeners.add(listener);
    }

    /** The system shown, or null on the empty state. */
    @Nullable
    SystemView activeView() {
        return active;
    }

    /** The shown system's name, or null: what the next launch shows again (WIN-08). */
    @Nullable
    String activePath() {
        SystemView view = active;
        return view == null ? null : view.title();
    }

    /** The ROM Play starts for the game selected in the shown system, or null. */
    @Nullable
    Game selectedGame() {
        SystemView view = active;
        return view == null ? null : view.selected();
    }

    /** The ROM selected in the shown game's details, or null: what Play This ROM starts. */
    @Nullable
    Game selectedRom() {
        SystemView view = active;
        return view == null ? null : view.selectedRom();
    }

    CommandMenu gameMenu() {
        return gameMenu;
    }

    /**
     * A filter box's {@code >} query: runs the command whose title it names (FIND-13), or says there is none.
     * True when a command ran.
     */
    boolean runByTitle(String name) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (com.partida.command.Command command : services.commands().all()) titles.put(command.id(), command.title());
        java.util.Optional<String> id = GameFilter.commandFor(name, titles);
        if (id.isEmpty()) {
            services.echo("systems.filter.noCommand", name);
            return false;
        }
        AppMenus.runOrSay(services, id.get());
        return true;
    }

    /** The window title: the system shown and the app (TABS-10). */
    void updateWindowTitle() {
        SystemView view = active;
        setTitle.accept(windowTitle(view == null ? null : view.shownName()));
    }

    /**
     * The window title for a system (or none). An app with no files or projects names what the window shows,
     * then the app (TABS-10). Pure.
     */
    static String windowTitle(@Nullable String system) {
        return system == null ? AppInfo.NAME : system + " — " + AppInfo.NAME;
    }
}
