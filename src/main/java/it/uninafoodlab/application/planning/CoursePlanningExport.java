package it.uninafoodlab.application.planning;

import it.uninafoodlab.application.course.CourseSessionSummary;
import java.util.List;

public record CoursePlanningExport(List<CourseSessionSummary> sessions,
        List<SessionPlanningSnapshot> snapshots) {
    public CoursePlanningExport {
        sessions = List.copyOf(sessions);
        snapshots = List.copyOf(snapshots);
        if (!sessions.stream().map(CourseSessionSummary::id).toList()
                .equals(snapshots.stream().map(SessionPlanningSnapshot::sessionId).toList())) {
            throw new IllegalArgumentException("Course planning export sessions are inconsistent");
        }
    }
}
