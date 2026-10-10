package it.uninafoodlab.presentation.course;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

record CourseCatalogSnapshot(List<CourseSummary> courses, Map<UUID, List<CourseSessionSummary>> sessions,
        int page, long totalCourses) {
    CourseCatalogSnapshot {
        courses = List.copyOf(courses);
        var copy = new LinkedHashMap<UUID, List<CourseSessionSummary>>();
        sessions.forEach((id, items) -> copy.put(id, List.copyOf(items)));
        sessions = Map.copyOf(copy);
    }

    static Optional<CourseSessionSummary> nextSession(List<CourseSessionSummary> sessions, Clock clock) {
        var now = LocalDateTime.now(clock.withZone(ZoneId.of("Europe/Rome")));
        return sessions.stream()
                .filter(session -> session.status() == SessionStatus.SCHEDULED)
                .filter(session -> !session.scheduledAt().isBefore(now))
                .min(Comparator.comparing(CourseSessionSummary::scheduledAt));
    }
}
