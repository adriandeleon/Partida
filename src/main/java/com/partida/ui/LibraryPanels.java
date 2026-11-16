package com.partida.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameCollection;
import com.partida.library.LibraryLoader;
import com.partida.library.RetroArchContext;
import com.partida.retroarch.CoreInfo;
import com.partida.retroarch.Thumbnails;
import org.jspecify.annotations.Nullable;

/**
 * The content of Partida's three tool windows, each built on its first open (TABS-14):
 *
 * <ul>
 *   <li>{@link Systems} (left): the library's systems with their game counts. Enter or a double-click opens one
 *       in a tab; while the library can't be read it says why.
 *   <li>{@link GamePanel} (right): the game selected in the active tab, with its box art, the core it starts
 *       with and any BIOS file that core is missing, and Play. It follows the active tab (TABS-23), and is
 *       unavailable on a tab that holds no system (TABS-20).
 *   <li>{@link Console} (bottom): what RetroArch prints while a game runs, busy meanwhile (TABS-22), with its find
 *       bar; it keeps the newest lines (PERF-11).
 * </ul>
 */
final class LibraryPanels {

    private LibraryPanels() {}

    /** The library's systems, a row each with its game count. */
    static final class Systems {
        private final ListView<GameCollection> list = new ListView<>();
        private final DocumentTabs tabs;
        private final LibraryService library;
        private final java.util.function.BooleanSupplier showHidden;
        private final Label placeholder = new Label();
        private final VBox node;

        /** {@code rowMenu} is each row's right-click menu (MENU-05): {@code systems.open} and its kin. */
        Systems(
                DocumentTabs tabs,
                LibraryService library,
                Supplier<CommandMenu> rowMenu,
                java.util.function.BooleanSupplier showHidden) {
            this.tabs = tabs;
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
            list.getItems().setAll(state.library().collections());
            if (was != null) {
                for (GameCollection c : list.getItems()) {
                    if (c.name().equals(was.name())) list.getSelectionModel().select(c);
                }
            }
            placeholder.setText(
                    switch (state.status()) {
                        case LOADING -> Messages.tr("systems.loading");
                        case NOT_FOUND -> Messages.tr("systems.notFound");
                        case FAILED -> Messages.tr("systems.failed", state.detail());
                        case READY -> Messages.tr("systems.empty");
                    });
        }

        Region node() {
            return node;
        }

        /** Draws each system's count again, its rows kept: Show Hidden ROMs changed what it counts. */
        void refreshCounts() {
            list.refresh();
        }

        /** Opens the selected system in a tab and gives its games the keyboard ({@code systems.open}). */
        void openSelected() {
            GameCollection collection = list.getSelectionModel().getSelectedItem();
            if (collection == null) return;
            SystemView view = tabs.view(tabs.open(collection));
            if (view != null) view.focusGames();
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

    /** What the Game window shows for one game, read off the FX thread (PERF-01). */
    record GameInfo(
            Game game,
            List<CoreInfo> cores,
            @Nullable CoreInfo preferred,
            Map<String, List<CoreInfo.Firmware>> missing,
            @Nullable Path boxart) {

        /** The largest box art read (PERF-12): past it, the initials show instead of a decoded image. */
        static final long BOXART_LIMIT = 16L * 1024 * 1024;

        /** Whether a box-art file is small enough to decode; checked off the FX thread, before any read. */
        static boolean fits(Path file) {
            try {
                return java.nio.file.Files.size(file) <= BOXART_LIMIT;
            } catch (java.io.IOException e) {
                return false;
            }
        }

        /** Every core that runs {@code game}, the one it would start with, missing BIOS files and box art. */
        static GameInfo read(RetroArchContext ctx, Game game) {
            List<CoreInfo> cores = new ArrayList<>(ctx.compatibleCores(game));
            CoreInfo preferred = ctx.preferredCore(game).orElse(null);
            if (preferred != null && !cores.contains(preferred)) cores.addFirst(preferred);
            Map<String, List<CoreInfo.Firmware>> missing = new LinkedHashMap<>();
            for (CoreInfo core : cores) missing.put(core.id(), core.missingFirmware(ctx.systemDir()));
            Path boxart = ctx.thumbnails()
                    .find(game.system(), game.title(), game.fileStem(), Thumbnails.Kind.BOXART)
                    .filter(GameInfo::fits)
                    .orElse(null);
            return new GameInfo(game, List.copyOf(cores), preferred, missing, boxart);
        }
    }

    /** The selected game: its box art, its core, missing BIOS files and Play. */
    static final class GamePanel {
        static final double ART_SIZE = 220;

        private final ImageView art = new ImageView();
        private final Label initials = new Label();
        private final Label title = new Label();
        private final Label system = new Label();
        private final Label file = new Label();
        private final ComboBox<CoreInfo> core = new ComboBox<>();
        private final Label warning = new Label();
        private final Button play = new Button(Messages.tr("command.game.play"));
        private final VBox details;
        private final Label empty = new Label(Messages.tr("game.none"));
        private final StackPane node;
        private final LatestOnly reads = new LatestOnly(Background::run, Platform::runLater);
        private @Nullable GameInfo shown;

        GamePanel(Runnable onPlay) {
            art.setPreserveRatio(true);
            UiZoom.bind(art.fitWidthProperty(), ART_SIZE);
            UiZoom.bind(art.fitHeightProperty(), ART_SIZE);
            initials.getStyleClass().add("game-initials");
            StackPane artBox = new StackPane(initials, art);
            artBox.getStyleClass().add("game-art");
            title.getStyleClass().add("game-title");
            title.setWrapText(true);
            system.getStyleClass().add("muted");
            system.setWrapText(true);
            file.getStyleClass().addAll("muted", "game-file");
            file.setWrapText(true);
            Label coreLabel = new Label(Messages.tr("game.core"));
            coreLabel.setLabelFor(core);
            core.setAccessibleText(Messages.tr("game.core"));
            core.setMaxWidth(Double.MAX_VALUE);
            core.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(@Nullable CoreInfo c) {
                    return c == null ? "" : c.label();
                }

                @Override
                public @Nullable CoreInfo fromString(String s) {
                    return null;
                }
            });
            core.valueProperty().addListener((o, was, now) -> showWarning());
            warning.getStyleClass().add("game-warning");
            warning.setWrapText(true);
            warning.managedProperty().bind(warning.textProperty().isNotEmpty());
            warning.visibleProperty().bind(warning.textProperty().isNotEmpty());
            play.setMaxWidth(Double.MAX_VALUE);
            play.setGraphic(Icons.line(Icons.RUN));
            play.setOnAction(e -> onPlay.run());
            details = UiZoom.vbox(8, artBox, title, system, coreLabel, core, warning, play, file);
            details.getStyleClass().add("game-panel");
            details.setAlignment(Pos.TOP_LEFT);
            empty.getStyleClass().add("muted");
            empty.setWrapText(true);
            node = new StackPane(details, empty);
            StackPane.setAlignment(empty, Pos.TOP_LEFT);
            show(null, null);
        }

