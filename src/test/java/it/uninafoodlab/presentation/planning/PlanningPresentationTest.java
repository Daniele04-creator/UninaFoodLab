package it.uninafoodlab.presentation.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanningPresentationTest {
    @Test
    void openCoursesKeepCurrentAndFuturePublishedCoursesWhileArchiveUsesTheLastSessionEnd() {
        var now = LocalDateTime.of(2026, 9, 9, 12, 0);
        var current = course("In corso", CourseStatus.PUBLISHED, Optional.of(now.plusMinutes(1)));
        var future = course("Futuro", CourseStatus.PUBLISHED, Optional.of(now.plusDays(8)));
        var ended = course("Terminato", CourseStatus.PUBLISHED, Optional.of(now));
        var cancelled = course("Annullato", CourseStatus.CANCELLED, Optional.of(now.plusDays(4)));
        var completed = course("Completato", CourseStatus.COMPLETED, Optional.of(now.plusDays(4)));
        var noRemainingSessions = course("Sessioni annullate", CourseStatus.PUBLISHED, Optional.empty());
        var draft = course("Bozza", CourseStatus.DRAFT, Optional.of(now.plusDays(3)));
        var courses = List.of(current, ended, cancelled, future, noRemainingSessions, completed, draft);

        assertThat(PlanningPresentation.visibleCourses(courses, PlanningPresentation.CourseScope.OPEN, now))
                .containsExactly(current, future);
        assertThat(PlanningPresentation.visibleCourses(courses, PlanningPresentation.CourseScope.ARCHIVE, now))
                .containsExactly(ended, cancelled, noRemainingSessions, completed);
    }

    @Test
    void historicalSessionDestinationOpensArchiveAndMissingDestinationKeepsTheCurrentScope() {
        var now = LocalDateTime.of(2026, 9, 9, 12, 0);
        var historical = course("Storico", CourseStatus.PUBLISHED, Optional.of(now.minusDays(1)));
        var current = course("Attuale", CourseStatus.PUBLISHED, Optional.of(now.plusDays(1)));
        var courses = List.of(historical, current);
        assertThat(PlanningPresentation.destinationScope(courses, historical.id(),
                PlanningPresentation.CourseScope.OPEN, now)).isEqualTo(PlanningPresentation.CourseScope.ARCHIVE);
        assertThat(PlanningPresentation.destinationScope(courses, current.id(),
                PlanningPresentation.CourseScope.ARCHIVE, now)).isEqualTo(PlanningPresentation.CourseScope.OPEN);
        assertThat(PlanningPresentation.destinationScope(courses, null,
                PlanningPresentation.CourseScope.ARCHIVE, now)).isEqualTo(PlanningPresentation.CourseScope.ARCHIVE);
        assertThat(PlanningPresentation.destinationScope(courses, UUID.randomUUID(),
                PlanningPresentation.CourseScope.OPEN, now)).isEqualTo(PlanningPresentation.CourseScope.OPEN);
    }

    @Test
    void consultsPastAndCurrentSessionsButAggregatesOnlyScheduledFuturePracticals() {
        var now = LocalDateTime.of(2026, 9, 9, 12, 0);
        var past = session(now.minusDays(1), SessionStatus.COMPLETED);
        var current = session(now, SessionStatus.SCHEDULED);
        var future = session(now.plusDays(1), SessionStatus.SCHEDULED);
        var cancelled = session(now.plusDays(2), SessionStatus.CANCELLED);
        var online = new CourseSessionSummary(UUID.randomUUID(), 1, now.plusDays(1), SessionMode.ONLINE,
                SessionStatus.SCHEDULED, 90, Optional.of(java.net.URI.create("https://example.test/lesson")),
                Optional.empty(), Optional.empty(), 0);
        var sessions = List.of(past, online, current, future, cancelled);
        assertThat(PlanningPresentation.consultableSessions(sessions)).containsExactly(past, current, future, cancelled);
        assertThat(PlanningPresentation.futureRequirementsSessions(sessions, now)).containsExactly(future);
    }

    private static CourseSessionSummary session(LocalDateTime at, SessionStatus status) {
        return new CourseSessionSummary(UUID.randomUUID(), 1, at, SessionMode.PRACTICAL, status,
                90, Optional.empty(), Optional.of("Laboratorio"), Optional.of(12), 1);
    }

    private static CourseSummary course(String title, CourseStatus status, Optional<LocalDateTime> lastEnd) {
        return new CourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Cucina italiana", CourseTitle.of(title),
                "Descrizione del corso", java.time.LocalDate.of(2026, 9, 1), java.time.LocalTime.of(10, 0),
                7, 2, lastEnd, status, IngredientMargin.zero());
    }
}
