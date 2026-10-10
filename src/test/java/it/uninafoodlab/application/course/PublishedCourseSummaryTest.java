package it.uninafoodlab.application.course;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.CourseStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishedCourseSummaryTest {

    @Test
    void ongoingCourseStaysAvailableUntilTheLastSessionEnds() {
        var start = LocalDateTime.of(2026, 9, 1, 10, 0);
        var end = LocalDateTime.of(2026, 9, 30, 12, 0);
        var course = new PublishedCourseSummary(UUID.randomUUID(), UUID.randomUUID(),
                "Cucina campana", "Marta Rossi", CourseTitle.of("Percorso cucina"),
                "Sessioni in laboratorio", start.toLocalDate(), start.toLocalTime(), 4, end);

        assertThat(course.hasEnded(start.plusDays(1))).isFalse();
        assertThat(course.hasEnded(end.minusNanos(1))).isFalse();
        assertThat(course.hasEnded(end)).isTrue();
        assertThat(course.hasEnded(end.plusDays(1))).isTrue();
    }

    @Test
    void cancellationImmediatelyMovesTheCourseToHistoryEvenWhenItsOriginalDateIsFuture() {
        var future = LocalDateTime.of(2027, 1, 1, 10, 0);
        var course = new PublishedCourseSummary(UUID.randomUUID(), UUID.randomUUID(),
                "Cucina campana", "Marta Rossi", CourseTitle.of("Percorso cucina"),
                "Sessioni in laboratorio", future.toLocalDate(), future.toLocalTime(), 1,
                future.plusHours(2), CourseStatus.CANCELLED);

        assertThat(course.hasEnded(future.minusMonths(1))).isTrue();
    }
}
