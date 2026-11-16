package com.partida.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ContextMenuEvent;

import com.partida.AppInfo;
import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameCollection;
import org.jspecify.annotations.Nullable;

/**
 * The system tabs (Pauta's Tabs and tool panels, Tabs): a strip of the library's systems, each tab a system's
 * games ({@link SystemView}), with a pinned group at its front, after Editora's {@code EditorArea} and the tab
 * half of its {@code MainController}. Every operation is a command ({@link Commands}, TABS-01) that acts on the
 * selected tab; the decisions are {@link TabStrip}'s, the headers {@link TabHeaders}', the closed list
 * {@link ClosedTabs}'.
 *
 * <p>A system tab holds nothing the user typed: closing one loses nothing and needs no question, and none is
 * ever unsaved. A tab is the system's name; the library's reloads hand it that system's games again, and a tab
 * restored from the last session fills in when the library first loads.
 */
final class DocumentTabs {

    private final AppServices services;
    private final TabPane tabPane = new TabPane();
    private final Map<Tab, SystemView> views = new IdentityHashMap<>();
    private final Set<Tab> pinned = Collections.newSetFromMap(new IdentityHashMap<>());

    private final CommandMenu welcomeMenu;
    private final javafx.scene.layout.StackPane node = new javafx.scene.layout.StackPane();
    private final javafx.scene.control.Label emptyHint = new javafx.scene.control.Label();
    private final List<Runnable> welcomeShown = new ArrayList<>();

    /** Each game row's right-click menu (MENU-05), one for every tab. */
    private final CommandMenu gameMenu;
    /** A ROM's right-click menu in a game's details. */
    private final CommandMenu romMenu;
    /** The box art every system's grid and details draw, shared so each image is read once. */
    private final BoxArt boxArt;

    private final ClosedTabs closedTabs = new ClosedTabs();
    private final TabHeaders headers;
    private final CommandMenu menu;
    private final Tab welcome;
    private final Consumer<String> setTitle;
    private final LibraryService library;
    private final List<Runnable> activeListeners = new ArrayList<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();

    DocumentTabs(AppServices services, LibraryService library, Node welcomeContent, Consumer<String> setTitle) {
        this(services, library, welcomeContent, setTitle, false);
    }

    /**
     * With {@code restore}, the systems open at the last quit open again, the one that was selected selected
     * (WIN-08); without (a first run, or {@code --no-session}: CLI-04), the Welcome page (WELC-03).
     */
    DocumentTabs(
            AppServices services,
            LibraryService library,
            Node welcomeContent,
            Consumer<String> setTitle,
            boolean restore) {
        this.services = services;
        this.library = library;
        this.showHiddenRoms = services.settings().library.showHiddenRoms;
        this.setTitle = setTitle;
        this.headers = new TabHeaders(pinned::contains, this::moveBeside);
        this.menu = new CommandMenu(services, TabContextMenu.COMMANDS);
        this.gameMenu = new CommandMenu(services, java.util.Arrays.asList("game.play", null, "find.show"));
        this.romMenu = new CommandMenu(services, java.util.Arrays.asList("library.playRom"));
        this.boxArt = new BoxArt(() -> library.state().retroArch());
        tabPane.getStyleClass().add("document-tabs");
        tabPane.setMinSize(0, 0); // the strip scrolls its headers; it never holds a tool window's divider back
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.FIXED); // the headers drag, with Pauta's feedback

        welcome = new Tab();
        welcome.setContent(welcomeContent);
        install(welcome);
        // The Welcome tab is not a system (WELC-11): not pinned, and its menu is Close alone.
        this.welcomeMenu = new CommandMenu(services, java.util.Arrays.asList("buffer.close"));

