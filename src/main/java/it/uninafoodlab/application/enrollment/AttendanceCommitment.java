package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.course.CourseTitle;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record AttendanceCommitment(
        UUID courseId, CourseTitle courseTitle, LocalDateTime scheduledAt, int durationMinutes) {

    public AttendanceCommitment {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(courseTitle, "courseTitle");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("Attendance duration must be positive");
        }
    }
}
