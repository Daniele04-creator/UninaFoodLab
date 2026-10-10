package it.uninafoodlab.application.enrollment;

import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.enrollment.EnrollmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record EnrollmentSummary(
        UUID courseId,
        CourseTitle title,
        String categoryName,
        String chefName,
        LocalDate startDate,
        EnrollmentStatus status,
        Instant enrolledAt) {

    public EnrollmentSummary {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(title, "title");
        categoryName = requireText(categoryName, "categoryName");
        chefName = requireText(chefName, "chefName");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
