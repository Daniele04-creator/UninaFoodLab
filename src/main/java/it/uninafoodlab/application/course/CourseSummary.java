package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseStatus;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CourseSummary(
        UUID id,
        UUID categoryId,
        String categoryName,
        CourseTitle title,
        String description,
        LocalDate startDate,
        LocalTime startTime,
        int recurrenceDays,
        int sessionCount,
        Optional<LocalDateTime> lastSessionEndsAt,
        CourseStatus status,
        IngredientMargin ingredientMargin) {

    public CourseSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(lastSessionEndsAt, "lastSessionEndsAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ingredientMargin, "ingredientMargin");
        categoryName = categoryName.strip();
        description = description.strip();
        if (categoryName.isEmpty()
                || description.isEmpty()
                || recurrenceDays < 1
                || sessionCount < 1) {
            throw new IllegalArgumentException("course summary is invalid");
        }
    }
}
