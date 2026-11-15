package com.partida.ui;

import java.io.File;
import java.nio.file.Path;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/** The real desktop: JavaFX's host services and native file choosers. */
public final class FxHost implements Host {

    private final HostServices hostServices;

    public FxHost(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @Override
    public void openUrl(String url) {
        hostServices.showDocument(url);
    }

    @Override
    public @Nullable Path chooseSaveFile(Window owner, String title, String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialName);
        File file = chooser.showSaveDialog(owner);
        return file == null ? null : file.toPath();
    }

    @Override
    public @Nullable Path chooseOpenFile(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        File file = chooser.showOpenDialog(owner);
        return file == null ? null : file.toPath();
    }

    @Override
    public @Nullable Path chooseFolder(Window owner, String title) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle(title);
        File folder = chooser.showDialog(owner);
        return folder == null ? null : folder.toPath();
    }

    @Override
    public boolean confirm(@Nullable Window owner, Confirmations.Question question) {
        return Confirm.ask(question, owner);
    }

    @Override
    public void exit() {
        Platform.exit();
    }
}
