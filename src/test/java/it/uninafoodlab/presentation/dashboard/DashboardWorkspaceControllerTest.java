package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardWorkspaceControllerTest {
    @Test
    void nextAgendaSessionIncludesCompletedSessionsAfterTheSelectedHistoricalDay() {
        var selectedDay = LocalDate.of(2026, 9, 30);
        var calendar = new DashboardCalendar<DashboardWorkspaceController.DashboardSession>(
                selectedDay, item -> item.session().scheduledAt());
        var first = dashboardSession("Pasta", selectedDay.plusDays(1).atTime(9, 0), 1);
        var completed = new DashboardWorkspaceController.DashboardSession(first.courseId(), first.courseTitle(),
                withState(first.session(), SessionMode.PRACTICAL, SessionStatus.COMPLETED, 1));
        var cancelledSource = dashboardSession("Annullata", selectedDay.plusDays(1).atTime(8, 0), 0);
        var cancelled = new DashboardWorkspaceController.DashboardSession(cancelledSource.courseId(),
                cancelledSource.courseTitle(), withState(cancelledSource.session(), SessionMode.PRACTICAL, SessionStatus.CANCELLED, 0));
        var later = dashboardSession("Pizza", selectedDay.plusDays(2).atTime(18, 0), 0);
        calendar.setSessions(List.of(later, cancelled, completed));

        assertThat(DashboardSessionPolicy.nextAgendaSession(calendar)).contains(completed);
        calendar.selectDay(completed.session().scheduledAt().toLocalDate());
        assertThat(DashboardSessionPolicy.nextAgendaSession(calendar)).contains(later);
        calendar.selectDay(later.session().scheduledAt().toLocalDate());
        assertThat(DashboardSessionPolicy.nextAgendaSession(calendar)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void operationalQuantitiesKeepWholeDosesAndNeverRoundDisplayedValues() {
        assertThat(it.uninafoodlab.presentation.common.QuantityFormat.quantity(new java.math.BigDecimal("125.000"), it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM)).isEqualTo("125 g");
        assertThat(it.uninafoodlab.presentation.common.QuantityFormat.quantity(new java.math.BigDecimal("2"), it.uninafoodlab.domain.catalog.MeasurementUnit.KILOGRAM)).isEqualTo("2000 g");
        assertThat(it.uninafoodlab.presentation.common.QuantityFormat.quantity(new java.math.BigDecimal("10.12545"), it.uninafoodlab.domain.catalog.MeasurementUnit.GRAM)).isEqualTo("10,12545 g");
    }


    @Test
    void menuPreparationListsEveryMissingMenuInDateOrderWithItsOwnDestination() {
        var now = LocalDateTime.of(2026, 9, 12, 12, 0);
        var first = dashboardSession("Pasta", now.plusDays(2), 0);
        var second = dashboardSession("Pizza", now.plusDays(11), 0);
        var ready = dashboardSession("Dolci", now.plusDays(1), 2);
        var past = dashboardSession("Pane", now.minusDays(1), 0);

        assertThat(DashboardSessionPolicy.menusToPrepare(now,
                List.of(second, ready, past, first))).containsExactly(first, second);
        assertThat(DashboardSessionPolicy.menusToPrepare(now, List.of(ready, past))).isEmpty();
        var assigned = new DashboardWorkspaceController.DashboardSession(first.courseId(), first.courseTitle(),
                withState(first.session(), SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 1));
        assertThat(DashboardSessionPolicy.menusToPrepare(now, List.of(second, assigned)))
                .containsExactly(second);
    }

    @Test
    void onlyScheduledPracticalSessionsNeedMenuPreparation() {
        var now = LocalDateTime.of(2026, 9, 12, 12, 0);
        var session = practicalSession(now.plusDays(2), now.plusDays(1), 0);
        for (var status : List.of(SessionStatus.CANCELLED, SessionStatus.COMPLETED)) {
            assertThat(DashboardSessionPolicy.requiresRecipe(now,
                    withState(session, SessionMode.PRACTICAL, status, 0))).isFalse();
        }
        assertThat(DashboardSessionPolicy.requiresRecipe(now,
                withState(session, SessionMode.ONLINE, SessionStatus.SCHEDULED, 0))).isFalse();
    }

    private static CourseSessionSummary withState(CourseSessionSummary session,
            SessionMode mode, SessionStatus status, int recipeCount) {
        return new CourseSessionSummary(session.id(), session.sequenceNumber(), session.scheduledAt(), mode,
                status, session.durationMinutes(), session.onlineUrl(), session.venue(), session.capacity(),
                session.attendanceClosesAt(), session.completedAt(), recipeCount);
    }

    private static DashboardWorkspaceController.DashboardSession dashboardSession(
            String title, LocalDateTime at, int recipes) {
        return new DashboardWorkspaceController.DashboardSession(UUID.randomUUID(), title,
                practicalSession(at, at.minusHours(1), recipes));
    }

    @Test
    void formatsTheActualDayShownInTheDashboardHeader() {
        assertThat(DashboardWorkspaceController.formatDashboardDate(LocalDate.of(2026, 8, 21)))
                .isEqualTo("Venerdì 21 agosto 2026");
    }

    @Test
    void recipeWarningDisappearsAsSoonAsARecipeIsAssigned() {
        var now = LocalDateTime.of(2026, 8, 23, 12, 0);

        assertThat(DashboardSessionPolicy.requiresRecipe(now, practicalSession(
                LocalDateTime.of(2026, 8, 24, 10, 0),
                LocalDateTime.of(2026, 8, 23, 10, 0), 0))).isTrue();
        assertThat(DashboardSessionPolicy.requiresRecipe(now, practicalSession(
                LocalDateTime.of(2026, 8, 24, 10, 0),
                LocalDateTime.of(2026, 8, 23, 10, 0), 1))).isFalse();
    }

    @Test
    void dashboardDoesNotCreateChefAttendanceWorkAfterASessionEnds() throws IOException {
        var source = Files.readString(Path.of(
                "src/main/java/it/uninafoodlab/presentation/dashboard/DashboardWorkspaceController.java"));

        assertThat(source)
                .doesNotContain("requiresAttendanceRegister", "attendanceRegisterAction",
                        "openAttendanceRegister", "Presenze da registrare",
                        "sessioni con presenze da registrare");
    }

    @Test
    void preparationQueueIncludesSessionStartingNowAheadOfFutureSessions() {
        var now = LocalDateTime.of(2026, 9, 17, 12, 0);
        var starting = dashboardSession("Pasta", now, 0);
        var future = dashboardSession("Pizza", now.plusHours(2), 0);
        var past = dashboardSession("Pane", now.minusMinutes(1), 0);

        assertThat(DashboardSessionPolicy.menusToPrepare(now, List.of(future, past, starting)))
                .containsExactly(starting, future);
    }

    private static CourseSessionSummary practicalSession(
            LocalDateTime scheduledAt, LocalDateTime attendanceClosesAt, int recipeCount) {
        return new CourseSessionSummary(
                UUID.randomUUID(), 1, scheduledAt, SessionMode.PRACTICAL,
                SessionStatus.SCHEDULED, 150, Optional.empty(), Optional.of("Laboratorio Napoli"),
                Optional.of(14), Optional.of(attendanceClosesAt), Optional.empty(), recipeCount);
    }
}
