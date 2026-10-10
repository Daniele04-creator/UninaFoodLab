package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.presentation.common.LabIcons;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;


final class ParticipantSessionTimeline {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ParticipantSessionTimeline() {}

    static void render(VBox target, List<PublishedCourseSessionSummary> sessions,
            List<PublishedSessionRecipeSummary> recipes, List<PracticalAttendanceSummary> attendance,
            boolean enrolled, Clock clock,
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> changeAttendance,
            Consumer<String> locationError) {
        target.getChildren().clear();
        var chronological = sessions.stream().sorted(Comparator
                .comparing(PublishedCourseSessionSummary::scheduledAt)
                .thenComparingInt(PublishedCourseSessionSummary::sequenceNumber)).toList();
        var recipesBySession = recipes.stream().collect(Collectors.groupingBy(PublishedSessionRecipeSummary::sessionId));
        var attendanceBySession = new HashMap<UUID, PracticalAttendanceSummary>();
        for (var response : attendance) attendanceBySession.putIfAbsent(response.sessionId(), response);
        var now = LocalDateTime.now(clock.withZone(ParticipantWorkspaceRules.ROME));
        var next = chronological.stream().filter(session -> session.status() != SessionStatus.CANCELLED
                && session.scheduledAt().isAfter(now)).findFirst();
        for (var session : chronological) {
            var row = row(session, locationError);
            if (session.mode() == SessionMode.PRACTICAL) {
                row.getChildren().add(menu(session, recipesBySession.getOrDefault(session.id(), List.of())));
                addParticipation(row, session, attendanceBySession.get(session.id()), enrolled, clock, changeAttendance);
            }
            target.getChildren().add(row);
            if (next.filter(item -> item.id().equals(session.id())).isPresent()) {
                var heading = (Label) ((HBox) row.getChildren().getFirst()).getChildren().getFirst();
                heading.setFocusTraversable(true);
                heading.setAccessibleText("Sessione " + session.sequenceNumber() + ", "
                        + appointment(session.scheduledAt(), session.durationMinutes()));
                var jump = new Button("Vai alla prossima sessione");
                jump.getStyleClass().add("button-secondary");
                jump.setUserData(session.id());
                jump.setAccessibleText("Vai alla prossima sessione: " + heading.getAccessibleText());
                jump.setOnAction(event -> Platform.runLater(() -> focusSession(heading)));
                target.getChildren().addFirst(jump);
            }
        }
        if (sessions.isEmpty()) target.getChildren().add(label("Programma non disponibile.", "form-helper"));
        else target.getChildren().getLast().getStyleClass().add("participant-session-last");
    }

