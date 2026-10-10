package it.uninafoodlab.presentation.catalog;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.presentation.common.LabIcons;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import javafx.beans.binding.Bindings;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


final class CatalogRecipeCell extends ListCell<RecipeSummary> {
    private final ListView<RecipeSummary> recipes;
    private final ToIntFunction<String> chapterCount;
    private final Consumer<RecipeSummary> open;
    private final BooleanSupplier showChapters;

    CatalogRecipeCell(ListView<RecipeSummary> recipes, ToIntFunction<String> chapterCount,
            BooleanSupplier showChapters, Consumer<RecipeSummary> open) {
        this.recipes = recipes;
        this.chapterCount = chapterCount;
        this.open = open;
        this.showChapters = showChapters;
    }

    @Override protected void updateItem(RecipeSummary recipe, boolean empty) {
        super.updateItem(recipe, empty);
        setText(null);
        if (empty || recipe == null) { setGraphic(null); return; }
        setGraphic(entry(recipe));
    }

    private GridPane entry(RecipeSummary recipe) {
        var copy = copy(recipe);
        var action = action(recipe);
        var row = new GridPane();
        row.setHgap(22);
        row.setMinWidth(0);
        row.prefWidthProperty().bind(Bindings.max(0, recipes.widthProperty().subtract(48)));
        var copyColumn = new ColumnConstraints();
        copyColumn.setMinWidth(0);
        copyColumn.setHgrow(Priority.ALWAYS);
        var actionColumn = new ColumnConstraints();
        actionColumn.setMinWidth(USE_PREF_SIZE);
        if (!showChapters.getAsBoolean()) {
            row.getStyleClass().add("cookbook-filtered-entry");
            row.getColumnConstraints().setAll(copyColumn, actionColumn);
            row.addRow(0, copy, action);
            row.widthProperty().addListener((observable, before, width) -> reflowFiltered(row, action, width.doubleValue()));
            return row;
        }
        var margin = chapter(recipe);
        var categoryColumn = new ColumnConstraints(180);
        row.getColumnConstraints().setAll(categoryColumn, copyColumn, actionColumn);
        row.addRow(0, margin, copy, action);
        row.widthProperty().addListener((observable, before, width) ->
                reflow(row, categoryColumn, margin, action, width.doubleValue()));
        return row;
    }

    private static VBox copy(RecipeSummary recipe) {
        var name = wrapped(recipe.name(), "cookbook-entry-title");
        name.setMinHeight(USE_PREF_SIZE);
        var details = wrapped(CatalogRecipeText.preview(recipe.description()), "cookbook-entry-description");
        details.setMinHeight(USE_PREF_SIZE);
        var count = new Label(recipe.ingredientCount() + (recipe.ingredientCount() == 1 ? " ingrediente" : " ingredienti"));
        count.getStyleClass().add("cookbook-entry-count");
        var copy = new VBox(6, name, details, count);
        copy.getStyleClass().add("cookbook-entry-copy");
        copy.setMinWidth(0);
        return copy;
    }

    private Button action(RecipeSummary recipe) {
        var action = new Button("Apri ricetta");
        action.setAccessibleText("Apri ricetta: " + recipe.name());
        action.setUserData(recipe.id());
        action.setMinWidth(USE_PREF_SIZE);
        action.setGraphic(LabIcons.icon("chevron-right", "dark", 16));
        action.setContentDisplay(ContentDisplay.RIGHT);
        action.getStyleClass().addAll("button-secondary", "cookbook-open-action");
        action.setOnAction(event -> open.accept(recipe));
        return action;
    }

    private VBox chapter(RecipeSummary recipe) {
        var margin = new VBox(5);
        margin.setMinWidth(0);
        if (!beginsChapter(recipe)) return margin;
        var chapter = wrapped(recipe.categoryName(), "cookbook-chapter-title");
        chapter.setMinHeight(USE_PREF_SIZE);
        int size = chapterCount.applyAsInt(recipe.categoryName());
        var total = new Label(size + (size == 1 ? " ricetta" : " ricette"));
        total.getStyleClass().add("cookbook-entry-count");
        margin.getChildren().addAll(chapter, total);
        return margin;
    }

    private boolean beginsChapter(RecipeSummary recipe) {
        if (getIndex() < 0 || getIndex() >= recipes.getItems().size()) return false;
        return getIndex() == 0
                || !recipes.getItems().get(getIndex() - 1).categoryName().equals(recipe.categoryName());
    }

    private static Label wrapped(String text, String style) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add(style);
        return label;
    }

    private static void reflow(GridPane row, ColumnConstraints category, VBox margin, Button action, double width) {
        boolean narrow = width < 610;
        double categoryWidth = width < 800 ? 140 : 180;
        category.setPrefWidth(categoryWidth);
        category.setMinWidth(categoryWidth);
        category.setMaxWidth(categoryWidth);
        row.setHgap(narrow ? 16 : 22);
        GridPane.setConstraints(action, narrow ? 1 : 2, narrow ? 1 : 0);
        GridPane.setValignment(margin, VPos.TOP);
    }

    private static void reflowFiltered(GridPane row, Button action, double width) {
        boolean narrow = width < 500;
        GridPane.setConstraints(action, narrow ? 0 : 1, narrow ? 1 : 0);
    }
}
