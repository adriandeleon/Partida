package com.partida.ui;

import java.util.List;

import com.partida.config.ConfigImport;
import com.partida.i18n.Messages;

/**
 * The words of every question the app asks before it removes or overwrites something the user made,
 * as pure code, so a unit test reads them without a screen (Pauta's Confirm dialog template). The
 * question names the thing and what happens to it; the action button carries the action's own verb,
 * never OK or Yes. {@link Confirm} turns one into a dialog.
 */
public final class Confirmations {

    /**
     * One question: the window title, the question itself, the action button's verb, and the parts the
     * user can tick (none for a plain question).
     */
    public record Question(String title, String message, String action, List<Choice> choices) {
        public Question(String title, String message, String action) {
            this(title, message, action, List.of());
        }
    }

    /** A part the action applies to, ticked or not; the dialog's check box writes {@link #ticked}. */
    public static final class Choice {
        private final String label;
        private boolean ticked;

        public Choice(String label, boolean ticked) {
            this.label = label;
            this.ticked = ticked;
        }

        public String label() {
            return label;
        }

        public boolean ticked() {
            return ticked;
        }

        public void tick(boolean ticked) {
            this.ticked = ticked;
        }
    }

    private Confirmations() {}

    /**
     * Before Import replaces parts of the config folder with a {@code .zip}'s: one choice per part the zip
     * holds, in {@code parts} order, all ticked.
     */
    public static Question importConfig(String fileName, List<ConfigImport.Part> parts) {
        List<Choice> choices = parts.stream()
                .map(p -> new Choice(Messages.tr("confirm.import.part." + partKey(p)), true))
                .toList();
        return new Question(
                Messages.tr("confirm.import.title"),
                Messages.tr("confirm.import.question", fileName),
                Messages.tr("confirm.import.action"),
                choices);
    }

    private static String partKey(ConfigImport.Part part) {
        return switch (part) {
            case PREFERENCES -> "preferences";
            case KEY_BINDINGS -> "keyBindings";
            case SYNC -> "sync";
            case OTHER_FILES -> "otherFiles";
        };
    }

    /**
     * Before a games folder leaves the library (CONF-01): the user added it and picked its core. Its files stay
     * on disk; only the entry, and the core it was given, go.
     */
    public static Question removeFolder(String path) {
        return new Question(
                Messages.tr("confirm.removeFolder.title"),
                Messages.tr("confirm.removeFolder.question", path),
                Messages.tr("confirm.removeFolder.action"));
    }

    /**
     * Before Restore Default Toolbar throws away the toolbar the user arranged (CONF-01): its buttons, their
     * order and its gaps go, and the app's own come back.
     */
    public static Question restoreToolbar() {
        return new Question(
                Messages.tr("confirm.restoreToolbar.title"),
                Messages.tr("confirm.restoreToolbar.question"),
                Messages.tr("confirm.restoreToolbar.action"));
    }

    /** Before a binding the user set is removed and the keymap's own applies again. */
    public static Question resetBinding(String commandTitle, String userChord, String keymapChord) {
        return new Question(
                Messages.tr("confirm.resetBinding.title"),
                keymapChord.isEmpty()
                        ? Messages.tr("confirm.resetBinding.questionUnbound", commandTitle, userChord)
                        : Messages.tr("confirm.resetBinding.question", commandTitle, userChord, keymapChord),
                Messages.tr("confirm.resetBinding.action"));
    }
}
