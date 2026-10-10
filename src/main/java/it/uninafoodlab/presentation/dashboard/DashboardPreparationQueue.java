package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController.DashboardSession;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class DashboardPreparationQueue {
    private final ToggleButton preparationToggle;
    private final VBox preparationSection;
    private final BiConsumer<UUID, UUID> openRecipeAssignmentAction;

    DashboardPreparationQueue(ToggleButton preparationToggle, VBox preparationSection, BiConsumer<UUID, UUID> openRecipeAssignmentAction) {
        this.preparationToggle = preparationToggle;
        this.preparationSection = preparationSection;
        this.openRecipeAssignmentAction = openRecipeAssignmentAction;
    }

    void toggle() {
        var expanded = preparationToggle.isSelected();
        preparationSection.setManaged(expanded);
        preparationSection.setVisible(expanded);
        var chevron = LabIcons.icon("chevron-down", "dark", 14);
        chevron.setRotate(expanded ? 180 : 0);
        var title = new Label("Menu da preparare");
        title.getStyleClass().add("agenda-preparation-title");
        var count = new Label(preparationToggle.getText());
        count.getStyleClass().add("agenda-muted");
        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var heading = new HBox(16, title, spacer, count, chevron);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        heading.setMinWidth(0);
        heading.prefWidthProperty().bind(preparationToggle.widthProperty().subtract(4));
        preparationToggle.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        preparationToggle.setGraphic(heading);
        preparationToggle.setAccessibleText("Menu da preparare, " + preparationToggle.getText());
        preparationToggle.setAccessibleHelp(expanded
                ? "Elenco aperto. Premi per nascondere le sessioni senza ricette."
                : "Premi per mostrare le sessioni senza ricette, ordinate per data.");
    }

    void show(List<DashboardSession> sessions) {
        preparationToggle.setText(sessions.size() + (sessions.size() == 1
                ? " sessione senza ricette" : " sessioni senza ricette"));
        preparationToggle.setVisible(!sessions.isEmpty());
        preparationToggle.setManaged(!sessions.isEmpty());
        boolean sameSessions = preparationSection.getChildren().size() == sessions.size()
                && java.util.stream.IntStream.range(0, sessions.size()).allMatch(index ->
                        sessions.get(index).session().id().equals(preparationSection.getChildren().get(index).getUserData()));
        if (!sameSessions) preparationSection.getChildren().clear();
        if (sessions.isEmpty()) preparationToggle.setSelected(false);
        for (int index = 0; index < sessions.size(); index++) {
            var item = sessions.get(index);
            var row = sameSessions ? (GridPane) preparationSection.getChildren().get(index) : preparationRow();
            row.setUserData(item.session().id());
            var text = (VBox) row.getChildren().get(0);
            ((Label) text.getChildren().get(0)).setText(item.courseTitle());
            var date = (Label) text.getChildren().get(1);
            date.setText("Sessione " + item.session().sequenceNumber() + ", " + item.session().scheduledAt().format(
                    DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm", Locale.ITALIAN)));
            var open = (Button) row.getChildren().get(1);
            open.setAccessibleText("Prepara il menu: " + item.courseTitle() + ", " + date.getText());
            open.setOnAction(event -> openRecipeAssignmentAction.accept(item.courseId(), item.session().id()));
            if (!sameSessions) preparationSection.getChildren().add(row);
        }
        toggle();
    }

    private static GridPane preparationRow() {
        var course = new Label();
        course.setWrapText(true);
        course.getStyleClass().add("agenda-preparation-title");
        var date = new Label();
        date.setWrapText(true);
        date.getStyleClass().add("agenda-muted");
        var text = new VBox(4, course, date);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(text, Priority.ALWAYS);
        var open = new Button("Prepara il menu");
        open.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        open.getStyleClass().addAll("course-button", "button-secondary");
        var row = new GridPane(16, 8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("agenda-preparation-row");
        var icon = LabIcons.icon("book-open", "dark", 24);
        icon.getStyleClass().add("agenda-preparation-icon");
        row.getChildren().addAll(text, open, icon);
        row.widthProperty().addListener((observable, before, width) -> {
            boolean narrow = width.doubleValue() < 620;
            GridPane.setConstraints(text, 1, 0);
            GridPane.setConstraints(open, narrow ? 1 : 2, narrow ? 1 : 0);
        });
        GridPane.setConstraints(icon, 0, 0);
        GridPane.setValignment(icon, javafx.geometry.VPos.TOP);
        GridPane.setConstraints(text, 1, 0);
        GridPane.setConstraints(open, 1, 1);
        return row;
    }

}
