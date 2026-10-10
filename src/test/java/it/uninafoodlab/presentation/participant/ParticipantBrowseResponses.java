package it.uninafoodlab.presentation.participant;

import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import it.uninafoodlab.application.course.CoursePeriod;
import it.uninafoodlab.application.course.PublishedCourseSessionSummary;
import it.uninafoodlab.application.course.PublishedCourseSummary;
import it.uninafoodlab.application.enrollment.AttendanceCommitment;
import it.uninafoodlab.application.enrollment.EnrollmentSummary;
import it.uninafoodlab.application.enrollment.PracticalAttendanceSummary;
import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

final class ParticipantBrowseResponses {
    private ParticipantBrowseResponses() {}

    static Object courses(Object[] arguments, List<PublishedCourseSummary> courses) {
        if (arguments[1] instanceof List<?> ids) return courses.stream().filter(course -> ids.contains(course.id())).toList();
        var filter = (CourseFilter) arguments[1];
        var filtered = courses.stream().filter(course -> matches(course, filter)).toList();
        return page(filtered, (PageRequest) arguments[2]);
    }

    static Object sessions(Object courseId, Function<UUID, List<PublishedCourseSessionSummary>> sessions) {
        if (courseId instanceof List<?> ids) {
            var grouped = new HashMap<UUID, List<PublishedCourseSessionSummary>>();
            for (var id : ids) grouped.put((UUID) id, sessions.apply((UUID) id));
            return grouped;
        }
        return sessions.apply((UUID) courseId);
    }

    private static boolean matches(PublishedCourseSummary course, CourseFilter filter) {
        boolean ended = course.hasEnded(filter.now());
        boolean period = filter.period() == CoursePeriod.ALL || (filter.period() == CoursePeriod.PAST ? ended : !ended);
        String text = (course.title().value() + " " + course.categoryName() + " " + course.chefName() + " " + course.description())
                .toLowerCase(Locale.ITALIAN);
        return period && (filter.categoryId().isEmpty() || filter.categoryId().get().equals(course.categoryId()))
                && text.contains(filter.search().toLowerCase(Locale.ITALIAN));
    }

    static Object enrollments(Object[] arguments, List<EnrollmentSummary> enrollments) {
        if (arguments[1] instanceof List<?> ids) {
            return enrollments.stream().filter(enrollment -> ids.contains(enrollment.courseId())).toList();
        }
        var filter = (CourseFilter) arguments[1];
        var filtered = enrollments.stream().filter(enrollment -> filter.period() == CoursePeriod.ALL
                || (filter.period() == CoursePeriod.ACTIVE
                ? enrollment.status() == EnrollmentStatus.ACTIVE : enrollment.status() == EnrollmentStatus.WITHDRAWN)).toList();
        return page(filtered, (PageRequest) arguments[2]);
    }

    static List<AttendanceCommitment> commitments(List<EnrollmentSummary> enrollments,
            Function<UUID, List<PracticalAttendanceSummary>> sessions) {
        var commitments = new ArrayList<AttendanceCommitment>();
        for (var enrollment : enrollments) {
            if (enrollment.status() != EnrollmentStatus.ACTIVE) continue;
            for (var session : sessions.apply(enrollment.courseId())) {
                if (session.attendanceStatus().orElse(null) == AttendanceStatus.CONFIRMED) {
                    commitments.add(new AttendanceCommitment(enrollment.courseId(), enrollment.title(),
                            session.scheduledAt(), session.durationMinutes()));
                }
            }
        }
        return commitments;
    }

    private static <T> Page<T> page(List<T> items, PageRequest request) {
        int from = Math.min(request.offset(), items.size());
        return new Page<>(items.subList(from, Math.min(from + request.size(), items.size())), items.size());
    }
}
