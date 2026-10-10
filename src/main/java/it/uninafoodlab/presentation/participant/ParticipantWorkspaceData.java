package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.catalog.BrowseCatalog;
import it.uninafoodlab.application.catalog.CategorySummary;
import it.uninafoodlab.application.course.BrowsePublishedCourses;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.course.PublishedSessionRecipeSummary;
import it.uninafoodlab.application.enrollment.BrowseEnrollments;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.ManageEnrollment;
import it.uninafoodlab.application.enrollment.ManagePracticalAttendance;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.application.notification.BrowseNotifications;
import it.uninafoodlab.application.notification.ManageNotifications;
import it.uninafoodlab.application.notification.ParticipantNotification;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class ParticipantWorkspaceData {

    private final AuthenticatedUser user;
    private final BrowsePublishedCourses publishedCourses;
    private final BrowseCatalog catalog;
    private final BrowseEnrollments enrollments;
    private final ManageEnrollment enrollmentManagement;
    private final ManagePracticalAttendance attendanceManagement;
    private final BrowseNotifications notifications;
    private final ManageNotifications notificationManagement;

    ParticipantWorkspaceData(
            AuthenticatedUser user,
            BrowsePublishedCourses publishedCourses,
            BrowseCatalog catalog,
            BrowseEnrollments enrollments,
            ManageEnrollment enrollmentManagement,
            ManagePracticalAttendance attendanceManagement,
            BrowseNotifications notifications,
            ManageNotifications notificationManagement) {
        this.user = Objects.requireNonNull(user, "user");
        this.publishedCourses = Objects.requireNonNull(publishedCourses, "publishedCourses");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.enrollments = Objects.requireNonNull(enrollments, "enrollments");
        this.enrollmentManagement = Objects.requireNonNull(enrollmentManagement, "enrollmentManagement");
        this.attendanceManagement = Objects.requireNonNull(attendanceManagement, "attendanceManagement");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.notificationManagement = Objects.requireNonNull(
                notificationManagement, "notificationManagement");
    }

    BrowseSnapshot browse(CourseFilter filter, PageRequest requestedPage) {
        var page = publishedCourses.courses(user, filter, requestedPage);
        var actualPage = clampPage(requestedPage, page.totalCount());
        if (!actualPage.equals(requestedPage)) page = publishedCourses.courses(user, filter, actualPage);
        var courseIds = page.items().stream().map(PublishedCourseSummary::id).toList();
        return new BrowseSnapshot(catalog.categories(user), page.items(),
                enrollments.enrollmentsForCourses(user, courseIds), Math.toIntExact(page.totalCount()), actualPage.index());
    }

    MineSnapshot mine(CourseFilter filter, PageRequest requestedPage, Optional<UUID> selectedCourse) {
        var page = enrollments.enrollments(user, filter, requestedPage);
        var actualPage = clampPage(requestedPage, page.totalCount());
        if (!actualPage.equals(requestedPage)) page = enrollments.enrollments(user, filter, actualPage);
        var courseIds = new java.util.ArrayList<>(page.items().stream().map(EnrollmentSummary::courseId).toList());
        selectedCourse.filter(id -> !courseIds.contains(id)).ifPresent(courseIds::add);
        var courses = publishedCourses.courses(user, courseIds);
        var authorizedIds = courses.stream().map(PublishedCourseSummary::id).toList();
        var sessions = publishedCourses.sessions(user, authorizedIds);
        var selectedEnrollment = selectedCourse.flatMap(id ->
                enrollments.enrollmentsForCourses(user, List.of(id)).stream().findFirst());
        return new MineSnapshot(page.items(), courses, sessions, Math.toIntExact(page.totalCount()),
                actualPage.index(), selectedEnrollment);
    }

    private static PageRequest clampPage(PageRequest requestedPage, long total) {
        long lastPage = Math.max(0, (total - 1) / requestedPage.size());
        return new PageRequest((int) Math.min(requestedPage.index(), lastPage), requestedPage.size());
    }

    Optional<NotificationCourseSnapshot> notificationCourse(UUID courseId, CourseFilter filter) {
        var courses = publishedCourses.courses(user, List.of(courseId));
        if (courses.isEmpty()) return Optional.empty();
        var snapshot = browse(filter, new PageRequest(0, 12));
        var enrolled = enrollments.enrollmentsForCourses(user, List.of(courseId));
        var combinedEnrollments = new java.util.ArrayList<>(snapshot.enrollments());
        for (var enrollment : enrolled) {
            if (combinedEnrollments.stream().noneMatch(existing -> existing.courseId().equals(enrollment.courseId()))) {
                combinedEnrollments.add(enrollment);
            }
        }
        var target = new BrowseSnapshot(snapshot.categories(), snapshot.courses(), List.copyOf(combinedEnrollments),
                snapshot.total(), snapshot.page());
        return Optional.of(new NotificationCourseSnapshot(target, courses.getFirst(), course(courseId)));
    }

    record NotificationCourseSnapshot(BrowseSnapshot browse, PublishedCourseSummary course, CourseDetailSnapshot detail) {}

    CourseDetailSnapshot course(UUID courseId) {
        return new CourseDetailSnapshot(
                publishedCourses.sessions(user, courseId),
                publishedCourses.menu(user, courseId));
    }

    List<PracticalAttendanceSummary> practicalSessions(UUID courseId) {
        return enrollments.practicalSessions(user, courseId);
    }

    AttendanceSnapshot attendance(UUID courseId) {
        return new AttendanceSnapshot(practicalSessions(courseId), commitments());
    }

    record AttendanceSnapshot(List<PracticalAttendanceSummary> sessions, List<AttendanceCommitment> commitments) {}

    List<AttendanceCommitment> commitments() {
        return enrollments.commitments(user);
    }

    List<ParticipantNotification> notifications() {
        return notifications.forParticipant(user);
    }

    void enroll(UUID courseId) {
        enrollmentManagement.enroll(user, courseId);
    }

    void withdraw(UUID courseId) {
        enrollmentManagement.withdraw(user, courseId);
    }

    void confirm(UUID sessionId) {
        attendanceManagement.confirm(user, sessionId);
    }

    void decline(UUID sessionId) {
        attendanceManagement.decline(user, sessionId);
    }

    void markRead(UUID notificationId) {
        notificationManagement.markRead(user, notificationId);
    }

    record BrowseSnapshot(
            List<CategorySummary> categories,
            List<PublishedCourseSummary> courses,
            List<EnrollmentSummary> enrollments, int total, int page) {}

    record MineSnapshot(
            List<EnrollmentSummary> enrollments,
            List<PublishedCourseSummary> courses,
            Map<UUID, List<PublishedCourseSessionSummary>> sessions, int total, int page,
            Optional<EnrollmentSummary> selected) {}

    record CourseDetailSnapshot(
            List<PublishedCourseSessionSummary> sessions,
            List<PublishedSessionRecipeSummary> menu) {}
}
