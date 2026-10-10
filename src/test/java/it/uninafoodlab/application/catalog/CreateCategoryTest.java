package it.uninafoodlab.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;
import it.uninafoodlab.domain.identity.Username;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCategoryTest {

    @Test
    void createsANormalizedGlobalCategoryForAChef() {
        var repository = new RecordingCatalogRepository();
        var createCategory = new CreateCategory(repository);
        var chef = chef();

        var category = createCategory.create(
                chef, new NewCategoryInput("  Pasticceria  ", " Dolci "));

        assertThat(category.name()).isEqualTo("Pasticceria");
        assertThat(category.description()).isEqualTo("Dolci");
        assertThat(repository.sessionToken).isEqualTo(chef.sessionToken());
        assertThat(repository.input).isEqualTo(new NewCategoryInput("Pasticceria", "Dolci"));
    }

    @Test
    void rejectsParticipantsBeforeCallingTheRepository() {
        var repository = new RecordingCatalogRepository();
        var createCategory = new CreateCategory(repository);

        var participantUser = participant();
        var input = new NewCategoryInput("Pasticceria", "Dolci");
        assertThatThrownBy(() -> createCategory.create(
                        participantUser, input))
                .isInstanceOf(CourseAuthorizationException.class)
                .hasMessage("Operazione riservata agli chef");
        assertThat(repository.input).isNull();
    }

    @Test
    void rejectsNamesAndDescriptionsOutsideTheApprovedBounds() {
        assertThatThrownBy(() -> new NewCategoryInput("  x ", ""))
                .isInstanceOf(IllegalArgumentException.class);
        var oversized = "x".repeat(501);
        assertThatThrownBy(() -> new NewCategoryInput("Pane", oversized))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesAnOmittedDescriptionAsAnEmptyString() {
        assertThat(new NewCategoryInput("Pane", null).description()).isEmpty();
    }

    private static AuthenticatedUser chef() {
        return user(Role.CHEF, "chef.test");
    }

    private static AuthenticatedUser participant() {
        return user(Role.PARTICIPANT, "participant.test");
    }

    private static AuthenticatedUser user(Role role, String username) {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Username.of(username),
                "Utente Test",
                role);
    }

    private static final class RecordingCatalogRepository implements CatalogRepository {

        private UUID sessionToken;
        private NewCategoryInput input;

        @Override
        public CategorySummary createCategory(UUID sessionToken, NewCategoryInput input) {
            this.sessionToken = sessionToken;
            this.input = input;
            return new CategorySummary(UUID.randomUUID(), input.name(), input.description());
        }

        @Override
        public List<CategorySummary> categories(UUID sessionToken) {
            return List.of();
        }

    }
}
