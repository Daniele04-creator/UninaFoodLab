package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CourseWorkspaceControllerTest {

    @Test
    void scheduleCommunicationIdentifiesTheCourseAndBothAppointments() {
        assertThat(CoursePresentation.scheduleMessage("Impasti", LocalDateTime.parse("2026-09-14T18:00"),
                LocalDateTime.parse("2026-09-16T19:30")))
                .contains("Impasti", "14/09/2026 18:00", "16/09/2026 19:30");
        assertThat(CoursePresentation.scheduleMessage("Impasti", null,
                LocalDateTime.parse("2026-09-16T19:30")))
                .contains("aggiunta", "Impasti", "16/09/2026 19:30");
    }

    @Test
    void missingMenuDoesNotReplaceTheSessionStatus() {
        var incomplete = sessionAt(1, "2026-09-05T18:30", SessionMode.PRACTICAL, 0);
        var ready = sessionAt(2, "2026-09-05T18:30", SessionMode.PRACTICAL, 1);
        assertThat(CoursePresentation.sessionStatusLabel(incomplete)).isEqualTo("Programmata");
        assertThat(CoursePresentation.sessionStatusLabel(ready)).isEqualTo("Programmata");
        var cancelled = sessionAt(3, "2026-09-05T18:30", SessionMode.PRACTICAL, SessionStatus.CANCELLED, 0);
        assertThat(CoursePresentation.sessionStatusLabel(cancelled)).isEqualTo("Annullata");
        assertThat(CoursePresentation.recipeAssignmentLabel(cancelled)).isEqualTo("Nessuna ricetta");
    }

    @Test
    void calendarKeepsEverySessionReturnedByTheApplicationLayer() {
        var sessions = List.of(
                sessionAt(1, "2026-08-18T18:30"),
                sessionAt(2, "2026-08-22T10:00"),
                sessionAt(3, "2026-08-29T10:00"),
                sessionAt(4, "2026-09-05T10:00"),
                sessionAt(5, "2026-09-12T10:00"),
                sessionAt(6, "2026-09-19T10:00"));

        assertThat(CourseSessionPolicy.sessionsForDisplay(sessions))
                .containsExactlyElementsOf(sessions);
    }

    @Test
    void rescheduledSessionsAreDisplayedChronologicallyWithoutChangingTheirIdentity() {
        var moved = sessionAt(1, "2026-09-25T18:30");
        var earlier = sessionAt(2, "2026-09-18T18:30");
        assertThat(CourseSessionPolicy.sessionsForDisplay(List.of(moved, earlier)))
                .containsExactly(earlier, moved);
    }

    @Test
    void sessionPreviewUsesTheApprovedItalianDateAndTimeFormat() {
        assertThat(CoursePresentation.formatSessionPreview(
                        LocalDate.of(2026, 8, 24), "18:30"))
                .isEqualTo("24 agosto 2026, 18:30");
        assertThat(CoursePresentation.formatCourseStartDate(
                        LocalDate.of(2026, 8, 24)))
                .isEqualTo("24 agosto 2026");
    }

    @Test
    void courseSummaryUsesTheApprovedCompactWording() {
        assertThat(CoursePresentation.formatCourseSummary(2, 7, 120))
                .isEqualTo("2 sessioni, ogni 7 giorni, 120 min");
        assertThat(CoursePresentation.formatCourseSummary(1, 1, 60))
                .isEqualTo("1 sessione, ogni giorno, 60 min");
    }

    @Test
    void practicalModeUsesTheApprovedPresenceWording() {
        assertThat(CoursePresentation.modeLabel(SessionMode.PRACTICAL))
                .isEqualTo("In presenza");
    }

    @Test
    void calendarMakesRecipeAssignmentStateExplicitForEverySessionMode() {
        assertThat(CoursePresentation.recipeAssignmentLabel(
                        sessionAt(1, "2026-09-02T18:30", SessionMode.PRACTICAL, 0)))
                .isEqualTo("Nessuna ricetta");
        assertThat(CoursePresentation.recipeAssignmentLabel(
                        sessionAt(2, "2026-09-05T10:00", SessionMode.PRACTICAL, 1)))
                .isEqualTo("1 ricetta");
        assertThat(CoursePresentation.recipeAssignmentLabel(
                        sessionAt(4, "2026-09-12T18:30", SessionMode.ONLINE, 0)))
                .isEqualTo("Non prevista");
    }

    @Test
    void recipeCanChangeOnlyBeforeAScheduledPracticalSessionStarts() {
        var beforeStart = Clock.fixed(Instant.parse("2026-09-05T16:29:59Z"), ZoneOffset.UTC);
        var atStart = Clock.fixed(Instant.parse("2026-09-05T16:30:00Z"), ZoneOffset.UTC);
        var practical = sessionAt(
                1, "2026-09-05T18:30", SessionMode.PRACTICAL, SessionStatus.SCHEDULED, 1);
        var cancelled = sessionAt(
                2, "2026-09-05T18:30", SessionMode.PRACTICAL, SessionStatus.CANCELLED, 0);
        var online = sessionAt(
                3, "2026-09-05T18:30", SessionMode.ONLINE, SessionStatus.SCHEDULED, 0);

        assertThat(CourseSessionPolicy.isRecipeMutable(practical, beforeStart)).isTrue();
        assertThat(CourseSessionPolicy.isRecipeMutable(practical, atStart)).isFalse();
        assertThat(CourseSessionPolicy.isRecipeMutable(cancelled, beforeStart)).isFalse();
        assertThat(CourseSessionPolicy.isRecipeMutable(online, beforeStart)).isFalse();
    }

    @Test
    void recipeActionExplainsAdditionAndUnchangedSelection() {
        var assigned = UUID.randomUUID();
        var replacement = UUID.randomUUID();

        assertThat(CoursePresentation.recipeActionLabel(
                        List.of(), Optional.of(replacement)))
                .isEqualTo("Aggiungi");
        assertThat(CoursePresentation.recipeActionLabel(
                        List.of(assigned), Optional.of(assigned)))
                .isEqualTo("Già associata");
        assertThat(CoursePresentation.recipeActionLabel(
                        List.of(assigned), Optional.of(replacement)))
                .isEqualTo("Aggiungi");
    }

    @Test
    void programLabelsComeFromSessionStateInsteadOfDemoDates() {
        var formerSpecialDate = sessionAt(3, "2026-08-29T10:00");

        assertThat(CoursePresentation.sessionStatusLabel(formerSpecialDate))
                .isEqualTo("Programmata");
        assertThat(CoursePresentation.programSessionName(formerSpecialDate))
                .isEqualTo("Sessione 3");
        assertThat(CoursePresentation.programModeLabel(formerSpecialDate))
                .isEqualTo("Online");
    }

    @Test
    void recipeSelectionFallsBackToTheFirstSessionWhenChangingCourse() {
        var previousCourseSession = sessionAt(1, "2026-08-22T10:00");
        var firstCurrentSession = sessionAt(2, "2026-09-02T18:30");
        var secondCurrentSession = sessionAt(3, "2026-09-09T18:30");

        assertThat(CourseSessionPolicy.recipeSessionSelection(
                        List.of(firstCurrentSession, secondCurrentSession), previousCourseSession))
                .contains(firstCurrentSession);
    }

    @Test
    void sessionCalendarFitsItsContentAndCapsLongProgramsAtNineRows() {
        assertThat(SessionTableSizing.preferredHeight(0))
                .isEqualTo(SessionTableSizing.preferredHeight(1));
        assertThat(SessionTableSizing.preferredHeight(2))
                .isGreaterThan(SessionTableSizing.preferredHeight(1))
                .isLessThan(SessionTableSizing.preferredHeight(5));
        assertThat(SessionTableSizing.preferredHeight(9))
                .isGreaterThan(SessionTableSizing.preferredHeight(5));
        assertThat(SessionTableSizing.preferredHeight(20))
                .isEqualTo(SessionTableSizing.preferredHeight(9));
    }

    @Test
    void programmaticFilterRebuildSuppressesIntermediateReactiveReloads() {
        var guard = new ReactiveFilterGuard();
        var reloadedDuringRebuild = new AtomicBoolean();
        guard.markReady();

        guard.update(() -> reloadedDuringRebuild.set(guard.permitsReload(false)));

        assertThat(reloadedDuringRebuild).isFalse();
        assertThat(guard.permitsReload(false)).isTrue();
        assertThat(guard.permitsReload(true)).isFalse();
    }

    private static CourseSessionSummary sessionAt(int sequenceNumber, String scheduledAt) {
        return sessionAt(sequenceNumber, scheduledAt, SessionMode.ONLINE, 0);
    }

    private static CourseSessionSummary sessionAt(
            int sequenceNumber, String scheduledAt, SessionMode mode, int recipeCount) {
        return sessionAt(
                sequenceNumber, scheduledAt, mode, SessionStatus.SCHEDULED, recipeCount);
    }

    private static CourseSessionSummary sessionAt(
            int sequenceNumber,
            String scheduledAt,
            SessionMode mode,
            SessionStatus status,
            int recipeCount) {
        return new CourseSessionSummary(
                UUID.randomUUID(), sequenceNumber, LocalDateTime.parse(scheduledAt), mode,
                status, 60, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), recipeCount);
    }
}