        /** Shows {@code game}, read from {@code ctx} off the FX thread; null shows that none is selected. */
        void show(@Nullable RetroArchContext ctx, @Nullable Game game) {
            if (ctx == null || game == null) {
                reads.cancel();
                shown = null;
                details.setVisible(false);
                empty.setVisible(true);
                return;
            }
            GameInfo current = shown;
            if (current != null && current.game().equals(game)) return;
            reads.submit(() -> GameInfo.read(ctx, game), this::apply);
        }

        private void apply(GameInfo info) {
            shown = info;
            Game game = info.game();
            title.setText(game.title());
            system.setText(game.system().isBlank() ? Messages.tr("game.unknownSystem") : game.system());
            file.setText(game.rom().toString());
            Path boxart = info.boxart();
            Image image = boxart == null
                    ? null
                    : new Image(boxart.toUri().toString(), ART_SIZE * 2, ART_SIZE * 2, true, true, true);
            art.setImage(image);
            initials.setText(initialsOf(game.title()));
            initials.setVisible(image == null);
            core.getItems().setAll(info.cores());
            core.setValue(
                    info.preferred() != null
                            ? info.preferred()
                            : info.cores().isEmpty() ? null : info.cores().getFirst());
            showWarning();
            details.setVisible(true);
            empty.setVisible(false);
        }

        private void showWarning() {
            GameInfo info = shown;
            if (info == null) {
                warning.setText("");
                return;
            }
            CoreInfo chosen = core.getValue();
            if (chosen == null) {
                warning.setText(Messages.tr("game.noCore", info.game().extension()));
                return;
            }
            List<CoreInfo.Firmware> missing = info.missing().getOrDefault(chosen.id(), List.of());
            warning.setText(
                    missing.isEmpty()
                            ? ""
                            : Messages.tr(
                                    "game.missingBios",
                                    missing.stream()
                                            .map(CoreInfo.Firmware::path)
                                            .collect(Collectors.joining(", "))));
        }

        /** "Super Mario World" → "SM": what the art's place shows when there is no box art. Pure. */
        static String initialsOf(String title) {
            StringBuilder sb = new StringBuilder();
            for (String word : title.split("[^\\p{L}\\p{N}]+", -1)) {
                if (word.isEmpty()) continue;
                sb.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
                if (sb.length() >= 2) break;
            }
            return sb.toString();
        }

        /** The core chosen for the game shown, or null. */
        @Nullable
        CoreInfo selectedCore(@Nullable Game game) {
            GameInfo info = shown;
            return info != null && info.game().equals(game) ? core.getValue() : null;
        }

        /** Whether a read is still running: the tests wait on it. */
        boolean reading() {
            return reads.pending();
        }

        StackPane node() {
            return node;
        }

        ComboBox<CoreInfo> core() {
            return core;
        }

        Label title() {
            return title;
        }

        Label warning() {
            return warning;
        }

        Button play() {
            return play;
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
