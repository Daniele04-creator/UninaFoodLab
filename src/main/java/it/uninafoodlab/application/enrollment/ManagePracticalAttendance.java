package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

public final class ManagePracticalAttendance {

    private final EnrollmentRepository enrollmentRepository;

    public ManagePracticalAttendance(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = Objects.requireNonNull(
                enrollmentRepository, "enrollmentRepository");
    }

    public void confirm(AuthenticatedUser actor, UUID sessionId) {
        setStatus(actor, sessionId, AttendanceStatus.CONFIRMED);
    }

    public void decline(AuthenticatedUser actor, UUID sessionId) {
        setStatus(actor, sessionId, AttendanceStatus.DECLINED);
    }

    private void setStatus(
            AuthenticatedUser actor, UUID sessionId, AttendanceStatus status) {
        requireParticipant(actor);
        enrollmentRepository.setAttendance(
                actor.sessionToken(),
                Objects.requireNonNull(sessionId, "sessionId"),
                status);
    }

    private static void requireParticipant(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.PARTICIPANT) {
            throw new EnrollmentAuthorizationException();
        }
    }
}
