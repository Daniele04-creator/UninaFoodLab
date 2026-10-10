package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.course.CourseSchedule;
import it.uninafoodlab.domain.course.CourseTitle;
import it.uninafoodlab.domain.course.IngredientMargin;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

public final class CreateCourse {

    private final CourseRepository courseRepository;

    public CreateCourse(CourseRepository courseRepository) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
    }

    public UUID execute(AuthenticatedUser actor, NewCourseInput input) {
        requireChef(actor);
        Objects.requireNonNull(input, "input");
        var course = new CourseCreation(
                actor.sessionToken(),
                input.categoryId(),
                CourseTitle.of(input.title()),
                input.description(),
                IngredientMargin.of(input.ingredientMarginPercent()),
                CourseSchedule.of(
                        input.startDate(),
                        input.startTime(),
                        input.recurrenceDays(),
                        input.sessionDetails()));
        return courseRepository.createAndPublish(course);
    }

    private static void requireChef(AuthenticatedUser actor) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
    }
}
