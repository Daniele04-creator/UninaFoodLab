package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

final class CourseOperationalView {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private CourseOperationalView() {
    }

    static String catalogLabel(CourseSummary course, Clock clock) {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(clock, "clock");
        if (course.status() == CourseStatus.CANCELLED
                || course.status() == CourseStatus.PUBLISHED
                        && course.lastSessionEndsAt().isEmpty()) {
            return "Annullato";
        }
        if (isArchived(course, clock)) {
            return "Concluso";
        }
        var now = LocalDateTime.now(clock.withZone(ROME));
        return LocalDateTime.of(course.startDate(), course.startTime()).isAfter(now)
                ? "In programma" : "In corso";
    }

    static boolean isArchived(CourseSummary course, Clock clock) {
        if (course.status() == CourseStatus.DRAFT) {
            return false;
        }
        if (course.status() == CourseStatus.COMPLETED
                || course.status() == CourseStatus.CANCELLED) {
            return true;
        }
        var now = LocalDateTime.now(clock.withZone(ROME));
        return course.lastSessionEndsAt()
                .map(end -> !end.isAfter(now))
                .orElse(true);
    }

    enum CatalogSection {
        ACTIVE,
        ARCHIVE,
        ALL
    }
}
