package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.util.Objects;

public record CourseParticipant(String displayName, EnrollmentStatus enrollmentStatus) {
    public CourseParticipant {
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        Objects.requireNonNull(enrollmentStatus, "enrollmentStatus");
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Participant display name must not be blank");
        }
    }
}
