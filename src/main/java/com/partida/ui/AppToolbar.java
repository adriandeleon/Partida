package com.partida.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.partida.command.Command;
import org.jspecify.annotations.Nullable;

/**
 * The main toolbar (Pauta's Toolbars and tooltips): a fast path to commands that are also in the menus
 * and the palette, never the only way to one (TBAR-08). Every button is a command, so its glyph, its
 * name and its chord come from the registry, {@link Icons} and the active keymap.
 *
 * <p>Two containers side by side, as in Editora (TBAR-15): the <b>icon cluster</b>, a
 * {@link ToolBar} whose last icons go into its overflow chevron when the window narrows, and the
 * <b>pinned tail</b>, which keeps Settings at the right end at any width (TBAR-16).
 *
 * <p>What each rule asks of it, and where:
 *
 * <ul>
 *   <li>TBAR-01: every button is named by {@link Icons#toolbarButton}, tooltip and accessible text one
 *       string; TBAR-02: out of the Tab order, the same helper.
 *   <li>TBAR-03: the name is {@link Icons#withChord} of the title and the keymap's chord, rebuilt by
 *       {@link #refresh} on every keymap change.
 *   <li>TBAR-04: groups are {@link Separator}s whose line the stylesheet makes transparent.
 *   <li>TBAR-05: {@link #setGroupVisible} and every other visibility change end in one pass over the
 *       separators ({@link Chrome#separatorsShown}), so no gap stands on its own.
 *   <li>TBAR-06: the bar sits on the menu bar's ground with no rule between them ({@code .chrome-band}).
 *   <li>TBAR-07: a button whose overlay is up wears {@code :open} ({@link OverlayToggles}), and so does the
 *       Simple mode toggle while the mode is on ({@link #setSimple}).
 *   <li>TBAR-10: one right-click menu, reused, closed by a press anywhere ({@link CommandMenu},
 *       {@link ContextMenuDismissal}).
 *   <li>TBAR-16: the tail is the Simple mode toggle, then Settings, after a 40 px gap, the
 *       same whether the mode is on or off.
 *   <li>TBAR-17: a button is disabled while its command can't run; {@link #refresh} reads
 *       that from the command and runs on the events that change it, never on a timer.
 *   <li>TBAR-19: the row keeps its height when a control hides ({@link #steadyHeight}), so
 *       the tabs under it never move when Simple mode hides groups.
 * </ul>
 *
 * <p><b>Simple mode</b> keeps only the buttons {@link #SIMPLE_KEEPS} names, the toggle and Settings; it
 * hides, it never removes, so turning it off brings back the bar as it was.
 *
 * <p><b>Customizable</b> (TBAR-09): the cluster is the user's, from {@link #AVAILABLE}, in the settings'
 * {@code toolbarLayout} ({@link ToolbarLayout}), which Settings → General edits: reorder, remove, add, and
 * restore the default {@link #CLUSTER}. The pinned tail is not in it.
 *
 * <p>An app replaces {@link #CLUSTER}, {@link #AVAILABLE} and {@link #TAIL} with its own commands; each one
 * needs a glyph in {@link Icons} and a row in {@link AppMenus} ({@code AppToolbarFxTest} checks both).
 */
final class AppToolbar {

    /** The icon cluster, in groups; {@code null} is a group gap. */
    static final List<@Nullable String> CLUSTER = Collections.unmodifiableList(Arrays.<@Nullable String>asList(
            "palette.show",
            null,
            "game.play",
            "game.stop",
            null,
            "library.refresh",
            "library.addFolder",
            null,
            "settings.theme",
            "settings.keymap",
            null,
            "sync.now",
            "view.messageLog"));

    /** The Simple mode toggle: the way into the mode and, lit, the way back out. */
    static final String SIMPLE_TOGGLE = "view.toggleSimpleMode";

    /** The pinned tail: the Simple mode toggle, then Settings closing the bar at its right end (TBAR-16). */
    static final List<String> TAIL = List.of(SIMPLE_TOGGLE, "app.settings");

