package it.uninafoodlab.application.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignRecipeToSessionTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CHEF_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CHEF_SESSION_TOKEN = UUID.randomUUID();

    @Test
    void assignsARecipeUsingTheAuthenticatedChefProfile() {
        var repository = new RecordingCourseRepository();
        var useCase = new AssignRecipeToSession(repository);
        var sessionId = UUID.randomUUID();
        var recipeId = UUID.randomUUID();

        useCase.execute(chef(), sessionId, recipeId);

        assertThat(repository.assignments)
                .containsExactly(new Assignment(CHEF_SESSION_TOKEN, sessionId, recipeId));
    }

    @Test
    void rejectsParticipantsBeforeCallingTheRepository() {
        var repository = new RecordingCourseRepository();
        var useCase = new AssignRecipeToSession(repository);
        var participant = new AuthenticatedUser(
                ACCOUNT_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Username.of("participant.demo"),
                "Partecipante Demo",
                Role.PARTICIPANT);

        var sessionId = UUID.randomUUID();
        var recipeId = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.execute(participant, sessionId, recipeId))
                .isInstanceOf(CourseAuthorizationException.class)
                .hasMessage("Operazione riservata agli chef");
        assertThat(repository.assignments).isEmpty();
    }

    private static AuthenticatedUser chef() {
        return new AuthenticatedUser(
                ACCOUNT_ID,
                CHEF_ID,
                CHEF_SESSION_TOKEN,
                Username.of("chef.demo"),
                "Chef Demo",
                Role.CHEF);
    }

    private record Assignment(UUID sessionToken, UUID sessionId, UUID recipeId) {}

    private static final class RecordingCourseRepository implements CourseRepository {

        private final List<Assignment> assignments = new ArrayList<>();

        @Override
        public UUID create(CourseCreation course) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID createAndPublish(CourseCreation course) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CourseSummary> findByChef(UUID sessionToken, Optional<UUID> categoryId) {
            return List.of();
        }

        @Override
        public List<CourseSessionSummary> sessions(UUID sessionToken, UUID courseId) {
            return List.of();
        }

        @Override
        public void assignRecipe(UUID sessionToken, UUID sessionId, UUID recipeId) {
            assignments.add(new Assignment(sessionToken, sessionId, recipeId));
        }

        @Override
        public List<PublishedCourseSessionSummary> publishedSessions(
                UUID sessionToken, UUID courseId) {
            throw new UnsupportedOperationException();
        }
    }
}
