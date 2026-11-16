package com.partida.ui;

import java.util.List;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.GameCollection;
import com.partida.library.LibraryLoader;
import org.jspecify.annotations.Nullable;

/**
 * The content of Partida's two tool windows, each built on its first open (TABS-14):
 *
 * <ul>
 *   <li>{@link Systems} (left): the library's systems with their game counts. Selecting one shows its games in
 *       the browser ({@link Browser}); Enter or a double-click also gives them the keyboard. While the library
 *       can't be read it says why.
 *   <li>{@link Console} (bottom): what RetroArch prints while a game runs, busy meanwhile (TABS-22), with its find
 *       bar; it keeps the newest lines (PERF-11).
 * </ul>
 */
final class LibraryPanels {

    private LibraryPanels() {}

    /** The library's systems, a row each with its game count. */
    static final class Systems {
        private final ListView<GameCollection> list = new ListView<>();
        private final Browser browser;
        private final LibraryService library;
        private final java.util.function.BooleanSupplier showHidden;
        private final Label placeholder = new Label();
        private final VBox node;

        /** {@code rowMenu} is each row's right-click menu (MENU-05): {@code systems.open} and its kin. */
        Systems(
                Browser browser,
                LibraryService library,
                Supplier<CommandMenu> rowMenu,
                java.util.function.BooleanSupplier showHidden) {
            this.browser = browser;
            this.library = library;
            this.showHidden = showHidden;
            list.getStyleClass().add("systems-list");
            list.setAccessibleText(Messages.tr("tool.systems"));
            placeholder.setWrapText(true);
            list.setPlaceholder(placeholder);
            list.setCellFactory(v -> CommandMenu.onRow(
                    new ListCell<>() {
                        /** Both glyphs made once per cell: a scroll step only picks one (PERF-09). */
                        private final javafx.scene.Node history = Icons.line(Icons.HISTORY);

                        private final javafx.scene.Node system = Icons.line(Icons.DOCUMENTS);

                        @Override
                        protected void updateItem(@Nullable GameCollection collection, boolean empty) {
                            super.updateItem(collection, empty);
                            if (empty || collection == null) {
                                setText(null);
                                setAccessibleText(null);
                                setGraphic(null);
                                return;
                            }
                            LibraryLoader.Counts counts =
                                    library.state().library().counts(collection.name());
                            int count = showHidden.getAsBoolean() ? counts.all() : counts.shown();
                            String shown = Chrome.systemName(collection.name());
                            setText(shown + "   " + count);
                            setAccessibleText(shown + ", " + Messages.tr("systems.games", count));
                            setGraphic(collection.name().equals(LibraryLoader.RECENTLY_PLAYED) ? history : system);
                        }
                    },
                    rowMenu));
            RowContextMenu.install(list); // the Menu key and Shift+F10 open the selected row's menu (MENU-09)
            // Walking the list shows each system in the browser; the keyboard stays in the list.
            list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
                if (now != null && !refreshing) browser.show(now.name());
            });
            list.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected();
            });
            list.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    openSelected();
                    e.consume();
                }
            });
            VBox.setVgrow(list, Priority.ALWAYS);
            node = new VBox(list);
            Redraw redraw = new Redraw(this::refresh); // once per pulse (PERF-06)
            library.onChanged(redraw::request);
            refresh();
        }

        /** Lists the library's systems again, keeping the selected one; an empty list says why it is empty. */
        void refresh() {
            LibraryService.State state = library.state();
            GameCollection was = list.getSelectionModel().getSelectedItem();
            String keep = was != null ? was.name() : browser.activePath();
            refreshing = true;
            try {
                list.getItems().setAll(state.library().collections());
                if (keep != null) {
                    for (GameCollection c : list.getItems()) {
                        if (c.name().equals(keep)) list.getSelectionModel().select(c);
                    }
                }
            } finally {
                refreshing = false;
            }
            placeholder.setText(
                    switch (state.status()) {
                        case LOADING -> Messages.tr("systems.loading");
                        case NOT_FOUND -> Messages.tr("systems.notFound");
                        case FAILED -> Messages.tr("systems.failed", state.detail());
                        case READY -> Messages.tr("systems.empty");
                    });
        }

        /** True while {@link #refresh} puts the rows back: that selection shows nothing new. */
        private boolean refreshing;

        Region node() {
            return node;
        }

        /** Draws each system's count again, its rows kept: Show Hidden ROMs changed what it counts. */
        void refreshCounts() {
            list.refresh();
        }

        /** Shows the selected system in the browser and gives its games the keyboard ({@code systems.open}). */
        void openSelected() {
            GameCollection collection = list.getSelectionModel().getSelectedItem();
            if (collection != null) browser.open(collection);
        }

        boolean hasSelection() {
            return list.getSelectionModel().getSelectedItem() != null;
        }

        ListView<GameCollection> list() {
            return list;
        }

        Label placeholder() {
            return placeholder;
        }
    }

    /** What RetroArch prints while a game runs. */
    static final class Console {
        /** The console keeps this many lines; past it the oldest go first (PERF-11). */
        static final int MAX_LINES = 1000;

        private final TextArea console = new TextArea();
        /** The console's find bar (FIND-01): {@code retroarch.find} opens it. */
        private final FindBar findBar = new FindBar(console, "retroarch.findNext", "retroarch.findPrevious");

        private final VBox node;

        Console() {
            console.setEditable(false);
            console.setAccessibleText(Messages.tr("tool.retroarch"));
            console.getStyleClass().addAll("output-console", "debug-log-text");
            console.setPromptText(Messages.tr("retroarch.empty"));
            Label hint = new Label(Messages.tr("retroarch.hint"));
            hint.getStyleClass().add("hint");
            HBox bar = UiZoom.hbox(8, hint);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.getStyleClass().add("output-bar");
            VBox.setVgrow(console, Priority.ALWAYS);
            node = new VBox(bar, findBar, console);
        }

        /** Adds lines, then drops the oldest past {@link #MAX_LINES}: the newest are kept (PERF-11). */
        void append(List<String> lines) {
            if (lines.isEmpty()) return;
            console.appendText(String.join("\n", lines) + "\n");
            int extra = console.getParagraphs().size() - 1 - MAX_LINES; // the text ends with a newline
            if (extra <= 0) return;
            String text = console.getText();
            int cut = 0;
            for (int i = 0; i < extra; i++) cut = text.indexOf('\n', cut) + 1;
            console.deleteText(0, cut);
        }

        /** Empties the console ({@code retroarch.clear}). */
        void clear() {
            console.clear();
        }

        FindBar findBar() {
            return findBar;
        }

        TextArea console() {
            return console;
        }

        Region node() {
            return node;
        }
    }
}
