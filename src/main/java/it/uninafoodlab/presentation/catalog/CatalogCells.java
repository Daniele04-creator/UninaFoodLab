package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.IngredientSummary;
import it.uninafoodlab.application.catalog.RecipeSummary;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;


final class CatalogCells {
    private CatalogCells() {}

    static <T> void display(ComboBox<T> box, java.util.function.Function<T, String> label) {
        box.setConverter(new StringConverter<>() {
            public String toString(T value) { return value == null ? "" : label.apply(value); }
            public T fromString(String value) { return null; }
        });
    }

    static void ingredients(ListView<IngredientSummary> ingredientList) {
        ingredientList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(IngredientSummary value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) { setGraphic(null); return; }
                var name = new Label(value.name());
                name.setWrapText(true);
                name.setMinWidth(0);
                name.setMaxWidth(Double.MAX_VALUE);
                name.getStyleClass().add("field-label");
                javafx.scene.layout.HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
                var unit = new Label(value.unit().symbol());
                unit.setMinWidth(USE_PREF_SIZE);
                unit.getStyleClass().add("form-helper");
                var action = new javafx.scene.control.Button(value.locked() ? "Dettagli" : "Modifica");
                action.getStyleClass().addAll("button-secondary", "catalog-ingredient-action");
                action.setMinWidth(USE_PREF_SIZE);
                action.setOnAction(event -> ingredientList.getSelectionModel().select(value));
                var row = new javafx.scene.layout.GridPane();
                row.setHgap(16);
                for (double percent : new double[]{45, 35, 20}) {
                    var column = new javafx.scene.layout.ColumnConstraints();
                    column.setPercentWidth(percent);
                    row.getColumnConstraints().add(column);
                }
                row.addRow(0, name, unit, action);
                row.prefWidthProperty().bind(javafx.beans.binding.Bindings.max(0,
                        ingredientList.widthProperty().subtract(48)));
                setGraphic(row);
            }
        });
    }

    static void recipes(ListView<RecipeSummary> recipeList,
            java.util.function.ToIntFunction<String> chapterCount,
            java.util.function.BooleanSupplier showChapters,
            java.util.function.Consumer<RecipeSummary> open) {
        recipeList.setCellFactory(list -> new CatalogRecipeCell(recipeList, chapterCount, showChapters, open));
    }
}
