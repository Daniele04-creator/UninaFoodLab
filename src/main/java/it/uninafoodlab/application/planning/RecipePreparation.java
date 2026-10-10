package it.uninafoodlab.application.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RecipePreparation(UUID recipeId, String recipeName, int baseServings,
        BigDecimal portionsPerParticipant, List<IngredientContribution> ingredients) {
    public RecipePreparation {
        Objects.requireNonNull(recipeId, "recipeId");
        recipeName = Objects.requireNonNull(recipeName, "recipeName").strip();
        Objects.requireNonNull(portionsPerParticipant, "portionsPerParticipant");
        ingredients = List.copyOf(ingredients);
        if (recipeName.isEmpty() || baseServings < 1 || portionsPerParticipant.signum() <= 0) {
            throw new IllegalArgumentException("Recipe preparation is invalid");
        }
    }
}
