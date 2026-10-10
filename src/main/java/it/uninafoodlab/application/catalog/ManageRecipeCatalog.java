package it.uninafoodlab.application.catalog;

import it.uninafoodlab.application.course.CourseAuthorizationException;
import it.uninafoodlab.domain.identity.AuthenticatedUser;
import it.uninafoodlab.domain.identity.Role;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ManageRecipeCatalog {
    private final RecipeCatalogManagementRepository repository;

    public ManageRecipeCatalog(RecipeCatalogManagementRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    private UUID chefSessionToken(AuthenticatedUser actor) {
        Objects.requireNonNull(actor);
        if (actor.role() != Role.CHEF) throw new CourseAuthorizationException();
        return actor.sessionToken();
    }

    public List<IngredientSummary> ingredients(AuthenticatedUser actor) {
        return List.copyOf(repository.ingredients(chefSessionToken(actor)));
    }

    public List<RecipeIngredientInput> recipeIngredients(AuthenticatedUser actor, UUID recipeId) {
        return List.copyOf(
                repository.recipeIngredients(chefSessionToken(actor), Objects.requireNonNull(recipeId)));
    }

    public UUID saveIngredient(AuthenticatedUser actor, Optional<UUID> id, IngredientInput input) {
        return repository.saveIngredient(
                chefSessionToken(actor), Objects.requireNonNull(id), Objects.requireNonNull(input));
    }

    public UUID saveRecipe(AuthenticatedUser actor, Optional<UUID> id, RecipeInput input) {
        return repository.saveRecipe(
                chefSessionToken(actor), Objects.requireNonNull(id), Objects.requireNonNull(input));
    }
}
