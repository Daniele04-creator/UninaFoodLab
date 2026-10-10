package it.uninafoodlab.presentation.participant;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantWorkspaceTest {

    @Test
    void nextAppointmentUsesPublishedScheduleAndExcludesCancelledAndEndedSessions() {
        var now = LocalDateTime.of(2026, 10, 1, 12, 0);
        var cancelled = appointment(now.plusMinutes(10), 60, SessionStatus.CANCELLED);
        var ended = appointment(now.minusHours(2), 60, SessionStatus.SCHEDULED);
        var later = appointment(now.plusDays(1), 60, SessionStatus.SCHEDULED);
        var running = appointment(now.minusMinutes(10), 60, SessionStatus.SCHEDULED);
        assertThat(ParticipantWorkspaceRules.nextAppointment(
                List.of(cancelled, later, ended, running), now)).contains(running);
        assertThat(ParticipantWorkspaceRules.nextAppointment(List.of(cancelled, ended), now)).isEmpty();
    }

    private static PublishedCourseSessionSummary appointment(
            LocalDateTime scheduledAt, int duration, SessionStatus status) {
        return new PublishedCourseSessionSummary(UUID.randomUUID(), 1, scheduledAt,
                SessionMode.PRACTICAL, duration, Optional.empty(), Optional.of("Atelier 1"),
                Optional.of(6), Optional.empty(), status);
    }

    @Test
    void courseSearchLooksAcrossTitleCategoryChefAndDescriptionAndKeepsDateOrder() {
        var older = course("Pane di casa", "Panificazione", "Chef Alba", "Impasti lenti",
                LocalDate.of(2026, 10, 2));
        var newer = course("Salse d'autunno", "Cucina vegetale", "Chef Bruno", "Pomodoro e spezie",
                LocalDate.of(2026, 11, 2));

        assertThat(ParticipantWorkspaceRules.filterCourses(List.of(newer, older), "  IMPASTI "))
                .containsExactly(older);
        assertThat(ParticipantWorkspaceRules.filterCourses(List.of(newer, older), "chef bruno"))
                .containsExactly(newer);
        assertThat(ParticipantWorkspaceRules.filterCourses(List.of(newer, older), ""))
                .containsExactly(older, newer);
    }

    @Test
    void attendanceActionsUseDistinctConfirmAndDeclineGuards() {
        var now = LocalDateTime.of(2026, 10, 1, 10, 0);
        var open = attendance(2, 1, now.plusHours(4), 1, Optional.empty());
        var full = attendance(1, 1, now.plusHours(4), 1, Optional.empty());
        var closed = attendance(2, 1, now.minusMinutes(1), 1, Optional.empty());
        var notReady = attendance(2, 1, now.plusHours(4), 0, Optional.empty());
        var confirmedFull = attendance(1, 1, now.plusHours(4), 1, Optional.of(AttendanceStatus.CONFIRMED));
        var declinedFull = attendance(1, 1, now.plusHours(4), 1, Optional.of(AttendanceStatus.DECLINED));

        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(open, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(open, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canChangeAttendance(
                full, AttendanceStatus.CONFIRMED, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canChangeAttendance(
                full, AttendanceStatus.DECLINED, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(full, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(full, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(closed, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(closed, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(notReady, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(notReady, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(confirmedFull, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(confirmedFull, now)).isTrue();
        assertThat(ParticipantWorkspaceRules.canConfirmAttendance(declinedFull, now)).isFalse();
        assertThat(ParticipantWorkspaceRules.canDeclineAttendance(declinedFull, now)).isFalse();
    }

    @Test
    void overlappingConfirmationRequiresAnExplicitDecisionBeforePersistence() {
        var session = attendance(2, 1, LocalDateTime.of(2026, 10, 1, 12, 0), 1, Optional.empty());
        var conflict = Optional.of("il corso \"Pasta fresca\" alle 10:30");

        assertThat(ParticipantWorkspaceRules.requiresConflictConfirmation(
                AttendanceStatus.CONFIRMED, conflict)).isTrue();
        assertThat(ParticipantWorkspaceRules.canPersistAttendanceChange(
                AttendanceStatus.CONFIRMED, conflict, false)).isFalse();
        assertThat(ParticipantWorkspaceRules.canPersistAttendanceChange(
                AttendanceStatus.CONFIRMED, conflict, true)).isTrue();
        assertThat(ParticipantWorkspaceRules.canPersistAttendanceChange(
                AttendanceStatus.DECLINED, conflict, false)).isTrue();
        assertThat(ParticipantWorkspaceRules.conflictPrompt(session, conflict.orElseThrow()))
                .contains("14:00", "Atelier 1", "Pasta fresca");
    }

    @Test
    void onlineLinkIsAvailableOnlyWhenTheAuthorizedCourseDetailSuppliesIt() {
        var enrolled = onlineSession(Optional.of(URI.create("https://meet.example.test/lab")));
        var notEnrolled = onlineSession(Optional.empty());

        assertThat(ParticipantWorkspaceRules.authorizedOnlineLink(enrolled))
                .contains("https://meet.example.test/lab");
        assertThat(ParticipantWorkspaceRules.authorizedOnlineLink(notEnrolled)).isEmpty();
    }

    @Test
    void scheduleConflictTreatsACommonBoundaryAsAvailable() {
        var first = new PracticalAttendanceSummary(UUID.randomUUID(), 1,
                LocalDateTime.of(2026, 10, 1, 10, 0), 60, "Atelier 1", 4, 1, 3,
                LocalDateTime.of(2026, 10, 1, 9, 0), Optional.of(AttendanceStatus.CONFIRMED), 1);
        var adjacent = new PracticalAttendanceSummary(UUID.randomUUID(), 1,
                LocalDateTime.of(2026, 10, 1, 11, 0), 60, "Atelier 2", 4, 1, 3,
                LocalDateTime.of(2026, 10, 1, 9, 0), Optional.empty(), 1);
        var overlapping = new PracticalAttendanceSummary(UUID.randomUUID(), 1,
                LocalDateTime.of(2026, 10, 1, 10, 59), 60, "Atelier 2", 4, 1, 3,
                LocalDateTime.of(2026, 10, 1, 9, 0), Optional.empty(), 1);

        assertThat(ParticipantWorkspaceRules.overlaps(first, adjacent)).isFalse();
        assertThat(ParticipantWorkspaceRules.overlaps(first, overlapping)).isTrue();
    }

    @Test
    void participantWorkspaceResourceExposesTheThreeOperationalSections() throws Exception {
        try (var stream = ParticipantWorkspaceTest.class.getResourceAsStream(
                "/it/uninafoodlab/presentation/participant/participant-workspace.fxml")) {
            assertThat(stream).isNotNull();
            var fxml = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(fxml)
                    .contains("onAction=\"#showBrowse\"", "onAction=\"#showMine\"",
                            "onAction=\"#showNotifications\"")
                    .contains("fx:id=\"courseList\"", "fx:id=\"attendanceList\"",
                            "fx:id=\"notificationList\"")
                    .contains("onAction=\"#enroll\"", "onAction=\"#withdraw\"",
                            "onAction=\"#markRead\"",
                            "fx:id=\"openNotificationCourseButton\"");
            assertThat(fxml).contains("Corsi disponibili e storico");
        }
    }

    private static PublishedCourseSessionSummary onlineSession(Optional<URI> onlineUrl) {
        return new PublishedCourseSessionSummary(UUID.randomUUID(), 1,
                LocalDateTime.of(2026, 10, 1, 18, 0), SessionMode.ONLINE, 90, onlineUrl,
                Optional.empty(), Optional.empty(), Optional.empty(), SessionStatus.SCHEDULED);
    }

    private static PublishedCourseSummary course(
            String title, String category, String chef, String description, LocalDate startDate) {
        return new PublishedCourseSummary(UUID.randomUUID(), UUID.randomUUID(), category, chef,
                CourseTitle.of(title), description, startDate, LocalTime.of(18, 0), 2);
    }

    private static PracticalAttendanceSummary attendance(
            int capacity, int confirmed, LocalDateTime cutoff, int recipeCount,
            Optional<AttendanceStatus> status) {
        var scheduled = cutoff.plusHours(2);
        return new PracticalAttendanceSummary(UUID.randomUUID(), 1, scheduled, 120, "Atelier 1",
                capacity, confirmed, capacity - confirmed, cutoff, status, recipeCount);
    }
}
