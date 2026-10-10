package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseSchedule;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import java.util.Objects;
import java.util.UUID;

public record CourseCreation(
        UUID sessionToken,
        UUID categoryId,
        CourseTitle title,
        String description,
        IngredientMargin ingredientMargin,
        CourseSchedule schedule) {

    public CourseCreation {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(ingredientMargin, "ingredientMargin");
        Objects.requireNonNull(schedule, "schedule");
        description = description.strip();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("course description must not be blank");
        }
    }
}
