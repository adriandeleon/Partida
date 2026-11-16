package com.partida.ui;

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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameTitle;
import com.partida.library.RetroArchContext;
import com.partida.retroarch.CoreInfo;
import org.jspecify.annotations.Nullable;

/**
 * The selected game in the browser (decision 3): its box art, its name and regions, the core Play starts it
 * with and any BIOS file that core is missing, Play with the ROM it starts, and every ROM of the game, the
 * hidden ones dimmed. Enter or a double-click on a ROM plays that ROM ({@code library.playRom}); its
 * right-click menu holds that command (MENU-05). The cores are read off the FX thread (PERF-01).
 */
final class GameDetails {

    static final double ART_WIDTH = 200;

    private final ImageView art = new ImageView();
    private final Label initials = new Label();
    private final Label title = new Label();
    private final Label meta = new Label();
    private final ComboBox<CoreInfo> core = new ComboBox<>();
    private final Label warning = new Label();
    private final Button play = new Button(Messages.tr("command.game.play"));
    private final Label starts = new Label();
    private final Label romsHeading = new Label();
    private final ListView<GameTitle.Rom> roms = new ListView<>();
    private final Label none = new Label(Messages.tr("game.none"));
    private final VBox details;
    private final StackPane node;
    private final BoxArt boxArt;
    private final Supplier<@Nullable RetroArchContext> retroArch;
    private final LatestOnly reads = new LatestOnly(Background::run, Platform::runLater);
    private @Nullable GameTitle shown;
    private @Nullable Game chosen;
    /** The cores read for the ROM Play starts, or null while none is read. */
    private @Nullable Cores cores;

