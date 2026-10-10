package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeSummary;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

final class CatalogRecipeLayout {
    private CatalogRecipeLayout() {}

    static void showFilteredRecipes(ListView<RecipeSummary> recipeList, List<RecipeSummary> filtered,
            RecipeSummary selected, UUID editingRecipe) {
        var selectedId = selected == null ? editingRecipe : selected.id();
        recipeList.getItems().setAll(filtered);
        filtered.stream().filter(recipe -> recipe.id().equals(selectedId)).findFirst()
                .ifPresent(recipe -> recipeList.getSelectionModel().select(recipe));
    }

    static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    static void bindBrowserWidth(VBox browser) {
        browser.setMaxWidth(Double.MAX_VALUE);
        browser.setMinWidth(0);
        HBox.setHgrow(browser, Priority.ALWAYS);
    }

    static void bindReadTitle(TextField name, Label title, BooleanSupplier editing) {
        name.textProperty().addListener((observable, before, after) -> {
            if (!editing.getAsBoolean()) title.setText(after.isBlank() ? "Seleziona una ricetta" : after);
        });
    }

    static void showEditingStyle(VBox workspace, boolean editing) {
        if (editing) {
            if (!workspace.getStyleClass().contains("catalog-editing")) workspace.getStyleClass().add("catalog-editing");
        } else {
            workspace.getStyleClass().remove("catalog-editing");
        }
    }

    static void showHeading(VBox workspace, Label title, Label state, TextField name,
            UUID recipeId, boolean editing) {
        showEditingStyle(workspace, editing);
        if (editing) {
            title.setText(recipeId == null ? "Nuova ricetta" : "Modifica ricetta");
            state.setText(recipeId == null ? "Bozza non salvata"
                    : "Salva nel ricettario. Per aggiornare una sessione usa Aggiorna dosi dal ricettario prima della chiusura.");
        } else {
            title.setText(name.getText());
            state.setText("");
        }
        show(state, !state.getText().isBlank());
    }


    static void layoutRecipeColumns(GridPane recipeColumns, VBox recipeReadView,
            VBox recipeEditView, VBox recipeIngredientsPanel) {
        boolean reading = recipeReadView.isManaged();
        boolean sideBySide = recipeColumns.getWidth() >= 730;
        configureColumns(recipeColumns, reading, sideBySide);
        GridPane.setConstraints(recipeReadView, sideBySide ? 1 : 0, sideBySide ? 0 : 1);
        GridPane.setConstraints(recipeEditView, 0, 0);
        GridPane.setConstraints(recipeIngredientsPanel, reading ? 0 : (sideBySide ? 1 : 0),
                reading || sideBySide ? 0 : 1);
        GridPane.setValignment(recipeReadView, javafx.geometry.VPos.TOP);
        GridPane.setValignment(recipeEditView, javafx.geometry.VPos.TOP);
        GridPane.setValignment(recipeIngredientsPanel, javafx.geometry.VPos.TOP);
    }

    private static void configureColumns(GridPane recipeColumns, boolean reading, boolean sideBySide) {
        var first = new ColumnConstraints();
        first.setHgrow(Priority.ALWAYS);
        first.setMinWidth(0);
        first.setPercentWidth(sideBySide ? (reading ? 40 : 55) : 100);
        recipeColumns.getColumnConstraints().setAll(first);
        if (sideBySide) {
            var second = new ColumnConstraints();
            second.setHgrow(Priority.ALWAYS);
            second.setMinWidth(0);
            second.setPercentWidth(reading ? 60 : 45);
            recipeColumns.getColumnConstraints().add(second);
        }
    }

}
