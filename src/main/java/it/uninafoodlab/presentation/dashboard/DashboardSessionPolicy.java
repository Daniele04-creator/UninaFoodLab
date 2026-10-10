package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.application.course.CourseSessionSummary;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.course.SessionStatus;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController.DashboardSession;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class DashboardSessionPolicy {
    private DashboardSessionPolicy() {}
    static java.util.Optional<DashboardSession> nextAgendaSession(DashboardCalendar<DashboardSession> calendar) {
        return calendar.nextSessionAfterSelectedDay(item -> item.session().status() != SessionStatus.CANCELLED);
    }

    static List<DashboardSession> menusToPrepare(LocalDateTime now, List<DashboardSession> sessions) {
        return sessions.stream()
                .filter(item -> requiresRecipe(now, item.session()))
                .sorted(Comparator.comparing(item -> item.session().scheduledAt()))
                .toList();
    }

    static boolean requiresRecipe(LocalDateTime now, CourseSessionSummary session) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(session, "session");
        return session.mode() == SessionMode.PRACTICAL
                && session.status() == SessionStatus.SCHEDULED
                && !session.scheduledAt().isBefore(now)
                && session.recipeCount() == 0;
    }

}
