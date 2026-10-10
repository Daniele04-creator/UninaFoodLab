package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.IngredientSummary;
import it.uninafoodlab.application.catalog.RecipeIngredientInput;
import it.uninafoodlab.presentation.common.QuantityFormat;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;
import java.util.function.Consumer;


final class CatalogIngredientRows {
    private final VBox ingredientLines;
    private final VBox ingredientReadLines;
    private final javafx.collections.ObservableList<RecipeIngredientInput> lines;
    private final java.util.function.BooleanSupplier editing;
    private final Consumer<String> onError;
    private final Runnable afterRemoval;
    private List<IngredientSummary> ingredients = List.of();
    private java.util.Map<java.util.UUID, IngredientSummary> ingredientsById = java.util.Map.of();
    private final java.util.Map<java.util.UUID, TextField> quantityEditors = new java.util.LinkedHashMap<>();

    CatalogIngredientRows(VBox ingredientLines, VBox ingredientReadLines,
            javafx.collections.ObservableList<RecipeIngredientInput> lines,
            java.util.function.BooleanSupplier editing, Consumer<String> onError, Runnable afterRemoval) {
        this.ingredientLines = ingredientLines;
        this.ingredientReadLines = ingredientReadLines;
        this.lines = lines;
        this.editing = editing;
        this.onError = onError;
        this.afterRemoval = afterRemoval;
    }

    void renderEditIngredients(List<IngredientSummary> values) {
        updateIngredients(values);
        quantityEditors.clear();
        ingredientLines.getChildren().clear();
        for (var item : List.copyOf(lines)) ingredientLines.getChildren().add(editRow(item));
        if (lines.isEmpty()) ingredientLines.getChildren().add(new Label("Nessun ingrediente aggiunto."));
    }


    List<RecipeIngredientInput> currentDraftLines() {
        return lines.stream().map(line -> {
            var editor = quantityEditors.get(line.ingredientId());
            if (editor == null || !editing.getAsBoolean()) return line;
            var amount = CatalogQuantityPolicy.parse(editor.getText());
            return amount.compareTo(line.quantity()) == 0 ? line
                    : new RecipeIngredientInput(line.ingredientId(), amount);
        }).toList();
    }

    void renderReadIngredients(List<IngredientSummary> values) {
        updateIngredients(values);
        ingredientReadLines.getChildren().clear();
        var ingredientHeading = new Label("Ingrediente");
        ingredientHeading.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(ingredientHeading, javafx.scene.layout.Priority.ALWAYS);
        var tableHeading = new HBox(16, ingredientHeading, new Label("Quantità"));
        tableHeading.getStyleClass().add("catalog-dose-table-heading");
        ingredientReadLines.getChildren().add(tableHeading);
        for (var line : lines) {
            var ingredient = java.util.Optional.ofNullable(ingredientsById.get(line.ingredientId()));
            var name = new Label(ingredient.map(IngredientSummary::name).orElse("Ingrediente non disponibile"));
            name.setWrapText(true);
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
            var amount = new Label(ingredient.map(value -> QuantityFormat.quantity(line.quantity(), value.unit()))
                    .orElse(QuantityFormat.format(line.quantity())));
            amount.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            amount.getStyleClass().add("catalog-dose-quantity");
            var row = new HBox(16, name, amount);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            row.getStyleClass().add("catalog-dose-row");
            ingredientReadLines.getChildren().add(row);
        }
        if (lines.isEmpty()) {
            ingredientReadLines.getChildren().add(new Label("Nessun ingrediente aggiunto."));
        }
    }

    private VBox editRow(RecipeIngredientInput item) {
        var ingredient = java.util.Optional.ofNullable(ingredientsById.get(item.ingredientId()));
        var name = new Label(ingredient.map(IngredientSummary::name).orElse("Ingrediente non disponibile"));
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
        var editor = new TextField(QuantityFormat.format(item.quantity()));
        quantityEditors.put(item.ingredientId(), editor);
        editor.setPrefWidth(90);
        editor.setMinWidth(75);
        editor.setMaxWidth(100);
        editor.setAccessibleText("Quantità di " + name.getText());
        editor.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                CatalogQuantityPolicy.acceptsEdit(change.getControlNewText()) ? change : null));
        Runnable commit = () -> commitQuantity(item, editor);
        editor.setOnAction(event -> commit.run());
        editor.focusedProperty().addListener((observable, before, focused) -> { if (Boolean.FALSE.equals(focused)) commit.run(); });
        var unit = new Label(ingredient.map(value -> value.unit().symbol()).orElse(""));
        unit.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        var remove = new Button("Rimuovi");
        remove.getStyleClass().addAll("course-button", "button-secondary");
        remove.setAccessibleText("Rimuovi " + name.getText());
        remove.setOnAction(event -> {
            lines.removeIf(value -> value.ingredientId().equals(item.ingredientId()));
            renderEditIngredients(ingredients);
            afterRemoval.run();
        });
        var controls = new HBox(10, editor, unit, remove);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var row = new VBox(6, name, controls);
        row.getStyleClass().add("catalog-dose-row");
        return row;
    }

    private void updateIngredients(List<IngredientSummary> updatedIngredients) {
        ingredients = List.copyOf(updatedIngredients);
        var index = new java.util.HashMap<java.util.UUID, IngredientSummary>();
        for (var ingredient : ingredients) index.putIfAbsent(ingredient.id(), ingredient);
        ingredientsById = java.util.Map.copyOf(index);
    }

    private void commitQuantity(RecipeIngredientInput item, TextField editor) {
        if (!editing.getAsBoolean()) return;
        var current = lines.stream().filter(value -> value.ingredientId().equals(item.ingredientId())).findFirst();
        if (current.isEmpty()) return;
        try {
            var amount = CatalogQuantityPolicy.parse(editor.getText());
            lines.set(lines.indexOf(current.get()), new RecipeIngredientInput(item.ingredientId(), amount));
        } catch (IllegalArgumentException _) {
            editor.setText(QuantityFormat.format(current.get().quantity()));
            onError.accept("Inserisci una quantità maggiore di zero. È stata mantenuta la quantità precedente.");
        }
    }
}
