package it.uninafoodlab.presentation.common;

import java.util.Objects;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

public final class DialogTheme {
    private DialogTheme() {}

    public static void apply(Dialog<?> dialog, Window owner) {
        if (owner != null && dialog.getOwner() == null) dialog.initOwner(owner);
        var pane = dialog.getDialogPane();
        for (var name : new String[] {"application.css", "dialogs.css"}) {
            var resource = Objects.requireNonNull(DialogTheme.class.getResource(
                    "/it/uninafoodlab/presentation/styles/" + name));
            if (!pane.getStylesheets().contains(resource.toExternalForm())) {
                pane.getStylesheets().add(resource.toExternalForm());
            }
        }
        if (!pane.getStyleClass().contains("studio-dialog")) pane.getStyleClass().add("studio-dialog");
        for (var node : pane.lookupAll(".button-bar")) {
            if (node instanceof ButtonBar bar) bar.setButtonOrder("+CO");
        }
        translateButtons(dialog);
        pane.getButtonTypes().addListener((javafx.collections.ListChangeListener<ButtonType>) change -> translateButtons(dialog));
    }

    private static void translateButtons(Dialog<?> dialog) {
        var names = java.util.Map.of(ButtonType.CANCEL, "Annulla", ButtonType.CLOSE, "Chiudi",
                ButtonType.YES, "Sì", ButtonType.NO, "No");
        for (var type : dialog.getDialogPane().getButtonTypes()) {
            if (names.containsKey(type) && dialog.getDialogPane().lookupButton(type) instanceof javafx.scene.control.Button button) {
                button.setText(names.get(type));
            }
        }
    }

    public static void primary(Dialog<?> dialog, ButtonType type) {
        var button = dialog.getDialogPane().lookupButton(type);
        if (button != null && !button.getStyleClass().contains("button-primary")) {
            button.getStyleClass().add("button-primary");
        }
    }

    public static javafx.scene.control.Button copyText(String text) {
        var button = new javafx.scene.control.Button("Copia testo");
        button.getStyleClass().add("button-secondary");
        button.setAccessibleText("Copia il testo completo negli appunti");
        button.setOnAction(event -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(text);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            button.setText("Testo copiato");
        });
        return button;
    }
}
