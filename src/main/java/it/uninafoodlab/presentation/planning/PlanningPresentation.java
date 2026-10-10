package it.uninafoodlab.presentation.planning;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class PlanningPresentation {
    private PlanningPresentation() {}

    enum CourseScope {
        OPEN("Attivi"), ARCHIVE("Passati");

        private final String label;

        CourseScope(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    static List<CourseSummary> visibleCourses(List<CourseSummary> courses, CourseScope scope, LocalDateTime now) {
        return courses.stream().filter(course -> course.status() != CourseStatus.DRAFT)
                .filter(course -> courseScope(course, now) == scope).toList();
    }

    static CourseScope destinationScope(List<CourseSummary> courses, UUID destinationCourseId,
            CourseScope currentScope, LocalDateTime now) {
        return courses.stream().filter(course -> course.id().equals(destinationCourseId))
                .filter(course -> course.status() != CourseStatus.DRAFT)
                .findFirst().map(course -> courseScope(course, now)).orElse(currentScope);
    }

    private static CourseScope courseScope(CourseSummary course, LocalDateTime now) {

        if (course.status() == CourseStatus.COMPLETED || course.status() == CourseStatus.CANCELLED) {
            return CourseScope.ARCHIVE;
        }
        return course.lastSessionEndsAt().filter(end -> end.isAfter(now)).isPresent()
                ? CourseScope.OPEN : CourseScope.ARCHIVE;
    }

    static List<CourseSessionSummary> consultableSessions(List<CourseSessionSummary> sessions) {
        return sessions.stream().filter(session -> session.mode() == SessionMode.PRACTICAL)
                .sorted(Comparator.comparing(CourseSessionSummary::scheduledAt)
                        .thenComparingInt(CourseSessionSummary::sequenceNumber)).toList();
    }

    static List<CourseSessionSummary> futureRequirementsSessions(
            List<CourseSessionSummary> sessions, LocalDateTime now) {
        return consultableSessions(sessions).stream()
                .filter(session -> session.status() == SessionStatus.SCHEDULED)
                .filter(session -> session.scheduledAt().isAfter(now)).toList();
    }

    static List<CourseSessionSummary> missingMenus(List<CourseSessionSummary> sessions) {
        return sessions.stream().filter(session -> session.recipeCount() == 0).toList();
    }

    static String menuCoverage(List<CourseSessionSummary> sessions) {
        if (sessions.isEmpty()) return "Nessuna sessione pratica futura";
        return "Menu disponibili: " + (sessions.size() - missingMenus(sessions).size())
                + " su " + sessions.size() + " sessioni";
    }

}
