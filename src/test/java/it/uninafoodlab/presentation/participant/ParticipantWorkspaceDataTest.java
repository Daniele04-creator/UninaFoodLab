package it.uninafoodlab.presentation.participant;

import static org.assertj.core.api.Assertions.assertThat;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CatalogRepository;
import it.uninafoodlab.application.course.BrowsePublishedCourses;
import it.uninafoodlab.application.course.CourseRepository;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.BrowseEnrollments;
import it.uninafoodlab.application.enrollment.EnrollmentRepository;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.ManageEnrollment;
import it.uninafoodlab.application.enrollment.ManagePracticalAttendance;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.NotificationRepository;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.SessionMode;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantWorkspaceDataTest {
    @Test
    void personalAppointmentUsesAuthorizedPublishedSessionsIncludingOnlineAppointments() {
        var own = course("Pane di casa");
        var available = course("Pasta fresca");
        var hidden = course("Corso storico non disponibile");
        var online = new PublishedCourseSessionSummary(UUID.randomUUID(), 2,
                LocalDateTime.of(2026, 10, 5, 18, 0), SessionMode.ONLINE, 60,
                Optional.empty(), Optional.empty(), Optional.empty());
        var requested = new ArrayList<UUID>();
        CourseRepository courses = proxy(CourseRepository.class, (method, args) -> switch (method.getName()) {
            case "findPublished", "findPublishedByIds" -> ParticipantBrowseResponses.courses(args, List.of(own, available));
            case "publishedSessions" -> ParticipantBrowseResponses.sessions(args[1], id -> { requested.add(id); return List.of(online); });
            default -> List.of();
        });
        EnrollmentRepository enrollments = proxy(EnrollmentRepository.class, (method, args) -> switch (method.getName()) {
            case "findByParticipant" -> ParticipantBrowseResponses.enrollments(args, List.of(enrollment(own, EnrollmentStatus.ACTIVE),
                    enrollment(hidden, EnrollmentStatus.WITHDRAWN)));
            default -> List.of();
        });
        var data = data(courses, enrollments);

        var snapshot = data.mine(new it.uninafoodlab.application.course.CourseFilter(Optional.empty(), "",
                it.uninafoodlab.application.course.CoursePeriod.ALL, LocalDateTime.of(2026, 10, 2, 12, 0)),
                new it.uninafoodlab.application.common.PageRequest(0, 12), Optional.empty());

        assertThat(snapshot.sessions()).containsOnlyKeys(own.id());
        assertThat(snapshot.sessions().get(own.id())).containsExactly(online);
        assertThat(requested).containsExactly(own.id());
        assertThat(snapshot.enrollments()).hasSize(2);
    }

    @Test
    void withdrawalKeepsTheOpenDetailEvenWhenTheActivePageBecomesEmpty() {
        var course = course("Pane di casa");
        var withdrawn = enrollment(course, EnrollmentStatus.WITHDRAWN);
        CourseRepository courses = proxy(CourseRepository.class, (method, args) -> switch (method.getName()) {
            case "findPublished", "findPublishedByIds" -> ParticipantBrowseResponses.courses(args, List.of(course));
            case "publishedSessions" -> ParticipantBrowseResponses.sessions(args[1], id -> List.of());
            default -> List.of();
        });
        EnrollmentRepository enrollments = proxy(EnrollmentRepository.class, (method, args) -> switch (method.getName()) {
            case "findByParticipant" -> ParticipantBrowseResponses.enrollments(args, List.of(withdrawn));
            default -> List.of();
        });
        var snapshot = data(courses, enrollments).mine(new it.uninafoodlab.application.course.CourseFilter(
                Optional.empty(), "", it.uninafoodlab.application.course.CoursePeriod.ACTIVE,
                LocalDateTime.of(2026, 10, 2, 12, 0)), new it.uninafoodlab.application.common.PageRequest(0, 12),
                Optional.of(course.id()));
        assertThat(snapshot.enrollments()).isEmpty();
        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.selected()).contains(withdrawn);
        assertThat(snapshot.courses()).containsExactly(course);
    }

    private static ParticipantWorkspaceData data(CourseRepository courses, EnrollmentRepository enrollments) {
        CatalogRepository catalog = proxy(CatalogRepository.class, (method, args) -> List.of());
        NotificationRepository notifications = proxy(NotificationRepository.class, (method, args) -> List.of());
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Username.of("partecipante.test"), "Partecipante", Role.PARTICIPANT);
        return new ParticipantWorkspaceData(user, new BrowsePublishedCourses(courses),
                new BrowseCatalog(catalog), new BrowseEnrollments(enrollments),
                new ManageEnrollment(enrollments), new ManagePracticalAttendance(enrollments),
                new BrowseNotifications(notifications), new ManageNotifications(notifications));
    }

    private static PublishedCourseSummary course(String title) {
        return new PublishedCourseSummary(UUID.randomUUID(), UUID.randomUUID(), "Panificazione", "Chef",
                CourseTitle.of(title), "Laboratorio", LocalDate.of(2026, 10, 2), LocalTime.of(18, 0), 2);
    }

    private static EnrollmentSummary enrollment(PublishedCourseSummary course, EnrollmentStatus status) {
        return new EnrollmentSummary(course.id(), course.title(), course.categoryName(), course.chefName(),
                course.startDate(), status, Instant.parse("2026-10-01T08:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Response response) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> response.apply(method, args));
    }

    @FunctionalInterface
    private interface Response {
        Object apply(java.lang.reflect.Method method, Object[] args);
    }
}
