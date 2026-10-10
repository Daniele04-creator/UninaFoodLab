package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.CourseStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public record PublishedCourseSummary(
        UUID id,
        UUID categoryId,
        String categoryName,
        String chefName,
        CourseTitle title,
        String description,
        LocalDate startDate,
        LocalTime startTime,
        int sessionCount,
        LocalDateTime lastSessionEndsAt,
        CourseStatus status) {

    public PublishedCourseSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryId, "categoryId");
        categoryName = required(categoryName, "categoryName");
        chefName = required(chefName, "chefName");
        Objects.requireNonNull(title, "title");
        description = required(description, "description");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(lastSessionEndsAt, "lastSessionEndsAt");
        Objects.requireNonNull(status, "status");
        if (sessionCount < 1) {
            throw new IllegalArgumentException("published course session count must be positive");
        }
    }

    public PublishedCourseSummary(UUID id, UUID categoryId, String categoryName, String chefName,
            CourseTitle title, String description, LocalDate startDate, LocalTime startTime,
            int sessionCount) {
        this(id, categoryId, categoryName, chefName, title, description, startDate, startTime,
                sessionCount, LocalDateTime.of(startDate, startTime), CourseStatus.PUBLISHED);
    }

    public PublishedCourseSummary(UUID id, UUID categoryId, String categoryName, String chefName,
            CourseTitle title, String description, LocalDate startDate, LocalTime startTime,
            int sessionCount, LocalDateTime lastSessionEndsAt) {
        this(id, categoryId, categoryName, chefName, title, description, startDate, startTime,
                sessionCount, lastSessionEndsAt, CourseStatus.PUBLISHED);
    }

    public boolean hasEnded(LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        return status == CourseStatus.COMPLETED || status == CourseStatus.CANCELLED
                || !now.isBefore(lastSessionEndsAt);
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
