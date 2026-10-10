package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.IngredientSummary;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;


final class CatalogIngredientSearch {
    private final TextField search;
    private final ComboBox<IngredientSummary> choice;
    private List<IngredientSummary> ingredients = List.of();

    CatalogIngredientSearch(TextField search, ComboBox<IngredientSummary> choice, TextField quantity) {
        this.search = search;
        this.choice = choice;
        choice.setEditable(false);
        choice.setPlaceholder(new Label("Nessun ingrediente trovato."));
        search.textProperty().addListener((observable, before, after) -> filter());
        search.setOnAction(event -> {
            if (choice.getItems().size() == 1) {
                choice.setValue(choice.getItems().getFirst());
                choice.hide();
                quantity.requestFocus();
            } else openResults();
        });
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                openResults();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && (!search.getText().isEmpty() || choice.isShowing())) {
                choice.hide();
                search.clear();
                event.consume();
            }
        });
    }

    void ingredients(List<IngredientSummary> values) {
        ingredients = List.copyOf(values);
        filter();
    }

    void clear() {
        choice.hide();
        search.clear();
        choice.setValue(null);
    }

    private void filter() {
        var selected = choice.getValue();
        choice.getItems().setAll(matching(ingredients, search.getText()));

        choice.setValue(selected == null ? null : ingredients.stream()
                .filter(value -> value.id().equals(selected.id())).findFirst().orElse(null));
    }

    private void openResults() {
        if (choice.getItems().isEmpty()) return;
        choice.requestFocus();
        choice.show();
    }

    static List<IngredientSummary> matching(List<IngredientSummary> values, String query) {
        var normalized = query.strip().toLowerCase(Locale.ITALIAN);
        return values.stream().filter(value -> value.toString().toLowerCase(Locale.ITALIAN).contains(normalized)).toList();
    }
}
