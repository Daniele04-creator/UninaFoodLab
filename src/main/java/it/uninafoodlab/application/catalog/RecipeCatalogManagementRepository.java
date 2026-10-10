package it.uninafoodlab.application.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeCatalogManagementRepository {
    List<IngredientSummary> ingredients(UUID sessionToken);

    List<RecipeIngredientInput> recipeIngredients(UUID sessionToken, UUID recipeId);

    UUID saveIngredient(UUID sessionToken, Optional<UUID> id, IngredientInput input);

    UUID saveRecipe(UUID sessionToken, Optional<UUID> id, RecipeInput input);
}
