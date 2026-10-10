package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.enrollment.AttendanceStatus;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.util.Objects;
import java.util.Optional;

public record SessionParticipant(
        String displayName,
        EnrollmentStatus enrollmentStatus,
        Optional<AttendanceStatus> attendanceStatus) {

    public SessionParticipant {
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        Objects.requireNonNull(enrollmentStatus, "enrollmentStatus");
        Objects.requireNonNull(attendanceStatus, "attendanceStatus");
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Participant display name must not be blank");
        }
    }
}
