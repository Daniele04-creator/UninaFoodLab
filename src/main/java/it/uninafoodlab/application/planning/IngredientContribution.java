package it.uninafoodlab.application.planning;

import it.uninafoodlab.domain.catalog.MeasurementUnit;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record IngredientContribution(UUID ingredientId, String ingredientName,
        MeasurementUnit unit, BigDecimal requiredQuantity) {
    public IngredientContribution {
        Objects.requireNonNull(ingredientId, "ingredientId");
        ingredientName = Objects.requireNonNull(ingredientName, "ingredientName").strip();
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        if (ingredientName.isEmpty() || requiredQuantity.signum() < 0) {
            throw new IllegalArgumentException("Ingredient contribution is invalid");
        }
    }
}
