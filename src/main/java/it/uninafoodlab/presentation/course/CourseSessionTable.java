package it.uninafoodlab.presentation.course;



import static it.uninafoodlab.presentation.course.CourseSessionPolicy.isRecipeMutable;
import static it.uninafoodlab.presentation.course.CoursePresentation.programModeLabel;
import static it.uninafoodlab.presentation.course.CoursePresentation.programSessionName;
import static it.uninafoodlab.presentation.course.CoursePresentation.recipeAssignmentLabel;
import static it.uninafoodlab.presentation.course.CoursePresentation.sessionStatusLabel;
import static it.uninafoodlab.presentation.course.CoursePresentation.sessionStatusNeedsAttention;

import it.uninafoodlab.application.course.CourseSessionSummary;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

final class CourseSessionTable {
    private final TableView<CourseSessionSummary> table;

    CourseSessionTable(TableView<CourseSessionSummary> table,
            TableColumn<CourseSessionSummary, String> sequence,
            TableColumn<CourseSessionSummary, String> date,
            TableColumn<CourseSessionSummary, String> mode,
            TableColumn<CourseSessionSummary, String> details,
            TableColumn<CourseSessionSummary, String> recipes,
            Clock clock, Consumer<CourseSessionSummary> onSelect) {
        this.table = table;
        sequence.setVisible(false);
        sequence.setCellValueFactory(cell -> new SimpleStringProperty(programSessionName(cell.getValue())));
        date.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().scheduledAt()
                .format(DateTimeFormatter.ofPattern("dd/MM/uuuu\nHH:mm"))));
        date.setCellFactory(ignored -> new DateCell());
        mode.setCellValueFactory(cell -> new SimpleStringProperty(programModeLabel(cell.getValue())));
        mode.setCellFactory(ignored -> new ModeCell());
        details.setCellValueFactory(cell -> new SimpleStringProperty(sessionStatusLabel(cell.getValue())));
        details.setCellFactory(ignored -> new StatusCell());
        recipes.setCellValueFactory(cell -> new SimpleStringProperty(recipeAssignmentLabel(cell.getValue())));
        recipes.setCellFactory(ignored -> new RecipeCell(clock));
        updateResizePolicy();
        table.widthProperty().addListener((observable, previous, width) -> updateResizePolicy());
        table.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, session) -> onSelect.accept(session));
    }

    private void updateResizePolicy() {
        var minimumWidth = table.getColumns().stream().filter(TableColumn::isVisible)
                .mapToDouble(TableColumn::getMinWidth).sum() + 2;
        table.setColumnResizePolicy(table.getWidth() < minimumWidth
                ? TableView.UNCONSTRAINED_RESIZE_POLICY
                : TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private static final class DateCell extends TableCell<CourseSessionSummary, String> {
        @Override
        protected void updateItem(String dateAndTime, boolean empty) {
            super.updateItem(dateAndTime, empty);
            setText(empty ? null : dateAndTime);
            setGraphic(null);
            var session = getTableRow() == null ? null : getTableRow().getItem();
            setTooltip(empty || session == null ? null : new Tooltip(session.scheduledAt()
                    .format(DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm", Locale.ITALIAN))));
        }
    }

    private static final class ModeCell extends TableCell<CourseSessionSummary, String> {
        @Override
        protected void updateItem(String mode, boolean empty) {
            super.updateItem(mode, empty);
            setText(null);
            setGraphic(empty || mode == null ? null : CourseSessionModeView.label("Online".equals(mode)
                    ? it.uninafoodlab.domain.course.SessionMode.ONLINE
                    : it.uninafoodlab.domain.course.SessionMode.PRACTICAL));
        }
    }

    private static final class StatusCell extends TableCell<CourseSessionSummary, String> {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            setText(null);
            if (empty || status == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            var label = new Label(status);
            label.setWrapText(true);
            label.setMinWidth(0);
            label.maxWidthProperty().bind(Bindings.max(0, widthProperty().subtract(48)));
            label.getStyleClass().addAll("course-session-state", statusStyle(status));
            var row = "Annullata".equals(status) ? new HBox(label) : new HBox(9.0, statusIcon(status), label);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
            setTooltip(new Tooltip(status));
        }

        private static ImageView statusIcon(String status) {
            var name = sessionStatusNeedsAttention(status) ? "status-pending.png" : "status-complete.png";
            var icon = new ImageView(new Image(Objects.requireNonNull(CourseSessionTable.class.getResourceAsStream(
                    "/it/uninafoodlab/presentation/assets/" + name))));
            icon.setFitWidth(22.0);
            icon.setFitHeight(22.0);
            icon.setPreserveRatio(true);
            icon.setSmooth(true);
            return icon;
        }
    }

    static String statusStyle(String status) {
        if ("Annullata".equals(status)) return "session-status-cancelled";
        return sessionStatusNeedsAttention(status) ? "session-status-pending" : "session-status-confirmed";
    }

    static String recipeStyle(String state, CourseSessionSummary session, Clock clock) {
        return switch (state) {
            case "Nessuna ricetta" -> isRecipeMutable(session, clock)
                    ? "recipe-assignment-missing" : "recipe-assignment-na";
            case "Non prevista" -> "recipe-assignment-na";
            default -> "recipe-assignment-ready";
        };
    }

    static Label recipeCountBadge(CourseSessionSummary session, Clock clock) {
        var recipeText = recipeAssignmentLabel(session);
        var menu = new Label(recipeText);
        menu.getStyleClass().addAll("course-session-menu-count",
                recipeStyle(recipeText, session, clock));
        menu.setWrapText(true);
        menu.setMinWidth(0);
        return menu;
    }

    private static final class RecipeCell extends TableCell<CourseSessionSummary, String> {
        private final Clock clock;

        private RecipeCell(Clock clock) {
            this.clock = clock;
        }

        @Override
        protected void updateItem(String state, boolean empty) {
            super.updateItem(state, empty);
            setText(null);
            if (empty || state == null) {
                setGraphic(null);
                return;
            }
            var badge = new Label(state);
            var session = getTableRow() == null ? null : getTableRow().getItem();
            badge.getStyleClass().addAll("recipe-assignment-badge", recipeStyle(state, session, clock));
            setGraphic(badge);
        }
    }
}
