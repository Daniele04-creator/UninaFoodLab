package it.uninafoodlab.application.course;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import it.uninafoodlab.domain.catalog.MeasurementUnit;

public record PublishedSessionRecipeSummary(
        UUID sessionId, UUID recipeId, String name, String description,
        int baseServings, BigDecimal portionsPerParticipant, List<IngredientDose> ingredients) {

    public PublishedSessionRecipeSummary(UUID sessionId, UUID recipeId, String name, String description,
            int baseServings, BigDecimal portionsPerParticipant) {
        this(sessionId, recipeId, name, description, baseServings, portionsPerParticipant, List.of());
    }

    public PublishedSessionRecipeSummary {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(recipeId, "recipeId");
        name = Objects.requireNonNull(name, "name").strip();
        description = Objects.requireNonNull(description, "description").strip();
        Objects.requireNonNull(portionsPerParticipant, "portionsPerParticipant");
        ingredients = List.copyOf(ingredients);
        if (name.isEmpty() || baseServings < 1 || portionsPerParticipant.signum() <= 0) {
            throw new IllegalArgumentException("published recipe is invalid");
        }
    }

    public record IngredientDose(String name, BigDecimal quantity, MeasurementUnit unit) {
        public IngredientDose {
            name = Objects.requireNonNull(name, "name").strip();
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(unit, "unit");
            if (name.isEmpty() || quantity.signum() <= 0) {
                throw new IllegalArgumentException("ingredient dose is invalid");
            }
        }
    }
}
