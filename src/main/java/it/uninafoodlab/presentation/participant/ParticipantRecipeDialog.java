package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.presentation.common.DialogTheme;
import it.uninafoodlab.presentation.common.QuantityFormat;
import it.uninafoodlab.presentation.common.SessionRecipeReading;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;


final class ParticipantRecipeDialog {
    private ParticipantRecipeDialog() {}

    static void show(Window owner, PublishedSessionRecipeSummary recipe) {
        var dialog = new Dialog<Void>();
        dialog.setTitle("Ricette della sessione");
        dialog.setHeaderText(recipe.name());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogTheme.apply(dialog, owner);
        dialog.getDialogPane().getStyleClass().add("recipe-reading-dialog");
        var rows = recipe.ingredients().stream().map(ingredient -> SessionRecipeReading.ingredient(
                ingredient.name(), QuantityFormat.format(ingredient.quantity()) + " " + ingredient.unit().symbol())).toList();
        var scroll = SessionRecipeReading.content(recipe.description(), rows);
        dialog.getDialogPane().setPrefWidth(660);dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }
}