        // The empty content area's own hint (WELC-04): when the last tab closes, the ways in and their keys.
        emptyHint.getStyleClass().add("empty-hint");
        emptyHint.setFocusTraversable(true);
        emptyHint.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        emptyHint.setVisible(false);
        node.getChildren().setAll(tabPane, emptyHint);
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> showEmptyHint());
        services.onKeybindingsChanged(this::refreshEmptyHint);

        tabPane.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            updateWindowTitle();
            activeListeners.forEach(Runnable::run);
            if (now == welcome) welcomeShown.forEach(Runnable::run); // rebuilt on every show (WELC-05)
        });
        // The filter boxes' key badges follow the keymap (FIND-13).
        services.onKeybindingsChanged(() -> views.values().forEach(SystemView::refreshBadge));
        // A reload refreshes every tab's list once per pulse, however many changes it brings (PERF-06).
        Redraw games = new Redraw(this::refreshGames);
        library.onChanged(games::request);
        // The grid or the list, and hidden ROMs shown or not, follow the settings; the ROM Play starts follows
        // each game started (decision 3).
        services.store().addListener(s -> applyBrowseSettings());
        library.playingProperty().addListener((o, was, now) -> views.values().forEach(SystemView::showDetails));

        java.util.List<String> last = services.settings().window.documents;
        if (restore && last != null && !last.isEmpty()) {
            // WIN-08: the last session's systems, in their order, the selected one selected. Their games arrive
            // with the library's first load.
            String active = services.settings().window.activeDocument;
            Tab selected = null;
            for (String name : last) {
                Tab tab = open(name, -1);
                if (name.equals(active)) selected = tab;
            }
            if (selected != null) tabPane.getSelectionModel().select(selected);
            else showWelcome();
        } else {
            // No session to restore: the Welcome page (WELC-03); --no-session opens nothing of its own (CLI-04).
            tabPane.getTabs().add(welcome);
            render(welcome);
            tabPane.getSelectionModel().select(welcome);
        }
        updateWindowTitle();
        showEmptyHint();
    }

    TabPane tabPane() {
        return tabPane;
    }

    /** What the window shows: the tabs, and over them the empty area's hint when none is open (WELC-04). */
    javafx.scene.layout.StackPane node() {
        return node;
    }

    javafx.scene.control.Label emptyHint() {
        return emptyHint;
    }

    /** Each view's grid or list, and its hidden games, as the settings have them now. */
    private void applyBrowseSettings() {
        com.partida.config.Settings.Library settings = services.settings().library;
        if (settings.showHiddenRoms != showHiddenRoms) {
            showHiddenRoms = settings.showHiddenRoms;
            views.values().forEach(SystemView::applyFilter);
            views.keySet().forEach(this::render); // the header's count
        }
        for (SystemView view : views.values()) {
            boolean grid = settings.gridSystems.contains(view.title());
            if (grid != view.isGrid()) view.setGrid(grid);
        }
    }

    private boolean showHiddenRoms;

    /** The systems open now, in tab order, by name: what the next launch reopens (WIN-08). */
    List<String> openPaths() {
        List<String> names = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            SystemView view = views.get(tab);
            if (view != null) names.add(view.title());
        }
        return names;
    }

    /** The selected system's name, or null on the Welcome page. */
    @Nullable
    String activePath() {
        SystemView view = activeView();
        return view == null ? null : view.title();
    }

    private void showEmptyHint() {
        boolean empty = tabPane.getTabs().isEmpty();
        if (empty) refreshEmptyHint();
        emptyHint.setVisible(empty);
        // It takes the keyboard, so the window's chords still reach it (WELC-04).
        if (empty) javafx.application.Platform.runLater(emptyHint::requestFocus);
    }

    /** The game lists' text size, in percent (WIN-09): relative to the window's own, so it follows the UI zoom. */
    void textZoom(int percent) {
        textZoom = percent;
        for (SystemView view : views.values()) applyTextZoom(view);
    }

    private int textZoom = com.partida.config.Settings.DEFAULT_TEXT_ZOOM;

    private void applyTextZoom(SystemView view) {
        String style = textZoom == com.partida.config.Settings.DEFAULT_TEXT_ZOOM
                ? ""
                : "-fx-font-size: " + (textZoom / 100.0) + "em;";
        view.list().setStyle(style);
        view.grid().setStyle(style);
        view.details().roms().setStyle(style);
    }

    /** The ways in, each with its chord in the active keymap (WELC-04). */
    private void refreshEmptyHint() {
        StringBuilder text = new StringBuilder(Messages.tr("empty.title"));
        for (String id : List.of("palette.show", "tool.systems", "switcher.show", "view.welcome")) {
            String chord = services.keybindings().label(id);
            text.append("\n").append(Messages.tr("command." + id));
            if (!chord.isEmpty()) text.append("   ").append(chord);
        }
        emptyHint.setText(text.toString());
    }

    /** Runs {@code listener} each time the Welcome page is shown, so it is rebuilt current (WELC-05). */
    void onWelcomeShown(Runnable listener) {
        welcomeShown.add(listener);
    }

    List<Tab> tabs() {
        return List.copyOf(tabPane.getTabs());
    }

    Tab welcome() {
        return welcome;
    }

    ClosedTabs closedTabs() {
        return closedTabs;
    }

    CommandMenu menu() {
        return menu;
    }

    CommandMenu gameMenu() {
        return gameMenu;
    }

    TabHeaders headers() {
        return headers;
    }

    /** Runs {@code listener} whenever the active tab changes: what a panel follows (TABS-23). */
    void onActiveChanged(Runnable listener) {
        activeListeners.add(listener);
    }

    /** Runs {@code listener} whenever the active tab's selected game changes: what the Game window follows. */
    void onSelectedGameChanged(Runnable listener) {
        selectionListeners.add(listener);
    }

    @Nullable
    Tab selected() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    /** The active tab's system, or null on a tab that holds none. */
    @Nullable
    SystemView activeView() {
        Tab tab = selected();
        return tab == null ? null : views.get(tab);
    }

    /** The ROM Play starts for the game selected in the active tab, or null. */
    @Nullable
    Game selectedGame() {
        SystemView view = activeView();
        return view == null ? null : view.selected();
    }

    /** The ROM selected in the active tab's details, or null: what Play This ROM starts. */
    @Nullable
    Game selectedRom() {
        SystemView view = activeView();
        return view == null ? null : view.selectedRom();
    }

    @Nullable
    SystemView view(Tab tab) {
        return views.get(tab);
    }

    boolean isPinned(Tab tab) {
        return pinned.contains(tab);
    }

    /** A system's tab is never unsaved: it holds nothing the user typed. */
    boolean isDirty(Tab tab) {
        return false;
    }

    /** The tab showing the system {@code name}, or null. */
    @Nullable
    Tab tabFor(String name) {
        for (Map.Entry<Tab, SystemView> e : views.entrySet()) {
            if (e.getValue().title().equals(name)) return e.getKey();
        }
        return null;
    }

    /** Opens {@code collection} in a tab and selects it; an open one is selected. */
    Tab open(GameCollection collection) {
        return open(collection.name(), -1);
    }

    /** Opens the system {@code name}, with row {@code selectedRow} selected (-1: the first). */
    private Tab open(String name, int selectedRow) {
        Tab existing = tabFor(name);
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return existing;
        }
        closedTabs.forget(name);
        GameCollection collection = library.state().collection(name);
        SystemView view = new SystemView(
                name,
                collection == null ? List.of() : collection.games(),
                selectedRow,
                new SystemView.Hooks(
                        () -> services.commands().run("game.play"),
                        () -> services.commands().run("library.playRom"),
                        library::romToPlay,
                        library::regions,
                        () -> services.settings().library.showHiddenRoms,
                        boxArt,
                        () -> gameMenu,
                        () -> romMenu));
        view.setGrid(services.settings().library.gridSystems.contains(name));
        Tab tab = new Tab();
        tab.setContent(view.node());
        TextInputKeys.install(
                view.filter(), () -> services.keybindings().keymap().emacs());
        view.connect(() -> services.keybindings().label("library.filter"), this::runByTitle);
        applyTextZoom(view);
        views.put(tab, view);
        install(tab);
        view.list().getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (tab == selected()) selectionListeners.forEach(Runnable::run);
        });
        tabPane.getTabs().add(tab);
        render(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    /** The library loaded again: every open tab gets its system's games, and its header its count. */
    private void refreshGames() {
        boxArt.clear();
        for (Map.Entry<Tab, SystemView> e : views.entrySet()) {
            GameCollection collection = library.state().collection(e.getValue().title());
            e.getValue().setGames(collection == null ? List.of() : collection.games());
            render(e.getKey());
        }
        selectionListeners.forEach(Runnable::run);
    }

    /** Selects the Welcome tab, bringing it back when it was closed; it is rebuilt each time (WELC-05). */
    void showWelcome() {
        if (!tabPane.getTabs().contains(welcome)) {
            tabPane.getTabs().add(TabStrip.moveToPinBoundary(tabs(), welcome, pinned::contains), welcome);
            render(welcome);
        }
        tabPane.getSelectionModel().select(welcome);
        welcomeShown.forEach(Runnable::run);
    }

    private void install(Tab tab) {
        // The close button is Close Tab like any other: one path, which keeps the closed list.
        tab.setOnCloseRequest(e -> {
            e.consume();
            closeTab(tab);
        });
    }

    /** Re-renders a tab's header from its state. */
    void render(Tab tab) {
        SystemView view = views.get(tab);
        if (view != null) headers.systemHeader(tab, view);
        else headers.contentHeader(tab, Messages.tr("tabs.welcome"));
        Node header = tab.getGraphic();
        if (header != null) header.setOnContextMenuRequested(e -> showMenu(tab, e));
    }

    /** The tab's right-click menu: the tab is selected first, so its commands act on it (MENU-10). */
    void showMenu(Tab tab, ContextMenuEvent event) {
        tabPane.getSelectionModel().select(tab);
        Node header = tab.getGraphic();
        if (header != null) (tab == welcome ? welcomeMenu : menu).show(header, event);
    }

    /** The Welcome tab's menu: Close alone (WELC-11). */
    CommandMenu welcomeMenu() {
        return welcomeMenu;
    }

    /** What a quit would lose (WIN-13): nothing, since a system tab holds nothing the user typed. */
    List<String> unsavedNames() {
        return List.of();
    }

    // ---- the operations (TABS-01), each run by a command on the selected tab ----

    void closeTab(Tab tab) {
        SystemView view = views.remove(tab);
        if (view != null) closedTabs.closed(view.title(), view.selectedIndex());
        pinned.remove(tab);
        tabPane.getTabs().remove(tab);
        updateWindowTitle();
    }

    private void closeAll(List<Tab> tabs) {
        tabs.forEach(this::closeTab);
    }

    void closeOtherTabs(Tab keep) {
        closeAll(TabStrip.closeOtherTabs(tabs(), keep, pinned::contains));
    }

    void closeAllTabs() {
        closeAll(TabStrip.closeAllTabs(tabs(), pinned::contains));
    }

    void closeUnmodifiedTabs() {
        closeAll(TabStrip.closeUnmodifiedTabs(tabs(), pinned::contains, this::isDirty));
    }

    void closeTabsToLeft(Tab tab) {
        closeAll(TabStrip.eligibleToLeft(tabs(), tab, pinned::contains));
    }

    void closeTabsToRight(Tab tab) {
        closeAll(TabStrip.eligibleToRight(tabs(), tab, pinned::contains));
    }

    /** Pins or unpins {@code tab}, moving it to the pin boundary (TABS-05). */
    void togglePin(Tab tab) {
        if (!pinned.remove(tab)) pinned.add(tab);
        moveToPinBoundary(tab);
        render(tab);
        services.echo(pinned.contains(tab) ? "status.tabPinned" : "status.tabUnpinned", title(tab));
    }

    /** Moves {@code tab} to the end of the pinned group, or just after it, keeping it selected. */
    void moveToPinBoundary(Tab tab) {
        int at = TabStrip.moveToPinBoundary(tabs(), tab, pinned::contains);
        move(tab, at);
    }

    /** A dragged tab dropped beside {@code target}, held to its own group (TABS-07). */
    void moveBeside(Tab dragged, Tab target, boolean after) {
        int at = TabStrip.dropIndex(tabs(), dragged, target, after, pinned::contains);
        if (at >= 0) move(dragged, at);
    }

    private void move(Tab tab, int at) {
        boolean wasSelected = tab == selected();
        tabPane.getTabs().remove(tab);
        tabPane.getTabs().add(Math.min(at, tabPane.getTabs().size()), tab);
        if (wasSelected) tabPane.getSelectionModel().select(tab);
    }

    /** Brings back the most recently closed system with the game that was selected selected (TABS-06). */
    void reopenClosed() {
        ClosedTabs.Entry entry = closedTabs.pop();
        if (entry == null) return;
        Tab tab = open(entry.name(), entry.selected());
        SystemView view = views.get(tab);
        if (view != null) view.focusGames();
    }

    /** Selects the tab {@code delta} steps along the strip, wrapping. */
    void selectNext(int delta) {
        int at = TabStrip.cycle(
                tabPane.getSelectionModel().getSelectedIndex(),
                tabPane.getTabs().size(),
                delta);
        if (at >= 0) tabPane.getSelectionModel().select(at);
    }

    /** Shows or hides the tab strip (TABS-12): {@code .tab-pane.no-tab-header} in {@code app.css}. */
    void setTabBarShown(boolean shown) {
        TabHeaders.toggle(tabPane, "no-tab-header", !shown);
        tabPane.requestLayout(); // a hidden header area is unmanaged, which asks for no layout of its own
    }

    /**
     * A filter box's {@code >} query: runs the command whose title it names (FIND-13), or says there is none.
     * True when a command ran.
     */
    boolean runByTitle(String name) {
        java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();
        for (com.partida.command.Command command : services.commands().all()) titles.put(command.id(), command.title());
        java.util.Optional<String> id = GameFilter.commandFor(name, titles);
        if (id.isEmpty()) {
            services.echo("systems.filter.noCommand", name);
            return false;
        }
        AppMenus.runOrSay(services, id.get());
        return true;
    }

    /** What a tab is called: its system's name, or Welcome. */
    String title(Tab tab) {
        SystemView view = views.get(tab);
        return view == null ? Messages.tr("tabs.welcome") : view.shownName();
    }

    /** The window title: the system shown and the app (TABS-10). */
    void updateWindowTitle() {
        SystemView view = activeView();
        setTitle.accept(windowTitle(view == null ? null : view.shownName()));
    }

    /**
     * The window title for a system (or none). An app with no files or projects names what the window shows,
     * then the app (TABS-10). Pure.
     */
    static String windowTitle(@Nullable String system) {
        return system == null ? AppInfo.NAME : system + " — " + AppInfo.NAME;
    }

    /** The tabs a close command would take now, by its id; for whether it can run. */
    List<Tab> wouldClose(String commandId) {
        Tab tab = selected();
        if (tab == null) return List.of();
        return switch (commandId) {
            case "buffer.closeOthers" -> TabStrip.closeOtherTabs(tabs(), tab, pinned::contains);
            case "buffer.closeAll" -> TabStrip.closeAllTabs(tabs(), pinned::contains);
            case "buffer.closeUnmodified" -> TabStrip.closeUnmodifiedTabs(tabs(), pinned::contains, this::isDirty);
            case "buffer.closeLeft" -> TabStrip.eligibleToLeft(tabs(), tab, pinned::contains);
            case "buffer.closeRight" -> TabStrip.eligibleToRight(tabs(), tab, pinned::contains);
            default -> List.of(tab);
        };
    }

    // ---- why a command cannot run now ----

    // The rules are Chrome's, pure and unit-tested (QUAL-05); these read the facts they take.

    @Nullable
    String closeUnavailable() {
        return Chrome.closeUnavailable(selected() != null);
    }

    @Nullable
    String nothingToClose(String commandId) {
        return Chrome.nothingToClose(
                selected() != null,
                selected() == null ? 0 : wouldClose(commandId).size());
    }

    @Nullable
    String reopenUnavailable() {
        return Chrome.reopenUnavailable(closedTabs.size());
    }

    @Nullable
    String cycleUnavailable() {
        return Chrome.cycleUnavailable(tabPane.getTabs().size());
    }
}
