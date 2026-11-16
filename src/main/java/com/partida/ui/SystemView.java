package com.partida.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameTitle;
import org.jspecify.annotations.Nullable;

/**
 * The browser's content ({@link Browser}): one system's games (decision 3), a row or a box-art tile each, every ROM of a game under
 * one entry, beside the selected game's details ({@link GameDetails}). A ROM a tag hides (a bad dump, a hack, a
 * beta) stays under its game; a game of hidden ROMs only is left out unless Show Hidden ROMs is on.
 *
 * <p>The filter box over the games narrows them as it is typed (FIND-03): {@code find.show} focuses it,
 * Escape empties it and then leads back to the games (FIND-07, FIND-14), and it stays out of the Tab order
 * (FIND-15). Typing a letter in the games jumps to the first game it starts. Enter or a double-click runs
 * {@code game.play}, which starts the ROM {@code LibraryService.romToPlay} picks; the row's right-click menu
 * holds its commands (MENU-05), and the Menu key and Shift+F10 open it (MENU-09). A reload hands the view its
 * system's games again, and the selected game stays selected when it is still there.
 */
final class SystemView {

    /** What the view needs from the window, apart from the games. */
    record Hooks(
            Runnable play,
            Runnable playRom,
            Function<GameTitle, Game> chooser,
            Supplier<List<String>> regions,
            BooleanSupplier showHidden,
            BoxArt boxArt,
            Supplier<com.partida.library.@Nullable RetroArchContext> retroArch,
            Supplier<CommandMenu> rowMenu,
            Supplier<CommandMenu> romMenu) {}

    /** A tile's width in the grid, before the UI zoom. */
    static final double TILE_WIDTH = 132;

    static final double TILE_GAP = 10;

    /** The details column's width, before the UI zoom. */
    static final double DETAILS_WIDTH = 300;

    private final String name;
    private final Hooks hooks;
    /** Every game of the system, its hidden ones too; the views show the ones the filter keeps. */
    private final ObservableList<GameTitle> titles = FXCollections.observableArrayList();

    private final FilteredList<GameTitle> shown = new FilteredList<>(titles, t -> true);
    /** The games as rows. Its selection is the view's in either mode: the grid draws and moves it. */
    private final ListView<GameTitle> list = new ListView<>(shown);
    /** The games as box-art tiles, a row of them per cell, so only the rows in sight are drawn. */
    private final ListView<List<GameTitle>> grid = new ListView<>();

    private boolean gridMode;
    private int columns = 1;
    private final TextField filter = new TextField();
    /** Each game's ROMs read for the filter, made on each load, read on each keystroke (PERF-09). */
    private final Map<GameTitle, List<GameFilter.Indexed>> index = new HashMap<>();

    /** The {@code key:value} terms made into chips, left of the field (FIND-13). */
    private final List<GameFilter.Term> chipTerms = new ArrayList<>();

    private final HBox chips = new HBox();
    /** The chord of {@code library.filter}, read from the keymap, never written out (FIND-13, KBD-13). */
    private final Label badge = new Label();

    private final HBox box;
    private Supplier<String> badgeText = () -> "";
    /** Runs the command a {@code >} query names; true when one ran. */
    private java.util.function.Predicate<String> runCommand = name -> false;

    private final Label noMatch = new Label(Messages.tr("systems.noMatch"));
    private final Label noGames = new Label(Messages.tr("systems.noGames"));
    private final Label gridEmpty = new Label();
    private final StackPane games;
    private final GameDetails details;
    /** The games beside the details, a column of its own width (the window's tool windows resize around it). */
    private final HBox split;

    private final VBox node;
    private String typed = "";
    private long typedAt;

