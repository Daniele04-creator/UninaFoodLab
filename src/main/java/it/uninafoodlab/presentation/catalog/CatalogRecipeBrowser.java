package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.catalog.RecipeFilter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


final class CatalogRecipeBrowser {
    private final ListView<RecipeSummary> recipes;
    private final VBox categories;
    private final ComboBox<String> categoryFilter;
    private final TextField search;
    private final CatalogRecipeIndex index;
    private final Consumer<Node> focus;

    CatalogRecipeBrowser(ListView<RecipeSummary> recipes, VBox categories, ComboBox<String> categoryFilter,
            TextField search, CatalogRecipeIndex index, Consumer<Node> focus, Consumer<RecipeSummary> open) {
        this.recipes = recipes;
        this.categories = categories;
        this.categoryFilter = categoryFilter;
        this.search = search;
        this.index = index;
        this.focus = focus;
        CatalogCells.recipes(recipes, index::chapterCount, index::showChapters, open);
        recipes.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && event.getTarget() == recipes) {
                open.accept(recipes.getSelectionModel().getSelectedItem());
                event.consume();
            }
        });
    }

    void renderCategories(int total) {
        categories.getChildren().clear();
        var heading = new Label("Categorie");
        heading.getStyleClass().add("cookbook-category-title");
        categories.getChildren().add(heading);
        addCategory("Tutte le categorie", total);
        index.categoryCounts().forEach(this::addCategory);
    }

    void updateCategories(java.util.Set<String> names) {
        var selected = categoryFilter.getValue();
        categoryFilter.getItems().setAll("Tutte le categorie");
        categoryFilter.getItems().addAll(names.stream().sorted().toList());
        categoryFilter.setValue(categoryFilter.getItems().contains(selected)
                ? selected : "Tutte le categorie");
    }

    RecipeFilter filter(List<CategorySummary> availableCategories) {
        var categoryId = availableCategories.stream()
                .filter(category -> category.name().equals(categoryFilter.getValue()))
                .map(CategorySummary::id).findFirst();
        return new RecipeFilter(categoryId, search.getText());
    }

    private void addCategory(String name, int count) {
        var category = new ToggleButton();
        String displayName = name.equals("Tutte le categorie") ? "Tutte le ricette" : name;
        var title = new Label(displayName);
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        title.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        title.getStyleClass().add("cookbook-category-name");
        HBox.setHgrow(title, Priority.ALWAYS);
        var total = new Label(Integer.toString(count));
        total.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        var row = new HBox(8, title, total);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.prefWidthProperty().bind(Bindings.max(0, category.widthProperty().subtract(20)));
        category.setGraphic(row);
        category.setAccessibleText(displayName + ", " + count + (count == 1 ? " ricetta" : " ricette"));
        category.setMaxWidth(Double.MAX_VALUE);
        category.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        category.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> 20 + row.prefHeight(row.getWidth()), row.widthProperty(), title.fontProperty()));
        category.getStyleClass().add("cookbook-category-action");
        category.setSelected(Objects.equals(categoryFilter.getValue(), name));
        category.setOnAction(event -> {
            category.setSelected(true);
            categoryFilter.setValue(name);
            focus.accept(search);
        });
        categories.getChildren().add(category);
    }

    void focusReturn(UUID returnRecipe) {
        var match = index.visible().stream().filter(recipe -> recipe.id().equals(returnRecipe)).findFirst();
        if (match.isEmpty()) { focus.accept(search); return; }
        recipes.scrollTo(index.visible().indexOf(match.get()));
        Platform.runLater(() -> {
            recipes.applyCss();
            recipes.layout();
            var action = recipes.lookupAll(".cookbook-open-action").stream()
                    .filter(node -> Objects.equals(node.getUserData(), returnRecipe)).findFirst();
            focus.accept(action.orElse(search));
        });
    }
}
