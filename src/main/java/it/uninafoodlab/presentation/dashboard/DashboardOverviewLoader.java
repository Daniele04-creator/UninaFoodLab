package it.uninafoodlab.presentation.dashboard;

import it.uninafoodlab.application.course.BrowseCourses;
import it.uninafoodlab.application.course.CourseSummary;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.presentation.dashboard.DashboardWorkspaceController.DashboardSession;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class DashboardOverviewLoader {
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private final AuthenticatedUser user;
    private final BrowseCourses browseCourses;

    DashboardOverviewLoader(AuthenticatedUser user, BrowseCourses browseCourses) {
        this.user = user;
        this.browseCourses = browseCourses;
    }

    DashboardSnapshot load() {
        var now = LocalDateTime.now(ROME);
        var courses = browseCourses.courses(user, Optional.empty(), Optional.empty());
        if (courses.isEmpty()) return new DashboardSnapshot(List.of(), List.of());
        var sessionsByCourse = browseCourses.sessions(user, courses.stream().map(CourseSummary::id).toList());
        var sessions = courses.stream()
                .flatMap(course -> sessionsByCourse.getOrDefault(course.id(), List.of()).stream()
                        .map(session -> new DashboardSession(course.id(), course.title().value(), session)))
                .sorted(Comparator.comparing(item -> item.session().scheduledAt()))
                .toList();
        return new DashboardSnapshot(sessions, DashboardSessionPolicy.menusToPrepare(now, sessions));
    }

    record DashboardSnapshot(
            List<DashboardSession> sessions,
            List<DashboardSession> menusToPrepare) {
        DashboardSnapshot {
            sessions = List.copyOf(sessions);
            menusToPrepare = List.copyOf(menusToPrepare);
        }
    }

}
