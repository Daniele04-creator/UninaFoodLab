package it.uninafoodlab.presentation.navigation;

import it.uninafoodlab.presentation.common.DialogTheme;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

final class UnsavedChangesDialog {
    private UnsavedChangesDialog() {}

    static void protectClose(Stage owner, java.util.function.Supplier<UnsavedChangesGuard> guard,
            Runnable dispose) {
        owner.setOnCloseRequest(event -> {
            if (!guard.get().allow(UnsavedChangesGuard.Action.CLOSE)) event.consume();
            else dispose.run();
        });
    }

    static boolean confirm(Stage owner, UnsavedChangesGuard.Action action) {
        var keep = new ButtonType("Continua modifica", ButtonBar.ButtonData.CANCEL_CLOSE);
        var proceed = proceedButton(action);
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, message(action), keep, proceed);
        dialog.setTitle("Modifiche non salvate");
        dialog.setHeaderText("Vuoi continuare senza salvare?");
        DialogTheme.apply(dialog, owner);
        var keepButton = (Button) dialog.getDialogPane().lookupButton(keep);
        keepButton.getStyleClass().add("button-secondary");
        keepButton.setDefaultButton(true);
        var proceedButton = (Button) dialog.getDialogPane().lookupButton(proceed);
        proceedButton.getStyleClass().add(action == UnsavedChangesGuard.Action.PASSWORD
                ? "button-primary" : "button-danger");
        proceedButton.setDefaultButton(false);
        var focused = owner.getScene() == null ? null : owner.getScene().getFocusOwner();
        var accepted = dialog.showAndWait().filter(proceed::equals).isPresent();
        if (!accepted && focused != null && focused.getScene() == owner.getScene()) focused.requestFocus();
        return accepted;
    }

    private static ButtonType proceedButton(UnsavedChangesGuard.Action action) {
        return new ButtonType(switch (action) {
            case LOGOUT -> "Esci e scarta modifiche";
            case CLOSE -> "Chiudi e scarta modifiche";
            case PASSWORD -> "Procedi al cambio password";
        }, ButtonBar.ButtonData.OK_DONE);
    }

    private static String message(UnsavedChangesGuard.Action action) {
        return action == UnsavedChangesGuard.Action.PASSWORD
                ? "Se cambi la password, dovrai accedere di nuovo e le modifiche non salvate andranno perse."
                : "Ci sono modifiche non salvate, anche nelle altre sezioni. Se esci, andranno perse.";
    }

    static void showBusy(Stage owner) {
        var dialog = new Alert(Alert.AlertType.INFORMATION,
                "Attendi che l’operazione in corso sia terminata, poi riprova.", ButtonType.CLOSE);
        dialog.setTitle("Operazione in corso");
        dialog.setHeaderText("L’operazione deve terminare prima di uscire");
        DialogTheme.apply(dialog, owner);
        dialog.showAndWait();
    }
}
