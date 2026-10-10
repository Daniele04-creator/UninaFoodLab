package it.uninafoodlab.presentation.planning;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.planning.SessionParticipant;
import it.uninafoodlab.application.planning.IngredientRequirement;
import it.uninafoodlab.domain.catalog.MeasurementUnit;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanningWorkspaceControllerTest {
    @Test
    void exportKeepsSessionOrderAndUsesTheFirstSnapshotForEachIdentity() {
        var first = session(LocalDateTime.of(2026, 9, 14, 18, 0), SessionMode.PRACTICAL);
        var second = session(LocalDateTime.of(2026, 9, 15, 18, 0), SessionMode.PRACTICAL);
        var original = new it.uninafoodlab.application.planning.SessionPlanningSnapshot(
                first.id(), first.scheduledAt(), 0, "SCHEDULED", "CURRENT", List.of());
        var repeated = new it.uninafoodlab.application.planning.SessionPlanningSnapshot(
                first.id(), first.scheduledAt().plusDays(1), 1, "CANCELLED", "CURRENT", List.of());
        var last = new it.uninafoodlab.application.planning.SessionPlanningSnapshot(
                second.id(), second.scheduledAt(), 0, "COMPLETED", "CURRENT", List.of());
        assertThat(PlanningDocuments.exportContext(List.of(second, first), List.of(original, repeated, last)))
                .isEqualTo("15/09/2026, 18:00, Svolta; 14/09/2026, 18:00, Programmata");
    }

    @Test
    void exportRejectsMissingSnapshotsInsteadOfPrintingAnIncompleteContext() {
        var session = session(LocalDateTime.of(2026, 9, 14, 18, 0), SessionMode.PRACTICAL);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PlanningDocuments.exportContext(List.of(session), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
    @org.junit.jupiter.api.Test
    void emptyParticipationStillShowsAllThreeAttendanceCounts() {
        assertThat(PlanningLabels.participantSummary(java.util.List.of()))
                .isEqualTo("0 confermati, 0 senza scelta, 0 rinunce");
    }

    @Test
    void menuCoverageDistinguishesMissingMenusFromAvailableMenusWithoutAttendance() {
        var missing = new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.of(2026, 9, 23, 18, 0),
                SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 90, Optional.empty(), Optional.of("Lab"), Optional.of(12), 0);
        var available = session(LocalDateTime.of(2026, 9, 14, 18, 0), SessionMode.PRACTICAL);
        assertThat(PlanningPresentation.menuCoverage(List.of(available, missing)))
                .isEqualTo("Menu disponibili: 1 su 2 sessioni");
        assertThat(PlanningPresentation.missingMenus(List.of(available, missing))).containsExactly(missing);
        assertThat(PlanningPresentation.missingMenus(List.of(available))).isEmpty();
        assertThat(PlanningPresentation.menuCoverage(List.of())).isEqualTo("Nessuna sessione pratica futura");
    }

    @Test
    void rendersCalendarChangesForTheSameSessionIdentity() {
        var original = session(LocalDateTime.of(2026, 9, 14, 10, 0), SessionMode.PRACTICAL);
        var updated = new CourseSessionSummary(original.id(), original.sequenceNumber(),
                LocalDateTime.of(2026, 9, 18, 15, 0), SessionMode.PRACTICAL, SessionStatus.CANCELLED,
                120, Optional.empty(), Optional.of("Laboratorio nuovo"), Optional.of(20),
                Optional.of(LocalDateTime.of(2026, 9, 17, 18, 0)), Optional.empty(), 1);

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(PlanningLabels.planningSessionLabel(updated))
                .isEqualTo("18 settembre 2026, 15:00, Annullata");
        assertThat(PlanningLabels.sessionContext(updated))
                .isEqualTo("Laboratorio nuovo, Chiusura adesioni: 17 settembre 2026 alle 18:00");
        assertThat(PlanningLabels.sessionContext(updated))
                .isNotEqualTo(PlanningLabels.sessionContext(original));
    }

    @Test
    void monthNavigationCrossesYearAndStopsAtSupportedBoundaries() {
        assertThat(ReportPeriodSelector.adjacentReportPeriod(YearMonth.of(2026, 12), 1))
                .contains(YearMonth.of(2027, 1));
        assertThat(ReportPeriodSelector.adjacentReportPeriod(YearMonth.of(2026, 1), -1))
                .contains(YearMonth.of(2025, 12));
        assertThat(ReportPeriodSelector.adjacentReportPeriod(YearMonth.of(2000, 1), -1)).isEmpty();
        assertThat(ReportPeriodSelector.adjacentReportPeriod(YearMonth.of(2200, 12), 1)).isEmpty();
    }

    @Test
    void reportYearSelectionPreservesHistoryAndPreventsUnsupportedPeriods() {
        var years = ReportPeriodSelector.reportYears();
        assertThat(years).hasSize(201).startsWith(2000, 2001).endsWith(2199, 2200)
                .contains(2026).doesNotContain(1999, 2201);
    }

    @Test
    void summarizesParticipantAdhesionsWithoutImplyingChefApproval() {
        var participants = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> new SessionParticipant(
                        "Partecipante " + (index + 1),
                        EnrollmentStatus.ACTIVE,
                        index < 12
                                ? Optional.of(AttendanceStatus.CONFIRMED)
                                : Optional.empty()))
                .toList();

        assertThat(PlanningLabels.participantSummary(participants))
                .isEqualTo("12 confermati, 2 senza scelta, 0 rinunce");
    }

    @Test
    void participantSummaryDoesNotCountDeclinedOrWithdrawnPeopleAsWaiting() {
        var participants = List.of(
                new SessionParticipant("Confermato", EnrollmentStatus.ACTIVE,
                        Optional.of(AttendanceStatus.CONFIRMED)),
                new SessionParticipant("In attesa", EnrollmentStatus.ACTIVE, Optional.empty()),
                new SessionParticipant("Ha rinunciato", EnrollmentStatus.ACTIVE,
                        Optional.of(AttendanceStatus.DECLINED)),
                new SessionParticipant("Ritirato", EnrollmentStatus.WITHDRAWN, Optional.empty()));

        assertThat(PlanningLabels.participantSummary(participants))
                .isEqualTo("1 confermato, 1 senza scelta, 1 rinuncia, 1 ritirato");
    }

    @Test
    void planningLabelsAndQuantitiesComeFromApplicationData() {
        var session = session(LocalDateTime.of(2026, 8, 22, 10, 0), SessionMode.PRACTICAL);
        var oil = new IngredientRequirement(
                "Olio extravergine", MeasurementUnit.MILLILITER, 12, new BigDecimal("1250"));

        assertThat(PlanningLabels.planningSessionLabel(session))
                .isEqualTo("22 agosto 2026, 10:00");
        assertThat(PlanningLabels.quantityLabel(oil))
                .isEqualTo("1250 ml");
        assertThat(PlanningLabels.quantityLabel(new IngredientRequirement(
                "Spezie", MeasurementUnit.GRAM, 1, BigDecimal.ONE)))
                .isEqualTo("1 g");
    }

    @Test
    void formatsEveryRecipeStatisticRequiredByTheTrace() {
        var report = new it.uninafoodlab.application.planning.MonthlyReport(
                YearMonth.of(2026, 8), 2, 1, 3,
                Optional.of(new BigDecimal("1.50")), Optional.of(1), Optional.of(2));

        assertThat(PlanningLabels.recipeStatisticsLabel(report))
                .isEqualTo("Media 1,5, Min 1, Max 2");
    }

    @Test
    void roundsLongRecipeAveragesToOneDecimalWithoutLosingMinimumAndMaximum() {
        var report = new it.uninafoodlab.application.planning.MonthlyReport(
                YearMonth.of(2026, 8), 2, 0, 7,
                Optional.of(new java.math.BigDecimal("1.1428571428571429")),
                Optional.of(1), Optional.of(2));

        assertThat(PlanningLabels.recipeStatisticsLabel(report))
                .isEqualTo("Media 1,1, Min 1, Max 2");
        var half = new it.uninafoodlab.application.planning.MonthlyReport(
                YearMonth.of(2026, 8), 2, 0, 7,
                Optional.of(new java.math.BigDecimal("1.25")), Optional.of(1), Optional.of(2));
        assertThat(PlanningLabels.recipeStatisticsLabel(half))
                .isEqualTo("Media 1,3, Min 1, Max 2");
        assertThat(half.averageRecipes()).contains(new java.math.BigDecimal("1.25"));
    }

    @Test
    void reportAverageOmitsUnneededDecimalForWholeRecipeCounts() {
        var report = new it.uninafoodlab.application.planning.MonthlyReport(
                YearMonth.of(2026, 8), 2, 0, 7,
                Optional.of(new java.math.BigDecimal("2.0")), Optional.of(2), Optional.of(2));

        assertThat(PlanningLabels.recipeStatisticsLabel(report))
                .isEqualTo("Media 2, Min 2, Max 2");
    }

    @Test
    void rendersMissingRecipeStatisticsAsUnavailableInsteadOfZero() {
        var report = new it.uninafoodlab.application.planning.MonthlyReport(
                YearMonth.of(2026, 8), 0, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(PlanningLabels.recipeStatisticsLabel(report))
                .isEqualTo("Media —, Min —, Max —");
    }

    private static CourseSessionSummary session(LocalDateTime dateTime, SessionMode mode) {
        return new CourseSessionSummary(
                UUID.randomUUID(),
                1,
                dateTime,
                mode,
                SessionStatus.SCHEDULED,
                120,
                mode == SessionMode.ONLINE
                        ? Optional.of(URI.create("https://meet.uninafoodlab.local/demo"))
                        : Optional.empty(),
                mode == SessionMode.PRACTICAL ? Optional.of("Laboratorio Napoli") : Optional.empty(),
                mode == SessionMode.PRACTICAL ? Optional.of(14) : Optional.empty(),
                1);
    }
}