    /** The blank before the tail's pair, Pauta's tail gap (TBAR-16); a gap, never a separator. */
    static final double TAIL_GAP = 40;

    /**
     * Every command the user can put on the bar (TBAR-09): the default cluster's and a few more, each with a
     * glyph of its own and a row in the full menus (TBAR-08).
     */
    static final List<String> AVAILABLE = List.of(
            "palette.show",
            "game.play",
            "game.stop",
            "library.refresh",
            "library.addFolder",
            "library.filter",
            "settings.theme",
            "settings.keymap",
            "sync.now",
            "view.messageLog",
            "tool.systems",
            "tool.game",
            "tool.retroarch",
            "view.welcome",
            "help.documentation");

    /**
     * The buttons Simple mode keeps, wherever the user put them: the palette and Play and Stop, and any of the
     * few others with a row in Simple mode's menus (TBAR-08). Every other button hides.
     */
    static final java.util.Set<String> SIMPLE_KEEPS = java.util.Set.of(
            "palette.show",
            "game.play",
            "game.stop",
            "library.filter",
            "tool.systems",
            "tool.game",
            "tool.retroarch",
            "view.welcome",
            "help.documentation");

    /** The bar's right-click menu: its commands (MENU-05), one menu reused (TBAR-10). */
    static final List<@Nullable String> MENU = Collections.unmodifiableList(Arrays.<@Nullable String>asList(
            "view.toggleToolbar", SIMPLE_TOGGLE, null, "view.customizeToolbar", "view.restoreToolbar"));

    private final AppServices services;
    private final ToolBar bar = new ToolBar();
    private final HBox tail = new HBox();
    private final HBox row;
    /** The buttons on the bar now, the cluster's in order and then the tail's. */
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    /** Every button made, so a layout change reuses a button rather than making it again. */
    private final Map<String, Button> made = new LinkedHashMap<>();

    private final OverlayToggles toggles;
    /** The cluster as the settings have it (TBAR-09), {@code null} a gap. */
    private List<@Nullable String> layout;

    private boolean simple;
    private final CommandMenu menu;
    private @Nullable CommandMenu overflowMenu;