    /**
     * @param roms every ROM of the system, grouped here into its games
     * @param selected the index, among all the system's games, of the one to select
     */
    SystemView(String name, List<Game> roms, int selected, Hooks hooks) {
        this.name = name;
        this.hooks = hooks;
        list.getStyleClass().add("game-list");
        list.setAccessibleText(Chrome.systemName(name)); // a screen reader says which system (ACC-01)
        list.setPlaceholder(noGames);
        list.setCellFactory(v -> CommandMenu.onRow(new TitleCell(), hooks.rowMenu()));
        RowContextMenu.install(list); // the Menu key and Shift+F10 open the selected row's menu (MENU-09)
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                hooks.play().run();
        });
        list.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                hooks.play().run();
                e.consume();
            }
        });
        list.addEventHandler(KeyEvent.KEY_TYPED, this::typeAhead);

        grid.getStyleClass().add("game-grid");
        grid.setAccessibleText(Chrome.systemName(name));
        grid.setCellFactory(v -> new TileRow());
        grid.setPlaceholder(gridEmpty);
        grid.addEventFilter(KeyEvent.KEY_PRESSED, this::onGridKey);
        grid.addEventHandler(KeyEvent.KEY_TYPED, this::typeAhead);
        // A width or zoom change cuts the rows again only when the columns change (PERF-09); new games always do.
        grid.widthProperty().addListener((obs, was, now) -> layoutGrid(false));
        UiZoom.factor().addListener((obs, was, now) -> layoutGrid(false));
        shown.addListener((javafx.collections.ListChangeListener<GameTitle>) c -> layoutGrid(true));
        // The Menu key and Shift+F10 open the selected game's menu on its tile (MENU-05, MENU-09).
        grid.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (!e.isKeyboardTrigger() || e.isConsumed()) return;
            Node tile = selectedTile();
            if (tile == null) return;
            javafx.geometry.Bounds at = tile.localToScreen(tile.getBoundsInLocal());
            if (at == null) return;
            hooks.rowMenu().get().menu().show(tile, at.getMinX() + 8, at.getMaxY());
            e.consume();
        });

        filter.getStyleClass().add("game-filter");
        filter.setPromptText(Messages.tr("systems.filter"));
        filter.setAccessibleText(Messages.tr("systems.filter.name", Chrome.systemName(name)));
        filter.setFocusTraversable(false); // a fresh launch doesn't land every keystroke in it (FIND-15)
        filter.textProperty().addListener((obs, was, now) -> applyFilter());
        filter.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKey);
        HBox.setHgrow(filter, Priority.ALWAYS);
        chips.getStyleClass().add("filter-chips");
        UiZoom.bind(chips.spacingProperty(), 4);
        badge.getStyleClass().add("key-badge");
        badge.setMouseTransparent(true);
        box = UiZoom.hbox(6, chips, filter, badge);
        box.getStyleClass().add("game-filter-box");
        box.setAlignment(Pos.CENTER_LEFT);
        showChips();

        details = new GameDetails(hooks.play(), hooks.playRom(), hooks.romMenu(), hooks.boxArt(), hooks.retroArch());
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
            showDetails();
            if (gridMode) grid.refresh();
        });

        games = new StackPane(list, grid);
        VBox left = new VBox(box, games);
        VBox.setVgrow(games, Priority.ALWAYS);
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMinWidth(0);
        UiZoom.bind(details.node().prefWidthProperty(), DETAILS_WIDTH);
        details.node().setMinWidth(Region.USE_PREF_SIZE);
        details.node().setMaxWidth(Region.USE_PREF_SIZE);
        split = new HBox(left, details.node());
        split.getStyleClass().add("system-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        node = new VBox(split);
        node.getStyleClass().add("system-view");
        setGrid(false);
        setGames(roms);
        if (selected >= 0 && selected < titles.size()) list.getSelectionModel().select(titles.get(selected));
        else if (!shown.isEmpty()) list.getSelectionModel().selectFirst();
    }

    // ---- the filter box ----

    /**
     * Escape empties the box, its chips too, and in an empty box leads back to the games (FIND-14, FIND-07).
     * Space or Enter after a {@code key:value} term makes it a chip; Enter after {@code >} runs the command it
     * names; Backspace at the start of the field takes the last chip back; Enter and Down otherwise go to the
     * games, on the first when none is selected.
     */
    private void onFilterKey(KeyEvent e) {
        GameFilter.KeyAction action = GameFilter.keyAction(
                e.getCode().name(),
                filter.getText(),
                filter.getCaretPosition(),
                filter.getSelection().getLength() > 0,
                !chipTerms.isEmpty());
        switch (action) {
            case NONE -> {
                return; // typing edits the box
            }
            case CLEAR -> clearFilter();
            case TO_LIST -> toList();
            case CHIP -> makeChip();
            case UNCHIP -> {
                chipTerms.removeLast();
                showChips();
                applyFilter();
            }
            case RUN -> {
                String command = GameFilter.command(filter.getText());
                if (command != null && runCommand.test(command)) filter.clear();
            }
        }
        e.consume();
    }

    private void toList() {
        if (list.getSelectionModel().isEmpty() && !shown.isEmpty())
            list.getSelectionModel().selectFirst();
        focusGames();
    }

    /** The word just typed, at the end of the field, as a chip when it is a {@code key:value} term. */
    private boolean makeChip() {
        String text = filter.getText();
        java.util.Optional<GameFilter.Term> term = GameFilter.chipAt(text, filter.getCaretPosition());
        if (term.isEmpty()) return false;
        int start = text.lastIndexOf(' ') + 1;
        chipTerms.add(term.get());
        showChips();
        filter.setText(text.substring(0, start).stripTrailing());
        filter.positionCaret(filter.getText().length());
        return true;
    }

    /** Draws the chips: each its term and a button that takes it away, out of the Tab order. */
    private void showChips() {
        chips.getChildren().clear();
        for (GameFilter.Term term : List.copyOf(chipTerms)) {
            javafx.scene.control.Button chip = new javafx.scene.control.Button(term.text() + " ×");
            chip.getStyleClass().add("filter-chip");
            chip.setFocusTraversable(false);
            chip.setAccessibleText(Messages.tr("systems.filter.removeChip", term.text()));
            chip.setOnAction(e -> {
                chipTerms.remove(term);
                showChips();
                applyFilter();
                filter.requestFocus();
            });
            chips.getChildren().add(chip);
        }
        chips.setVisible(!chipTerms.isEmpty());
        chips.setManaged(!chipTerms.isEmpty());
    }

    private void clearFilter() {
        chipTerms.clear();
        showChips();
        filter.clear();
        applyFilter();
    }

    /**
     * Keeps the games the query asks for, a game kept when any of its ROMs matches (every word in its name,
     * every chip's term met, FIND-13), and a game of hidden ROMs only when Show Hidden ROMs is on.
     */
    void applyFilter() {
        GameTitle was = selectedTitle();
        GameFilter.Query query = GameFilter.parse(filter.getText(), chipTerms);
        boolean showHidden = hooks.showHidden().getAsBoolean();
        shown.setPredicate(t -> (showHidden || !t.hidden()) && (query.isEmpty() || matches(t, query)));
        Label placeholder = query.isEmpty() ? noGames : noMatch;
        list.setPlaceholder(placeholder);
        gridEmpty.setText(placeholder.getText());
        if (was != null && shown.contains(was)) list.getSelectionModel().select(was);
        else if (!shown.isEmpty()) list.getSelectionModel().selectFirst();
        reveal();
        showDetails();
    }

    private boolean matches(GameTitle title, GameFilter.Query query) {
        List<GameFilter.Indexed> roms = index.get(title);
        if (roms == null)
            roms = title.roms().stream()
                    .map(r -> GameFilter.Indexed.of(r.game()))
                    .toList();
        for (GameFilter.Indexed rom : roms) {
            if (GameFilter.matches(rom, query)) return true;
        }
        return false;
    }

    /** What the box's key badge shows, and what runs a {@code >} query: the window's, set once. */
    void connect(Supplier<String> badgeText, java.util.function.Predicate<String> runCommand) {
        this.badgeText = badgeText;
        this.runCommand = runCommand;
        refreshBadge();
    }

    /** The badge again, after a keymap change; hidden when the command has no chord. */
    void refreshBadge() {
        String chord = badgeText.get();
        badge.setText(chord);
        badge.setVisible(!chord.isEmpty());
        badge.setManaged(!chord.isEmpty());
    }

    Label badge() {
        return badge;
    }

    List<GameFilter.Term> chipTerms() {
        return List.copyOf(chipTerms);
    }

    /** {@code find.show}: the filter box, its text selected so typing replaces it. */
    void focusFilter() {
        filter.requestFocus();
        filter.selectAll();
    }

    /** Whether the box narrows the games now. */
    boolean filtering() {
        return !GameFilter.parse(filter.getText(), chipTerms).isEmpty();
    }

    /** {@code find.next} and {@code find.previous} while filtering: the next or previous game kept, wrapping. */
    void step(boolean forward) {
        int n = shown.size();
        if (n == 0) return;
        int at = list.getSelectionModel().getSelectedIndex();
        int next = at < 0 ? (forward ? 0 : n - 1) : Math.floorMod(at + (forward ? 1 : -1), n);
        select(next);
    }

    // ---- the games ----

    /** Typing a letter or a digit in the games jumps to the first game whose name starts with what was typed. */
    private void typeAhead(KeyEvent e) {
        String c = e.getCharacter();
        if (c.length() != 1 || !Character.isLetterOrDigit(c.charAt(0)) || e.isShortcutDown() || e.isAltDown()) return;
        long now = System.currentTimeMillis();
        typed = BrowseRules.typed(typed, typedAt, c.charAt(0), now);
        typedAt = now;
        int from = typed.length() == 1
                ? list.getSelectionModel().getSelectedIndex() + 1
                : Math.max(0, list.getSelectionModel().getSelectedIndex());
        int at = BrowseRules.typeAhead(shown, typed, from);
        if (at < 0 && typed.length() > 1) {
            // What was typed so far matches nothing: this letter starts over.
            typed = c;
            at = BrowseRules.typeAhead(shown, typed, list.getSelectionModel().getSelectedIndex() + 1);
        }
        if (at >= 0) select(at);
        e.consume();
    }

    /** Selects the game at {@code at} among the shown ones and scrolls it into sight, in either mode. */
    private void select(int at) {
        list.getSelectionModel().select(at);
        reveal();
    }

    private void reveal() {
        int at = list.getSelectionModel().getSelectedIndex();
        if (at < 0) return;
        if (gridMode) grid.scrollTo(at / Math.max(1, columns));
        else list.scrollTo(at);
    }

    /** The grid's arrows, Home and End move the view's selection; Enter plays (the grid has no rows to select). */
    private void onGridKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) {
            hooks.play().run();
            e.consume();
            return;
        }
        if (e.isShortcutDown() || e.isAltDown()) return;
        int next = BrowseRules.gridMove(
                e.getCode().name(), list.getSelectionModel().getSelectedIndex(), shown.size(), columns);
        if (next < 0) return;
        select(next);
        e.consume();
    }

    /**
     * The grid's rows again, for its width now: as many tiles across as fit. Unless {@code always}, only when
     * the number across changed, so a window being resized doesn't cut every game into rows on each frame.
     */
    private void layoutGrid(boolean always) {
        double tile = TILE_WIDTH * UiZoom.factor().get();
        double gap = TILE_GAP * UiZoom.factor().get();
        double width = grid.getWidth() - 18 * UiZoom.factor().get(); // the scroll bar's room
        int fit = BrowseRules.columns(width, tile, gap);
        boolean changed = fit != columns;
        columns = fit;
        if (!gridMode || (!always && !changed && !grid.getItems().isEmpty())) return;
        grid.getItems().setAll(BrowseRules.rows(shown, fit));
        reveal();
    }

    /** The box-art grid on, or the list: a choice per system, kept in the settings ({@code library.toggleGrid}). */
    void setGrid(boolean on) {
        boolean focused = list.isFocused() || grid.isFocused();
        gridMode = on;
        list.setVisible(!on);
        list.setManaged(!on);
        grid.setVisible(on);
        grid.setManaged(on);
        if (on) layoutGrid(true);
        else grid.getItems().clear();
        reveal();
        if (focused) focusGames();
    }

    boolean isGrid() {
        return gridMode;
    }

    /** The games take the keyboard: the list, or the grid in its mode. */
    void focusGames() {
        if (gridMode) grid.requestFocus();
        else list.requestFocus();
    }

    /** The system's ROMs after a reload, grouped into games; the selected game stays when it is still there. */
    void setGames(List<Game> roms) {
        GameTitle was = selectedTitle();
        String keep = was == null ? null : was.id();
        List<GameTitle> grouped = GameTitle.group(roms, hooks.regions().get());
        index.clear();
        for (GameTitle title : grouped) {
            index.put(
                    title,
                    title.roms().stream()
                            .map(r -> GameFilter.Indexed.of(r.game()))
                            .toList());
        }
        titles.setAll(grouped);
        applyFilter();
        if (keep == null) return;
        for (GameTitle title : shown) {
            if (title.id().equals(keep)) {
                list.getSelectionModel().select(title);
                reveal();
                return;
            }
        }
    }

    /** The details again: after a selection, a play (the ROM Play starts may change) or a setting. */
    void showDetails() {
        GameTitle title = selectedTitle();
        details.show(
                title,
                title == null ? null : hooks.chooser().apply(title),
                hooks.showHidden().getAsBoolean());
    }

    /** The system's name: the view's identity, for the session and the grid setting. */
    String title() {
        return name;
    }

    /** The name the user reads: the system's, or the play history's in their language (INTL-07). */
    String shownName() {
        return Chrome.systemName(name);
    }

    ListView<GameTitle> list() {
        return list;
    }

    ListView<List<GameTitle>> grid() {
        return grid;
    }

    int columns() {
        return columns;
    }

    GameDetails details() {
        return details;
    }

    TextField filter() {
        return filter;
    }

    /** What the browser shows: the filter box over the games, beside the selected game's details. */
    VBox node() {
        return node;
    }

    @Nullable
    GameTitle selectedTitle() {
        return list.getSelectionModel().getSelectedItem();
    }

    /** The ROM Play starts for the selected game ({@code LibraryService.romToPlay}), or null. */
    @Nullable
    Game selected() {
        GameTitle title = selectedTitle();
        return title == null ? null : hooks.chooser().apply(title);
    }

    /** The ROM selected in the details' list: what {@code library.playRom} starts. */
    @Nullable
    Game selectedRom() {
        return details.selectedRom();
    }

    /** The selected game's index among all the system's games, filtered out or not; -1 for none. */
    int selectedIndex() {
        GameTitle title = selectedTitle();
        return title == null ? -1 : titles.indexOf(title);
    }

    /** How many games the system has that browsing shows, whatever the filter keeps. */
    int size() {
        boolean showHidden = hooks.showHidden().getAsBoolean();
        return (int) titles.stream().filter(t -> showHidden || !t.hidden()).count();
    }

    /** A game's row: its glyph, its name, and how many ROMs and which regions; made once (PERF-09). */
    private final class TitleCell extends ListCell<GameTitle> {
        private final Node glyph = Icons.line(Icons.FILE);
        private final Label name = new Label();
        private final Label summary = new Label();
        private final Region gap = new Region();
        private final HBox row = UiZoom.hbox(6, glyph, name, gap, summary);

        TitleCell() {
            summary.getStyleClass().add("muted");
            summary.setMinWidth(Region.USE_PREF_SIZE);
            name.setMinWidth(0);
            HBox.setHgrow(gap, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(@Nullable GameTitle title, boolean empty) {
            super.updateItem(title, empty);
            setText(null);
            if (empty || title == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            boolean showHidden = hooks.showHidden().getAsBoolean();
            name.setText(title.title());
            summary.setText(BrowseRules.summary(title, showHidden));
            setGraphic(row);
            setAccessibleText(BrowseRules.accessible(title, showHidden));
        }
    }

    /** A row of the grid: its games' tiles, box art over the name, made once and filled on each update. */
    private final class TileRow extends ListCell<List<GameTitle>> {
        private final HBox tiles = new HBox();
        private final List<Tile> made = new ArrayList<>();

        TileRow() {
            UiZoom.bind(tiles.spacingProperty(), TILE_GAP);
            tiles.setAlignment(Pos.TOP_LEFT);
            getStyleClass().add("tile-row");
        }

        @Override
        protected void updateItem(@Nullable List<GameTitle> row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            while (made.size() < row.size()) made.add(new Tile());
            tiles.getChildren()
                    .setAll(made.subList(0, row.size()).stream().map(Tile::node).toList());
            for (int i = 0; i < row.size(); i++) made.get(i).show(row.get(i));
            setGraphic(tiles);
            // The row read out as its games (ACC-01); each tile is read on its own as a button.
            setAccessibleText(
                    String.join(", ", row.stream().map(GameTitle::title).toList()));
        }
    }

    /** One game in the grid: its box art, or its initials until there is art, over its name. */
    private final class Tile {
        private final ImageView art = new ImageView();
        private final Label initials = new Label();
        private final Label name = new Label();
        private final VBox node;
        private @Nullable GameTitle title;

        Tile() {
            art.setPreserveRatio(true);
            UiZoom.bind(art.fitWidthProperty(), TILE_WIDTH);
            UiZoom.bind(art.fitHeightProperty(), TILE_WIDTH * 1.3);
            initials.getStyleClass().add("game-initials");
            StackPane cover = new StackPane(initials, art);
            cover.getStyleClass().add("tile-art");
            UiZoom.bind(cover.prefWidthProperty(), TILE_WIDTH);
            UiZoom.bind(cover.prefHeightProperty(), TILE_WIDTH * 1.3);
            name.getStyleClass().add("tile-name");
            name.setWrapText(true);
            name.setAlignment(Pos.TOP_CENTER);
            name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            UiZoom.bind(name.prefWidthProperty(), TILE_WIDTH);
            UiZoom.bind(name.maxWidthProperty(), TILE_WIDTH);
            // A button to a screen reader (ACC-02): its press plays the game, as a double-click does.
            node = new VBox(cover, name) {
                @Override
                public void executeAccessibleAction(javafx.scene.AccessibleAction action, Object... parameters) {
                    GameTitle game = title;
                    if (action == javafx.scene.AccessibleAction.FIRE && game != null) {
                        list.getSelectionModel().select(game);
                        hooks.play().run();
                    } else {
                        super.executeAccessibleAction(action, parameters);
                    }
                }
            };
            UiZoom.bind(node.spacingProperty(), 4);
            node.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
            node.getStyleClass().add("game-tile");
            node.setAlignment(Pos.TOP_CENTER);
            node.setOnMouseClicked(e -> {
                GameTitle clicked = title;
                if (clicked == null || e.getButton() != MouseButton.PRIMARY) return;
                list.getSelectionModel().select(clicked);
                grid.requestFocus();
                if (e.getClickCount() == 2) hooks.play().run();
            });
            // A right-click selects the game and opens its menu, the list row's (MENU-05, MENU-10).
            node.setOnContextMenuRequested(e -> {
                GameTitle clicked = title;
                if (clicked == null) return;
                list.getSelectionModel().select(clicked);
                hooks.rowMenu().get().show(node, e);
            });
        }

        void show(GameTitle game) {
            boolean same = game == title;
            title = game;
            // A pseudo-class, so a move changes no style class list on any tile (PERF-10).
            node.pseudoClassStateChanged(SELECTED, game == selectedTitle());
            node.setAccessibleText(
                    BrowseRules.accessible(game, hooks.showHidden().getAsBoolean()));
            if (same) return;
            name.setText(game.title());
            initials.setText(BrowseRules.initials(game.title()));
            art.setImage(null);
            initials.setVisible(true);
            double width = TILE_WIDTH * UiZoom.factor().get() * 2;
            hooks.boxArt().load(game.best().game(), width, image -> {
                if (title != game) return; // the tile shows another game by now
                art.setImage(image);
                initials.setVisible(image == null);
            });
        }

        VBox node() {
            return node;
        }
    }

    private static final javafx.css.PseudoClass SELECTED = javafx.css.PseudoClass.getPseudoClass("selected");

    /** The selected game's tile, when its row is in sight. */
    private @Nullable Node selectedTile() {
        for (Node n : grid.lookupAll(".game-tile")) {
            if (n.getPseudoClassStates().contains(SELECTED)) return n;
        }
        return null;
    }

    /** The tiles of the rows in sight, for the tests. */
    List<Node> tiles() {
        List<Node> found = new ArrayList<>();
        for (Node n : grid.lookupAll(".game-tile")) found.add(n);
        return found;
    }
}
