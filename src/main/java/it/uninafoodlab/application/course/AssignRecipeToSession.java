package it.uninafoodlab.application.course;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import java.util.Objects;
import java.util.UUID;

public final class AssignRecipeToSession {

    private final CourseRepository courseRepository;

    public AssignRecipeToSession(CourseRepository courseRepository) {
        this.courseRepository = Objects.requireNonNull(courseRepository, "courseRepository");
    }

    public void execute(AuthenticatedUser actor, UUID sessionId, UUID recipeId) {
        validate(actor, sessionId, recipeId);
        courseRepository.assignRecipe(actor.sessionToken(), sessionId, recipeId);
    }

    public void remove(AuthenticatedUser actor, UUID sessionId, UUID recipeId) {
        validate(actor, sessionId, recipeId);
        courseRepository.removeRecipe(actor.sessionToken(), sessionId, recipeId);
    }

    private static void validate(AuthenticatedUser actor, UUID sessionId, UUID recipeId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(recipeId, "recipeId");
        if (actor.role() != Role.CHEF) {
            throw new CourseAuthorizationException();
        }
    }
}
