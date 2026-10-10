package it.uninafoodlab.presentation.common;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class SessionRecipeReading {
    private SessionRecipeReading() {}

    public static ScrollPane content(String description, List<? extends Node> ingredientRows) {
        var ingredients = new VBox(10);
        ingredients.getStyleClass().add("recipe-dialog-ingredients");
        ingredients.getChildren().addAll(ingredientRows);
        if (ingredientRows.isEmpty()) ingredients.getChildren().add(value("Nessun ingrediente disponibile nella ricetta della sessione."));
        var preparation = value(description.isBlank() ? "Nessuna descrizione disponibile."
                : RecipePreparationText.readingText(description));
        preparation.getStyleClass().add("recipe-dialog-preparation");
        var body = new VBox(16, value("Versione associata alla sessione"),
                heading("Ingredienti, dosi per una persona"), ingredients,
                heading("Preparazione"), preparation);
        body.getStyleClass().add("recipe-dialog-content");
        var scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(400);
        scroll.getStyleClass().add("workspace-scroll");
        return scroll;
    }

    public static HBox ingredient(String name, String quantity) {
        var nameLabel = value(name);
        nameLabel.setMinWidth(0);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        var quantityLabel = value(quantity);
        quantityLabel.setMinWidth(Region.USE_PREF_SIZE);
        var row = new HBox(24, nameLabel, quantityLabel);
        row.getStyleClass().add("recipe-dialog-ingredient");
        return row;
    }

    public static Label value(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("page-description");
        return label;
    }

    private static Label heading(String text) {
        var label = value(text);
        label.getStyleClass().add("recipe-dialog-heading");
        return label;
    }
}
