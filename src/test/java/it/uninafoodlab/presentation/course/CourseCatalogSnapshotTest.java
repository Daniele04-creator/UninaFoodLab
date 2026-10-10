package it.uninafoodlab.presentation.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseCatalogSnapshotTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void nextAppointmentUsesActualScheduleAndSkipsCancelledPastAndCompletedSessions() {
        var actual = session("2026-10-08T17:45", SessionStatus.SCHEDULED);
        var sessions = List.of(session("2026-10-01T13:00", SessionStatus.CANCELLED),
                session("2026-10-01T13:00", SessionStatus.SCHEDULED),
                session("2026-09-30T17:00", SessionStatus.SCHEDULED),
                session("2026-10-03T17:00", SessionStatus.COMPLETED),
                session("2026-10-22T17:00", SessionStatus.SCHEDULED), actual);

        assertThat(CourseCatalogSnapshot.nextSession(sessions, clock)).contains(actual);
        assertThat(CourseCatalogSnapshot.nextSession(List.of(sessions.getFirst()), clock)).isEmpty();
    }

    @Test
    void retainsPageTotalsAndCopiesAuthorizedCourseSessions() {
        var first = course("Pasta fresca");
        var second = course("Pane");
        var firstSession = session("2026-10-09T11:00", SessionStatus.SCHEDULED);
        var sessions = new ArrayList<>(List.of(firstSession));
        var snapshot = new CourseCatalogSnapshot(List.of(first, second),
                java.util.Map.of(first.id(), sessions, second.id(), List.of()), 2, 26);
        sessions.clear();

        assertThat(snapshot.page()).isEqualTo(2);
        assertThat(snapshot.totalCourses()).isEqualTo(26);
        assertThat(snapshot.courses()).containsExactly(first, second);
        assertThat(snapshot.sessions().get(first.id())).containsExactly(firstSession);
        assertThat(snapshot.sessions().get(second.id())).isEmpty();
    }

    private static CourseSessionSummary session(String date, SessionStatus status) {
        return new CourseSessionSummary(UUID.randomUUID(), 1, LocalDateTime.parse(date),
                SessionMode.PRACTICAL, status, 90, Optional.empty(), Optional.of("Laboratorio"),
                Optional.of(12), 0);
    }

    private static CourseSummary course(String title) {
        return new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina", new CourseTitle(title),
                "Descrizione", LocalDate.of(2026, 10, 2), LocalTime.of(17, 0), 7, 3,
                Optional.of(LocalDateTime.of(2026, 10, 22, 18, 30)), CourseStatus.PUBLISHED,
                IngredientMargin.zero());
    }
}
