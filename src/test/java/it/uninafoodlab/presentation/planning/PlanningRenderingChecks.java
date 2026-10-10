package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

final class PlanningRenderingChecks {
    private PlanningRenderingChecks() {}

    static void verify() {
        var participants = new TableView<SessionParticipant>();
        var requirements = new TableView<IngredientRequirement>();
        participants.setFixedCellSize(46);
        requirements.setFixedCellSize(46);
        var ingredient = new TableColumn<IngredientRequirement, String>("Ingrediente");
        var confirmed = new TableColumn<IngredientRequirement, String>("Confermati");
        var quantity = new TableColumn<IngredientRequirement, String>("Quantità");
        var name = new TableColumn<SessionParticipant, String>("Nome");
        var enrollment = new TableColumn<SessionParticipant, String>("Iscrizione");
        var attendance = new TableColumn<SessionParticipant, String>("Adesione");
        requirements.getColumns().addAll(List.of(ingredient, confirmed, quantity));
        participants.getColumns().addAll(List.of(name, enrollment, attendance));
        new PlanningTables(requirements, participants, ingredient, confirmed, quantity,
                name, enrollment, attendance).configure();
        var metrics = new FlowPane();
        var summary = new Label();
        var note = new Label();
        var view = new PlanningResultsView(new VBox(), requirements, participants,
                new Label(), metrics, new Label(), new Label(), summary, note, confirmed);
        var stage = new Stage();
        var root = new VBox(metrics, participants, note);
        stage.setScene(new Scene(root, 900, 500));
        try {
            var people = List.of(
                    new SessionParticipant("Anna", EnrollmentStatus.ACTIVE, Optional.of(AttendanceStatus.CONFIRMED)),
                    new SessionParticipant("Bruno", EnrollmentStatus.WITHDRAWN, Optional.of(AttendanceStatus.DECLINED)),
                    new SessionParticipant("Carla", EnrollmentStatus.ACTIVE, Optional.empty()),
                    new SessionParticipant("Diego", EnrollmentStatus.WITHDRAWN, Optional.of(AttendanceStatus.CONFIRMED)));
            var frozenRequirements = List.of(new IngredientRequirement("Farina",
                    it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM, 2,
                    new java.math.BigDecimal("200")));
            view.renderSession(session(SessionStatus.SCHEDULED, 1, true), frozenRequirements, people);
            stage.show();
            root.applyCss();
            root.layout();
            check(summary.getText().contains("Adesioni attuali: 1"), "Only active confirmed attendance counts as current");
            check(summary.getText().contains("Base delle quantità: 2 persone"),
                    "Frozen preparation retains its original count after withdrawal");
            check(note.getText().contains("calcolate sulle adesioni confermate"),
                    "Quantity basis is distinguished from current attendance");
            check(hasStatus(participants, "✓", "ops-status-positive"), "Active or confirmed rows show positive status");
            check(hasStatus(participants, "×", "ops-status-negative"), "Withdrawn or declined rows show negative status");
            check(hasStatus(participants, "−", "ops-status-neutral"), "Unanswered attendance shows neutral status");

            view.renderSession(session(SessionStatus.CANCELLED, 1, true), List.of(), List.of(people.getFirst()));
            check(note.getText().contains("Sessione annullata: dati disponibili per consultazione."),
                    "Cancelled session keeps historical data with an explicit warning");
            check(metrics.lookupAll(".ops-participant-caption").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("partecipante")), "One participant uses singular caption");
            check(metrics.lookupAll(".ops-participant-caption").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getText().equals("iscrizione attiva")), "One active enrollment uses singular caption");

            view.renderSession(session(SessionStatus.COMPLETED, 1, false), List.of(), List.of());
            check(summary.getText().contains("Adesioni attuali: 0"), "Zero current confirmations are explicit");
            check(note.getText().endsWith("Consultazione della sessione in corso o passata."),
                    "Past session is explicitly presented as consultation");
            view.renderSession(session(SessionStatus.SCHEDULED, 0, true), List.of(), List.of());
            check(note.getText().startsWith("Nessuna ricetta nel menu"), "Missing menu takes precedence over missing confirmations");
        } finally {
            stage.close();
        }
    }

    private static boolean hasStatus(TableView<?> table, String symbol, String style) {
        return table.lookupAll(".ops-status-icon").stream().map(Label.class::cast)
                .anyMatch(label -> label.getText().equals(symbol) && label.getStyleClass().contains(style));
    }

    private static CourseSessionSummary session(SessionStatus status, int recipes, boolean future) {
        var time = LocalDateTime.now(ZoneId.of("Europe/Rome")).plusDays(future ? 1 : -1);
        return new CourseSessionSummary(UUID.randomUUID(), 1, time, SessionMode.PRACTICAL, status,
                60, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12), Optional.empty(),
                Optional.empty(), List.of(), recipes);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
