package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.presentation.common.LabIcons;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController.DashboardSession;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class DashboardAgendaView {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final VBox taskRows;
    private final BiConsumer<UUID, UUID> openSession;

    DashboardAgendaView(VBox taskRows, BiConsumer<UUID, UUID> openSession) {
        this.taskRows = taskRows;
        this.openSession = openSession;
    }

    void showDaySessions(VBox container, List<DashboardSession> sessions) {
        boolean sameSessions = container.getChildren().size() == sessions.size()
                && java.util.stream.IntStream.range(0, sessions.size()).allMatch(index ->
                        sessions.get(index).session().id().equals(container.getChildren().get(index).getUserData()));
        if (!sameSessions) container.getChildren().clear();
        for (int i = 0; i < sessions.size(); i++) {
            var item = sessions.get(i);
            var row = sameSessions ? (GridPane) container.getChildren().get(i) : sessionRow();
            var session = item.session();
            row.setUserData(session.id());
            var time = (VBox) row.getChildren().get(0);
            var start = (Label) time.getChildren().get(0);
            var end = (Label) time.getChildren().get(1);
            start.setText(TIME.format(session.scheduledAt()));
            end.setText(TIME.format(session.scheduledAt().plusMinutes(session.durationMinutes())));
            var description = (VBox) row.getChildren().get(1);
            ((Label) description.getChildren().get(0)).setText(item.courseTitle());
            ((Label) description.getChildren().get(1)).setText("Sessione " + session.sequenceNumber());
            var metadata = (HBox) description.getChildren().get(2);
            var modeLabel = (Label) metadata.getChildren().get(0);
            showMode(modeLabel, session.mode());
            var state = (Label) metadata.getChildren().get(1);
            state.setText(statusLabel(session.status()));
            state.setManaged(!state.getText().isBlank());
            state.setVisible(state.isManaged());
            state.getStyleClass().remove("agenda-session-cancelled");
            if (session.status() == SessionStatus.CANCELLED) state.getStyleClass().add("agenda-session-cancelled");
            var open = (Button) row.getChildren().get(2);
            open.setAccessibleText("Apri sessione " + session.sequenceNumber() + ": " + item.courseTitle()
                    + ", " + start.getText() + "–" + end.getText() + ", " + modeLabel.getText()
                    + (state.isManaged() ? ", " + state.getText() : ""));
            open.setOnAction(event -> openSession.accept(item.courseId(), session.id()));
            if (!sameSessions) container.getChildren().add(row);
        }
    }

    private static void showMode(Label label, SessionMode mode) {
        boolean online = mode == SessionMode.ONLINE;
        label.setText(online ? "Online" : "In presenza");
        label.setGraphic(LabIcons.icon(online ? "video" : "cooking-pot", "dark", 16));
    }

    private static GridPane sessionRow() {
        var start = new Label();
        start.getStyleClass().add("agenda-session-start");
        var end = new Label();
        end.getStyleClass().add("agenda-session-end");
        var time = new VBox(4, start, end);
        time.setMinWidth(Region.USE_PREF_SIZE);
        time.getStyleClass().add("agenda-session-time");
        var title = new Label();
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.getStyleClass().add("agenda-session-title");
        var sequence = new Label();
        sequence.getStyleClass().add("agenda-session-sequence");
        var mode = new Label();
        mode.getStyleClass().add("agenda-session-mode");
        var state = new Label();
        state.getStyleClass().add("agenda-session-state");
        var metadata = new HBox(10, mode, state);
        metadata.setAlignment(Pos.CENTER_LEFT);
        var text = new VBox(5, title, sequence, metadata);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(text, Priority.ALWAYS);
        var open = new Button("Apri sessione");
        open.getStyleClass().addAll("course-button", "lab-text-action", "agenda-open-session");
        var arrow = new Label("→");
        arrow.getStyleClass().add("agenda-action-arrow");
        open.setGraphic(arrow);
        open.setMinWidth(Region.USE_PREF_SIZE);
        var row = new GridPane(18, 8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("agenda-session-row");
        row.getChildren().addAll(time, text, open);
        row.widthProperty().addListener((observable, before, width) -> layoutRow(row, width.doubleValue()));
        layoutRow(row, 0);
        return row;
    }

    private static void layoutRow(GridPane row, double width) {
        boolean narrow = width < 660;
        GridPane.setConstraints(row.getChildren().get(0), 0, 0);
        GridPane.setConstraints(row.getChildren().get(1), 1, 0);
        GridPane.setConstraints(row.getChildren().get(2), narrow ? 1 : 2, narrow ? 1 : 0);
        GridPane.setHalignment(row.getChildren().get(2), javafx.geometry.HPos.RIGHT);
        GridPane.setValignment(row.getChildren().get(0), javafx.geometry.VPos.TOP);
    }

    void showTasks(int missingMenus) {
        taskRows.getChildren().clear();
        if (missingMenus == 0) {
            var empty = new Label("Nessun menu da preparare.");
            empty.setWrapText(true);
            empty.getStyleClass().add("agenda-muted");
            taskRows.getChildren().add(empty);
        }
    }

    void showEmptyDay(boolean empty, DashboardCalendar<DashboardSession> calendar, VBox sessions,
            VBox emptyPanel, Label emptyLabel, Button nextButton) {
        sessions.setVisible(!empty);
        sessions.setManaged(!empty);
        emptyPanel.setVisible(empty);
        emptyPanel.setManaged(empty);
        emptyLabel.setText("Nessuna sessione in programma per questo giorno.");
        var next = DashboardSessionPolicy.nextAgendaSession(calendar);
        nextButton.setVisible(next.isPresent());
        nextButton.setManaged(next.isPresent());
        nextButton.setText(next.map(item -> "Vai alla prossima sessione: " + item.session().scheduledAt()
                .format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))).orElse("Esplora corsi"));
    }

    private static String statusLabel(SessionStatus status) {
        return switch (status) {
            case CANCELLED -> "Annullata";
            case COMPLETED -> "Svolta";
            default -> "";
        };
    }
}