    GameDetails(
            Runnable onPlay,
            Runnable onPlayRom,
            Supplier<CommandMenu> romMenu,
            BoxArt boxArt,
            Supplier<@Nullable RetroArchContext> retroArch) {
        this.boxArt = boxArt;
        this.retroArch = retroArch;
        art.setPreserveRatio(true);
        UiZoom.bind(art.fitWidthProperty(), ART_WIDTH);
        UiZoom.bind(art.fitHeightProperty(), ART_WIDTH * 1.4);
        initials.getStyleClass().add("game-initials");
        StackPane artBox = new StackPane(initials, art);
        artBox.getStyleClass().add("game-art");
        title.getStyleClass().add("game-title");
        title.setWrapText(true);
        meta.getStyleClass().add("muted");
        meta.setWrapText(true);
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
        HBox coreRow = UiZoom.hbox(8, coreLabel, core);
        coreRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(core, Priority.ALWAYS);
        warning.getStyleClass().add("game-warning");
        warning.setWrapText(true);
        warning.managedProperty().bind(warning.textProperty().isNotEmpty());
        warning.visibleProperty().bind(warning.textProperty().isNotEmpty());
        play.setMaxWidth(Double.MAX_VALUE);
        play.setGraphic(Icons.line(Icons.RUN));
        play.setOnAction(e -> onPlay.run());
        starts.getStyleClass().add("muted");
        starts.setWrapText(true);
        romsHeading.getStyleClass().add("settings-card-heading");
        roms.getStyleClass().add("rom-list");
        roms.setAccessibleText(Messages.tr("details.roms"));
        roms.setCellFactory(v -> CommandMenu.onRow(new RomCell(), romMenu));
        RowContextMenu.install(roms);
        roms.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) onPlayRom.run();
        });
        roms.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                onPlayRom.run();
                e.consume();
            }
        });
        VBox.setVgrow(roms, Priority.ALWAYS);
        details = UiZoom.vbox(8, artBox, title, meta, coreRow, warning, play, starts, romsHeading, roms);
        details.getStyleClass().add("game-details");
        details.setAlignment(Pos.TOP_LEFT);
        UiZoom.pad(details, 12);
        none.getStyleClass().add("muted");
        none.setWrapText(true);
        UiZoom.pad(none, 12);
        node = new StackPane(details, none);
        StackPane.setAlignment(none, Pos.TOP_LEFT);
        node.getStyleClass().add("game-details-pane");
        show(null, null, false);
    }

    /** Shows {@code game}, Play starting {@code start}; null shows that no game is selected. */
    void show(@Nullable GameTitle game, @Nullable Game start, boolean showHidden) {
        shown = game;
        chosen = start;
        details.setVisible(game != null);
        none.setVisible(game == null);
        if (game == null) {
            roms.getItems().clear();
            art.setImage(null);
            reads.cancel();
            cores = null;
            return;
        }
        title.setText(game.title());
        String regions = String.join(", ", game.regions());
        String system = Chrome.systemName(game.system());
        meta.setText(regions.isEmpty() ? system : system + " · " + regions);
        GameTitle.Rom startRom = start == null ? game.best() : romOf(game, start);
        String what = BrowseRules.describe(startRom.name());
        starts.setText(
                Messages.tr("details.starts", what.isEmpty() ? startRom.game().contentName() : what));
        romsHeading.setText(Messages.tr("details.romsCount", game.roms().size()));
        if (!roms.getItems().equals(game.roms())) roms.getItems().setAll(game.roms());
        roms.refresh(); // the marker follows the ROM Play starts
        if (roms.getSelectionModel().isEmpty()
                || !game.roms().contains(roms.getSelectionModel().getSelectedItem()))
            roms.getSelectionModel().select(startRom);
        readCores(startRom.game());
        initials.setText(BrowseRules.initials(game.title()));
        art.setImage(null);
        initials.setVisible(true);
        double width = ART_WIDTH * UiZoom.factor().get() * 2;
        boxArt.load(startRom.game(), width, image -> {
            if (shown != game) return; // another game is shown by now
            setArt(image);
        });
    }

    /** The cores that run the ROM Play starts, read off the FX thread; one already read is kept. */
    private void readCores(Game rom) {
        Cores current = cores;
        if (current != null && current.game().equals(rom)) return;
        RetroArchContext ctx = retroArch.get();
        cores = null;
        core.getItems().clear();
        warning.setText("");
        if (ctx == null) {
            reads.cancel();
            return;
        }
        reads.submit(() -> Cores.read(ctx, rom), this::applyCores);
    }

    private void applyCores(Cores read) {
        if (!read.game().equals(chosenRom())) return; // another ROM is shown by now
        cores = read;
        core.getItems().setAll(read.cores());
        core.setValue(
                read.preferred() != null
                        ? read.preferred()
                        : read.cores().isEmpty() ? null : read.cores().getFirst());
        showWarning();
    }

    /** The ROM Play starts for the game shown, or null. */
    private @Nullable Game chosenRom() {
        GameTitle game = shown;
        if (game == null) return null;
        return chosen == null ? game.best().game() : romOf(game, chosen).game();
    }

    private void showWarning() {
        Cores read = cores;
        if (read == null) {
            warning.setText("");
            return;
        }
        CoreInfo picked = core.getValue();
        if (picked == null) {
            warning.setText(Messages.tr("game.noCore", read.game().extension()));
            return;
        }
        List<CoreInfo.Firmware> missing = read.missing().getOrDefault(picked.id(), List.of());
        warning.setText(
                missing.isEmpty()
                        ? ""
                        : Messages.tr(
                                "game.missingBios",
                                missing.stream().map(CoreInfo.Firmware::path).collect(Collectors.joining(", "))));
    }

    /** The core chosen for {@code rom}, or null when it isn't the ROM Play starts or its cores aren't read. */
    @Nullable
    CoreInfo selectedCore(@Nullable Game rom) {
        Cores read = cores;
        return read != null && read.game().equals(rom) ? core.getValue() : null;
    }

    /** Whether a read of the cores is still running: the tests wait on it. */
    boolean reading() {
        return reads.pending();
    }

    ComboBox<CoreInfo> core() {
        return core;
    }

    Label warning() {
        return warning;
    }

    /** The cores that run a ROM, the one it would start with, and each core's missing BIOS files. */
    record Cores(
            Game game,
            List<CoreInfo> cores,
            @Nullable CoreInfo preferred,
            Map<String, List<CoreInfo.Firmware>> missing) {

        /** Read from {@code ctx}: off the FX thread, since it reads core info and the system folder. */
        static Cores read(RetroArchContext ctx, Game game) {
            List<CoreInfo> cores = new ArrayList<>(ctx.compatibleCores(game));
            CoreInfo preferred = ctx.preferredCore(game).orElse(null);
            if (preferred != null && !cores.contains(preferred)) cores.addFirst(preferred);
            Map<String, List<CoreInfo.Firmware>> missing = new LinkedHashMap<>();
            for (CoreInfo c : cores) missing.put(c.id(), c.missingFirmware(ctx.systemDir()));
            return new Cores(game, List.copyOf(cores), preferred, missing);
        }
    }

    private void setArt(@Nullable Image image) {
        art.setImage(image);
        initials.setVisible(image == null);
    }

    private static GameTitle.Rom romOf(GameTitle game, Game start) {
        for (GameTitle.Rom rom : game.roms()) {
            if (rom.game().equals(start)) return rom;
        }
        return game.best();
    }

    /** The ROM selected in the list, or null: what {@code library.playRom} starts. */
    @Nullable
    Game selectedRom() {
        GameTitle.Rom rom = roms.getSelectionModel().getSelectedItem();
        return rom == null ? null : rom.game();
    }

    @Nullable
    GameTitle shown() {
        return shown;
    }

    ListView<GameTitle.Rom> roms() {
        return roms;
    }

    Button play() {
        return play;
    }

    Label starts() {
        return starts;
    }

    ImageView art() {
        return art;
    }

    StackPane node() {
        return node;
    }

    /** A ROM's row: the marker on the one Play starts, what its tags say and its file; made once (PERF-09). */
    private final class RomCell extends ListCell<GameTitle.Rom> {
        private final Label what = new Label();
        private final Label file = new Label();
        private final javafx.scene.Node marker = Icons.line(Icons.RUN);
        private final javafx.scene.layout.Region blank = new javafx.scene.layout.Region();
        private final VBox text = UiZoom.vbox(1, what, file);
        private final javafx.scene.layout.HBox row;

        RomCell() {
            file.getStyleClass().add("muted");
            UiZoom.bind(blank.prefWidthProperty(), 16);
            row = UiZoom.hbox(6, blank, text);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(GameTitle.@Nullable Rom rom, boolean empty) {
            super.updateItem(rom, empty);
            setText(null);
            getStyleClass().remove("hidden-rom");
            if (empty || rom == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            String described = BrowseRules.describe(rom.name());
            what.setText(described.isEmpty() ? rom.name().title() : described);
            file.setText(rom.game().contentName());
            boolean isStart = rom.game().equals(chosen);
            row.getChildren().set(0, isStart ? marker : blank);
            if (rom.name().hidden()) getStyleClass().add("hidden-rom");
            setGraphic(row);
            setAccessibleText(
                    (isStart ? Messages.tr("details.startsMark") + ", " : "") + what.getText() + ", " + file.getText());
        }
    }
}
