package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import static it.uninafoodlab.presentation.planning.PlanningLabels.sessionContext;
import static it.uninafoodlab.presentation.planning.PlanningLabels.planningSessionLabel;
import static it.uninafoodlab.presentation.planning.PlanningLabels.participantSummary;

final class PlanningResultsView {
    private static final String PARTICIPANTS_CAPTION = "partecipanti";
    private static final String ACTIVE_ENROLLMENTS_CAPTION = "iscrizioni attive";
    private final VBox missingMenusBox;
    private final TableView<IngredientRequirement> requirementTable;
    private final TableView<SessionParticipant> participantTable;
    private final Label participantSummaryLabel;
    private final javafx.scene.layout.FlowPane participantMetrics;
    private final Label sessionContextLabel;
    private final Label participantEmptyLabel;
    private final Label requirementSummaryLabel;
    private final Label requirementNoteLabel;
    private final TableColumn<IngredientRequirement, String> confirmedParticipantsColumn;
    PlanningResultsView(
            VBox missingMenusBox,
            TableView<IngredientRequirement> requirementTable,
            TableView<SessionParticipant> participantTable,
            Label participantSummaryLabel,
            javafx.scene.layout.FlowPane participantMetrics,
            Label sessionContextLabel,
            Label participantEmptyLabel,
            Label requirementSummaryLabel,
            Label requirementNoteLabel,
            TableColumn<IngredientRequirement, String> confirmedParticipantsColumn) {
        this.missingMenusBox = missingMenusBox;
        this.requirementTable = requirementTable;
        this.participantTable = participantTable;
        this.participantSummaryLabel = participantSummaryLabel;
        this.participantMetrics = participantMetrics;
        this.sessionContextLabel = sessionContextLabel;
        this.participantEmptyLabel = participantEmptyLabel;
        this.requirementSummaryLabel = requirementSummaryLabel;
        this.requirementNoteLabel = requirementNoteLabel;
        this.confirmedParticipantsColumn = confirmedParticipantsColumn;
    }

    private void addParticipantMetric(long value, String caption) {
        var count = new Label(Long.toString(value));
        count.getStyleClass().add("ops-participant-count");
        var label = value == 1 ? switch (caption) {
            case PARTICIPANTS_CAPTION -> "partecipante";
            case ACTIVE_ENROLLMENTS_CAPTION -> "iscrizione attiva";
            case "posti" -> "posto";
            default -> caption;
        } : caption;
        var text = new Label(label);
        text.getStyleClass().add("ops-participant-caption");
        var metric = new HBox(8, count, text);
        metric.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        metric.getStyleClass().add("ops-participant-metric");
        participantMetrics.getChildren().add(metric);
    }

    void clear(String summary) {
        missingMenusBox.getChildren().clear();
        missingMenusBox.setVisible(false);
        missingMenusBox.setManaged(false);
        requirementTable.getItems().clear();
        requirementTable.setPlaceholder(new Label(summary));
        participantTable.getItems().clear();
        participantSummaryLabel.setText(summary);
        participantSummaryLabel.setManaged(false);
        participantSummaryLabel.setVisible(false);
        participantMetrics.setManaged(false);
        participantMetrics.setVisible(false);
        sessionContextLabel.setText("");
        participantEmptyLabel.setText(summary);
        requirementSummaryLabel.setText("");
        requirementNoteLabel.setText("");
    }

    private void renderMissingMenus(List<CourseSessionSummary> sessions, UUID courseId, java.util.function.BiConsumer<UUID, UUID> sessionNavigation) {
        var missing = PlanningPresentation.missingMenus(sessions);
        missingMenusBox.getChildren().clear();
        missingMenusBox.setVisible(!missing.isEmpty());
        missingMenusBox.setManaged(!missing.isEmpty());
        if (missing.isEmpty()) return;
        var warning = new Label(missing.size() == sessions.size()
                ? "Fabbisogni non disponibili: i menu delle sessioni future sono ancora da preparare."
                : "Quantità parziali: manca il menu delle sessioni indicate.");
        warning.setWrapText(true);
        warning.getStyleClass().add("ops-muted");
        missingMenusBox.getChildren().add(warning);
        for (var session : missing) {
            var link = new Button("Apri sessione, " + planningSessionLabel(session));
            link.setWrapText(true);
            link.getStyleClass().add("ops-text-link");
            link.setOnAction(event -> sessionNavigation.accept(courseId, session.id()));
            link.setDisable(courseId == null);
            missingMenusBox.getChildren().add(link);
        }
    }

