package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

public final class ManageEnrollment {

    private final EnrollmentRepository enrollmentRepository;

    public ManageEnrollment(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = Objects.requireNonNull(
                enrollmentRepository, "enrollmentRepository");
    }

    public void enroll(AuthenticatedUser actor, UUID courseId) {
        requireParticipant(actor);
        enrollmentRepository.enroll(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"));
    }

    public void withdraw(AuthenticatedUser actor, UUID courseId) {
        requireParticipant(actor);
        enrollmentRepository.withdraw(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"));
    }

    private static void requireParticipant(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.PARTICIPANT) {
            throw new EnrollmentAuthorizationException();
        }
    }
}
