package com.partida.ui;

import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import com.partida.library.GameTitle;
import org.jspecify.annotations.Nullable;

/**
 * The selected game in the browser (decision 3): its box art, its name and regions, Play with the ROM it
 * starts, and every ROM of the game, the hidden ones dimmed. Enter or a double-click on a ROM plays that ROM
 * ({@code library.playRom}); its right-click menu holds that command (MENU-05).
 */
final class GameDetails {

    static final double ART_WIDTH = 200;

    private final ImageView art = new ImageView();
    private final Label initials = new Label();
    private final Label title = new Label();
    private final Label meta = new Label();
    private final Button play = new Button(Messages.tr("command.game.play"));
    private final Label starts = new Label();
    private final Label romsHeading = new Label();
    private final ListView<GameTitle.Rom> roms = new ListView<>();
    private final Label none = new Label(Messages.tr("game.none"));
    private final VBox details;
    private final StackPane node;
    private final BoxArt boxArt;
    private @Nullable GameTitle shown;
    private @Nullable Game chosen;

    GameDetails(Runnable onPlay, Runnable onPlayRom, Supplier<CommandMenu> romMenu, BoxArt boxArt) {
        this.boxArt = boxArt;
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
        details = UiZoom.vbox(8, artBox, title, meta, play, starts, romsHeading, roms);
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
        initials.setText(BrowseRules.initials(game.title()));
        art.setImage(null);
        initials.setVisible(true);
        double width = ART_WIDTH * UiZoom.factor().get() * 2;
        boxArt.load(startRom.game(), width, image -> {
            if (shown != game) return; // another game is shown by now
            setArt(image);
        });
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
