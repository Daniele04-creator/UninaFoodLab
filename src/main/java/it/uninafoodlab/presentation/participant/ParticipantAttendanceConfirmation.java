package it.uninafoodlab.presentation.participant;

import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;


final class ParticipantAttendanceConfirmation {

    private ParticipantAttendanceConfirmation() {}

    static boolean show(Node owner, String conflictMessage) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(conflictMessage, "conflictMessage");
        var cancel = ButtonType.CANCEL;
        var confirm = new ButtonType("Conferma comunque", ButtonBar.ButtonData.OK_DONE);
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Sovrapposizione di orario");
        dialog.setHeaderText("La presenza si sovrappone a un'altra sessione");
        var details = new Label(conflictMessage + "\nVuoi confermare comunque la presenza?");
        details.setWrapText(true);
        details.setMaxWidth(520);
        var content = new VBox(10, details);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, confirm);
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog,
                owner.getScene() == null ? null : owner.getScene().getWindow());
        it.uninafoodlab.presentation.common.DialogTheme.primary(dialog, confirm);
        return dialog.showAndWait().filter(confirm::equals).isPresent();
    }
}