    AppToolbar(AppServices services, OverlayToggles toggles) {
        this.services = services;
        this.toggles = toggles;
        this.layout = ToolbarLayout.read(services.settings().toolbarLayout, CLUSTER, AVAILABLE);
        fill();
        Region gap = new Region();
        UiZoom.bind(gap.minWidthProperty(), TAIL_GAP); // follows the UI zoom (WIN-12)
        UiZoom.bind(gap.prefWidthProperty(), TAIL_GAP);
        gap.getStyleClass().add("toolbar-gap");
        tail.getChildren().add(gap);
        for (String id : TAIL) tail.getChildren().add(make(id));
        tail.getStyleClass().add("toolbar-tail");
        tail.setAlignment(Pos.CENTER_RIGHT);
        tail.setMinWidth(Region.USE_PREF_SIZE);
        bar.setMinWidth(0);
        HBox.setHgrow(bar, Priority.ALWAYS);
        row = new HBox(bar, tail) {
            // TBAR-19: never shorter than the bar with every control shown, whatever is hidden now.
            @Override
            protected double computeMinHeight(double width) {
                return Math.max(super.computeMinHeight(width), steadyHeight());
            }

            @Override
            protected double computePrefHeight(double width) {
                return Math.max(super.computePrefHeight(width), steadyHeight());
            }
        };
        row.getStyleClass().add("toolbar-row");
        row.setAlignment(Pos.CENTER_LEFT);

        menu = new CommandMenu(services, MENU);
        row.setOnContextMenuRequested(e -> menu.show(row, e));
        // MENU-15: the overflow chevron opens a menu of command rows, each with its chord, in place of the
        // stock one, whose rows are the bare icon buttons. A press or key on the chevron is taken before the
        // skin's own handler sees it.
        bar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Node chevron = chevron(e.getTarget());
            if (chevron == null || e.getButton() != MouseButton.PRIMARY) return;
            e.consume();
            showOverflow(chevron);
        });
        bar.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            Node chevron = chevron(e.getTarget());
            if (chevron == null) return;
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.DOWN) {
                e.consume();
                showOverflow(chevron);
            }
        });

        services.onKeybindingsChanged(this::refresh);
        // A setting can change what a command can do (Sync Now waits for a repository): read it again. The
        // layout too: a change in Settings reaches the bar at once (TBAR-09).
        services.store().addListener(s -> {
            List<@Nullable String> saved = ToolbarLayout.read(s.toolbarLayout, CLUSTER, AVAILABLE);
            if (!saved.equals(layout)) setLayout(saved);
            refresh();
        });
        refresh();
    }

    /** The cluster's layout now (TBAR-09). */
    List<@Nullable String> layout() {
        return new ArrayList<>(layout);
    }

    /** Lays the cluster out again as {@code layout} has it, reusing its buttons (TBAR-09). */
    void setLayout(List<@Nullable String> layout) {
        this.layout = new ArrayList<>(layout);
        fill();
        setSimple(simple);
        refresh();
    }

    /** Puts the layout's buttons and gaps in the cluster, in order. */
    private void fill() {
        buttons.clear();
        List<Node> items = new ArrayList<>();
        for (String id : layout) {
            items.add(id == null ? new Separator(Orientation.VERTICAL) : make(id));
        }
        bar.getItems().setAll(items);
        for (String id : TAIL) {
            Button pinned = made.get(id);
            if (pinned != null) buttons.put(id, pinned);
        }
    }

    /** The button for {@code id}, made on first use and reused after. */
    private Button make(String id) {
        Button reused = made.get(id);
        if (reused != null) {
            reused.setVisible(true);
            reused.setManaged(true);
            buttons.put(id, reused);
            return reused;
        }
        String path = Icons.forCommand(id);
        Node glyph = Icons.toolbarLine(path == null ? "" : path);
        Button button = Icons.toolbarButton(glyph, id, () -> services.commands().run(id));
        button.setId("toolbar-" + id);
        // The Simple mode toggle is lit by the mode, not by an overlay (setSimple), so an overlay opening or
        // closing never clears it.
        if (!SIMPLE_TOGGLE.equals(id)) toggles.track(button, id);
        buttons.put(id, button);
        made.put(id, button);
        return button;
    }

    /** The overflow chevron {@code target} is in, or null: the stock skin's {@code .tool-bar-overflow-button}. */
    private @Nullable Node chevron(@Nullable Object target) {
        for (Node n = target instanceof Node node ? node : null; n != null && n != bar; n = n.getParent()) {
            if (n.getStyleClass().contains("tool-bar-overflow-button")) return n;
        }
        return null;
    }

    /**
     * The cluster's commands whose buttons don't fit: the skin moves an overflowed button out of the bar's row
     * into a second box that it keeps hidden and unmanaged until its own menu shows, so a shown button whose
     * box is hidden is one. A group Simple mode hides is not shown, and not listed.
     * Gaps stay between groups that both have a row.
     */
    List<@Nullable String> overflowed() {
        List<@Nullable String> ids = new ArrayList<>();
        boolean gap = false;
        for (String id : layout) {
            if (id == null) {
                gap = !ids.isEmpty();
                continue;
            }
            Button button = buttons.get(id);
            if (button == null || !button.isVisible() || inRow(button)) continue;
            if (gap) ids.add(null);
            gap = false;
            ids.add(id);
        }
        return ids;
    }

    private boolean inRow(Node button) {
        Parent box = button.getParent();
        return box != null && box.getParent() == bar && box.isVisible() && box.isManaged();
    }

    /** Opens the overflow's menu under the chevron: the overflowed commands, each row with its chord (MENU-15). */
    void showOverflow(Node chevron) {
        List<@Nullable String> ids = overflowed();
        if (ids.isEmpty()) return;
        CommandMenu shown = new CommandMenu(services, ids);
        overflowMenu = shown;
        shown.menu().getStyleClass().add("toolbar-overflow-menu");
        shown.menu().show(chevron, Side.BOTTOM, 0, 0);
    }

    /** The overflow menu last opened, or null: for the tests. */
    @Nullable
    CommandMenu overflowMenu() {
        return overflowMenu;
    }

    /** The whole bar: the cluster and the tail, in one row. */
    HBox node() {
        return row;
    }

    ToolBar bar() {
        return bar;
    }

    HBox tail() {
        return tail;
    }

    CommandMenu menu() {
        return menu;
    }

    /** The button that runs {@code commandId}, or null when the bar has none. */
    @Nullable
    Button button(String commandId) {
        return buttons.get(commandId);
    }

    List<Button> buttons() {
        return List.copyOf(buttons.values());
    }

    /**
     * Names every button again from its command's title and chord (TBAR-03), sets whether it can run
     * (TBAR-17), and tidies the gaps (TBAR-05). Runs on a keymap change and a settings change; an app calls
     * it too from whatever event changes what a command on the bar can do.
     */
    void refresh() {
        buttons.forEach((id, button) -> {
            Command command = services.commands().get(id);
            String title = command == null ? id : command.title();
            Icons.name(button, Icons.withChord(title, services.keybindings().label(id)));
            button.setDisable(command == null || command.unavailableReason() != null);
        });
        collapseSeparators();
    }

    /**
     * Shows or hides the group that holds {@code commandId} (as Simple mode or a missing tool would), then
     * tidies the gaps it leaves (TBAR-05).
     */
    void setGroupVisible(String commandId, boolean visible) {
        List<Node> items = bar.getItems();
        int at = items.indexOf(buttons.get(commandId));
        if (at < 0) return;
        int start = at;
        while (start > 0 && !(items.get(start - 1) instanceof Separator)) start--;
        for (int i = start; i < items.size() && !(items.get(i) instanceof Separator); i++) {
            items.get(i).setVisible(visible);
            items.get(i).setManaged(visible);
        }
        collapseSeparators();
    }

    /**
     * Simple mode on or off: hides or shows the groups in {@link #SIMPLE_HIDES} (their gaps tidied, TBAR-05)
     * and lights the toggle while the mode is on (TBAR-07). The toggle and Settings stay where they are
     * (TBAR-16), and the row keeps its height (TBAR-19).
     */
    void setSimple(boolean on) {
        simple = on;
        Button toggle = buttons.get(SIMPLE_TOGGLE);
        if (toggle != null) toggle.pseudoClassStateChanged(OverlayToggles.OPEN, on);
        for (String id : layout) {
            Button button = id == null ? null : buttons.get(id);
            if (button == null) continue;
            boolean shown = !on || SIMPLE_KEEPS.contains(id);
            button.setVisible(shown);
            button.setManaged(shown);
        }
        collapseSeparators();
    }

    /**
     * The row's height with every control shown (TBAR-19): the tallest item in the cluster or the tail,
     * hidden ones included, inside each container's insets. A hidden item still has a preferred height, so
     * the row never shrinks when Simple mode (or a missing tool) hides the tallest one.
     */
    double steadyHeight() {
        return Math.max(tallest(bar.getItems(), bar), tallest(tail.getChildren(), tail));
    }

    private static double tallest(List<Node> items, Region container) {
        double tallest = 0;
        for (Node item : items) {
            if (!(item instanceof Separator)) tallest = Math.max(tallest, item.prefHeight(-1));
        }
        return Math.ceil(tallest + container.snappedTopInset() + container.snappedBottomInset());
    }

    /** One pass over the separators: none leads, none trails, none follows another (TBAR-05). */
    void collapseSeparators() {
        List<Node> items = bar.getItems();
        List<@Nullable Boolean> shape = new ArrayList<>();
        for (Node item : items) shape.add(item instanceof Separator ? null : item.isVisible());
        boolean[] shown = Chrome.separatorsShown(shape);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Separator separator) {
                separator.setVisible(shown[i]);
                separator.setManaged(shown[i]);
            }
        }
    }
}
