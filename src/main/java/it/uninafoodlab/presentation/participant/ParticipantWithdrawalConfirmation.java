package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.presentation.common.DialogTheme;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;


final class ParticipantWithdrawalConfirmation {
    private ParticipantWithdrawalConfirmation() {}

    static boolean show(Node owner, String courseTitle) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(courseTitle, "courseTitle");
        var keep = new ButtonType("Mantieni iscrizione", ButtonBar.ButtonData.CANCEL_CLOSE);
        var confirm = new ButtonType("Conferma ritiro", ButtonBar.ButtonData.OK_DONE);
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Ritira iscrizione");
        dialog.setHeaderText("Ritirare l'iscrizione?");
        var content = new VBox(12,
                paragraph("Stai per ritirarti da «" + courseTitle + "»."),
                paragraph("Le adesioni confermate alle sessioni future saranno annullate e i posti liberati. "
                        + "Potrai continuare a consultare lo storico del corso."),
                paragraph("Se ti iscriverai di nuovo, dovrai confermare nuovamente le adesioni."));
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setAccessibleText("Conferma il ritiro dell'iscrizione al corso " + courseTitle);
        dialog.getDialogPane().getButtonTypes().setAll(keep, confirm);
        var scene = owner.getScene();
        var previousFocus = scene == null ? null : scene.getFocusOwner();
        DialogTheme.apply(dialog, scene == null ? null : scene.getWindow());
        var keepButton = (Button) dialog.getDialogPane().lookupButton(keep);
        var confirmButton = (Button) dialog.getDialogPane().lookupButton(confirm);
        keepButton.getStyleClass().add("button-secondary");
        confirmButton.getStyleClass().add("button-danger");
        keepButton.setDefaultButton(true);
        confirmButton.setDefaultButton(false);
        keepButton.setAccessibleText("Mantieni l'iscrizione e le adesioni al corso");
        confirmButton.setAccessibleText("Conferma il ritiro e annulla le adesioni future");
        dialog.setOnShown(event -> keepButton.requestFocus());
        try {
            return dialog.showAndWait().filter(confirm::equals).isPresent();
        } finally {
            if (previousFocus != null) previousFocus.requestFocus();
        }
    }

    private static Label paragraph(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(520);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return label;
    }
}
