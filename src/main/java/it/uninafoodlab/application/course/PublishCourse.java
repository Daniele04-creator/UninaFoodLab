package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

public final class PublishCourse {

    private final CourseRepository courseRepository;

    public PublishCourse(CourseRepository courseRepository) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
    }

    public void execute(AuthenticatedUser actor, UUID courseId) {
        Objects.requireNonNull(actor, "actor");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
        courseRepository.publish(
                actor.sessionToken(), Objects.requireNonNull(courseId, "courseId"));
    }
}
