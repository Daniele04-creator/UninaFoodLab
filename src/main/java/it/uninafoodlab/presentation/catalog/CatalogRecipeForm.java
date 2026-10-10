package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.presentation.common.UserErrorMessages;

import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.IngredientSummary;
import it.uninafoodlab.application.catalog.RecipeIngredientInput;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;


final class CatalogRecipeForm {
    private final VBox workspace;
    private final javafx.scene.control.ScrollPane recipeScroll;
    private final TextField recipeName;
    private final TextArea recipeDescription;
    private final ComboBox<CategorySummary> categoryChoice;
    private final ComboBox<IngredientSummary> lineIngredient;
    private final TextField quantity;
    private final Label recipeNameError;
    private final Label recipeDescriptionError;
    private final Label recipeCategoryError;
    private final Label recipeIngredientError;
    private final Consumer<String> onError;
    CatalogRecipeForm(VBox workspace, javafx.scene.control.ScrollPane recipeScroll,
            TextField recipeName, TextArea recipeDescription, ComboBox<CategorySummary> categoryChoice,
            ComboBox<IngredientSummary> lineIngredient, TextField quantity, Label recipeNameError,
            Label recipeDescriptionError, Label recipeCategoryError, Label recipeIngredientError,
            Consumer<String> onError) {
        this.workspace = workspace;
        this.recipeScroll = recipeScroll;
        this.recipeName = recipeName;
        this.recipeDescription = recipeDescription;
        this.categoryChoice = categoryChoice;
        this.lineIngredient = lineIngredient;
        this.quantity = quantity;
        this.recipeNameError = recipeNameError;
        this.recipeDescriptionError = recipeDescriptionError;
        this.recipeCategoryError = recipeCategoryError;
        this.recipeIngredientError = recipeIngredientError;
        this.onError = onError;
    }

    List<Label> recipeErrors() {
        return List.of(recipeNameError, recipeDescriptionError, recipeCategoryError, recipeIngredientError);
    }

    CatalogDraftState.RecipeDraft draft(UUID id, List<RecipeIngredientInput> lines) {
        return new CatalogDraftState.RecipeDraft(id, recipeName.getText(), recipeDescription.getText(),
                "1", CatalogCategoryDialog.selectedCategoryId(categoryChoice), lines,
                lineIngredient.getValue() == null ? null : lineIngredient.getValue().id(), quantity.getText());
    }

    void clearRecipeErrors() {
        recipeErrors().forEach(label -> label.setText(""));
    }

    void recipeError(Label error, Node field, String message) {
        error.setText(message);
        onError.accept(message);
        javafx.application.Platform.runLater(() -> {
            if (workspace.getScene() == null) return;
            workspace.applyCss();
            workspace.layout();
            var content = recipeScroll.getContent();
            var fieldBounds = content.sceneToLocal(field.localToScene(field.getBoundsInLocal()));
            var errorBounds = content.sceneToLocal(error.localToScene(error.getBoundsInLocal()));
            double height = recipeScroll.getViewportBounds().getHeight();
            double range = content.getLayoutBounds().getHeight() - height;
            if (range > 0) {
                double top = Math.max(0, Math.min(fieldBounds.getMinY(), errorBounds.getMinY()) - 16);
                double bottom = Math.max(fieldBounds.getMaxY(), errorBounds.getMaxY()) + 16;
                double current = recipeScroll.getVvalue() * range;
                if (top < current) recipeScroll.setVvalue(top / range);
                else if (bottom > current + height) recipeScroll.setVvalue(Math.min(top, bottom - height) / range);
            }
            field.requestFocus();
        });
    }

    boolean validate(List<RecipeIngredientInput> lines) {
        var name = recipeName.getText().strip();
        if (name.isEmpty() || name.length() > 120) {
            recipeError(recipeNameError, recipeName, "Il nome della ricetta deve contenere da 1 a 120 caratteri.");
            return false;
        }
        if (categoryChoice.getValue() == null) {
            recipeError(recipeCategoryError, categoryChoice, "Scegli una categoria.");
            return false;
        }
        var description = recipeDescription.getText().strip();
        if (description.isEmpty() || description.length() > 4000) {
            recipeError(recipeDescriptionError, recipeDescription, "Inserisci una descrizione fino a 4000 caratteri.");
            return false;
        }
        if (!validatePendingIngredient()) return false;
        if (lines.isEmpty()) {
            recipeError(recipeIngredientError, lineIngredient, "Inserisci almeno un ingrediente, senza duplicati.");
            return false;
        }
        return true;
    }

    private boolean validatePendingIngredient() {
        if (quantity.getText().isBlank() && lineIngredient.getValue() == null) return true;
        if (lineIngredient.getValue() == null) {
            recipeError(recipeIngredientError, lineIngredient, "Scegli un ingrediente e aggiungi la riga prima di salvare.");
            return false;
        }
        try { CatalogQuantityPolicy.parse(quantity.getText()); }
        catch (IllegalArgumentException error) {
            recipeError(recipeIngredientError, quantity,
                    UserErrorMessages.validation(error, "Inserisci una quantità positiva, fino a 3 decimali."));
            return false;
        }
        recipeError(recipeIngredientError, quantity, "Aggiungi la riga ingrediente in corso prima di salvare.");
        return false;
    }
}
