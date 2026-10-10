package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.application.common.Page;
import it.uninafoodlab.application.common.PageRequest;
import it.uninafoodlab.application.course.CourseFilter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BrowseEnrollments {

    private final EnrollmentRepository enrollmentRepository;

    public BrowseEnrollments(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = Objects.requireNonNull(
                enrollmentRepository, "enrollmentRepository");
    }

    public Page<EnrollmentSummary> enrollments(
            AuthenticatedUser actor, CourseFilter filter, PageRequest page) {
        requireParticipant(actor);
        return enrollmentRepository.findByParticipant(actor.sessionToken(),
                Objects.requireNonNull(filter, "filter"), Objects.requireNonNull(page, "page"));
    }

    public List<EnrollmentSummary> enrollmentsForCourses(AuthenticatedUser actor, List<UUID> courseIds) {
        requireParticipant(actor);
        var selectedIds = List.copyOf(courseIds);
        if (selectedIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 courses can be requested");
        }
        return selectedIds.isEmpty() ? List.of()
                : List.copyOf(enrollmentRepository.findByParticipant(actor.sessionToken(), selectedIds));
    }

    public List<AttendanceCommitment> commitments(AuthenticatedUser actor) {
        requireParticipant(actor);
        return List.copyOf(enrollmentRepository.commitments(actor.sessionToken()));
    }

    public List<PracticalAttendanceSummary> practicalSessions(
            AuthenticatedUser actor, UUID courseId) {
        requireParticipant(actor);
        return List.copyOf(enrollmentRepository.practicalSessions(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId")));
    }

    private static void requireParticipant(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.PARTICIPANT) {
            throw new EnrollmentAuthorizationException();
        }
    }
}