    void renderSession(CourseSessionSummary selectedSession, List<IngredientRequirement> requirements, List<SessionParticipant> participants) {
        requirementTable.getItems().setAll(requirements);
        participantTable.getItems().setAll(participants.stream()
                .sorted(java.util.Comparator.comparing(SessionParticipant::displayName,
                        java.text.Collator.getInstance(Locale.ITALIAN)))
                .toList());
        participantSummaryLabel.setText(participantSummary(participants));
        sessionContextLabel.setText(sessionContext(selectedSession));
        participantSummaryLabel.setManaged(true);
        participantSummaryLabel.setVisible(true);
        participantMetrics.getChildren().clear();
        addParticipantMetric(participants.size(), PARTICIPANTS_CAPTION);
        addParticipantMetric(participants.stream().filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE).count(), ACTIVE_ENROLLMENTS_CAPTION);
        selectedSession.capacity().ifPresent(capacity -> addParticipantMetric(capacity, "posti"));
        participantMetrics.setManaged(true);
        participantMetrics.setVisible(true);
        participantEmptyLabel.setText("Nessun partecipante per questa sessione");
        var confirmed = participants.stream()
                .filter(participant -> participant.enrollmentStatus() == EnrollmentStatus.ACTIVE)
                .filter(participant -> participant.attendanceStatus()
                        .filter(status -> status == AttendanceStatus.CONFIRMED).isPresent()).count();
        requirementSummaryLabel.setText(selectedSession.venue().orElse("Laboratorio")
                + ", Adesioni attuali: " + confirmed
                + (requirements.isEmpty() ? "" : ", Base delle quantità: "
                    + requirements.getFirst().confirmedParticipants() + " persone"));
        confirmedParticipantsColumn.setVisible(false);
        requirementTable.setPlaceholder(new Label("Nessun ingrediente nel menu di questa sessione."));
        var note = "Le quantità sono calcolate sulle adesioni confermate degli iscritti attivi.";
        if (selectedSession.recipeCount() == 0) {
            note = "Nessuna ricetta nel menu: i fabbisogni non sono ancora disponibili.";
        }
        if (selectedSession.status() == SessionStatus.CANCELLED) {
            note += " Sessione annullata: dati disponibili per consultazione.";
        } else if (!selectedSession.scheduledAt().isAfter(LocalDateTime.now(ZoneId.of("Europe/Rome")))) {
            note += " Consultazione della sessione in corso o passata.";
        }
        requirementNoteLabel.setText(note);
    }

    void renderCourseParticipants(List<it.uninafoodlab.application.planning.CourseParticipant> participants) {
        participantTable.getItems().setAll(participants.stream()
                .sorted(java.util.Comparator.comparing(
                        it.uninafoodlab.application.planning.CourseParticipant::displayName,
                        java.text.Collator.getInstance(Locale.ITALIAN)))
                .map(item -> new SessionParticipant(item.displayName(), item.enrollmentStatus(), Optional.empty()))
                .toList());
        participantSummaryLabel.setText("Iscrizioni al corso. Le adesioni riguardano soltanto le sessioni pratiche.");
        participantSummaryLabel.setVisible(true);
        participantSummaryLabel.setManaged(true);
        participantEmptyLabel.setText("Nessuna iscrizione per questo corso.");
        participantMetrics.getChildren().clear();
        addParticipantMetric(participants.size(), PARTICIPANTS_CAPTION);
        addParticipantMetric(participants.stream().filter(item -> item.enrollmentStatus() == EnrollmentStatus.ACTIVE).count(), ACTIVE_ENROLLMENTS_CAPTION);
        participantMetrics.setVisible(true);
        participantMetrics.setManaged(true);
    }

    void renderFutureRequirements(List<IngredientRequirement> requirements, List<CourseSessionSummary> futureSessions,
            UUID courseId, java.util.function.BiConsumer<UUID, UUID> sessionNavigation) {
        requirementTable.getItems().setAll(requirements);
        participantTable.getItems().clear();
        participantSummaryLabel.setText(
                "Seleziona una singola sessione per consultare i partecipanti.");
        participantEmptyLabel.setText("Seleziona una singola sessione");
        requirementSummaryLabel.setText(PlanningPresentation.menuCoverage(futureSessions));
        renderMissingMenus(futureSessions, courseId, sessionNavigation);
        confirmedParticipantsColumn.setVisible(false);
        sessionContextLabel.setText("Totale delle sessioni pratiche future del corso");
        requirementTable.setPlaceholder(new Label(futureSessions.isEmpty()
                ? "Nessuna sessione pratica futura per questo corso."
                : "Nessun ingrediente nei menu delle sessioni future."));
        requirementNoteLabel.setText("Totale delle sole sessioni pratiche future, escluse quelle annullate. "
                + (!requirements.isEmpty() && requirements.stream().allMatch(item -> item.confirmedParticipants() == 0)
                    ? "Nessuna adesione confermata alle sessioni con menu disponibile: quantità pari a zero."
                    : "Le quantità sono calcolate sulle adesioni confermate degli iscritti attivi."));
    }

}
