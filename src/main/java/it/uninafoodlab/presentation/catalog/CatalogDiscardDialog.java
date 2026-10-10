package it.uninafoodlab.presentation.catalog;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;


final class CatalogDiscardDialog {
    private CatalogDiscardDialog() {}
    static boolean allowDiscard(VBox workspace, boolean changed, String subject, String subjectText) {
        if (!changed) return true;
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Modifiche non salvate");
        dialog.setHeaderText("Vuoi lasciare le modifiche alla " + (subject.equals("ricetta") ? "ricetta?" : "scheda ingrediente?"));
        it.uninafoodlab.presentation.common.DialogTheme.apply(dialog,
                workspace.getScene() == null ? null : workspace.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        var content = new Label("Le modifiche non salvate andranno perse.");
        content.setWrapText(true);
        content.setMinWidth(0);
        content.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        content.setPrefWidth(440);
        content.setMaxWidth(440);
        content.getStyleClass().add("section-title");
        var subjectName = new Label(subjectText);
        subjectName.setWrapText(true);
        subjectName.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        subjectName.setMaxWidth(440);
        subjectName.getStyleClass().add("form-helper");
        var details = new VBox(14, content, subjectName);
        details.setPadding(new javafx.geometry.Insets(12, 12, 20, 12));
        dialog.getDialogPane().setContent(details);
        dialog.getDialogPane().setPrefWidth(520);
        var keep = new ButtonType("Continua modifica", ButtonBar.ButtonData.CANCEL_CLOSE);
        var discard = new ButtonType("Scarta modifiche", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(keep, discard);
        var discardButton = (Button) dialog.getDialogPane().lookupButton(discard);
        discardButton.getStyleClass().addAll("course-button", "button-danger");
        discardButton.setDefaultButton(false);
        var keepButton = (Button) dialog.getDialogPane().lookupButton(keep);
        keepButton.getStyleClass().addAll("course-button", "button-secondary");
        keepButton.setDefaultButton(true);
        return dialog.showAndWait().filter(discard::equals).isPresent();
    }

}
