package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import java.time.format.DateTimeFormatter;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

final class CourseScheduleForm {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CourseScheduleEditor editor;

    CourseScheduleForm(CourseScheduleEditor editor) {
        this.editor = editor;
    }

    Node create(boolean reschedule, CourseSessionSummary selected) {
        var grid = createGrid();
        int start = 0;
        if (reschedule) {
            addCurrentAppointment(grid, selected);
            start = 1;
        }
        addField(grid, new Label(reschedule ? "Nuova data" : "Data"), editor.scheduleDateField, 0, start);
        addField(grid, new Label(reschedule ? "Nuovo orario" : "Ora"), editor.scheduleTimeField, 1, start);
        int row = start + 2;
        if (!reschedule) {
            addSessionDetails(grid, row);
            row += 4;
        } else {
            addField(grid, new Label(selected.mode() == SessionMode.ONLINE ? "Link della lezione (HTTPS)" : "Sede o link alla posizione"),
                    editor.scheduleLocationField, 0, row);
            GridPane.setColumnSpan(editor.scheduleLocationField, 2);
            row += 2;
        }
        addAttendanceCutoff(grid, row, reschedule);
        addNotification(grid, row);
        if (!reschedule) {
            addSummary(grid, row);
        }
        styleFields(grid);
        return grid;
    }

    private GridPane createGrid() {
        var grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.getStyleClass().add("course-schedule-form");
        for (int column = 0; column < 2; column++) {
            var constraint = new ColumnConstraints();
            constraint.setPercentWidth(50);
            constraint.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(constraint);
        }
        return grid;
    }

    private void addCurrentAppointment(GridPane grid, CourseSessionSummary selected) {
        var context = readOnlyValue("Attualmente: " + DATE_TIME_FORMAT.format(selected.scheduledAt())
                + ", " + CoursePresentation.modeLabel(selected.mode()) + ", "
                + selected.durationMinutes() + " minuti\n" + editor.scheduleLocationField.getText()
                + selected.capacity().map(value -> ", " + value + " posti").orElse(""));
        grid.add(context, 0, 0, 2, 1);
    }

    private void addSessionDetails(GridPane grid, int row) {
        addField(grid, new Label("Modalità"), editor.scheduleModeField, 0, row);
        addField(grid, new Label("Durata (minuti)"), editor.scheduleDurationField, 1, row);
        var locationLabel = new Label();
        locationLabel.textProperty().bind(Bindings.when(
                editor.scheduleModeField.valueProperty().isEqualTo(SessionMode.ONLINE))
                .then("Link della lezione (HTTPS)").otherwise("Sede o link alla posizione"));
        addField(grid, locationLabel, editor.scheduleLocationField, 0, row + 2);
        var capacityLabel = new Label("Capienza (posti)");
        addField(grid, capacityLabel, editor.scheduleCapacityField, 1, row + 2);
        showForPracticalSession(capacityLabel);
        showForPracticalSession(editor.scheduleCapacityField);
    }

    private void addAttendanceCutoff(GridPane grid, int row, boolean reschedule) {
        var label = new Label(reschedule ? "Nuova scadenza adesioni, prima dell’inizio"
                : "Chiusura adesioni, prima dell’inizio");
        grid.add(label, 0, row, 2, 1);
        grid.add(editor.scheduleAttendanceClosesAtField, 0, row + 1, 2, 1);
        showForPracticalSession(label);
        showForPracticalSession(editor.scheduleAttendanceClosesAtField);
    }

    private void showForPracticalSession(Node node) {
        var practical = editor.scheduleModeField.valueProperty().isEqualTo(SessionMode.PRACTICAL);
        node.visibleProperty().bind(practical);
        node.managedProperty().bind(practical);
    }

    private void addNotification(GridPane grid, int row) {
        var notificationLabel = new Label("Comunicazione agli iscritti");
        notificationLabel.getStyleClass().add("section-title");
        grid.add(notificationLabel, 0, row + 2, 2, 1);
        grid.add(new Label("Titolo comunicazione"), 0, row + 3, 2, 1);
        grid.add(editor.scheduleNotificationTitleField, 0, row + 4, 2, 1);
        grid.add(new Label("Messaggio"), 0, row + 5, 2, 1);
        grid.add(editor.scheduleNotificationMessageField, 0, row + 6, 2, 1);
    }

    private void addSummary(GridPane grid, int row) {
        var summary = readOnlyValue("");
        summary.textProperty().bind(Bindings.createStringBinding(this::summaryText,
                editor.scheduleDateField.valueProperty(), editor.scheduleTimeField.valueProperty(),
                editor.scheduleDurationField.valueProperty(), editor.scheduleModeField.valueProperty(),
                editor.scheduleCapacityField.valueProperty()));
        grid.add(new Label("Riepilogo sessione"), 0, row + 7, 2, 1);
        grid.add(summary, 0, row + 8, 2, 1);
    }

    private String summaryText() {
        var date = editor.scheduleDateField.getValue();
        var time = editor.scheduleTimeField.getValue();
        var duration = editor.scheduleDurationField.getValue();
        if (date == null || time == null || duration == null) {
            return "Seleziona data, ora e durata.";
        }
        return DATE_TIME_FORMAT.format(date.atTime(time)) + "–" + time.plusMinutes(duration)
                + (editor.scheduleModeField.getValue() == SessionMode.PRACTICAL
                        ? ", " + editor.scheduleCapacityField.getValue() + " posti" : ", Online");
    }

    private void styleFields(GridPane grid) {
        for (var node : grid.getChildren()) {
            if (node instanceof Label label) {
                label.setWrapText(true);
                label.getStyleClass().add("field-label");
            }
            if (node instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private void addField(GridPane grid, Label label, Node field, int column, int row) {
        label.setLabelFor(field);
        GridPane.setColumnSpan(field, 1);
        grid.add(label, column, row);
        grid.add(field, column, row + 1);
    }

    private Label readOnlyValue(String value) {
        var label = new Label(value);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("page-description");
        return label;
    }
}
