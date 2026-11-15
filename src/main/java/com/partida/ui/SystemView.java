package com.partida.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.partida.i18n.Messages;
import com.partida.library.Game;
import org.jspecify.annotations.Nullable;

/**
 * A tab's content: one system's games, a row each, with the tab's find bar over the list (FIND-01). Enter or a
 * double-click runs {@code game.play} on the selected game; the row's right-click menu holds its commands
 * (MENU-05). A reload of the library hands the tab its system's games again, and the selected game stays
 * selected when it is still there.
 */
final class SystemView {

    private final String name;
    private final ListView<Game> list = new ListView<>();
    /** The tab's find bar over the games' titles (FIND-01): {@code find.show} opens it. */
    private final FindBar findBar;

    private final VBox node;

    /**
     * @param play runs {@code game.play}, the one way a game starts
     * @param rowMenu each row's right-click menu
     */
    SystemView(String name, List<Game> games, int selected, Runnable play, Supplier<CommandMenu> rowMenu) {
        this.name = name;
        list.getStyleClass().add("game-list");
        list.setAccessibleText(name); // a screen reader says which system (ACC-01)
        list.setPlaceholder(new Label(Messages.tr("systems.noGames")));
        list.setCellFactory(v -> CommandMenu.onRow(
                new ListCell<>() {
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
                        setGraphic(Icons.line(Icons.FILE));
                        setAccessibleText(game.system().isBlank() ? game.title() : game.title() + ", " + game.system());
                    }
                },
                rowMenu));
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) play.run();
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                play.run();
                e.consume();
            }
        });
        findBar = new FindBar(FindBar.Target.of(list, Game::title), "find.next", "find.previous");
        findBar.getStyleClass().add("document-find-bar");
        VBox.setVgrow(list, Priority.ALWAYS);
        node = new VBox(findBar, list);
        setGames(games);
        if (selected >= 0 && selected < games.size()) list.getSelectionModel().select(selected);
        else if (!games.isEmpty()) list.getSelectionModel().selectFirst();
    }

    /** The system's games after a reload; the selected game stays selected when it is still listed. */
    void setGames(List<Game> games) {
        Game was = selected();
        Path keep = was == null ? null : was.rom();
        list.getItems().setAll(games);
        if (keep == null) return;
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).rom().equals(keep)) {
                list.getSelectionModel().select(i);
                return;
            }
        }
    }

    /** The system's name: the tab's title and its identity, for the session and Reopen Closed Tab. */
    String title() {
        return name;
    }

    ListView<Game> list() {
        return list;
    }

    /** What the tab shows: the find bar, when open, over the games. */
    VBox node() {
        return node;
    }

    FindBar findBar() {
        return findBar;
    }

    @Nullable
    Game selected() {
        return list.getSelectionModel().getSelectedItem();
    }

    int selectedIndex() {
        return list.getSelectionModel().getSelectedIndex();
    }

    int size() {
        return list.getItems().size();
    }
}
