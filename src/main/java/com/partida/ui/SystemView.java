package com.partida.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import org.jspecify.annotations.Nullable;

/**
 * A tab's content: one system's games, a row each, with a filter box over the list (FIND-03). A library is
 * the app's main content and a list that grows without bound, so the box narrows the list as it is typed, in
 * place of a find bar: {@code find.show} focuses it, Escape empties it and then leads back to the list
 * (FIND-07, FIND-14), and it stays out of the Tab order (FIND-15). Enter or a double-click runs
 * {@code game.play} on the selected game; the row's right-click menu holds its commands (MENU-05), and the
 * Menu key and Shift+F10 open it (MENU-09). A reload of the library hands the tab its system's games again,
 * and the selected game stays selected when it is still there.
 */
final class SystemView {

    private final String name;
    /** Every game of the system; the list shows the ones the filter keeps. */
    private final ObservableList<Game> games = FXCollections.observableArrayList();

    private final FilteredList<Game> shown = new FilteredList<>(games, g -> true);
    private final ListView<Game> list = new ListView<>(shown);
    private final TextField filter = new TextField();
    /** Each game's lower-case title and system, made on each load, read on each keystroke (PERF-09). */
    private final java.util.Map<Game, GameFilter.Indexed> index = new java.util.HashMap<>();

    /** The {@code key:value} terms made into chips, left of the field (FIND-13). */
    private final List<GameFilter.Term> chipTerms = new java.util.ArrayList<>();

    private final javafx.scene.layout.HBox chips = new javafx.scene.layout.HBox();
    /** The chord of {@code library.filter}, read from the keymap, never written out (FIND-13, KBD-13). */
    private final Label badge = new Label();

    private final javafx.scene.layout.HBox box;
    private java.util.function.Supplier<String> badgeText = () -> "";
    /** Runs the command a {@code >} query names; true when one ran. */
    private java.util.function.Predicate<String> runCommand = name -> false;

    private final Label noMatch = new Label(Messages.tr("systems.noMatch"));
    private final Label noGames = new Label(Messages.tr("systems.noGames"));

    private final VBox node;