    private static void focusSession(Label heading) {
        if (heading.getScene() == null) return;
        heading.getScene().getRoot().applyCss();
        heading.getScene().getRoot().layout();
        for (Node parent = heading.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll) {
                var content = scroll.getContent();
                var bounds = content.sceneToLocal(heading.localToScene(heading.getBoundsInLocal()));
                double range = content.getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight();
                if (range > 0) scroll.setVvalue(scroll.getVmin()
                        + Math.clamp(bounds.getMinY() / range, 0.0, 1.0) * (scroll.getVmax() - scroll.getVmin()));
                break;
            }
        }
        heading.requestFocus();
    }

    private static VBox menu(PublishedCourseSessionSummary session, List<PublishedSessionRecipeSummary> recipes) {
        var menu = new VBox(4);
        menu.getStyleClass().add("participant-session-menu");
        menu.getChildren().add(label("Menu", "form-label"));
        for (var recipe : recipes) {
            var open = new Button(recipe.name());
            open.getStyleClass().add("button-quiet");
            open.setWrapText(true);
            open.setMaxWidth(Double.MAX_VALUE);
            open.setAlignment(Pos.CENTER_LEFT);
            open.setGraphic(LabIcons.icon("book-open", "dark", 16));
            open.setAccessibleText("Consulta ricetta e ingredienti: " + recipe.name());
            open.setOnAction(event -> ParticipantRecipeDialog.show(open.getScene().getWindow(), recipe));
            menu.getChildren().add(open);
        }
        if (recipes.isEmpty()) menu.getChildren().add(label(
                session.status() == SessionStatus.COMPLETED ? "Nessuna ricetta disponibile."
                        : "Il menu verrà pubblicato dallo Chef.", "form-helper"));
        return menu;
    }

    private static void addParticipation(VBox row, PublishedCourseSessionSummary session,
            PracticalAttendanceSummary attendance, boolean enrolled, Clock clock,
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> changeAttendance) {
        if (enrolled && session.status() != SessionStatus.CANCELLED) {
            if (attendance != null) {
                row.getChildren().add(response(attendance, clock, changeAttendance));
            } else if (session.status() != SessionStatus.COMPLETED) {
                row.getChildren().add(label("Adesioni non ancora caricate.", "form-helper"));
            }
        } else if (!enrolled && session.attendanceClosesAt().isPresent()) {
            row.getChildren().add(label("Adesioni fino al " + ParticipantWorkspaceRules.DATE_TIME
                    .format(session.attendanceClosesAt().orElseThrow()), "form-helper"));
        }
    }

    static void renderAttendanceOnly(VBox target, List<PracticalAttendanceSummary> attendance, Clock clock,
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> changeAttendance) {
        target.getChildren().clear();
        for (var item : attendance.stream().sorted(Comparator.comparing(PracticalAttendanceSummary::scheduledAt)).toList()) {
            var row = new VBox(8);
            row.getStyleClass().add("participant-session-row");
            row.setUserData(item.sessionId());
            row.getChildren().addAll(label("Sessione " + item.sequenceNumber(), "course-list-title"),
                    label(appointment(item.scheduledAt(), item.durationMinutes()), "course-list-meta"),
                    label("In presenza, " + item.venue(), "participant-mode"), response(item, clock, changeAttendance));
            target.getChildren().add(row);
        }
        if (!target.getChildren().isEmpty()) target.getChildren().getLast().getStyleClass().add("participant-session-last");
    }

    private static VBox row(PublishedCourseSessionSummary session, Consumer<String> locationError) {
        var row = new VBox(8);
        row.getStyleClass().add("participant-session-row");
        row.setUserData(session.id());
        var title = label("Sessione " + session.sequenceNumber(), "course-list-title");
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        var state = label(switch (session.status()) {
            case SCHEDULED -> "Programmata";
            case COMPLETED -> "Svolta";
            case CANCELLED -> "Annullata";
        }, "participant-session-state");
        state.setMaxWidth(Region.USE_PREF_SIZE);
        if (session.status() == SessionStatus.COMPLETED) state.getStyleClass().add("participant-session-completed");
        if (session.status() == SessionStatus.CANCELLED) state.getStyleClass().add("participant-session-cancelled");
        var heading = new HBox(12, title, state);
        heading.setAlignment(Pos.CENTER_LEFT);
        var date = label(appointment(session.scheduledAt(), session.durationMinutes()), "course-list-meta");
        date.setGraphic(LabIcons.icon("calendar", "dark", 16));
        var mode = label(ParticipantWorkspaceRules.formatMode(session.mode()), "participant-mode");
        mode.setGraphic(LabIcons.icon(session.mode() == SessionMode.ONLINE ? "video" : "cooking-pot", "dark", 16));
        var meta = new FlowPane(18, 8, date, mode);
        row.getChildren().addAll(heading, meta);
        if (session.mode() == SessionMode.PRACTICAL) {
            var venue = label(ParticipantSessionLocation.label(session), "course-list-meta");
            venue.setGraphic(LabIcons.icon("map-pin", "dark", 16));
            row.getChildren().add(venue);
            var location = new ParticipantSessionLocation(() -> session, locationError,
                    it.uninafoodlab.presentation.common.SessionLocationLinks::open);
            location.update(session);
            row.getChildren().add(location.button());
        } else ParticipantWorkspaceRules.authorizedOnlineLink(session).ifPresent(link -> {
            var feedback = label("", "course-feedback-error");
            feedback.setVisible(false);
            feedback.setManaged(false);
            var lesson = new ParticipantOnlineLesson(session, message -> {
                feedback.setText(message);
                feedback.setVisible(!message.isBlank());
                feedback.setManaged(!message.isBlank());
            }, it.uninafoodlab.presentation.common.SessionLocationLinks::open);
            var copy = new Button("Copia link");
            copy.getStyleClass().add("button-secondary");
            copy.setAccessibleText("Copia il collegamento della sessione online " + session.sequenceNumber());
            copy.setOnAction(event -> {
                var content = new ClipboardContent(); content.putString(link);
                Clipboard.getSystemClipboard().setContent(content);
            });
            row.getChildren().addAll(new FlowPane(8, 8, lesson.button(), copy), feedback);
        });
        return row;
    }

    private static VBox response(PracticalAttendanceSummary item, Clock clock,
            BiConsumer<PracticalAttendanceSummary, AttendanceStatus> changeAttendance) {
        var response = new VBox(6);
        response.getStyleClass().add("participant-session-response");
        var status = label(ParticipantWorkspaceRules.attendanceStatus(item), "participant-response-status");
        if (item.attendanceStatus().orElse(null) == AttendanceStatus.CONFIRMED)
            status.getStyleClass().add("participant-response-confirmed");
        var detail = label(item.confirmedCount() + "/" + item.capacity() + " posti occupati\nAdesioni fino al "
                + ParticipantWorkspaceRules.DATE_TIME.format(item.attendanceClosesAt()), "form-helper");
        response.getChildren().addAll(status, detail);
        if (!item.programReady()) response.getChildren().add(label("Menu in preparazione: attendi la pubblicazione delle ricette.", "form-helper"));
        var now = LocalDateTime.now(clock);
        if (!item.isAttendanceOpen(now)) {
            response.getChildren().add(label("Adesioni chiuse", "form-helper"));
            return response;
        }
        var confirm = new Button("Confermo");
        confirm.getStyleClass().add("button-primary");
        confirm.setAccessibleText("Conferma la presenza alla sessione " + item.sequenceNumber());
        confirm.setDisable(!item.programReady() || !ParticipantWorkspaceRules.canConfirmAttendance(item, now)
                || item.attendanceStatus().orElse(null) == AttendanceStatus.CONFIRMED);
        confirm.setOnAction(event -> changeAttendance.accept(item, AttendanceStatus.CONFIRMED));
        var decline = new Button("Non partecipo");
        decline.getStyleClass().add("button-secondary");
        decline.setAccessibleText("Non partecipare alla sessione " + item.sequenceNumber());
        decline.setDisable(!item.programReady() || !ParticipantWorkspaceRules.canDeclineAttendance(item, now));
        decline.setOnAction(event -> changeAttendance.accept(item, AttendanceStatus.DECLINED));
        response.getChildren().add(new FlowPane(8, 8, confirm, decline));
        return response;
    }

    private static String appointment(LocalDateTime start, int minutes) {
        var end = start.plusMinutes(minutes);
        return ParticipantWorkspaceRules.DATE.format(start) + ", " + TIME.format(start) + "–"
                + (start.toLocalDate().equals(end.toLocalDate()) ? TIME.format(end)
                : ParticipantWorkspaceRules.DATE.format(end) + " " + TIME.format(end));
    }

    private static Label label(String text, String css) {
        var label = new Label(text);
        label.getStyleClass().add(css);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}
