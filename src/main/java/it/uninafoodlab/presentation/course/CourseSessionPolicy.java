package it.uninafoodlab.presentation.course;

import static it.uninafoodlab.presentation.course.CoursePresentation.*;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CourseSessionPolicy {
    private CourseSessionPolicy() {}

    static boolean isRecipeMutable(CourseSessionSummary session, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return session != null
                && session.mode() == SessionMode.PRACTICAL
                && session.status() == SessionStatus.SCHEDULED
                && LocalDateTime.ofInstant(clock.instant(), ROME).isBefore(session.scheduledAt())
                && session.attendanceClosesAt()
                    .map(cutoff -> LocalDateTime.ofInstant(clock.instant(), ROME).isBefore(cutoff)).orElse(true);
    }

    static List<CourseSessionSummary> sessionsForDisplay(List<CourseSessionSummary> sessions) {
        return Objects.requireNonNull(sessions, "sessions").stream()
                .sorted(java.util.Comparator.comparing(CourseSessionSummary::scheduledAt)
                        .thenComparingInt(CourseSessionSummary::sequenceNumber)).toList();
    }

    static Optional<CourseSessionSummary> recipeSessionSelection(
            List<CourseSessionSummary> sessions, CourseSessionSummary selectedSession) {
        var availableSessions = List.copyOf(Objects.requireNonNull(sessions, "sessions"));
        if (selectedSession != null) {
            var retainedSelection = availableSessions.stream()
                    .filter(session -> session.id().equals(selectedSession.id()))
                    .findFirst();
            if (retainedSelection.isPresent()) {
                return retainedSelection;
            }
        }
        return availableSessions.stream().findFirst();
    }
}
