package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.readOnlyValue;

import it.uninafoodlab.application.catalog.RecipeSummary;
import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.SessionLifecycleRepository.SavedRecipe;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class CourseAssignedRecipeRows {
    private static final String COURSE_BUTTON_STYLE = "course-button";
    private final VBox rows;
    private final CourseRecipeDetails details;
    private final Consumer<UUID> remove;

    CourseAssignedRecipeRows(VBox rows, CourseRecipeDetails details, Consumer<UUID> remove) {
        this.rows = rows;
        this.details = details;
        this.remove = remove;
    }

    void clear() { rows.getChildren().clear(); }

    void showMessage(String message) { rows.getChildren().setAll(readOnlyValue(message)); }

    void renderCatalog(List<RecipeSummary> recipes, CourseSessionSummary session, boolean mutable) {
        for (var recipe : recipes) {
            if (!session.recipeIds().contains(recipe.id())) continue;
            var name = new Label(recipe.name());
            name.setWrapText(true);
            name.setMinWidth(0);
            name.getStyleClass().add("field-label");
            var metadata = readOnlyValue(recipe.categoryName());
            var description = new VBox(5, name, metadata);
            description.setMinWidth(0);
            description.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(description, Priority.ALWAYS);
            var detailsButton = new Button("Apri ricetta");
            detailsButton.getStyleClass().addAll(COURSE_BUTTON_STYLE, "button-secondary", "course-recipe-open");
            detailsButton.setAccessibleText("Apri ricetta " + recipe.name());
            detailsButton.setOnAction(event -> this.details.show(recipe));
            var remove = new Button("Rimuovi");
            remove.getStyleClass().addAll(COURSE_BUTTON_STYLE, "button-danger");
            remove.setAccessibleText("Rimuovi " + recipe.name() + " dal menu");
            remove.setVisible(mutable);
            remove.setManaged(mutable);
            remove.setOnAction(event -> this.remove.accept(recipe.id()));
            var rowActions = new javafx.scene.layout.FlowPane(8, 8, detailsButton, remove);
            var row = assignedRow(description, rowActions);
            rows.getChildren().add(row);
        }
    }

    void renderSaved(List<SavedRecipe> menu, boolean mutable) {
        clear();
        for (var saved : menu) rows.getChildren().add(savedRecipeRow(saved, mutable));
    }

    private GridPane savedRecipeRow(SavedRecipe saved, boolean mutable) {
        var name = new Label(saved.name());
        name.setWrapText(true);
        name.setMinWidth(0);
        name.getStyleClass().add("field-label");
        var open = new Button("Apri ricetta");
        open.getStyleClass().addAll(COURSE_BUTTON_STYLE, "button-secondary", "course-recipe-open");
        open.setAccessibleText("Apri ricetta " + saved.name());
        open.setOnAction(event -> details.showSaved(saved));
        var rowActions = new FlowPane(8, 8, open);
        if (mutable) {
            var remove = new Button("Rimuovi");
            remove.getStyleClass().addAll(COURSE_BUTTON_STYLE, "button-danger");
            remove.setAccessibleText("Rimuovi " + saved.name() + " dal menu");
            remove.setOnAction(event -> this.remove.accept(saved.id()));
            rowActions.getChildren().add(remove);
        }
        return assignedRow(name, rowActions);
    }

    private static GridPane assignedRow(javafx.scene.Node description, FlowPane actions) {
        var row = new GridPane();
        row.setMinWidth(0);
        row.setHgap(16);
        row.setVgap(6);
        row.getStyleClass().add("course-assigned-recipe");
        actions.getStyleClass().add("course-recipe-actions");
        actions.setPrefWrapLength(actions.prefWidth(-1));
        var textColumn = new ColumnConstraints();
        textColumn.setMinWidth(0);
        textColumn.setHgrow(Priority.ALWAYS);
        var actionColumn = new ColumnConstraints();
        actionColumn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        row.getColumnConstraints().setAll(textColumn, actionColumn);
        row.addRow(0, description, actions);
        row.widthProperty().addListener((observable, before, width) -> {
            boolean compact = width.doubleValue() < 480;
            GridPane.setConstraints(description, 0, 0, compact ? 2 : 1, 1);
            GridPane.setConstraints(actions, compact ? 0 : 1, compact ? 1 : 0, compact ? 2 : 1, 1);
        });
        return row;
    }

}
