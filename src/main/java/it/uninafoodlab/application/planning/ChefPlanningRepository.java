package it.uninafoodlab.application.planning;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public interface ChefPlanningRepository {

    default SessionPreparation sessionPreparation(UUID sessionToken, UUID sessionId) {
        throw new UnsupportedOperationException("Session preparation snapshot is unavailable");
    }

    List<IngredientRequirement> requirements(UUID sessionToken, UUID sessionId);

    default List<SessionPlanningSnapshot> exportSnapshots(UUID sessionToken, List<UUID> sessionIds) {
        return sessionIds.stream()
                .map(sessionId -> new SessionPlanningSnapshot(sessionId, java.time.LocalDateTime.MIN, 0, "PRELIMINARY", "UNAVAILABLE",
                        requirements(sessionToken, sessionId)))
                .toList();
    }

    default CoursePlanningExport courseExport(UUID sessionToken, UUID courseId) {
        throw new UnsupportedOperationException("Course planning export is unavailable");
    }

    List<SessionParticipant> participants(UUID sessionToken, UUID sessionId);

    List<CourseParticipant> courseParticipants(UUID sessionToken, UUID courseId);

    MonthlyReport monthlyReport(UUID sessionToken, YearMonth month);
}
