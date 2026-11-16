package com.partida.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import com.partida.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The tab switcher (TABS-09), after Editora's: the open files in tab order, the current one highlighted.
 * The pickers' keys move it ({@link PickerKeys}: the arrows, the page keys and the keymap's line chords, {@code C-n} / {@code C-p} in Emacs), Tab too; Enter, or releasing Ctrl (Cmd on macOS) after moving
 * with it held, activates; {@code C-g} or Esc cancels ({@link OverlayHost}); {@code C-d}, Backspace or Delete closes the
 * highlighted file. Its width is fixed to the longest path on each open, so it never resizes while you move.
 */
final class Switcher {

    static final double CELL_HEIGHT = 28;
    static final int MAX_VISIBLE = 12;
    static final double MIN_WIDTH = 360;

    private final DocumentTabs tabs;
    private final OverlayHost host;
    private final ListView<Tab> list = new ListView<>();
    private final Label path = new Label();
    private final VBox card;
    /**
     * Whether releasing Ctrl (or Cmd) activates: set once the highlight moved with it held, as Ctrl+Tab does.
     * Not on its own: {@code C-x C-b} is typed with Ctrl down, and letting go of it must not close the list.
     */
    private boolean armed;

    Switcher(DocumentTabs tabs, OverlayHost host) {
        this.tabs = tabs;
        this.host = host;
        list.setAccessibleText(Messages.tr("switcher.header")); // ACC-01
        UiZoom.bind(list.fixedCellSizeProperty(), CELL_HEIGHT); // sizes follow the UI zoom (WIN-12)
        list.setCellFactory(v -> new Row());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> updateFooter());
        list.setOnMouseClicked(e -> commit());

        Label header = new Label(Messages.tr("switcher.header"));
        header.getStyleClass().add("switcher-header");
        path.getStyleClass().add("switcher-path");
        path.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label(Messages.tr("switcher.hint"));
        hint.getStyleClass().add("hint");

        card = UiZoom.vbox(6, header, list, path, hint);
        card.getStyleClass().addAll("command-palette", "switcher");
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        card.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            KeyCode code = e.getCode();
            if (armed && (code == KeyCode.CONTROL || code == KeyCode.META || code == KeyCode.COMMAND)) {
                commit();
                e.consume();
            }
        });
    }

    /** Opens over the window with the current tab highlighted. */
    void show() {
        armed = false;
        list.getItems().setAll(tabs.tabs());
        int rows = Math.max(1, Math.min(list.getItems().size(), MAX_VISIBLE));
        list.setPrefHeight((rows * CELL_HEIGHT + 2) * UiZoom.factor().get());
        Tab current = tabs.selected();
        list.getSelectionModel().select(Math.max(0, list.getItems().indexOf(current)));
        double width = widthFor();
        card.setMinWidth(width);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        host.show(card, Pos.TOP_CENTER, UiZoom.insets(60, 0, 0, 0), null);
        list.requestFocus();
    }

    /** The card's width: the longest path among the open files, so it never resizes while you move. */
    double widthFor() {
        double max = 0;
        for (Tab tab : list.getItems()) max = Math.max(max, textWidth(pathOf(tab)));
        double zoom = UiZoom.factor().get(); // the path is measured in the zoomed font already (WIN-12)
        return Math.max(MIN_WIDTH * zoom, Math.ceil(max) + 48 * zoom);
    }

    private double textWidth(String text) {
        Text probe = new Text(text);
        probe.setFont(path.getFont());
        return probe.getLayoutBounds().getWidth();
    }

    private String pathOf(Tab tab) {
        SystemView view = tabs.view(tab);
        return view == null ? tabs.title(tab) : com.partida.i18n.Messages.tr("systems.games", view.size());
    }

    private java.util.function.@Nullable Supplier<com.partida.command.Keybindings> keys;
    private boolean mac;

    /** The keymap the moves come from: the pickers' keys, the same helper (QOPEN-12). */
    void keymap(java.util.function.Supplier<com.partida.command.Keybindings> keys, boolean mac) {
        this.keys = keys;
        this.mac = mac;
    }

    private void onKey(KeyEvent e) {
        boolean ctrl = e.isControlDown();
        KeyCode code = e.getCode();
        boolean held = ctrl || e.isMetaDown();
        PickerKeys.Move move = Picker.moveFor(e, keys == null ? null : keys.get(), mac);
        int size = list.getItems().size();
        int at = list.getSelectionModel().getSelectedIndex();
        // Where the key leads is PickerKeys' rule (QUAL-05); here it only acts.
        switch (PickerKeys.switcher(move, code.name(), e.isShiftDown(), ctrl)) {
            case NEXT -> {
                move(1);
                armed |= held;
            }
            case PREVIOUS -> {
                move(-1);
                armed |= held;
            }
            case PAGE_DOWN -> select(PickerKeys.page(at, size, MAX_VISIBLE, true));
            case LAST -> select(size - 1);
            case PAGE_UP -> select(PickerKeys.page(at, size, MAX_VISIBLE, false));
            case FIRST -> select(0);
            case OPEN -> commit();
            case CLOSE -> closeSelected();
            case NONE -> {
                return;
            }
        }
        e.consume();
    }

    private void select(int index) {
        list.getSelectionModel().select(index);
        list.scrollTo(list.getSelectionModel().getSelectedIndex());
    }

    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) return;
        list.getSelectionModel().select(TabStrip.cycle(list.getSelectionModel().getSelectedIndex(), size, delta));
        list.scrollTo(list.getSelectionModel().getSelectedIndex());
    }

    /** Activates the highlighted tab and closes the switcher. */
    void commit() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        armed = false;
        host.hide();
        if (tab != null) tabs.tabPane().getSelectionModel().select(tab);
    }

    /** Closes the highlighted file; the switcher stays open on the next one, at the same width. */
    void closeSelected() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        if (tab == null) return;
        int at = list.getSelectionModel().getSelectedIndex();
        tabs.closeTab(tab);
        list.getItems().remove(tab);
        if (!list.getItems().isEmpty())
            list.getSelectionModel().select(Math.min(at, list.getItems().size() - 1));
    }

    private void updateFooter() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        path.setText(tab == null ? " " : pathOf(tab));
    }

    ListView<Tab> list() {
        return list;
    }

    VBox card() {
        return card;
    }

    /** A row: the file's name, with the unsaved marker the tab strip shows. */
    private final class Row extends ListCell<Tab> {
        @Override
        protected void updateItem(@Nullable Tab tab, boolean empty) {
            super.updateItem(tab, empty);
            if (empty || tab == null) {
                setText(null);
                return;
            }
            setText((tabs.isDirty(tab) ? "• " : "") + tabs.title(tab));
        }
    }
}
