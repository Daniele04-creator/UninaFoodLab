package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.catalog.ManageRecipeCatalog;
import it.uninafoodlab.presentation.common.QuantityFormat;
import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.common.FxAsyncExecutor;
import java.util.function.Consumer;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class CourseRecipeDetails {
    private final AuthenticatedUser user;
    private final ManageRecipeCatalog recipeCatalog;
    private final Consumer<Dialog<ButtonType>> configureDialog;

    CourseRecipeDetails(AuthenticatedUser user, ManageRecipeCatalog recipeCatalog,
            Consumer<Dialog<ButtonType>> configureDialog) {
        this.user = user;
        this.recipeCatalog = recipeCatalog;
        this.configureDialog = configureDialog;
    }

    private record RecipeIngredientDisplay(String name, String quantity) {}

    void showSaved(it.uninafoodlab.application.planning.SessionLifecycleRepository.SavedRecipe recipe) {
        var dialog = new Dialog<ButtonType>();
        configureDialog.accept(dialog);
        dialog.setTitle(recipe.name());
        dialog.setHeaderText(recipe.name());
        dialog.getDialogPane().getStyleClass().add("recipe-reading-dialog");
        var doses = recipe.doses() == null ? java.util.List.<Label>of()
                : recipe.doses().lines().filter(line -> !line.isBlank())
                        .map(it.uninafoodlab.presentation.common.SessionRecipeReading::value).toList();
        var scroll = it.uninafoodlab.presentation.common.SessionRecipeReading.content(recipe.description(), doses);
        dialog.getDialogPane().setPrefWidth(660);
        dialog.getDialogPane().setContent(scroll);
        var close = new ButtonType("Chiudi", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        dialog.showAndWait();
    }

    void show(RecipeSummary recipe) {
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle(recipe.name());
        dialog.setHeaderText(recipe.name());
        configureDialog.accept(dialog);
        var description = readOnlyValue(recipe.description());
        description.setPrefWidth(460);
        var preparationHeading = new Label("Preparazione");
        var ingredientsHeading = new Label("Ingredienti");
        preparationHeading.getStyleClass().add("recipe-dialog-heading");
        ingredientsHeading.getStyleClass().add("recipe-dialog-heading");
        var content = new VBox(14,
                readOnlyValue(recipe.categoryName()),
                preparationHeading, description, ingredientsHeading,
                readOnlyValue("Dosi per 1 persona"));
        content.getStyleClass().add("recipe-dialog-content");
        var ingredientRows = new VBox(8);
        content.getChildren().add(ingredientRows);
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        scroll.getStyleClass().add("workspace-scroll");
        dialog.getDialogPane().setContent(scroll);
        if (recipeCatalog != null) {
            ingredientRows.getChildren().add(readOnlyValue("Caricamento ingredienti…"));
            FxAsyncExecutor.run("course-recipe-details", () -> {
                var ingredients = recipeCatalog.ingredients(user);
                var ingredientsById = new java.util.HashMap<java.util.UUID, it.uninafoodlab.application.catalog.IngredientSummary>();
                ingredients.forEach(ingredient -> ingredientsById.putIfAbsent(ingredient.id(), ingredient));
                return recipeCatalog.recipeIngredients(user, recipe.id()).stream().map(line -> {
                    var ingredient = java.util.Optional.ofNullable(ingredientsById.get(line.ingredientId()));
                    return ingredient.map(item -> new RecipeIngredientDisplay(item.name(),
                            QuantityFormat.format(line.quantity()) + " " + item.unit().symbol()))
                            .orElse(new RecipeIngredientDisplay("Ingrediente non disponibile", ""));
                }).toList();
            }, lines -> {
                ingredientRows.getChildren().clear();
                lines.forEach(line -> {
                    var name = readOnlyValue(line.name());
                    HBox.setHgrow(name, Priority.ALWAYS);
                    var quantity = readOnlyValue(line.quantity());
                    quantity.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                    var row = new HBox(20, name, quantity);
                    row.getStyleClass().add("recipe-dialog-ingredient");
                    ingredientRows.getChildren().add(row);
                });
            }, failure -> ingredientRows.getChildren().setAll(readOnlyValue("Impossibile caricare gli ingredienti. Chiudi e riprova.")));
        } else {
            ingredientRows.getChildren().add(readOnlyValue("Dettaglio ingredienti non disponibile."));
        }
        var close = new ButtonType("Chiudi", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(close);
        dialog.getDialogPane().lookupButton(close).getStyleClass().addAll("course-button", "button-primary");
        dialog.showAndWait();
    }
}