    /**
     * @param selected the index, among all the system's games, of the one to select
     * @param play runs {@code game.play}, the one way a game starts
     * @param rowMenu each row's right-click menu
     */
    SystemView(String name, List<Game> games, int selected, Runnable play, Supplier<CommandMenu> rowMenu) {
        this.name = name;
        list.getStyleClass().add("game-list");
        list.setAccessibleText(Chrome.systemName(name)); // a screen reader says which system (ACC-01)
        list.setPlaceholder(noGames);
        list.setCellFactory(v -> CommandMenu.onRow(
                new ListCell<>() {
                    /** Made once per cell: a scroll step only sets the text (PERF-09). */
                    private final javafx.scene.Node glyph = Icons.line(Icons.FILE);

                    @Override
                    protected void updateItem(@Nullable Game game, boolean empty) {
                        super.updateItem(game, empty);
                        if (empty || game == null) {
                            setText(null);
                            setGraphic(null);
                            setAccessibleText(null);
                            return;
                        }
                        setText(game.title());
                        setGraphic(glyph);
                        setAccessibleText(game.system().isBlank() ? game.title() : game.title() + ", " + game.system());
                    }
                },
                rowMenu));
        RowContextMenu.install(list); // the Menu key and Shift+F10 open the selected row's menu (MENU-09)
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) play.run();
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                play.run();
                e.consume();
            }
        });

        filter.getStyleClass().add("game-filter");
        filter.setPromptText(Messages.tr("systems.filter"));
        filter.setAccessibleText(Messages.tr("systems.filter.name", Chrome.systemName(name)));
        filter.setFocusTraversable(false); // a fresh launch doesn't land every keystroke in it (FIND-15)
        filter.textProperty().addListener((obs, was, now) -> applyFilter());
        filter.addEventFilter(KeyEvent.KEY_PRESSED, this::onFilterKey);
        javafx.scene.layout.HBox.setHgrow(filter, Priority.ALWAYS);
        chips.getStyleClass().add("filter-chips");
        UiZoom.bind(chips.spacingProperty(), 4);
        badge.getStyleClass().add("key-badge");
        badge.setMouseTransparent(true);
        box = UiZoom.hbox(6, chips, filter, badge);
        box.getStyleClass().add("game-filter-box");
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        showChips();

        VBox.setVgrow(list, Priority.ALWAYS);
        node = new VBox(box, list);
        node.getStyleClass().add("system-view");
        setGames(games);
        if (selected >= 0 && selected < games.size()) list.getSelectionModel().select(games.get(selected));
        else if (!games.isEmpty()) list.getSelectionModel().selectFirst();
    }

    /**
     * Escape empties the box, its chips too, and in an empty box leads back to the list (FIND-14, FIND-07).
     * Space or Enter after a {@code key:value} term makes it a chip; Enter after {@code >} runs the command it
     * names; Backspace at the start of the field takes the last chip back; Enter and Down otherwise go to the
     * list, on its first row when none is selected.
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
        list.requestFocus();
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

    /** Keeps the games the query asks for: every word in the title, every chip's term met (FIND-13). */
    private void applyFilter() {
        Game was = selected();
        GameFilter.Query query = GameFilter.parse(filter.getText(), chipTerms);
        shown.setPredicate(query.isEmpty() ? g -> true : g -> GameFilter.matches(indexed(g), query));
        list.setPlaceholder(query.isEmpty() ? noGames : noMatch);
        if (was != null && shown.contains(was)) list.getSelectionModel().select(was);
        else if (!shown.isEmpty()) list.getSelectionModel().selectFirst();
        list.scrollTo(Math.max(0, list.getSelectionModel().getSelectedIndex()));
    }

    /** What the box's key badge shows, and what runs a {@code >} query: the window's, set once. */
    void connect(java.util.function.Supplier<String> badgeText, java.util.function.Predicate<String> runCommand) {
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

    private GameFilter.Indexed indexed(Game game) {
        GameFilter.Indexed found = index.get(game);
        return found != null ? found : GameFilter.Indexed.of(game);
    }

    /** {@code find.show}: the filter box, its text selected so typing replaces it. */
    void focusFilter() {
        filter.requestFocus();
        filter.selectAll();
    }

    /** Whether the box narrows the list now. */
    boolean filtering() {
        return !GameFilter.parse(filter.getText(), chipTerms).isEmpty();
    }

    /** {@code find.next} and {@code find.previous} while filtering: the next or previous game kept, wrapping. */
    void step(boolean forward) {
        int n = shown.size();
        if (n == 0) return;
        int at = list.getSelectionModel().getSelectedIndex();
        int next = at < 0 ? (forward ? 0 : n - 1) : Math.floorMod(at + (forward ? 1 : -1), n);
        list.getSelectionModel().select(next);
        list.scrollTo(next);
    }

    /** The system's games after a reload; the selected game stays selected when it is still listed. */
    void setGames(List<Game> newGames) {
        Game was = selected();
        Path keep = was == null ? null : was.rom();
        index.clear();
        for (Game game : newGames) index.put(game, GameFilter.Indexed.of(game));
        games.setAll(newGames);
        if (keep == null) return;
        for (Game game : shown) {
            if (game.rom().equals(keep)) {
                list.getSelectionModel().select(game);
                return;
            }
        }
    }

    /** The system's name: the tab's title and its identity, for the session and Reopen Closed Tab. */
    String title() {
        return name;
    }

    /** The name the user reads: the system's, or the play history's in their language (INTL-07). */
    String shownName() {
        return Chrome.systemName(name);
    }

    ListView<Game> list() {
        return list;
    }

    TextField filter() {
        return filter;
    }

    /** What the tab shows: the filter box (its chips, field and key badge) over the games. */
    VBox node() {
        return node;
    }

    @Nullable
    Game selected() {
        return list.getSelectionModel().getSelectedItem();
    }

    /** The selected game's index among all the system's games, filtered out or not; -1 for none. */
    int selectedIndex() {
        Game game = selected();
        return game == null ? -1 : games.indexOf(game);
    }

    /** How many games the system has, whatever the filter shows. */
    int size() {
        return games.size();
    }
}
